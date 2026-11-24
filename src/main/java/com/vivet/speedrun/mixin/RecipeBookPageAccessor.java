package com.vivet.speedrun.mixin;

import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RecipeBookPage.class)
public interface RecipeBookPageAccessor {
    @Accessor("currentPage")
    int vivet$currentPage();

    @Accessor("currentPage")
    void vivet$setCurrentPage(int page);

    @Accessor("totalPages")
    int vivet$totalPages();

    @Invoker("updateButtonsForPage")
    void vivet$updateButtonsForPage();

    @Accessor("buttons")
    java.util.List<net.minecraft.client.gui.screens.recipebook.RecipeButton> vivet$buttons();

    @Accessor("overlay")
    net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent vivet$overlay();

    @Accessor("isFiltering")
    boolean vivet$filtering();
}
