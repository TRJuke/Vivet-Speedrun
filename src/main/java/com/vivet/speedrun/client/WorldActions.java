package com.vivet.speedrun.client;

import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.game.MatchRules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import com.vivet.speedrun.client.screen.VivetTitleScreen;

import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

public final class WorldActions {
    private static final long STOP_TIMEOUT_MS = 30_000L;

    private static final long LOAD_TIMEOUT_MS = 300_000L;

    private static final String RACE_LEVEL_NAME = "Vivet Race";

    private record Waiter(BooleanSupplier ready, Runnable action, long deadline, String what) {
    }

    private static final List<Waiter> WAITERS = new CopyOnWriteArrayList<>();

    private static volatile boolean expectingSwap;
    private static volatile long expectingSwapUntil;

    private static final long SWAP_WINDOW_MS = 15_000L;

    private WorldActions() {
    }

    public static boolean consumeExpectedSwap() {
        boolean expected = expectingSwap && System.currentTimeMillis() < expectingSwapUntil;

        expectingSwap = false;
        expectingSwapUntil = 0L;
        return expected;
    }

    private static void expectSwap() {
        expectingSwap = true;
        expectingSwapUntil = System.currentTimeMillis() + SWAP_WINDOW_MS;
    }

    public static void tick() {
        if (WAITERS.isEmpty()) return;

        for (Waiter waiter : WAITERS) {
            if (waiter.ready().getAsBoolean()) {
                WAITERS.remove(waiter);
                waiter.action().run();
            } else if (System.currentTimeMillis() > waiter.deadline()) {
                WAITERS.remove(waiter);
                VivetSpeedrun.LOGGER.warn("Timed out waiting for {}", waiter.what());
            }
        }
    }

    public static void waitFor(BooleanSupplier ready, Runnable action, long timeoutMs, String what) {
        WAITERS.add(new Waiter(ready, action, System.currentTimeMillis() + timeoutMs, what));
    }

    public static void cancelWaits() {
        WAITERS.clear();
    }

