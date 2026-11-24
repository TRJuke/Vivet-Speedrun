package com.vivet.speedrun.game;

import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.config.ConfigManager;
import com.vivet.speedrun.config.ModConfig;

import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;

public final class WorldRuleApplier {
    private static final int TICK_INTERVAL = 10;

    private static int tickCounter;

    private WorldRuleApplier() {
    }

    public static void applyAll(MinecraftServer server) {
        if (server == null) return;
        ModConfig config = ConfigManager.get();

        for (WorldRule rule : WorldRule.values()) {
            GameRule<Boolean> gameRule = rule.gameRule();
            if (gameRule == null) continue;

            boolean enabled = Modifiers.enabled(rule);
            server.getGameRules().set(gameRule, rule.valueFor(enabled), server);
        }

        applyTimeAndWeather(server, config);
        applyRespawnRules(server, config);

        applyToPlayers(server, config);
    }

    private static void applyTimeAndWeather(MinecraftServer server, ModConfig config) {
        boolean alwaysDay = Modifiers.enabled(WorldRule.ALWAYS_DAY);
        boolean alwaysNight = !alwaysDay && Modifiers.enabled(WorldRule.ALWAYS_NIGHT);

        server.getGameRules().set(GameRules.ADVANCE_TIME, !(alwaysDay || alwaysNight), server);
        if (alwaysDay) {
            runCommand(server, "time set day");
        } else if (alwaysNight) {
            runCommand(server, "time set midnight");
        }

        boolean clearWeather = Modifiers.enabled(WorldRule.CLEAR_WEATHER);
        boolean endlessStorm = !clearWeather && Modifiers.enabled(WorldRule.PERMANENT_THUNDER);

        server.getGameRules().set(GameRules.ADVANCE_WEATHER, !(clearWeather || endlessStorm), server);
        for (ServerLevel level : server.getAllLevels()) {
            var weather = level.getWeatherData();
            if (clearWeather) {
                weather.setRaining(false);
                weather.setThundering(false);
                weather.setRainTime(0);
                weather.setThunderTime(0);
                weather.setClearWeatherTime(Integer.MAX_VALUE);
            } else if (endlessStorm) {
                weather.setClearWeatherTime(0);
                weather.setRaining(true);
                weather.setThundering(true);
                weather.setRainTime(Integer.MAX_VALUE);
                weather.setThunderTime(Integer.MAX_VALUE);
            }
        }
    }

    private static void applyRespawnRules(MinecraftServer server, ModConfig config) {
        server.getGameRules().set(GameRules.IMMEDIATE_RESPAWN,
                Modifiers.enabled(WorldRule.HARDCORE), server);
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        if (++tickCounter < TICK_INTERVAL) return;
        tickCounter = 0;

        applyToPlayers(server, ConfigManager.get());
    }

    private static void applyToPlayers(MinecraftServer server, ModConfig config) {
        boolean infiniteSaturation = Modifiers.enabled(WorldRule.INFINITE_SATURATION);
        boolean invulnerable = Modifiers.enabled(WorldRule.INVULNERABLE);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            var food = player.getFoodData();
            if (infiniteSaturation
                    && (food.getFoodLevel() < 20 || food.getSaturationLevel() < 15.0F)) {
                food.setFoodLevel(20);
                food.setSaturation(20.0F);
            }
            if (invulnerable != player.isPermanentlyInvulnerable()) {
                player.setPermanentlyInvulnerable(invulnerable);
            }

            applyEffects(player, config);
        }
    }

    private static void applyEffects(ServerPlayer player, ModConfig config) {
        for (WorldRule rule : WorldRule.values()) {
            if (rule.kind() != WorldRule.Kind.EFFECT) continue;

            Holder<MobEffect> effect = rule.effect();
            if (effect == null) continue;

            MobEffectInstance active = player.getEffect(effect);
            if (Modifiers.enabled(rule)) {
                int amplifier = Modifiers.level(rule) - 1;

                if (active == null || !active.isInfiniteDuration()
                        || active.getAmplifier() != amplifier) {
                    player.addEffect(new MobEffectInstance(effect,
                            MobEffectInstance.INFINITE_DURATION, amplifier,
                            true, false, true));
                }
            } else if (active != null && active.isInfiniteDuration()) {
                player.removeEffect(effect);
            }
        }
    }

    public static void giveStarterKit(ServerPlayer player) {
        if (!Modifiers.enabled(WorldRule.STARTER_KIT)) return;

        giveIfAbsent(player, new ItemStack(Items.STONE_SWORD));
        giveIfAbsent(player, new ItemStack(Items.STONE_PICKAXE));
        giveIfAbsent(player, new ItemStack(Items.STONE_AXE));
        giveIfAbsent(player, new ItemStack(Items.STONE_SHOVEL));
        giveIfAbsent(player, new ItemStack(Items.COOKED_BEEF, 16));
        giveIfAbsent(player, new ItemStack(Items.TORCH, 32));
        giveIfAbsent(player, new ItemStack(Items.OAK_LOG, 16));
    }

    private static void giveIfAbsent(ServerPlayer player, ItemStack stack) {
        if (!player.addItem(stack)) {
            player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
    }

    public static void restoreDefaults(MinecraftServer server) {
        if (server == null) return;

        server.getGameRules().set(GameRules.ADVANCE_TIME, true, server);
        server.getGameRules().set(GameRules.ADVANCE_WEATHER, true, server);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.setPermanentlyInvulnerable(false);

            for (WorldRule rule : WorldRule.values()) {
                Holder<MobEffect> effect = rule.effect();
                if (effect == null) continue;

                MobEffectInstance active = player.getEffect(effect);
                if (active != null && active.isInfiniteDuration()) player.removeEffect(effect);
            }
        }
    }

    private static void runCommand(MinecraftServer server, String command) {
        try {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
        } catch (Exception e) {
            VivetSpeedrun.LOGGER.warn("Command failed: {}", command, e);
        }
    }
}
