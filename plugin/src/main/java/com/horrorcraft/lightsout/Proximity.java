package com.horrorcraft.lightsout;

/** Who can hear whom. Pure; positions are snapshots taken on the main thread. */
public final class Proximity {

    public record Pos(String world, double x, double y, double z, boolean spectator) {}

    /** A chat line after the '*' marker is handled. */
    public record Line(String text, boolean voiced) {}

    private Proximity() {}

    public static boolean hears(Pos speaker, Pos listener, double radius) {
        if (speaker == null || listener == null) return false;
        if (!speaker.world().equals(listener.world())) return false;
        // The dead talk only to the dead.
        if (speaker.spectator() && !listener.spectator()) return false;
        double dx = speaker.x() - listener.x();
        double dy = speaker.y() - listener.y();
        double dz = speaker.z() - listener.z();
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    /** A leading '*' means text-only: shown in chat, never voiced. */
    public static Line parse(String raw) {
        String s = raw == null ? "" : raw.strip();
        if (s.startsWith("*")) return new Line(s.substring(1).strip(), false);
        return new Line(s, true);
    }
}