    public static void recreateWorld(Minecraft minecraft, Long seed, Runnable onLoaded) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            VivetSpeedrun.LOGGER.warn("Not in a singleplayer world, cannot reset");
            return;
        }

        String levelId = levelIdOf(server);
        expectSwap();
        minecraft.disconnect(new TitleScreen(), false);

        whenServerStopped(minecraft, () -> {
            deleteLevelFolder(minecraft, levelId);
            createFreshLevel(minecraft, levelId, seed, onLoaded);
        });
    }

    public static void openSeededWorld(Minecraft minecraft, Long seed, Runnable onLoaded) {
        String levelId = nextRaceLevelId(minecraft);

        if (minecraft.getSingleplayerServer() == null) {
            sweepRaceWorlds(minecraft, levelId);
            createFreshLevel(minecraft, levelId, seed, onLoaded);
            return;
        }

        expectSwap();
        minecraft.disconnect(new TitleScreen(), false);
        whenServerStopped(minecraft, () -> {
            sweepRaceWorlds(minecraft, levelId);
            createFreshLevel(minecraft, levelId, seed, onLoaded);
        });
    }

    public static void openExistingWorld(Minecraft minecraft, String levelId, Runnable onLoaded) {
        if (levelId == null || levelId.isBlank()) return;

        if (minecraft.getSingleplayerServer() == null) {
            openLevel(minecraft, levelId, onLoaded);
            return;
        }

        expectSwap();
        minecraft.disconnect(new TitleScreen(), false);
        whenServerStopped(minecraft, () -> openLevel(minecraft, levelId, onLoaded));
    }

    private static void openLevel(Minecraft minecraft, String levelId, Runnable onLoaded) {
        try {
            claim(levelId);
            VivetSpeedrun.LOGGER.info("Reopening world '{}'", levelId);

            WorldOpenFlows flows = minecraft.createWorldOpenFlows();

            flows.openWorld(levelId, () -> minecraft.gui.setScreen(new VivetTitleScreen()));

            if (onLoaded != null) whenServerRunning(minecraft, onLoaded);
        } catch (Exception e) {
            VivetSpeedrun.LOGGER.error("Could not reopen world '{}'", levelId, e);
        }
    }

    public static void deleteWorld(Minecraft minecraft) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            minecraft.gui.setScreen(new TitleScreen());
            return;
        }

        String levelId = levelIdOf(server);
        minecraft.disconnect(new TitleScreen(), false);

        whenServerStopped(minecraft, () -> {
            deleteLevelFolder(minecraft, levelId);
            VivetSpeedrun.LOGGER.info("Deleted world '{}'", levelId);
        });
    }

    public static void quitRace(Minecraft minecraft) {
        quitRaceTo(minecraft, TitleScreen::new);
    }

    public static void returnFromMatch(String reason, boolean won) {
        trace("back to the queue (" + reason + ")");
        Minecraft minecraft = Minecraft.getInstance();
        ResultsPrompt.dismiss();

        if (com.vivet.speedrun.session.Session.isActive()) {
            com.vivet.speedrun.session.Session.quit();
        }

        int tone = won ? com.vivet.speedrun.client.ui.Theme.SUCCESS
                : com.vivet.speedrun.client.ui.Theme.TEXT_MUTED;

        if (com.vivet.speedrun.session.Session.wasRanked()) {
            com.vivet.speedrun.client.screen.RankedScreen.carryStatus(
                    com.vivet.speedrun.session.Session.wasRankedSolo()
                            ? com.vivet.speedrun.client.screen.RankedScreen.Mode.SOLO
                            : com.vivet.speedrun.client.screen.RankedScreen.Mode.VERSUS,
                    reason, tone);
            quitRaceTo(minecraft,
                    com.vivet.speedrun.client.screen.RankedScreen::returnDestination);
            return;
        }

        com.vivet.speedrun.client.screen.FindMatchScreen.carryStatus(reason, tone);
        quitRaceTo(minecraft,
                com.vivet.speedrun.client.screen.FindMatchScreen::returnDestination);
    }

    public static void quitRaceTo(Minecraft minecraft,
                                  java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> destination) {
        if (leavingRace()) return;

        trace("leaving the world");

        com.vivet.speedrun.p2p.Achievements.stale();
        cancelWaits();

        MatchRules.clear();

        com.vivet.speedrun.client.hud.ItemRoll.cancel();

        com.vivet.speedrun.client.screen.VictoryScreen.clearNotice();

        com.vivet.speedrun.client.Cues.clear();

        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            if (minecraft.getConnection() != null) {
                if (com.vivet.speedrun.session.Session.isHosted() && HostedRejoin.inRace()) {
                    leaveRunningRace(minecraft, destination);
                    return;
                }
                closeRaceConnection(minecraft, destination);
                return;
            }
            minecraft.gui.setScreen(destination.get());
            sweepRaceWorlds(minecraft, null);
            return;
        }

        String levelId = levelIdOf(server);
        minecraft.disconnect(destination.get(), false);

        whenServerStopped(minecraft, () -> {
            if (isRaceLevel(levelId)) deleteLevelFolder(minecraft, levelId);
            sweepRaceWorlds(minecraft, null);
        });
    }

    private static final long LEAVE_WAIT_MS = 1_500L;

    private static volatile long leavingUntil;

    private static volatile boolean raceEndedWhileLeaving;

    private static void leaveRunningRace(Minecraft minecraft,
                                         java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> destination) {
        VivetSpeedrun.LOGGER.info("Exit: giving up the current race first");
        long deadline = System.currentTimeMillis() + LEAVE_WAIT_MS;
        leavingUntil = deadline + 5_000L;
        raceEndedWhileLeaving = false;

        HostedRejoin.clear();

        Network.forfeit();
        com.vivet.speedrun.p2p.RankedApi.hostedGiveUp();

        waitFor(() -> raceEndedWhileLeaving || System.currentTimeMillis() >= deadline
                        || minecraft.getConnection() == null,
                () -> closeRaceConnection(minecraft, destination),
                LEAVE_WAIT_MS + 10_000L, "the race to end");
    }

    public static boolean raceEndedOnTheWayOut() {
        if (!leavingRace()) return false;
        raceEndedWhileLeaving = true;
        VivetSpeedrun.LOGGER.info("Race ended while leaving");
        return true;
    }

    public static void trace(String what) {
        VivetSpeedrun.LOGGER.info("Exit: {}", what);
    }

    private static boolean leavingRace() {
        return System.currentTimeMillis() < leavingUntil;
    }

    private static void closeRaceConnection(Minecraft minecraft,
                                            java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> destination) {
        leavingUntil = 0L;
        raceEndedWhileLeaving = false;
        HostedRejoin.clear();
        com.vivet.speedrun.session.Session.endHostedSolo();

        if (minecraft.level != null) {
            minecraft.level.disconnect(
                    net.minecraft.client.multiplayer.ClientLevel.DEFAULT_QUIT_MESSAGE);
        }
        minecraft.disconnect(destination.get(), false);
    }

    public static void hopRaceServer(Minecraft minecraft, Runnable then) {
        trace("joining the next race server");
        com.vivet.speedrun.p2p.Achievements.stale();
        leavingUntil = 0L;
        raceEndedWhileLeaving = false;
        cancelWaits();
        com.vivet.speedrun.client.hud.ItemRoll.cancel();
        com.vivet.speedrun.client.screen.VictoryScreen.clearNotice();

        if (minecraft.getConnection() != null) {
            expectSwap();
            if (minecraft.level != null) {
                minecraft.level.disconnect(
                        net.minecraft.client.multiplayer.ClientLevel.DEFAULT_QUIT_MESSAGE);
            }
            minecraft.disconnect(
                    com.vivet.speedrun.client.screen.FindMatchScreen.returnDestination(), false);
        }
        then.run();
    }

    private static boolean isRaceLevel(String levelId) {
        return levelId != null && levelId.startsWith(RACE_LEVEL_NAME);
    }

    private static void sweepRaceWorlds(Minecraft minecraft, String keep) {
        LevelStorageSource source = minecraft.getLevelSource();

        try (var entries = Files.list(source.getBaseDir())) {
            List<String> stale = entries
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(WorldActions::isRaceLevel)
                    .filter(id -> !id.equals(keep))
                    .toList();

            for (String id : stale) {
                deleteLevelFolder(minecraft, id);
                VivetSpeedrun.LOGGER.info("Deleted race world '{}'", id);
            }
        } catch (Exception e) {
            VivetSpeedrun.LOGGER.debug("Could not clean up race worlds", e);
        }
    }

    private static String nextRaceLevelId(Minecraft minecraft) {
        return nextLevelId(minecraft, RACE_LEVEL_NAME);
    }

    private static String nextLevelId(Minecraft minecraft, String prefix) {
        LevelStorageSource source = minecraft.getLevelSource();

        for (int suffix = 1; suffix < 1000; suffix++) {
            String candidate = suffix == 1 ? prefix : prefix + " " + suffix;
            try {
                if (!source.levelExists(candidate) && source.isNewLevelIdAcceptable(candidate)) {
                    return candidate;
                }
            } catch (Exception e) {
                VivetSpeedrun.LOGGER.debug("Could not check world name", e);
            }
        }
        return prefix + " " + System.currentTimeMillis();
    }

    private static void createFreshLevel(Minecraft minecraft, String levelId, Long seed,
                                         Runnable onLoaded) {
        try {
            claim(levelId);

            MatchRules.Policy rules = MatchRules.policy();

            LevelSettings settings = new LevelSettings(
                    levelId,
                    GameType.SURVIVAL,
                    new LevelSettings.DifficultySettings(
                            rules.difficulty(), false, rules.locked()),
                    false,
                    WorldDataConfiguration.DEFAULT);

            VivetSpeedrun.LOGGER.info("Creating world '{}'", levelId);

            WorldOpenFlows flows = minecraft.createWorldOpenFlows();
            flows.createFreshLevel(
                    levelId,
                    settings,
                    seed == null ? WorldOptions.defaultWithRandomSeed() : new WorldOptions(seed, true, false),
                    WorldPresets::createNormalWorldDimensions,
                    null);

            if (onLoaded != null) whenServerRunning(minecraft, onLoaded);
        } catch (Exception e) {
            VivetSpeedrun.LOGGER.error("Could not create a new world", e);
        }
    }

    private static void deleteLevelFolder(Minecraft minecraft, String levelId) {
        if (levelId == null || levelId.isBlank()
                || ".".equals(levelId) || "..".equals(levelId)
                || levelId.contains("/") || levelId.contains("\\")) {
            VivetSpeedrun.LOGGER.error("Not deleting '{}', not a race world", levelId);
            return;
        }

        LevelStorageSource source = minecraft.getLevelSource();
        try (LevelStorageSource.LevelStorageAccess access = source.createAccess(levelId)) {
            access.deleteLevel();
        } catch (IOException e) {
            VivetSpeedrun.LOGGER.error("Could not delete world '{}'", levelId, e);
        }
    }

    private static String ourLevelId = "";

    private static void claim(String levelId) {
        ourLevelId = levelId == null ? "" : levelId;
    }

    public static void disown() {
        ourLevelId = "";
    }

    public static boolean inModWorld(Minecraft minecraft) {
        if (minecraft == null || ourLevelId.isEmpty()) return false;

        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null) return false;

        String levelId = levelIdOf(server);
        if (!levelId.equals(ourLevelId)) return false;

        return levelId.startsWith(RACE_LEVEL_NAME);
    }

    static String levelIdOf(IntegratedServer server) {
        return server.getWorldPath(LevelResource.ROOT).normalize().getFileName().toString();
    }

    private static void whenServerStopped(Minecraft minecraft, Runnable action) {
        waitFor(() -> minecraft.getSingleplayerServer() == null, action,
                STOP_TIMEOUT_MS, "the world to close");
    }

    private static void whenServerRunning(Minecraft minecraft, Runnable action) {
        waitFor(() -> minecraft.getSingleplayerServer() != null && minecraft.player != null,
                action, LOAD_TIMEOUT_MS, "the world to load");
    }
}
