package com.vivet.speedrun.session;

import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.config.ConfigManager;
import com.vivet.speedrun.game.ItemTiers;
import com.vivet.speedrun.p2p.RelayConnection;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class SessionHost {
    private final Consumer<SessionMessage> onMessage;
    private final Runnable onRosterChanged;

    private final Map<Integer, String> guestNames = new ConcurrentHashMap<>();

    private final java.util.Set<String> kicked = ConcurrentHashMap.newKeySet();

    private RelayConnection connection;

    private Consumer<String> onRoomOpened = code -> { };
    private Consumer<String> onFailed = reason -> { };

    private volatile String roomCode = "";

    public SessionHost(Consumer<SessionMessage> onMessage, Runnable onRosterChanged) {
        this.onMessage = onMessage;
        this.onRosterChanged = onRosterChanged;
    }

    public void start(Consumer<String> onRoomOpened, Consumer<String> onFailed) {
        this.onRoomOpened = onRoomOpened;
        this.onFailed = onFailed;

        this.connection = new RelayConnection(new Handler());
        this.connection.connect(RelayConnection.hostUri(ConfigManager.get().relayUrl), onFailed);
    }

    public void broadcast(SessionMessage message) {
        if (this.connection != null) this.connection.send(message.encode(), null);
    }

    private void sendTo(int peer, SessionMessage message) {
        if (this.connection != null) this.connection.send(message.encode(), peer);
    }

    public String roomCode() {
        return this.roomCode;
    }

    public List<String> guestNames() {
        return List.copyOf(this.guestNames.values());
    }

    public void stop() {
        this.guestNames.clear();
        this.kicked.clear();
        this.roomCode = "";

        if (this.connection != null) {
            this.connection.close();
            this.connection = null;
        }
    }

    private void admit(int peer, SessionMessage join) {
        String name = join.name == null ? "?" : join.name.trim();

        int theirs = join.protocol == null ? 0 : join.protocol;
        if (theirs != VivetSpeedrun.PROTOCOL) {
            refuse(peer, name, theirs < VivetSpeedrun.PROTOCOL
                    ? "Your Vivet Speedrun is out of date. Update and restart."
                    : "The host needs to update Vivet Speedrun.");
            return;
        }

        String ratings = join.ratings == null ? "none" : join.ratings;
        if (!ratings.equals(ItemTiers.dataHash())) {
            refuse(peer, name, "You and the host are on different versions. You both need the same version.");
            return;
        }

        if (this.kicked.contains(key(name))) {
            refuse(peer, name, "You were kicked from the lobby.");
            return;
        }

        if (ConfigManager.get().privateFriendsOnly
                && !com.vivet.speedrun.p2p.Social.isFriendNamed(name)) {
            refuse(peer, name, "This lobby is friends only.");
            return;
        }

        Integer previous = null;
        for (Map.Entry<Integer, String> entry : this.guestNames.entrySet()) {
            if (name.equals(entry.getValue())) {
                previous = entry.getKey();
                break;
            }
        }

        if (previous != null) {
            this.guestNames.remove(previous);
            this.guestNames.put(peer, name);

            VivetSpeedrun.LOGGER.info("'{}' rejoined as player {} (was {})", name, peer, previous);

            sendTo(peer, welcomeFor());
            return;
        }

        this.guestNames.put(peer, name);
        VivetSpeedrun.LOGGER.info("'{}' joined the room", name);

        sendTo(peer, welcomeFor());

        this.onMessage.accept(join);
        this.onRosterChanged.run();
    }

    private SessionMessage welcomeFor() {
        SessionMessage welcome = new SessionMessage(SessionMessage.Type.WELCOME);
        welcome.protocol = VivetSpeedrun.PROTOCOL;
        welcome.ratings = ItemTiers.dataHash();

        List<String> roster = new java.util.ArrayList<>();
        roster.add(Session.localName());
        roster.addAll(this.guestNames.values());
        welcome.players = roster.toArray(new String[0]);
        return welcome;
    }

    private void refuse(int peer, String name, String reason) {
        SessionMessage reject = new SessionMessage(SessionMessage.Type.REJECT);
        reject.text = reason;
        sendTo(peer, reject);

        VivetSpeedrun.LOGGER.info("Refused '{}': {}", name, reason);
    }

    public boolean kick(String name) {
        if (name == null || name.isBlank()) return false;

        Integer peer = null;
        for (Map.Entry<Integer, String> entry : this.guestNames.entrySet()) {
            if (name.equalsIgnoreCase(entry.getValue())) {
                peer = entry.getKey();
                break;
            }
        }
        if (peer == null) return false;

        String actual = this.guestNames.remove(peer);
        this.kicked.add(key(actual));

        refuse(peer, actual, "You were kicked from the lobby.");

        SessionMessage left = SessionMessage.of(SessionMessage.Type.FORFEIT, actual);
        left.text = "removed";
        this.onMessage.accept(left);
        this.onRosterChanged.run();

        VivetSpeedrun.LOGGER.info("Kicked '{}' from the room", actual);
        return true;
    }

    private static String key(String name) {
        return name.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private void drop(int peer) {
        String name = this.guestNames.remove(peer);
        if (name == null) return;

        SessionMessage left = SessionMessage.of(SessionMessage.Type.FORFEIT, name);
        left.text = "disconnected";
        this.onMessage.accept(left);
        this.onRosterChanged.run();
    }

    private final class Handler implements RelayConnection.Listener {
        @Override
        public void onFrame(RelayConnection.Frame frame) {
            switch (frame.op == null ? "" : frame.op) {
                case "hosted" -> {
                    SessionHost.this.roomCode = frame.room == null ? "" : frame.room;
                    VivetSpeedrun.LOGGER.info("Hosting room {}",
                            SessionHost.this.roomCode);
                    SessionHost.this.onRoomOpened.accept(SessionHost.this.roomCode);
                }
                case "peer" -> {
                    if ("left".equals(frame.state) && frame.id != null) drop(frame.id);
                }
                case "verdict" -> SessionHost.this.onMessage.accept(
                        SessionMessage.fromVerdict(frame.winner, frame.seconds, frame.reason,
                                frame.walkover, frame.voided, frame.conceded, frame.matchId));
                case "labels" -> SessionHost.this.onMessage.accept(
                        SessionMessage.labels(frame.labels, frame.uuids,
                                RelayConnection.slotsOf(frame.looks)));
                case "away" -> SessionHost.this.onMessage.accept(
                        SessionMessage.presence(SessionMessage.Type.AWAY, frame.name,
                                frame.until, frame.seconds));
                case "back" -> SessionHost.this.onMessage.accept(
                        SessionMessage.presence(SessionMessage.Type.BACK, frame.name,
                                null, null));
                case "forfeited" -> SessionHost.this.onMessage.accept(
                        SessionMessage.presence(SessionMessage.Type.FORFEITED,
                                frame.name, null, null));
                case "msg" -> onGameMessage(frame);
                case "refused" -> {
                    SessionMessage refused =
                            new SessionMessage(SessionMessage.Type.REFUSED);
                    refused.text = frame.reason;
                    SessionHost.this.onMessage.accept(refused);
                }
                case "error" -> {
                    String reason = frame.reason == null ? "Couldn't open the lobby." : frame.reason;
                    SessionHost.this.onFailed.accept(reason);
                }
                default -> VivetSpeedrun.LOGGER.debug("Ignored unknown message {}",
                        frame.op);
            }
        }

        private void onGameMessage(RelayConnection.Frame frame) {
            SessionMessage message = SessionMessage.decode(frame.data);
            if (message == null) return;

            int peer = frame.from == null ? -1 : frame.from;
            if (message.type == SessionMessage.Type.JOIN) {
                admit(peer, message);
                return;
            }

            if (message.isRelayOnly() || message.isHostOnly()) {
                VivetSpeedrun.LOGGER.warn("Ignored {} from guest {}, host only",
                        message.type, peer);
                return;
            }

            String claimed = SessionHost.this.guestNames.get(peer);
            if (claimed == null) {
                VivetSpeedrun.LOGGER.warn("Ignored {} from player {}, not in the room",
                        message.type, peer);
                return;
            }
            if (message.name != null && !claimed.equals(message.name)) {
                VivetSpeedrun.LOGGER.warn("Player {} sent {} as '{}' but joined as '{}', fixed",
                        peer, message.type, message.name, claimed);
                message.name = claimed;
            }

            SessionHost.this.onMessage.accept(message);
        }

        @Override
        public void onClosed(String reason) {
            VivetSpeedrun.LOGGER.info("Disconnected: {}", reason);
            SessionHost.this.onFailed.accept(reason);
        }
    }
}
