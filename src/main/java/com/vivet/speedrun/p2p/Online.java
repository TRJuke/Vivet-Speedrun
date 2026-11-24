package com.vivet.speedrun.p2p;

import com.google.gson.Gson;
import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.config.ConfigManager;

import java.util.concurrent.atomic.AtomicBoolean;

public final class Online {
    private static volatile int count = -1;

    private static volatile int discord = -1;

    private static volatile long at;
    private static final AtomicBoolean FETCHING = new AtomicBoolean();

    private static volatile boolean heard;
    private static volatile long askedAt;

    private static final long TTL_MS = 60_000L;

    private static final long AWAY_TTL_MS = 600_000L;

    private static final long RETRY_MS = 30_000L;

    private static final Gson GSON = new Gson();

    private static final class Answer {
        int online;
        Integer discord;
        String error;
    }

    private Online() {
    }

    public static int count() {
        return count;
    }

    public static int discord() {
        return discord;
    }

    public static void seed(int online) {
        if (online < 0) return;

        count = online;
        at = System.currentTimeMillis();
    }

    public static void refresh() {
        long now = System.currentTimeMillis();

        boolean whole = count >= 0 && heard;
        if (whole ? now - at < Attention.interval(TTL_MS, AWAY_TTL_MS) : now - askedAt < RETRY_MS) {
            return;
        }
        if (!FETCHING.compareAndSet(false, true)) return;

        askedAt = now;
        String url = MojangAuth.httpBase(ConfigManager.get().relayUrl) + "/online";

        MojangAuth.get(url)
                .thenApply(body -> GSON.fromJson(body, Answer.class))
                .whenComplete((answer, error) -> {
                    if (at > now) {
                        if (error == null && answer != null && answer.error == null) {
                            heard = true;
                            if (answer.discord != null && answer.discord >= 0) discord = answer.discord;
                        }
                        FETCHING.set(false);
                        return;
                    }

                    if (error == null && answer != null && answer.error == null
                            && answer.online >= 0) {
                        count = answer.online;
                        heard = true;
                        if (answer.discord != null && answer.discord >= 0) discord = answer.discord;
                    } else {
                        VivetSpeedrun.LOGGER.debug("Could not get player count");
                    }

                    at = System.currentTimeMillis();
                    FETCHING.set(false);
                });
    }
}
