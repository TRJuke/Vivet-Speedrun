package com.vivet.speedrun.client.ui;

import com.vivet.speedrun.p2p.MojangAuth;
import com.vivet.speedrun.p2p.RankedApi;

public final class Ranks {
    private static final String[] ORDER = {
            "unranked", "coal", "copper", "iron", "gold", "emerald", "diamond", "netherite",
            "grand_champion",
    };

    public static final String SPEED_KING = "speed_king";

    public static final String[] SPEED_TITLES = {
            SPEED_KING, "speed_prince", "speed_knight", "speed_squire",
    };

    public static String titleAtPlace(int place) {
        return switch (place) {
            case 1 -> SPEED_KING;
            case 5 -> "speed_prince";
            case 20 -> "speed_knight";
            case 50 -> "speed_squire";
            default -> null;
        };
    }

    public static String nextSpeedTitle(String current) {
        for (int i = SPEED_TITLES.length - 1; i >= 0; i--) {
            if (SPEED_TITLES[i].equals(current)) return i == 0 ? null : SPEED_TITLES[i - 1];
        }
        return SPEED_TITLES[SPEED_TITLES.length - 1];
    }

    private Ranks() {
    }

    public static String speedTitle(MojangAuth.Profile profile) {
        if (profile == null || !profile.placed) return null;
        return speedTitle(profile.uuid);
    }

    public static String speedTitle(String uuid) {
        if (uuid == null || uuid.isEmpty()) return null;
        return RankedApi.speedTitle(uuid);
    }

    public static String displayRank(MojangAuth.Profile profile) {
        if (profile == null) return "unranked";

        String title = speedTitle(profile);
        return title == null ? profile.rank : title;
    }

    public static int colorOf(String rankId) {
        RankedApi.Config config = RankedApi.cachedConfig();
        if (config != null && config.ranks != null) {
            for (RankedApi.Rank rank : config.ranks) {
                if (rank.id != null && rank.id.equals(rankId) && rank.color != null) {
                    Integer parsed = parseHex(rank.color);
                    if (parsed != null) return parsed;
                }
            }
        }
        return fallbackColor(rankId);
    }

    public static String nameOf(String rankId) {
        RankedApi.Config config = RankedApi.cachedConfig();
        if (config != null && config.ranks != null) {
            for (RankedApi.Rank rank : config.ranks) {
                if (rank.id != null && rank.id.equals(rankId) && rank.name != null) return rank.name;
            }
        }
        return fallbackName(rankId);
    }

    private static String fallbackName(String rankId) {
        return titleCase(rankId);
    }

    public static Integer nextThreshold(String rankId) {
        RankedApi.Config config = RankedApi.cachedConfig();
        if (config == null || config.ranks == null) {
            String next = nextRankId(rankId);
            return next.equals(rankId) ? null : fallbackThreshold(next);
        }

        boolean past = false;
        for (RankedApi.Rank rank : config.ranks) {
            if (past && rank.min != null) return rank.min;
            if (rank.id != null && rank.id.equals(rankId)) past = true;
        }
        return null;
    }

    public static Integer thresholdOf(String rankId) {
        RankedApi.Config config = RankedApi.cachedConfig();
        if (config == null || config.ranks == null) return fallbackThreshold(rankId);

        for (RankedApi.Rank rank : config.ranks) {
            if (rank.id != null && rank.id.equals(rankId)) return rank.min;
        }
        return fallbackThreshold(rankId);
    }

    public static String nextRankId(String rankId) {
        RankedApi.Config config = RankedApi.cachedConfig();
        String[] order = ORDER;

        if (config != null && config.ranks != null) {
            order = new String[config.ranks.length];
            for (int i = 0; i < config.ranks.length; i++) order[i] = config.ranks[i].id;
        }

        for (int i = 0; i < order.length - 1; i++) {
            if (order[i].equals(rankId)) return order[i + 1];
        }
        return rankId;
    }

    public static final int[] PLACES = { 100, 50, 20, 5, 1 };

    public static int chasedPlace(int position) {
        int chasing = 1;
        for (int place : PLACES) {
            if (position > place && place > chasing) chasing = place;
        }
        return chasing;
    }

    public static Integer heldPlace(Integer position) {
        if (position == null) return null;

        Integer held = null;
        for (int place : PLACES) {
            if (position <= place && (held == null || place < held)) held = place;
        }
        return held;
    }

    public static Integer placeCutoff(int place, Integer[] ladder) {
        if (ladder == null) return null;

        for (int i = 0; i < PLACES.length; i++) {
            if (PLACES[i] == place) return i < ladder.length ? ladder[i] : null;
        }
        return null;
    }

    public static int[] placeBounds(int chasing, Integer held, Integer[] ladder) {
        Integer top = placeCutoff(chasing, ladder);
        if (top == null) return null;

        Integer bottom = held == null ? thresholdOf("grand_champion") : placeCutoff(held, ladder);
        if (bottom == null || top <= bottom) return null;

        return new int[] { bottom, top };
    }

    private static Integer fallbackThreshold(String rankId) {
        return switch (rankId == null ? "" : rankId) {
            case "coal" -> 0;
            case "copper" -> 450;
            case "iron" -> 950;
            case "gold" -> 1500;
            case "emerald" -> 2200;
            case "diamond" -> 3000;
            case "netherite" -> 4000;

            case "grand_champion" -> 5000;
            default -> null;
        };
    }

    private static Integer parseHex(String color) {
        try {
            String hex = color.startsWith("#") ? color.substring(1) : color;
            return 0xFF000000 | Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int fallbackColor(String rankId) {
        return switch (rankId == null ? "" : rankId) {
            case SPEED_KING -> 0xFFC77DFF;
            case "speed_prince" -> 0xFF7AD5FF;
            case "speed_knight" -> 0xFFFF7A7A;
            case "speed_squire" -> 0xFFD9A08C;
            case "coal" -> 0xFF3A3A3A;
            case "copper" -> 0xFFC06B3E;
            case "iron" -> 0xFFC8C8C8;
            case "gold" -> 0xFFE8C34A;
            case "emerald" -> 0xFF41D67C;
            case "diamond" -> 0xFF4EE1E1;
            case "netherite" -> 0xFF6B5C58;
            case "grand_champion" -> 0xFFF5E8A0;
            default -> Theme.TEXT_FAINT;
        };
    }

    private static String titleCase(String id) {
        if (id == null || id.isBlank()) return "Unranked";

        StringBuilder out = new StringBuilder();
        for (String word : id.split("_")) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    public static float progressOf(String rankId) {
        for (int i = 0; i < ORDER.length; i++) {
            if (ORDER[i].equals(rankId)) return i / (float) (ORDER.length - 1);
        }
        return 0.0F;
    }

    public static String remaining(long until) {
        long millis = until - System.currentTimeMillis();
        if (millis <= 0) return "a moment";

        long minutes = millis / 60_000L;
        if (minutes < 60) return Math.max(1, minutes) + "m";
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }
}
