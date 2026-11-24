package com.vivet.speedrun.replay;

import com.vivet.speedrun.game.RunMode;

import java.util.ArrayList;
import java.util.List;

public final class ReplayMatch {
    public String match = "";

    public String kind = "";
    public String band = "";
    public String itemId = "";
    public boolean setSeed;

    public long startedAt;
    public long endedAt;

    public long keepUntil;
    public String keptFor = "";

    public String outcome = "";
    public String reason = "";
    public String winner = "";
    public String winnerName = "";
    public Integer winnerCentis;

    public Integer reports;

    public List<Player> players = new ArrayList<>();

    public List<Pov> povs = new ArrayList<>();

    public transient boolean outsider;

    public static final class Player {
        public String uuid = "";
        public String name = "";
    }

    public static final class Pov {
        public String uuid = "";
        public String name = "";

        public String file = "";

        public String url = "";

        public long bytes;
        public int centis;

        public String mc = "";

        public boolean finished;

        public String sha256 = "";
    }

    public boolean isUsable() {
        return match != null && !match.isEmpty() && povs != null && !povs.isEmpty();
    }

    public boolean isSolo() {
        return "solo".equals(kind);
    }

    public RunMode mode() {
        return switch (kind == null ? "" : kind) {
            case "solo" -> RunMode.RANKED_SOLO;
            case "ranked" -> RunMode.RANKED_MATCH;
            default -> RunMode.PUBLIC;
        };
    }

    public String resultFor(String uuid) {
        if ("draw".equals(outcome)) return "draw";
        if (winner == null || winner.isEmpty() || uuid == null) return "";
        if (isSolo()) return winner.equals(uuid) ? "won" : "";
        return winner.equals(uuid) ? "won" : "lost";
    }

    public boolean played(String uuid) {
        if (uuid == null || uuid.isEmpty() || players == null) return false;
        for (Player player : players) {
            if (player != null && uuid.equals(player.uuid)) return true;
        }
        return false;
    }

    public Player opponentOf(String uuid) {
        if (players == null) return null;
        for (Player player : players) {
            if (player != null && player.uuid != null && !player.uuid.equals(uuid)) return player;
        }
        return null;
    }

    public int centis() {
        int longest = 0;
        if (povs != null) {
            for (Pov pov : povs) longest = Math.max(longest, pov.centis);
        }
        return longest;
    }

    public ReplayMatch sanitize() {
        if (match == null) match = "";
        if (kind == null) kind = "";
        if (band == null) band = "";
        if (itemId == null) itemId = "";
        if (keptFor == null) keptFor = "";
        if (outcome == null) outcome = "";
        if (reason == null) reason = "";
        if (winner == null) winner = "";
        if (winnerName == null) winnerName = "";
        if (players == null) players = new ArrayList<>();
        if (povs == null) povs = new ArrayList<>();
        players.removeIf(player -> player == null || player.uuid == null || player.name == null);
        povs.removeIf(pov -> pov == null || !ReplayStore.isFile(pov.file)
                || pov.uuid == null || pov.name == null);
        for (Pov pov : povs) {
            if (pov.mc == null) pov.mc = "";
            if (pov.url == null) pov.url = "";

            String digest = pov.sha256 == null ? "" : pov.sha256.trim().toLowerCase(java.util.Locale.ROOT);
            pov.sha256 = digest.matches("[0-9a-f]{64}") ? digest : "";
        }
        return this;
    }
}
