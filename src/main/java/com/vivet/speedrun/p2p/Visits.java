package com.vivet.speedrun.p2p;

import com.google.gson.Gson;
import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.config.ConfigManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Visits {
    private static final Gson GSON = new Gson();

    private static final long TTL_MS = 300_000L;

    private static final long RETRY_MS = 30_000L;

    private static final int MAX_REMEMBERED = 32;

    private static final Map<String, Look> LOOKS = new ConcurrentHashMap<>();
    private static final Map<String, Book> BOOKS = new ConcurrentHashMap<>();

    private Visits() {
    }

    public static Look locker(String uuid) {
        String id = normalise(uuid);
        if (id == null) return EMPTY_LOOK;

        Look look = LOOKS.computeIfAbsent(id, Look::new);
        look.ask();
        return look;
    }

    public static Book collection(String uuid) {
        String id = normalise(uuid);
        if (id == null) return EMPTY_BOOK;

        Book book = BOOKS.computeIfAbsent(id, Book::new);
        book.ask();
        return book;
    }

    public static void forget() {
        LOOKS.clear();
        BOOKS.clear();
    }

    public static final class Look extends Wardrobe.View {
        private final String uuid;
        private final Asking asking = new Asking();

        private volatile Wardrobe.Locker answer;

        private Look(String uuid) {
            this.uuid = uuid;
        }

        @Override
        public Wardrobe.Locker locker() {
            return this.answer;
        }

        @Override
        public boolean known() {
            return this.answer != null;
        }

        @Override
        public long stamp() {
            return this.asking.stamp;
        }

        public boolean refused() {
            return this.asking.refused;
        }

        private void ask() {
            if (this.uuid == null || !this.asking.begin(known())) return;

            String url = base() + "/cosmetics?uuid=" + this.uuid + "&t=" + encode(token());

            MojangAuth.get(url)
                    .thenApply(body -> GSON.fromJson(body, Wardrobe.Locker.class))
                    .whenComplete((fresh, error) -> {
                        if (error == null && fresh != null && fresh.error == null) {
                            this.answer = fresh;
                            this.asking.done(false);
                            return;
                        }

                        this.asking.done(error == null && fresh != null && fresh.error != null);

                        VivetSpeedrun.LOGGER.debug("Could not read a locker: {}",
                                error == null ? "refused" : error.toString());
                    });
        }
    }

    private static final Look EMPTY_LOOK = new Look(null);

    public static final class Book {
        private final String uuid;
        private final Asking asking = new Asking();

        private volatile Map<String, RankedApi.Found> entries;

        private Book(String uuid) {
            this.uuid = uuid;
        }

        public RankedApi.Found get(String key) {
            Map<String, RankedApi.Found> held = this.entries;
            return held == null || key == null ? null : held.get(key);
        }

        public boolean known() {
            return this.entries != null;
        }

        public long stamp() {
            return this.asking.stamp;
        }

        public boolean refused() {
            return this.asking.refused;
        }

        private void ask() {
            if (this.uuid == null || !this.asking.begin(known())) return;

            String url = base() + "/collection?uuid=" + this.uuid + "&t=" + encode(token());

            MojangAuth.get(url)
                    .thenApply(body -> GSON.fromJson(body, RankedApi.Collected.class))
                    .whenComplete((fresh, error) -> {
                        if (error == null && fresh != null && fresh.error == null
                                && fresh.entries != null) {
                            Map<String, RankedApi.Found> built = new ConcurrentHashMap<>();
                            for (RankedApi.Found row : fresh.entries) {
                                if (row != null && row.id != null) built.put(row.id, row);
                            }
                            this.entries = built;
                            this.asking.done(false);
                            return;
                        }

                        this.asking.done(error == null && fresh != null && fresh.error != null);

                        VivetSpeedrun.LOGGER.debug("Could not read a collection: {}",
                                error == null ? "refused" : error.toString());
                    });
        }
    }

    private static final Book EMPTY_BOOK = new Book(null);

    private static final class Asking {
        private final AtomicBoolean fetching = new AtomicBoolean();

        private volatile long at;
        private volatile long stamp;
        private volatile boolean refused;

        private boolean begin(boolean known) {
            if (token() == null) return false;

            long now = System.currentTimeMillis();
            long wait = known ? TTL_MS : RETRY_MS;

            if (this.at != 0L && now - this.at < (this.refused ? TTL_MS : wait)) return false;

            return this.fetching.compareAndSet(false, true);
        }

        private void done(boolean refused) {
            this.refused = refused;

            this.at = System.currentTimeMillis();
            this.stamp = this.at;
            this.fetching.set(false);
        }
    }

    private static String base() {
        return MojangAuth.httpBase(ConfigManager.get().relayUrl);
    }

    private static String token() {
        return MojangAuth.token();
    }

    private static String normalise(String uuid) {
        if (uuid == null) return null;

        String id = uuid.replace("-", "").trim().toLowerCase(java.util.Locale.ROOT);
        if (!id.matches("[0-9a-f]{32}")) return null;

        if (LOOKS.size() > MAX_REMEMBERED) LOOKS.clear();
        if (BOOKS.size() > MAX_REMEMBERED) BOOKS.clear();

        return id;
    }

    private static String encode(String value) {
        return value == null ? ""
                : java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
