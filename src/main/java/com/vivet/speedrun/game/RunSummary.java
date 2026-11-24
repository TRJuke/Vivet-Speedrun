package com.vivet.speedrun.game;

import com.vivet.speedrun.p2p.MojangAuth;
import com.vivet.speedrun.p2p.Standings;
import com.vivet.speedrun.session.Racer;
import com.vivet.speedrun.session.Session;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class RunSummary {
    public final RunMode mode;
    public final String itemId;
    public final Difficulty band;

    public final int seconds;

    public final int centis;

    public final boolean found;

    public final String detail;

    public final int rerolls;

    public final boolean setSeed;

    public final boolean versus;

    public final boolean decided;

    public final String winnerName;
    public final int winnerSeconds;

    public final RunRecord.Outcome outcome;

    public final int margin;

    public final List<Racer> standings;

    public final int matchSize;

    public final int place;

    public final int previousBest;

    public final int previousBestCentis;

    public final boolean personalBest;

    public final PersonalBests.Entry itemRecord;
    public final PersonalBests.Entry bandRecord;
    public final PersonalBests.Entry modeRecord;

    public final Standings.Answer boards;

    public final MojangAuth.Profile ratingBefore;

    private RunSummary(Builder builder) {
        this.mode = builder.mode;
        this.itemId = builder.itemId;
        this.band = builder.band;
        this.seconds = builder.seconds;
        this.centis = builder.centis;
        this.found = builder.found;
        this.detail = builder.detail;
        this.rerolls = builder.rerolls;
        this.setSeed = builder.setSeed;
        this.versus = builder.versus;
        this.decided = builder.decided;
        this.winnerName = builder.winnerName;
        this.winnerSeconds = builder.winnerSeconds;
        this.outcome = builder.outcome;
        this.margin = builder.margin;
        this.standings = builder.standings;
        this.matchSize = builder.matchSize;
        this.place = builder.place;
        this.previousBest = builder.previousBest;
        this.previousBestCentis = builder.previousBestCentis;
        this.personalBest = builder.personalBest;
        this.itemRecord = builder.itemRecord;
        this.bandRecord = builder.bandRecord;
        this.modeRecord = builder.modeRecord;
        this.boards = builder.boards;
        this.ratingBefore = builder.ratingBefore;
    }

    private static final class Builder {
        RunMode mode = RunMode.SOLO;
        String itemId = "";
        Difficulty band = Difficulty.MEDIUM;
        int seconds;
        int centis;
        boolean found;
        String detail = "";
        int rerolls;
        boolean setSeed;
        boolean versus;
        boolean decided = true;
        String winnerName = "";
        int winnerSeconds;
        RunRecord.Outcome outcome = RunRecord.Outcome.LOST;
        int margin = -1;
        List<Racer> standings = List.of();
        int matchSize = 1;
        int place;
        int previousBest = -1;
        int previousBestCentis = -1;
        boolean personalBest;
        PersonalBests.Entry itemRecord;
        PersonalBests.Entry bandRecord;
        PersonalBests.Entry modeRecord;
        Standings.Answer boards;
        MojangAuth.Profile ratingBefore;
    }

    public static RunSummary of(String itemId, Difficulty band, int seconds, int centis,
                                boolean found, String localDetail, int rerolls,
                                MojangAuth.Profile ratingBefore) {
        Builder builder = new Builder();

        builder.mode = RunMode.ofLast();
        builder.itemId = itemId == null ? "" : itemId;
        builder.band = band == null ? Difficulty.MEDIUM : band;
        builder.seconds = Math.max(0, seconds);
        builder.centis = Math.max(0, centis);
        builder.found = found;
        builder.detail = localDetail == null ? "" : localDetail;
        builder.rerolls = Math.max(0, rerolls);
        builder.setSeed = Session.wasSetSeed();
        builder.ratingBefore = ratingBefore;
        builder.boards = Standings.current();

        builder.matchSize = Math.max(1, Session.lastMatchSize());
        builder.versus = builder.mode.isVersus() && builder.matchSize > 1;
        builder.standings = sortStandings(Session.finalStandings());

        if (builder.versus) {
            boolean serverDrew = "drew".equals(builder.detail);

            builder.decided = Session.isMatchDecided() || serverDrew;
            builder.winnerName = serverDrew ? "" : Session.lastWinner();
            builder.winnerSeconds = serverDrew ? 0 : Session.lastWinnerSeconds();

            boolean noResult = Session.wasVoided() || Session.wasConceded();
            boolean youWon = !serverDrew && !noResult && Session.lastWinnerWasLocal();
            boolean drawn = serverDrew || (builder.decided && builder.winnerName.isEmpty()
                    && !noResult && (Session.wasDrawn() || everybodyDrew(builder.standings)));

            builder.outcome = !builder.decided ? RunRecord.Outcome.IN_PROGRESS
                    : youWon ? RunRecord.Outcome.WON
                    : drawn ? RunRecord.Outcome.DRAW
                    : RunRecord.Outcome.LOST;

            builder.margin = found && builder.winnerSeconds > 0
                    ? Math.max(0, builder.seconds - builder.winnerSeconds) : -1;

            builder.place = placeOf(builder.standings, Session.localName());
        } else {
            builder.decided = true;
            builder.outcome = found ? RunRecord.Outcome.WON : RunRecord.Outcome.LOST;
            builder.winnerName = found ? Session.localName() : "";
            builder.winnerSeconds = found ? builder.seconds : 0;
            builder.margin = found ? 0 : -1;
            builder.place = found ? 1 : 0;
        }

        Standings.adoptYourBest(builder.mode, builder.setSeed, builder.itemId);

        builder.previousBest = PersonalBests.bestOf(
                PersonalBests.itemKey(builder.mode, builder.setSeed, builder.itemId));
        builder.previousBestCentis = PersonalBests.bestCentisOf(
                PersonalBests.itemKey(builder.mode, builder.setSeed, builder.itemId));

        if (builder.decided) {
            builder.personalBest = PersonalBests.noteFinished(builder.mode, builder.setSeed,
                    builder.itemId, builder.band, builder.seconds, builder.centis, found,
                    builder.outcome);
        }

        builder.itemRecord = PersonalBests.forItem(builder.mode, builder.setSeed, builder.itemId);
        builder.bandRecord = PersonalBests.forBand(builder.mode, builder.setSeed, builder.band);
        builder.modeRecord = PersonalBests.forMode(builder.mode, builder.setSeed);

        return new RunSummary(builder);
    }

    private static List<Racer> sortStandings(List<Racer> racers) {
        List<Racer> sorted = new ArrayList<>(racers);
        sorted.sort(Comparator
                .comparingInt((Racer racer) -> racer.status == Racer.Status.FINISHED ? 0 : 1)
                .thenComparingInt(racer -> racer.status == Racer.Status.FINISHED
                        ? racer.seconds : Integer.MAX_VALUE));
        return List.copyOf(sorted);
    }

    private static int placeOf(List<Racer> standings, String name) {
        int place = 0;
        for (Racer racer : standings) {
            if (racer.status != Racer.Status.FINISHED) continue;
            place++;
            if (racer.name.equals(name)) return place;
        }
        return 0;
    }

    private static boolean everybodyDrew(List<Racer> standings) {
        for (Racer racer : standings) {
            if (racer.status == Racer.Status.FINISHED) return false;
        }
        return !standings.isEmpty();
    }

    public boolean won() {
        return this.outcome == RunRecord.Outcome.WON;
    }

    public boolean drew() {
        return this.outcome == RunRecord.Outcome.DRAW;
    }

    public boolean undecided() {
        return this.outcome == RunRecord.Outcome.IN_PROGRESS;
    }

    public int improvedBy() {
        if (!this.personalBest || this.previousBestCentis <= 0) return -1;
        return this.previousBestCentis - this.centis;
    }

    public int missedBestBy() {
        if (!this.found || this.previousBestCentis <= 0
                || this.centis <= this.previousBestCentis) {
            return -1;
        }
        return this.centis - this.previousBestCentis;
    }

    public Standings.Board itemBoard() {
        return this.boards == null ? null : this.boards.item;
    }

    public Standings.Board bandBoard() {
        return this.boards == null ? null : this.boards.band;
    }

    public Standings.Board seedBoard() {
        return this.boards == null ? null : this.boards.seed;
    }
}
