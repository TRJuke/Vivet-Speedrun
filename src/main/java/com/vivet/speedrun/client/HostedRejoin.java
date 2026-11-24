package com.vivet.speedrun.client;

import com.google.gson.Gson;
import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.game.Difficulty;
import com.vivet.speedrun.p2p.RankedApi;
import com.vivet.speedrun.session.Session;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

public final class HostedRejoin {
    private static final Gson GSON = new Gson();
    private static final Path FILE = Paths.get("config", "vivet_speedrun_hosted_race.json");
    private static final long REFRESH_MS = 1_000L;

    public static final class Saved {
        public String match = "";
        public String host = "";
        public int port;
        public String band = Difficulty.HARD.id();
        public boolean setSeed;
        public long aliveAt;
        public long windowMs = 120_000L;

        public String kind = "solo";
        public String opponent = "";
        public String opponentUuid = "";

        public long deadline() {
            return this.aliveAt + Math.max(0L, this.windowMs);
        }

        boolean isMatch() {
            return "ranked".equals(this.kind) || "public".equals(this.kind);
        }

        boolean usable() {
            return this.host != null && !this.host.isBlank() && this.port > 0 && this.aliveAt > 0;
        }

        Saved copy() {
            Saved out = new Saved();
            out.match = this.match;
            out.host = this.host;
            out.port = this.port;
            out.band = this.band;
            out.setSeed = this.setSeed;
            out.aliveAt = this.aliveAt;
            out.windowMs = this.windowMs;
            out.kind = this.kind;
            out.opponent = this.opponent;
            out.opponentUuid = this.opponentUuid;
            return out;
        }
    }

    private static Saved racing;

    private static Saved offered;

    private static long nextWriteAt;

    private static volatile boolean askingMissed;
    private static long missedAskAt;
    private static int missedTries;

    private static final long MISSED_RETRY_MS = 60_000L;
    private static final int MISSED_TRIES = 3;

    private HostedRejoin() {
    }

