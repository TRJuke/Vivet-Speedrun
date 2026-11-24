package com.vivet.speedrun.p2p;

import com.google.gson.Gson;
import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.config.ConfigManager;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class Watching {
    private static final Gson GSON = new Gson();

    private Watching() {
    }

    public static final class Ticket {
        public String host;
        public int port;
        public String match;

        public String racer;
        public String racerUuid;

        public String kind;
        public String band;

        public Boolean staff;

        public String error;
        public Boolean full;
    }

    public static final class Match {
        public String match;
        public String kind;
        public String band;
        public Long startedAt;
        public List<Social.Person> racers;
    }

    public static final class Live {
        public Long now;
        public List<Match> matches;
        public String error;
    }

    private static volatile Live live;
    private static volatile long liveAt;
    private static volatile long stamp;
    private static final AtomicBoolean FETCHING = new AtomicBoolean();

    public static void watch(String uuid, Consumer<Ticket> onAnswer, Consumer<String> onFailed) {
        String token = MojangAuth.token();
        String id = Profiles.normalise(uuid);
        if (token == null || id == null) {
            onFailed.accept("Sign in first.");
            return;
        }

        MojangAuth.get(base() + "/spectate?t=" + encode(token) + "&uuid=" + encode(id))
                .thenApply(body -> GSON.fromJson(body, Ticket.class))
                .whenComplete((ticket, error) -> {
                    if (error != null || ticket == null) {
                        VivetSpeedrun.LOGGER.info("Spectate request failed: {}",
                                error == null ? "empty response" : error.toString());
                        onFailed.accept("Couldn't connect. Open a ticket on the Discord for help.");
                        return;
                    }
                    onAnswer.accept(ticket);
                });
    }

    public static long stamp() {
        return stamp;
    }

    public static boolean liveKnown() {
        return live != null;
    }

    public static List<Match> liveMatches() {
        Live held = live;
        return held == null || held.matches == null ? List.of() : held.matches;
    }

    public static long runningFor(Match match) {
        Live held = live;
        if (held == null || held.now == null || match == null || match.startedAt == null
                || match.startedAt <= 0) {
            return 0L;
        }
        return Math.max(0L, held.now + (System.currentTimeMillis() - liveAt) - match.startedAt);
    }

    public static void loadLive() {
        String token = MojangAuth.token();
        if (token == null || !FETCHING.compareAndSet(false, true)) return;

        MojangAuth.get(base() + "/spectate/live?t=" + encode(token))
                .thenApply(body -> GSON.fromJson(body, Live.class))
                .whenComplete((answer, error) -> {
                    FETCHING.set(false);
                    if (error != null || answer == null || answer.error != null) {
                        VivetSpeedrun.LOGGER.info("Could not load live matches: {}",
                                error != null ? error.toString()
                                        : answer == null ? "empty response" : answer.error);
                        return;
                    }
                    liveAt = System.currentTimeMillis();
                    live = answer;
                    stamp = liveAt;
                });
    }

    public static void forgetLive() {
        live = null;
        stamp = System.currentTimeMillis();
    }

    private static String base() {
        return MojangAuth.httpBase(ConfigManager.get().relayUrl);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
