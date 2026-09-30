package com.horrorcraft.lightsout;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * state.json: run id, seed, stun index, lives, killer visibility. The plugin is the only writer;
 * each save writes a temp file and renames it over the old one, keeping the previous as .bak.
 */
public final class StateStore {

    public static final class State {
        public String run_id;
        public long seed;
        public long stun_index;
        public Map<String, Integer> lives = new LinkedHashMap<>();
        public boolean killer_hidden = true;

        public State copy() {
            State s = new State();
            s.run_id = run_id;
            s.seed = seed;
            s.stun_index = stun_index;
            s.lives = new LinkedHashMap<>(lives);
            s.killer_hidden = killer_hidden;
            return s;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final Path bak;
    private final Path tmp;
    private final Logger log;

    public StateStore(Path dir, Logger log) {
        this.file = dir.resolve("state.json");
        this.bak = dir.resolve("state.json.bak");
        this.tmp = dir.resolve("state.json.tmp");
        this.log = log;
    }

    /** Never throws. Missing: fresh state. Corrupt: the .bak. Both bad: fresh state, loudly. */
    public State load() {
        State s = read(file);
        if (s != null) return s;
        if (Files.exists(file)) log.warning("state.json unreadable, trying state.json.bak");
        s = read(bak);
        if (s != null) return s;
        if (Files.exists(file) || Files.exists(bak)) {
            log.severe("state.json and .bak both unreadable; starting with no run. Check lives before playing.");
        }
        return new State();
    }

    private State read(Path p) {
        if (!Files.exists(p)) return null;
        try {
            State s = GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), State.class);
            if (s == null) return null;
            if (s.lives == null) s.lives = new LinkedHashMap<>();
            return s;
        } catch (IOException | JsonParseException e) {
            log.log(Level.WARNING, "could not read " + p, e);
            return null;
        }
    }

    /** Call on the writer thread only. */
    public void save(State s) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(tmp, GSON.toJson(s), StandardCharsets.UTF_8);
            if (Files.exists(file)) Files.copy(file, bak, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.log(Level.WARNING, "state save failed", e);
        }
    }
}
