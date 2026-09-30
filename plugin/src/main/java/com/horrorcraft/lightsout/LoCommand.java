package com.horrorcraft.lightsout;

import com.horrorcraft.lightsout.Roster.Actor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** /lo: director controls, used over RCON by the bridge and by ops for testing. */
public final class LoCommand implements TabExecutor {

    private static final List<String> SUBS =
            List.of("run", "status", "arm", "disarm", "hit", "stun", "carry", "drop", "killer");

    private final Game game;

    public LoCommand(Game game) {
        this.game = game;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length == 0) return usage(s);
        try {
            switch (a[0].toLowerCase()) {
                case "run" -> run(s, a);
                case "status" -> status(s);
                case "arm" -> arm(s, a);
                case "disarm" -> {
                    game.gate().disarm();
                    game.outbox().emit("disarm", Map.of());
                    s.sendMessage("ok disarmed");
                }
                case "hit" -> hit(s, a);
                case "stun" -> {
                    game.stun(game.killer(), "director", null);
                    s.sendMessage("ok stunned");
                }
                case "carry" -> carry(s, a);
                case "drop" -> drop(s, a);
                case "killer" -> killer(s, a);
                default -> usage(s);
            }
        } catch (RuntimeException e) {
            s.sendMessage("error " + e.getMessage());
        }
        return true;
    }

    private boolean usage(CommandSender s) {
        s.sendMessage("usage: /lo run <run_id> <seed> | status | arm <target> <1-3> | disarm | hit <camper> | "
                + "stun | carry <carrier> <rider> | drop <rider> | killer <hide|show>");
        return true;
    }

    private void run(CommandSender s, String[] a) {
        if (a.length != 3) { s.sendMessage("usage: /lo run <run_id> <seed>"); return; }
        long seed;
        try {
            seed = Long.parseLong(a[2]);
        } catch (NumberFormatException e) {
            s.sendMessage("error seed must be a 64-bit integer");
            return;
        }
        game.startRun(a[1], seed);
        s.sendMessage("ok run " + a[1] + " seed " + seed + " lives reset");
    }

    private void status(CommandSender s) {
        StateStore.State st = game.state();
        s.sendMessage("run " + st.run_id + " seed " + st.seed + " stun_index " + game.state().stun_index);
        s.sendMessage("killer " + (st.killer_hidden ? "hidden" : "shown")
                + (game.killerStunned() ? " STUNNED" : "")
                + (game.killer() == null ? " (offline)" : ""));
        s.sendMessage("armed " + (game.gate().target() == null ? "no" : game.gate().target() + " x" + game.gate().remaining()));
        StringBuilder lives = new StringBuilder("lives");
        for (Map.Entry<String, Integer> e : game.lives().snapshot().entrySet()) {
            Player p = game.online(e.getKey());
            lives.append(' ').append(e.getKey()).append('=').append(e.getValue());
            if (p != null) lives.append("(").append(String.format("%.1f", p.getHealth())).append("hp)");
        }
        s.sendMessage(lives.toString());
        StringBuilder carries = new StringBuilder("carries");
        for (Map.Entry<UUID, UUID> e : game.carries().entrySet()) {
            Player r = Bukkit.getPlayer(e.getKey());
            Player c = Bukkit.getPlayer(e.getValue());
            carries.append(' ').append(c == null ? "?" : game.idOf(c)).append('>').append(r == null ? "?" : game.idOf(r));
        }
        s.sendMessage(carries.toString());
    }

    private void arm(CommandSender s, String[] a) {
        if (a.length != 3) { s.sendMessage("usage: /lo arm <target> <1-3>"); return; }
        Actor t = livesActor(a[1]);
        if (t == null) { s.sendMessage("error unknown camper " + a[1]); return; }
        int hits = Integer.parseInt(a[2]);
        game.gate().arm(t.id(), hits);
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("target", t.id());
        f.put("hits", hits);
        game.outbox().emit("arm", f);
        s.sendMessage("ok armed " + t.id() + " x" + hits);
    }

    private void hit(CommandSender s, String[] a) {
        if (a.length != 2) { s.sendMessage("usage: /lo hit <camper>"); return; }
        Actor t = livesActor(a[1]);
        if (t == null) { s.sendMessage("error unknown camper " + a[1]); return; }
        Player p = game.online(t.id());
        if (p == null) { s.sendMessage("error " + t.id() + " is offline"); return; }
        if (game.lives().get(t.id()) <= 0) { s.sendMessage("error " + t.id() + " is dead"); return; }
        game.landHit(p, t, "director", null);
        s.sendMessage("ok " + t.id() + " lives " + game.lives().get(t.id()));
    }

    private void carry(CommandSender s, String[] a) {
        if (a.length != 3) { s.sendMessage("usage: /lo carry <carrier> <rider>"); return; }
        Player c = player(a[1]);
        Player r = player(a[2]);
        if (c == null || r == null) { s.sendMessage("error both must be online"); return; }
        s.sendMessage(game.carry(c, r) ? "ok carrying" : "error could not mount");
    }

    private void drop(CommandSender s, String[] a) {
        if (a.length != 2) { s.sendMessage("usage: /lo drop <rider>"); return; }
        Player r = player(a[1]);
        if (r == null || !game.carries().containsKey(r.getUniqueId())) { s.sendMessage("error not carried"); return; }
        game.drop(r.getUniqueId(), "director");
        s.sendMessage("ok dropped");
    }

    private void killer(CommandSender s, String[] a) {
        if (a.length != 2 || !(a[1].equalsIgnoreCase("hide") || a[1].equalsIgnoreCase("show"))) {
            s.sendMessage("usage: /lo killer <hide|show>");
            return;
        }
        boolean hide = a[1].equalsIgnoreCase("hide");
        game.setKillerHidden(hide);
        s.sendMessage("ok killer " + (hide ? "hidden" : "shown"));
    }

    /** Accepts an actor id (josh) or a username (Josh, ACSsnipertroll). */
    private Actor livesActor(String name) {
        Actor t = game.roster().byId(name);
        if (t == null) t = game.roster().byUsername(name);
        return t != null && t.hasLives() ? t : null;
    }

    private Player player(String name) {
        Actor t = game.roster().byId(name);
        if (t != null) return game.online(t.id());
        return Bukkit.getPlayerExact(name);
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length == 1) return filter(SUBS, a[0]);
        String sub = a[0].toLowerCase();
        if (a.length == 2 && sub.equals("killer")) return filter(List.of("hide", "show"), a[1]);
        if ((a.length == 2 && List.of("arm", "hit", "carry", "drop").contains(sub)) || (a.length == 3 && sub.equals("carry"))) {
            List<String> ids = new ArrayList<>();
            for (Actor x : game.roster().all()) ids.add(x.id());
            return filter(ids, a[a.length - 1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.startsWith(prefix.toLowerCase())) out.add(o);
        return out;
    }
}
