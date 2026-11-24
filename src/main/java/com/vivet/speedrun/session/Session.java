package com.vivet.speedrun.session;

import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.config.ConfigManager;
import com.vivet.speedrun.game.Difficulty;
import com.vivet.speedrun.game.DimensionPool;
import com.vivet.speedrun.game.Target;
import com.vivet.speedrun.game.ItemTiers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public final class Session {
    public enum Role {
        NONE,
        HOST,
        GUEST,

        HOSTED,
    }

    public enum Phase {
        LOBBY,

        READY_CHECK,

        LOADING,

        RACING,

        FINISHED,
    }

    public static final class Vote {
        public final SessionMessage.VoteKind kind;
        public final String proposer;

        public final long id;

        public final Map<String, Boolean> responses = new LinkedHashMap<>();

        Vote(SessionMessage.VoteKind kind, String proposer, long id) {
            this.kind = kind;
            this.proposer = proposer;
            this.id = id;
        }

        public String title() {
            return this.kind == SessionMessage.VoteKind.REROLL
                    ? "Reroll the item?"
                    : "Draw?";
        }
    }

    public static final int MAX_RACERS = 16;

    private static final long READY_TIMEOUT_MS = 240_000L;

    private static final long GO_TIMEOUT_MS = READY_TIMEOUT_MS + 30_000L;

    private static final long JOIN_TIMEOUT_MS = 15_000L;

    private static final Queue<SessionMessage> inbox = new ConcurrentLinkedQueue<>();
    private static final Map<String, Racer> racers = new LinkedHashMap<>();

    private static final List<String> feed = new ArrayList<>();

    private static Role role = Role.NONE;
    private static Phase phase = Phase.LOBBY;
    private static SessionHost host;
    private static SessionGuest guest;
    private static Vote openVote;
    private static String localName = "You";

    public enum Tone {
        NEUTRAL,
        GOOD,
        BAD,

        CHAT,
    }

    private static java.util.function.BiConsumer<String, Tone> chatSink = (message, tone) -> { };

    public interface NamedChat {
        void accept(String name, String labelId, String suffix, Tone tone);
    }

    private static NamedChat namedChatSink = null;

    public enum Cue {
        RIVAL_MOVED,

        OPPONENT_LEFT,

        OPPONENT_RETURNED,
    }

    private static Consumer<Cue> cueSink = cue -> { };

    private static final Map<String, String> labels = new LinkedHashMap<>();

    private static final Map<String, String> accountIds = new LinkedHashMap<>();

    private static final Map<String, String> lastAccountIds = new LinkedHashMap<>();

    private static final Map<String, Map<String, String>> looks = new LinkedHashMap<>();

    private static String awayName = "";

    private static long awayUntil;

    public record Rejoinable(String roomCode, Difficulty band, DimensionPool dimension,
                             boolean includeEasier, long salt,
                             net.minecraft.world.Difficulty worldDifficulty) {
    }

    private static Rejoinable rejoinable;

    private static Consumer<SessionMessage> onStart = message -> { };

    private static java.util.function.IntConsumer onReroll = index -> { };

    private static Consumer<String> onEnd = reason -> { };

    private static Consumer<Vote> onVoteOpened = vote -> { };

    private static Runnable onVoteClosed = () -> { };

    private static Runnable onDraw = () -> { };

    private static Runnable onGo = () -> { };

    private static Runnable onReadyCheck = () -> { };

    private static Runnable onReadyCheckDone = () -> { };

    private static Runnable onHostQuit = () -> { };

    private static volatile boolean graceOffered;

    private static volatile int carryOnSeconds;

    private static volatile long carryOnUntil;

    private static Consumer<Difficulty> onGraceOffered = band -> { };

    private static java.util.function.BiConsumer<String, Boolean> onPublicMatchOver =
            (reason, won) -> { };

    private static long readyDeadline;

    private static long goDeadline;

    private static boolean playToLastFinisher;

    private static int matchSize;

    private static String matchWinner = "";

    private static boolean publicMatch;

    private static Difficulty publicBand = Difficulty.MEDIUM;

    private static boolean ranked;

    private static boolean lastRanked;

    private static boolean rankedSolo;

    private static boolean lastRankedSolo;

    private static boolean setSeedRun;
    private static boolean lastSetSeedRun;

    private static boolean hosted;

    private static boolean lastHosted;

    private static volatile String hostedMatch = "";

    private static Consumer<SessionMessage> lineSink = message -> { };

    private static String lastWinner = "";
    private static int lastWinnerSeconds;
    private static boolean lastPublic;

    private static boolean lastPrivate;
    private static boolean lastMultiplayer;
    private static int lastMatchSize;
    private static String lastEndReason = "";

    private static boolean lastVoided;

    private static boolean lastConceded;

    private static boolean lastDrawn;

    private static final List<Racer> lastStandings = new ArrayList<>();

    private static boolean matchDecided;

    private static String lastMatchId = "";

    public static String lastMatchId() {
        return lastMatchId;
    }

    private static final java.util.Set<String> rematchWanted = new java.util.LinkedHashSet<>();

    private static boolean publicOver;

    private static boolean started;

    private static long matchSalt;
    private static Difficulty matchBand = Difficulty.MEDIUM;
    private static boolean matchIncludeEasier;
    private static DimensionPool matchDimension = DimensionPool.ANY;

    private static Long matchSeed;

    private static net.minecraft.world.Difficulty matchWorldDifficulty =
            net.minecraft.world.Difficulty.NORMAL;

    private static int matchRerolls;

    public static final int PUBLIC_REROLL_LIMIT = 5;

    private static int rerollsSpent;

    private static long voteSequence;

    private static long nextStartAllowedAt;

    private static final long START_COOLDOWN_MS = 5_000L;

    private static long joinDeadline;

    private static Consumer<String> statusSink = message -> { };

    private static Runnable pendingConnected;
    private static Consumer<String> pendingError;

    private Session() {
    }

    public static void bind(java.util.function.BiConsumer<String, Tone> chat,
                           Consumer<SessionMessage> start,
                           java.util.function.IntConsumer reroll, Consumer<String> end) {
        chatSink = chat;
        onStart = start;
        onReroll = reroll;
        onEnd = end;
    }

    public static void bindClientEffects(NamedChat chat, Consumer<Cue> cues) {
        namedChatSink = chat;
        cueSink = cues;
    }

    public static Rejoinable rejoinable() {
        return rejoinable;
    }

    public static long rejoinWindowMs() {
        return 20_000L;
    }

    public static void forgetRejoin() {
        rejoinable = null;
        forgetRejoinSink.run();
    }

    public static void adoptRejoin(Rejoinable back) {
        rejoinable = back;
    }

    public static void bindForgetRejoin(Runnable sink) {
        forgetRejoinSink = sink == null ? () -> { } : sink;
    }

    private static Runnable forgetRejoinSink = () -> { };

    public static void rejoin(Runnable onConnected, Consumer<String> onError) {
        Rejoinable back = rejoinable;
        if (back == null) {
            onError.accept("No match to rejoin.");
            return;
        }

        join(back.roomCode(), onConnected, onError);

        matchDimension = back.dimension() == null ? DimensionPool.ANY : back.dimension();
        matchIncludeEasier = back.includeEasier();
        matchSalt = back.salt();
        matchWorldDifficulty = back.worldDifficulty() == null
                ? net.minecraft.world.Difficulty.NORMAL : back.worldDifficulty();
        if (back.band() != null) matchBand = back.band();

        phase = Phase.RACING;

        com.vivet.speedrun.game.MatchRules.set(worldRules());
    }

    public static String awayName() {
        return awayName;
    }

    public static int awaySecondsLeft() {
        if (awayName.isEmpty() || awayUntil == 0L) return -1;
        return (int) Math.max(0L, (awayUntil - System.currentTimeMillis()) / 1000L);
    }

    public static String labelIdOf(String name) {
        return name == null ? null : labels.get(name);
    }

    public static String accountIdOf(String name) {
        return name == null ? null : accountIds.get(name);
    }

    public static String lastAccountIdOf(String name) {
        if (name == null) return null;

        String frozen = lastAccountIds.get(name);
        return frozen != null ? frozen : accountIds.get(name);
    }

    public static String lookOf(String name, String slot) {
        if (name == null || slot == null) return null;

        Map<String, String> mine = looks.get(name);
        if (mine == null) return null;

        String worn = mine.get(slot);
        return worn == null ? "" : worn;
    }

    public static boolean lookKnown(String name) {
        return name != null && looks.containsKey(name);
    }

    public static String matchBackground() {
        return com.vivet.speedrun.p2p.Wardrobe.known()
                ? com.vivet.speedrun.p2p.Wardrobe.inSlot("loading_background") : "";
    }

    public static boolean matchBackgroundKnown() {
        return com.vivet.speedrun.p2p.Wardrobe.known();
    }

    static String plainText(String text) {
        return text.replaceAll("[\\p{Cntrl}\\u00A7]", " ");
    }

    private static void named(String name, String suffix, String plain, Tone tone) {
        if (namedChatSink == null) {
            chatSink.accept(plain, tone);
            return;
        }
        namedChatSink.accept(name, labelIdOf(name), suffix, tone);
    }

    public static void bindVotes(Consumer<Vote> voteOpened, Runnable voteClosed, Runnable draw) {
        onVoteOpened = voteOpened;
        onVoteClosed = voteClosed;
        onDraw = draw;
    }

    public static void bindGo(Runnable go) {
        onGo = go;
    }

    public static void bindReadyCheck(Runnable opened, Runnable closed) {
        onReadyCheck = opened;
        onReadyCheckDone = closed;
    }

    public static void bindHostQuit(Runnable hostQuit) {
        onHostQuit = hostQuit;
    }

    public static void bindPublicMatch(java.util.function.BiConsumer<String, Boolean> over) {
        onPublicMatchOver = over;
    }

    public static void bindGraceOffer(Consumer<Difficulty> onOffer) {
        onGraceOffered = onOffer;
    }

    public static void answerGrace(boolean keepPlaying) {
        if (!isActive() || !graceOffered) return;

        graceOffered = false;

        if (keepPlaying && carryOnSeconds > 0) {
            carryOnUntil = System.currentTimeMillis() + carryOnSeconds * 1000L;
        }

        SessionMessage message = SessionMessage.of(
                SessionMessage.Type.GRACE_ANSWER, localName);
        message.agree = keepPlaying;
        send(message);
    }

    public static int carryOnSeconds() {
        return carryOnSeconds;
    }

    public static boolean isCarryingOn() {
        return carryOnUntil > 0L && isActive();
    }

    public static long carryOnMillisLeft() {
        if (!isCarryingOn()) return 0L;
        return Math.max(0L, carryOnUntil - System.currentTimeMillis());
    }

    public static float carryOnShare() {
        if (!isCarryingOn() || carryOnSeconds <= 0) return 1.0F;
        return Math.min(1.0F, (float) carryOnMillisLeft() / (carryOnSeconds * 1000.0F));
    }

    private static void forgetCarryOn() {
        carryOnUntil = 0L;
        carryOnSeconds = 0;
    }

    public static void bindStatus(Consumer<String> status) {
        statusSink = status == null ? message -> { } : status;
    }

    public static void setLocalName(String name) {
        localName = name == null || name.isBlank() ? "You" : name;
    }

    public static String localName() {
        return localName;
    }

    public static Role role() {
        return role;
    }

    public static Phase phase() {
        return phase;
    }

    public static boolean canHostStart() {
        if (role != Role.HOST) return false;

        if (racers.size() <= 1) return false;

        if (phase == Phase.LOADING) return false;

        return phase != Phase.RACING;
    }

    public static com.vivet.speedrun.game.MatchRules.Policy worldRules() {
        if (ranked) return com.vivet.speedrun.game.MatchRules.Policy.forRanked();
        if (publicMatch) return com.vivet.speedrun.game.MatchRules.Policy.forPublicQueue();
        return com.vivet.speedrun.game.MatchRules.Policy.forPrivate(matchWorldDifficulty);
    }

    public static net.minecraft.world.Difficulty worldDifficulty() {
        return matchWorldDifficulty;
    }

    public static boolean isPublicMatch() {
        return publicMatch;
    }

    public static boolean isRanked() {
        return ranked;
    }

    public static boolean wasRanked() {
        return lastRanked;
    }

    public static boolean isSetSeed() {
        return setSeedRun;
    }

    public static boolean wasSetSeed() {
        return lastSetSeedRun;
    }

    public static boolean isRankedSolo() {
        return rankedSolo;
    }

    public static boolean wasRankedSolo() {
        return lastRankedSolo;
    }

    public static Difficulty publicBand() {
        return publicBand;
    }

    public static boolean wantsRematch() {
        return rematchWanted.contains(localName);
    }

    public static int rematchCount() {
        return rematchWanted.size();
    }

    public static int matchPlayerCount() {
        return Math.max(1, racers.size());
    }

    public static boolean canRematch() {
        return racers.size() > 1;
    }

    public static int startWaitSecondsLeft() {
        if (goDeadline == 0L || phase != Phase.LOADING) return -1;
        return (int) Math.max(0L, (goDeadline - System.currentTimeMillis()) / 1000L);
    }

    public static List<String> stillLoading() {
        if (!isActive() || phase != Phase.LOADING) return List.of();

        Racer you = racers.get(localName);
        if (you == null || !you.ready) return List.of();

        List<String> waiting = new ArrayList<>();
        for (Racer racer : racers.values()) {
            if (!racer.ready && !racer.isDone()) waiting.add(racer.name);
        }
        return waiting;
    }

    public static boolean canEditModifiers() {
        if (!isActive()) return true;

        if (role != Role.HOST) return false;

        return phase != Phase.LOADING && phase != Phase.RACING;
    }

    public static boolean canStartLocally() {
        return !isActive();
    }

    public static boolean isActive() {
        return role != Role.NONE;
    }

    public static boolean isMultiplayer() {
        return isActive() && racers.size() > 1;
    }

    public static boolean playsToLastFinisher() {
        return playToLastFinisher;
    }

    public static boolean localIsMatchWinner() {
        return !matchWinner.isEmpty() && matchWinner.equals(localName);
    }

    public static List<Racer> racers() {
        return new ArrayList<>(racers.values());
    }

    public static boolean isMatchDecided() {
        return matchDecided;
    }

    public static String lastWinner() {
        return lastWinner;
    }

    public static int lastWinnerSeconds() {
        return lastWinnerSeconds;
    }

    public static boolean lastWinnerWasLocal() {
        return !lastWinner.isEmpty() && lastWinner.equals(localName);
    }

    public static boolean wasPublic() {
        return lastPublic;
    }

    public static boolean wasPrivate() {
        return lastPrivate;
    }

    public static boolean wasMultiplayer() {
        return lastMultiplayer;
    }

    public static int lastMatchSize() {
        return lastMatchSize;
    }

    public static boolean wasVoided() {
        return lastVoided;
    }

    public static boolean wasConceded() {
        return lastConceded;
    }

    public static boolean wasDrawn() {
        return lastDrawn;
    }

    public static String lastEndReason() {
        return lastEndReason;
    }

    public static List<Racer> finalStandings() {
        if (!lastStandings.isEmpty()) return new ArrayList<>(lastStandings);
        return racers();
    }

    private static void rememberMatch(String winner, int winnerSeconds, String reason) {
        rememberMatch(winner, winnerSeconds, reason, false);
    }

    private static void rememberMatch(String winner, int winnerSeconds, String reason,
                                      boolean voided) {
        matchDecided = true;
        lastWinner = winner == null ? "" : winner;
        lastWinnerSeconds = winnerSeconds;
        lastEndReason = reason == null ? "" : reason;
        lastVoided = voided;
        lastConceded = false;
        lastDrawn = false;
        lastPublic = publicMatch;
        lastPrivate = isActive() && !publicMatch;
        lastMultiplayer = racers.size() > 1 || matchSize > 1;
        lastMatchSize = Math.max(matchSize, racers.size());

        lastStandings.clear();
        lastStandings.addAll(racers.values());

        lastAccountIds.clear();
        lastAccountIds.putAll(accountIds);
    }

    public static String rememberMissedMatch(String me, Difficulty band, boolean isRanked,
                                             String opponent, String opponentUuid, String winner,
                                             Integer seconds, String reason, boolean walkover,
                                             boolean voided, boolean conceded, String matchId) {
        String mine = me == null ? "" : me;
        String theirs = opponent == null ? "" : opponent;
        String won = winner == null ? "" : winner;
        int time = seconds == null ? 0 : Math.max(0, seconds);

        String line;
        if (voided || conceded) {
            line = reason == null || reason.isBlank() ? "No winner" : reason;
        } else if (!won.isEmpty() && !walkover) {
            line = won + " wins with a time of " + com.vivet.speedrun.game.Time.format(time);
        } else if (walkover && !won.isEmpty() && !won.equals(mine)) {
            line = "You left. Match lost";
        } else if (won.isEmpty()) {
            line = reason == null || reason.isBlank() || reason.startsWith("Race") || reason.startsWith("Match complete")
                    ? "No winner" : reason;
        } else {
            line = reason == null || reason.isBlank() ? "You win" : reason;
        }

        matchDecided = true;
        lastMatchId = matchId == null ? "" : matchId;
        lastWinner = won;
        lastWinnerSeconds = walkover ? 0 : time;
        lastEndReason = line;
        lastVoided = voided;
        lastConceded = conceded;
        lastPublic = true;
        lastPrivate = false;
        lastRanked = isRanked;
        lastRankedSolo = false;
        lastSetSeedRun = false;
        lastMultiplayer = true;
        lastMatchSize = 2;
        publicBand = band == null ? Difficulty.MEDIUM : band;

        lastStandings.clear();
        for (String name : theirs.isEmpty() ? List.of(mine) : List.of(mine, theirs)) {
            Racer racer = new Racer(name);
            if (!voided && !conceded && name.equals(won)) {
                racer.status = Racer.Status.FINISHED;
                racer.seconds = walkover ? 0 : time;
                racer.detail = walkover ? "last one standing" : "found it";
            } else {
                racer.status = Racer.Status.OUT;
                racer.detail = walkover && !name.equals(won) && !won.isEmpty() ? "left"
                        : "did not finish";
            }
            lastStandings.add(racer);
        }

        lastAccountIds.clear();
        if (!theirs.isEmpty() && opponentUuid != null && !opponentUuid.isBlank()) {
            lastAccountIds.put(theirs, opponentUuid);
        }
        return line;
    }

    public static void forgetLastMatch() {
        matchDecided = false;
        lastMatchId = "";
        lastWinner = "";
        lastWinnerSeconds = 0;
        lastEndReason = "";
        lastVoided = false;
        lastConceded = false;
        lastDrawn = false;
        lastPublic = false;
        lastPrivate = false;
        lastMultiplayer = false;
        lastMatchSize = 0;
        lastAccountIds.clear();
        lastRanked = false;
        lastRankedSolo = false;
        lastSetSeedRun = false;
        lastHosted = false;
        lastStandings.clear();
    }

    public static List<String> feed() {
        return feed;
    }

    public static Vote openVote() {
        return openVote;
    }

    public static void startHosting(Consumer<String> onRoomOpened, Consumer<String> onFailed) {
        stop();

        host = new SessionHost(inbox::add,
                () -> inbox.add(new SessionMessage(SessionMessage.Type.ROSTER_REFRESH)));

        role = Role.HOST;
        phase = Phase.LOBBY;
        racers.clear();
        racers.put(localName, new Racer(localName));
        feed.clear();

        joinedRoom = "";
        lastRanked = false;
        lastRankedSolo = false;

        host.start(onRoomOpened, reason -> {
            if (phase == Phase.LOADING || phase == Phase.RACING) {
                inbox.add(messageWithText(SessionMessage.Type.LINK_LOST,
                        "Connection lost."));
                return;
            }
            onFailed.accept(reason);
        });
    }

    public static void beginHostedSolo(boolean setSeed) {
        forgetLastMatch();

        ranked = true;
        lastRanked = true;
        rankedSolo = true;
        lastRankedSolo = true;
        setSeedRun = setSeed;
        lastSetSeedRun = setSeed;
        hosted = true;
        lastHosted = true;
    }

    public static boolean wasHosted() {
        return lastHosted;
    }

    public static void endHostedSolo() {
        if (!hosted || role == Role.HOSTED) return;
        hosted = false;
        ranked = false;
        rankedSolo = false;
        setSeedRun = false;
    }

    public static boolean isHosted() {
        return hosted;
    }

    public static boolean isHostedMatch() {
        return role == Role.HOSTED;
    }

    public static String hostedMatch() {
        return hostedMatch;
    }

    public static void bindLines(Consumer<SessionMessage> sink) {
        lineSink = sink == null ? message -> { } : sink;
    }

    public static void beginHostedMatch(String match, Difficulty band, boolean isRanked,
                                        String opponent, String opponentUuid, boolean resume) {
        stop();

        forgetLastMatch();

        role = Role.HOSTED;
        hostedMatch = match == null ? "" : match;
        hosted = true;
        lastHosted = true;

        matchDimension = DimensionPool.ANY;

        publicMatch = true;
        publicBand = band;
        matchBand = band;
        ranked = isRanked;
        lastRanked = isRanked;
        rankedSolo = false;
        lastRankedSolo = false;
        setSeedRun = false;
        lastSetSeedRun = false;

        racers.clear();
        racer(localName);
        if (opponent != null && !opponent.isBlank()) {
            racer(opponent);
            if (opponentUuid != null && !opponentUuid.isBlank()) accountIds.put(opponent, opponentUuid);
        }
        matchSize = racers.size();

        matchWinner = "";
        matchDecided = false;
        lastWinner = "";
        lastWinnerSeconds = 0;
        lastEndReason = "";
        lastVoided = false;
        lastStandings.clear();
        lastMatchId = "";
        rematchWanted.clear();
        graceOffered = false;
        forgetCarryOn();
        feed.clear();
        matchRerolls = 0;
        rerollsSpent = 0;
        ConfigManager.get().sessionRules = null;
        ConfigManager.get().sessionLevels = null;

        phase = resume ? Phase.RACING : Phase.LOADING;

        takeEarlyLabels();
    }

    public static void hostedRacing() {
        if (role == Role.HOSTED && phase == Phase.LOADING) phase = Phase.RACING;
    }

    private static volatile SessionMessage earlyLabels;
    private static volatile String earlyLabelsMatch = "";
    private static volatile long earlyLabelsAt;

    private static final long EARLY_LABELS_MS = 60_000L;

    private static volatile int labelsVersion;

    public static int labelsVersion() {
        return labelsVersion;
    }

    private static void takeEarlyLabels() {
        SessionMessage early = earlyLabels;
        String forMatch = earlyLabelsMatch;
        long at = earlyLabelsAt;
        earlyLabels = null;

        if (early == null || !hostedMatch.equals(forMatch)) return;
        if (System.currentTimeMillis() - at > EARLY_LABELS_MS) return;
        handle(early);
    }

    public static void receiveLine(String json) {
        if (role != Role.HOSTED) return;

        SessionMessage message = SessionMessage.decode(json);
        if (message == null || message.type == null) return;

        switch (message.type) {
            case EVENT, TIME, FINISH, FORFEIT, VOTE_OPEN, VOTE_RESULT, DRAW, REMATCH, ROSTER ->
                    inbox.add(message);
            default -> VivetSpeedrun.LOGGER.debug("Ignored {} from race server", message.type);
        }
    }

    public static void receiveHostedFrame(String match, com.vivet.speedrun.p2p.RelayConnection.Frame frame) {
        if (frame == null || frame.op == null) return;

        if (role != Role.HOSTED || match == null || !match.equals(hostedMatch)) {
            if ("labels".equals(frame.op) && match != null && !match.isEmpty()) {
                earlyLabels = SessionMessage.labels(frame.labels, frame.uuids,
                        com.vivet.speedrun.p2p.RelayConnection.slotsOf(frame.looks));
                earlyLabelsMatch = match;
                earlyLabelsAt = System.currentTimeMillis();
            }
            return;
        }

        SessionMessage message = switch (frame.op) {
            case "verdict" -> SessionMessage.fromVerdict(frame.winner, frame.seconds,
                    voidLine(frame, localName), frame.walkover, frame.voided, frame.conceded,
                    frame.matchId);
            case "labels" -> SessionMessage.labels(frame.labels, frame.uuids,
                    com.vivet.speedrun.p2p.RelayConnection.slotsOf(frame.looks));
            case "away" -> SessionMessage.presence(SessionMessage.Type.AWAY, frame.name,
                    frame.until, frame.seconds);
            case "back" -> SessionMessage.presence(SessionMessage.Type.BACK, frame.name, null, null);
            case "forfeited" -> SessionMessage.presence(SessionMessage.Type.FORFEITED,
                    frame.name, null, null);
            case "afk" -> SessionMessage.afk(frame.state, frame.name, frame.reason);
            case "grace" -> SessionMessage.grace(frame.band, frame.seconds, frame.name);
            case "carryon" -> SessionMessage.carryOn(frame.until, frame.seconds);
            case "refused" -> messageWithText(SessionMessage.Type.REFUSED, frame.reason);
            case "msg" -> {
                SessionMessage said = SessionMessage.decode(frame.data);
                yield said != null && said.type == SessionMessage.Type.CHAT ? said : null;
            }
            default -> null;
        };
        if (message != null) inbox.add(message);
    }

    public static String voidLine(com.vivet.speedrun.p2p.RelayConnection.Frame frame, String me) {
        if (frame == null) return null;
        if (!Boolean.TRUE.equals(frame.voided)) return frame.reason;
        if (frame.leaver != null && frame.leaver.equals(me)) return "You left early";
        return "Opponent left early";
    }

    public static String currentRoom() {
        String hosted = roomCode();
        if (!hosted.isEmpty()) return hosted;

        return joinedRoom == null ? "" : joinedRoom;
    }

    public static boolean kick(String name) {
        if (host == null || isLocalName(name)) return false;
        return host.kick(name);
    }

    public static String roomCode() {
        return host == null ? "" : host.roomCode();
    }

    private static String joinedRoom = "";

    public static void join(String roomCode, Runnable onConnected, Consumer<String> onError) {
        stop();

        pendingConnected = onConnected;
        pendingError = onError;
        joinDeadline = System.currentTimeMillis() + JOIN_TIMEOUT_MS;

        guest = new SessionGuest(inbox::add, () -> {
            inbox.add(messageWithText(
                    pendingConnected != null
                            ? SessionMessage.Type.HOST_QUIT
                            : SessionMessage.Type.LINK_LOST,
                    "Lost connection to the host."));
        });

        role = Role.GUEST;
        phase = Phase.LOBBY;
        racers.clear();
        feed.clear();

        joinedRoom = roomCode == null ? "" : roomCode;

        lastRanked = false;
        lastRankedSolo = false;

        guest.connect(roomCode, localName,
                () -> statusSink.accept("Joining..."),
                error -> inbox.add(messageWithText(SessionMessage.Type.REJECT, error)));
    }

    private static void failJoin(String reason) {
        Consumer<String> error = pendingError;
        pendingConnected = null;
        pendingError = null;

        stop();
        if (error != null) {
            error.accept(reason);
        } else {
            statusSink.accept(reason);
        }
    }

    public static void quit() {
        if (role == Role.HOST && host != null) {
            host.broadcast(messageWithText(SessionMessage.Type.HOST_QUIT,
                    "The host closed the lobby."));
        }

        if (phase == Phase.RACING && (role == Role.HOST || role == Role.GUEST)) {
            SessionMessage forfeit = SessionMessage.of(SessionMessage.Type.FORFEIT, localName);
            forfeit.text = "left the match";
            send(forfeit);
        }

        stop();

        forgetRejoin();
    }

    public static boolean holdsRejoin() {
        return role == Role.GUEST && isMultiplayer() && phase == Phase.RACING
                && !joinedRoom.isEmpty();
    }

    public static Rejoinable wouldRejoin() {
        if (!holdsRejoin()) return null;

        return new Rejoinable(joinedRoom, matchBand, matchDimension, matchIncludeEasier,
                matchSalt, matchWorldDifficulty);
    }

    public static void stop() {
        Rejoinable held = wouldRejoin();
        if (held != null) rejoinable = held;

        if (host != null) {
            com.vivet.speedrun.p2p.Hub.closeInvites();
            host.stop();
            host = null;
        }
        if (guest != null) {
            guest.stop();
            guest = null;
        }

        if (role == Role.HOSTED) {
            hosted = false;
            hostedMatch = "";
        }

        role = Role.NONE;
        phase = Phase.LOBBY;
        nextStartAllowedAt = 0L;

        readyDeadline = 0L;
        goDeadline = 0L;
        publicMatch = false;
        ranked = false;
        rankedSolo = false;
        setSeedRun = false;
        joinedRoom = "";
        matchSeed = null;
        publicOver = false;
        rematchWanted.clear();
        openVote = null;
        matchRerolls = 0;
        rerollsSpent = 0;
        voteSequence = 0L;
        started = false;
        playToLastFinisher = false;
        matchSize = 0;
        matchWinner = "";
        onReadyCheckDone.run();
        joinDeadline = 0L;
        pendingConnected = null;
        pendingError = null;
        ConfigManager.get().sessionRules = null;
        ConfigManager.get().sessionLevels = null;
        racers.clear();
        feed.clear();
        accountIds.clear();
        inbox.clear();
        onVoteClosed.run();
    }

    private static void send(SessionMessage message) {
        if (role == Role.HOST && host != null) {
            host.broadcast(message);
        } else if (role == Role.GUEST && guest != null) {
            guest.send(message);
        } else if (role == Role.HOSTED) {
            switch (message.type) {
                case CHAT, VOTE_REQUEST, VOTE_CAST, REMATCH, GRACE_ANSWER -> lineSink.accept(message);
                default -> { }
            }
        } else {
            return;
        }
        inbox.add(message);
    }

    public static void proposeNewMatch() {
        if (!canHostStart()) return;

        com.vivet.speedrun.p2p.Hub.closeInvites();

        for (Racer racer : racers.values()) {
            racer.lobbyReady = false;
        }
        send(new SessionMessage(SessionMessage.Type.READY_CHECK));
    }

    public static Long matchSeed() {
        return matchSeed;
    }

    public static void readyUp() {
        if (!isActive() || phase != Phase.READY_CHECK) return;
        send(SessionMessage.of(SessionMessage.Type.LOBBY_READY, localName));
    }

    public static void unready() {
        if (!isActive() || phase != Phase.READY_CHECK) return;
        send(SessionMessage.of(SessionMessage.Type.LOBBY_UNREADY, localName));
    }

    public static boolean isLocallyReady() {
        Racer racer = racers.get(localName);
        return racer != null && racer.lobbyReady;
    }

    public static void beginMatch() {
        if (role != Role.HOST || phase != Phase.READY_CHECK) return;
        phase = Phase.LOADING;

        var random = new java.util.Random();
        var config = ConfigManager.get();
        broadcastStart(random.nextLong(), random.nextLong(), config.difficulty,
                config.playToLastFinisher);
    }

    public static void broadcastStart(long seed, long salt, Difficulty difficulty,
                                      boolean waitForAll) {
        if (role != Role.HOST) return;

        var config = ConfigManager.get();
        SessionMessage message = new SessionMessage(SessionMessage.Type.START);
        message.seed = seed;
        message.salt = salt;
        message.difficulty = difficulty.id();

        message.worldDifficulty = config.privateWorldDifficulty().getSerializedName();
        message.includeEasier = config.includeEasierItems;
        message.dimension = config.dimensionPool.id();
        message.waitForAll = waitForAll;
        message.rules = config.ruleSnapshot();
        message.ruleLevels = config.levelSnapshot();

        rematchWanted.clear();
        resetRacers();

        started = false;
        matchSize = racers.size();
        matchWinner = "";
        readyDeadline = System.currentTimeMillis() + READY_TIMEOUT_MS;

        send(message);
    }

    private static net.minecraft.world.Difficulty parseWorldDifficulty(String name) {
        if (name == null || name.isBlank()) return net.minecraft.world.Difficulty.NORMAL;

        net.minecraft.world.Difficulty parsed = net.minecraft.world.Difficulty.byName(name);
        return parsed == null ? net.minecraft.world.Difficulty.NORMAL : parsed;
    }

    public static void requestRematch() {
        setRematch(true);
    }

    public static void cancelRematch() {
        setRematch(false);
    }

    public static void refuseRematch() {
        if (role != Role.HOSTED) return;
        rematchWanted.clear();
        publicOver = true;
    }

    private static void setRematch(boolean wanted) {
        if (!isActive() || !publicMatch || publicOver) return;
        if (rematchWanted.contains(localName) == wanted) return;

        SessionMessage message = SessionMessage.of(SessionMessage.Type.REMATCH, localName);
        message.agree = wanted;
        send(message);
    }

    public static void reportReady() {
        if (!isActive()) return;

        goDeadline = System.currentTimeMillis() + GO_TIMEOUT_MS;
        send(SessionMessage.of(SessionMessage.Type.READY, localName));
    }

    private static void giveUpOnStart() {
        goDeadline = 0L;

        String reason = "The match never started.";
        chatSink.accept(reason, Tone.BAD);

        rememberMatch("", 0, reason);
        stop();
        onHostQuit.run();
    }

    private static void sendGo() {
        if (role != Role.HOST || started) return;

        started = true;
        send(new SessionMessage(SessionMessage.Type.GO));
    }

    private static void checkEveryoneLobbyReady() {
        if (role != Role.HOST || phase != Phase.READY_CHECK || racers.isEmpty()) return;

        if (racers.values().stream().allMatch(racer -> racer.lobbyReady)) beginMatch();
    }

    private static void checkEveryoneReady() {
        if (role != Role.HOST || started || racers.isEmpty()) return;

        if (phase != Phase.LOADING) return;

        boolean everyoneReady = racers.values().stream().allMatch(racer -> racer.ready);
        if (everyoneReady || System.currentTimeMillis() > readyDeadline) sendGo();
    }

    public static void announce(String text) {
        if (!isActive()) return;

        SessionMessage message = SessionMessage.of(SessionMessage.Type.EVENT, localName);
        message.text = text;
        send(message);
    }

    public static void reportTime(int seconds, int centis, int activity, String itemId) {
        if (!isActive() || phase != Phase.RACING) return;

        SessionMessage message = SessionMessage.of(SessionMessage.Type.TIME, localName);
        message.seconds = seconds;
        message.centis = centis;

        message.activity = activity;

        if (itemId != null && !itemId.isBlank()) message.itemId = itemId;

        if (matchDimension != DimensionPool.ANY) message.dimension = matchDimension.id();

        message.seedText = seedLabel();

        send(message);
    }

    private static String seedLabel() {
        Long seed = matchSeed;
        return seed == null ? null : String.valueOf(seed.longValue());
    }

    public static void reportFinish(int seconds, int centis, String itemId) {
        if (!isActive()) return;

        Racer you = racer(localName);
        you.status = Racer.Status.FINISHED;
        you.seconds = seconds;
        you.centis = centis;
        you.detail = "found it";

        SessionMessage message = SessionMessage.of(SessionMessage.Type.FINISH, localName);
        message.seconds = seconds;
        message.centis = centis;

        message.itemId = itemId;

        if (matchDimension != DimensionPool.ANY) message.dimension = matchDimension.id();
        message.seedText = seedLabel();

        send(message);
    }

    public static void reportOut(String detail, String itemId) {
        if (!isActive()) return;

        Racer you = racer(localName);
        if (you.isDone()) return;

        you.status = Racer.Status.OUT;
        you.detail = detail == null ? "" : detail;

        SessionMessage message = SessionMessage.of(SessionMessage.Type.FORFEIT, localName);
        message.text = you.detail;

        if (itemId != null && !itemId.isBlank()) message.itemId = itemId;

        send(message);
    }

    public static void say(String text) {
        if (!isActive() || text == null || text.isBlank()) return;

        SessionMessage message = SessionMessage.of(SessionMessage.Type.CHAT, localName);
        message.text = text;
        send(message);

        named(localName, ": " + text, "<" + localName + "> " + text, Tone.CHAT);
    }

    public static void proposeVote(SessionMessage.VoteKind kind) {
        if (!isActive()) return;

        if (openVote != null) return;
        if (kind == SessionMessage.VoteKind.REROLL && !canProposeReroll()) return;

        SessionMessage message = SessionMessage.of(SessionMessage.Type.VOTE_REQUEST, localName);
        message.vote = kind;
        send(message);
    }

    public static void castVote(boolean agree) {
        if (!isActive() || openVote == null) return;

        SessionMessage message = SessionMessage.of(SessionMessage.Type.VOTE_CAST, localName);
        message.vote = openVote.kind;
        message.agree = agree;
        message.voteId = openVote.id;
        send(message);
    }

    public static boolean canProposeReroll() {
        if (!isActive() || isRanked()) return false;
        if (anyoneStoppedRacing()) return false;
        return !publicMatch || rerollsSpent < PUBLIC_REROLL_LIMIT;
    }

    private static boolean anyoneStoppedRacing() {
        return racers.values().stream().anyMatch(Racer::isDone);
    }

    public static int rerollsLeft() {
        if (!publicMatch) return -1;
        return Math.max(0, PUBLIC_REROLL_LIMIT - rerollsSpent);
    }

    private static void broadcastRoster() {
        if (role != Role.HOST || host == null) return;

        List<String> names = new ArrayList<>();
        names.add(localName);
        names.addAll(host.guestNames());

        SessionMessage message = new SessionMessage(SessionMessage.Type.ROSTER);
        message.players = names.toArray(new String[0]);
        send(message);
    }

    public static void drain() {
        SessionMessage message;
        while ((message = inbox.poll()) != null) {
            try {
                handle(message);
            } catch (Exception e) {
                VivetSpeedrun.LOGGER.warn("Bad match message", e);
            }
        }

        if (role == Role.HOST && !started && readyDeadline > 0) checkEveryoneReady();

        if (goDeadline > 0 && phase == Phase.LOADING
                && System.currentTimeMillis() > goDeadline) {
            giveUpOnStart();
            return;
        }

        if (role == Role.GUEST && pendingConnected != null
                && System.currentTimeMillis() > joinDeadline) {
            failJoin("The host didn't respond.");
        }
    }

    private static void handle(SessionMessage message) {
        if (role == Role.NONE) return;

        switch (message.type) {
            case JOIN -> {
                racer(message.name);
                note(message.name + " joined");
                broadcastRoster();
            }
            case WELCOME -> {
                applyRoster(message.players);
                statusSink.accept("Joined! Waiting for the host to start.");

                Runnable connected = pendingConnected;
                pendingConnected = null;
                pendingError = null;
                if (connected != null) connected.run();
            }
            case ROSTER -> applyRoster(message.players);
            case ROSTER_REFRESH -> broadcastRoster();
            case READY_CHECK -> {
                phase = Phase.READY_CHECK;
                for (Racer racer : racers.values()) {
                    racer.lobbyReady = false;
                }
                note("New match started");

                chatSink.accept("The host started a new match. Ready up!", Tone.NEUTRAL);
                onReadyCheck.run();
            }
            case LOBBY_READY -> {
                racer(message.name).lobbyReady = true;
                note(message.name + " is ready");
                checkEveryoneLobbyReady();
            }
            case LOBBY_UNREADY -> {
                racer(message.name).lobbyReady = false;
                note(message.name + " isn't ready");
            }
            case START -> {
                if (System.currentTimeMillis() < nextStartAllowedAt) {
                    VivetSpeedrun.LOGGER.warn("Ignored duplicate START ({} ms after the last)",
                            START_COOLDOWN_MS - (nextStartAllowedAt - System.currentTimeMillis()));
                    return;
                }
                nextStartAllowedAt = System.currentTimeMillis() + START_COOLDOWN_MS;

                phase = Phase.LOADING;
                onReadyCheckDone.run();
                closeVote();
                resetRacers();
                matchSize = racers.size();
                matchWinner = "";

                matchDecided = false;
                lastWinner = "";
                lastWinnerSeconds = 0;
                lastEndReason = "";
                lastVoided = false;
                lastStandings.clear();

                lastMatchId = "";

                playToLastFinisher = Boolean.TRUE.equals(message.waitForAll);
                rematchWanted.clear();
                graceOffered = false;
                forgetCarryOn();
                feed.clear();

                matchSeed = message.seed;
                matchSalt = message.salt == null ? 0L : message.salt;

                matchBand = Difficulty.byId(message.difficulty == null
                        ? "medium" : message.difficulty);
                matchIncludeEasier = Boolean.TRUE.equals(message.includeEasier);
                matchDimension = DimensionPool.byId(message.dimension);
                matchRerolls = 0;
                rerollsSpent = 0;

                matchWorldDifficulty = parseWorldDifficulty(message.worldDifficulty);

                com.vivet.speedrun.game.MatchRules.set(worldRules());

                ConfigManager.get().sessionRules = message.rules;
                ConfigManager.get().sessionLevels = message.ruleLevels;
                onStart.accept(message);
            }
            case READY -> {
                racer(message.name).ready = true;
                note(message.name + " is ready");
                checkEveryoneReady();
            }
            case GO -> {
                phase = Phase.RACING;
                goDeadline = 0L;
                onGo.run();
            }
            case CHAT -> {
                if (!isLocal(message.name) && message.text != null) {
                    String said = plainText(message.text);
                    named(message.name, ": " + said, "<" + message.name + "> " + said, Tone.CHAT);
                }
            }
            case EVENT -> {
                Racer racer = racer(message.name);
                String event = message.text == null ? null : plainText(message.text);
                if (event != null) racer.addMilestone(event);

                if (!isLocal(message.name)) {
                    note(message.name + " " + event);

                    named(message.name, " " + event,
                            message.name + " " + event, Tone.NEUTRAL);
                    cueSink.accept(Cue.RIVAL_MOVED);
                }
            }
            case TIME -> {
                Racer racer = racer(message.name);
                if (message.seconds != null && message.seconds > racer.liveSeconds) {
                    racer.liveSeconds = message.seconds;
                }

                int reported = message.centis != null
                        ? message.centis
                        : com.vivet.speedrun.game.Time.toCentis(
                                message.seconds == null ? 0 : message.seconds);
                racer.syncLive(reported);
            }
            case FINISH -> {
                Racer racer = racer(message.name);
                racer.status = Racer.Status.FINISHED;
                racer.seconds = message.seconds == null ? 0 : message.seconds;

                racer.centis = message.centis == null
                        ? com.vivet.speedrun.game.Time.toCentis(racer.seconds)
                        : message.centis;
                racer.liveSeconds = Math.max(racer.liveSeconds, racer.seconds);
                racer.liveCentis = racer.centis;
                racer.liveAt = 0L;
                note(message.name + " found the item in "
                        + com.vivet.speedrun.game.Time.format(racer.seconds));

                if (!isLocal(message.name)) {
                    announceRivalFinish(racer);
                }
            }
            case FORFEIT -> {
                Racer racer = racer(message.name);
                racer.status = "disconnected".equals(message.text)
                        ? Racer.Status.DISCONNECTED
                        : Racer.Status.OUT;
                racer.detail = message.text == null ? "" : message.text;
                note(message.name + " " + racer.statusPhrase());

                if (!isLocal(message.name)) {
                    chatSink.accept("died".equals(racer.detail)
                            ? message.name + " died and is out."
                            : message.name + " " + racer.statusPhrase(), Tone.NEUTRAL);
                }
                checkEveryoneLobbyReady();
            }
            case VOTE_REQUEST -> {
                if (role != Role.HOST || message.vote == null || message.name == null) return;
                if (openVote != null) return;
                if (message.vote == SessionMessage.VoteKind.REROLL && !canProposeReroll()) return;

                SessionMessage opened = new SessionMessage(SessionMessage.Type.VOTE_OPEN);
                opened.vote = message.vote;
                opened.name = message.name;
                opened.voteId = ++voteSequence;
                send(opened);
            }
            case VOTE_OPEN -> {
                if (message.vote == null || message.name == null || message.voteId == null) return;

                openVote = new Vote(message.vote, message.name, message.voteId);
                openVote.responses.put(message.name, true);
                chatSink.accept(message.name + " started a vote: " + openVote.title(), Tone.NEUTRAL);

                if (!isLocal(message.name)) onVoteOpened.accept(openVote);
                resolveVote();
            }
            case VOTE_CAST -> {
                if (openVote == null || message.agree == null) return;

                if (message.voteId != null && message.voteId != openVote.id) return;

                openVote.responses.put(message.name, message.agree);
                resolveVote();
            }
            case VOTE_RESULT -> {
                closeVote();

                if (message.vote == SessionMessage.VoteKind.REROLL) rerollsSpent++;

                String what = message.vote == SessionMessage.VoteKind.REROLL ? "Reroll" : "Draw";
                if (Boolean.TRUE.equals(message.agree)) {
                    note(what + " approved");
                    chatSink.accept(what + " approved.", Tone.GOOD);
                } else if (message.text != null && !message.text.isBlank()) {
                    note(what + " cancelled");
                    chatSink.accept(message.text, Tone.BAD);
                } else {
                    String by = message.name == null ? "someone" : message.name;
                    note(what + " declined by " + by);
                    chatSink.accept(what + " declined by " + by + ".", Tone.BAD);
                }

                if (message.vote == SessionMessage.VoteKind.REROLL && publicMatch) {
                    int left = rerollsLeft();
                    chatSink.accept(left == 1 ? "1 reroll left." : left + " rerolls left.",
                            Tone.NEUTRAL);
                }
            }
            case REROLL -> {
                closeVote();
                note("New item rolled");
                matchRerolls = message.rerollIndex == null ? matchRerolls + 1 : message.rerollIndex;
                onReroll.accept(matchRerolls);
            }
            case DRAW -> {
                phase = Phase.FINISHED;
                        ConfigManager.get().sessionRules = null;
                ConfigManager.get().sessionLevels = null;

                closeVote();
                for (Racer racer : racers.values()) {
                    if (racer.status != Racer.Status.RACING) continue;
                    racer.status = Racer.Status.OUT;
                    racer.detail = "drew";
                }

                rememberMatch("", 0, "Draw agreed");
                lastDrawn = true;
                onDraw.run();
                onEnd.accept("Draw agreed");
            }
            case REMATCH -> {
                if (!publicMatch || publicOver) return;

                boolean wants = message.agree == null || message.agree;
                if (wants) {
                    rematchWanted.add(message.name);
                } else {
                    rematchWanted.remove(message.name);
                }

                note(message.name + (wants ? " wants a rematch" : " isn't ready"));
                if (!isLocal(message.name)) {
                    chatSink.accept(wants
                            ? message.name + " wants a rematch."
                            : message.name + " isn't ready anymore.", Tone.NEUTRAL);
                }
            }
            case GRACE -> {
                if (message.name != null && !message.name.equals(localName)) {
                    VivetSpeedrun.LOGGER.info("Keep playing offer was for {}, ignored",
                            message.name);
                    return;
                }
                if (racer(localName).isDone()) {
                    VivetSpeedrun.LOGGER.info("Keep playing offer dropped, already out ({})", racer(localName).detail);
                    return;
                }
                VivetSpeedrun.LOGGER.info("Keep playing offer: {} s", message.seconds);

                graceOffered = true;
                carryOnSeconds = message.seconds == null ? 0 : Math.max(0, message.seconds);
                onGraceOffered.accept(Difficulty.byId(message.difficulty));
            }
            case CARRY_ON -> {
                if (message.seconds != null && message.seconds > 0) {
                    carryOnSeconds = message.seconds;
                }
                if (message.until != null && message.until > 0L) {
                    carryOnUntil = message.until;
                }
            }
            case END -> {
                String ending = message.matchId == null ? "" : message.matchId;
                if (matchDecided && !ending.isEmpty() && ending.equals(lastMatchId)) return;

                phase = Phase.FINISHED;
                closeVote();
                graceOffered = false;
                forgetCarryOn();

                forgetRejoin();
                awayName = "";
                awayUntil = 0L;

                ConfigManager.get().sessionRules = null;
                ConfigManager.get().sessionLevels = null;

                boolean noResult = Boolean.TRUE.equals(message.voided)
                        || Boolean.TRUE.equals(message.conceded);
                matchWinner = message.name == null || noResult ? "" : message.name;

                lastMatchId = message.matchId == null ? "" : message.matchId;

                Racer champion = matchWinner.isEmpty() ? null : racers.get(matchWinner);
                if (champion != null && champion.status == Racer.Status.RACING) {
                    champion.status = Racer.Status.FINISHED;
                    champion.seconds = message.seconds == null
                            ? champion.liveSeconds : message.seconds;
                    champion.detail = "last one standing";
                }

                for (Racer racer : racers.values()) {
                    if (racer.status != Racer.Status.RACING) continue;
                    racer.status = Racer.Status.OUT;
                    if (racer.detail.isBlank()) racer.detail = "did not finish";
                }

                String reason = message.text == null ? "Lobby closed" : message.text;
                boolean walkover = Boolean.TRUE.equals(message.walkover);
                if (message.name != null && message.seconds != null && !walkover) {
                    reason = message.name + " wins with "
                            + com.vivet.speedrun.game.Time.format(message.seconds);
                    note(reason);
                }

                rememberMatch(matchWinner,
                        champion != null ? champion.seconds
                                : (message.seconds == null ? 0 : message.seconds),
                        reason,
                        Boolean.TRUE.equals(message.voided));

                lastConceded = Boolean.TRUE.equals(message.conceded);

                lastDrawn = matchWinner.isEmpty() && !walkover && !noResult;

                if (publicMatch && walkover) {
                    publicOver = true;
                    onPublicMatchOver.accept(reason, localIsMatchWinner());
                }
                onEnd.accept(reason);
            }
            case LINK_LOST -> {
                String reason = message.text == null ? "Connection lost." : message.text;

                boolean comeback = holdsRejoin();

                if (comeback) {
                    chatSink.accept(reason + " You can still rejoin.", Tone.BAD);
                    stop();
                    onHostQuit.run();
                    return;
                }

                chatSink.accept(reason, Tone.BAD);
                stop();
                onHostQuit.run();
            }
            case HOST_QUIT -> {
                if (role != Role.GUEST) return;

                String reason = message.text == null
                        ? "The host closed the lobby." : message.text;

                if (pendingConnected != null) {
                    failJoin("The host closed the connection before you joined.");
                    return;
                }

                chatSink.accept(reason, Tone.BAD);
                stop();

                forgetRejoin();
                onHostQuit.run();
            }
            case AFK -> {
                boolean kicked = "kicked".equals(message.state);
                boolean mine = isLocal(message.name);
                String reason = message.text == null ? "" : message.text;

                if (kicked && !mine) {
                    Racer idle = racer(message.name);
                    if (!idle.isDone()) {
                        idle.status = Racer.Status.OUT;
                        idle.detail = "went idle";
                    }
                }

                note(mine ? (kicked ? "You were kicked" : "You went AFK")
                        : message.name + (kicked ? " was kicked" : " went AFK"));

                if (mine) {
                    chatSink.accept(reason, Tone.BAD);
                } else if (kicked) {
                    chatSink.accept(message.name + " was kicked for being AFK.", Tone.GOOD);
                } else {
                    chatSink.accept(message.name + " is AFK.", Tone.NEUTRAL);
                }
            }
            case REFUSED -> {
                chatSink.accept(message.text == null ? "Message not sent." : message.text,
                        Tone.BAD);
            }
            case REJECT -> {
                String reason = message.text == null ? "The host didn't let you in." : message.text;
                chatSink.accept(reason, Tone.BAD);
                failJoin(reason);
            }
            case LABELS -> {
                labels.clear();
                if (message.labels != null) {
                    message.labels.forEach((name, id) -> {
                        if (id != null && !id.isBlank()) labels.put(name, id);
                    });
                }

                if (message.uuids != null) {
                    message.uuids.forEach((name, id) -> {
                        if (id != null && !id.isBlank()) accountIds.put(name, id);
                    });
                }

                if (message.looks != null) {
                    message.looks.forEach((name, slots) -> {
                        if (slots != null) looks.put(name, Map.copyOf(slots));
                    });
                }
                labelsVersion++;
            }
            case AWAY -> {
                if (isLocal(message.name)) break;

                awayName = message.name == null ? "" : message.name;
                awayUntil = message.until == null ? 0L : message.until;

                int seconds = message.seconds == null ? 60 : message.seconds;
                note(awayName + " lost connection");
                chatSink.accept(awayName + " disconnected. They have " + seconds + "s to rejoin.",
                        Tone.NEUTRAL);
                cueSink.accept(Cue.OPPONENT_LEFT);
            }
            case BACK -> {
                if (isLocal(message.name)) break;

                awayName = "";
                awayUntil = 0L;
                note(message.name + " reconnected");
                chatSink.accept(message.name + " is back.", Tone.GOOD);
                cueSink.accept(Cue.OPPONENT_RETURNED);
            }
            case FORFEITED -> {
                if (isLocal(message.name)) break;

                awayName = "";
                awayUntil = 0L;

                Racer gone = racer(message.name);
                if (!gone.isDone()) {
                    gone.status = Racer.Status.OUT;
                    gone.detail = "left";
                }
                note(message.name + " forfeited");
                chatSink.accept(message.name + " didn't rejoin and forfeits.",
                        Tone.GOOD);
            }
        }
    }

    private static void applyRoster(String[] players) {
        if (players == null) return;

        for (String name : players) {
            racer(name);
        }
        racers.keySet().retainAll(List.of(players));

        checkEveryoneLobbyReady();
    }

    private static void closeVote() {
        openVote = null;
        onVoteClosed.run();
    }

    private static void resolveVote() {
        if (openVote == null || role != Role.HOST) return;

        String decliner = openVote.responses.entrySet().stream()
                .filter(entry -> !entry.getValue())
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);

        if (decliner != null) {
            SessionMessage result = new SessionMessage(SessionMessage.Type.VOTE_RESULT);
            result.vote = openVote.kind;
            result.agree = false;
            result.name = decliner;
            send(result);

            closeVote();
            return;
        }

        long stillRacing = racers.values().stream().filter(racer -> !racer.isDone()).count();
        if (openVote.responses.size() < Math.max(1, stillRacing)) return;

        SessionMessage.VoteKind kind = openVote.kind;
        closeVote();

        if (kind == SessionMessage.VoteKind.REROLL && !canProposeReroll()) {
            SessionMessage cancelled = new SessionMessage(SessionMessage.Type.VOTE_RESULT);
            cancelled.vote = kind;
            cancelled.agree = false;
            cancelled.text = anyoneStoppedRacing() ? "Too late to reroll." : "No rerolls left.";
            send(cancelled);
            return;
        }

        SessionMessage passed = new SessionMessage(SessionMessage.Type.VOTE_RESULT);
        passed.vote = kind;
        passed.agree = true;
        send(passed);

        if (kind == SessionMessage.VoteKind.DRAW) {
            send(new SessionMessage(SessionMessage.Type.DRAW));
        } else {
            SessionMessage message = new SessionMessage(SessionMessage.Type.REROLL);
            message.rerollIndex = ++matchRerolls;
            send(message);
        }
    }

    private static void resetRacers() {
        for (Racer racer : racers.values()) {
            racer.status = Racer.Status.RACING;
            racer.seconds = 0;
            racer.liveSeconds = 0;
            racer.centis = 0;
            racer.liveCentis = 0;
            racer.liveAt = 0L;
            racer.detail = "";
            racer.ready = false;
            racer.milestones.clear();
        }
    }

    private static void announceRivalFinish(Racer rival) {
        String time = com.vivet.speedrun.game.Time.format(rival.seconds);
        chatSink.accept(rival.name + " found the item in " + time + "!", Tone.BAD);

        Racer you = racers.get(localName);
        if (you == null || you.isDone()) return;

        if (playToLastFinisher) {
            chatSink.accept("Keep going. The match ends when everyone finishes.",
                    Tone.NEUTRAL);
        } else {
            chatSink.accept("Beat " + time + " to win.", Tone.NEUTRAL);
        }
    }

    public static boolean someoneHasFinished() {
        return fastestFinisher() != null;
    }

    private static Racer fastestFinisher() {
        return racers.values().stream()
                .filter(racer -> racer.status == Racer.Status.FINISHED)
                .min(java.util.Comparator.comparingInt(racer -> racer.seconds))
                .orElse(null);
    }

    public static Target drawTarget() {
        return ItemTiers.draw(matchBand, matchIncludeEasier, matchDimension, matchSalt,
                matchRerolls);
    }

    public static DimensionPool matchDimension() {
        return matchDimension;
    }

    public static DimensionPool activeDimension() {
        if (hosted) return DimensionPool.ANY;

        if (isActive()) return matchDimension;

        if (com.vivet.speedrun.game.Practice.isActive()) return DimensionPool.ANY;

        return ConfigManager.get().dimensionPool;
    }

    public static Difficulty matchBand() {
        return matchBand;
    }

    private static SessionMessage messageWithText(SessionMessage.Type type, String text) {
        SessionMessage message = new SessionMessage(type);
        message.text = text;
        return message;
    }

    private static Racer racer(String name) {
        String key = name == null ? "?" : name;

        Racer existing = racers.get(key);
        if (existing != null) return existing;

        Racer created = new Racer(key);
        racers.put(key, created);
        return created;
    }

    public static boolean isLocalName(String name) {
        return isLocal(name);
    }

    private static boolean isLocal(String name) {
        return localName.equals(name);
    }

    private static void note(String text) {
        feed.add(text);
        while (feed.size() > 40) {
            feed.remove(0);
        }
    }
}
