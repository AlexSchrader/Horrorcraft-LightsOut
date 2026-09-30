package com.horrorcraft.lightsout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StunRollerTest {

    private static final long SEED = -1541124385142397106L;

    @Test
    void sameSeedSameRolls() {
        StunRoller a = new StunRoller(SEED, 0);
        StunRoller b = new StunRoller(SEED, 0);
        for (int i = 0; i < 100; i++) assertEquals(a.next(0.5), b.next(0.5));
    }

    @Test
    void rollDependsOnlyOnSeedAndIndex() {
        StunRoller fresh = new StunRoller(SEED, 0);
        for (int i = 0; i < 7; i++) fresh.next(0.5);
        StunRoller resumed = new StunRoller(SEED, 7); // e.g. after a restart, index from state.json
        assertEquals(fresh.next(0.5), resumed.next(0.5));
        assertEquals(StunRoller.valueAt(SEED, 8), resumed.next(0.5).value());
    }

    @Test
    void indexesAreLoggedInOrder() {
        StunRoller r = new StunRoller(SEED, 0);
        assertEquals(0, r.next(0.5).index());
        assertEquals(1, r.next(0.5).index());
        assertEquals(2, r.nextIndex());
    }

    @Test
    void differentSeedsDiffer() {
        int same = 0;
        for (int i = 0; i < 64; i++) {
            if (StunRoller.valueAt(SEED, i) == StunRoller.valueAt(SEED + 1, i)) same++;
        }
        assertEquals(0, same);
    }

    @Test
    void valuesAreUniformEnough() {
        int stunned = 0;
        int n = 100_000;
        StunRoller r = new StunRoller(SEED, 0);
        for (int i = 0; i < n; i++) {
            StunRoller.Roll roll = r.next(0.5);
            assertTrue(roll.value() >= 0 && roll.value() < 1);
            if (roll.stunned()) stunned++;
        }
        assertEquals(0.5, stunned / (double) n, 0.01);
    }

    @Test
    void stunnedMeansBelowChance() {
        StunRoller r = new StunRoller(SEED, 0);
        for (int i = 0; i < 200; i++) {
            StunRoller.Roll roll = r.next(0.3);
            assertEquals(roll.value() < 0.3, roll.stunned());
        }
    }
}
