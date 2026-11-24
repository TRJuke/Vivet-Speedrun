package com.vivet.speedrun.p2p;

import com.vivet.speedrun.VivetSpeedrun;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;

public final class CollectionBook {
    private static volatile Map<String, RankedApi.Found> entries = Map.of();

    private static volatile boolean known;

    private static volatile long at;
    private static final AtomicBoolean FETCHING = new AtomicBoolean();

    private static final long TTL_MS = 300_000L;
    private static final long AWAY_TTL_MS = 1_800_000L;

    private static final long RETRY_MS = 30_000L;

    private CollectionBook() {
    }

    public static boolean known() {
        return known;
    }

    public static long stamp() {
        return at;
    }

    public static RankedApi.Found get(String key) {
        return key == null ? null : entries.get(key);
    }

    public static int found() {
        int total = 0;
        for (RankedApi.Found entry : entries.values()) {
            if (entry != null && entry.finishes != null && entry.finishes > 0) total++;
        }
        return total;
    }

    public static void stale() {
        at = 0L;
    }

    public static void refresh() {
        long now = System.currentTimeMillis();

        long wait = known ? Attention.interval(TTL_MS, AWAY_TTL_MS) : RETRY_MS;
        if (now - at < wait) return;
        if (MojangAuth.token() == null) return;
        if (!FETCHING.compareAndSet(false, true)) return;

        RankedApi.collection(
                answer -> {
                    if (answer != null && answer.error == null && answer.entries != null) {
                        Map<String, RankedApi.Found> built = new ConcurrentHashMap<>();
                        for (RankedApi.Found row : answer.entries) {
                            if (row != null && row.id != null) built.put(row.id, row);
                        }
                        entries = built;
                        known = true;
                    }

                    at = System.currentTimeMillis();
                    FETCHING.set(false);
                },
                reason -> {
                    VivetSpeedrun.LOGGER.debug("Could not load collection: {}", reason);
                    at = System.currentTimeMillis();
                    FETCHING.set(false);
                });
    }
}
