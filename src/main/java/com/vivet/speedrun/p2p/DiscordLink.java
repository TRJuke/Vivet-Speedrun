package com.vivet.speedrun.p2p;

import com.google.gson.Gson;
import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.config.ConfigManager;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;

public final class DiscordLink {
    public static final String INVITE = "https://discord.gg/zjHv4N39FW";

    private static final Gson GSON = new Gson();
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private static volatile Boolean linked;
    private static volatile String code;
    private static volatile long expiresAt;
    private static volatile boolean failed;
    private static volatile long stamp;

    private static final AtomicBoolean ASKING = new AtomicBoolean();

    private static volatile IntConsumer LINKED = gained -> { };

    private DiscordLink() {
    }

    public static void bindLinked(IntConsumer sink) {
        if (sink != null) LINKED = sink;
    }

    public static final class Frame {
        public Boolean linked;
        public Integer gained;
    }

    private static final class Answer {
        String code;
        Long expiresAt;
        Boolean linked;
        String error;
    }

    public static Boolean linked() {
        Boolean known = linked;
        if (known != null) return known;
        MojangAuth.Profile profile = MojangAuth.knownProfile();
        return profile == null ? null : profile.discordLinked;
    }

    public static boolean isLinked() {
        return Boolean.TRUE.equals(linked());
    }

    public static String code() {
        return code != null && System.currentTimeMillis() < expiresAt ? code : null;
    }

    public static boolean expired() {
        return code != null && System.currentTimeMillis() >= expiresAt;
    }

    public static int secondsLeft() {
        return (int) Math.max(0L, (expiresAt - System.currentTimeMillis() + 999L) / 1000L);
    }

    public static boolean asking() {
        return ASKING.get();
    }

    public static boolean failed() {
        return failed;
    }

    public static long stamp() {
        return stamp;
    }

    public static void generate() {
        String token = MojangAuth.token();
        if (token == null) {
            failed = true;
            stamp = System.currentTimeMillis();
            return;
        }
        if (!ASKING.compareAndSet(false, true)) return;
        failed = false;
        stamp = System.currentTimeMillis();

        String url = MojangAuth.httpBase(ConfigManager.get().relayUrl) + "/discord/code";
        HttpRequest request = MojangAuth.request(url)
                .timeout(TIMEOUT)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(java.util.Map.of("token", token))))
                .build();

        MojangAuth.http().sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, error) -> {
                    try {
                        Answer answer = error == null ? GSON.fromJson(response.body(), Answer.class) : null;
                        if (answer != null && Boolean.TRUE.equals(answer.linked)) {
                            linked = true;
                            code = null;
                        } else if (answer != null && answer.code != null && answer.expiresAt != null) {
                            code = answer.code;

                            expiresAt = System.currentTimeMillis() + 298_000L;
                        } else {
                            failed = true;
                        }
                    } catch (RuntimeException e) {
                        VivetSpeedrun.LOGGER.debug("Bad link code response", e);
                        failed = true;
                    } finally {
                        ASKING.set(false);
                        stamp = System.currentTimeMillis();
                    }
                });
    }

    public static void forgetExpired() {
        if (expired()) code = null;
    }

    static void accept(Frame frame, boolean hello) {
        if (frame == null || frame.linked == null) return;
        boolean was = isLinked();
        linked = frame.linked;
        MojangAuth.Profile profile = MojangAuth.knownProfile();
        if (profile != null) profile.discordLinked = frame.linked;
        if (frame.linked) code = null;
        stamp = System.currentTimeMillis();

        if (!hello && frame.linked && !was) {
            LINKED.accept(frame.gained == null ? 0 : frame.gained);
        }
    }
}
