package com.horrorcraft.lightsout;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Lives per camper. Lives only go down; only a new run resets them. */
public final class Lives {

    public static final int MAX = 3;

    /** What a number of lives means for the body. */
    public record Tier(int lives, double health, int slownessAmplifier, boolean noSprint, boolean bleeding) {}

    private final Map<String, Integer> lives = new LinkedHashMap<>();
    private final double[] healthByLives;

    /** healthByLives[n] = health at n lives, for n = 1..3. */
    public Lives(double[] healthByLives) {
        if (healthByLives.length != MAX + 1) throw new IllegalArgumentException("need health for 0..3 lives");
        this.healthByLives = healthByLives.clone();
    }

    public Tier tier(int n) {
        return switch (n) {
            case 3 -> new Tier(3, healthByLives[3], -1, false, false);
            case 2 -> new Tier(2, healthByLives[2], 0, false, false);   // Slowness I
            case 1 -> new Tier(1, healthByLives[1], 1, true, true);     // Slowness II, no sprint, blood
            default -> new Tier(0, 0, -1, false, false);
        };
    }

    public void reset(Collection<String> ids) {
        lives.clear();
        for (String id : ids) lives.put(id, MAX);
    }

    /** Loads saved lives; out-of-range or unknown values are clamped, never raised above MAX. */
    public void restore(Collection<String> ids, Map<String, Integer> saved) {
        reset(ids);
        if (saved == null) return;
        for (String id : ids) {
            Integer v = saved.get(id);
            if (v != null) lives.put(id, Math.max(0, Math.min(MAX, v)));
        }
    }

    public int get(String id) {
        return lives.getOrDefault(id, MAX);
    }

    /** Removes one life; returns what is left. */
    public int lose(String id) {
        int left = Math.max(0, get(id) - 1);
        lives.put(id, left);
        return left;
    }

    public void kill(String id) {
        lives.put(id, 0);
    }

    public Map<String, Integer> snapshot() {
        return new LinkedHashMap<>(lives);
    }
}
