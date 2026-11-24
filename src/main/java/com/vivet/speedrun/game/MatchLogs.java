package com.vivet.speedrun.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.session.Racer;
import com.vivet.speedrun.session.Session;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MatchLogs {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<MatchLog>>() {}.getType();

    private static final Path FILE = Paths.get("config", "vivet_speedrun", "logs.json");

    private static final int MAX = 500;

    private static final long SETTLE_TIMEOUT_MS = 120_000L;

    private static List<MatchLog> logs;

    private static String pending;
    private static long pendingSince;

    private static long pendingSeed;

    private static String pendingSeedLog;

    private static String lastLog;
    private static long lastLogAt;

    private static String overMatch;
    private static String overSeed;
    private static String overItem;
    private static long overAt;

    private static final long OVER_JOIN_MS = 5 * 60_000L;

    private MatchLogs() {
    }

    public static List<MatchLog> all() {
        if (logs == null) load();
        return logs;
    }

    public static List<MatchLog> inMode(RunMode mode) {
        List<MatchLog> found = new ArrayList<>();
        for (MatchLog log : all()) {
            if (log.runMode() == mode) found.add(log);
        }
        return found;
    }

    public static int count() {
        return all().size();
    }

    public static boolean logs(RunMode mode) {
        return mode == RunMode.PUBLIC || mode == RunMode.RANKED_MATCH || mode == RunMode.RANKED_SOLO;
    }

    public static void record(RunSummary summary, boolean firstFind, boolean tookRecord,
                              boolean nether, long seed) {
        if (summary == null || !logs(summary.mode)) return;

        MatchLog log = new MatchLog();
        log.id = UUID.randomUUID().toString().replace("-", "");
        log.endedAt = System.currentTimeMillis();
        log.mode = summary.mode.id();
        log.band = summary.band == null ? "" : summary.band.id();
        log.itemId = summary.itemId;
        log.nether = nether;
        log.centis = summary.centis;
        log.found = summary.found;
        log.detail = summary.detail;
        log.rerolls = summary.rerolls;
        log.setSeed = summary.setSeed;

        landSeed();

        pendingSeed = summary.setSeed ? 0L : seed;
        pendingSeedLog = log.id;
        log.matchSize = summary.matchSize;
        log.place = summary.place;
        log.winnerName = summary.winnerName;

        log.personalBest = summary.personalBest;
        log.previousBestCentis = summary.previousBestCentis;
        log.firstFind = firstFind;
        log.leaderboard = summary.mode.isRecorded() && summary.found;
        log.tookRecord = tookRecord;

        log.racers = racersOf(summary);

        log.result = "";

        add(log);

        pending = log.id;
        pendingSince = System.currentTimeMillis();

        lastLog = log.id;
        lastLogAt = pendingSince;

        if (overMatch != null && pendingSince - overAt < OVER_JOIN_MS && overItem.equals(log.itemId)) {
            joinOver(log, overMatch, overSeed);
            overMatch = null;
            save();
        }
    }

    public static void raceOver(String matchId, String seed, String itemId) {
        String match = matchId == null ? "" : matchId;
        String world = seed == null ? "" : seed;
        String item = itemId == null ? "" : itemId;

        MatchLog log = lastLog == null ? null : byId(lastLog);
        boolean fresh = log != null && System.currentTimeMillis() - lastLogAt < OVER_JOIN_MS;

        if (fresh && item.equals(log.itemId) && log.seed == 0L) {
            joinOver(log, match, world);
            save();
            return;
        }

        overMatch = match;
        overSeed = world;
        overItem = item;
        overAt = System.currentTimeMillis();
    }

    private static void joinOver(MatchLog log, String match, String seed) {
        if (!match.isEmpty()) log.matchId = match;
        if (log.setSeed) return;
        try {
            long parsed = Long.parseLong(seed.trim());
            if (parsed != 0L) {
                log.seed = parsed;
                if (log.id.equals(pendingSeedLog)) {
                    pendingSeed = 0L;
                    pendingSeedLog = null;
                }
            }
        } catch (NumberFormatException ignored) {
        }
    }

    public static void recordMissed(RunMode mode, Difficulty band, String itemId, String matchId,
                                    String me, String opponent, String opponentUuid,
                                    String winner, int winnerCentis, boolean walkover,
                                    String result, String endReason,
                                    Integer ratingDelta, Integer ratingAfter, int xpGained) {
        if (!logs(mode)) return;
        if (matchId != null && !matchId.isEmpty()) {
            for (MatchLog log : all()) {
                if (matchId.equals(log.matchId)) return;
            }
        }

        boolean won = "won".equals(result);

        MatchLog log = new MatchLog();
        log.id = UUID.randomUUID().toString().replace("-", "");
        log.endedAt = System.currentTimeMillis();
        log.matchId = matchId == null ? "" : matchId;
        log.mode = mode.id();
        log.band = band == null ? "" : band.id();
        log.itemId = itemId == null ? "" : itemId;
        log.found = won && !walkover;
        log.centis = log.found ? Math.max(0, winnerCentis) : 0;
        log.detail = log.found ? "found it" : walkover && won ? "last one standing" : "did not finish";
        log.result = result == null ? "" : result;
        log.endReason = endReason == null ? "" : endReason;
        log.winnerName = winner == null ? "" : winner;
        log.matchSize = 2;
        log.place = log.found ? 1 : 0;
        log.leaderboard = mode.isRecorded() && log.found;

        String you = me == null ? "" : me;
        log.racers.add(new MatchLog.Entry(you, "", log.centis, log.found,
                log.found ? "found it" : won ? "last one standing" : "left", true));
        if (opponent != null && !opponent.isEmpty()) {
            boolean theyWon = opponent.equals(winner);
            log.racers.add(new MatchLog.Entry(opponent, opponentUuid,
                    theyWon && !walkover ? Math.max(0, winnerCentis) : 0, theyWon && !walkover,
                    theyWon ? (walkover ? "last one standing" : "found it") : "did not finish",
                    false));
        }

        log.ratingKnown = true;
        if (ratingDelta != null) {
            log.ratingDelta = ratingDelta;
            log.ratingAfter = ratingAfter == null ? 0 : ratingAfter;
        }
        log.xpGained = Math.max(0, xpGained);

        add(log);
    }

    private static List<MatchLog.Entry> racersOf(RunSummary summary) {
        List<MatchLog.Entry> entries = new ArrayList<>();
        if (summary.standings == null) return entries;

        String you = Session.localName();

        for (Racer racer : summary.standings) {
            if (racer == null || racer.name == null || racer.name.isEmpty()) continue;

            boolean finished = racer.status == Racer.Status.FINISHED;
            String uuid = Session.accountIdOf(racer.name);

            entries.add(new MatchLog.Entry(racer.name, uuid, racer.centis, finished,
                    racer.statusText(), racer.name.equals(you)));
        }
        return entries;
    }

    public static void settle() {
        String id = pending;
        if (id == null) return;

        MatchLog log = byId(id);
        if (log == null) {
            pending = null;
            return;
        }

        boolean changed = false;
        boolean done = false;

        String matchId = Session.lastMatchId();
        if (matchId != null && !matchId.isEmpty() && log.matchId.isEmpty()) {
            log.matchId = matchId;
            changed = true;
        }

        if (!log.decided()) {
            String settled = resultNow(log);
            if (settled != null) {
                log.result = settled;
                log.endReason = Session.lastEndReason();
                log.winnerName = Session.lastWinner();
                changed = true;
            }
        }

        if (!log.ratingKnown) {
            var standing = com.vivet.speedrun.p2p.PostMatch.current();
            if (standing != null) {
                log.ratingKnown = true;
                log.ratingDelta = standing.change();
                log.ratingAfter = ratingOf(standing);
                changed = true;
            }
        }

        if (log.xpGained == 0 && com.vivet.speedrun.p2p.PostMatch.xpArrived()) {
            log.xpGained = com.vivet.speedrun.p2p.PostMatch.xpGained();
            changed = true;
        }

        if (log.runMode() == RunMode.RANKED_SOLO) {
            done = com.vivet.speedrun.p2p.PostMatch.xpArrived();
        } else {
            done = log.decided() && log.ratingKnown;
        }

        if (System.currentTimeMillis() - pendingSince > SETTLE_TIMEOUT_MS) done = true;

        changed |= landSeed();

        if (done) {
            pendingSeed = 0L;
            pendingSeedLog = null;
        }

        if (changed) save();
        if (done) pending = null;
    }

    private static boolean landSeed() {
        if (pendingSeed == 0L || pendingSeedLog == null) return false;

        MatchLog log = byId(pendingSeedLog);
        if (log == null) {
            pendingSeed = 0L;
            pendingSeedLog = null;
            return false;
        }
        if (!log.decided() || log.seed != 0L) return false;

        log.seed = pendingSeed;
        pendingSeed = 0L;
        pendingSeedLog = null;
        return true;
    }

    private static int ratingOf(com.vivet.speedrun.p2p.PostMatch.Standing standing) {
        var profile = standing.profile();
        if (profile == null || profile.rating == null) return 0;
        return profile.rating;
    }

    private static String resultNow(MatchLog log) {
        if (log.runMode() == RunMode.RANKED_SOLO) {
            return log.found ? "won" : "lost";
        }

        if (Session.wasConceded() || Session.wasVoided()) return "no_contest";

        String winner = Session.lastWinner();
        if (winner == null || winner.isEmpty()) {
            return Session.isMatchDecided() && Session.wasDrawn() ? "draw" : null;
        }

        return Session.lastWinnerWasLocal() ? "won" : "lost";
    }

    private static MatchLog byId(String id) {
        for (MatchLog log : all()) {
            if (log.id.equals(id)) return log;
        }
        return null;
    }

    private static void add(MatchLog log) {
        all().add(0, log);

        while (all().size() > MAX) {
            all().remove(all().size() - 1);
        }
        save();
    }

    public static boolean remove(String id) {
        if (id == null) return false;

        boolean gone = all().removeIf(log -> id.equals(log.id));
        if (gone) {
            if (id.equals(pending)) pending = null;
            if (id.equals(pendingSeedLog)) { pendingSeed = 0L; pendingSeedLog = null; }
            save();
        }
        return gone;
    }

    public static void clear() {
        all().clear();
        pending = null;
        pendingSeed = 0L;
        pendingSeedLog = null;
        save();
    }

    public static List<MatchLog> readFrom(Path file) {
        List<MatchLog> read = new ArrayList<>();
        if (file == null || !Files.exists(file)) return read;

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<MatchLog> loaded = GSON.fromJson(reader, LIST_TYPE);
            if (loaded == null) return read;
            for (MatchLog log : loaded) {
                if (log == null) continue;
                log.sanitize();
                if (!log.id.isEmpty()) read.add(log);
            }
        } catch (Exception e) {
            VivetSpeedrun.LOGGER.warn("Could not read game log from another install", e);
        }
        return read;
    }

    public static int adopt(List<MatchLog> theirs) {
        java.util.Set<String> known = new java.util.HashSet<>();
        for (MatchLog log : all()) known.add(log.id);

        int added = 0;
        for (MatchLog log : theirs) {
            if (log == null || log.id.isEmpty() || !known.add(log.id)) continue;
            all().add(log);
            added++;
        }
        if (added == 0) return 0;

        all().sort(java.util.Comparator.comparingLong((MatchLog log) -> log.endedAt).reversed());
        while (all().size() > MAX) {
            all().remove(all().size() - 1);
        }
        save();
        return added;
    }

    public static void load() {
        logs = new ArrayList<>();

        if (!Files.exists(FILE)) return;

        try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            List<MatchLog> loaded = GSON.fromJson(reader, LIST_TYPE);
            if (loaded == null) return;

            for (MatchLog log : loaded) {
                if (log == null) continue;
                log.sanitize();
                if (!log.id.isEmpty()) logs.add(log);
            }
        } catch (Exception e) {
            VivetSpeedrun.LOGGER.error("Could not read the game log", e);
            logs = new ArrayList<>();
        }
    }

    public static void save() {
        if (logs == null) return;

        try {
            Path parent = FILE.getParent();
            if (parent != null) Files.createDirectories(parent);

            Path temporary = FILE.resolveSibling(FILE.getFileName() + ".writing");

            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(logs, writer);
            }

            Files.move(temporary, FILE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            VivetSpeedrun.LOGGER.error("Could not save the game log", e);
        }
    }
}
