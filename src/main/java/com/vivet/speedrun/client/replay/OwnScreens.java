package com.vivet.speedrun.client.replay;

import com.vivet.speedrun.mixin.AnvilNameAccessor;
import com.vivet.speedrun.mixin.BookEditAccessor;
import com.vivet.speedrun.mixin.BookViewAccessor;
import com.vivet.speedrun.mixin.SignEditAccessor;
import com.vivet.speedrun.net.Payloads;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.HangingSignEditScreen;
import net.minecraft.client.gui.screens.inventory.LecternScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.level.block.entity.HangingSignBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;

import java.util.ArrayList;
import java.util.List;

public final class OwnScreens {
    private static Screen mirror;
    private static String mirrorKey;

    private static String dismissed;

    private static boolean covered;

    private static final List<String> seenPages = new ArrayList<>();

    private OwnScreens() {
    }

    public static boolean isOwnScreen(Screen screen) {
        return screen instanceof AbstractSignEditScreen || screen instanceof BookEditScreen
                || screen instanceof net.minecraft.client.gui.screens.inventory.BookSignScreen
                || (screen instanceof BookViewScreen && !(screen instanceof LecternScreen));
    }

    public static boolean isMirror(Object screen) {
        return screen != null && screen == mirror;
    }

    public static Payloads.OwnScreen read(Minecraft minecraft) {
        Screen screen = minecraft.gui.screen();
        if (screen instanceof AbstractSignEditScreen sign) {
            SignEditAccessor parts = (SignEditAccessor) sign;
            List<String> lines = new ArrayList<>();
            for (String line : parts.vivet$messages()) lines.add(clip(line, Payloads.OwnScreen.MAX_TEXT));
            return new Payloads.OwnScreen(Payloads.OwnScreen.SIGN, 0, 0, parts.vivet$line(), lines,
                    parts.vivet$sign().getBlockPos().asLong(), parts.vivet$slot() == SignTextSlot.FRONT);
        }
        if (screen instanceof BookEditScreen book) {
            BookEditAccessor parts = (BookEditAccessor) book;
            List<String> pages = parts.vivet$pages();
            int page = parts.vivet$currentPage();
            String text = page >= 0 && page < pages.size() ? pages.get(page) : "";
            return new Payloads.OwnScreen(Payloads.OwnScreen.BOOK_EDIT, page, pages.size(), 0,
                    List.of(clip(text, Payloads.OwnScreen.MAX_TEXT)), 0L, false);
        }
        if (screen instanceof BookViewScreen view && !(screen instanceof LecternScreen)) {
            BookViewAccessor parts = (BookViewAccessor) view;
            BookViewScreen.BookAccess book = parts.vivet$book();
            int page = parts.vivet$currentPage();
            int count = book == null ? 0 : book.getPageCount();
            String text = book != null && page >= 0 && page < count ? book.getPage(page).getString() : "";
            return new Payloads.OwnScreen(Payloads.OwnScreen.BOOK_READ, page, count, 0,
                    List.of(clip(text, Payloads.OwnScreen.MAX_TEXT)), 0L, false);
        }
        if (screen instanceof net.minecraft.client.gui.screens.inventory.BookSignScreen signing) {
            EditBox title = ((com.vivet.speedrun.mixin.BookSignAccessor) signing).vivet$title();
            return new Payloads.OwnScreen(Payloads.OwnScreen.BOOK_SIGN, 0, 0, 0,
                    List.of(clip(title == null ? "" : title.getValue(), Payloads.OwnScreen.MAX_TEXT)), 0L, false);
        }
        if (screen instanceof AnvilScreen anvil) {
            EditBox name = ((AnvilNameAccessor) anvil).vivet$name();
            if (name != null) {
                return new Payloads.OwnScreen(Payloads.OwnScreen.ANVIL, 0, 0, 0,
                        List.of(clip(name.getValue(), Payloads.OwnScreen.MAX_TEXT)), 0L, false);
            }
        }
        return Payloads.OwnScreen.NONE;
    }

    private static String clip(String text, int most) {
        if (text == null) return "";
        return text.length() > most ? text.substring(0, most) : text;
    }

    static boolean follow(Minecraft minecraft, ReplayScreens.Watched watched) {
        Payloads.OwnScreen want = watched.ridingWatched() ? watched.ownScreen() : null;
        Screen current = minecraft.gui.screen();
        int kind = want == null ? Payloads.OwnScreen.NONE_KIND : want.kind();

        if (kind == Payloads.OwnScreen.ANVIL && current instanceof AnvilScreen anvil
                && ReplayPlayback.isReplayMenu(anvil.getMenu())) {
            EditBox name = ((AnvilNameAccessor) anvil).vivet$name();
            String text = want.text().isEmpty() ? "" : want.text().get(0);
            if (name != null && !name.getValue().equals(text)) name.setValue(text);
        }
        if (kind != Payloads.OwnScreen.SIGN && kind != Payloads.OwnScreen.BOOK_EDIT
                && kind != Payloads.OwnScreen.BOOK_READ && kind != Payloads.OwnScreen.BOOK_SIGN) {
            if (mirror != null) {
                if (current == mirror) {
                    minecraft.gui.setScreen(null);
                    click(minecraft);
                }
                mirror = null;
                mirrorKey = null;
                seenPages.clear();
            }
            dismissed = null;
            covered = false;
            return false;
        }

        String key = kind + "@" + want.pos() + "/" + want.front();
        if (key.equals(dismissed)) return true;
        if (mirror != null && current != mirror) {
            if (current != null) {
                covered = true;
                return true;
            }
            if (covered) {
                covered = false;
                ReplayScreens.putUp(minecraft, mirror);
            } else {
                dismissed = mirrorKey;
                mirror = null;
                mirrorKey = null;
                seenPages.clear();
                return true;
            }
        }
        if (mirror == null || !key.equals(mirrorKey)) {
            if (current != null && current != mirror && !ReplayScreens.isFollowing(current)) return true;
            if (mirror != null) click(minecraft);
            seenPages.clear();
            Screen copy = build(minecraft, want);
            if (copy == null) return true;

            ReplayScreens.putUp(minecraft, copy);
            mirror = copy;
            mirrorKey = key;
        }
        update(mirror, want);
        return true;
    }

