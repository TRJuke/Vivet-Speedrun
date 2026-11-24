package com.vivet.speedrun.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.BookSignScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BookSignScreen.class)
public interface BookSignAccessor {
    @Accessor("titleBox")
    EditBox vivet$title();
}
