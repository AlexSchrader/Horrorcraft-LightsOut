package com.horrorcraft.lightsout;

import com.horrorcraft.lightsout.Roster.Actor;
import com.horrorcraft.lightsout.Roster.Role;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** All game state and actions. Main thread only, except positions() which chat reads async. */
public final class Game {

    private static final int FOOD_NO_SPRINT = 6; // the client cannot sprint at food 6 or below
    private static final int MAX_DRIPS = 2000;
    private static final Particle.DustOptions BLOOD = new Particle.DustOptions(Color.fromRGB(110, 0, 0), 1.2f);

    private record Drip(World world, double x, double y, double z, long at) {}

    private final LightsOutPlugin plugin;
    private final Roster roster;
    private final Lives lives;
    private final HitGate gate;
    private final Outbox outbox;
    private final StateStore store;
    private final StateStore.State state;
    private StunRoller stun;

    private final double chatRadius;
    private final double stunChance;
    private final long stunMs;
    private final long trailMs;

    private long killerStunnedUntil;
    private final Map<UUID, UUID> carries = new HashMap<>(); // rider -> carrier
    private final ArrayDeque<Drip> drips = new ArrayDeque<>();
    private final Map<UUID, Proximity.Pos> positions = new ConcurrentHashMap<>();
    private final Set<UUID> lethal = new HashSet<>();         // our own killing blow, let through once
    private final Map<UUID, String> deathCause = new HashMap<>();
    private final Set<String> deathLogged = new HashSet<>();  // one death per camper per run

    public Game(LightsOutPlugin plugin, Roster roster, Lives lives, long hitSpacingMs, double chatRadius,
                double stunChance, long stunMs, long trailMs, Outbox outbox, StateStore store, StateStore.State state) {
        this.plugin = plugin;
        this.roster = roster;
        this.lives = lives;
        this.gate = new HitGate(hitSpacingMs);
        this.chatRadius = chatRadius;
        this.stunChance = stunChance;
        this.stunMs = stunMs;
        this.trailMs = trailMs;
        this.outbox = outbox;
        this.store = store;
        this.state = state;
        this.lives.restore(roster.livesIds(), state.lives);
        this.stun = new StunRoller(state.seed, state.stun_index);
    }

    // ---------- lookups ----------

    public Roster roster() { return roster; }
    public Lives lives() { return lives; }
    public HitGate gate() { return gate; }
    public StateStore.State state() { return state; }
    public double chatRadius() { return chatRadius; }
    public Map<UUID, Proximity.Pos> positions() { return positions; }
    public Outbox outbox() { return outbox; }

    public Actor actor(Player p) {
        return p == null ? null : roster.byUsername(p.getName());
    }

    public Player online(String id) {
        Actor a = roster.byId(id);
        return a == null ? null : Bukkit.getPlayerExact(a.username());
    }

    public Player killer() {
        Actor k = roster.killer();
        return k == null ? null : Bukkit.getPlayerExact(k.username());
    }

    public boolean killerStunned() {
        return System.currentTimeMillis() < killerStunnedUntil;
    }

    public Map<UUID, UUID> carries() { return carries; }

    // ---------- run ----------

    public void startRun(String runId, long seed) {
        state.run_id = runId;
        state.seed = seed;
        state.stun_index = 0;
        stun = new StunRoller(seed, 0);
        lives.reset(roster.livesIds());
        gate.disarm();
        killerStunnedUntil = 0;
        for (UUID rider : new java.util.ArrayList<>(carries.keySet())) drop(rider, "run_start");
        drips.clear();
        deathLogged.clear();
        outbox.setRun(runId);
        save();
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("seed", Long.toString(seed));
        f.put("lives", lives.snapshot());
        outbox.emit("run_start", f);
        for (Player p : Bukkit.getOnlinePlayers()) applyTier(p, true);
    }