    private static void click(Minecraft minecraft) {
        minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private static void pageTurn() {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.0F));
    }

    private static Screen build(Minecraft minecraft, Payloads.OwnScreen want) {
        if (want.kind() == Payloads.OwnScreen.SIGN) {
            if (minecraft.level == null) return null;
            if (!(minecraft.level.getBlockEntity(BlockPos.of(want.pos())) instanceof SignBlockEntity sign)) return null;
            SignTextSlot side = want.front() ? SignTextSlot.FRONT : SignTextSlot.BACK;
            return sign instanceof HangingSignBlockEntity
                    ? new HangingSignEditScreen(sign, side, false)
                    : new SignEditScreen(sign, side, false);
        }
        if (want.kind() == Payloads.OwnScreen.BOOK_SIGN) {
            net.minecraft.world.entity.player.Player owner = ReplayPlayback.hudPlayer();
            if (owner == null) owner = minecraft.player;
            if (owner == null) return null;
            BookEditScreen book = new BookEditScreen(owner, new ItemStack(Items.WRITABLE_BOOK), InteractionHand.MAIN_HAND,
                    WritableBookContent.EMPTY);
            return new net.minecraft.client.gui.screens.inventory.BookSignScreen(book, owner, InteractionHand.MAIN_HAND,
                    List.of());
        }
        if (want.kind() == Payloads.OwnScreen.BOOK_EDIT) {
            if (minecraft.player == null) return null;
            List<Filterable<String>> pages = new ArrayList<>();
            for (int i = 0; i < Math.max(1, want.pages()); i++) pages.add(Filterable.passThrough(""));
            return new BookEditScreen(minecraft.player, new ItemStack(Items.WRITABLE_BOOK), InteractionHand.MAIN_HAND,
                    new WritableBookContent(pages));
        }
        return new BookViewScreen(new BookViewScreen.BookAccess(List.of(Component.empty())));
    }

    private static void update(Screen screen, Payloads.OwnScreen want) {
        String first = want.text().isEmpty() ? "" : want.text().get(0);
        if (screen instanceof net.minecraft.client.gui.screens.inventory.BookSignScreen signing) {
            EditBox title = ((com.vivet.speedrun.mixin.BookSignAccessor) signing).vivet$title();
            if (title != null && !title.getValue().equals(first)) title.setValue(first);
            return;
        }
        if (screen instanceof AbstractSignEditScreen sign) {
            SignEditAccessor parts = (SignEditAccessor) sign;
            String[] lines = parts.vivet$messages();
            for (int i = 0; i < lines.length; i++) lines[i] = i < want.text().size() ? want.text().get(i) : "";
            parts.vivet$setLine(Math.max(0, Math.min(lines.length - 1, want.line())));
            return;
        }
        if (screen instanceof BookEditScreen book) {
            BookEditAccessor parts = (BookEditAccessor) book;
            List<String> pages = parts.vivet$pages();
            int page = Math.max(0, Math.min(WritableBookContent.MAX_PAGES - 1, want.page()));
            while (pages.size() <= page) pages.add("");
            boolean changed = !first.equals(pages.get(page)) || parts.vivet$currentPage() != page;
            if (!changed) return;
            if (parts.vivet$currentPage() != page) pageTurn();
            pages.set(page, first);
            parts.vivet$setCurrentPage(page);
            parts.vivet$updatePageContent();
            parts.vivet$updateButtonVisibility();
            return;
        }
        if (screen instanceof BookViewScreen view) {
            int count = Math.max(1, want.pages());
            int page = Math.max(0, Math.min(count - 1, want.page()));
            while (seenPages.size() < count) seenPages.add("");
            boolean changed = !first.equals(seenPages.get(page)) || ((BookViewAccessor) view).vivet$currentPage() != page
                    || ((BookViewAccessor) view).vivet$book().getPageCount() != count;
            if (!changed) return;
            if (((BookViewAccessor) view).vivet$currentPage() != page) pageTurn();
            seenPages.set(page, first);
            List<Component> pages = new ArrayList<>();
            for (int i = 0; i < count; i++) pages.add(Component.literal(seenPages.get(i)));
            view.setBookAccess(new BookViewScreen.BookAccess(pages));
            view.setPage(page);
        }
    }
}
