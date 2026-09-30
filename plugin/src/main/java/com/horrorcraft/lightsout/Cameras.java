package com.horrorcraft.lightsout;

import com.horrorcraft.lightsout.AutoDirector.Situation;
import com.horrorcraft.lightsout.AutoDirector.Subject;
import com.horrorcraft.lightsout.Roster.Actor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Spectator camera accounts: forced spectator, hidden from everyone but other cameras, locked
 * onto a target (or the auto director). Main thread only.
 */
public final class Cameras {

    public enum Mode { AUTO, TARGET, FREE }

    public record Settings(long minShotMs, long rotateMs, double nearRadius, double chaseRadius,
                           long bodyHoldMs, long cueMs) {}

    /** One camera account. */
    public static final class Rig {
        final Actor actor;
        Mode mode;
        String target;            // TARGET mode: actor id to follow
        String following;         // actor id the camera is locked on now, null when holding or free
        long holdUntil;           // body hold: stay put until then
        AutoDirector auto;

        Rig(Actor actor) {
            this.actor = actor;
        }
    }

    private record Cue(String camper, long until) {}

    private final LightsOutPlugin plugin;
    private final Game game;
    private final Settings settings;
    private final Map<String, Rig> rigs = new LinkedHashMap<>();  // by camera actor id
    private final Map<String, Cue> cues = new HashMap<>();        // reveal, chase, discovery
    private final Map<String, Location> lastSeen = new HashMap<>(); // actor id -> body or last spot

    public Cameras(LightsOutPlugin plugin, Game game, Settings settings, Map<String, String> follow) {
        this.plugin = plugin;
        this.game = game;
        this.settings = settings;
        for (Actor a : game.roster().all()) {
            if (!a.isCamera()) continue;
            Rig r = new Rig(a);
            String f = follow.getOrDefault(a.id(), "free");
            setMode(r, f);
            rigs.put(a.id(), r);
        }
    }

    public Map<String, Rig> rigs() { return rigs; }

    public Rig rig(String name) {
        Actor a = game.roster().byId(name);
        if (a == null) a = game.roster().byUsername(name);
        return a == null ? null : rigs.get(a.id());
    }

    // ---------- modes and cues ----------

    /** "auto", "free", or a target (actor id or username). Returns an error, or null. */
    public String setMode(Rig r, String what) {
        String w = what.toLowerCase(Locale.ROOT);
        if (w.equals("auto")) {
            r.mode = Mode.AUTO;
            r.target = null;
            r.auto = new AutoDirector(settings.minShotMs(), settings.rotateMs(), settings.nearRadius());
        } else if (w.equals("free")) {
            r.mode = Mode.FREE;
            r.target = null;
        } else {
            Actor t = game.roster().byId(w);
            if (t == null) t = game.roster().byUsername(what);
            if (t == null || t.isCamera()) return "unknown target " + what;
            r.mode = Mode.TARGET;
            r.target = t.id();
        }
        r.holdUntil = 0;
        return null;
    }

    /** Applies a mode change right away, as the director asked for it. */
    public void directorSet(Rig r) {
        Player cam = online(r);
        if (r.mode == Mode.FREE) {
            if (cam != null && cam.getSpectatorTarget() != null) cam.setSpectatorTarget(null);
            r.following = null;
            log(r, null, "free");
            return;
        }
        r.following = null;
        tick(r, System.currentTimeMillis(), true);
    }

    public void cue(String kind, String camper) {
        cues.put(kind, new Cue(camper, System.currentTimeMillis() + settings.cueMs()));
    }

    // ---------- events ----------

    public void onJoin(Player p) {
        Actor a = game.actor(p);
        if (a != null && a.isCamera()) {
            if (p.getGameMode() != GameMode.SPECTATOR) p.setGameMode(GameMode.SPECTATOR);
            for (Player other : Bukkit.getOnlinePlayers()) hideFrom(other, p);
            Rig r = rigs.get(a.id());
            if (r != null) {
                r.following = null;
                Bukkit.getScheduler().runTaskLater(plugin, () -> tick(r, System.currentTimeMillis(), true), 20);
            }
        } else {
            for (Rig r : rigs.values()) {
                Player cam = online(r);
                if (cam != null) hideFrom(p, cam);
            }
        }
    }

