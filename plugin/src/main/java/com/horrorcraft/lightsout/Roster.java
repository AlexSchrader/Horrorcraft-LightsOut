package com.horrorcraft.lightsout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Who is who: Minecraft username (case-insensitive) to actor id and role. */
public final class Roster {

    public enum Role { CAMPER, HUMAN, KILLER, POLICE }

    public record Actor(String username, String id, Role role) {
        /** Campers and Alex: the ones with lives. */
        public boolean hasLives() {
            return role == Role.CAMPER || role == Role.HUMAN;
        }
    }

    private final Map<String, Actor> byUsername = new HashMap<>();
    private final Map<String, Actor> byId = new HashMap<>();
    private final List<Actor> all = new ArrayList<>();

    public Roster(List<Actor> actors) {
        for (Actor a : actors) {
            byUsername.put(a.username().toLowerCase(Locale.ROOT), a);
            byId.put(a.id(), a);
            all.add(a);
        }
    }

    /** Parses one config entry; returns null (and the caller logs) when the entry is malformed. */
    public static Actor parse(String username, String id, String role) {
        if (username == null || username.isBlank() || id == null || id.isBlank() || role == null) return null;
        try {
            return new Actor(username, id.trim().toLowerCase(Locale.ROOT), Role.valueOf(role.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public Actor byUsername(String username) {
        return username == null ? null : byUsername.get(username.toLowerCase(Locale.ROOT));
    }

    public Actor byId(String id) {
        return id == null ? null : byId.get(id.toLowerCase(Locale.ROOT));
    }

    public Actor killer() {
        for (Actor a : all) if (a.role() == Role.KILLER) return a;
        return null;
    }

    /** Ids of every actor with lives, in config order. */
    public List<String> livesIds() {
        List<String> ids = new ArrayList<>();
        for (Actor a : all) if (a.hasLives()) ids.add(a.id());
        return ids;
    }

    public List<Actor> all() {
        return Collections.unmodifiableList(all);
    }
}
