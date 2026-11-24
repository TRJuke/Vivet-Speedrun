package com.vivet.speedrun.client.ui;

import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.p2p.Achievements;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public final class AchievementText {
    public static final Identifier TROPHY =
            Identifier.fromNamespaceAndPath(VivetSpeedrun.MOD_ID, "textures/gui/trophy.png");
    public static final int TROPHY_SIZE = 26;

    public enum Filter {
        ALL("all"),
        UNCLAIMED("unclaimed"),
        PROGRESS("progress"),
        NOT_STARTED("not_started"),
        CLAIMED("claimed"),
        SECRET("secret");

        public final String key;

        Filter(String key) {
            this.key = key;
        }

        public boolean has(boolean hidden, boolean found, boolean claimable, boolean claimed,
                           boolean started) {
            boolean unknown = hidden && !found;
            return switch (this) {
                case ALL -> true;
                case UNCLAIMED -> claimable;
                case PROGRESS -> !claimable && !claimed && !unknown && started;
                case NOT_STARTED -> !claimable && !claimed && !unknown && !started;
                case CLAIMED -> claimed;
                case SECRET -> hidden;
            };
        }

        public Component label() {
            return Component.translatable("vivet.achievements.cat." + this.key);
        }
    }

    private static final List<String> RUNGS = List.of("Coal", "Copper", "Iron", "Gold", "Emerald",
            "Diamond", "Netherite", "Grand Champion");

    private static final String[] ROMAN = { "I", "II", "III", "IV", "V", "VI", "VII", "VIII" };

    private AchievementText() {
    }

    public static int nodesOf(String id, int fallback) {
        Achievements.Book book = Achievements.book();
        if (book != null && book.achievements != null) {
            for (Achievements.Entry entry : book.achievements) {
                if (entry != null && id.equals(entry.id) && entry.nodes != null) {
                    return entry.nodes.length;
                }
            }
        }
        return fallback;
    }

    private static boolean named(String id) {
        return Achievements.isSecret(id) || Language.getInstance().has("vivet.achievement." + id);
    }

    public static Component name(String id, boolean found) {
        if (!found || id == null || !named(id)) return Component.translatable("vivet.achievements.hidden");
        String secret = Achievements.secretName(id);
        if (secret != null) return Component.literal(secret);
        Component name = Component.translatable("vivet.achievement." + id);
        return "speed_god".equals(id) ? scrambled(name.getString()) : name;
    }

    private static Component scrambled(String text) {
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean scramble = Character.isLetter(c) && c != 'S' && c != 'G';
            net.minecraft.network.chat.MutableComponent piece = Component.literal(String.valueOf(c));
            if (scramble) piece = piece.withStyle(net.minecraft.ChatFormatting.OBFUSCATED);
            out.append(piece);
        }
        return out;
    }

    public static String numeral(int node) {
        return node >= 0 && node < ROMAN.length ? ROMAN[node] : String.valueOf(node + 1);
    }

    public static Component nodeName(String id, int node, int nodes, boolean found) {
        Component base = name(id, found);
        if (nodes <= 1 || !found || !named(id)) return base;
        return base.copy().append(" " + numeral(node));
    }

    public static Component describe(String id, int node, long target, boolean found) {
        if (!found || id == null || !named(id)) return Component.translatable("vivet.achievements.hidden");
        String secret = Achievements.secretLine(id);
        if (secret != null) return Component.literal(secret);

        String key = "vivet.achievement." + id + ".desc";
        if ("climb".equals(id)) {
            int rung = (int) Math.max(1, Math.min(RUNGS.size(), target)) - 1;
            return Component.translatable(key, RUNGS.get(rung));
        }
        String shown = "distance".equals(id) ? grouped(target / 1000L) : grouped(target);
        if (target == 1 && Language.getInstance().has(key + ".one")) {
            return Component.translatable(key + ".one", shown);
        }
        return Component.translatable(key, shown);
    }

    public static String progress(String id, long progress, long target) {
        if ("distance".equals(id)) {
            return grouped(Math.min(progress, target) / 1000L) + "/" + grouped(target / 1000L) + " km";
        }
        return grouped(Math.min(progress, target)) + "/" + grouped(target);
    }

    public static String progressPast(String id, long progress, long target) {
        if ("distance".equals(id)) {
            return grouped(progress / 1000L) + "/" + grouped(target / 1000L) + " km";
        }
        return grouped(progress) + "/" + grouped(target);
    }

    public static String grouped(long value) {
        return NumberFormat.getIntegerInstance(Locale.US).format(value);
    }

    public static int colour(String rarity) {
        return Cosmetics.tierColour(rarity);
    }
}