    public static void load() {
        offered = null;
        if (!Files.exists(FILE)) return;
        try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            Saved saved = GSON.fromJson(reader, Saved.class);
            if (saved != null && saved.usable()) offered = saved;
        } catch (Exception e) {
            VivetSpeedrun.LOGGER.warn("Could not read saved hosted race", e);
        }
    }

    public static void joined(RankedApi.HostedTicket ticket, Difficulty band, boolean setSeed) {
        Saved saved = new Saved();
        saved.match = ticket.match == null ? "" : ticket.match;
        saved.host = ticket.host;
        saved.port = ticket.port;
        saved.band = band.id();
        saved.setSeed = setSeed;
        saved.kind = ticket.isMatch() ? ticket.kind : "solo";
        saved.opponent = ticket.opponent == null ? "" : ticket.opponent;
        saved.opponentUuid = ticket.opponentUuid == null ? "" : ticket.opponentUuid;
        racing = saved;
        nextWriteAt = 0L;
    }

    public static void tick(Minecraft minecraft) {
        Saved live = racing;
        if (live == null || !Session.isHosted() || !ClientState.isRunning()) return;
        if (!HostedConnect.isRaceServer(minecraft.getCurrentServer())) return;

        long now = System.currentTimeMillis();
        if (now < nextWriteAt) return;
        nextWriteAt = now + REFRESH_MS;

        live.aliveAt = now;

        live.windowMs = "ranked".equals(live.kind) ? 60_000L
                : "public".equals(live.kind) ? 20_000L
                : RankedApi.hostedRejoinMs();
        offered = live.copy();
        write(offered);
    }

    public static boolean inRace() {
        return racing != null && Session.isHosted() && ClientState.isRunning();
    }

    public static Saved offered() {
        Saved saved = offered;
        if (saved == null) return null;
        if (System.currentTimeMillis() >= saved.deadline()) {
            if (!saved.isMatch()) clear();
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (HostedConnect.isRaceServer(minecraft.getCurrentServer())) return null;
        return saved;
    }

    public static boolean pending() {
        Saved saved = offered;
        return saved != null && System.currentTimeMillis() < saved.deadline();
    }

    public static int secondsLeft() {
        Saved saved = offered;
        if (saved == null) return 0;
        return (int) Math.max(0L, (saved.deadline() - System.currentTimeMillis()) / 1000L);
    }

    public static Saved missed() {
        Saved saved = offered;
        if (saved == null || !saved.isMatch()) return null;
        return System.currentTimeMillis() >= saved.deadline() ? saved : null;
    }

    public static void lookForMissed(Minecraft minecraft) {
        Saved saved = missed();
        if (saved == null || askingMissed || missedTries >= MISSED_TRIES) return;
        if (Session.isActive() || minecraft.level != null) return;
        if (!(minecraft.gui.screen() instanceof com.vivet.speedrun.client.screen.VivetTitleScreen)) {
            return;
        }
        if (!com.vivet.speedrun.p2p.MojangAuth.isSignedIn()) return;

        long now = System.currentTimeMillis();
        if (now < missedAskAt) return;
        missedAskAt = now + MISSED_RETRY_MS;
        missedTries++;
        askingMissed = true;

        RankedApi.hostedRejoin(saved.match, answer -> minecraft.execute(() -> {
            askingMissed = false;
            if (answer.result != null) {
                showMissed(minecraft, answer.result);
            } else if (Boolean.TRUE.equals(answer.over)) {
                clear();
            }
        }), reason -> askingMissed = false);
    }

    public static void showMissed(Minecraft minecraft, RankedApi.Missed result) {
        clear();
        com.vivet.speedrun.client.screen.HostedDroppedScreen.reset();
        if (result == null || result.verdict == null) return;

        if (Session.isActive()) Session.stop();

        String me = minecraft.getUser() != null ? minecraft.getUser().getName() : Session.localName();
        com.vivet.speedrun.p2p.RelayConnection.Frame verdict = result.verdict;
        boolean voided = Boolean.TRUE.equals(verdict.voided);
        boolean conceded = Boolean.TRUE.equals(verdict.conceded);

        Difficulty band = Difficulty.byId(result.band);
        String line = Session.rememberMissedMatch(me, band == null ? Difficulty.MEDIUM : band,
                result.ranked, result.opponent, result.opponentUuid, verdict.winner,
                verdict.seconds, Session.voidLine(verdict, me),
                Boolean.TRUE.equals(verdict.walkover),
                voided, conceded, verdict.matchId == null ? result.match : verdict.matchId);

        RankedApi.MissedStanding standing = result.standing;
        RankedApi.Before levelBefore = result.xp == null ? null : result.xp.before;
        com.vivet.speedrun.p2p.PostMatch.acceptMissed(
                standing == null ? null : standing.profile,
                standing == null ? null : standing.delta,
                result.xp == null ? null : result.xp.xp,
                levelBefore == null || levelBefore.level == null ? 0 : levelBefore.level,
                levelBefore == null || levelBefore.into == null ? 0 : levelBefore.into,
                levelBefore == null || levelBefore.need == null ? 0 : levelBefore.need);

        boolean nothing = voided || conceded;
        boolean won = !nothing && me.equals(verdict.winner);
        boolean drew = !nothing && (verdict.winner == null || verdict.winner.isEmpty());

        Integer after = standing == null || standing.profile == null ? null : standing.profile.rating;
        int gained = result.xp == null || result.xp.xp == null ? 0 : result.xp.xp.gained;
        int seconds = verdict.seconds == null ? 0 : Math.max(0, verdict.seconds);
        com.vivet.speedrun.game.MatchLogs.recordMissed(
                result.ranked ? com.vivet.speedrun.game.RunMode.RANKED_MATCH
                        : com.vivet.speedrun.game.RunMode.PUBLIC,
                band, result.itemId, verdict.matchId == null ? result.match : verdict.matchId, me,
                result.opponent, result.opponentUuid, verdict.winner, seconds * 100,
                Boolean.TRUE.equals(verdict.walkover),
                nothing ? "no_contest" : won ? "won" : drew ? "draw" : "lost", line,
                standing == null ? null : standing.delta, after, gained);

        if (won) {
            Cues.matchWon();
        } else if (drew || nothing) {
            Cues.matchEnded();
        } else {
            Cues.matchLost();
        }

        minecraft.gui.setScreen(new com.vivet.speedrun.client.screen.EndgameScreen(line, won, drew,
                ratingBefore(standing)));
    }

    private static com.vivet.speedrun.p2p.MojangAuth.Profile ratingBefore(
            RankedApi.MissedStanding standing) {
        if (standing == null || standing.profile == null || standing.before == null
                || standing.before.rating == null) {
            return null;
        }

        com.vivet.speedrun.p2p.MojangAuth.Profile after = standing.profile;
        com.vivet.speedrun.p2p.MojangAuth.Profile before = new com.vivet.speedrun.p2p.MojangAuth.Profile();
        before.uuid = after.uuid;
        before.name = after.name;
        before.rating = standing.before.rating;
        before.rank = standing.before.rank == null ? after.rank : standing.before.rank;
        before.placed = after.placed;
        before.placementGames = after.placementGames;
        before.placementMatches = after.placementMatches;
        return before;
    }

    public static void clear() {
        racing = null;
        offered = null;
        try {
            Files.deleteIfExists(FILE);
        } catch (Exception e) {
            VivetSpeedrun.LOGGER.debug("Could not delete saved hosted race", e);
        }
    }

    public static void rejoin(Minecraft minecraft, Screen back, Consumer<String> onFailed) {
        Saved saved = offered;
        String match = saved == null ? null : saved.match;

        RankedApi.hostedRejoin(match, answer -> minecraft.execute(() -> {
            if (answer.result != null) {
                showMissed(minecraft, answer.result);
                return;
            }
            if (answer.error != null || answer.host == null || answer.port <= 0) {
                clear();
                onFailed.accept(answer.error == null ? "That match is over." : answer.error);
                return;
            }
            Difficulty band = Difficulty.byId(answer.band);
            if (band == null) band = Difficulty.HARD;

            VivetSpeedrun.LOGGER.info("Rejoining hosted race on {}:{}",
                    answer.host, answer.port);

            if (answer.isMatch()) {
                HostedConnect.joinMatch(minecraft, back, band, answer, true);
                return;
            }

            Session.beginHostedSolo(answer.setSeed);
            joined(answer, band, answer.setSeed);
            HostedConnect.join(minecraft, back, answer);
            HostedLoading.begin(minecraft, back);
        }), reason -> minecraft.execute(() -> onFailed.accept(reason)));
    }

    public static void giveUp() {
        RankedApi.hostedGiveUp();
        clear();
        if (Session.isHostedMatch()) {
            Session.stop();
        } else {
            Session.endHostedSolo();
        }
    }

    private static void write(Saved saved) {
        try {
            Path parent = FILE.getParent();
            if (parent != null) Files.createDirectories(parent);
            try (Writer writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(saved, writer);
            }
        } catch (Exception e) {
            VivetSpeedrun.LOGGER.warn("Could not save hosted race", e);
        }
    }
}
