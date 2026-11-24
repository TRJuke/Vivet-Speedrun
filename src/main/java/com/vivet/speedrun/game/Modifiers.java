package com.vivet.speedrun.game;

import com.vivet.speedrun.config.ConfigManager;

public final class Modifiers {
    private Modifiers() {
    }

    public static boolean applyNow() {
        return RunMode.current() == RunMode.PRIVATE;
    }

    public static boolean enabled(WorldRule rule) {
        return applyNow() && ConfigManager.get().isRuleEnabled(rule);
    }

    public static int level(WorldRule rule) {
        return rule.hasLevels() ? ConfigManager.get().levelOf(rule) : 1;
    }
}
