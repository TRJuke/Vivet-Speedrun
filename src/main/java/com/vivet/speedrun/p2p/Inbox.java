package com.vivet.speedrun.p2p;

import com.google.gson.Gson;
import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.config.ConfigManager;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Inbox {
    private static final Gson GSON = new Gson();

    private static final java.time.Duration TIMEOUT = java.time.Duration.ofSeconds(15);

    private static final long TTL_MS = 120_000L;
    private static final long AWAY_TTL_MS = 600_000L;

    private static final long RETRY_MS = 30_000L;

    private Inbox() {
    }

    public static final class Line {
        public long id;

        public String kind;

        public String actor_uuid;
        public String actor_name;

        public String title;

        public String body;

        public String ref;

        public long created_at;

        public long read_at;

        public boolean unread() {
            return this.read_at <= 0L;
        }
    }

    public static final class Book {
        public List<Line> items;
        public Integer unread;
        public String error;
    }

    private static volatile Book book;
    private static volatile boolean known;
    private static volatile long at;
    private static volatile long stamp;

    private static final AtomicBoolean FETCHING = new AtomicBoolean();

    private static final Set<Long> announced = new HashSet<>();

    public static boolean known() {
        return known;
    }

    public static long stamp() {
        return stamp;
    }

    public static List<Line> lines() {
        Book held = book;
        return held == null || held.items == null ? List.of() : held.items;
    }

    public static int unread() {
        Book held = book;
        return held == null || held.unread == null ? 0 : held.unread;
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

        String url = MojangAuth.httpBase(ConfigManager.get().relayUrl)
                + "/inbox?t=" + encode(token);

        MojangAuth.get(url)
                .thenApply(body -> GSON.fromJson(body, Book.class))
                .whenComplete((answer, error) -> {
                    if (error == null && answer != null && answer.error == null) {
                        take(answer);
                    } else {
                        VivetSpeedrun.LOGGER.debug("Could not load inbox: {}",
                                error == null ? "empty response" : error.toString());
                    }

                    at = System.currentTimeMillis();
                    FETCHING.set(false);
                });
    }

    static void take(Book answer) {
        Book previous = book;

        book = answer;
        known = true;
        at = System.currentTimeMillis();
        stamp = at;

        if (previous == null) {
            for (Line line : lines()) announced.add(line.id);
            return;
        }

        List<Line> fresh = new ArrayList<>();
        for (Line line : lines()) {
            if (line == null || !line.unread()) continue;
            if (announced.add(line.id)) fresh.add(line);
        }

        for (int i = fresh.size() - 1; i >= 0; i--) SINK.accept(fresh.get(i));
    }

    public static void read(Long id) {
        Map<String, Object> body = new HashMap<>();
        if (id != null) body.put("id", id);
        post(body);
    }

    public static void read(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;

        Map<String, Object> body = new HashMap<>();
        body.put("id", ids);
        post(body);
    }

    private static final Set<Long> ANSWERED_WELCOME = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static Line welcome() {
        for (Line line : lines()) {
            if (line != null && "welcome".equals(line.kind) && !ANSWERED_WELCOME.contains(line.id)) {
                return line;
            }
        }
        return null;
    }

    public static boolean answered(long id) {
        return ANSWERED_WELCOME.contains(id);
    }

    public static void answerWelcome(long id) {
        ANSWERED_WELCOME.add(id);
        drop(id);
    }

    public static void drop(long id) {
        Map<String, Object> body = new HashMap<>();
        body.put("drop", id);
        post(body);
    }

    public static void drop(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;

        Map<String, Object> body = new HashMap<>();
        body.put("drop", ids);
        post(body);
    }

    private static void post(Map<String, Object> body) {
        String token = MojangAuth.token();
        if (token == null) return;

        body.put("token", token);

        String url = MojangAuth.httpBase(ConfigManager.get().relayUrl) + "/inbox/read";

        HttpRequest request = MojangAuth.request(url)
                .timeout(TIMEOUT)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .build();

        MojangAuth.http().sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .whenComplete((answer, error) -> {
                    if (error != null || answer == null) {
                        VivetSpeedrun.LOGGER.debug("Could not update inbox",
                                error);
                        return;
                    }

                    try {
                        Book fresh = GSON.fromJson(answer, Book.class);
                        if (fresh != null && fresh.error == null) take(fresh);
                    } catch (Exception ignored) {
                    }
                });
    }

    private static volatile java.util.function.Consumer<Line> SINK = line -> { };

    public static void bindArrivals(java.util.function.Consumer<Line> sink) {
        if (sink != null) SINK = sink;
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
