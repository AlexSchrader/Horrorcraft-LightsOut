package com.horrorcraft.lightsout;

import java.util.HashMap;
import java.util.Map;

/**
 * Decides whether a killer swing counts. The director arms the killer against one target
 * for a number of hits; everything else is a miss.
 */
public final class HitGate {

    public enum Result { LAND, STUNNED, DEAD, NOT_ARMED, WRONG_TARGET, CAP_REACHED, TOO_SOON }

    private final long spacingMs;
    private final Map<String, Long> lastHit = new HashMap<>();
    private String target;
    private int remaining;

    public HitGate(long spacingMs) {
        this.spacingMs = spacingMs;
    }

    public void arm(String target, int hits) {
        if (hits < 1 || hits > Lives.MAX) throw new IllegalArgumentException("hits must be 1..3");
        this.target = target;
        this.remaining = hits;
    }

    public void disarm() {
        target = null;
        remaining = 0;
    }

    public String target() {
        return target;
    }

    public int remaining() {
        return remaining;
    }

    public Result tryHit(String victim, int victimLives, boolean killerStunned, long nowMs) {
        if (killerStunned) return Result.STUNNED;
        if (victimLives <= 0) return Result.DEAD;
        if (target == null) return Result.NOT_ARMED;
        if (!target.equals(victim)) return Result.WRONG_TARGET;
        if (remaining <= 0) return Result.CAP_REACHED;
        Long last = lastHit.get(victim);
        if (last != null && nowMs - last < spacingMs) return Result.TOO_SOON;
        remaining--;
        lastHit.put(victim, nowMs);
        return Result.LAND;
    }
}