    public void save() {
        state.lives = lives.snapshot();
        state.stun_index = stun.nextIndex();
        StateStore.State snap = state.copy();
        outbox.submit(() -> store.save(snap));
    }

    // ---------- lives and wounds ----------

    /** Makes health and effects match lives. Health is only ever lowered, except by a new run. */
    public void applyTier(Player p) {
        applyTier(p, false);
    }

    private void applyTier(Player p, boolean newRun) {
        Actor a = actor(p);
        if (a == null || !a.hasLives() || p.isDead()) return;
        int n = lives.get(a.id());
        if (n <= 0) return; // dead; step 2 owns what happens next
        Lives.Tier t = lives.tier(n);

        double max = 20;
        AttributeInstance attr = p.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) max = attr.getValue();
        double hp = Math.min(t.health(), max);
        if (p.getHealth() > hp + 0.01 || (newRun && Math.abs(p.getHealth() - hp) > 0.01)) p.setHealth(hp);

        PotionEffect cur = p.getPotionEffect(PotionEffectType.SLOWNESS);
        if (t.slownessAmplifier() < 0) {
            if (cur != null && cur.isInfinite()) p.removePotionEffect(PotionEffectType.SLOWNESS);
        } else if (cur == null || cur.getAmplifier() != t.slownessAmplifier() || !cur.isInfinite()) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION,
                    t.slownessAmplifier(), false, false, true));
        }

        if (t.noSprint()) {
            if (p.getFoodLevel() > FOOD_NO_SPRINT) p.setFoodLevel(FOOD_NO_SPRINT);
            if (p.isSprinting()) p.setSprinting(false);
        }
    }

    public boolean noSprint(Player p) {
        Actor a = actor(p);
        return a != null && a.hasLives() && lives.get(a.id()) == 1;
    }

    /** A killer swing connected with a camper. The gate decides if it counts. */
    public void onKillerSwing(Player killer, Player victim, Actor va) {
        HitGate.Result r = gate.tryHit(va.id(), lives.get(va.id()), killerStunned(), System.currentTimeMillis());
        if (r != HitGate.Result.LAND) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("target", va.id());
            f.put("reason", r.name().toLowerCase());
            f.put("armed_target", gate.target());
            f.put("armed_remaining", gate.remaining());
            outbox.emit("hit_blocked", f);
            return;
        }
        landHit(victim, va, "killer", killer);
    }

    /** Removes one life with full feedback. source: killer or director. */
    public void landHit(Player victim, Actor va, String source, Player killer) {
        int before = lives.get(va.id());
        int after = lives.lose(va.id());
        save();

        Location loc = victim.getLocation();
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("target", va.id());
        f.put("source", source);
        f.put("lives_before", before);
        f.put("lives_after", after);
        f.put("armed_remaining", gate.remaining());
        putPos(f, loc);
        outbox.emit("hit", f);

        victim.playHurtAnimation(loc.getYaw());
        loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_HURT, 1f, 1f);
        loc.getWorld().spawnParticle(Particle.BLOCK, loc.clone().add(0, 1, 0), 30, 0.25, 0.4, 0.25,
                Material.REDSTONE_BLOCK.createBlockData());
        if (killer != null) {
            Vector push = loc.toVector().subtract(killer.getLocation().toVector()).setY(0);
            if (push.lengthSquared() > 1e-4) victim.setVelocity(push.normalize().multiply(0.4).setY(0.25));
        }

        if (after <= 0) {
            dropInvolving(victim.getUniqueId(), "died");
            // Next tick: killing inside the attack's own damage event ran the death twice.
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (victim.isOnline() && !victim.isDead()) kill(victim, source);
            });
        } else {
            applyTier(victim);
        }
    }

    /**
     * Kills through the normal damage path with no damager, so the death message never names
     * the killer. Never call from inside a damage event (see landHit).
     */
    private void kill(Player victim, String cause) {
        UUID id = victim.getUniqueId();
        deathCause.put(id, cause);
        lethal.add(id);
        try {
            victim.damage(1000.0);
        } finally {
            lethal.remove(id);
        }
        if (!victim.isDead()) {
            plugin.getLogger().warning(victim.getName() + " survived the lethal hit; forcing death");
            victim.setHealth(0);
        }
    }

    /** True once for our own killing blow, so the damage listener lets it through. */
    public boolean takeLethal(Player p) {
        return lethal.remove(p.getUniqueId());
    }

    /** Called from the death event for any camper death (killer hit or void). */
    public void onDeath(Player p, String cause) {
        Actor a = actor(p);
        if (a == null) return;
        dropInvolving(p.getUniqueId(), "died");
        String ours = deathCause.remove(p.getUniqueId());
        if (ours != null) cause = ours;
        if (!a.hasLives() || !deathLogged.add(a.id())) return;
        if (lives.get(a.id()) > 0) {
            lives.kill(a.id());
            save();
        }
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("target", a.id());
        f.put("cause", cause);
        putPos(f, p.getLocation());
        outbox.emit("death", f);
    }

    // ---------- stun ----------

    /** A camper or Alex hit the killer. */
    public void onKillerStruck(Player by, Actor ba, Player killer) {
        if (killerStunned()) {
            outbox.emit("killer_struck", Map.of("by", ba.id(), "result", "already_stunned"));
            return;
        }
        StunRoller.Roll roll = stun.next(stunChance);
        save();
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("by", ba.id());
        f.put("seed", Long.toString(stun.seed()));
        f.put("index", roll.index());
        f.put("roll", roll.value());
        f.put("chance", roll.chance());
        f.put("stunned", roll.stunned());
        outbox.emit("stun_roll", f);
        if (roll.stunned()) stun(killer, "roll", ba.id());
    }

    public void stun(Player killer, String source, String by) {
        long now = System.currentTimeMillis();
        killerStunnedUntil = now + stunMs;
        if (killer != null) {
            int ticks = (int) Math.max(1, stunMs / 50);
            killer.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 254, false, false, false));
            killer.setVelocity(new Vector(0, Math.min(0, killer.getVelocity().getY()), 0));
            Location l = killer.getLocation();
            l.getWorld().playSound(l, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.6f);
        }
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("source", source);
        f.put("by", by);
        f.put("seconds", stunMs / 1000.0);
        f.put("until", java.time.Instant.ofEpochMilli(killerStunnedUntil).toString());
        outbox.emit("stun", f);
    }

    // ---------- killer visibility ----------

    public void setKillerHidden(boolean hidden) {
        state.killer_hidden = hidden;
        save();
        for (Player p : Bukkit.getOnlinePlayers()) applyKillerVisibility(p);
        Player k = killer();
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("hidden", hidden);
        if (k != null) putPos(f, k.getLocation());
        outbox.emit("killer_visibility", f);
    }

    /** Campers and Alex see the killer only when shown. Police and others always see him. */
    public void applyKillerVisibility(Player viewer) {
        Player k = killer();
        if (k == null || viewer.equals(k)) return;
        Actor va = actor(viewer);
        boolean hide = state.killer_hidden && va != null && va.hasLives();
        if (hide) viewer.hidePlayer(plugin, k);
        else viewer.showPlayer(plugin, k);
    }

    // ---------- carry ----------

    public boolean carry(Player carrier, Player rider) {
        if (carrier.equals(rider)) return false;
        dropInvolving(rider.getUniqueId(), "recarry");
        if (rider.isInsideVehicle()) rider.leaveVehicle();
        if (!carrier.addPassenger(rider)) return false;
        carries.put(rider.getUniqueId(), carrier.getUniqueId());
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("carrier", idOf(carrier));
        f.put("rider", idOf(rider));
        putPos(f, carrier.getLocation());
        outbox.emit("carry", f);
        return true;
    }

    public void drop(UUID rider, String reason) {
        UUID carrierId = carries.remove(rider);
        if (carrierId == null) return;
        Player r = Bukkit.getPlayer(rider);
        Player c = Bukkit.getPlayer(carrierId);
        if (c != null && r != null) c.removePassenger(r);
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("carrier", c == null ? carrierId.toString() : idOf(c));
        f.put("rider", r == null ? rider.toString() : idOf(r));
        f.put("reason", reason);
        if (r != null) putPos(f, r.getLocation());
        outbox.emit("drop", f);
    }

    /** Ends any carry this player is part of, as rider or carrier. */
    public void dropInvolving(UUID player, String reason) {
        if (carries.containsKey(player)) drop(player, reason);
        for (Map.Entry<UUID, UUID> e : new java.util.ArrayList<>(carries.entrySet())) {
            if (e.getValue().equals(player)) drop(e.getKey(), reason);
        }
    }

    /** Puts a rider back on after any dismount (sneak, teleport). */
    public void remount(UUID rider) {
        UUID carrierId = carries.get(rider);
        if (carrierId == null) return;
        Player r = Bukkit.getPlayer(rider);
        Player c = Bukkit.getPlayer(carrierId);
        if (r == null || c == null || r.isDead() || c.isDead()) {
            drop(rider, "lost");
            return;
        }
        if (c.getPassengers().contains(r)) return;
        r.teleport(c.getLocation());
        if (!c.addPassenger(r)) drop(rider, "remount_failed");
    }

    // ---------- ticks ----------

    /** Every tick: position snapshot for async chat, and sprint lock. */
    public void tickFast() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location l = p.getLocation();
            positions.put(p.getUniqueId(), new Proximity.Pos(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(),
                    p.getGameMode() == GameMode.SPECTATOR));
            if (p.isSprinting() && noSprint(p)) p.setSprinting(false);
        }
    }

    /** Every second: re-assert tiers (milk, relog) and redraw the blood trail. */
    public void tickSecond() {
        for (Player p : Bukkit.getOnlinePlayers()) applyTier(p);
        long now = System.currentTimeMillis();
        while (!drips.isEmpty() && now - drips.peekFirst().at() > trailMs) drips.pollFirst();
        for (Iterator<Drip> it = drips.iterator(); it.hasNext(); ) {
            Drip d = it.next();
            d.world().spawnParticle(Particle.DUST, d.x(), d.y(), d.z(), 2, 0.08, 0.0, 0.08, 0, BLOOD);
        }
    }

    /** Every drip interval: bleeding campers leave a drop where they stand. */
    public void tickBlood() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Actor a = actor(p);
            if (a == null || !a.hasLives() || p.isDead() || p.getGameMode() == GameMode.SPECTATOR) continue;
            if (!lives.tier(lives.get(a.id())).bleeding()) continue;
            Location l = p.getLocation();
            Drip d = new Drip(l.getWorld(), l.getX(), l.getY() + 0.05, l.getZ(), now);
            drips.addLast(d);
            if (drips.size() > MAX_DRIPS) drips.pollFirst();
            l.getWorld().spawnParticle(Particle.DUST, l.getX(), l.getY() + 0.6, l.getZ(), 4, 0.15, 0.3, 0.15, 0, BLOOD);
        }
    }

    public void forget(Player p) {
        positions.remove(p.getUniqueId());
    }

    // ---------- helpers ----------

    public String idOf(Player p) {
        Actor a = actor(p);
        return a == null ? p.getName() : a.id();
    }

    public static void putPos(Map<String, Object> f, Location l) {
        if (l == null) return;
        f.put("x", round(l.getX()));
        f.put("y", round(l.getY()));
        f.put("z", round(l.getZ()));
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }

    public boolean isRole(Player p, Role role) {
        Actor a = actor(p);
        return a != null && a.role() == role;
    }
}
