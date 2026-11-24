package com.vivet.speedrun.mixin;

import net.minecraft.client.gui.screens.inventory.BookEditScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(BookEditScreen.class)
public interface BookEditAccessor {
    @Accessor("pages")
    List<String> vivet$pages();

    @Accessor("currentPage")
    int vivet$currentPage();

    @Accessor("currentPage")
    void vivet$setCurrentPage(int page);

    @Invoker("updatePageContent")
    void vivet$updatePageContent();

    @Invoker("updateButtonVisibility")
    void vivet$updateButtonVisibility();
}
