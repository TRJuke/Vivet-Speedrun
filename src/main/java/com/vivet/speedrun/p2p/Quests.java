package com.vivet.speedrun.p2p;

import com.google.gson.Gson;
import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.config.ConfigManager;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class Quests {
    private static final Gson GSON = new Gson();
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    public static final class Book {
        public Long day;
        public Long resetAt;
        public Integer rerolls;
        public Integer rerollsMax;
        public Quest[] list;

        public Done[] done;

        public Integer paid;
        public String error;
    }

    public static final class Quest {
        public Integer slot;
        public String id;
        public String rarity;
        public Integer target;
        public Integer progress;
        public Boolean done;
        public Integer pay;

        public String text;
    }

    public static final class Done {
        public String id;
        public String rarity;
        public Integer target;
        public Integer pay;
        public String text;
    }

    private static volatile Book book;
    private static volatile long stamp;
    private static volatile long at;
    private static final AtomicBoolean FETCHING = new AtomicBoolean();
    private static final AtomicBoolean REROLLING = new AtomicBoolean();

    private static final long RETRY_MS = 30_000L;

    private static final long SPREAD_MS = ThreadLocalRandom.current().nextLong(1_000L, 45_000L);

    private static volatile Consumer<Done> SINK = done -> { };

    private Quests() {
    }

    public static void bindDone(Consumer<Done> sink) {
        if (sink != null) SINK = sink;
    }

    public static Book book() {
        return book;
    }

    public static long stamp() {
        return stamp;
    }

    public static boolean current() {
        Book held = book;
        return held != null && held.list != null
                && (held.resetAt == null || System.currentTimeMillis() < held.resetAt);
    }

    public static long untilReset() {
        Book held = book;
        if (held == null || held.resetAt == null) return -1L;
        return Math.max(0L, held.resetAt - System.currentTimeMillis());
    }

    public static boolean rerolling() {
        return REROLLING.get();
    }

    public static void refresh() {
        long now = System.currentTimeMillis();
        Book held = book;
        boolean due = held == null || held.list == null
                || (held.resetAt != null && now >= held.resetAt + SPREAD_MS);
        if (!due) return;
        if (now - at < RETRY_MS) return;

        String token = MojangAuth.token();
        if (token == null) return;
        if (!FETCHING.compareAndSet(false, true)) return;
        at = now;

        String url = MojangAuth.httpBase(ConfigManager.get().relayUrl) + "/quests?t="
                + java.net.URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8);

        MojangAuth.get(url)
                .thenApply(body -> GSON.fromJson(body, Book.class))
                .whenComplete((answer, error) -> {
                    if (error == null && answer != null && answer.error == null && answer.list != null) {
                        keep(answer);
                    } else {
                        VivetSpeedrun.LOGGER.debug("Could not load daily quests", error);
                    }
                    at = System.currentTimeMillis();
                    FETCHING.set(false);
                });
    }

    private static void keep(Book answer) {
        answer.done = null;
        answer.paid = null;
        book = answer;
        stamp = System.currentTimeMillis();
    }

    public static void accept(Book frame) {
        if (frame == null) return;
        Done[] done = frame.done;
        int paid = frame.paid == null ? 0 : frame.paid;
        if (frame.list != null) keep(frame);

        if (done != null) {
            for (Done each : done) {
                if (each != null && each.id != null) SINK.accept(each);
            }
        }

        if (paid > 0) PostMatch.addQuestCoins(paid);
        stamp = System.currentTimeMillis();
    }

    private static final class Rerolled {
        Long day;
        Long resetAt;
        Integer rerolls;
        Integer rerollsMax;
        Quest[] list;
        String error;
    }

    public static void reroll(Consumer<String> done) {
        String token = MojangAuth.token();
        if (token == null) return;
        if (!REROLLING.compareAndSet(false, true)) return;
        stamp = System.currentTimeMillis();

        String url = MojangAuth.httpBase(ConfigManager.get().relayUrl) + "/quests/reroll";
        HttpRequest request = MojangAuth.request(url)
                .timeout(TIMEOUT)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(Map.of("token", token))))
                .build();

        MojangAuth.http().sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .whenComplete((answer, error) -> {
                    Rerolled rerolled = null;
                    try {
                        rerolled = answer == null ? null : GSON.fromJson(answer, Rerolled.class);
                    } catch (Exception ignored) {
                    }
                    String refused = null;
                    if (rerolled != null && rerolled.error == null && rerolled.list != null) {
                        Book next = new Book();
                        next.day = rerolled.day;
                        next.resetAt = rerolled.resetAt;
                        next.rerolls = rerolled.rerolls;
                        next.rerollsMax = rerolled.rerollsMax;
                        next.list = rerolled.list;
                        keep(next);
                    } else {
                        refused = rerolled != null && rerolled.error != null ? rerolled.error : "";
                        VivetSpeedrun.LOGGER.debug("Reroll refused: {}",
                                rerolled == null ? error : rerolled.error);
                    }
                    REROLLING.set(false);
                    stamp = System.currentTimeMillis();
                    if (done != null) done.accept(refused);
                });
    }
}
