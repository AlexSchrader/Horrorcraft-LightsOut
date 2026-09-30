package com.horrorcraft.lightsout;

import com.horrorcraft.lightsout.Proximity.Pos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProximityTest {

    private static Pos at(double x, double y, double z) {
        return new Pos("lightsout", x, y, z, false);
    }

    @Test
    void withinRadiusHears() {
        assertTrue(Proximity.hears(at(0, 64, 0), at(24, 64, 0), 24));
        assertTrue(Proximity.hears(at(0, 64, 0), at(10, 70, 10), 24));
    }

    @Test
    void beyondRadiusDoesNot() {
        assertFalse(Proximity.hears(at(0, 64, 0), at(24.01, 64, 0), 24));
        assertFalse(Proximity.hears(at(0, 64, 0), at(20, 64, 20), 24)); // ~28.3
    }

    @Test
    void heightCounts() {
        assertFalse(Proximity.hears(at(0, 64, 0), at(0, 100, 0), 24));
    }

    @Test
    void otherWorldDoesNot() {
        assertFalse(Proximity.hears(at(0, 64, 0), new Pos("lightsout_nether", 0, 64, 0, false), 24));
    }

    @Test
    void unknownPositionDoesNot() {
        assertFalse(Proximity.hears(at(0, 64, 0), null, 24));
        assertFalse(Proximity.hears(null, at(0, 64, 0), 24));
    }

    @Test
    void deadTalkOnlyToDead() {
        Pos ghost = new Pos("lightsout", 0, 64, 0, true);
        Pos ghost2 = new Pos("lightsout", 5, 64, 0, true);
        assertFalse(Proximity.hears(ghost, at(1, 64, 0), 24));
        assertTrue(Proximity.hears(ghost, ghost2, 24));
        assertTrue(Proximity.hears(at(1, 64, 0), ghost, 24)); // the dead still hear the living nearby
    }

    @Test
    void starMeansTextOnly() {
        assertEquals(new Proximity.Line("where is everyone", false), Proximity.parse("*where is everyone"));
        assertEquals(new Proximity.Line("hello", false), Proximity.parse("  * hello "));
        assertEquals(new Proximity.Line("hello", true), Proximity.parse("hello"));
        assertEquals(new Proximity.Line("a*b", true), Proximity.parse("a*b"));
        assertEquals(new Proximity.Line("", true), Proximity.parse(null));
    }
}
