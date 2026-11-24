package com.vivet.speedrun.p2p;

import com.google.gson.Gson;
import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.config.ConfigManager;

import java.util.concurrent.atomic.AtomicBoolean;

public final class Bounties {
    private static volatile View view;
    private static volatile long at;
    private static volatile long askedAt;
    private static final AtomicBoolean FETCHING = new AtomicBoolean();

    private static final long TTL_MS = 60_000L;

    public static final long CASHOUT_MIN_CENTS = 1_000L;
    private static final long RETRY_MS = 30_000L;

    private static final Gson GSON = new Gson();

    public static final class Winner {
        public int place;
        public String name;
        public long cents;
        public long at;
    }

    public static final class First {
        public String id;
        public long cents;
        public long runnerUp;
        public boolean open = true;
        public Winner[] winners = new Winner[0];
        public int finishers;
    }

    public static final class Secret {
        public String tier;
        public long cents;
        public int total;
        public int found;
        public Winner[] winners = new Winner[0];
    }

    public static final class Monthly {
        public String month;
        public long elo;
        public long seed;
        public long clip;
        public String last;
        public MonthlyWinner[] winners = new MonthlyWinner[0];
    }

    public static final class MonthlyWinner {
        public String bounty;
        public String name;
        public long cents;
    }

    public static final class Range {
        public long min;
        public long max;
    }

    public static final class Recent {
        public String bounty;
        public int place;
        public String name;
        public long cents;
        public long at;
    }

    public static final class View {
        public Recent[] recent = new Recent[0];
        public long totalWon;
        public boolean live;

        public int players;
        public Range range;
        public First[] firsts = new First[0];
        public Secret[] secrets = new Secret[0];
        public Monthly monthly;
        public String error;
    }

    public static final class Me {
        public boolean optedIn;
        public long won;
        public long balance;
        public long pending;
        public java.util.Map<String, Integer> secrets = new java.util.HashMap<>();
        public String error;
    }

    private static volatile Me me;
    private static volatile long meAt;
    private static volatile long meAskedAt;
    private static final AtomicBoolean ME_FETCHING = new AtomicBoolean();

    public static Me me() {
        return me;
    }

    public static void refreshMe() {
        long now = System.currentTimeMillis();
        boolean whole = me != null;
        if (whole ? now - meAt < TTL_MS : now - meAskedAt < RETRY_MS) return;
        String token = MojangAuth.token();
        if (token == null) return;
        if (!ME_FETCHING.compareAndSet(false, true)) return;

        meAskedAt = now;
        String url = MojangAuth.httpBase(ConfigManager.get().relayUrl) + "/bounties/me?t="
                + java.net.URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8);
        MojangAuth.get(url)
                .thenApply(body -> GSON.fromJson(body, Me.class))
                .whenComplete((answer, error) -> {
                    if (error == null && answer != null && answer.error == null) me = answer;
                    meAt = System.currentTimeMillis();
                    ME_FETCHING.set(false);
                });
    }

    public static final View PLACEHOLDER = placeholder();

    private Bounties() {
    }

    public static View view() {
        View current = view;
        return current != null ? current : PLACEHOLDER;
    }

    public static void refresh() {
        long now = System.currentTimeMillis();
        boolean whole = view != null;
        if (whole ? now - at < TTL_MS : now - askedAt < RETRY_MS) return;
        if (!FETCHING.compareAndSet(false, true)) return;

        askedAt = now;
        String url = MojangAuth.httpBase(ConfigManager.get().relayUrl) + "/bounties";
        MojangAuth.get(url)
                .thenApply(body -> GSON.fromJson(body, View.class))
                .whenComplete((answer, error) -> {
                    if (error == null && answer != null && answer.error == null && answer.firsts != null
                            && answer.secrets != null && answer.monthly != null) {
                        view = answer;
                    } else {
                        VivetSpeedrun.LOGGER.debug("Could not get bounties");
                    }
                    at = System.currentTimeMillis();
                    FETCHING.set(false);
                });
    }

    private static View placeholder() {
        View v = new View();
        v.firsts = new First[] {
                first("level_100", 2_500, 300),
                first("all_items", 2_500, 300),
                first("speed_god", 2_500, 300),
                first("grand_champion", 300, 0),
        };
        v.secrets = new Secret[] {
                secret("easy", 500, 6),
                secret("medium", 500, 5),
                secret("hard", 800, 3),
                secret("impossible", 1_500, 2),
        };
        Monthly m = new Monthly();
        m.elo = 1_000;
        m.seed = 200;
        m.clip = 500;
        v.monthly = m;
        Range range = new Range();
        range.min = 200;
        range.max = 2_500;
        v.range = range;
        return v;
    }

    private static First first(String id, long cents, long runnerUp) {
        First f = new First();
        f.id = id;
        f.cents = cents;
        f.runnerUp = runnerUp;
        return f;
    }

    private static Secret secret(String tier, long cents, int total) {
        Secret s = new Secret();
        s.tier = tier;
        s.cents = cents;
        s.total = total;
        return s;
    }

    public static int bandOf(long players) {
        if (players < 50) return 0;
        long band = 50;
        double[] steps = { 2, 2.5, 2 };
        for (int i = 0; ; i++) {
            long next = Math.round(band * steps[i % 3]);
            if (next > players) return (int) Math.min(Integer.MAX_VALUE, band);
            band = next;
        }
    }

    public static long fullAt(int band) {
        return band <= 0 ? 2_500L : band * 100L;
    }

    private static long share(long full, double share, long floor) {
        return Math.max(floor, (long) Math.ceil(full * share / 100.0) * 100L);
    }

    public static long firstAt(String id, int band, boolean runnerUp) {
        long full = fullAt(band);
        if ("grand_champion".equals(id)) return runnerUp ? 0 : share(full, 0.10, 0);
        return share(full, runnerUp ? 0.10 : 1.0, 0);
    }

    public static long secretAt(String tier, int band) {
        double share = switch (tier) {
            case "easy" -> 0.05;
            case "medium" -> 0.15;
            case "hard" -> 0.30;
            case "impossible" -> 0.60;
            default -> 0.0;
        };
        return share <= 0 ? 0 : share(fullAt(band), share, 500);
    }

    public static long eloAt(int band) {
        return share(fullAt(band), 0.10, 1_000);
    }

    public static long seedAt(int band) {
        return Math.round(share(fullAt(band), 0.10, 1_000) / 5.0);
    }

    public static long clipAt(int band) {
        return share(fullAt(band), 0.10 / 4, 500);
    }

    public static long totalAt(View view, int band) {
        long total = 0;
        for (First first : view.firsts) if (first.open) total += firstAt(first.id, band, false);
        for (Secret secret : view.secrets) total += (long) Math.max(0, secret.total - secret.found) * secretAt(secret.tier, band);
        total += eloAt(band) + seedAt(band) * 5 + clipAt(band) * 4;
        return total;
    }

    public static String money(long cents) {
        long dollars = cents / 100;
        long rest = Math.abs(cents % 100);
        String whole = String.format(java.util.Locale.ROOT, "%,d", dollars);
        return rest == 0 ? "$" + whole : "$" + whole + String.format(java.util.Locale.ROOT, ".%02d", rest);
    }
}
