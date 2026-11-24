package com.vivet.speedrun.client.replay;

import com.vivet.speedrun.mixin.MenuScreensAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

public final class ReplayScreens {
    interface Watched {
        boolean ridingWatched();

        boolean watchedHasScreenOpen();

        Screen inventoryScreen();

        void stopSpectating();

        default boolean ownKey(KeyEvent event) {
            return false;
        }

        default boolean canLeave() {
            return true;
        }

        com.vivet.speedrun.net.Payloads.Book book();

        default int bookPick() {
            return -1;
        }

        default com.vivet.speedrun.net.Payloads.Drag drag() {
            return null;
        }

        default com.vivet.speedrun.net.Payloads.OwnScreen ownScreen() {
            return null;
        }
    }

    private static Screen shown;

    private static boolean following;

    private static boolean dismissed;

    private static boolean opening;

    private ReplayScreens() {
    }

    private static Watched current() {
        ReplayPlayback playback = ReplayPlayback.get();
        if (playback != null) return playback;
        return SpectateEyes.watched();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    static Screen forMenu(AbstractContainerMenu menu, Inventory inventory, Component title) {
        Screen mount = MountMenus.screenFor(menu, inventory);
        if (mount != null) return mount;
        try {
            var constructor = MenuScreensAccessor.vivet$constructor((net.minecraft.world.inventory.MenuType) menu.getType());
            return constructor == null ? null : (Screen) ((net.minecraft.client.gui.screens.MenuScreens.ScreenConstructor) constructor)
                    .create(menu, inventory, title);
        } catch (RuntimeException unsupported) {
            return null;
        }
    }

    private static boolean ours(Screen screen) {
        return screen != null && (screen == shown || screen instanceof com.vivet.speedrun.client.screen.ReplayInventoryScreen
                || screen instanceof AbstractContainerScreen<?> container && ReplayPlayback.isReplayMenu(container.getMenu()));
    }

    static void containerChanged() {
        Minecraft minecraft = Minecraft.getInstance();
        Watched watched = current();
        if (watched == null || !ours(minecraft.gui.screen())) return;
        Screen next = watched.inventoryScreen();
        if (next != null) show(minecraft, next, following);
    }

    public static void open() {
        Watched watched = current();
        if (watched == null) return;
        Screen screen = watched.inventoryScreen();
        if (screen != null) show(Minecraft.getInstance(), screen, false);
    }

    static void follow(Watched watched) {
        if (OwnScreens.follow(Minecraft.getInstance(), watched)) return;
        followScreen(watched);

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() instanceof AbstractContainerScreen<?> screen && ReplayPlayback.isReplayMenu(screen.getMenu())) {
            CraftingBook.show(minecraft, screen, watched.book());
            CraftingBook.showPick(minecraft, screen, watched.bookPick());
            SlotDrag.show(screen, watched.drag());
        }
    }

    private static void followScreen(Watched watched) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen current = minecraft.gui.screen();
        boolean want = watched.ridingWatched() && watched.watchedHasScreenOpen();

        if (following && !ours(current)) {
            following = false;

            if (current == null && want) dismissed = true;
        }
        if (!want) {
            dismissed = false;
            if (following) {
                following = false;
                shown = null;
                minecraft.gui.setScreen(null);
            }
            return;
        }
        if (dismissed || current != null) return;
        Screen next = watched.inventoryScreen();
        if (next != null) show(minecraft, next, true);
    }

    public static boolean isFollowing(Screen screen) {
        return following && screen != null && screen == Minecraft.getInstance().gui.screen() && ours(screen);
    }

    public static boolean opening() {
        return opening;
    }

    public static boolean keyOverFollowed(KeyEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        Watched watched = current();
        if (watched == null) return false;
        if (watched.ownKey(event)) return true;
        if (minecraft.options.keyShift.matches(event) && watched.canLeave()) {
            following = false;
            shown = null;
            minecraft.gui.setScreen(null);
            watched.stopSpectating();
            return true;
        }
        return false;
    }

    static void putUp(Minecraft minecraft, Screen screen) {
        following = false;
        shown = null;
        opening = true;
        try {
            minecraft.gui.setScreen(screen);
        } finally {
            opening = false;
        }
    }

    private static void show(Minecraft minecraft, Screen screen, boolean followed) {
        shown = screen;
        following = followed;
        opening = followed;
        try {
            minecraft.gui.setScreen(screen);
        } finally {
            opening = false;
        }
    }
}
