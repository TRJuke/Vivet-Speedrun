package com.vivet.speedrun.replay;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ReplayItems {
    private static final Map<String, ItemStack> STACKS = new ConcurrentHashMap<>();

    private static final int CACHE_LIMIT = 8_192;

    private static final ItemStack ABSENT = ItemStack.EMPTY;

    private ReplayItems() {
    }

    public static ItemStack stackOf(ReplayTrace.Item entry) {
        if (entry == null || entry.id() == null) return ItemStack.EMPTY;

        if (STACKS.size() >= CACHE_LIMIT) STACKS.clear();

        ItemStack prototype = STACKS.computeIfAbsent(entry.id(), ReplayItems::lookUp);
        if (prototype == ABSENT || prototype.isEmpty()) return ItemStack.EMPTY;

        return com.vivet.speedrun.game.ItemIcons.stack(prototype.getItem(),
                Math.max(1, Math.min(entry.count(), prototype.getItem().getDefaultMaxStackSize())));
    }

    private static ItemStack lookUp(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) return ABSENT;

        return BuiltInRegistries.ITEM.getOptional(identifier)
                .map(item -> com.vivet.speedrun.game.ItemIcons.stack(item, 1))
                .filter(stack -> !stack.isEmpty())
                .orElse(ABSENT);
    }

    public static Component nameOf(ReplayTrace.Item entry) {
        ItemStack stack = stackOf(entry);
        if (!stack.isEmpty()) return stack.getHoverName();

        Identifier identifier = entry == null || entry.id() == null
                ? null : Identifier.tryParse(entry.id());

        Item item = identifier == null ? null : BuiltInRegistries.ITEM.getValue(identifier);
        return item == null || item == net.minecraft.world.item.Items.AIR
                ? Component.translatable("vivet.replay.unknown_thing")
                : com.vivet.speedrun.game.ItemIcons.name(item);
    }
}
