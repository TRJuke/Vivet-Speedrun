package com.vivet.speedrun.game;

import com.vivet.speedrun.session.Session;

public enum RunMode {
    SOLO("solo", "Practice"),

    PRIVATE("private", "Private Lobby"),

    PUBLIC("public", "Unranked Match"),

    RANKED_MATCH("ranked", "Ranked 1v1"),

    RANKED_SOLO("ranked_solo", "Solo");

    private final String id;
    private final String displayName;

    RunMode(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return this.id;
    }

    public String displayName() {
        return this.displayName;
    }

    public boolean isRecorded() {
        return this == RANKED_MATCH || this == RANKED_SOLO;
    }

    public boolean isVersus() {
        return this == PRIVATE || this == PUBLIC || this == RANKED_MATCH;
    }

    public static RunMode current() {
        if (Session.isRankedSolo()) return RANKED_SOLO;
        if (Session.isRanked()) return RANKED_MATCH;
        if (Session.isPublicMatch()) return PUBLIC;

        if (Session.isActive()) return PRIVATE;

        return SOLO;
    }

    public static RunMode ofLast() {
        if (Session.isActive()) return current();

        if (Session.wasRankedSolo()) return RANKED_SOLO;
        if (Session.wasRanked()) return RANKED_MATCH;
        if (Session.wasPublic()) return PUBLIC;

        if (Session.wasPrivate()) return PRIVATE;

        return SOLO;
    }

    public static RunMode byId(String id) {
        for (RunMode mode : values()) {
            if (mode.id.equals(id)) return mode;
        }
        return SOLO;
    }
}
