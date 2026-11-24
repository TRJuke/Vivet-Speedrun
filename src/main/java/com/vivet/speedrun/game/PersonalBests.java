package com.vivet.speedrun.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.vivet.speedrun.VivetSpeedrun;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PersonalBests {
    public static final class Entry {
        public int best;

        public int bestCentis;

        public long bestAt;

        public int attempts;

        public int finishes;

        public int wins;
        public int losses;
        public int draws;

        public int streak;
        public int bestStreak;

        public int lastSeconds;
        public int lastCentis;
        public long lastAt;

        public boolean hasBest() {
            return this.bestCentis > 0 || this.best > 0 || this.bestAt > 0;
        }

        public int bestCentis() {
            return this.bestCentis > 0
                    ? this.bestCentis
                    : com.vivet.speedrun.game.Time.toCentis(this.best);
        }
    }

    private static final class Store {
        Map<String, Entry> entries = new LinkedHashMap<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = Paths.get("config", "vivet_speedrun_records.json");

    private static Store store = new Store();
    private static boolean loaded;

    private PersonalBests() {
    }

    private static String scope(RunMode mode, boolean setSeed) {
        return setSeed ? mode.id() + ":set" : mode.id();
    }

    private static String itemScope(RunMode mode, boolean setSeed) {
        return sharesBest(mode) ? SHARED : scope(mode, setSeed);
    }

    private static boolean sharesBest(RunMode mode) {
        return mode == RunMode.RANKED_SOLO || mode == RunMode.RANKED_MATCH || mode == RunMode.PUBLIC;
    }

    private static final String SHARED = "counted";

    private static final String FOLDED = SHARED + "|folded";

    public static String itemKey(RunMode mode, boolean setSeed, String itemId) {
        return itemScope(mode, setSeed) + "|item|" + (itemId == null ? "?" : itemId);
    }

    public static String bandKey(RunMode mode, boolean setSeed, Difficulty band) {
        return scope(mode, setSeed) + "|band|" + (band == null ? "medium" : band.id());
    }

    public static String modeKey(RunMode mode, boolean setSeed) {
        return scope(mode, setSeed) + "|mode";
    }

    public static Entry get(String key) {
        ensureLoaded();
        return store.entries.get(key);
    }

    public static Entry forItem(RunMode mode, boolean setSeed, String itemId) {
        return get(itemKey(mode, setSeed, itemId));
    }

    public static Entry forBand(RunMode mode, boolean setSeed, Difficulty band) {
        return get(bandKey(mode, setSeed, band));
    }

    public static Entry forMode(RunMode mode, boolean setSeed) {
        return get(modeKey(mode, setSeed));
    }

    public static int bestOf(String key) {
        Entry entry = get(key);
        return entry == null || !entry.hasBest() ? -1 : entry.best;
    }

    public static int bestCentisOf(String key) {
        Entry entry = get(key);
        return entry == null || !entry.hasBest() ? -1 : entry.bestCentis();
    }

    public static boolean noteFinished(RunMode mode, boolean setSeed, String itemId,
                                       Difficulty band, int seconds, int centis, boolean found,
                                       RunRecord.Outcome outcome) {
        ensureLoaded();

        boolean improved = false;
        for (String key : new String[]{
                itemKey(mode, setSeed, itemId),
                bandKey(mode, setSeed, band),
                modeKey(mode, setSeed)}) {
            Entry entry = entry(key);

            entry.attempts++;

            if (found && (centis > 0 || mode == RunMode.SOLO)) {
                entry.finishes++;
                entry.lastSeconds = seconds;
                entry.lastCentis = centis;
                entry.lastAt = System.currentTimeMillis();

                if (!entry.hasBest() || centis < entry.bestCentis()) {
                    entry.best = seconds;
                    entry.bestCentis = centis;
                    entry.bestAt = System.currentTimeMillis();

                    if (key.contains("|item|")) improved = true;
                }
            }

            switch (outcome) {
                case WON -> {
                    entry.wins++;
                    entry.streak++;
                    entry.bestStreak = Math.max(entry.bestStreak, entry.streak);
                }
                case DRAW -> {
                    entry.draws++;
                    entry.streak = 0;
                }
                default -> {
                    entry.losses++;
                    entry.streak = 0;
                }
            }
        }

        save();
        return improved;
    }

    public static boolean adoptBest(RunMode mode, boolean setSeed, String itemId, int centis) {
        if (centis <= 0 || itemId == null || itemId.isEmpty() || !sharesBest(mode)) return false;
        ensureLoaded();

        Entry entry = entry(itemKey(mode, setSeed, itemId));
        if (entry.hasBest() && entry.bestCentis() <= centis) return false;

        entry.best = Time.toSeconds(centis);
        entry.bestCentis = centis;
        entry.bestAt = System.currentTimeMillis();
        save();
        return true;
    }

    private static Entry entry(String key) {
        return store.entries.computeIfAbsent(key, ignored -> new Entry());
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        load();
    }

    public static void load() {
        loaded = true;

        if (!Files.exists(PATH)) {
            store = new Store();
            return;
        }

        try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
            Store read = GSON.fromJson(reader, Store.class);
            store = read == null || read.entries == null ? new Store() : read;
        } catch (Exception e) {
            VivetSpeedrun.LOGGER.error("Could not read personal records", e);
            store = new Store();
            return;
        }

        if (!store.entries.containsKey(FOLDED)) {
            fold();
            save();
        }
    }

    private static void fold() {
        Map<String, Entry> shared = new LinkedHashMap<>();

        for (Map.Entry<String, Entry> held : store.entries.entrySet()) {
            String key = held.getKey();
            Entry old = held.getValue();
            int bar = key.indexOf("|item|");
            if (old == null || bar < 0) continue;

            String scope = key.substring(0, bar);
            if (scope.endsWith(":set")) scope = scope.substring(0, scope.length() - 4);
            if (!scope.equals(RunMode.RANKED_SOLO.id()) && !scope.equals(RunMode.RANKED_MATCH.id())
                    && !scope.equals(RunMode.PUBLIC.id())) continue;

            Entry into = shared.computeIfAbsent(SHARED + key.substring(bar), ignored -> new Entry());
            into.attempts += old.attempts;
            into.finishes += old.finishes;
            into.wins += old.wins;
            into.losses += old.losses;
            into.draws += old.draws;
            into.bestStreak = Math.max(into.bestStreak, old.bestStreak);

            if (old.hasBest() && (!into.hasBest() || old.bestCentis() < into.bestCentis())) {
                into.best = old.best;
                into.bestCentis = old.bestCentis();
                into.bestAt = old.bestAt;
            }
            if (old.lastAt > into.lastAt) {
                into.lastAt = old.lastAt;
                into.lastSeconds = old.lastSeconds;
                into.lastCentis = old.lastCentis;
                into.streak = old.streak;
            }
        }

        for (Map.Entry<String, Entry> made : shared.entrySet()) {
            store.entries.putIfAbsent(made.getKey(), made.getValue());
        }
        store.entries.put(FOLDED, new Entry());
    }

    public static int bestsIn(Path file) {
        Store theirs = readStore(file);
        int count = 0;
        for (Entry entry : theirs.entries.values()) {
            if (entry != null && entry.hasBest()) count++;
        }
        return count;
    }

    public static int adopt(Path file) {
        ensureLoaded();
        Store theirs = readStore(file);
        int changed = 0;

        for (Map.Entry<String, Entry> held : theirs.entries.entrySet()) {
            Entry old = held.getValue();
            if (old == null) continue;

            Entry mine = store.entries.get(held.getKey());
            if (mine == null) {
                store.entries.put(held.getKey(), old);
                changed++;
                continue;
            }

            boolean moved = false;
            if (old.hasBest() && (!mine.hasBest() || old.bestCentis() < mine.bestCentis())) {
                mine.best = old.best;
                mine.bestCentis = old.bestCentis();
                mine.bestAt = old.bestAt;
                moved = true;
            }
            if (old.lastAt > mine.lastAt) {
                mine.lastAt = old.lastAt;
                mine.lastSeconds = old.lastSeconds;
                mine.lastCentis = old.lastCentis;
                mine.streak = old.streak;
                moved = true;
            }
            int before = mine.attempts + mine.finishes + mine.wins + mine.losses + mine.draws + mine.bestStreak;
            mine.attempts = Math.max(mine.attempts, old.attempts);
            mine.finishes = Math.max(mine.finishes, old.finishes);
            mine.wins = Math.max(mine.wins, old.wins);
            mine.losses = Math.max(mine.losses, old.losses);
            mine.draws = Math.max(mine.draws, old.draws);
            mine.bestStreak = Math.max(mine.bestStreak, old.bestStreak);
            if (moved || before != mine.attempts + mine.finishes + mine.wins + mine.losses + mine.draws
                    + mine.bestStreak) {
                changed++;
            }
        }

        if (changed > 0) save();
        return changed;
    }

    private static Store readStore(Path file) {
        if (file == null || !Files.exists(file)) return new Store();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Store read = GSON.fromJson(reader, Store.class);
            return read == null || read.entries == null ? new Store() : read;
        } catch (Exception e) {
            VivetSpeedrun.LOGGER.warn("Could not read records from another install", e);
            return new Store();
        }
    }

    public static void save() {
        try {
            Path parent = PATH.getParent();
            if (parent != null) Files.createDirectories(parent);

            try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(store, writer);
            }
        } catch (Exception e) {
            VivetSpeedrun.LOGGER.error("Could not save personal records", e);
        }
    }
}
