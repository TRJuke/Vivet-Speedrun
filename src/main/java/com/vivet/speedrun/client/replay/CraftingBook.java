package com.vivet.speedrun.client.replay;

import com.vivet.speedrun.mixin.RecipeBookComponentAccessor;
import com.vivet.speedrun.mixin.RecipeBookPageAccessor;
import com.vivet.speedrun.mixin.RecipeBookScreenAccessor;
import com.vivet.speedrun.net.Payloads;

import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.world.inventory.RecipeBookType;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

public final class CraftingBook {
    private static Screen laidOut;
    private static boolean laidOutOpen;
    private static boolean laidOutCraftable;

    private CraftingBook() {
    }

    public static Payloads.Book read(Minecraft minecraft) {
        if (!(minecraft.gui.screen() instanceof AbstractRecipeBookScreen<?> screen) || minecraft.player == null) {
            return Payloads.Book.SHUT;
        }
        RecipeBookComponent<?> component = ((RecipeBookScreenAccessor) screen).vivet$book();
        if (component == null || !component.isVisible()) return Payloads.Book.SHUT;
        RecipeBookComponentAccessor book = (RecipeBookComponentAccessor) component;
        int tab = Math.max(0, book.vivet$tabs().indexOf(book.vivet$selectedTab()));
        int page = book.vivet$page() == null ? 0 : ((RecipeBookPageAccessor) book.vivet$page()).vivet$currentPage();
        boolean craftable = minecraft.player.getRecipeBook().isFiltering(screen.getMenu().getRecipeBookType());
        EditBox box = book.vivet$searchBox();
        String search = box == null ? "" : box.getValue();
        if (search.length() > Payloads.Book.MAX_SEARCH) search = search.substring(0, Payloads.Book.MAX_SEARCH);
        return new Payloads.Book(true, tab, page, craftable, search);
    }

    public static int readPick(Minecraft minecraft) {
        if (!(minecraft.gui.screen() instanceof AbstractRecipeBookScreen<?> screen)) return -1;
        RecipeBookComponent<?> component = ((RecipeBookScreenAccessor) screen).vivet$book();
        if (component == null || !component.isVisible()) return -1;
        if (!(((RecipeBookComponentAccessor) component).vivet$page() instanceof RecipeBookPageAccessor page)) return -1;
        OverlayRecipeComponent overlay = page.vivet$overlay();
        if (overlay == null || !overlay.isVisible()) return -1;
        List<RecipeButton> buttons = page.vivet$buttons();
        for (int i = 0; i < buttons.size(); i++) {
            if (buttons.get(i).visible && buttons.get(i).getCollection() == overlay.getRecipeCollection()) return i;
        }
        return -1;
    }

    private static Screen ghostOn;
    private static List<ItemStack> ghostUnder = List.of();

    static void ghost(Minecraft minecraft, ClientboundPlaceGhostRecipePacket ghost) {
        if (!(minecraft.gui.screen() instanceof AbstractRecipeBookScreen<?> screen)) return;
        if (!ReplayPlayback.isReplayMenu(screen.getMenu()) || screen.getMenu().containerId != ghost.containerId()) return;
        screen.fillGhostRecipe(ghost.recipeDisplay());
        ghostOn = screen;
        ghostUnder = craftingSlots(screen);
    }

    private static void wipeGhost(AbstractRecipeBookScreen<?> screen, RecipeBookComponent<?> component) {
        if (ghostOn != screen) return;
        List<ItemStack> now = craftingSlots(screen);
        if (sameStacks(now, ghostUnder)) return;
        ((RecipeBookComponentAccessor) component).vivet$ghostSlots().clear();
        ghostOn = null;
        ghostUnder = List.of();
    }

    private static List<ItemStack> craftingSlots(AbstractRecipeBookScreen<?> screen) {
        RecipeBookComponent<?> component = ((RecipeBookScreenAccessor) screen).vivet$book();
        if (component == null) return List.of();
        RecipeBookComponentAccessor parts = (RecipeBookComponentAccessor) component;
        List<ItemStack> out = new ArrayList<>();
        for (Slot slot : screen.getMenu().slots) {
            if (parts.vivet$isCraftingSlot(slot)) out.add(slot.getItem().copy());
        }
        return out;
    }

