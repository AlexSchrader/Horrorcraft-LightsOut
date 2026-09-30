package com.horrorcraft.lightsout;

import java.util.List;

/**
 * The director camera's cut logic. Pure: the caller builds a Situation from the world.
 * Priority: chase, reveal, discovery, closest camper to the killer, rotation.
 * Every shot is held at least minShotMs unless its subject is released (died, left).
 */
public final class AutoDirector {

    /** A living camper or the killer, where they are. */
    public record Subject(String id, String world, double x, double y, double z) {}

    /** living: living campers in roster order. killer: null when offline. Cue ids may be null. */
    public record Situation(long now, List<Subject> living, Subject killer,
                            String chase, String reveal, String discovery) {}

    public record Shot(String target, String reason) {}

    private final long minShotMs;
    private final long rotateMs;
    private final double nearRadius;

    private String current;
    private String reason;
    private long shotStart;
    private long rotateSince;

    public AutoDirector(long minShotMs, long rotateMs, double nearRadius) {
        this.minShotMs = minShotMs;
        this.rotateMs = rotateMs;
        this.nearRadius = nearRadius;
    }

    public String current() { return current; }
    public String reason() { return reason; }

    /** The subject died or left: the next decide() may cut at once. */
    public void release() {
        current = null;
        reason = null;
    }

    /** Returns a cut, or null to stay on the current shot. */
    public Shot decide(Situation s) {
        Shot want = want(s);
        if (want == null) return null;

        if ("rotate".equals(want.reason())) return rotate(s, want);

        if (want.target().equals(current)) {
            reason = want.reason();
            return null;
        }
        if (held(s)) return null;
        return cut(want, s.now());
    }

    /** What the director would like to show, ignoring the minimum shot length. */
    Shot want(Situation s) {
        if (isLiving(s, s.chase())) return new Shot(s.chase(), "chase");
        if (isLiving(s, s.reveal())) return new Shot(s.reveal(), "reveal");
        if (isLiving(s, s.discovery())) return new Shot(s.discovery(), "discovery");
        if (s.killer() != null) {
            Subject best = null;
            double bestD = nearRadius * nearRadius;
            for (Subject c : s.living()) {
                if (!c.world().equals(s.killer().world())) continue;
                double d = distSq(c, s.killer());
                if (d <= bestD) {
                    bestD = d;
                    best = c;
                }
            }
            if (best != null) return new Shot(best.id(), "closest");
        }
        if (!s.living().isEmpty()) return new Shot(null, "rotate");
        if (s.killer() != null) return new Shot(s.killer().id(), "killer");
        return null;
    }

    private Shot rotate(Situation s, Shot want) {
        List<Subject> living = s.living();
        int i = indexOf(living, current);
        if (i >= 0) {
            if (!"rotate".equals(reason)) {
                // Nothing near the killer any more: rotation starts on the current shot.
                reason = "rotate";
                rotateSince = s.now();
                return null;
            }
            if (s.now() - rotateSince < rotateMs || living.size() < 2) return null;
            rotateSince = s.now();
            return cut(new Shot(living.get((i + 1) % living.size()).id(), "rotate"), s.now());
        }
        if (held(s)) return null;
        rotateSince = s.now();
        return cut(new Shot(living.get(0).id(), "rotate"), s.now());
    }

    /** The current shot is still valid and younger than the minimum. */
    private boolean held(Situation s) {
        boolean valid = current != null && (indexOf(s.living(), current) >= 0
                || (s.killer() != null && current.equals(s.killer().id())));
        return valid && s.now() - shotStart < minShotMs;
    }

    private Shot cut(Shot shot, long now) {
        current = shot.target();
        reason = shot.reason();
        shotStart = now;
        return shot;
    }

    private static boolean isLiving(Situation s, String id) {
        return id != null && indexOf(s.living(), id) >= 0;
    }

    private static int indexOf(List<Subject> list, String id) {
        if (id == null) return -1;
        for (int i = 0; i < list.size(); i++) if (list.get(i).id().equals(id)) return i;
        return -1;
    }

    private static double distSq(Subject a, Subject b) {
        double dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
