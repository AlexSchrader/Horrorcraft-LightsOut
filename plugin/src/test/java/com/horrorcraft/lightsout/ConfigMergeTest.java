package com.horrorcraft.lightsout;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** An old config.yml (no cameras) gets the new camera entries, with follow values readable. */
class ConfigMergeTest {

    private static final String OLD = """
            world: lightsout
            actors:
              Josh: {id: josh, role: camper}
              Hollow: {id: killer, role: killer}
            """;

    private static YamlConfiguration defaults() throws Exception {
        try (var in = ConfigMergeTest.class.getResourceAsStream("/config.yml")) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void mergedThenReloadedConfigHasCameraFollow(@TempDir Path dir) throws Exception {
        File f = dir.resolve("config.yml").toFile();
        Files.writeString(f.toPath(), OLD);

        // What onEnable does: load, copy defaults, save, reload.
        YamlConfiguration c = YamlConfiguration.loadConfiguration(f);
        c.setDefaults(defaults());
        c.options().copyDefaults(true);
        c.save(f);
        YamlConfiguration reloaded = YamlConfiguration.loadConfiguration(f);

        assertEquals("auto", reloaded.getString("actors.Camera.follow", "free"));
        assertEquals("Hollow", reloaded.getString("actors.KillerCam.follow", "free"));
        assertEquals("camera", reloaded.getString("actors.CamJosh.role"));
        assertEquals(8.0, reloaded.getDouble("cameras.min-shot-seconds", -1));
        assertEquals("josh", reloaded.getString("actors.Josh.id")); // existing entries kept
    }

    @Test
    void withoutReloadFollowFallsBack(@TempDir Path dir) throws Exception {
        // The bug: before the reload, getString(path, fallback) ignores the jar defaults.
        File f = dir.resolve("config.yml").toFile();
        Files.writeString(f.toPath(), OLD);
        YamlConfiguration c = YamlConfiguration.loadConfiguration(f);
        c.setDefaults(defaults());
        c.options().copyDefaults(true);
        assertEquals("free", c.getString("actors.Camera.follow", "free"));
    }
}
