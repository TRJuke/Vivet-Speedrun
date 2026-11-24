package com.vivet.speedrun.p2p;

import com.google.gson.Gson;
import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.config.ConfigManager;
import com.vivet.speedrun.game.Difficulty;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public final class RankedApi {
    private static final Gson GSON = new Gson();

    public static final class Board {
        public String kind;
        public String band;
        public String item;
        public String seed;
        public Row[] rows;
        public String error;
    }

    public static final class Row {
        public int place;
        public String uuid;
        public String name;

        public Integer seconds;

        public Integer centis;
        public String itemId;

        public String replay;

        public Integer rating;
        public String rank;

        public boolean speedKing;

        public String speedTitle;
        public Integer wins;
        public Integer losses;

        public Integer value;

        public String label;

        public String background;

        public Boolean placed;

        public String emblem;
    }

    public static final class Config {
        public Rank[] ranks;

        public int hostedRejoinSeconds;
        public int placementMatches;
        public int graceSeconds;
        public int afkWarnSeconds;
        public int afkKickSeconds;
        public String setSeed;

        public java.util.Map<String, String> setSeedItems;

        public String speedKing;

        public java.util.Map<String, String> speedTitles;

        public int bioMax;

        public String error;
    }

    public static final class Rank {
        public String id;
        public String name;
        public Integer min;
        public String color;
    }

    public static final class HostedTicket {
        public String match;
        public String host;
        public int port;
        public String band;
        public String error;
        public boolean full;
        public Long bannedUntil;

        public boolean queued;
        public int position;

        public boolean racing;

        public boolean setSeed;

        public String kind;
        public String opponent;
        public String opponentUuid;
        public Boolean ranked;

        public Boolean over;

        public Missed result;

        public boolean isMatch() {
            return "ranked".equals(this.kind) || "public".equals(this.kind);
        }
    }

    public static final class Missed {
        public String match;
        public String band;
        public boolean ranked;
        public String opponent;
        public String opponentUuid;

        public String itemId;

        public RelayConnection.Frame verdict;

        public MissedStanding standing;

        public MissedXp xp;
    }

    public static final class MissedStanding {
        public MojangAuth.Profile profile;
        public Integer delta;
        public Before before;
    }

    public static final class MissedXp {
        public RelayConnection.Xp xp;
        public Before before;
    }

    public static final class Before {
        public Integer rating;
        public String rank;
        public Integer level;
        public Integer into;
        public Integer need;
    }

    public static final class Ack {
        public boolean ok;
        public String error;
    }

    public static final class Collected {
        public java.util.List<Found> entries;
        public Long at;
        public String error;
    }

    public static final class Found {
        public String id;

        public Integer attempts;
        public Integer finishes;

        public Integer wins;
        public Integer losses;
        public Integer draws;

        public Integer best;

        public Integer place;
    }

    private static volatile Config config;

    private static volatile long configAt;
    private static final java.util.concurrent.atomic.AtomicBoolean REFRESHING =
            new java.util.concurrent.atomic.AtomicBoolean();

    private static final long CONFIG_TTL_MS = 300_000L;

    private static final long AWAY_CONFIG_TTL_MS = 1_800_000L;

    private static volatile java.util.Map<String, String> speedTitles = java.util.Map.of();

    private static final String SPEED_KING = "speed_king";

    public static void noteSpeedTitles(java.util.Map<String, String> titles) {
        java.util.Map<String, String> next = new java.util.HashMap<>();
        if (titles != null) {
            titles.forEach((uuid, title) -> {
                if (uuid != null && !uuid.isBlank() && title != null && !title.isBlank()) {
                    next.put(uuid, title);
                }
            });
        }
        speedTitles = java.util.Map.copyOf(next);
    }

    public static void noteSpeedKing(String uuid) {
        noteSpeedTitles(uuid == null || uuid.isBlank()
                ? java.util.Map.of() : java.util.Map.of(uuid, SPEED_KING));
    }

    public static void noteProfile(MojangAuth.Profile profile) {
        if (profile == null || profile.uuid == null) return;

        String title = profile.speedTitle != null && !profile.speedTitle.isBlank()
                ? profile.speedTitle
                : (profile.speedKing ? SPEED_KING : null);

        java.util.Map<String, String> next = new java.util.HashMap<>(speedTitles);
        if (title == null) {
            next.remove(profile.uuid);
        } else {
            next.put(profile.uuid, title);
        }
        speedTitles = java.util.Map.copyOf(next);
    }

    private static void noteConfigTitles(Config loaded) {
        if (loaded == null) {
            noteSpeedTitles(java.util.Map.of());
        } else if (loaded.speedTitles != null) {
            noteSpeedTitles(loaded.speedTitles);
        } else {
            noteSpeedKing(loaded.speedKing);
        }
    }

    public static String speedTitle(String uuid) {
        return uuid == null || uuid.isEmpty() ? null : speedTitles.get(uuid);
    }

    private RankedApi() {
    }

    public static Config cachedConfig() {
        return config;
    }

    private static String base() {
        return MojangAuth.httpBase(ConfigManager.get().relayUrl);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String configUrl() {
        return base() + "/ranked/config";
    }

    public static void startHostedSolo(Difficulty band, boolean setSeed, boolean pity,
                                       Consumer<HostedTicket> onOpened, Consumer<String> onFailed) {
        String token = MojangAuth.token();
        if (token == null) {
            onFailed.accept("Not signed in.");
            return;
        }

        String url = base() + "/hosted/solo?t=" + encode(token)
                + "&band=" + band.id()
                + "&seed=" + (setSeed ? "set" : "random")
                + "&pity=" + (pity ? "1" : "0")
                + "&queue=1";

        fetch(url, HostedTicket.class, onOpened, onFailed);
    }

    public static void hostedRejoin(String match, Consumer<HostedTicket> onAnswer,
                                    Consumer<String> onFailed) {
        String token = MojangAuth.token();
        if (token == null) {
            onFailed.accept("Not signed in.");
            return;
        }
        String named = match == null || match.isBlank() ? "" : "&match=" + encode(match);
        fetch(base() + "/hosted/rejoin?t=" + encode(token) + named, HostedTicket.class, onAnswer,
                onFailed);
    }

    public static void hostedGiveUp() {
        String token = MojangAuth.token();
        if (token == null) return;
        fetch(base() + "/hosted/giveup?t=" + encode(token), Ack.class, ack -> { },
                logging("Giving up a hosted race"));
    }

    public static void leaveHostedQueue() {
        String token = MojangAuth.token();
        if (token == null) return;
        fetch(base() + "/hosted/queue/leave?t=" + encode(token), Ack.class, ack -> { },
                logging("Leaving the line"));
    }

    public static long hostedRejoinMs() {
        Config known = config;
        int seconds = known == null || known.hostedRejoinSeconds <= 0 ? 120 : known.hostedRejoinSeconds;
        return seconds * 1000L;
    }

    public static void loadConfig(Consumer<Config> onLoaded, Consumer<String> onFailed) {
        Config known = config;
        if (known != null) {
            onLoaded.accept(known);
            refreshConfig();
            return;
        }

        fetch(configUrl(), Config.class,
                loaded -> {
                    config = loaded;
                    configAt = System.currentTimeMillis();
                    noteConfigTitles(loaded);
                    onLoaded.accept(loaded);
                },
                onFailed);
    }

    public static void refreshConfig() {
        if (config == null) return;

        long ttl = Attention.interval(CONFIG_TTL_MS, AWAY_CONFIG_TTL_MS);
        if (System.currentTimeMillis() - configAt < ttl) return;
        if (!REFRESHING.compareAndSet(false, true)) return;

        fetch(configUrl(), Config.class,
                loaded -> {
                    if (loaded != null && loaded.error == null) {
                        config = loaded;
                        configAt = System.currentTimeMillis();
                        noteConfigTitles(loaded);
                    } else {
                        configAt = System.currentTimeMillis();
                    }
                    REFRESHING.set(false);
                },
                failure -> {
                    configAt = System.currentTimeMillis();
                    REFRESHING.set(false);
                });
    }

    public static void loadProfile(Consumer<MojangAuth.Profile> onLoaded,
                                   Consumer<String> onFailed) {
        String token = MojangAuth.token();
        if (token == null) {
            onFailed.accept("Not signed in.");
            return;
        }

        fetch(base() + "/profile?t=" + encode(token), MojangAuth.Profile.class,
                profile -> {
                    MojangAuth.updateProfile(profile);
                    noteProfile(profile);
                    onLoaded.accept(profile);
                },
                onFailed);
    }

    public static void loadProfileOf(String name, Consumer<MojangAuth.Profile> onLoaded,
                                     Consumer<String> onFailed) {
        if (name == null || name.isBlank()) {
            onFailed.accept("No opponent yet.");
            return;
        }

        fetch(base() + "/profile?name=" + encode(name), MojangAuth.Profile.class,
                onLoaded, onFailed);
    }

    public static void itemBoard(String itemId, Consumer<Board> onLoaded,
                                 Consumer<String> onFailed) {
        fetch(base() + "/board?kind=item&item=" + encode(itemId), Board.class, onLoaded, onFailed);
    }

    public static void bandBoard(Difficulty band, Consumer<Board> onLoaded,
                                 Consumer<String> onFailed) {
        fetch(base() + "/board?kind=band&band=" + band.id(), Board.class, onLoaded, onFailed);
    }

    public static void seedBoard(Difficulty band, Consumer<Board> onLoaded,
                                 Consumer<String> onFailed) {
        fetch(base() + "/board?kind=seed&band=" + band.id(), Board.class, onLoaded, onFailed);
    }

    public static void collection(Consumer<Collected> onLoaded, Consumer<String> onFailed) {
        String token = MojangAuth.token();
        if (token == null) {
            onFailed.accept("Not signed in.");
            return;
        }

        fetch(base() + "/collection?t=" + encode(token), Collected.class, onLoaded, onFailed);
    }

    public static void ratingBoard(Consumer<Board> onLoaded, Consumer<String> onFailed) {
        fetch(base() + "/board?kind=rating", Board.class, onLoaded, onFailed);
    }

    public static void playerBoard(String kind, Consumer<Board> onLoaded,
                                   Consumer<String> onFailed) {
        fetch(base() + "/board?kind=" + encode(kind), Board.class, onLoaded, onFailed);
    }

    private static <T> void fetch(String url, Class<T> type, Consumer<T> onLoaded,
                                  Consumer<String> onFailed) {
        MojangAuth.get(url)
                .thenApply(body -> GSON.fromJson(body, type))
                .whenComplete((result, error) -> {
                    if (error != null || result == null) {
                        VivetSpeedrun.LOGGER.info("Request failed: {}",
                                error == null ? "empty response" : error.toString());
                        onFailed.accept(reasonFor(error));
                        return;
                    }
                    onLoaded.accept(result);
                });
    }

    private static String reasonFor(Throwable error) {
        return "Couldn't connect. Open a ticket on the Discord for help.";
    }

    public static <T> Consumer<String> logging(String what) {
        return reason -> VivetSpeedrun.LOGGER.info("{}: {}", what, reason);
    }
}
