package com.horrorcraft.lightsout;

import org.junit.jupiter.api.Test;

import static com.horrorcraft.lightsout.HitGate.Result.*;
import static org.junit.jupiter.api.Assertions.*;

class HitGateTest {

    @Test
    void unarmedKillerLandsNothing() {
        HitGate g = new HitGate(1500);
        assertEquals(NOT_ARMED, g.tryHit("josh", 3, false, 0));
    }

    @Test
    void onlyTheArmedTargetCanBeHit() {
        HitGate g = new HitGate(1500);
        g.arm("mara", 2);
        assertEquals(WRONG_TARGET, g.tryHit("josh", 3, false, 0));
        assertEquals(LAND, g.tryHit("mara", 3, false, 0));
    }

    @Test
    void hitCapIsEnforced() {
        HitGate g = new HitGate(1500);
        g.arm("tyler", 2);
        assertEquals(LAND, g.tryHit("tyler", 3, false, 0));
        assertEquals(LAND, g.tryHit("tyler", 2, false, 2000));
        assertEquals(CAP_REACHED, g.tryHit("tyler", 1, false, 4000));
        assertEquals(0, g.remaining());
    }

    @Test
    void oneSwingCannotCountTwice() {
        HitGate g = new HitGate(1500);
        g.arm("dane", 3);
        assertEquals(LAND, g.tryHit("dane", 3, false, 1000));
        assertEquals(TOO_SOON, g.tryHit("dane", 2, false, 1200));
        assertEquals(TOO_SOON, g.tryHit("dane", 2, false, 2499));
        assertEquals(LAND, g.tryHit("dane", 2, false, 2500));
        assertEquals(1, g.remaining()); // blocked swings did not use up the cap
    }

    @Test
    void humanIsArmedTheSameWay() {
        HitGate g = new HitGate(1500);
        assertEquals(NOT_ARMED, g.tryHit("alex", 3, false, 0));
        g.arm("alex", 1);
        assertEquals(LAND, g.tryHit("alex", 3, false, 0));
        assertEquals(CAP_REACHED, g.tryHit("alex", 2, false, 5000));
    }

    @Test
    void stunnedKillerCannotHit() {
        HitGate g = new HitGate(1500);
        g.arm("josh", 1);
        assertEquals(STUNNED, g.tryHit("josh", 3, true, 0));
        assertEquals(1, g.remaining());
    }

    @Test
    void deadCannotBeHit() {
        HitGate g = new HitGate(1500);
        g.arm("josh", 1);
        assertEquals(DEAD, g.tryHit("josh", 0, false, 0));
    }

    @Test
    void disarmStopsHits() {
        HitGate g = new HitGate(1500);
        g.arm("josh", 3);
        g.disarm();
        assertEquals(NOT_ARMED, g.tryHit("josh", 3, false, 0));
    }

    @Test
    void armRejectsOutOfRangeHits() {
        HitGate g = new HitGate(1500);
        assertThrows(IllegalArgumentException.class, () -> g.arm("josh", 0));
        assertThrows(IllegalArgumentException.class, () -> g.arm("josh", 4));
    }
}