    /** Cameras are hidden from every non-camera player. */
    private void hideFrom(Player viewer, Player cam) {
        if (viewer.equals(cam)) return;
        Actor va = game.actor(viewer);
        if (va != null && va.isCamera()) viewer.showPlayer(plugin, cam);
        else viewer.hidePlayer(plugin, cam);
    }

    /** A subject died or left: cameras on it hold on the spot, then move on. */
    public void onGone(Player p) {
        Actor a = game.actor(p);
        if (a == null) return;
        Location spot = p.getLocation().clone();
        lastSeen.put(a.id(), spot);
        long now = System.currentTimeMillis();
        for (Rig r : rigs.values()) {
            if (!a.id().equals(r.following)) continue;
            Player cam = online(r);
            r.following = null;
            if (r.auto != null) r.auto.release();
            if (cam == null) continue;
            cam.setSpectatorTarget(null);
            cam.teleport(bodyShot(spot));
            r.holdUntil = now + settings.bodyHoldMs();
            log(r, a.id(), "body");
        }
    }

    public boolean isCamera(Player p) {
        Actor a = game.actor(p);
        return a != null && a.isCamera();
    }

    // ---------- ticks ----------

    /** Every 2 s: auto cuts, holds ending, fixed cameras switching between target and killer. */
    public void tickDirector() {
        long now = System.currentTimeMillis();
        for (Rig r : rigs.values()) tick(r, now, false);
    }

    /** Every 5 s: spectator mode and re-lock onto whatever the camera should be following. */
    public void tickRelock() {
        for (Rig r : rigs.values()) {
            Player cam = online(r);
            if (cam == null) continue;
            if (cam.getGameMode() != GameMode.SPECTATOR) cam.setGameMode(GameMode.SPECTATOR);
            if (r.following == null) continue;
            Player t = game.online(r.following);
            if (t != null && !t.equals(cam.getSpectatorTarget())) lockOn(cam, t);
        }
    }

    private void tick(Rig r, long now, boolean force) {
        Player cam = online(r);
        if (cam == null || r.mode == Mode.FREE) return;
        if (r.holdUntil > now) return;
        r.holdUntil = 0;

        if (r.mode == Mode.AUTO) {
            AutoDirector.Shot shot = r.auto.decide(situation(now));
            if (shot != null) follow(r, cam, shot.target(), shot.reason());
            else if (force && r.auto.current() != null) follow(r, cam, r.auto.current(), "director");
            return;
        }

        // TARGET: the target while it is alive and online, otherwise the killer.
        Actor target = game.roster().byId(r.target);
        String want = alive(target) ? r.target : null;
        String reason = "target";
        if (want == null) {
            Player k = game.killer();
            if (k != null && !k.equals(cam)) {
                want = game.roster().killer().id();
                reason = "killer_fallback";
            }
        }
        if (want == null) return;
        if (force || !want.equals(r.following)) follow(r, cam, want, reason);
    }

    private void follow(Rig r, Player cam, String id, String reason) {
        Player t = game.online(id);
        if (t == null) return;
        r.following = id;
        lockOn(cam, t);
        log(r, id, reason);
    }

    private void lockOn(Player cam, Player target) {
        if (cam.getGameMode() != GameMode.SPECTATOR) cam.setGameMode(GameMode.SPECTATOR);
        Location c = cam.getLocation();
        Location t = target.getLocation();
        if (!c.getWorld().equals(t.getWorld()) || c.distanceSquared(t) > 48 * 48) cam.teleport(t);
        cam.setSpectatorTarget(target);
    }

    // ---------- situation for the auto director ----------

