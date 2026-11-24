package com.vivet.speedrun.session;

import java.util.ArrayList;
import java.util.List;

public class Racer {
    public enum Status {
        RACING,

        FINISHED,

        OUT,

        DISCONNECTED,
    }

    public final String name;
    public Status status = Status.RACING;

    public int seconds;

    public int centis;

    public int liveSeconds;

    public int liveCentis;
    public long liveAt;

    public boolean ready;

    public boolean lobbyReady;

    public String detail = "";

    public final List<String> milestones = new ArrayList<>();

    private static final int MAX_MILESTONES = 6;

    public Racer(String name) {
        this.name = name;
    }

    public void addMilestone(String text) {
        this.milestones.add(text);
        while (this.milestones.size() > MAX_MILESTONES) {
            this.milestones.remove(0);
        }
    }

    public int projectedCentis() {
        if (this.status != Status.RACING) {
            return this.centis > 0 ? this.centis : this.liveCentis;
        }
        if (this.liveAt == 0L) return this.liveCentis;

        long since = System.currentTimeMillis() - this.liveAt;
        if (since < 0L) since = 0L;

        return this.liveCentis + (int) (since / 10L);
    }

    public void syncLive(int reportedCentis) {
        this.liveCentis = Math.max(reportedCentis, projectedCentis());
        this.liveAt = System.currentTimeMillis();
        this.liveSeconds = Math.max(this.liveSeconds,
                this.liveCentis / com.vivet.speedrun.game.Time.CENTIS_PER_SECOND);
    }

    public boolean isDone() {
        return this.status != Status.RACING;
    }

    public String statusText() {
        if (this.status != Status.RACING && !this.detail.isBlank()) return shown(this.detail);

        return switch (this.status) {
            case RACING -> "Searching...";
            case FINISHED -> "Found it";
            case OUT -> "Out";
            case DISCONNECTED -> "Disconnected";
        };
    }

    public String statusPhrase() {
        String text = statusText();
        return text.isEmpty() ? text : Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }

    public static String shown(String detail) {
        if (detail == null || detail.isBlank()) return "";
        return switch (detail) {
            case "found it" -> "Found it";
            case "did not finish" -> "Didn't finish";
            case "last one standing" -> "Last one standing";
            case "gave up" -> "Gave up";
            case "went idle" -> "Went AFK";
            case "time cap", "out of time" -> "Out of time";
            case "stopped the run" -> "Stopped";
            case "quit the run" -> "Quit";
            case "left the match" -> "Left";
            case "no contest" -> "No contest";
            default -> Character.toUpperCase(detail.charAt(0)) + detail.substring(1);
        };
    }
}
