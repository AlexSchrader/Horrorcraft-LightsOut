package com.horrorcraft.lightsout;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class LightsOutPlugin extends JavaPlugin {

    private Outbox outbox;
    private Game game;

    @Override
    public void onEnable() {
        saveDefaultConfig();
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

        Bukkit.getPluginManager().registerEvents(new GameListener(this, game), this);
        PluginCommand lo = getCommand("lo");
        if (lo != null) {
            LoCommand cmd = new LoCommand(game);
            lo.setExecutor(cmd);
            lo.setTabCompleter(cmd);
        }

        int dripTicks = Math.max(1, c.getInt("blood.drip-ticks", 10));
        Bukkit.getScheduler().runTaskTimer(this, game::tickFast, 1, 1);
        Bukkit.getScheduler().runTaskTimer(this, game::tickSecond, 20, 20);
        Bukkit.getScheduler().runTaskTimer(this, game::tickBlood, dripTicks, dripTicks);

        // Reload-safe: bring anyone already online in line.
        for (Player p : Bukkit.getOnlinePlayers()) {
            game.applyKillerVisibility(p);
            game.applyTier(p);
        }

        outbox.emit("plugin_enable", java.util.Map.of("version", getPluginMeta().getVersion()));
        getLogger().info("LightsOut ready. run=" + state.run_id + " lives=" + game.lives().snapshot()
                + " killer_hidden=" + state.killer_hidden);
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

    @Override
    public void onDisable() {
        if (game != null) {
            for (java.util.UUID rider : new ArrayList<>(game.carries().keySet())) game.drop(rider, "shutdown");
            game.save();
        }
        if (outbox != null) outbox.close();
    }
}
