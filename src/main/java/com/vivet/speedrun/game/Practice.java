package com.vivet.speedrun.game;

import com.vivet.speedrun.config.ConfigManager;

public final class Practice {
    public enum World {
        FRESH,

        SET
    }

    private static String itemId;
    private static World world = World.FRESH;

    private static boolean lastRan;
    private static int lastCentis;
    private static boolean lastFinished;
    private static boolean lastWasBest;

    private Practice() {
    }

    public static void choose(String chosen) {
        itemId = chosen;
        lastRan = false;
        lastCentis = 0;
        lastFinished = false;
        lastWasBest = false;

        Long remembered = ConfigManager.get().practiceSeeds.get(chosen);
        world = remembered == null ? World.FRESH : World.SET;
    }

    public static String itemId() {
        return itemId;
    }

    public static Target target() {
        return itemId == null ? null : ItemTiers.byId(itemId);
    }

    public static boolean isActive() {
        return itemId != null;
    }

    public static World world() {
        return world;
    }

    public static void setWorld(World chosen) {
        world = chosen == null ? World.FRESH : chosen;
    }

    public static Long seed() {
        return itemId == null ? null : ConfigManager.get().practiceSeeds.get(itemId);
    }

    public static void setSeed(Long typed) {
        if (itemId == null) return;

        if (typed == null) {
            ConfigManager.get().practiceSeeds.remove(itemId);
        } else {
            ConfigManager.get().practiceSeeds.put(itemId, typed);
        }
        ConfigManager.save();
    }

    public static Long seedForNextWorld() {
        return world == World.SET ? seed() : null;
    }

    public static Long parseSeed(String typed) {
        String text = typed == null ? "" : typed.trim();
        if (text.isEmpty()) return null;

        try {
            return Long.parseLong(text);
        } catch (NumberFormatException notANumber) {
            return (long) text.hashCode();
        }
    }

    public static void noteEnded(int centis, boolean finished, boolean best) {
        lastRan = true;
        lastCentis = centis;
        lastFinished = finished;
        lastWasBest = best;
    }

    public static boolean hasLastRun() {
        return lastRan;
    }

    public static int lastCentis() {
        return lastCentis;
    }

    public static boolean lastFinished() {
        return lastFinished;
    }

    public static boolean lastWasBest() {
        return lastWasBest;
    }

    public static int bestCentis() {
        if (itemId == null) return -1;

        return PersonalBests.bestCentisOf(PersonalBests.itemKey(RunMode.SOLO, false, itemId));
    }

    public static void clear() {
        itemId = null;
        lastRan = false;
        lastCentis = 0;
        lastFinished = false;
        lastWasBest = false;
    }
}
