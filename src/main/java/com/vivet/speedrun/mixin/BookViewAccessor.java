package com.vivet.speedrun.mixin;

import net.minecraft.client.gui.screens.inventory.BookViewScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BookViewScreen.class)
public interface BookViewAccessor {
    @Accessor("bookAccess")
    BookViewScreen.BookAccess vivet$book();

    @Accessor("currentPage")
    int vivet$currentPage();
}
