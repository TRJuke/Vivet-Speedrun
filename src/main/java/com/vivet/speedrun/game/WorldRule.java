package com.vivet.speedrun.game;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.function.Supplier;

public enum WorldRule {
    INFINITE_SATURATION(Category.EASY, "Infinite Saturation"),

    SPEED(Category.EASY, "Speed", () -> MobEffects.SPEED, 6),

    HASTE(Category.EASY, "Haste", () -> MobEffects.HASTE, 5),

    JUMP_BOOST(Category.EASY, "Jump Boost", () -> MobEffects.JUMP_BOOST, 6),

    NIGHT_VISION(Category.EASY, "Night Vision", () -> MobEffects.NIGHT_VISION, 1),

    WATER_BREATHING(Category.EASY, "Water Breathing", () -> MobEffects.WATER_BREATHING, 1),

    RESISTANCE(Category.EASY, "Resistance", () -> MobEffects.RESISTANCE, 5),

    INVULNERABLE(Category.EASY, "Invulnerable"),

    KEEP_INVENTORY(Category.EASY, "Keep Inventory",
            Kind.VANILLA, () -> GameRules.KEEP_INVENTORY),

    NO_FALL_DAMAGE(Category.EASY, "No Fall Damage",
            Kind.VANILLA_INVERTED, () -> GameRules.FALL_DAMAGE),

    NO_FIRE_DAMAGE(Category.EASY, "No Fire Damage",
            Kind.VANILLA_INVERTED, () -> GameRules.FIRE_DAMAGE),

    NO_FREEZING(Category.EASY, "No Freezing",
            Kind.VANILLA_INVERTED, () -> GameRules.FREEZE_DAMAGE),

    NO_WARDEN(Category.EASY, "No Warden",
            Kind.VANILLA_INVERTED, () -> GameRules.SPAWN_WARDENS),

    ALWAYS_DAY(Category.EASY, "Always Day"),

    CLEAR_WEATHER(Category.EASY, "Clear Weather"),

    STARTER_KIT(Category.EASY, "Starter Kit"),

    HARDCORE(Category.HARD, "Hardcore"),

    ALWAYS_NIGHT(Category.HARD, "Always Night"),

    PERMANENT_THUNDER(Category.HARD, "Permanent Thunder"),

    MINING_FATIGUE(Category.HARD, "Mining Fatigue", () -> MobEffects.MINING_FATIGUE, 2),

    SLOWNESS(Category.HARD, "Slowness", () -> MobEffects.SLOWNESS, 5),

    WEAKNESS(Category.HARD, "Weakness", () -> MobEffects.WEAKNESS, 3),

    HUNGER(Category.HARD, "Hunger", () -> MobEffects.HUNGER, 5),

    UHC(Category.HARD, "UHC",
            Kind.VANILLA_INVERTED, () -> GameRules.NATURAL_HEALTH_REGENERATION),

    UNIVERSAL_ANGER(Category.HARD, "Universal Anger",
            Kind.VANILLA, () -> GameRules.UNIVERSAL_ANGER);

    public enum Category {
        EASY("Easy"),
        HARD("Hard");

        private final String title;

        Category(String title) {
            this.title = title;
        }

        public String title() {
            return this.title;
        }
    }

    public enum Kind {
        VANILLA,

        VANILLA_INVERTED,

        EFFECT,

        CUSTOM,
    }

    private final Category category;
    private final String title;
    private final Kind kind;
    private final Supplier<GameRule<Boolean>> rule;
    private final Supplier<Holder<MobEffect>> effect;
    private final int maxLevel;

    WorldRule(Category category, String title) {
        this(category, title, Kind.CUSTOM, null, null, 1);
    }

    WorldRule(Category category, String title, Kind kind, Supplier<GameRule<Boolean>> rule) {
        this(category, title, kind, rule, null, 1);
    }

    WorldRule(Category category, String title, Supplier<Holder<MobEffect>> effect, int maxLevel) {
        this(category, title, Kind.EFFECT, null, effect, maxLevel);
    }

    WorldRule(Category category, String title, Kind kind, Supplier<GameRule<Boolean>> rule,
              Supplier<Holder<MobEffect>> effect, int maxLevel) {
        this.category = category;
        this.title = title;
        this.kind = kind;
        this.rule = rule;
        this.effect = effect;
        this.maxLevel = maxLevel;
    }

    public Category category() {
        return this.category;
    }

    public Kind kind() {
        return this.kind;
    }

    public GameRule<Boolean> gameRule() {
        return this.rule == null ? null : this.rule.get();
    }

    public Holder<MobEffect> effect() {
        return this.effect == null ? null : this.effect.get();
    }

    public int maxLevel() {
        return this.maxLevel;
    }

    public boolean hasLevels() {
        return this.maxLevel > 1;
    }

    public boolean valueFor(boolean enabled) {
        return this.kind == Kind.VANILLA_INVERTED ? !enabled : enabled;
    }

    public String title() {
        return this.title;
    }

    public String key() {
        return this.name().toLowerCase();
    }

    public static WorldRule byKey(String key) {
        for (WorldRule rule : values()) {
            if (rule.key().equalsIgnoreCase(key)) return rule;
        }
        return null;
    }
}
