package com.vivet.speedrun.client;

import com.vivet.speedrun.game.Difficulty;
import com.vivet.speedrun.game.ItemTiers;
import com.vivet.speedrun.game.RunRecord;
import com.vivet.speedrun.net.Payloads;

import com.vivet.speedrun.game.Target;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public final class ClientState {
    private static boolean running;
    private static Target target;
    private static ItemStack targetStack = ItemStack.EMPTY;
    private static int elapsedCentis;
    private static Difficulty difficulty = Difficulty.EASY;
    private static int rerollsUsed;
    private static int activity;
    private static List<RunRecord> history = new ArrayList<>();

    private ClientState() {
    }

    public static void update(Payloads.RunState state) {
        running = state.running();
        elapsedCentis = state.elapsedCentis();
        difficulty = state.difficulty();
        rerollsUsed = state.rerollsUsed();
        activity = state.activity();

        target = ItemTiers.byId(state.itemId());
        targetStack = target == null ? new ItemStack(Items.BARRIER) : target.displayStack();
    }

    public static void stopped() {
        running = false;
    }

    public static void updateHistory(Payloads.History payload) {
        List<RunRecord> records = new ArrayList<>();
        for (Payloads.HistoryEntry entry : payload.entries()) {
            records.add(entry.toRecord());
        }
        history = records;
    }

    public static boolean isRunning() {
        return running;
    }

    public static ItemStack target() {
        return targetStack;
    }

    public static String targetId() {
        return target == null ? "" : target.id();
    }

    public static Component targetName() {
        return target == null ? Component.empty() : target.displayName();
    }

    public static int elapsedSeconds() {
        return com.vivet.speedrun.game.Time.toSeconds(elapsedCentis);
    }

    public static int elapsedCentis() {
        return elapsedCentis;
    }

    public static Difficulty difficulty() {
        return difficulty;
    }

    public static int rerollsUsed() {
        return rerollsUsed;
    }

    public static int activity() {
        return activity;
    }

    public static boolean canReroll() {
        if (!running) return false;

        if (com.vivet.speedrun.session.Session.isRanked()) return false;

        if (com.vivet.speedrun.session.Session.isMultiplayer()
                && !com.vivet.speedrun.session.Session.canProposeReroll()) {
            return false;
        }
        if (com.vivet.speedrun.session.Session.isPublicMatch()) return true;

        return com.vivet.speedrun.config.ConfigManager.get().allowRerolls;
    }

    public static List<RunRecord> history() {
        return history;
    }
}
