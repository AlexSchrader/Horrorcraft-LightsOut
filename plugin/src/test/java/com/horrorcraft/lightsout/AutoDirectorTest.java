package com.horrorcraft.lightsout;

import com.horrorcraft.lightsout.AutoDirector.Shot;
import com.horrorcraft.lightsout.AutoDirector.Situation;
import com.horrorcraft.lightsout.AutoDirector.Subject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AutoDirectorTest {

    private static final long S = 1000;

    private static Subject at(String id, double x) {
        return new Subject(id, "lightsout", x, 89, 0);
    }

    private static final Subject KILLER = at("killer", 0);
    private static final Subject JOSH = at("josh", 10);
    private static final Subject DANE = at("dane", 30);
    private static final Subject MARA = at("mara", 50);
    private static final List<Subject> ALL = List.of(JOSH, DANE, MARA);

    private static AutoDirector director() {
        return new AutoDirector(8 * S, 60 * S, 60);
    }

    private static Situation sit(long now, List<Subject> living, Subject killer, String chase, String reveal, String discovery) {
        return new Situation(now, living, killer, chase, reveal, discovery);
    }

    private static Situation quiet(long now) {
        return sit(now, ALL, KILLER, null, null, null);
    }

    // ---------- priority ----------

    @Test
    void chaseBeatsEverything() {
        assertEquals(new Shot("mara", "chase"), director().decide(sit(0, ALL, KILLER, "mara", "dane", "josh")));
    }

    @Test
    void revealBeatsDiscoveryAndClosest() {
        assertEquals(new Shot("dane", "reveal"), director().decide(sit(0, ALL, KILLER, null, "dane", "mara")));
    }

    @Test
    void discoveryBeatsClosest() {
        assertEquals(new Shot("mara", "discovery"), director().decide(sit(0, ALL, KILLER, null, null, "mara")));
    }

    @Test
    void closestLivingCamperToKiller() {
        assertEquals(new Shot("josh", "closest"), director().decide(quiet(0)));
    }

    @Test
    void closestIgnoresOtherWorlds() {
        Subject joshNether = new Subject("josh", "lightsout_nether", 1, 89, 0);
        assertEquals(new Shot("dane", "closest"),
                director().decide(sit(0, List.of(joshNether, DANE, MARA), KILLER, null, null, null)));
    }

    @Test
    void cuesOnDeadCampersAreIgnored() {
        // Mara is not in the living list: her chase cue falls through to the next rule.
        assertEquals(new Shot("josh", "closest"),
                director().decide(sit(0, List.of(JOSH, DANE), KILLER, "mara", null, null)));
    }

    @Test
    void nothingWithin60BlocksRotates() {
        Subject far = at("killer", -500);
        assertEquals(new Shot("josh", "rotate"), director().decide(sit(0, ALL, far, null, null, null)));
    }

    @Test
    void killerOfflineRotates() {
        assertEquals(new Shot("josh", "rotate"), director().decide(sit(0, ALL, null, null, null, null)));
    }

    @Test
    void noLivingCampersFollowsKiller() {
        assertEquals(new Shot("killer", "killer"), director().decide(sit(0, List.of(), KILLER, null, null, null)));
    }

    @Test
    void nobodyAtAllStays() {
        assertNull(director().decide(sit(0, List.of(), null, null, null, null)));
    }

    // ---------- minimum shot ----------

    @Test
    void shotIsHeldAtLeast8Seconds() {
        AutoDirector d = director();
        assertEquals(new Shot("josh", "closest"), d.decide(quiet(0)));
        assertNull(d.decide(sit(2 * S, ALL, KILLER, "mara", null, null)));
        assertNull(d.decide(sit(7999, ALL, KILLER, "mara", null, null)));
        assertEquals(new Shot("mara", "chase"), d.decide(sit(8 * S, ALL, KILLER, "mara", null, null)));
    }

    @Test
    void sameSubjectIsNotACut() {
        AutoDirector d = director();
        assertNotNull(d.decide(quiet(0)));
        assertNull(d.decide(quiet(20 * S)));
        assertNull(d.decide(sit(30 * S, ALL, KILLER, "josh", null, null))); // chase on the same camper
        assertEquals("chase", d.reason());
    }

    @Test
    void noJitterBetweenTwoCloseCampers() {
        AutoDirector d = director();
        Subject a = at("josh", 10), b = at("dane", 11);
        assertEquals("josh", d.decide(sit(0, List.of(a, b), KILLER, null, null, null)).target());
        Subject a2 = at("josh", 12), b2 = at("dane", 11);
        assertNull(d.decide(sit(2 * S, List.of(a2, b2), KILLER, null, null, null)));
        assertNull(d.decide(sit(4 * S, List.of(a, b), KILLER, null, null, null)));
        assertEquals("dane", d.decide(sit(8 * S, List.of(a2, b2), KILLER, null, null, null)).target());
    }

    @Test
    void releasedSubjectCutsAtOnce() {
        AutoDirector d = director();
        assertEquals("josh", d.decide(quiet(0)).target());
        d.release(); // Josh died; body hold is over
        assertEquals(new Shot("dane", "closest"), d.decide(sit(1 * S, List.of(DANE, MARA), KILLER, null, null, null)));
    }

    @Test
    void deadSubjectDoesNotHoldTheShot() {
        AutoDirector d = director();
        assertEquals("josh", d.decide(quiet(0)).target());
        // Josh gone from the living list without a release: the shot is no longer valid.
        assertEquals("dane", d.decide(sit(1 * S, List.of(DANE, MARA), KILLER, null, null, null)).target());
    }

    // ---------- rotation ----------

    @Test
    void rotationEvery60SecondsInRosterOrderAndWraps() {
        AutoDirector d = director();
        Subject far = at("killer", -500);
        assertEquals("josh", d.decide(sit(0, ALL, far, null, null, null)).target());
        assertNull(d.decide(sit(59 * S, ALL, far, null, null, null)));
        assertEquals(new Shot("dane", "rotate"), d.decide(sit(60 * S, ALL, far, null, null, null)));
        assertEquals(new Shot("mara", "rotate"), d.decide(sit(120 * S, ALL, far, null, null, null)));
        assertEquals(new Shot("josh", "rotate"), d.decide(sit(180 * S, ALL, far, null, null, null)));
    }

    @Test
    void rotationStartsOnTheCurrentShot() {
        AutoDirector d = director();
        assertEquals("josh", d.decide(quiet(0)).target());
        Subject far = at("killer", -500);
        assertNull(d.decide(sit(10 * S, ALL, far, null, null, null))); // killer leaves: keep Josh
        assertNull(d.decide(sit(69 * S, ALL, far, null, null, null)));
        assertEquals(new Shot("dane", "rotate"), d.decide(sit(70 * S, ALL, far, null, null, null)));
    }

    @Test
    void singleSurvivorIsNotRotatedAway() {
        AutoDirector d = director();
        Subject far = at("killer", -500);
        assertEquals("mara", d.decide(sit(0, List.of(MARA), far, null, null, null)).target());
        assertNull(d.decide(sit(600 * S, List.of(MARA), far, null, null, null)));
    }

    @Test
    void eventInterruptsRotationAfterMinShot() {
        AutoDirector d = director();
        Subject far = at("killer", -500);
        assertEquals("josh", d.decide(sit(0, ALL, far, null, null, null)).target());
        assertNull(d.decide(sit(3 * S, ALL, far, null, "mara", null)));
        assertEquals(new Shot("mara", "reveal"), d.decide(sit(8 * S, ALL, far, null, "mara", null)));
    }
}
