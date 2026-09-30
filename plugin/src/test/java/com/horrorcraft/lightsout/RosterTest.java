package com.horrorcraft.lightsout;

import com.horrorcraft.lightsout.Roster.Actor;
import com.horrorcraft.lightsout.Roster.Role;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RosterTest {

    private static Roster cast() {
        return new Roster(List.of(
                Roster.parse("ACSsnipertroll", "alex", "human"),
                Roster.parse("Josh", "josh", "camper"),
                Roster.parse("Dane", "dane", "camper"),
                Roster.parse("Mara", "mara", "camper"),
                Roster.parse("Tyler", "tyler", "camper"),
                Roster.parse("Reyes", "reyes", "police"),
                Roster.parse("Hale", "hale", "police"),
                Roster.parse("Hollow", "killer", "killer"),
                Roster.parse("CamJosh", "camjosh", "camera")));
    }

    @Test
    void camerasHaveNoLives() {
        Actor cam = cast().byUsername("camjosh");
        assertTrue(cam.isCamera());
        assertFalse(cam.hasLives());
        assertFalse(cast().livesIds().contains("camjosh"));
    }

    @Test
    void usernameLookupIgnoresCase() {
        Roster r = cast();
        assertEquals("alex", r.byUsername("acssnipertroll").id());
        assertEquals(Role.HUMAN, r.byUsername("ACSsnipertroll").role());
        assertNull(r.byUsername("Steve"));
        assertNull(r.byUsername(null));
    }

    @Test
    void onlyCampersAndAlexHaveLives() {
        Roster r = cast();
        assertEquals(List.of("alex", "josh", "dane", "mara", "tyler"), r.livesIds());
        assertFalse(r.byId("killer").hasLives());
        assertFalse(r.byId("hale").hasLives());
    }

    @Test
    void killerIsFound() {
        Actor k = cast().killer();
        assertEquals("Hollow", k.username());
        assertEquals(Role.KILLER, k.role());
    }

    @Test
    void badEntriesAreRejected() {
        assertNull(Roster.parse("X", "x", "ghost"));
        assertNull(Roster.parse("X", "", "camper"));
        assertNull(Roster.parse("X", "x", null));
        assertNull(Roster.parse(" ", "x", "camper"));
    }
}