    Situation situation(long now) {
        List<Subject> living = new ArrayList<>();
        for (Actor a : game.roster().all()) {
            if (!alive(a) || !a.hasLives()) continue;
            living.add(subject(a.id(), game.online(a.id()).getLocation()));
        }
        Player k = game.killer();
        Subject killer = k == null ? null : subject(game.roster().killer().id(), k.getLocation());

        String chase = cueFor("chase", now);
        if (chase == null && killer != null && game.gate().target() != null && game.gate().remaining() > 0) {
            Player t = game.online(game.gate().target());
            if (t != null && t.getWorld().equals(k.getWorld())
                    && t.getLocation().distanceSquared(k.getLocation()) <= settings.chaseRadius() * settings.chaseRadius()) {
                chase = game.gate().target();
            }
        }
        String reveal = cueFor("reveal", now);
        if (reveal == null && killer != null && !game.state().killer_hidden) {
            reveal = closest(living, killer);
        }
        return new Situation(now, living, killer, chase, reveal, cueFor("discovery", now));
    }

    private String closest(List<Subject> living, Subject killer) {
        String best = null;
        double bestD = settings.nearRadius() * settings.nearRadius();
        for (Subject s : living) {
            if (!s.world().equals(killer.world())) continue;
            double dx = s.x() - killer.x(), dy = s.y() - killer.y(), dz = s.z() - killer.z();
            double d = dx * dx + dy * dy + dz * dz;
            if (d <= bestD) {
                bestD = d;
                best = s.id();
            }
        }
        return best;
    }

    private String cueFor(String kind, long now) {
        Cue c = cues.get(kind);
        if (c == null) return null;
        if (now > c.until()) {
            cues.remove(kind);
            return null;
        }
        return c.camper();
    }

    /** Online, not dead, not spectating, and (for campers) lives left. */
    private boolean alive(Actor a) {
        if (a == null) return false;
        Player p = game.online(a.id());
        if (p == null || p.isDead() || p.getGameMode() == GameMode.SPECTATOR) return false;
        return !a.hasLives() || game.lives().get(a.id()) > 0;
    }

    private static Subject subject(String id, Location l) {
        return new Subject(id, l.getWorld().getName(), l.getX(), l.getY(), l.getZ());
    }

    /** A spot 3 blocks back and 2 up from the body, looking at it. */
    private static Location bodyShot(Location body) {
        Vector back = body.getDirection().setY(0);
        if (back.lengthSquared() < 1e-4) back = new Vector(0, 0, 1);
        Location cam = body.clone().subtract(back.normalize().multiply(3)).add(0, 2, 0);
        cam.setDirection(body.clone().add(0, 0.5, 0).toVector().subtract(cam.toVector()));
        return cam;
    }

    // ---------- helpers ----------

    private Player online(Rig r) {
        return Bukkit.getPlayerExact(r.actor.username());
    }

    private void log(Rig r, String target, String reason) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("account", r.actor.id());
        f.put("mode", r.mode.name().toLowerCase(Locale.ROOT));
        f.put("target", target);
        f.put("reason", reason);
        game.outbox().emit("cam", f);
    }

    /** One line per camera for /lo cam status. */
    public List<String> status() {
        List<String> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Rig r : rigs.values()) {
            Player cam = online(r);
            Entity spec = cam == null ? null : cam.getSpectatorTarget();
            String mode = r.mode == Mode.TARGET ? "target:" + r.target : r.mode.name().toLowerCase(Locale.ROOT);
            out.add(r.actor.username() + " " + (cam == null ? "offline" : cam.getGameMode().name().toLowerCase(Locale.ROOT))
                    + " mode=" + mode
                    + " following=" + r.following
                    + " spectating=" + (spec instanceof Player sp ? sp.getName() : spec == null ? "none" : spec.getType().name())
                    + (r.holdUntil > now ? " hold=" + ((r.holdUntil - now + 999) / 1000) + "s" : "")
                    + (r.auto != null && r.auto.reason() != null ? " shot=" + r.auto.reason() : ""));
        }
        return out;
    }
}
