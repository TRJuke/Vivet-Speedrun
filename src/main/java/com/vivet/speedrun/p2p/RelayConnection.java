package com.vivet.speedrun.p2p;

import com.google.gson.Gson;
import com.vivet.speedrun.VivetSpeedrun;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class RelayConnection {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);

    private static final long PING_INTERVAL_MS = 5_000L;

    private static final long SILENT_LIMIT_MS = 20_000L;

    private static final ScheduledExecutorService KEEPALIVE =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "vivet-relay-keepalive");
                thread.setDaemon(true);
                return thread;
            });

    public static final int HOST_PEER = 0;

    private static final Gson GSON = new Gson();

    public static final class Look {
        public java.util.Map<String, String> slots;

        public java.util.Map<String, String> modes;
    }

    public static java.util.Map<String, java.util.Map<String, String>> slotsOf(
            java.util.Map<String, Look> looks) {
        if (looks == null) return null;

        java.util.Map<String, java.util.Map<String, String>> out = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String, Look> entry : looks.entrySet()) {
            Look look = entry.getValue();
            out.put(entry.getKey(), look == null || look.slots == null
                    ? java.util.Map.of() : java.util.Map.copyOf(look.slots));
        }
        return out;
    }

    public static final class Frame {
        public String op;
        public String data;
        public String room;
        public String reason;
        public String state;
        public Integer id;
        public Integer from;
        public Integer to;
        public Integer protocol;

        public java.util.Map<String, Integer> counts;

        public String[] bands;

        public Integer size;

        public String band;

        public Integer position;

        public java.util.Map<String, Integer> rankedCounts;

        public Boolean casualClosed;

        public Boolean ranked;

        public Long until;

        public java.util.Map<String, String> labels;

        public java.util.Map<String, String> uuids;

        public java.util.Map<String, Look> looks;

        public String name;

        public String outcome;

        public String leaver;

        public Boolean voided;

        public String loser;

        public MojangAuth.Profile profile;

        public Integer delta;

        public Xp xp;

        public String winner;

        public Integer seconds;

        public Boolean walkover;

        public Boolean conceded;

        public String matchId;

        public Hosted hosted;
    }

    public static final class Hosted {
        public String match;
        public String host;
        public Integer port;
        public Boolean ranked;
        public String opponent;
        public String opponentUuid;
    }

    public static final class Xp {
        public int gained;

        public int total;

        public int level;

        public int into;
        public int need;
    }

    public interface Listener {
        void onFrame(Frame frame);

        void onClosed(String reason);
    }

    private static volatile HttpClient client;

    private final Listener listener;

    private volatile WebSocket socket;
    private volatile boolean closed;

    private CompletableFuture<WebSocket> sends = CompletableFuture.completedFuture(null);

    private final StringBuilder partial = new StringBuilder();

    private volatile ScheduledFuture<?> keepalive;

    private volatile long lastHeard;

    private final Adapter adapter = new Adapter();

    public RelayConnection(Listener listener) {
        this.listener = listener;
    }

    public void connect(URI uri, Consumer<String> onFailed) {
        MojangAuth.Lifted lifted = MojangAuth.liftToken(uri.toString());
        VivetSpeedrun.LOGGER.info("Connecting to {}", lifted.uri());

        java.net.http.WebSocket.Builder builder = httpClient().newWebSocketBuilder()
                .connectTimeout(CONNECT_TIMEOUT);
        if (lifted.token() != null) builder.header("Authorization", "Bearer " + lifted.token());
        builder.buildAsync(lifted.uri(), this.adapter)
                .whenComplete((opened, error) -> {
                    if (error != null) {
                        VivetSpeedrun.LOGGER.info("Could not connect: {}",
                                error.toString());
                        this.closed = true;
                        onFailed.accept(describe(error));
                        return;
                    }

                    if (this.closed) return;
                    this.socket = opened;
                });
    }

    private void startKeepalive(WebSocket open) {
        this.keepalive = KEEPALIVE.scheduleWithFixedDelay(() -> {
            if (this.closed) return;

            long quiet = System.currentTimeMillis() - this.lastHeard;
            if (quiet > SILENT_LIMIT_MS) {
                VivetSpeedrun.LOGGER.info("Relay connection silent for {} ms, dropping it", quiet);
                open.abort();
                this.adapter.fire("Connection lost.");
                return;
            }

            try {
                open.sendPing(ByteBuffer.allocate(0));
            } catch (Exception e) {
                VivetSpeedrun.LOGGER.debug("Keepalive failed", e);
            }
        }, PING_INTERVAL_MS, PING_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void stopKeepalive() {
        ScheduledFuture<?> running = this.keepalive;
        this.keepalive = null;
        if (running != null) running.cancel(false);
    }

    public void sendFrame(Object frame) {
        sendRaw(GSON.toJson(frame));
    }

    public void send(String line, Integer to) {
        Frame frame = new Frame();
        frame.op = "msg";
        frame.data = line;
        frame.to = to;

        sendRaw(GSON.toJson(frame));
    }

    private synchronized void sendRaw(String text) {
        WebSocket open = this.socket;
        if (open == null || this.closed) return;

        this.sends = this.sends
                .thenCompose(ignored -> open.sendText(text, true))
                .exceptionally(error -> {
                    VivetSpeedrun.LOGGER.debug("Send failed", error);
                    return open;
                });
    }

    public void close() {
        this.closed = true;
        stopKeepalive();

        WebSocket open = this.socket;
        this.socket = null;
        if (open == null) return;

        try {
            open.sendClose(WebSocket.NORMAL_CLOSURE, "leaving");
        } catch (Exception e) {
            open.abort();
        }
    }

    private static HttpClient httpClient() {
        HttpClient existing = client;
        if (existing != null) return existing;

        synchronized (RelayConnection.class) {
            if (client == null) {
                client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
            }
            return client;
        }
    }

    private static String describe(Throwable error) {
        return "Couldn't connect. Open a ticket on the Discord for help.";
    }

    private final class Adapter implements WebSocket.Listener {
        @Override
        public void onOpen(WebSocket webSocket) {
            if (RelayConnection.this.closed) {
                try {
                    webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "leaving");
                } catch (Exception e) {
                    webSocket.abort();
                }
                return;
            }
            RelayConnection.this.socket = webSocket;
            RelayConnection.this.lastHeard = System.currentTimeMillis();
            RelayConnection.this.startKeepalive(webSocket);
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
            RelayConnection.this.lastHeard = System.currentTimeMillis();
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
            RelayConnection.this.lastHeard = System.currentTimeMillis();
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            webSocket.request(1);
            RelayConnection.this.lastHeard = System.currentTimeMillis();

            RelayConnection.this.partial.append(data);
            if (!last) return null;

            String line = RelayConnection.this.partial.toString();
            RelayConnection.this.partial.setLength(0);

            try {
                Frame frame = GSON.fromJson(line, Frame.class);
                if (frame != null && frame.op != null) RelayConnection.this.listener.onFrame(frame);
            } catch (Exception e) {
                VivetSpeedrun.LOGGER.warn("Bad message from server", e);
            }
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int status, String reason) {
            fire(reason == null || reason.isBlank()
                    ? "Connection closed."
                    : reason);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            VivetSpeedrun.LOGGER.debug("Connection error", error);
            fire("Connection lost.");
        }

        private void fire(String reason) {
            if (RelayConnection.this.closed) return;

            RelayConnection.this.closed = true;
            RelayConnection.this.stopKeepalive();
            RelayConnection.this.listener.onClosed(reason);
        }
    }

    public static URI hostUri(String relayUrl) {
        String token = identity();
        return URI.create(websocketBase(relayUrl) + "/host"
                + (token.isEmpty() ? "" : "?" + token.substring(1)));
    }

    private static String identity() {
        String token = com.vivet.speedrun.p2p.MojangAuth.token();
        return token == null || token.isBlank()
                ? ""
                : "&t=" + java.net.URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    public static URI joinUri(String relayUrl, String roomCode) {
        String encoded = java.net.URLEncoder.encode(roomCode, StandardCharsets.UTF_8);
        return URI.create(websocketBase(relayUrl) + "/join?room=" + encoded + identity());
    }

    public static URI matchmakeUri(String relayUrl, int protocol, String token) {
        return URI.create(websocketBase(relayUrl) + "/matchmake?v=" + protocol
                + "&h=1&mc=" + java.net.URLEncoder.encode(
                        net.minecraft.SharedConstants.getCurrentVersion().name(), StandardCharsets.UTF_8)
                + (token == null || token.isBlank()
                        ? ""
                        : "&t=" + java.net.URLEncoder.encode(token, StandardCharsets.UTF_8)));
    }

    private static String websocketBase(String relayUrl) {
        String trimmed = relayUrl == null ? "" : relayUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }

        if (trimmed.startsWith("https://")) return "wss://" + trimmed.substring(8);
        if (trimmed.startsWith("http://")) return "ws://" + trimmed.substring(7);
        if (trimmed.startsWith("wss://") || trimmed.startsWith("ws://")) return trimmed;

        return "wss://" + trimmed;
    }
}
