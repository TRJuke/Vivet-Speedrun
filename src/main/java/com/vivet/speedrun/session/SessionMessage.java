package com.vivet.speedrun.session;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public class SessionMessage {
    public enum Type {
        JOIN,

        WELCOME,

        ROSTER,

        ROSTER_REFRESH,

        READY_CHECK,

        LOBBY_READY,

        LOBBY_UNREADY,

        START,

        READY,

        GO,

        EVENT,

        TIME,

        FINISH,

        FORFEIT,

        CHAT,

        VOTE_REQUEST,

        VOTE_OPEN,

        VOTE_CAST,

        VOTE_RESULT,

        REROLL,

        DRAW,

        END,

        REMATCH,

        HOST_QUIT,

        LINK_LOST,

        REJECT,

        REFUSED,

        AFK,

        LABELS,

        AWAY,

        BACK,

        FORFEITED,

        GRACE,

        GRACE_ANSWER,

        CARRY_ON,
    }

    public enum VoteKind {
        REROLL,
        DRAW,
    }

    private static final Gson GSON = new Gson();

    public Type type;
    public String name;
    public String text;

    public String uuid;
    public String itemId;
    public String difficulty;
    public Long seed;
    public Integer seconds;

    public Integer centis;

    public Integer activity;

    public String state;

    public Boolean waitForAll;

    public Boolean walkover;

    public Boolean voided;
    public Boolean agree;
    public VoteKind vote;

    public Long voteId;
    public String[] players;

    public Integer protocol;

    public String ratings;

    public Long salt;

    public Integer rerollIndex;

    public String worldDifficulty;

    public Boolean includeEasier;

    public String dimension;

    public String seedText;

    public java.util.Map<String, Integer> ruleLevels;

    public java.util.Map<String, Boolean> rules;

    public boolean isHostOnly() {
        return switch (this.type) {
            case WELCOME, ROSTER, READY_CHECK, START, GO, VOTE_OPEN, VOTE_RESULT, REROLL, DRAW,
                 HOST_QUIT, REJECT -> true;
            default -> false;
        };
    }

    public boolean isRelayOnly() {
        return this.type == Type.END || this.type == Type.AFK
                || this.type == Type.LABELS || this.type == Type.AWAY
                || this.type == Type.BACK || this.type == Type.FORFEITED
                || this.type == Type.GRACE || this.type == Type.CARRY_ON;
    }

    public SessionMessage() {
    }

    public SessionMessage(Type type) {
        this.type = type;
    }

    public java.util.Map<String, String> labels;

    public Long until;

    public java.util.Map<String, String> uuids;

    public java.util.Map<String, java.util.Map<String, String>> looks;

    public static SessionMessage labels(java.util.Map<String, String> labels,
                                        java.util.Map<String, String> uuids,
                                        java.util.Map<String, java.util.Map<String, String>> looks) {
        SessionMessage message = new SessionMessage(Type.LABELS);
        message.labels = labels;
        message.uuids = uuids;
        message.looks = looks;
        return message;
    }

    public static SessionMessage presence(Type type, String name, Long until, Integer seconds) {
        SessionMessage message = of(type, name);
        message.until = until;
        message.seconds = seconds;
        return message;
    }

    public static SessionMessage grace(String band, Integer seconds, String forName) {
        SessionMessage message = of(Type.GRACE, forName);
        message.difficulty = band;
        message.seconds = seconds;
        return message;
    }

    public static SessionMessage carryOn(Long until, Integer seconds) {
        SessionMessage message = new SessionMessage(Type.CARRY_ON);
        message.until = until;
        message.seconds = seconds;
        return message;
    }

    public static SessionMessage afk(String state, String name, String reason) {
        SessionMessage message = of(Type.AFK, name);
        message.state = state;
        message.text = reason;
        return message;
    }

    public String matchId;

    public Boolean conceded;

    public static SessionMessage fromVerdict(String winner, Integer seconds, String reason,
                                             Boolean walkover, Boolean voided, Boolean conceded,
                                             String matchId) {
        SessionMessage end = new SessionMessage(Type.END);
        end.name = winner;
        end.seconds = seconds;
        end.text = reason == null ? "Match complete" : reason;
        end.walkover = Boolean.TRUE.equals(walkover);
        end.voided = Boolean.TRUE.equals(voided);
        end.conceded = Boolean.TRUE.equals(conceded);
        end.matchId = matchId == null ? "" : matchId;
        return end;
    }

    public static SessionMessage of(Type type, String name) {
        SessionMessage message = new SessionMessage(type);
        message.name = name;
        return message;
    }

    public String encode() {
        return GSON.toJson(this);
    }

    public static SessionMessage decode(String line) {
        try {
            SessionMessage message = GSON.fromJson(line, SessionMessage.class);
            return message != null && message.type != null ? message : null;
        } catch (JsonSyntaxException e) {
            return null;
        }
    }
}
