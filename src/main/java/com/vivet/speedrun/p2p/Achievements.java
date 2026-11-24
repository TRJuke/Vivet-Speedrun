package com.vivet.speedrun.p2p;

import com.google.gson.Gson;
import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.config.ConfigManager;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class Achievements {
    private static final Gson GSON = new Gson();
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    public static final class Book {
        public Entry[] achievements;
        public Integer unclaimed;
        public Road[] road;
        public Integer level;
        public String error;
    }

    public static final class Entry {
        public String id;
        public Boolean hidden;
        public Long progress;
        public Node[] nodes;

        public String name;
        public String desc;
    }

    public static final class Node {
        public Long target;
        public Integer pay;
        public String rarity;
        public Boolean done;
        public Boolean claimed;
        public String[] grants;
    }

    public static final class Road {
        public Integer level;
        public Integer coins;
        public String[] unlocks;
        public Soon[] soon;
    }

    public static final class Soon {
        public String set;
        public String kind;
    }

    public static final class Frame {
        public Integer unclaimed;
        public Done[] done;
    }

    public static final class Done {
        public String id;
        public Integer node;
        public Boolean hidden;
        public String rarity;
        public Integer nodes;

        public String name;
        public String desc;

        public String sound;
    }

    public static final class Claimed {
        public Boolean ok;
        public Integer balance;
        public String[] granted;
        public Integer unclaimed;
        public String error;
    }

    private static volatile Book book;
    private static volatile boolean known;
    private static volatile long at;
    private static volatile long stamp;
    private static final AtomicBoolean FETCHING = new AtomicBoolean();

    private static volatile int unclaimed;

    private static final Set<String> CLAIMING = ConcurrentHashMap.newKeySet();

    private static final Map<String, String[]> SECRETS = new ConcurrentHashMap<>();

    private static final long TTL_MS = 300_000L;
    private static final long AWAY_TTL_MS = 1_800_000L;
    private static final long RETRY_MS = 30_000L;

    private static volatile Consumer<Done> SINK = done -> { };

    private Achievements() {
    }

    public static void bindDone(Consumer<Done> sink) {
        if (sink != null) SINK = sink;
    }

    public static boolean known() {
        return known;
    }

    public static Book book() {
        return book;
    }

    public static long stamp() {
        return stamp;
    }

    public static int unclaimed() {
        return Math.max(0, unclaimed);
    }

    public static String secretName(String id) {
        String[] text = id == null ? null : SECRETS.get(id);
        return text == null ? null : text[0];
    }

    public static String secretLine(String id) {
        String[] text = id == null ? null : SECRETS.get(id);
        return text == null ? null : text[1];
    }

    public static boolean isSecret(String id) {
        return id != null && SECRETS.containsKey(id);
    }

    private static void learn(String id, Boolean hidden, String name, String desc) {
        if (id == null || !Boolean.TRUE.equals(hidden) || name == null || name.isBlank()) return;
        SECRETS.put(id, new String[] { name, desc == null ? "" : desc });
    }

    public static boolean claimed(String id, int node, Node entry) {
        return (entry != null && Boolean.TRUE.equals(entry.claimed))
                || CLAIMING.contains(id + ":" + node);
    }

    public static void stale() {
        at = 0L;
    }

    public static void refresh() {
        long now = System.currentTimeMillis();
        long wait = known ? Attention.interval(TTL_MS, AWAY_TTL_MS) : RETRY_MS;
        if (now - at < wait) return;

        String token = MojangAuth.token();
        if (token == null) return;
        if (!FETCHING.compareAndSet(false, true)) return;

        String url = MojangAuth.httpBase(ConfigManager.get().relayUrl) + "/achievements?t="
                + java.net.URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8);

        MojangAuth.get(url)
                .thenApply(body -> GSON.fromJson(body, Book.class))
                .whenComplete((answer, error) -> {
                    if (error == null && answer != null && answer.error == null
                            && answer.achievements != null) {
                        book = answer;
                        known = true;

                        for (Entry entry : answer.achievements) {
                            if (entry == null) continue;
                            learn(entry.id, entry.hidden, entry.name, entry.desc);
                            if (entry.nodes == null) continue;
                            for (int i = 0; i < entry.nodes.length; i++) {
                                if (entry.nodes[i] != null && Boolean.TRUE.equals(entry.nodes[i].claimed)) {
                                    CLAIMING.remove(entry.id + ":" + i);
                                }
                            }
                        }
                        if (answer.unclaimed != null) unclaimed = answer.unclaimed;
                        stamp = System.currentTimeMillis();
                    } else {
                        VivetSpeedrun.LOGGER.debug("Could not read achievements",
                                error);
                    }
                    at = System.currentTimeMillis();
                    FETCHING.set(false);
                });
    }

    public static void accept(Frame frame) {
        if (frame == null) return;
        if (frame.unclaimed != null) unclaimed = frame.unclaimed;

        if (frame.done != null && frame.done.length > 0) {
            stale();
            for (Done done : frame.done) {
                if (done == null || done.id == null) continue;
                learn(done.id, done.hidden, done.name, done.desc);
                SINK.accept(done);
            }
        }
        stamp = System.currentTimeMillis();
    }

    private static final long CLAIM_GAP_MS = 400L;
    private static volatile long lastClaimAt;

    public static boolean claim(String id, int node) {
        String token = MojangAuth.token();
        if (token == null || id == null) return false;

        long now = System.currentTimeMillis();
        if (now - lastClaimAt < CLAIM_GAP_MS) return false;

        String key = id + ":" + node;
        if (!CLAIMING.add(key)) return false;
        lastClaimAt = now;
        unclaimed = Math.max(0, unclaimed - 1);
        stamp = System.currentTimeMillis();

        Map<String, Object> body = Map.of("token", token, "id", id, "node", node);
        String url = MojangAuth.httpBase(ConfigManager.get().relayUrl) + "/achievements/claim";

        HttpRequest request = MojangAuth.request(url)
                .timeout(TIMEOUT)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .build();

        MojangAuth.http().sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .whenComplete((answer, error) -> {
                    Claimed claimed = null;
                    try {
                        claimed = answer == null ? null : GSON.fromJson(answer, Claimed.class);
                    } catch (Exception ignored) {
                    }

                    if (claimed != null && Boolean.TRUE.equals(claimed.ok)) {
                        MojangAuth.noteCoins(claimed.balance);
                        if (claimed.unclaimed != null) unclaimed = claimed.unclaimed;
                        if (claimed.granted != null && claimed.granted.length > 0) {
                            Wardrobe.stale();
                        }
                    } else {
                        CLAIMING.remove(key);
                        VivetSpeedrun.LOGGER.debug("Claim refused: {}",
                                claimed == null ? error : claimed.error);
                    }

                    stale();
                    stamp = System.currentTimeMillis();
                });
        return true;
    }
}
