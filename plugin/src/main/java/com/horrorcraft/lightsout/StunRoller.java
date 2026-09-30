package com.horrorcraft.lightsout;

/**
 * Stun rolls derived from the run seed. Roll n depends only on (seed, n), so a run can be
 * replayed from the logged index regardless of what else happened.
 */
public final class StunRoller {

    public record Roll(long index, double value, double chance, boolean stunned) {}

    // Keeps stun rolls independent of any other stream derived from the same run seed.
    private static final long SALT = 0x5354554E5354554EL; // "STUNSTUN"

    private final long seed;
    private long index;

    public StunRoller(long seed, long nextIndex) {
        this.seed = seed;
        this.index = nextIndex;
    }

    public long seed() {
        return seed;
    }

    public long nextIndex() {
        return index;
    }

    public Roll next(double chance) {
        long i = index++;
        double v = valueAt(seed, i);
        return new Roll(i, v, chance, v < chance);
    }

    /** Uniform in [0, 1). */
    public static double valueAt(long seed, long index) {
        return (mix64((seed ^ SALT) + 0x9E3779B97F4A7C15L * (index + 1)) >>> 11) * 0x1.0p-53;
    }

    // SplitMix64 finalizer.
    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
