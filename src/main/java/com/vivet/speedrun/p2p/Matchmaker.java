package com.vivet.speedrun.p2p;

import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.config.ConfigManager;
import com.vivet.speedrun.game.Difficulty;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Matchmaker {
    private static volatile boolean casualClosed;

    public static boolean casualClosed() {
        return casualClosed;
    }

    public interface Listener {
        void onCounts(Map<String, Integer> casual, Map<String, Integer> ranked);

        void onHostedMatched(Difficulty band, RankedApi.HostedTicket ticket);

        void onFailed(String reason);

        default void onBanned(long until, String reason) {
        }

        default void onRefused(String reason) {
        }

        default void onLine(int position) {
        }
    }

    private static final class Request {
        String op;
        String band;
        String mode;

        Request(String op, String band, String mode) {
            this.op = op;
            this.band = band;
            this.mode = mode;
        }
    }

    private final Listener listener;

    private RelayConnection connection;

    private volatile boolean greeted;

    private volatile boolean finished;

    public Matchmaker(Listener listener) {
        this.listener = listener;
    }

    public void connect() {
        String relay = ConfigManager.get().relayUrl;

        this.connection = new RelayConnection(new Handler());
        this.connection.connect(
                RelayConnection.matchmakeUri(relay, VivetSpeedrun.PROTOCOL, MojangAuth.token()),
                this::fail);
    }

    public void queue(Difficulty band) {
        queue(band, false);
    }

    public void queue(Difficulty band, boolean ranked) {
        if (this.connection == null) return;
        this.connection.sendFrame(new Request("queue", band.id(), ranked ? "ranked" : "casual"));
    }

    public void unqueue() {
        if (this.connection == null) return;
        this.connection.sendFrame(new Request("unqueue", null, null));
    }

    public void close() {
        this.finished = true;

        if (this.connection != null) {
            this.connection.close();
            this.connection = null;
        }
    }

    private void fail(String reason) {
        if (this.finished) return;

        this.finished = true;
        this.listener.onFailed(reason);
    }

    private final class Handler implements RelayConnection.Listener {
        @Override
        public void onFrame(RelayConnection.Frame frame) {
            switch (frame.op == null ? "" : frame.op) {
                case "lobby" -> {
                    Matchmaker.this.greeted = true;
                    VivetSpeedrun.LOGGER.info("Joined matchmaking");
                    publish(frame);
                }
                case "counts" -> publish(frame);
                case "matched" -> {
                    if (frame.hosted != null && frame.hosted.host != null
                            && frame.hosted.port != null && frame.hosted.port > 0) {
                        Matchmaker.this.finished = true;

                        RankedApi.HostedTicket ticket = new RankedApi.HostedTicket();
                        ticket.match = frame.hosted.match;
                        ticket.host = frame.hosted.host;
                        ticket.port = frame.hosted.port;
                        ticket.band = frame.band;
                        ticket.ranked = frame.hosted.ranked;
                        ticket.kind = Boolean.TRUE.equals(frame.hosted.ranked) ? "ranked" : "public";
                        ticket.opponent = frame.hosted.opponent;
                        ticket.opponentUuid = frame.hosted.opponentUuid;

                        VivetSpeedrun.LOGGER.info("Match found on {}",
                                ticket.kind);
                        Matchmaker.this.listener.onHostedMatched(Difficulty.byId(frame.band), ticket);
                    }
                }
                case "line" -> Matchmaker.this.listener.onLine(
                        frame.position == null ? 0 : Math.max(0, frame.position));
                case "banned" -> Matchmaker.this.listener.onBanned(
                        frame.until == null ? 0L : frame.until,
                        frame.reason == null
                                ? "You're banned from Ranked for now." : frame.reason);
                case "error" -> {
                    String reason = frame.reason == null
                            ? "Couldn't join the queue." : frame.reason;
                    if (Matchmaker.this.greeted) {
                        Matchmaker.this.listener.onRefused(reason);
                    } else {
                        fail(reason);
                    }
                }
                default -> VivetSpeedrun.LOGGER.debug("Unknown matchmaking message {}",
                        frame.op);
            }
        }

        private void publish(RelayConnection.Frame frame) {
            casualClosed = Boolean.TRUE.equals(frame.casualClosed);
            Matchmaker.this.listener.onCounts(
                    tally(frame.counts), tally(frame.rankedCounts));
        }

        private Map<String, Integer> tally(Map<String, Integer> reported) {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (Difficulty band : Difficulty.values()) {
                Integer waiting = reported == null ? null : reported.get(band.id());
                counts.put(band.id(), waiting == null ? 0 : waiting);
            }
            return counts;
        }

        @Override
        public void onClosed(String reason) {
            VivetSpeedrun.LOGGER.info("Matchmaking closed: {}", reason);

            fail(reason);
        }
    }
}
