package com.vivet.speedrun.p2p;

import com.google.gson.Gson;
import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.config.ConfigManager;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Social {
    private static final Gson GSON = new Gson();

    private static final java.time.Duration TIMEOUT = java.time.Duration.ofSeconds(15);

    private static final long TTL_MS = 60_000L;
    private static final long AWAY_TTL_MS = 300_000L;

    private static final long RETRY_MS = 15_000L;

    private static final long TYPING_GAP_MS = 250L;

    private Social() {
    }

    public static final class Person {
        public String uuid;
        public String name;
        public String label;
        public Integer rating;
        public String rank;
        public boolean placed;

        public Integer level;

        public String background;

        public Integer place;

        public Long since;
    }

    public static final class Book {
        public String uuid;
        public List<Person> friends;
        public List<Person> incoming;
        public List<Person> outgoing;
        public List<Person> following;
        public List<Person> blocked;

        public Integer followerCount;

        public String error;
    }

    public static final class Found {
        public List<Person> players;
        public String error;
    }

    private static volatile Book book;
    private static volatile boolean known;
    private static volatile long at;
    private static volatile long stamp;

    private static final AtomicBoolean FETCHING = new AtomicBoolean();

    public static boolean known() {
        return known;
    }

    public static long stamp() {
        return stamp;
    }

    private static List<Person> listOf(List<Person> list) {
        return list == null ? List.of() : list;
    }

    public static List<Person> friends() {
        Book held = book;
        return held == null ? List.of() : listOf(held.friends);
    }

    public static List<Person> incoming() {
        Book held = book;
        return held == null ? List.of() : listOf(held.incoming);
    }

    public static List<Person> outgoing() {
        Book held = book;
        return held == null ? List.of() : listOf(held.outgoing);
    }

    public static List<Person> following() {
        Book held = book;
        return held == null ? List.of() : listOf(held.following);
    }

    public static List<Person> blocked() {
        Book held = book;
        return held == null ? List.of() : listOf(held.blocked);
    }

    public static boolean isFriendNamed(String name) {
        if (name == null || name.isBlank()) return false;

        for (Person person : friends()) {
            if (person != null && name.equalsIgnoreCase(person.name)) return true;
        }
        return false;
    }

    public static int friendCount() {
        return friends().size();
    }

    public static int waitingCount() {
        return incoming().size();
    }

    private static boolean holds(List<Person> list, String uuid) {
        String id = Profiles.normalise(uuid);
        if (id == null) return false;

        for (Person person : list) {
            if (person != null && id.equals(Profiles.normalise(person.uuid))) return true;
        }
        return false;
    }

    public static Standing standingWith(MojangAuth.Profile profile) {
        if (profile == null) return new Standing(false, false, false, false, false, false);

        MojangAuth.Relation told = profile.relation;
        boolean blockedBy = told != null && told.blockedBy;

        if (!known) {
            return told == null
                    ? new Standing(false, false, false, false, false, false)
                    : new Standing(told.friend, told.incoming, told.outgoing, told.following,
                                   told.blocked, told.blockedBy);
        }

        String uuid = profile.uuid;
        return new Standing(holds(friends(), uuid), holds(incoming(), uuid),
                            holds(outgoing(), uuid), holds(following(), uuid),
                            holds(blocked(), uuid), blockedBy);
    }

    public static Standing standingWith(String uuid) {
        String id = Profiles.normalise(uuid);
        if (id == null || !known) {
            return new Standing(false, false, false, false, false, false);
        }

        return new Standing(holds(friends(), id), holds(incoming(), id), holds(outgoing(), id),
                            holds(following(), id), holds(blocked(), id), false);
    }

    public record Standing(boolean friend, boolean incoming, boolean outgoing, boolean following,
                           boolean blocked, boolean blockedBy) {
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
                + "/social?t=" + encode(token);

        MojangAuth.get(url)
                .thenApply(body -> GSON.fromJson(body, Book.class))
                .whenComplete((answer, error) -> {
                    if (error == null && answer != null && answer.error == null) {
                        take(answer);
                    } else {
                        VivetSpeedrun.LOGGER.debug("Could not load friends: {}", error == null ? "empty response" : error.toString());
                    }

                    at = System.currentTimeMillis();
                    FETCHING.set(false);
                });
    }

    static void take(Book answer) {
        book = answer;
        known = true;
        at = System.currentTimeMillis();
        stamp = at;
    }

    public static void act(String action, String uuid) {
        String token = MojangAuth.token();
        String id = Profiles.normalise(uuid);
        if (token == null || id == null) return;

        Map<String, Object> body = new HashMap<>();
        body.put("token", token);
        body.put("action", action);
        body.put("uuid", id);

        String url = MojangAuth.httpBase(ConfigManager.get().relayUrl) + "/social/act";

        HttpRequest request = MojangAuth.request(url)
                .timeout(TIMEOUT)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .build();

        MojangAuth.http().sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .whenComplete((answer, error) -> {
                    if (error != null || answer == null) {
                        VivetSpeedrun.LOGGER.debug("Could not update friend",
                                error);
                        return;
                    }

                    try {
                        Book fresh = GSON.fromJson(answer, Book.class);
                        if (fresh != null && fresh.error == null) take(fresh);
                    } catch (Exception ignored) {
                    }

                    Profiles.again(id);
                });
    }

    private static volatile String typing = "";
    private static volatile long typedAt;

    private static volatile String asked = "";

    private static volatile List<Person> results = List.of();
    private static volatile String resultsFor = "";
    private static volatile long searchStamp;
    private static volatile long retryAfter;

    private static final AtomicBoolean SEARCHING = new AtomicBoolean();

    public static void typed(String query) {
        String wanted = query == null ? "" : query.trim();
        if (wanted.equals(typing)) return;

        typing = wanted;
        typedAt = System.currentTimeMillis();

        if (!wanted.equals(resultsFor)) {
            results = List.of();
            searchStamp = System.currentTimeMillis();
        }
    }

    public static void pump() {
        String wanted = typing;
        long now = System.currentTimeMillis();

        if (wanted.length() < 2) return;
        if (wanted.equals(asked)) return;
        if (now - typedAt < TYPING_GAP_MS) return;
        if (now < retryAfter) return;

        String token = MojangAuth.token();
        if (token == null) return;
        if (!SEARCHING.compareAndSet(false, true)) return;

        asked = wanted;

        String url = MojangAuth.httpBase(ConfigManager.get().relayUrl)
                + "/social/search?t=" + encode(token) + "&q=" + encode(wanted);

        MojangAuth.get(url)
                .thenApply(body -> GSON.fromJson(body, Found.class))
                .whenComplete((answer, error) -> {
                    if (error == null && answer != null && answer.error == null) {
                        results = answer.players == null ? List.of() : answer.players;
                        resultsFor = wanted;
                        searchStamp = System.currentTimeMillis();
                    } else {
                        asked = "";
                        retryAfter = System.currentTimeMillis() + RETRY_MS;
                    }
                    SEARCHING.set(false);
                });
    }

    public static List<Person> results() {
        return results;
    }

    public static String resultsFor() {
        return resultsFor;
    }

    public static boolean searching() {
        return SEARCHING.get();
    }

    public static long searchStamp() {
        return searchStamp;
    }

    public static void clearSearch() {
        typing = "";
        asked = "";
        results = List.of();
        resultsFor = "";
        searchStamp = System.currentTimeMillis();
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
