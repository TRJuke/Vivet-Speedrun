package com.vivet.speedrun.game;

import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.config.ConfigManager;
import com.vivet.speedrun.config.ModConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.GameType;

import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

public final class RunManager {
    private static long startedAt;

    private static int finalCentis;

    private static long holdStartedAt;

    private static long heldMillis;

    private static final long MAX_HELD_MS = 30_000L;

    public static final long CLOCK_HOLD_LIMIT_MS = 1_700L;

    private static boolean running;

    private static boolean sessionRun;
    private static Target target;
    private static Difficulty difficulty = Difficulty.MEDIUM;
    private static int rerollsUsed;

    private static boolean startPending;

    private static final Random RANDOM = new Random();

    private static Consumer<RunRecord> onRunEnded = record -> { };

    private static java.util.function.BiConsumer<String, Boolean> onTargetChosen =
            (itemId, reroll) -> { };

    private RunManager() {
    }

    public static void bind(Consumer<RunRecord> ended,
                            java.util.function.BiConsumer<String, Boolean> chosen) {
        onRunEnded = ended;
        onTargetChosen = chosen;
    }

    public static boolean isRunning() {
        return running;
    }

    public static Target target() {
        return target;
    }

    public static int elapsedSeconds() {
        return Time.toSeconds(elapsedCentis());
    }

    public static int elapsedCentis() {
        if (!running) return finalCentis;

        long now = System.currentTimeMillis();
        long held = Math.min(MAX_HELD_MS,
                heldMillis + (holdStartedAt == 0L ? 0L : now - holdStartedAt));
        return (int) Math.max(0L, (now - startedAt - held) / 10L);
    }

    public static void holdClock() {
        if (!running || holdStartedAt != 0L) return;
        holdStartedAt = System.currentTimeMillis();
    }

    public static void releaseClock() {
        if (holdStartedAt == 0L) return;

        heldMillis = Math.min(MAX_HELD_MS, heldMillis
                + Math.min(System.currentTimeMillis() - holdStartedAt, CLOCK_HOLD_LIMIT_MS));
        holdStartedAt = 0L;
    }

    public static Difficulty difficulty() {
        return difficulty;
    }

    public static int rerollsUsed() {
        return rerollsUsed;
    }

    public static void cancelPendingStart() {
        startPending = false;
    }

    public static boolean start(MinecraftServer server) {
        if (!SeedDifficulty.isReady()) {
            startPending = true;
            broadcast(server, Component.translatable("vivet.message.measuring")
                    .withStyle(ChatFormatting.GRAY));
            return false;
        }

        startPending = false;

        String itemId = Practice.isActive() ? Practice.itemId() : pickItem(null);
        if (itemId == null) {
            broadcast(server, Component.translatable("vivet.message.no_items")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        Difficulty tier = ItemTiers.classify(ItemTiers.byId(itemId),
                com.vivet.speedrun.session.Session.activeDimension());

        boolean started = startWith(server, itemId, tier);

        if (started) sessionRun = false;
        return started;
    }

    public static boolean startWith(MinecraftServer server, String itemId, Difficulty tier) {
        if (running || server == null) return false;

        if (!com.vivet.speedrun.session.Session.isActive()) {
            com.vivet.speedrun.session.Session.forgetLastMatch();
        } else {
            Practice.clear();
        }

        sessionRun = true;

        Target picked = ItemTiers.byId(itemId);
        if (picked == null) return false;

        target = picked;
        difficulty = tier;
        startedAt = System.currentTimeMillis();
        finalCentis = 0;
        rerollsUsed = 0;
        heldMillis = 0L;
        holdStartedAt = 0L;
        running = true;

        holdClock();

        NetherStart.begin(server, com.vivet.speedrun.session.Session.activeDimension());

        MatchRules.allowSpectating(false);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.gameMode() == GameType.SPECTATOR) {
                player.setGameMode(GameType.SURVIVAL);
            }

            player.setPermanentlyInvulnerable(false);
            WorldRuleApplier.giveStarterKit(player);
        }

        WorldRuleApplier.applyAll(server);
        MilestoneTracker.reset();
        RunHistory.add(new RunRecord(itemId, difficulty));

        announceStart(server);
        onTargetChosen.accept(itemId, false);
        return true;
    }

