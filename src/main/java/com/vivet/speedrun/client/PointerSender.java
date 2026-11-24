package com.vivet.speedrun.client;

import com.vivet.speedrun.client.replay.ReplayPlayback;
import com.vivet.speedrun.mixin.ContainerScreenAccessor;
import com.vivet.speedrun.net.Payloads;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

final class PointerSender {
    private static boolean open;
    private static int lastX = Integer.MIN_VALUE;
    private static int lastY = Integer.MIN_VALUE;

    private PointerSender() {
    }

    private static Payloads.Book lastBook;

    private static Integer lastPick;

    private static Payloads.Drag lastDrag;

    private static int lastJump = -1;

    private static Payloads.OwnScreen lastOwn;

    static void tick(Minecraft minecraft) {
        sendBook(minecraft);
        sendDrag(minecraft);
        sendRideJump(minecraft);
        sendOwnScreen(minecraft);
        if (minecraft.getConnection() == null || ReplayPlayback.isActive() || Spectating.isActive()
                || !ClientPlayNetworking.canSend(Payloads.Pointer.TYPE)) {
            open = false;
            return;
        }
        net.minecraft.client.gui.screens.Screen screen = minecraft.gui.screen();
        boolean container = screen instanceof AbstractContainerScreen<?>;

        boolean own = !container && com.vivet.speedrun.client.replay.OwnScreens.isOwnScreen(screen);
        if (!container && !own) {
            if (open) {
                open = false;
                ClientPlayNetworking.send(new Payloads.Pointer(false, (short) 0, (short) 0));
            }
            return;
        }

        var window = minecraft.getWindow();
        double mouseX = minecraft.mouseHandler.xpos() * screen.width / Math.max(1, window.getScreenWidth());
        double mouseY = minecraft.mouseHandler.ypos() * screen.height / Math.max(1, window.getScreenHeight());
        int left = container ? ((ContainerScreenAccessor) (Object) screen).vivet$leftPos() : screen.width / 2;
        int top = container ? ((ContainerScreenAccessor) (Object) screen).vivet$topPos() : screen.height / 2;
        int x = clamp((int) Math.round((mouseX - left) * 4));
        int y = clamp((int) Math.round((mouseY - top) * 4));
        if (open && x == lastX && y == lastY) return;
        open = true;
        lastX = x;
        lastY = y;
        ClientPlayNetworking.send(new Payloads.Pointer(true, (short) x, (short) y));
    }

    private static void sendBook(Minecraft minecraft) {
        if (minecraft.getConnection() == null || ReplayPlayback.isActive() || Spectating.isActive()
                || !ClientPlayNetworking.canSend(Payloads.Book.TYPE)) {
            lastBook = null;
            lastPick = null;
            return;
        }
        Payloads.Book book = com.vivet.speedrun.client.replay.CraftingBook.read(minecraft);
        if (!book.equals(lastBook)) {
            lastBook = book;
            ClientPlayNetworking.send(book);
        }

        if (!ClientPlayNetworking.canSend(Payloads.BookPick.TYPE)) {
            lastPick = null;
            return;
        }
        int pick = com.vivet.speedrun.client.replay.CraftingBook.readPick(minecraft);
        if (lastPick != null && lastPick == pick) return;
        lastPick = pick;
        ClientPlayNetworking.send(new Payloads.BookPick(pick));
    }

    private static void sendDrag(Minecraft minecraft) {
        if (minecraft.getConnection() == null || ReplayPlayback.isActive() || Spectating.isActive()
                || !ClientPlayNetworking.canSend(Payloads.Drag.TYPE)) {
            lastDrag = null;
            return;
        }
        Payloads.Drag drag = com.vivet.speedrun.client.replay.SlotDrag.read(minecraft);
        if (drag.equals(lastDrag)) return;
        lastDrag = drag;
        ClientPlayNetworking.send(drag);
    }

    private static void sendOwnScreen(Minecraft minecraft) {
        if (minecraft.getConnection() == null || ReplayPlayback.isActive() || Spectating.isActive()
                || !ClientPlayNetworking.canSend(Payloads.OwnScreen.TYPE)) {
            lastOwn = null;
            return;
        }
        Payloads.OwnScreen screen = com.vivet.speedrun.client.replay.OwnScreens.read(minecraft);
        if (screen.equals(lastOwn)) return;
        lastOwn = screen;
        ClientPlayNetworking.send(screen);
    }

    private static void sendRideJump(Minecraft minecraft) {
        if (minecraft.getConnection() == null || minecraft.player == null || ReplayPlayback.isActive()
                || Spectating.isActive() || !ClientPlayNetworking.canSend(Payloads.RideJump.TYPE)) {
            lastJump = -1;
            return;
        }
        int percent = minecraft.player.jumpableVehicle() == null ? 0
                : Math.round(Math.max(0.0F, Math.min(1.0F, minecraft.player.getJumpRidingScale())) * 100.0F);
        if (percent == lastJump) return;
        lastJump = percent;
        ClientPlayNetworking.send(new Payloads.RideJump(percent));
    }

    private static int clamp(int value) {
        return Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
    }
}
