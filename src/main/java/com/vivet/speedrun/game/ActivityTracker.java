package com.vivet.speedrun.game;

import net.minecraft.server.level.ServerPlayer;

public final class ActivityTracker {
    private static final int SAMPLE_TICKS = 10;

    private static final double MOVE_THRESHOLD = 0.5;

    private static final float LOOK_THRESHOLD = 5.0F;

    private static int counter;

    private static int sinceSample;
    private static double lastX;
    private static double lastY;
    private static double lastZ;
    private static float lastYaw;
    private static float lastPitch;
    private static int lastSlot = -1;
    private static int lastContainer = -1;
    private static int lastInventoryHash;
    private static boolean primed;

    private ActivityTracker() {
    }

    public static int counter() {
        return counter;
    }

    public static void reset() {
        counter = 0;
        sinceSample = 0;
        primed = false;
        lastSlot = -1;
        lastContainer = -1;
        lastInventoryHash = 0;
    }

    public static void tick(ServerPlayer player) {
        if (player == null) return;
        if (++sinceSample < SAMPLE_TICKS) return;
        sinceSample = 0;

        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        int slot = player.getInventory().getSelectedSlot();
        int container = player.containerMenu == null ? -1 : player.containerMenu.containerId;
        int inventory = inventoryHash(player);

        if (!primed) {
            primed = true;
            remember(x, y, z, yaw, pitch, slot, container, inventory);
            return;
        }

        double moved = Math.sqrt(square(x - lastX) + square(y - lastY) + square(z - lastZ));
        if (moved > MOVE_THRESHOLD) counter += 1 + (int) moved;

        if (Math.abs(angleBetween(yaw, lastYaw)) > LOOK_THRESHOLD
                || Math.abs(pitch - lastPitch) > LOOK_THRESHOLD) {
            counter += 1;
        }

        if (slot != lastSlot) counter += 1;
        if (container != lastContainer) counter += 1;
        if (inventory != lastInventoryHash) counter += 1;

        remember(x, y, z, yaw, pitch, slot, container, inventory);
    }

    private static void remember(double x, double y, double z, float yaw, float pitch,
                                 int slot, int container, int inventory) {
        lastX = x;
        lastY = y;
        lastZ = z;
        lastYaw = yaw;
        lastPitch = pitch;
        lastSlot = slot;
        lastContainer = container;
        lastInventoryHash = inventory;
    }

    private static int inventoryHash(ServerPlayer player) {
        var inventory = player.getInventory();

        int hash = 17;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            var stack = inventory.getItem(slot);
            hash = hash * 31 + (stack.isEmpty()
                    ? 0 : stack.getItem().hashCode() * 31 + stack.getCount());
        }
        return hash;
    }

    private static float angleBetween(float a, float b) {
        float difference = (a - b) % 360.0F;
        if (difference > 180.0F) difference -= 360.0F;
        if (difference < -180.0F) difference += 360.0F;
        return difference;
    }

    private static double square(double value) {
        return value * value;
    }
}
