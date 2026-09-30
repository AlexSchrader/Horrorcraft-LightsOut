package com.horrorcraft.lightsout;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LivesTest {

    private static final double[] HP = {0, 6.0, 13.0, 20.0};
    private static final List<String> IDS = List.of("josh", "dane", "mara", "tyler", "alex");

    @Test
    void everyoneStartsWithThree() {
        Lives l = new Lives(HP);
        l.reset(IDS);
        for (String id : IDS) assertEquals(3, l.get(id));
    }

    @Test
    void tiersMatchSpec() {
        Lives l = new Lives(HP);
        Lives.Tier full = l.tier(3);
        assertEquals(20.0, full.health());
        assertEquals(-1, full.slownessAmplifier());
        assertFalse(full.noSprint());
        assertFalse(full.bleeding());

        Lives.Tier hurt = l.tier(2);
        assertEquals(13.0, hurt.health());      // ~6.5 hearts
        assertEquals(0, hurt.slownessAmplifier()); // Slowness I
        assertFalse(hurt.noSprint());
        assertFalse(hurt.bleeding());

        Lives.Tier bad = l.tier(1);
        assertEquals(6.0, bad.health());        // ~3 hearts
        assertEquals(1, bad.slownessAmplifier()); // Slowness II
        assertTrue(bad.noSprint());
        assertTrue(bad.bleeding());

        assertEquals(0, l.tier(0).health());
    }

    @Test
    void livesOnlyGoDownAndStopAtZero() {
        Lives l = new Lives(HP);
        l.reset(IDS);
        assertEquals(2, l.lose("josh"));
        assertEquals(1, l.lose("josh"));
        assertEquals(0, l.lose("josh"));
        assertEquals(0, l.lose("josh"));
        assertEquals(3, l.get("dane"));
    }

    @Test
    void restoreClampsAndNeverExceedsMax() {
        Lives l = new Lives(HP);
        l.restore(IDS, Map.of("josh", 1, "dane", 9, "mara", -4));
        assertEquals(1, l.get("josh"));
        assertEquals(3, l.get("dane"));
        assertEquals(0, l.get("mara"));
        assertEquals(3, l.get("tyler")); // missing from save: full
    }

    @Test
    void restoreFromNothingIsFull() {
        Lives l = new Lives(HP);
        l.restore(IDS, null);
        assertEquals(Map.of("josh", 3, "dane", 3, "mara", 3, "tyler", 3, "alex", 3), l.snapshot());
    }
}
