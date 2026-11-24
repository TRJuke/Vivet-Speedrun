package com.vivet.speedrun.game;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record Target(Item item, String potion) {
    public static final Set<Item> POTION_ITEMS =
            Set.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION);

    private static final Map<Item, Set<Item>> ALSO_COUNTS = Map.of(
            Items.EGG, Set.of(Items.EGG, Items.BLUE_EGG, Items.BROWN_EGG));

    private static final char SEP = '#';

    public static Target of(Item item) {
        return new Target(item, null);
    }

    public String id() {
        String path = ItemTiers.idOf(this.item);
        return this.potion == null ? path : path + SEP + this.potion;
    }

    public static Target parse(String id) {
        if (id == null || id.isEmpty()) return null;

        String path = id;
        String potion = null;
        int hash = id.indexOf(SEP);
        if (hash >= 0) {
            path = id.substring(0, hash);
            potion = id.substring(hash + 1);
        }

        Identifier key = Identifier.tryParse(path.trim());
        if (key == null) return null;

        Item item = BuiltInRegistries.ITEM.getOptional(key).orElse(null);
        if (item == null) return null;
        if (potion != null && !POTION_ITEMS.contains(item)) return null;
        return new Target(item, potion);
    }

    public boolean matches(ItemStack stack) {
        Set<Item> also = ALSO_COUNTS.get(this.item);
        boolean right = also == null ? stack.getItem() == this.item : also.contains(stack.getItem());
        if (!right) return false;
        if (!POTION_ITEMS.contains(this.item)) return true;

        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) return false;
        if (contents.is(Potions.WATER)) return false;

        Optional<net.minecraft.core.Holder<Potion>> held = contents.potion();
        if (this.potion == null) {
            return held.isPresent() || !contents.customEffects().isEmpty();
        }
        return held.isPresent() && basePotionOf(held.get()).equals(this.potion);
    }

    public static String basePotionOf(net.minecraft.core.Holder<Potion> holder) {
        String path = holder.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
        if (path.startsWith("long_")) return path.substring(5);
        if (path.startsWith("strong_")) return path.substring(7);
        return path;
    }

    public ItemStack displayStack() {
        if (!ItemIcons.ready(this.item)) return ItemStack.EMPTY;

        if (this.potion == null) {
            if (POTION_ITEMS.contains(this.item)) {
                return PotionContents.createItemStack(this.item, Potions.AWKWARD);
            }
            return new ItemStack(this.item);
        }
        return BuiltInRegistries.POTION
                .getOptional(Identifier.withDefaultNamespace(this.potion))
                .map(p -> PotionContents.createItemStack(this.item, BuiltInRegistries.POTION.wrapAsHolder(p)))
                .orElseGet(() -> new ItemStack(this.item));
    }

    public Component displayName() {
        Component disc = discName(this.item);
        if (disc != null) return disc;

        if (!ItemIcons.ready(this.item)) return ItemIcons.name(this.item);

        if (this.potion == null && POTION_ITEMS.contains(this.item)) {
            if (this.item == Items.SPLASH_POTION) return Component.translatable("vivet.target.splash_potion");
            if (this.item == Items.LINGERING_POTION) return Component.translatable("vivet.target.lingering_potion");
            return Component.translatable("vivet.target.potion");
        }
        return displayStack().getItemName();
    }

    private static Component discName(Item item) {
        if (item == null) return null;
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        if (!path.startsWith("music_disc_")) return null;

        String song = Component.translatable("jukebox_song.minecraft." + path.substring(11)).getString();
        int split = song.lastIndexOf(" - ");
        return Component.literal(split < 0 ? song : song.substring(split + 3));
    }
}
