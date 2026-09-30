package com.horrorcraft.lightsout;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LightsOutPlugin extends JavaPlugin {

    private Outbox outbox;
    private Game game;
    private Cameras cameras;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // Settings added by newer versions reach an existing config.yml. Reload afterwards:
        // getString(path, fallback) ignores jar defaults, so read the merged file, not defaults.
        getConfig().options().copyDefaults(true);
        saveConfig();
        reloadConfig();
        FileConfiguration c = getConfig();

        // Keep this plugin out of any other world (Polis).
        String want = c.getString("world", "lightsout");
        String have = Bukkit.getWorlds().isEmpty() ? "" : Bukkit.getWorlds().get(0).getName();
        if (!want.equals(have)) {
            getLogger().severe("Main world is '" + have + "', expected '" + want + "'. LightsOut disabled.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        Roster roster = loadRoster(c.getConfigurationSection("actors"));
        double[] hp = {0,
                c.getDouble("health.1", 6.0),
                c.getDouble("health.2", 13.0),
                c.getDouble("health.3", 20.0)};

        StateStore store = new StateStore(getDataFolder().toPath(), getLogger());
        StateStore.State state = store.load();
        outbox = new Outbox(getDataFolder().toPath().resolve("outbox"), getLogger(), state.run_id);

        game = new Game(this, roster, new Lives(hp),
                c.getLong("hit-spacing-ms", 1500),
                c.getDouble("chat-radius", 24),
                c.getDouble("stun.chance", 0.5),
                (long) (c.getDouble("stun.seconds", 3) * 1000),
                (long) (c.getDouble("blood.trail-seconds", 60) * 1000),
                outbox, store, state);

        cameras = new Cameras(this, game, new Cameras.Settings(
                seconds(c, "cameras.min-shot-seconds", 8),
                seconds(c, "cameras.rotate-seconds", 60),
                c.getDouble("cameras.near-radius", 60),
                c.getDouble("cameras.chase-radius", 20),
                seconds(c, "cameras.body-hold-seconds", 10),
                seconds(c, "cameras.cue-seconds", 20)),
                loadFollow(c.getConfigurationSection("actors"), roster));

        Bukkit.getPluginManager().registerEvents(new GameListener(this, game, cameras), this);
        PluginCommand lo = getCommand("lo");
        if (lo != null) {
            LoCommand cmd = new LoCommand(game, cameras);
            lo.setExecutor(cmd);
            lo.setTabCompleter(cmd);
        }

        int dripTicks = Math.max(1, c.getInt("blood.drip-ticks", 10));
        Bukkit.getScheduler().runTaskTimer(this, game::tickFast, 1, 1);
        Bukkit.getScheduler().runTaskTimer(this, game::tickSecond, 20, 20);
        Bukkit.getScheduler().runTaskTimer(this, game::tickBlood, dripTicks, dripTicks);
        int directorTicks = Math.max(1, (int) (c.getDouble("cameras.director-seconds", 2) * 20));
        int relockTicks = Math.max(1, (int) (c.getDouble("cameras.relock-seconds", 5) * 20));
        Bukkit.getScheduler().runTaskTimer(this, cameras::tickDirector, directorTicks, directorTicks);
        Bukkit.getScheduler().runTaskTimer(this, cameras::tickRelock, relockTicks, relockTicks);

        // Reload-safe: bring anyone already online in line.
        for (Player p : Bukkit.getOnlinePlayers()) {
            game.applyKillerVisibility(p);
            cameras.onJoin(p);
            game.applyTier(p);
        }

        outbox.emit("plugin_enable", java.util.Map.of("version", getPluginMeta().getVersion()));
        getLogger().info("LightsOut ready. run=" + state.run_id + " lives=" + game.lives().snapshot()
                + " killer_hidden=" + state.killer_hidden);
        getLogger().info("Cameras: " + String.join(", ", cameras.status()));
    }

    private Roster loadRoster(ConfigurationSection actors) {
        List<Roster.Actor> list = new ArrayList<>();
        if (actors == null) {
            getLogger().warning("config has no actors; nobody has lives");
            return new Roster(list);
        }
        for (String username : actors.getKeys(false)) {
            Roster.Actor a = Roster.parse(username, actors.getString(username + ".id"), actors.getString(username + ".role"));
            if (a == null) getLogger().warning("skipping bad actor entry: " + username);
            else list.add(a);
        }
        return new Roster(list);
    }

    /** Camera actor id -> "auto" or the username it follows. */
    private Map<String, String> loadFollow(ConfigurationSection actors, Roster roster) {
        Map<String, String> follow = new HashMap<>();
        if (actors == null) return follow;
        for (String username : actors.getKeys(false)) {
            Roster.Actor a = roster.byUsername(username);
            if (a != null && a.isCamera()) follow.put(a.id(), actors.getString(username + ".follow", "free"));
        }
        return follow;
    }

    private static long seconds(FileConfiguration c, String path, double def) {
        return (long) (c.getDouble(path, def) * 1000);
    }

    @Override
    public void onDisable() {
        if (game != null) {
            for (java.util.UUID rider : new ArrayList<>(game.carries().keySet())) game.drop(rider, "shutdown");
            game.save();
        }
        if (outbox != null) outbox.close();
    }
}
