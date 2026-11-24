package com.vivet.speedrun.client.replay;

import com.vivet.speedrun.mixin.QuickCraftAccessor;
import com.vivet.speedrun.net.Payloads;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class SlotDrag {
    private SlotDrag() {
    }

    public static Payloads.Drag read(Minecraft minecraft) {
        if (!(minecraft.gui.screen() instanceof AbstractContainerScreen<?> screen)) return Payloads.Drag.NONE;
        QuickCraftAccessor drag = (QuickCraftAccessor) screen;
        if (!drag.vivet$dragging() || drag.vivet$dragSlots().isEmpty()) return Payloads.Drag.NONE;
        List<Integer> slots = new ArrayList<>();
        for (Slot slot : drag.vivet$dragSlots()) {
            if (slots.size() >= Payloads.Drag.MAX_SLOTS) break;
            slots.add(slot.index);
        }
        slots.sort(null);
        return new Payloads.Drag(drag.vivet$dragType(), slots);
    }

    static void show(Screen screen, Payloads.Drag wanted) {
        if (!(screen instanceof AbstractContainerScreen<?> followed)) return;
        QuickCraftAccessor drag = (QuickCraftAccessor) followed;
        Payloads.Drag want = wanted == null ? Payloads.Drag.NONE : wanted;
        Set<Slot> shown = drag.vivet$dragSlots();
        if (want.kind() < 0 || want.slots().isEmpty()) {
            if (drag.vivet$dragging() || !shown.isEmpty()) {
                shown.clear();
                drag.vivet$setDragging(false);
            }
            return;
        }
        List<Slot> slots = new ArrayList<>();
        var menu = followed.getMenu().slots;
        for (int index : want.slots()) {
            if (index >= 0 && index < menu.size()) slots.add(menu.get(index));
        }
        if (drag.vivet$dragging() && drag.vivet$dragType() == want.kind()
                && shown.size() == slots.size() && shown.containsAll(slots)) {
            return;
        }
        shown.clear();
        shown.addAll(slots);
        drag.vivet$setDragType(want.kind());
        drag.vivet$setDragging(true);
        drag.vivet$recalculate();
    }
}
