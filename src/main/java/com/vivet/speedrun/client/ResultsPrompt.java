package com.vivet.speedrun.client;

import com.vivet.speedrun.client.screen.VictoryScreen;
import com.vivet.speedrun.client.screen.WaitingScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;

import java.util.function.Supplier;

public final class ResultsPrompt {
    private static final long WINDOW_MS = 15_000L;

    private static Supplier<Screen> pending;
    private static long deadline;

    private static boolean parked;

    private ResultsPrompt() {
    }

    public static void show(Supplier<Screen> screen) {
        pending = screen;
        parked = false;
        deadline = System.currentTimeMillis() + WINDOW_MS;
        apply(Minecraft.getInstance(), true);
    }

    public static void dismiss() {
        pending = null;
        parked = false;
        deadline = 0L;
    }

    public static void park() {
        if (pending == null) return;

        parked = true;
        deadline = Long.MAX_VALUE;
        Minecraft.getInstance().gui.setScreen(null);
    }

    public static boolean isParked() {
        return parked && pending != null;
    }

    public static void unpark() {
        if (!isParked()) return;

        parked = false;
        deadline = System.currentTimeMillis() + WINDOW_MS;
        apply(Minecraft.getInstance(), true);
    }

    public static void tick(Minecraft minecraft) {
        if (pending == null || parked) return;

        if (System.currentTimeMillis() > deadline) {
            dismiss();
            return;
        }
        apply(minecraft, false);
    }

    private static void apply(Minecraft minecraft, boolean opening) {
        Screen current = minecraft.gui.screen();

        if (!opening) {
            if (isOurs(current)) return;

            if (current != null && !(current instanceof DeathScreen)
                    && !(current instanceof LevelLoadingScreen)) {
                dismiss();
                return;
            }
        }

        if (current instanceof LevelLoadingScreen) return;
        if (minecraft.player == null || !minecraft.player.isAlive()) return;

        minecraft.gui.setScreen(pending.get());
    }

    private static boolean isOurs(Screen screen) {
        return screen instanceof VictoryScreen || screen instanceof WaitingScreen
                || screen instanceof com.vivet.speedrun.client.screen.EndgameScreen
                || screen instanceof com.vivet.speedrun.client.screen.GraceScreen;
    }
}
