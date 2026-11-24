package com.vivet.speedrun.session;

import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.game.ItemTiers;
import com.vivet.speedrun.config.ConfigManager;
import com.vivet.speedrun.p2p.RelayConnection;

import java.util.function.Consumer;

public class SessionGuest {
    private final Consumer<SessionMessage> onMessage;
    private final Runnable onDisconnected;

    private RelayConnection connection;
    private volatile boolean inRoom;

    public SessionGuest(Consumer<SessionMessage> onMessage, Runnable onDisconnected) {
        this.onMessage = onMessage;
        this.onDisconnected = onDisconnected;
    }

    public void connect(String roomCode, String playerName, Runnable onReachedRelay,
                        Consumer<String> onError) {
        this.connection = new RelayConnection(new Handler(playerName, onReachedRelay));
        this.connection.connect(
                RelayConnection.joinUri(ConfigManager.get().relayUrl, roomCode), onError);
    }

    public void send(SessionMessage message) {
        if (this.connection == null) return;
        this.connection.send(message.encode(), null);
    }

    public void stop() {
        this.inRoom = false;

        if (this.connection != null) {
            this.connection.close();
            this.connection = null;
        }
    }

    private static SessionMessage verdictOf(RelayConnection.Frame frame) {
        return SessionMessage.fromVerdict(frame.winner, frame.seconds, frame.reason,
                frame.walkover, frame.voided, frame.conceded, frame.matchId);
    }

    private final class Handler implements RelayConnection.Listener {
        private final String playerName;
        private final Runnable onReachedRelay;

        Handler(String playerName, Runnable onReachedRelay) {
            this.playerName = playerName;
            this.onReachedRelay = onReachedRelay;
        }

        private void announce() {
            SessionMessage join = SessionMessage.of(SessionMessage.Type.JOIN, this.playerName);
            join.protocol = VivetSpeedrun.PROTOCOL;
            join.ratings = ItemTiers.dataHash();
            SessionGuest.this.connection.send(join.encode(), RelayConnection.HOST_PEER);
        }

        @Override
        public void onFrame(RelayConnection.Frame frame) {
            switch (frame.op == null ? "" : frame.op) {
                case "joined" -> {
                    SessionGuest.this.inRoom = true;
                    VivetSpeedrun.LOGGER.info("Joined room {} as player {}",
                            frame.room, frame.id);
                    this.onReachedRelay.run();

                    announce();
                }
                case "msg" -> {
                    SessionMessage message = SessionMessage.decode(frame.data);
                    if (message == null) return;

                    if (message.isRelayOnly()) {
                        VivetSpeedrun.LOGGER.warn("Ignored {} from a player, only the server decides results", message.type);
                        return;
                    }

                    int from = frame.from == null ? -1 : frame.from;
                    if (message.isHostOnly() && from != RelayConnection.HOST_PEER) {
                        VivetSpeedrun.LOGGER.warn("Ignored {} from player {}, host only",
                                message.type, from);
                        return;
                    }

                    SessionGuest.this.onMessage.accept(message);
                }
                case "verdict" -> SessionGuest.this.onMessage.accept(verdictOf(frame));
                case "labels" -> SessionGuest.this.onMessage.accept(
                        SessionMessage.labels(frame.labels, frame.uuids,
                                RelayConnection.slotsOf(frame.looks)));
                case "away" -> SessionGuest.this.onMessage.accept(
                        SessionMessage.presence(SessionMessage.Type.AWAY, frame.name,
                                frame.until, frame.seconds));
                case "back" -> SessionGuest.this.onMessage.accept(
                        SessionMessage.presence(SessionMessage.Type.BACK, frame.name,
                                null, null));
                case "forfeited" -> SessionGuest.this.onMessage.accept(
                        SessionMessage.presence(SessionMessage.Type.FORFEITED,
                                frame.name, null, null));
                case "refused" -> {
                    SessionMessage refused =
                            new SessionMessage(SessionMessage.Type.REFUSED);
                    refused.text = frame.reason;
                    SessionGuest.this.onMessage.accept(refused);
                }
                case "error" -> {
                    SessionMessage reject = new SessionMessage(SessionMessage.Type.REJECT);
                    reject.text = frame.reason == null
                            ? "That code didn't work." : frame.reason;
                    SessionGuest.this.onMessage.accept(reject);
                }
                default -> VivetSpeedrun.LOGGER.debug("Ignored unknown message {}",
                        frame.op);
            }
        }

        @Override
        public void onClosed(String reason) {
            VivetSpeedrun.LOGGER.info("Disconnected: {}", reason);

            SessionGuest.this.inRoom = false;
            SessionGuest.this.onDisconnected.run();
        }
    }
}
