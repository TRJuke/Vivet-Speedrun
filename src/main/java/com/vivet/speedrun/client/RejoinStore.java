package com.vivet.speedrun.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.game.Difficulty;
import com.vivet.speedrun.game.DimensionPool;
import com.vivet.speedrun.session.Session;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class RejoinStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = Paths.get("config", "vivet_speedrun_rejoin.json");

    private static final long REFRESH_MS = 1_000L;

    public static final class Saved {
        public String roomCode = "";
        public Difficulty band = Difficulty.MEDIUM;

        public String worldDifficulty = "NORMAL";

        public boolean includeEasier;
        public long salt;

        public String levelId = "";

        public String targetId = "";
        public Difficulty difficulty = Difficulty.MEDIUM;
        public DimensionPool dimension = DimensionPool.ANY;
        public int rerollsUsed;

        public long runStartedAt;

        public long aliveAt;

        public long windowMs = 60_000L;

        public String opponent = "";

        public long deadline() {
            return this.aliveAt + Math.max(0L, this.windowMs);
        }

        public boolean usable() {
            return !this.roomCode.isBlank() && isWorldName(this.levelId) && this.runStartedAt > 0L;
        }

        private static boolean isWorldName(String levelId) {
            return levelId != null && !levelId.isBlank()
                    && !".".equals(levelId) && !"..".equals(levelId)
                    && !levelId.contains("/") && !levelId.contains("\\");
        }
    }

    private static Saved loaded;

    private static volatile String currentLevelId = "";

    private static long nextWriteAt;

    private static boolean announced;

    private RejoinStore() {
    }

    public static void load() {
        loaded = null;
        if (!Files.exists(FILE)) return;

        try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            Saved saved = GSON.fromJson(reader, Saved.class);
            if (saved != null && saved.usable()) loaded = saved;
        } catch (Exception e) {
            VivetSpeedrun.LOGGER.warn("Could not read saved match", e);
        }
    }

    public static Saved offered(Minecraft minecraft) {
        Saved saved = loaded;
        if (saved == null) return why(null, "no saved match");

        if (System.currentTimeMillis() >= saved.deadline()) {
            clear();
            return why(null, "rejoin window expired");
        }
        if (Session.isActive()) return why(null, "this game is already in a match");
        if (!levelExists(minecraft, saved.levelId)) {
            return why(null, "race world '" + saved.levelId + "' is missing");
        }

        why(saved, "");

        return saved;
    }

    private static String lastReason = "";

    private static Saved why(Saved offer, String reason) {
        String now = offer != null ? "offering" : reason;
        if (now.equals(lastReason)) return offer;
        lastReason = now;

        if (offer != null) {
            VivetSpeedrun.LOGGER.info("Offering rejoin");
        } else {
            VivetSpeedrun.LOGGER.info("No rejoin available: {}", reason);
        }
        return offer;
    }

    public static int secondsLeft(Saved saved) {
        if (saved == null) return 0;
        return (int) Math.max(0L, (saved.deadline() - System.currentTimeMillis()) / 1000L);
    }

    private static long levelCheckedAt;
    private static boolean levelWasThere;

    private static boolean levelExists(Minecraft minecraft, String levelId) {
        long now = System.currentTimeMillis();
        if (now - levelCheckedAt < 1_000L) return levelWasThere;

        levelCheckedAt = now;
        try {
            LevelStorageSource source = minecraft.getLevelSource();
            levelWasThere = source != null && source.levelExists(levelId);
        } catch (Exception e) {
            VivetSpeedrun.LOGGER.debug("Could not check for world '{}'", levelId, e);
            levelWasThere = false;
        }
        return levelWasThere;
    }

    public static void tick(Minecraft minecraft) {
        rememberOpenWorld(minecraft);

        if (!Session.holdsRejoin()) return;
        if (currentLevelId.isEmpty()) return;
        if (!ClientState.isRunning()) return;

        long now = System.currentTimeMillis();
        if (now < nextWriteAt) return;
        nextWriteAt = now + REFRESH_MS;

        Session.Rejoinable back = Session.wouldRejoin();
        if (back == null) return;

        Saved saved = new Saved();
        saved.roomCode = back.roomCode();
        saved.band = back.band();

        saved.levelId = currentLevelId;

        saved.targetId = ClientState.targetId();
        saved.difficulty = ClientState.difficulty();
        saved.dimension = Session.activeDimension();
        saved.rerollsUsed = ClientState.rerollsUsed();
        saved.worldDifficulty = Session.worldDifficulty().name();
        saved.includeEasier = back.includeEasier();
        saved.salt = back.salt();

        saved.runStartedAt = now - ClientState.elapsedCentis() * 10L;

        saved.aliveAt = now;
        saved.windowMs = Session.rejoinWindowMs();
        saved.opponent = opponentName();

        loaded = saved;
        write(saved);

        if (!announced) {
            announced = true;
            VivetSpeedrun.LOGGER.info("Saved rejoin for {} in world '{}'",
                    saved.roomCode, saved.levelId);
        }
    }

    private static String opponentName() {
        for (com.vivet.speedrun.session.Racer racer : Session.racers()) {
            if (!Session.isLocalName(racer.name)) return racer.name;
        }
        return "";
    }

    private static void rememberOpenWorld(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        if (server == null) return;

        try {
            currentLevelId = WorldActions.levelIdOf(server);
        } catch (Exception e) {
            VivetSpeedrun.LOGGER.debug("Could not get open world name", e);
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
            VivetSpeedrun.LOGGER.warn("Could not save match", e);
        }
    }

    public static void clear() {
        loaded = null;
        nextWriteAt = 0L;
        levelCheckedAt = 0L;
        announced = false;
        lastReason = "";

        try {
            Files.deleteIfExists(FILE);
        } catch (Exception e) {
            VivetSpeedrun.LOGGER.debug("Could not clear saved match", e);
        }
    }

    public static com.vivet.speedrun.game.MatchRules.Policy policyFor(Saved saved) {
        return com.vivet.speedrun.game.MatchRules.Policy.forPrivate(worldDifficultyOf(saved));
    }

    public static Session.Rejoinable asRejoinable(Saved saved) {
        return new Session.Rejoinable(saved.roomCode, saved.band,
                saved.dimension == null ? DimensionPool.ANY : saved.dimension,
                saved.includeEasier, saved.salt, worldDifficultyOf(saved));
    }

    private static net.minecraft.world.Difficulty worldDifficultyOf(Saved saved) {
        try {
            return net.minecraft.world.Difficulty.valueOf(saved.worldDifficulty);
        } catch (Exception e) {
            VivetSpeedrun.LOGGER.debug("Unknown saved world difficulty '{}'",
                    saved.worldDifficulty);
            return net.minecraft.world.Difficulty.NORMAL;
        }
    }
}
