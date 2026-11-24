package com.vivet.speedrun.p2p;

import com.google.gson.Gson;
import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.config.ConfigManager;

import java.net.URI;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Hub {
    private static final Gson GSON = new Gson();

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);

    private static final long PING_INTERVAL_MS = 10_000L;

    private static final long SILENT_LIMIT_MS = 30_000L;

    private static volatile long lastHeard;

    private static final long FIRST_RETRY_MS = 5_000L;
    private static final long MAX_RETRY_MS = 120_000L;

    private static final ScheduledExecutorService TIMER =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "vivet-hub");
                thread.setDaemon(true);
                return thread;
            });

    private Hub() {
    }

    private static final class Frame {
        String op;

        String uuid;

        String[] online;

        Boolean on;

        Social.Book book;

        Inbox.Book inbox;

        String name;
        String label;
        String room;

        String body;
        Long at;

        InviteFrame[] invites;

        String[] invitesOut;

        String[] uuids;

        MojangAuth.Profile profile;
        Integer delta;
        RelayConnection.Xp xp;

        PostMatch.Coins coins;

        Achievements.Frame achievements;

        Quests.Book quests;

        Integer position;
        String error;
        String match;
        String host;
        Integer port;

        RelayConnection.Frame frame;
        String band;
        Boolean ranked;
        String opponent;
        String opponentUuid;

        Boolean rematch;

        String[] watchable;

        Boolean staff;

        DiscordLink.Frame discord;
    }

    private static volatile RankedApi.HostedTicket rematchReady;

    private static volatile String matchCalledOff;

    private static volatile String rematchRefused;

    public static RankedApi.HostedTicket takeRematch() {
        RankedApi.HostedTicket ready = rematchReady;
        rematchReady = null;
        return ready;
    }

    public static String takeCalledOff() {
        String off = matchCalledOff;
        matchCalledOff = null;
        return off;
    }

    public static String takeRematchRefused() {
        String refused = rematchRefused;
        rematchRefused = null;
        return refused;
    }

    private static volatile boolean hostedVoided;

    public static boolean takeHostedVoid() {
        boolean was = hostedVoided;
        hostedVoided = false;
        return was;
    }

    private static final class InviteFrame {
        String uuid;
        String name;
        String label;
        String room;
        Long at;
    }

    public record Invite(String uuid, String name, String label, String room, long at) {
    }

    private static volatile WebSocket socket;

    private static final int DISPLACED = 4001;

    private static final int SIGNED_OUT = 4003;

    private static final int THROTTLED = 4008;

    private static volatile boolean displaced;

    private static volatile boolean displacementHandled;

    private static volatile boolean wanted;
    private static volatile boolean connected;
    private static volatile ScheduledFuture<?> keepalive;
    private static volatile ScheduledFuture<?> redial;
    private static volatile long retryMs = FIRST_RETRY_MS;

    private static final AtomicBoolean DIALLING = new AtomicBoolean();

    private static final Set<String> here = ConcurrentHashMap.newKeySet();

    private static final Set<String> watchable = ConcurrentHashMap.newKeySet();

    private static volatile boolean staff;

    private static volatile long stamp;

    private static final Deque<Invite> invites = new ArrayDeque<>();

    private static final Set<String> invitedOut = ConcurrentHashMap.newKeySet();

    private static volatile java.util.function.Consumer<String> GONE = uuid -> { };

    public static void bindInviteGone(java.util.function.Consumer<String> sink) {
        if (sink != null) GONE = sink;
    }

    private static volatile java.util.function.BiConsumer<String, String> REFUSED = (uuid, name) -> { };

    public static void bindInviteRefused(java.util.function.BiConsumer<String, String> sink) {
        if (sink != null) REFUSED = sink;
    }

    private static final StringBuilder partial = new StringBuilder();

    public static long stamp() {
        return stamp;
    }

    public static boolean isOnline(String uuid) {
        String id = Profiles.normalise(uuid);
        return id != null && here.contains(id);
    }

    public static int onlineCount() {
        return here.size();
    }

    public static boolean isWatchable(String uuid) {
        String id = Profiles.normalise(uuid);
        return id != null && watchable.contains(id);
    }

    public static boolean isStaff() {
        return staff;
    }

    public static void dropInvite(Invite invite) {
        if (invite == null) return;

        synchronized (invites) {
            invites.remove(invite);
        }
        stamp = System.currentTimeMillis();

        java.util.Map<String, Object> frame = new java.util.HashMap<>();
        frame.put("op", "invite_answer");
        frame.put("uuid", invite.uuid());
        send(GSON.toJson(frame));
    }

    public static boolean hasInvited(String uuid) {
        String id = Profiles.normalise(uuid);
        return id != null && invitedOut.contains(id);
    }

    public static void closeInvites() {
        if (invitedOut.isEmpty()) return;

        invitedOut.clear();
        stamp = System.currentTimeMillis();

        send("{\"op\":\"invite_close\"}");
    }

    public static List<Invite> invites() {
        synchronized (invites) {
            return List.copyOf(invites);
        }
    }

    public static void open() {
        displaced = false;
        displacementHandled = false;
        wanted = true;
        dial();
    }

    public static boolean takeDisplacement() {
        if (!displaced || displacementHandled) return false;
        displacementHandled = true;
        return true;
    }

    public static void close() {
        wanted = false;
        connected = false;

        stopTimer(keepalive);
        keepalive = null;
        stopTimer(redial);
        redial = null;

        WebSocket open = socket;
        socket = null;
        here.clear();
        watchable.clear();

        if (open == null) return;
        try {
            open.sendClose(WebSocket.NORMAL_CLOSURE, "leaving");
        } catch (Exception e) {
            open.abort();
        }
    }

    private static void stopTimer(ScheduledFuture<?> running) {
        if (running != null) running.cancel(false);
    }

    private static void dial() {
        if (!wanted || connected) return;

        String token = MojangAuth.token();
        if (token == null || token.isBlank()) return;
        if (!DIALLING.compareAndSet(false, true)) return;

        URI uri = URI.create(websocketBase(ConfigManager.get().relayUrl) + "/hub");

        MojangAuth.http().newWebSocketBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .header("Authorization", "Bearer " + token)
                .buildAsync(uri, new Adapter())
                .whenComplete((opened, error) -> {
                    DIALLING.set(false);

                    if (error != null) {
                        VivetSpeedrun.LOGGER.debug("Could not reach the server: {}",
                                error.toString());
                        again();
                        return;
                    }
                    socket = opened;
                });
    }

    private static void again() {
        if (!wanted) return;

        long wait = retryMs + java.util.concurrent.ThreadLocalRandom.current().nextLong(retryMs / 4 + 1);
        retryMs = Math.min(MAX_RETRY_MS, retryMs * 2);

        stopTimer(redial);
        redial = TIMER.schedule(Hub::dial, wait, TimeUnit.MILLISECONDS);
    }

    private static void startKeepalive(WebSocket open) {
        stopTimer(keepalive);

        keepalive = TIMER.scheduleWithFixedDelay(() -> {
            if (!connected || socket != open) return;

            long quiet = System.currentTimeMillis() - lastHeard;
            if (quiet > SILENT_LIMIT_MS) {
                VivetSpeedrun.LOGGER.info("Hub silent for {} ms, dialling again", quiet);
                open.abort();
                ended(open);
                return;
            }

            try {
                open.sendPing(ByteBuffer.allocate(0));
            } catch (Exception e) {
                VivetSpeedrun.LOGGER.debug("Keepalive failed", e);
            }
        }, PING_INTERVAL_MS, PING_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    public static void invite(String uuid, String room) {
        String id = Profiles.normalise(uuid);
        if (id == null || room == null || room.isBlank()) return;

        java.util.Map<String, Object> frame = new java.util.HashMap<>();
        frame.put("op", "invite");
        frame.put("uuid", id);
        frame.put("room", room);

        invitedOut.add(id);
        stamp = System.currentTimeMillis();

        send(GSON.toJson(frame));
    }

    public static void nothingPressed(int count) {
        if (count <= 0) return;

        java.util.Map<String, Object> frame = new java.util.HashMap<>();
        frame.put("op", "nothing");
        frame.put("count", count);
        send(GSON.toJson(frame));
    }

    private static synchronized void send(String text) {
        WebSocket open = socket;
        if (open == null || !connected) return;

        try {
            open.sendText(text, true);
        } catch (Exception e) {
            VivetSpeedrun.LOGGER.debug("Could not send message", e);
        }
    }

    private static void take(Frame frame) {
        if (frame == null || frame.op == null) return;

        switch (frame.op) {
            case "hello" -> {
                here.clear();
                if (frame.online != null) {
                    for (String uuid : frame.online) {
                        String id = Profiles.normalise(uuid);
                        if (id != null) here.add(id);
                    }
                }

                connected = true;
                retryMs = FIRST_RETRY_MS;

                if (frame.invites != null) {
                    synchronized (invites) {
                        invites.clear();
                        for (InviteFrame held : frame.invites) {
                            String id = held == null ? null : Profiles.normalise(held.uuid);
                            if (id == null || held.room == null || held.room.isBlank()) continue;
                            invites.addLast(new Invite(id, held.name == null ? "?" : held.name,
                                    held.label == null ? "" : held.label, held.room,
                                    held.at == null ? System.currentTimeMillis() : held.at));
                        }
                    }
                }
                if (frame.invitesOut != null) replaceInvitedOut(frame.invitesOut);

                replaceWatchable(frame.watchable);
                staff = Boolean.TRUE.equals(frame.staff);

                if (frame.coins != null) MojangAuth.noteCoins(frame.coins.balance);

                if (frame.achievements != null) Achievements.accept(frame.achievements);

                if (frame.quests != null) Quests.accept(frame.quests);

                DiscordLink.accept(frame.discord, true);

                Social.stale();
                Social.refresh();
                Inbox.stale();
                Inbox.refresh();
                Chat.stale();
                Chat.refresh();
            }

            case "presence" -> {
                String id = Profiles.normalise(frame.uuid);
                if (id == null) return;

                if (Boolean.TRUE.equals(frame.on)) {
                    if (here.add(id)) ONLINE.accept(id, nameOf(id));
                } else {
                    here.remove(id);
                }
            }

            case "social" -> {
                if (frame.book != null && frame.book.error == null) Social.take(frame.book);

                if (frame.online != null) {
                    here.clear();
                    for (String uuid : frame.online) {
                        String id = Profiles.normalise(uuid);
                        if (id != null) here.add(id);
                    }
                }
                if (frame.watchable != null) replaceWatchable(frame.watchable);
            }

            case "watchable" -> {
                String id = Profiles.normalise(frame.uuid);
                if (id == null) return;
                if (Boolean.TRUE.equals(frame.on)) watchable.add(id);
                else watchable.remove(id);
            }

            case "inbox" -> {
                if (frame.inbox != null && frame.inbox.error == null) Inbox.take(frame.inbox);
            }

            case "chat" -> {
                Chat.take(frame.uuid, frame.name, frame.body,
                        frame.at == null ? System.currentTimeMillis() : frame.at);
            }

            case "invited" -> {
                String id = Profiles.normalise(frame.uuid);
                if (id == null || frame.room == null || frame.room.isBlank()) return;

                Invite invite = new Invite(id, frame.name == null ? "?" : frame.name,
                        frame.label == null ? "" : frame.label, frame.room,
                        System.currentTimeMillis());

                synchronized (invites) {
                    invites.removeIf(held -> held.uuid().equals(id));

                    while (invites.size() >= 8) invites.pollFirst();

                    invites.addLast(invite);
                }
                SINK.accept(invite);
            }

            case "invite_gone" -> {
                String id = Profiles.normalise(frame.uuid);
                if (id == null) return;

                synchronized (invites) {
                    invites.removeIf(held -> held.uuid().equals(id));
                }
                GONE.accept(id);
            }

            case "invites_out" -> replaceInvitedOut(frame.uuids);

            case "invite_refused" -> {
                String id = Profiles.normalise(frame.uuid);
                if (id == null) return;
                REFUSED.accept(id, frame.name == null ? "" : frame.name);
            }

            case "standing" -> PostMatch.accept(frame.profile, frame.delta);
            case "xp" -> {
                if (frame.xp != null) {
                    PostMatch.acceptXp(frame.xp.total, frame.xp.level, frame.xp.into, frame.xp.need,
                            frame.xp.gained);
                }
            }
            case "coins" -> PostMatch.acceptCoins(frame.coins);

            case "achievements" -> Achievements.accept(frame.achievements);

            case "quests" -> Quests.accept(frame.quests);

            case "discord" -> DiscordLink.accept(frame.discord, false);
            case "hosted_void" -> hostedVoided = true;

            case "hosted_queue" -> HostedQueue.place(
                    frame.position == null ? 0 : frame.position, frame.error);
            case "hosted_ready" -> {
                if (frame.host != null && frame.port != null && frame.port > 0) {
                    HostedQueue.turn(frame.match, frame.host, frame.port);
                }
            }

            case "hmatch" -> com.vivet.speedrun.session.Session.receiveHostedFrame(frame.match,
                    frame.frame);
            case "hmatch_ready" -> {
                if (frame.host != null && frame.port != null && frame.port > 0) {
                    RankedApi.HostedTicket ready = new RankedApi.HostedTicket();
                    ready.match = frame.match;
                    ready.host = frame.host;
                    ready.port = frame.port;
                    ready.band = frame.band;
                    ready.kind = Boolean.TRUE.equals(frame.ranked) ? "ranked" : "public";
                    ready.ranked = frame.ranked;
                    ready.opponent = frame.opponent;
                    ready.opponentUuid = frame.opponentUuid;
                    rematchReady = ready;
                }
            }

            case "hmatch_cancel" -> {
                if (Boolean.TRUE.equals(frame.rematch)) {
                    rematchRefused = frame.match == null ? "" : frame.match;
                } else {
                    matchCalledOff = frame.match == null ? "" : frame.match;
                }
            }

            default -> {
                return;
            }
        }

        stamp = System.currentTimeMillis();
    }

    private static void replaceWatchable(String[] uuids) {
        watchable.clear();
        if (uuids == null) return;
        for (String uuid : uuids) {
            String id = Profiles.normalise(uuid);
            if (id != null) watchable.add(id);
        }
    }

    private static void replaceInvitedOut(String[] uuids) {
        invitedOut.clear();
        if (uuids == null) return;
        for (String uuid : uuids) {
            String id = Profiles.normalise(uuid);
            if (id != null) invitedOut.add(id);
        }
    }

    private static volatile java.util.function.Consumer<Invite> SINK = invite -> { };

    public static void bindInvites(java.util.function.Consumer<Invite> sink) {
        if (sink != null) SINK = sink;
    }

    private static volatile java.util.function.BiConsumer<String, String> ONLINE = (id, name) -> { };

    public static void bindOnline(java.util.function.BiConsumer<String, String> sink) {
        if (sink != null) ONLINE = sink;
    }

    private static String nameOf(String uuid) {
        for (Social.Person person : Social.friends()) {
            if (person != null && uuid.equals(Profiles.normalise(person.uuid))) {
                return person.name == null ? "" : person.name;
            }
        }
        return "";
    }

    public static String labelOf(String uuid) {
        String id = Profiles.normalise(uuid);
        if (id == null) return "";

        for (Social.Person person : Social.friends()) {
            if (person != null && id.equals(Profiles.normalise(person.uuid))) {
                return person.label == null ? "" : person.label;
            }
        }
        return "";
    }

    private static final class Adapter implements WebSocket.Listener {
        @Override
        public void onOpen(WebSocket webSocket) {
            socket = webSocket;
            connected = true;
            lastHeard = System.currentTimeMillis();
            partial.setLength(0);
            startKeepalive(webSocket);
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
            if (webSocket == socket) lastHeard = System.currentTimeMillis();
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
            if (webSocket == socket) lastHeard = System.currentTimeMillis();
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            webSocket.request(1);
            if (webSocket == socket) lastHeard = System.currentTimeMillis();

            partial.append(data);
            if (!last) return null;

            String line = partial.toString();
            partial.setLength(0);

            try {
                take(GSON.fromJson(line, Frame.class));
            } catch (Exception e) {
                VivetSpeedrun.LOGGER.debug("Bad message from server", e);
            }
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int status, String reason) {
            if (status == DISPLACED) {
                wanted = false;
                displaced = true;
            }

            if (status == SIGNED_OUT) wanted = false;

            if (status == THROTTLED) retryMs = MAX_RETRY_MS / 2;

            ended(webSocket);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            VivetSpeedrun.LOGGER.debug("Connection error", error);
            ended(webSocket);
        }
    }

    private static synchronized void ended(WebSocket which) {
        if (which != socket) return;

        connected = false;
        socket = null;

        stopTimer(keepalive);
        keepalive = null;

        here.clear();
        watchable.clear();
        stamp = System.currentTimeMillis();

        again();
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
