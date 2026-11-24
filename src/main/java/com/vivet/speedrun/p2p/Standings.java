package com.vivet.speedrun.p2p;

import com.google.gson.Gson;

import com.vivet.speedrun.game.Time;
import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.config.ConfigManager;
import com.vivet.speedrun.game.Difficulty;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class Standings {
    public static final class Board {
        public Integer best;

        public Integer bestCentis;

        public String holder;

        public String bestItem;

        public Integer cutoff;

        public Integer cutoffCentis;

        public int filled;

        public int capacity;

        public Mine you;

        public String id;
    }

    public static final class Mine {
        public int seconds;

        public int centis;

        public Integer place;
    }

    public static final class Answer {
        public Board item;
        public Board band;
        public Board seed;
        public String error;
    }

    private static final Gson GSON = new Gson();

    private static volatile Answer current;

    private static volatile String currentKey = "";

    private static volatile String pendingKey = "";

    private Standings() {
    }

    public static Answer current() {
        return current;
    }

    public static void clear() {
        current = null;
        currentKey = "";
        pendingKey = "";
    }

    public static void load(String itemId, Difficulty band) {
        String key = key(itemId, band);
        if (key.equals(currentKey) || key.equals(pendingKey)) return;

        fetch(itemId, band, key);
    }

    public static void refresh(String itemId, Difficulty band) {
        String key = key(itemId, band);
        if (key.equals(pendingKey)) return;

        fetch(itemId, band, key);
    }

    private static void fetch(String itemId, Difficulty band, String key) {
        pendingKey = key;

        StringBuilder url = new StringBuilder(MojangAuth.httpBase(ConfigManager.get().relayUrl))
                .append("/standing?band=").append(band == null ? "hard" : band.id());

        if (itemId != null && !itemId.isBlank()) {
            url.append("&item=").append(URLEncoder.encode(itemId, StandardCharsets.UTF_8));
        }

        String token = MojangAuth.token();
        if (token != null) {
            url.append("&t=").append(URLEncoder.encode(token, StandardCharsets.UTF_8));
        }

        MojangAuth.get(url.toString())
                .thenApply(body -> GSON.fromJson(body, Answer.class))
                .whenComplete((answer, error) -> {
                    if (!key.equals(pendingKey)) return;
                    pendingKey = "";

                    if (error != null || answer == null || answer.error != null) {
                        VivetSpeedrun.LOGGER.info("Could not load standings: {}",
                                error == null ? "no response" : error.toString());
                        return;
                    }

                    current = answer;
                    currentKey = key;
                });
    }

    private static String key(String itemId, Difficulty band) {
        return (itemId == null ? "" : itemId) + "@" + (band == null ? "" : band.id());
    }

    public static void adoptYourBest(com.vivet.speedrun.game.RunMode mode, boolean setSeed,
                                     String itemId) {
        Answer answer = current;
        if (answer == null || answer.item == null || itemId == null
                || !itemId.equals(answer.item.id)) {
            return;
        }
        com.vivet.speedrun.game.PersonalBests.adoptBest(mode, setSeed, itemId,
                mineCentis(answer.item));
    }

    public static Integer bestCentis(Board board) {
        if (board == null) return null;
        if (board.bestCentis != null) return board.bestCentis;
        return board.best == null ? null : Time.toCentis(board.best);
    }

    public static Integer cutoffCentis(Board board) {
        if (board == null) return null;
        if (board.cutoffCentis != null) return board.cutoffCentis;
        return board.cutoff == null ? null : Time.toCentis(board.cutoff);
    }

    public static int mineCentis(Board board) {
        if (board == null || board.you == null) return -1;
        return board.you.centis > 0 ? board.you.centis : Time.toCentis(board.you.seconds);
    }

    public static int centisToBoard(Board board, int centis) {
        Integer cutoff = cutoffCentis(board);
        if (board == null) return -1;
        if (cutoff == null) return 0;
        return centis <= cutoff ? 0 : centis - cutoff;
    }
}
