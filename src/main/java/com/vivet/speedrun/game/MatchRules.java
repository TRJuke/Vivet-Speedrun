package com.vivet.speedrun.game;

import com.vivet.speedrun.VivetSpeedrun;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.WorldData;

public final class MatchRules {
    public record Policy(Difficulty difficulty, boolean locked, boolean ranked) {
        public static final Policy OPEN = new Policy(Difficulty.NORMAL, false, false);

        public static Policy forPractice() {
            return new Policy(Difficulty.HARD, true, false);
        }

        public static Policy forRanked() {
            return new Policy(Difficulty.HARD, true, true);
        }

        public static Policy forPublicQueue() {
            return new Policy(Difficulty.NORMAL, true, false);
        }

        public static Policy forPrivate(Difficulty chosen) {
            return new Policy(chosen == null ? Difficulty.NORMAL : chosen, true, false);
        }
    }

    private static volatile Policy policy = Policy.OPEN;

    private static volatile boolean armed;

    private static volatile String tamper;

    private static volatile boolean spectatingAllowed;

    private static java.util.function.Consumer<MinecraftServer> revokeCommands =
            server -> server.getWorldData().setAllowCommands(false);

    private MatchRules() {
    }

    public static void bindCommandRevoke(java.util.function.Consumer<MinecraftServer> hook) {
        if (hook != null) revokeCommands = hook;
    }

    public static void set(Policy next) {
        policy = next == null ? Policy.OPEN : next;
        armed = false;
        tamper = null;
        spectatingAllowed = false;

        if (policy.locked()) {
            VivetSpeedrun.LOGGER.info("Match locked to {}",
                    policy.difficulty().getSerializedName(), policy.ranked() ? ", ranked" : "");
        }
    }

    public static void clear() {
        set(Policy.OPEN);
    }

    public static void arm() {
        if (policy.locked()) armed = true;
    }

    public static Policy policy() {
        return policy;
    }

    public static void allowSpectating(boolean allowed) {
        spectatingAllowed = allowed;
    }

    public static boolean isLocked() {
        return policy.locked();
    }

    public static String tampered() {
        return tamper;
    }

    public static void enforce(MinecraftServer server) {
        Policy current = policy;
        if (server == null || !current.locked()) return;

        WorldData data = server.getWorldData();
        Difficulty wanted = current.difficulty();

        if (armed) {
            if (data.getDifficulty() != wanted) {
                report("difficulty changed to " + data.getDifficulty().getSerializedName());
            } else if (data.isAllowCommands()) {
                report("commands enabled");
            } else if (data.getGameType() != GameType.SURVIVAL && !sanctioned(data.getGameType())) {
                report("game mode changed to " + data.getGameType().getName());
            }
        }

        if (data.getDifficulty() != wanted || !data.isDifficultyLocked()) {
            server.setDifficulty(wanted, true);
            server.setDifficultyLocked(true);
        }

        if (data.isAllowCommands()) revokeCommands.accept(server);

        if (data.getGameType() != GameType.SURVIVAL && !sanctioned(data.getGameType())) {
            data.setGameType(GameType.SURVIVAL);
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            GameType was = player.gameMode();
            if (was == GameType.SURVIVAL || sanctioned(was)) continue;

            if (armed) report("game mode changed to " + was.getName());
            player.setGameMode(GameType.SURVIVAL);
        }
    }

    private static boolean sanctioned(GameType mode) {
        return mode == GameType.SPECTATOR && spectatingAllowed;
    }

    private static void report(String what) {
        if (tamper != null) return;

        tamper = what;
        VivetSpeedrun.LOGGER.warn("Match rules changed: {}", what);
    }
}
