package com.vivet.speedrun.p2p;

import com.google.gson.Gson;
import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.config.ConfigManager;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Chat {
    private static final Gson GSON = new Gson();

    private static final java.time.Duration TIMEOUT = java.time.Duration.ofSeconds(15);

    private static final long TTL_MS = 120_000L;
    private static final long AWAY_TTL_MS = 600_000L;

    private static final long RETRY_MS = 30_000L;

    private static final long THREAD_TTL_MS = 60_000L;

    private static final long ERROR_MS = 6_000L;

    private Chat() {
    }

    public static final class Thread {
        public String uuid;
        public String name;
        public String label;
        public Integer rating;
        public String rank;
        public boolean placed;
        public Integer level;

        public String background;

        public String last;
        public String from;
        public Long at;

        public Integer unread;

        public int unreadCount() {
            return this.unread == null ? 0 : this.unread;
        }

        public long when() {
            return this.at == null ? 0L : this.at;
        }
    }

    public static final class Book {
        public List<Thread> threads;
        public Integer unread;

        public Boolean banned;

        public String bannedText;

        public String error;
    }

    public static final class Message {
        public String from;
        public String body;
        public Long at;

        public long when() {
            return this.at == null ? 0L : this.at;
        }
    }

    public static final class Conversation {
        public String uuid;
        public List<Message> messages;
        public String error;
    }

    private static volatile Book book;
    private static volatile boolean known;
    private static volatile long at;
    private static volatile long stamp;

    private static final AtomicBoolean FETCHING = new AtomicBoolean();

    private static final Map<String, List<Message>> held = new ConcurrentHashMap<>();
    private static final Map<String, Long> asked = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> loading = new ConcurrentHashMap<>();

    private static volatile String refusal = "";
    private static volatile long refusedAt;

    public static long stamp() {
        return stamp;
    }

    public static List<Thread> threads() {
        Book have = book;
        return have == null || have.threads == null ? List.of() : have.threads;
    }

    public static boolean banned() {
        Book have = book;
        return have != null && Boolean.TRUE.equals(have.banned);
    }

    public static String bannedText() {
        Book have = book;
        return have != null && have.bannedText != null && !have.bannedText.isBlank()
                ? have.bannedText : "vivet.chat.banned";
    }

    public static int unread() {
        Book have = book;
        return have == null || have.unread == null ? 0 : have.unread;
    }

    public static Thread threadWith(String uuid) {
        String id = Profiles.normalise(uuid);
        if (id == null) return null;

        for (Thread thread : threads()) {
            if (thread != null && id.equals(Profiles.normalise(thread.uuid))) return thread;
        }
        return null;
    }

    public static List<Message> messagesWith(String uuid) {
        String id = Profiles.normalise(uuid);
        if (id == null) return List.of();

        List<Message> lines = held.get(id);
        return lines == null ? List.of() : lines;
    }

    public static String refusal() {
        if (refusal.isEmpty()) return "";
        if (System.currentTimeMillis() - refusedAt > ERROR_MS) return "";
        return refusal;
    }

    public static void clearRefusal() {
        refusal = "";
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

        String url = MojangAuth.httpBase(ConfigManager.get().relayUrl) + "/chat?t=" + encode(token);

        MojangAuth.get(url)
                .thenApply(body -> GSON.fromJson(body, Book.class))
                .whenComplete((answer, error) -> {
                    if (error == null && answer != null && answer.error == null) {
                        takeBook(answer);
                    } else {
                        VivetSpeedrun.LOGGER.debug("Could not load chats: {}",
                                error == null ? "empty response" : error.toString());
                    }

                    at = System.currentTimeMillis();
                    FETCHING.set(false);
                });
    }

    public static void open(String uuid) {
        String id = Profiles.normalise(uuid);
        if (id == null) return;

        long now = System.currentTimeMillis();
        if (now - asked.getOrDefault(id, 0L) < THREAD_TTL_MS) return;
        if (Boolean.TRUE.equals(loading.putIfAbsent(id, Boolean.TRUE))) return;

        String token = MojangAuth.token();
        if (token == null) {
            loading.remove(id);
            return;
        }

        String url = MojangAuth.httpBase(ConfigManager.get().relayUrl)
                + "/chat/thread?t=" + encode(token) + "&uuid=" + id;

        MojangAuth.get(url)
                .thenApply(body -> GSON.fromJson(body, Conversation.class))
                .whenComplete((answer, error) -> {
                    if (error == null && answer != null && answer.error == null) {
                        takeConversation(id, answer.messages);
                    }

                    asked.put(id, System.currentTimeMillis());
                    loading.remove(id);
                });
    }

    public static void say(String uuid, String body) {
        String id = Profiles.normalise(uuid);
        if (id == null || body == null || body.isBlank()) return;

        String token = MojangAuth.token();
        if (token == null) return;

        Map<String, Object> payload = new HashMap<>();
        payload.put("token", token);
        payload.put("uuid", id);
        payload.put("body", body);

        post("/chat/send", payload, answer -> {
            Conversation fresh = GSON.fromJson(answer, Conversation.class);
            if (fresh == null) return;

            if (fresh.error != null) {
                refusal = fresh.error;
                refusedAt = System.currentTimeMillis();
                stamp = refusedAt;
                return;
            }

            takeConversation(id, fresh.messages);

            Thread thread = threadWith(id);
            if (thread != null && fresh.messages != null && !fresh.messages.isEmpty()) {
                Message last = fresh.messages.get(fresh.messages.size() - 1);
                thread.last = last.body;
                thread.from = last.from;
                thread.at = last.at;
            } else {
                stale();
                refresh();
            }
        });
    }

    public static void read(String uuid) {
        String id = Profiles.normalise(uuid);
        if (id == null) return;

        String token = MojangAuth.token();
        if (token == null) return;

        Map<String, Object> payload = new HashMap<>();
        payload.put("token", token);
        payload.put("uuid", id);

        post("/chat/read", payload, answer -> {
            Book fresh = GSON.fromJson(answer, Book.class);
            if (fresh != null && fresh.error == null) takeBook(fresh);
        });
    }

    public static void report(String uuid, String note) {
        String id = Profiles.normalise(uuid);
        if (id == null) return;

        String token = MojangAuth.token();
        if (token == null) return;

        Map<String, Object> payload = new HashMap<>();
        payload.put("token", token);
        payload.put("uuid", id);
        payload.put("note", note == null ? "" : note);

        post("/chat/report", payload, answer -> {
        });
    }

    private static void takeBook(Book answer) {
        book = answer;
        known = true;
        at = System.currentTimeMillis();
        stamp = at;
    }

    private static void takeConversation(String id, List<Message> messages) {
        List<Message> lines = new ArrayList<>();
        if (messages != null) {
            for (Message message : messages) {
                if (message != null && message.body != null) lines.add(message);
            }
        }

        held.put(id, lines);
        asked.put(id, System.currentTimeMillis());
        stamp = System.currentTimeMillis();
    }

    static void take(String uuid, String name, String body, long when) {
        String id = Profiles.normalise(uuid);
        if (id == null || body == null || body.isBlank()) return;

        Message message = new Message();
        message.from = id;
        message.body = body;
        message.at = when;

        List<Message> lines = held.get(id);
        if (lines != null) {
            List<Message> grown = new ArrayList<>(lines);
            grown.add(message);
            held.put(id, grown);
        }

        Thread thread = threadWith(id);
        if (thread == null) {
            stale();
            refresh();
        } else {
            thread.last = body;
            thread.from = id;
            thread.at = when;
            thread.unread = thread.unreadCount() + 1;

            Book have = book;
            if (have != null) have.unread = unread() + 1;
        }

        stamp = System.currentTimeMillis();
        SINK.accept(new Arrival(id, name == null ? "" : name, body, when));
    }

    public record Arrival(String uuid, String name, String body, long at) {
    }

    private static volatile java.util.function.Consumer<Arrival> SINK = arrival -> { };

    public static void bindArrivals(java.util.function.Consumer<Arrival> sink) {
        if (sink != null) SINK = sink;
    }

    private static void post(String path, Map<String, Object> payload,
                             java.util.function.Consumer<String> onAnswer) {
        String url = MojangAuth.httpBase(ConfigManager.get().relayUrl) + path;

        HttpRequest request = MojangAuth.request(url)
                .timeout(TIMEOUT)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)))
                .build();

        MojangAuth.http().sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .whenComplete((answer, error) -> {
                    if (error != null || answer == null) {
                        VivetSpeedrun.LOGGER.debug("Chat request failed", error);
                        return;
                    }

                    try {
                        onAnswer.accept(answer);
                    } catch (Exception ignored) {
                    }
                });
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
