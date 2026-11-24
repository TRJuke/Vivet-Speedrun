package com.vivet.speedrun.game;

public class RunRecord {
    public enum Outcome {
        WON,

        LOST,

        DRAW,

        IN_PROGRESS,
    }

    public String itemId = "air";
    public String winnerName = "";
    public Outcome outcome = Outcome.IN_PROGRESS;
    public Difficulty difficulty = Difficulty.MEDIUM;

    public int seconds;

    public int centis;

    public long date = System.currentTimeMillis();

    public int rerollsUsed;

    public String detail = "";

    public RunRecord() {
    }

    public RunRecord(String itemId, Difficulty difficulty) {
        this.itemId = itemId;
        this.difficulty = difficulty;
        this.outcome = Outcome.IN_PROGRESS;
        this.date = System.currentTimeMillis();
    }

    public boolean isWin() {
        return this.outcome == Outcome.WON;
    }

    public boolean isInProgress() {
        return this.outcome == Outcome.IN_PROGRESS;
    }

    public void sanitize() {
        if (this.itemId == null || this.itemId.isBlank()) this.itemId = "air";
        if (this.winnerName == null) this.winnerName = "";
        if (this.detail == null) this.detail = "";
        if (this.outcome == null) this.outcome = Outcome.LOST;
        if (this.difficulty == null) this.difficulty = Difficulty.MEDIUM;
        if (this.seconds < 0) this.seconds = 0;
        if (this.centis < 0) this.centis = 0;

        if (this.centis == 0 && this.seconds > 0) {
            this.centis = com.vivet.speedrun.game.Time.toCentis(this.seconds);
        } else if (this.seconds == 0 && this.centis > 0) {
            this.seconds = com.vivet.speedrun.game.Time.toSeconds(this.centis);
        }
    }
}
