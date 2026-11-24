package com.vivet.speedrun.game;

import com.vivet.speedrun.VivetSpeedrun;

import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.concurrent.Executor;

public final class ItemIcons {
    private ItemIcons() {
    }

    private static volatile boolean warming;

    public static void warmUp(Executor onRenderThread) {
        if (warming || ready(Items.STONE)) return;
        warming = true;

        Thread worker = new Thread(() -> {
            try {
                List<DataComponentInitializers.PendingComponents<?>> pending =
                        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS
                                .build(VanillaRegistries.createWorldLookup());

                onRenderThread.execute(() -> {
                    try {
                        if (ready(Items.STONE)) return;

                        for (DataComponentInitializers.PendingComponents<?> entry : pending) {
                            entry.apply();
                        }
                        VivetSpeedrun.LOGGER.info("Item icons loaded early");
                    } catch (Throwable e) {
                        VivetSpeedrun.LOGGER.warn("Could not load item icons early, waiting for a world", e);
                    }
                });
            } catch (Throwable e) {
                VivetSpeedrun.LOGGER.warn("Could not load item icons early, waiting for a world", e);
            }
        }, "vivet-item-components");

        worker.setDaemon(true);
        worker.start();
    }

    public static boolean ready(Item item) {
        if (item == null) return false;

        try {
            return item.builtInRegistryHolder().areComponentsBound();
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static ItemStack stack(Item item, int count) {
        if (!ready(item)) return ItemStack.EMPTY;

        try {
            return new ItemStack(item, Math.max(1, count));
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    public static Component name(Item item) {
        if (item == null) return Component.empty();
        return Component.translatable(item.getDescriptionId());
    }
}
