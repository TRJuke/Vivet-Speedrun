package com.vivet.speedrun.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractSignEditScreen.class)
public interface SignEditAccessor {
    @Accessor("sign")
    SignBlockEntity vivet$sign();

    @Accessor("messages")
    String[] vivet$messages();

    @Accessor("line")
    int vivet$line();

    @Accessor("line")
    void vivet$setLine(int line);

    @Accessor("slot")
    SignTextSlot vivet$slot();
}