    private static boolean sameStacks(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (!ItemStack.matches(a.get(i), b.get(i))) return false;
        return true;
    }

    static void showPick(Minecraft minecraft, Screen screen, int pick) {
        if (!(screen instanceof AbstractRecipeBookScreen<?> followed) || minecraft.level == null) return;
        RecipeBookComponent<?> component = ((RecipeBookScreenAccessor) followed).vivet$book();
        if (component == null) return;
        wipeGhost(followed, component);
        if (!component.isVisible()) return;
        RecipeBookComponentAccessor parts = (RecipeBookComponentAccessor) component;
        if (!(parts.vivet$page() instanceof RecipeBookPageAccessor page)) return;
        OverlayRecipeComponent overlay = page.vivet$overlay();
        if (overlay == null) return;
        List<RecipeButton> buttons = page.vivet$buttons();
        if (pick < 0 || pick >= buttons.size() || !buttons.get(pick).visible) {
            if (overlay.isVisible()) overlay.setVisible(false);
            return;
        }
        RecipeButton button = buttons.get(pick);
        if (overlay.isVisible() && overlay.getRecipeCollection() == button.getCollection()) return;
        int x = parts.vivet$xOrigin();
        int y = parts.vivet$yOrigin();
        overlay.init(button.getCollection(), SlotDisplayContext.fromLevel(minecraft.level), page.vivet$filtering(),
                button.getX(), button.getY(), x + BOOK_WIDTH / 2, y + 13 + BOOK_HEIGHT / 2, button.getWidth());
    }

    private static final int BOOK_WIDTH = 147;
    private static final int BOOK_HEIGHT = 166;

    static void show(Minecraft minecraft, Screen screen, Payloads.Book wanted) {
        if (!(screen instanceof AbstractRecipeBookScreen<?> followed) || minecraft.player == null) return;
        RecipeBookComponent<?> component = ((RecipeBookScreenAccessor) followed).vivet$book();
        if (component == null) return;
        Payloads.Book book = wanted == null ? Payloads.Book.SHUT : wanted;
        RecipeBookType type = followed.getMenu().getRecipeBookType();
        ClientRecipeBook own = minecraft.player.getRecipeBook();

        boolean filterChanged = book.open() && own.isFiltering(type) != book.craftable();
        if (component.isVisible() != book.open() || filterChanged) {
            if (laidOut == followed && book.open() == laidOutOpen && book.craftable() == laidOutCraftable) return;
            laidOut = followed;
            laidOutOpen = book.open();
            laidOutCraftable = book.craftable();
            own.setOpen(type, book.open());
            if (book.open()) own.setFiltering(type, book.craftable());
            followed.resize(followed.width, followed.height);
        }
        if (!book.open() || !component.isVisible()) return;

        RecipeBookComponentAccessor parts = (RecipeBookComponentAccessor) component;
        boolean changed = false;
        List<RecipeBookTabButton> tabs = parts.vivet$tabs();
        if (book.tab() >= 0 && book.tab() < tabs.size()) {
            RecipeBookTabButton tab = tabs.get(book.tab());
            if (tab != parts.vivet$selectedTab()) {
                parts.vivet$select(tab);
                changed = true;
            }
        }
        EditBox box = parts.vivet$searchBox();
        if (box != null && !box.getValue().equals(book.search())) {
            box.setValue(book.search());
            parts.vivet$setLastSearch(book.search());
            changed = true;
        }
        if (changed) parts.vivet$updateCollections(false, book.craftable());

        if (parts.vivet$page() instanceof RecipeBookPageAccessor page) {
            int last = Math.max(0, page.vivet$totalPages() - 1);
            int at = Math.max(0, Math.min(book.page(), last));
            if (page.vivet$currentPage() != at) {
                page.vivet$setCurrentPage(at);
                page.vivet$updateButtonsForPage();
            }
        }
    }
}
