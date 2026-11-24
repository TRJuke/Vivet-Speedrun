package com.vivet.speedrun.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Set;

@Mixin(AbstractContainerScreen.class)
public interface QuickCraftAccessor {
    @Accessor("isQuickCrafting")
    boolean vivet$dragging();

    @Accessor("isQuickCrafting")
    void vivet$setDragging(boolean dragging);

    @Accessor("quickCraftingType")
    int vivet$dragType();

    @Accessor("quickCraftingType")
    void vivet$setDragType(int type);

    @Accessor("quickCraftSlots")
    Set<Slot> vivet$dragSlots();

    @Invoker("recalculateQuickCraftRemaining")
    void vivet$recalculate();
}
