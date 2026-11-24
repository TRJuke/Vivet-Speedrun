package com.vivet.speedrun.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(RecipeBookComponent.class)
public interface RecipeBookComponentAccessor {
    @Accessor("tabButtons")
    List<RecipeBookTabButton> vivet$tabs();

    @Accessor("selectedTab")
    RecipeBookTabButton vivet$selectedTab();

    @Accessor("searchBox")
    EditBox vivet$searchBox();

    @Accessor("lastSearch")
    void vivet$setLastSearch(String search);

    @Accessor("recipeBookPage")
    RecipeBookPage vivet$page();

    @Invoker("replaceSelected")
    void vivet$select(RecipeBookTabButton tab);

    @Invoker("updateCollections")
    void vivet$updateCollections(boolean resetPage, boolean filtering);

    @Accessor("ghostSlots")
    net.minecraft.client.gui.screens.recipebook.GhostSlots vivet$ghostSlots();

    @Invoker("isCraftingSlot")
    boolean vivet$isCraftingSlot(net.minecraft.world.inventory.Slot slot);

    @Invoker("getXOrigin")
    int vivet$xOrigin();

    @Invoker("getYOrigin")
    int vivet$yOrigin();
}