    public static void stop(MinecraftServer server, RunRecord.Outcome outcome, String winnerName,
                            String detail) {
        if (!running) return;

        finalCentis = elapsedCentis();
        running = false;

        RunRecord record = RunHistory.newest();
        if (record == null || !record.isInProgress()) {
            record = new RunRecord(target.id(), difficulty);
            RunHistory.add(record);
        }

        record.outcome = outcome;
        record.winnerName = winnerName == null ? "" : winnerName;
        record.centis = finalCentis;
        record.seconds = Time.toSeconds(finalCentis);
        record.rerollsUsed = rerollsUsed;
        record.date = System.currentTimeMillis();
        record.detail = detail == null ? "" : detail;
        RunHistory.replaceNewest(record);

        if (server != null) {
            WorldRuleApplier.restoreDefaults(server);
            protectAfterMatch(server);
        }

        onRunEnded.accept(record);
    }

    private static void protectAfterMatch(MinecraftServer server) {
        if (!sessionRun) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.setPermanentlyInvulnerable(true);
            player.setRemainingFireTicks(0);
            player.clearFire();
        }
    }

    public static void abandonQuietly() {
        if (!running) return;

        finalCentis = elapsedCentis();
        running = false;

        RunRecord record = RunHistory.newest();
        if (record != null && record.isInProgress()) {
            record.outcome = RunRecord.Outcome.LOST;
            record.centis = finalCentis;
            record.seconds = Time.toSeconds(finalCentis);
            record.date = System.currentTimeMillis();
            RunHistory.replaceNewest(record);
        }
    }

    public static void toggle(MinecraftServer server) {
        if (running) {
            stop(server, RunRecord.Outcome.LOST, "", "stopped the run");
            broadcast(server, Component.translatable("vivet.message.stopped")
                    .withStyle(ChatFormatting.RED));
        } else {
            start(server);
        }
    }

    public static void forfeit(MinecraftServer server) {
        if (!running) return;

        broadcast(server, Component.translatable("vivet.message.gave_up")
                .withStyle(ChatFormatting.RED));
        stop(server, RunRecord.Outcome.LOST, "", "gave up");
    }

    public static void winByDefault(MinecraftServer server) {
        if (!running) return;

        broadcast(server, Component.translatable("vivet.message.last_standing")
                .withStyle(ChatFormatting.GREEN));
        playSound(server, SoundEvents.PLAYER_LEVELUP);
        stop(server, RunRecord.Outcome.WON, localPlayerName(server), "last one standing");
    }

    private static String localPlayerName(MinecraftServer server) {
        if (server == null) return "";

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        return players.isEmpty() ? "" : players.get(0).getName().getString();
    }

    public static void loseRace(MinecraftServer server) {
        if (!running) return;

        stop(server, RunRecord.Outcome.LOST, "", "did not finish");
    }

    public static void spectate(MinecraftServer server) {
        if (running || server == null) return;

        MatchRules.allowSpectating(true);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.gameMode() != GameType.SPECTATOR) {
                player.setGameMode(GameType.SPECTATOR);
            }
        }
    }

    public static void draw(MinecraftServer server) {
        if (!running) return;

        broadcast(server, Component.translatable("vivet.message.draw")
                .withStyle(ChatFormatting.YELLOW));
        stop(server, RunRecord.Outcome.DRAW, "", "drew");
    }

    public static void tick(MinecraftServer server) {
        if (startPending && !running && server != null && SeedDifficulty.isReady()) {
            startPending = false;
            start(server);
        }

        if (!running || server == null || server.isPaused()) return;

        if (holdStartedAt != 0L
                && System.currentTimeMillis() - holdStartedAt >= CLOCK_HOLD_LIMIT_MS) {
            releaseClock();
        }

        WorldRuleApplier.tick(server);
        checkInventories(server);
    }

    private static void checkInventories(MinecraftServer server) {
        if (target == null) return;

        boolean suddenDeath = Modifiers.enabled(WorldRule.HARDCORE);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.gameMode() == GameType.SPECTATOR) continue;

            if (suddenDeath && player.isDeadOrDying()) {
                broadcast(server, Component.translatable("vivet.message.sudden_death")
                        .withStyle(ChatFormatting.RED));
                playSound(server, SoundEvents.ENDER_DRAGON_GROWL);
                stop(server, RunRecord.Outcome.LOST, "", "died");
                return;
            }

            if (player.getInventory().contains(target::matches)) {
                String name = player.getName().getString();

                broadcast(server, Component.translatable("vivet.message.found",
                        name, displayName(), Time.format(elapsedSeconds()))
                        .withStyle(ChatFormatting.GREEN));
                playSound(server, SoundEvents.PLAYER_LEVELUP);

                stop(server, RunRecord.Outcome.WON, name, "found it");
                return;
            }
        }
    }

    public static String reroll(MinecraftServer server, String itemId) {
        if (!running) {
            if (itemId != null) {
                VivetSpeedrun.LOGGER.warn("Dropped reroll to '{}', no run in progress", itemId);
            }
            return null;
        }

        if (itemId == null && !canReroll()) {
            broadcast(server, Component.translatable("vivet.message.no_rerolls")
                    .withStyle(ChatFormatting.RED));
            return null;
        }

        String chosen = itemId != null ? itemId : pickItem(target.id());
        if (chosen == null) return null;

        Target rerolled = ItemTiers.byId(chosen);
        if (rerolled == null) return null;

        target = rerolled;
        rerollsUsed++;

        holdClock();

        RunRecord record = RunHistory.newest();
        if (record != null && record.isInProgress()) {
            record.itemId = chosen;
            record.rerollsUsed = rerollsUsed;
            RunHistory.replaceNewest(record);
        }

        onTargetChosen.accept(chosen, true);
        return chosen;
    }

    public static boolean canReroll() {
        if (!running) return false;

        if (Practice.isActive()) return false;

        if (com.vivet.speedrun.session.Session.isPublicMatch()) return true;

        return ConfigManager.get().allowRerolls;
    }

    private static String pickItem(String avoid) {
        ModConfig config = ConfigManager.get();

        List<String> avoided = avoid == null ? List.of() : List.of(avoid);
        Target picked = ItemTiers.pick(config.difficulty, config.includeEasierItems,
                config.dimensionPool, avoided, RANDOM);
        return picked == null ? null : picked.id();
    }

    private static Resume pendingResume;

    public record Resume(String itemId, Difficulty tier, int rerollsUsed, long startedAtMillis) {
    }

    public static void armResume(Resume resume) {
        pendingResume = resume;
    }

    public static boolean resumeFromHistory(MinecraftServer server) {
        Resume resume = pendingResume;
        pendingResume = null;

        if (resume != null && restore(server, resume)) return true;

        RunRecord record = RunHistory.newest();
        if (record == null || !record.isInProgress()) return false;

        record.outcome = RunRecord.Outcome.LOST;
        if (record.detail == null || record.detail.isBlank()) record.detail = "quit the run";
        record.date = System.currentTimeMillis();
        RunHistory.replaceNewest(record);

        VivetSpeedrun.LOGGER.info("Saved interrupted run for {} as a loss",
                record.itemId);
        return false;
    }

    private static boolean restore(MinecraftServer server, Resume resume) {
        if (server == null || resume == null) return false;

        Target picked = ItemTiers.byId(resume.itemId());
        if (picked == null) return false;

        target = picked;
        difficulty = resume.tier();
        startedAt = resume.startedAtMillis();
        rerollsUsed = resume.rerollsUsed();
        finalCentis = 0;
        heldMillis = 0L;
        holdStartedAt = 0L;
        sessionRun = true;
        running = true;

        RunRecord record = RunHistory.newest();
        if (record != null && resume.itemId().equals(record.itemId)) {
            record.outcome = RunRecord.Outcome.IN_PROGRESS;
            record.detail = "";
            record.winnerName = "";
            RunHistory.replaceNewest(record);
        } else {
            RunHistory.add(new RunRecord(resume.itemId(), difficulty));
        }

        WorldRuleApplier.applyAll(server);
        MilestoneTracker.reset();

        VivetSpeedrun.LOGGER.info("Resumed interrupted run for {}",
                resume.itemId());
        return true;
    }

    public static void persist() {
        if (!running) return;

        RunRecord record = RunHistory.newest();
        if (record != null && record.isInProgress()) {
            record.seconds = elapsedSeconds();
            record.outcome = RunRecord.Outcome.LOST;
            if (record.detail == null || record.detail.isBlank()) record.detail = "quit the run";
            record.date = System.currentTimeMillis();
            RunHistory.replaceNewest(record);
        }

        finalCentis = elapsedCentis();
        running = false;
        sessionRun = false;
    }

    private static void announceStart(MinecraftServer server) {
        if (Modifiers.enabled(WorldRule.HARDCORE)) {
            broadcast(server, Component.translatable("vivet.message.sudden_death_on")
                    .withStyle(ChatFormatting.RED));
        }
        playSound(server, SoundEvents.UI_TOAST_IN);
    }

    private static Component displayName() {
        return target == null ? Component.empty() : target.displayName();
    }

    private static void broadcast(MinecraftServer server, Component message) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
        }
    }

    private static void playSound(MinecraftServer server, SoundEvent sound) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.playSound(sound, 1.0F, 1.0F);
        }
    }
}
