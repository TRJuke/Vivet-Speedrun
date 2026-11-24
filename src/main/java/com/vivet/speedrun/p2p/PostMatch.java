package com.vivet.speedrun.p2p;

public final class PostMatch {
    public record Standing(MojangAuth.Profile profile, Integer delta) {
        public int change() {
            return this.delta == null ? 0 : this.delta;
        }

        public boolean moved() {
            return this.delta != null && this.delta != 0;
        }
    }

    private static volatile Standing current;

    private static volatile int gained;

    private static volatile boolean xpArrived;

    private static volatile int beforeLevel;
    private static volatile int beforeInto;
    private static volatile int beforeNeed;
    private static volatile boolean beforeKnown;

    public static final class Coins {
        public Integer balance;
        public Integer gained;
        public Boolean race;
        public Part[] parts;
    }

    public static final class Part {
        public String reason;
        public Integer amount;
    }

    private static volatile Part[] coinParts = new Part[0];
    private static volatile boolean coinsArrived;

    private static volatile int questCoins;

    private PostMatch() {
    }

    public static void clear() {
        current = null;
        gained = 0;
        xpArrived = false;
        beforeLevel = 0;
        beforeInto = 0;
        beforeNeed = 0;
        beforeKnown = false;
        coinParts = new Part[0];
        coinsArrived = false;
        questCoins = 0;
    }

    public static void addQuestCoins(int amount) {
        if (amount > 0) questCoins += amount;
    }

    public static int questCoins() {
        return questCoins;
    }

    public static void acceptCoins(Coins coins) {
        if (coins == null) return;
        MojangAuth.noteCoins(coins.balance);
        if (!Boolean.TRUE.equals(coins.race)) return;

        java.util.List<Part> kept = new java.util.ArrayList<>();
        if (coins.parts != null) {
            for (Part part : coins.parts) {
                if (part != null && part.amount != null && part.amount > 0) kept.add(part);
            }
        }
        coinParts = kept.toArray(new Part[0]);
        coinsArrived = true;
    }

    public static Part[] coinParts() {
        Part[] parts = coinParts;
        int quests = questCoins;
        if (quests <= 0) return parts;
        Part quest = new Part();
        quest.reason = "quest";
        quest.amount = quests;
        Part[] out = java.util.Arrays.copyOf(parts, parts.length + 1);
        out[parts.length] = quest;
        return out;
    }

    public static boolean coinsArrived() {
        return coinsArrived;
    }

    public static void accept(MojangAuth.Profile profile, Integer delta) {
        if (profile == null) return;

        current = new Standing(profile, delta);

        MojangAuth.updateProfile(profile);
        RankedApi.noteProfile(profile);
    }

    public static void acceptXp(int total, int level, int into, int need, int paid) {
        gained = Math.max(0, paid);
        xpArrived = true;

        MojangAuth.Profile profile = MojangAuth.knownProfile();
        if (profile == null || level <= 0) return;

        if (!beforeKnown) {
            beforeLevel = Math.max(1, profile.level);
            beforeInto = profile.xpInto;
            beforeNeed = profile.xpNext;
            beforeKnown = true;
        }

        profile.xp = total;
        profile.level = level;
        profile.xpInto = into;
        profile.xpNext = need;
    }

    public static void acceptMissed(MojangAuth.Profile profile, Integer delta, RelayConnection.Xp xp,
                                    int levelBefore, int intoBefore, int needBefore) {
        clear();
        if (profile != null) accept(profile, delta);

        if (xp == null) {
            xpArrived = true;
            return;
        }

        if (levelBefore > 0) {
            beforeLevel = levelBefore;
            beforeInto = Math.max(0, intoBefore);
            beforeNeed = Math.max(0, needBefore);
            beforeKnown = true;
        }
        acceptXp(xp.total, xp.level, xp.into, xp.need, xp.gained);
    }

    public static int levelBefore() {
        return beforeKnown ? beforeLevel : 0;
    }

    public static int intoBefore() {
        return beforeKnown ? beforeInto : 0;
    }

    public static int costBefore() {
        return beforeKnown ? beforeNeed : 0;
    }

    public static int xpGained() {
        return gained;
    }

    public static boolean xpArrived() {
        return xpArrived;
    }

    public static Standing current() {
        return current;
    }

    public static boolean hasArrived() {
        return current != null;
    }
}
