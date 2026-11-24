package com.vivet.speedrun.client.ui;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

import java.text.NumberFormat;
import java.util.Locale;

public final class QuestText {
    private QuestText() {
    }

    public static String line(String id, int target, String secret) {
        if (secret != null && !secret.isBlank()) return secret;
        if (id == null) return "";
        Language language = Language.getInstance();
        String one = "vivet.quest." + id + ".one";
        if (target == 1 && language.has(one)) return Component.translatable(one).getString();
        String key = "vivet.quest." + id;
        if (!language.has(key)) return id;
        return Component.translatable(key, NumberFormat.getIntegerInstance(Locale.US).format(target)).getString();
    }

    public static int colour(String rarity) {
        return Cosmetics.tierColour(rarity == null ? "common" : rarity);
    }

    public static String progress(int progress, int target) {
        NumberFormat format = NumberFormat.getIntegerInstance(Locale.US);
        return format.format(Math.max(0, Math.min(progress, target))) + "/" + format.format(target);
    }
}
