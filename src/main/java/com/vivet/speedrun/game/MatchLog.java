package com.vivet.speedrun.game;

import java.util.ArrayList;
import java.util.List;

public class MatchLog {
    public String id = "";

    public String matchId = "";

    public long endedAt = System.currentTimeMillis();

    public String mode = "";

    public String band = "";

    public String itemId = "";

    public boolean nether;

    public int centis;

    public boolean found;

    public String detail = "";

    public int rerolls;

    public boolean setSeed;

    public long seed;

    public String result = "";

    public String endReason = "";

    public String winnerName = "";

    public List<Entry> racers = new ArrayList<>();

    public int matchSize = 1;

    public int place;

    public boolean personalBest;

    public int previousBestCentis = -1;

    public boolean firstFind;

    public boolean leaderboard;

    public boolean tookRecord;

    public int ratingDelta;

    public boolean ratingKnown;

    public int ratingAfter;

    public int xpGained;

    public static class Entry {
        public String name = "";

        public String uuid = "";

        public int centis;

        public boolean finished;

        public String status = "";

        public boolean you;

        public Entry() {
        }

        public Entry(String name, String uuid, int centis, boolean finished, String status,
                     boolean you) {
            this.name = name == null ? "" : name;
            this.uuid = uuid == null ? "" : uuid;
            this.centis = centis;
            this.finished = finished;
            this.status = status == null ? "" : status;
            this.you = you;
        }

        public boolean hasAccount() {
            return this.uuid != null && !this.uuid.isEmpty();
        }
    }

    public RunMode runMode() {
        return RunMode.byId(this.mode);
    }

    public Difficulty difficulty() {
        Difficulty found = Difficulty.byId(this.band);
        return found == null ? Difficulty.MEDIUM : found;
    }

    public boolean won() {
        return "won".equals(this.result);
    }

    public boolean lost() {
        return "lost".equals(this.result);
    }

    public boolean drew() {
        return "draw".equals(this.result);
    }

    public boolean noContest() {
        return "no_contest".equals(this.result);
    }

    public boolean decided() {
        return this.result != null && !this.result.isEmpty();
    }

    public Entry opponent() {
        if (this.racers == null) return null;

        for (Entry entry : this.racers) {
            if (entry != null && !entry.you) return entry;
        }
        return null;
    }

    public boolean hasMarks() {
        return this.personalBest || this.firstFind || this.tookRecord;
    }

    public void sanitize() {
        if (this.id == null) this.id = "";
        if (this.matchId == null) this.matchId = "";
        if (this.mode == null) this.mode = "";
        if (this.band == null) this.band = "";
        if (this.itemId == null) this.itemId = "";
        if (this.detail == null) this.detail = "";
        if (this.result == null) this.result = "";
        if (this.endReason == null) this.endReason = "";
        if (this.winnerName == null) this.winnerName = "";
        if (this.racers == null) this.racers = new ArrayList<>();
        if (this.matchSize < 1) this.matchSize = 1;

        this.racers.removeIf(entry -> entry == null || entry.name == null || entry.name.isEmpty());
        for (Entry entry : this.racers) {
            if (entry.uuid == null) entry.uuid = "";
            if (entry.status == null) entry.status = "";
        }
    }
}
