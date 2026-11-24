package com.vivet.speedrun.game;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class MilestoneTracker {
    private static final Map<Item, String> WATCHED = new LinkedHashMap<>();

    static {
        WATCHED.put(Items.IRON_PICKAXE, "got an Iron Pickaxe");
        WATCHED.put(Items.DIAMOND, "found Diamonds");
        WATCHED.put(Items.DIAMOND_PICKAXE, "got a Diamond Pickaxe");
        WATCHED.put(Items.OBSIDIAN, "got Obsidian");
        WATCHED.put(Items.BLAZE_ROD, "got Blaze Rods");
        WATCHED.put(Items.ENDER_PEARL, "got Ender Pearls");
        WATCHED.put(Items.ENDER_EYE, "made Eyes of Ender");
        WATCHED.put(Items.ANCIENT_DEBRIS, "found Ancient Debris");
        WATCHED.put(Items.NETHERITE_INGOT, "made Netherite");
        WATCHED.put(Items.ELYTRA, "got an Elytra");
        WATCHED.put(Items.NETHER_STAR, "killed the Wither");
        WATCHED.put(Items.TOTEM_OF_UNDYING, "got a Totem");
        WATCHED.put(Items.GOLDEN_APPLE, "got a Golden Apple");
        WATCHED.put(Items.BUCKET, "made a Bucket");
        WATCHED.put(Items.SHIELD, "made a Shield");
    }

    private static final Set<String> announced = new LinkedHashSet<>();

    private static final Set<String> alreadyEarned = new LinkedHashSet<>();

    private static boolean baselineTaken;
    private static int tickCounter;

    private MilestoneTracker() {
    }

    public static Map<Item, String> watched() {
        return java.util.Collections.unmodifiableMap(WATCHED);
    }

    public static void reset() {
        announced.clear();
        alreadyEarned.clear();
        baselineTaken = false;
        tickCounter = 0;
    }

    public static void tick(ServerPlayer player, Consumer<String> report) {
        if (player == null) return;
        if (++tickCounter < 20) return;
        tickCounter = 0;

        for (Map.Entry<Item, String> entry : WATCHED.entrySet()) {
            String milestone = entry.getValue();
            if (announced.contains(milestone)) continue;

            if (player.getInventory().contains(stack -> stack.getItem() == entry.getKey())) {
                announced.add(milestone);
                report.accept(milestone);
            }
        }

        scanAdvancements(player, report);
    }

    private static void scanAdvancements(ServerPlayer player, Consumer<String> report) {
        var server = player.level().getServer();
        if (server == null) return;

        boolean baseline = !baselineTaken;
        baselineTaken = true;

        for (AdvancementHolder holder : server.getAdvancements().getAllAdvancements()) {
            if (holder.value().display().isEmpty()) continue;

            if (holder.value().parent().isEmpty()) continue;

            if (!player.getAdvancements().getOrStartProgress(holder).isDone()) continue;

            String id = holder.id().toString();
            if (baseline) {
                alreadyEarned.add(id);
                continue;
            }
            if (alreadyEarned.contains(id) || !announced.add(id)) continue;

            report.accept("earned " + holder.value().display().get().title().getString());
        }
    }

    public static void dimensionChanged(String dimensionId, Consumer<String> report) {
        String milestone = switch (dimensionId) {
            case "minecraft:the_end" -> "entered The End";
            default -> null;
        };
        if (milestone == null || !announced.add(milestone)) return;

        report.accept(milestone);
    }

    public static void died(Consumer<String> report) {
        report.accept("died");
    }
}
