package com.vivet.speedrun.p2p;

import com.google.gson.Gson;
import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.config.ConfigManager;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Wardrobe {
    private static final Gson GSON = new Gson();

    public static final class Item {
        public String id;
        public String set;
        public String kind;
        public String rarity;
        public String obtained;

        public String name;
        public String tip;

        public Integer need;

        public String achievement;

        public Integer price;
    }

    public static final class Set {
        public String id;
        public String name;
        public String rarity;
    }

    public static final class Slot {
        public String id;
        public String kind;
    }

    public static final class Catalogue {
        public List<Slot> slots;
        public List<Set> sets;
        public List<Item> cosmetics;
    }

    public static final class Owned {
        public String id;
        public String source;
        public Long at;
    }

    public static final class Worn {
        public String slot;
        public String id;
        public String mode;
    }

    public static final class Locker {
        public Catalogue catalogue;
        public String uuid;
        public List<Owned> owned;
        public List<Worn> equipped;
        public List<String> favourites;
        public Boolean rankedLook;
        public String error;
    }

    private static volatile Locker locker;
    private static volatile boolean known;
    private static volatile long at;

    private static final AtomicBoolean FETCHING = new AtomicBoolean();

    private static final long TTL_MS = 300_000L;
    private static final long AWAY_TTL_MS = 1_800_000L;

    private static final long RETRY_MS = 30_000L;

    private static final java.time.Duration TIMEOUT = java.time.Duration.ofSeconds(15);

    private Wardrobe() {
    }

    public abstract static class View {
        public abstract Locker locker();

        public abstract boolean known();

        public abstract long stamp();

        public boolean mine() {
            return false;
        }

        public List<Item> catalogue() {
            Locker held = locker();
            return held == null || held.catalogue == null || held.catalogue.cosmetics == null
                    ? List.of() : held.catalogue.cosmetics;
        }

        public List<Slot> slots() {
            Locker held = locker();
            return held == null || held.catalogue == null || held.catalogue.slots == null
                    ? List.of() : held.catalogue.slots;
        }

        public Item item(String id) {
            if (id == null) return null;
            for (Item entry : catalogue()) {
                if (id.equals(entry.id)) return entry;
            }
            return null;
        }

        public String setName(String setId) {
            Locker held = locker();
            if (held != null && held.catalogue != null && held.catalogue.sets != null) {
                for (Set entry : held.catalogue.sets) {
                    if (entry != null && setId != null && setId.equals(entry.id)) return entry.name;
                }
            }
            return setId == null ? "" : setId;
        }

        public boolean owns(String id) {
            Locker held = locker();
            if (held == null || held.owned == null || id == null) return false;
            for (Owned row : held.owned) {
                if (row != null && id.equals(row.id)) return true;
            }
            return false;
        }

        public boolean isFavourite(String id) {
            Locker held = locker();
            return held != null && held.favourites != null && id != null
                    && held.favourites.contains(id);
        }

        public String inSlot(String slot) {
            Worn worn = wornIn(slot);
            return worn == null || worn.id == null ? "" : worn.id;
        }

        public String modeOf(String slot) {
            Worn worn = wornIn(slot);
            return worn == null || worn.mode == null ? "fixed" : worn.mode;
        }

        private Worn wornIn(String slot) {
            Locker held = locker();
            if (held == null || held.equipped == null || slot == null) return null;
            for (Worn worn : held.equipped) {
                if (worn != null && slot.equals(worn.slot)) return worn;
            }
            return null;
        }

        public int ownedCount() {
            Locker held = locker();
            return held == null || held.owned == null ? 0 : held.owned.size();
        }

        private volatile List<String> setOrder = List.of();
        private volatile Locker setOrderFrom;

        public List<String> setsInOrder() {
            Locker current = locker();
            if (current == this.setOrderFrom) return this.setOrder;

            List<String> order = new ArrayList<>();
            java.util.Set<String> seen = new HashSet<>();

            for (Item entry : catalogue()) {
                if (entry != null && entry.set != null && seen.add(entry.set)) order.add(entry.set);
            }

            this.setOrder = List.copyOf(order);
            this.setOrderFrom = current;
            return this.setOrder;
        }

        public boolean ownsWholeSet(String setId) {
            boolean any = false;
            for (Item entry : catalogue()) {
                if (entry == null || !java.util.Objects.equals(entry.set, setId)) continue;
                any = true;
                if (!owns(entry.id)) return false;
            }
            return any;
        }
    }

    private static final View MINE = new View() {
        @Override
        public Locker locker() {
            return Wardrobe.locker;
        }

        @Override
        public boolean known() {
            return Wardrobe.known;
        }

        @Override
        public long stamp() {
            return Wardrobe.at;
        }

        @Override
        public boolean mine() {
            return true;
        }
    };

    public static View mine() {
        return MINE;
    }

    public static boolean known() {
        return MINE.known();
    }

    public static List<Item> catalogue() {
        return MINE.catalogue();
    }

    public static Item item(String id) {
        return MINE.item(id);
    }

    public static String setName(String setId) {
        return MINE.setName(setId);
    }

    public static boolean owns(String id) {
        return MINE.owns(id);
    }

    public static boolean isFavourite(String id) {
        return MINE.isFavourite(id);
    }

    public static String inSlot(String slot) {
        return MINE.inSlot(slot);
    }

    public static int ownedCount() {
        return MINE.ownedCount();
    }

    public static List<String> setsInOrder() {
        return MINE.setsInOrder();
    }

    public static String previewName() {
        MojangAuth.Profile profile = MojangAuth.knownProfile();
        if (profile != null && profile.name != null && !profile.name.isBlank()) {
            return profile.name;
        }
        return "Vivet";
    }

    public static void stale() {
        at = 0L;
    }

    public static void refresh() {
        long now = System.currentTimeMillis();

        long wait = known ? Attention.interval(TTL_MS, AWAY_TTL_MS) : RETRY_MS;
        if (now - at < wait) return;

        String token = MojangAuth.token();
        if (token == null) return;
        if (!FETCHING.compareAndSet(false, true)) return;

        String url = MojangAuth.httpBase(ConfigManager.get().relayUrl)
                + "/cosmetics?t=" + encode(token);

        MojangAuth.get(url)
                .thenApply(body -> GSON.fromJson(body, Locker.class))
                .whenComplete((answer, error) -> {
                    if (error == null && answer != null && answer.error == null) {
                        locker = answer;
                        known = true;
                    } else {
                        VivetSpeedrun.LOGGER.debug("Could not load locker: {}",
                                error == null ? "empty response" : error.toString());
                    }

                    at = System.currentTimeMillis();
                    FETCHING.set(false);
                });
    }

    public static void wear(Map<String, Object> what) {
        String token = MojangAuth.token();
        if (token == null) return;

        Map<String, Object> body = new HashMap<>(what);
        body.put("token", token);

        String url = MojangAuth.httpBase(ConfigManager.get().relayUrl) + "/cosmetics/wear";

        HttpRequest request = MojangAuth.request(url)
                .timeout(TIMEOUT)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .build();

        MojangAuth.http().sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .whenComplete((answer, error) -> {
                    if (error != null || answer == null) {
                        VivetSpeedrun.LOGGER.debug("Could not change slot",
                                error);
                        return;
                    }

                    try {
                        Locker fresh = GSON.fromJson(answer, Locker.class);

                        Locker previous = locker;
                        if (fresh != null && fresh.error == null) {
                            if (fresh.catalogue == null && previous != null) {
                                fresh.catalogue = previous.catalogue;
                            }
                            locker = fresh;
                            known = true;
                            at = System.currentTimeMillis();
                        }
                    } catch (Exception ignored) {
                    }
                });
    }

    public static void equip(String slot, String id, String mode) {
        wear(Map.of("action", "equip", "slot", slot, "id", id == null ? "" : id,
                    "mode", mode == null ? "fixed" : mode));
    }

    public static void equipRank(String slot) {
        wear(Map.of("action", "equip", "slot", slot, "id", "", "mode", "rank"));
    }

    public static void equipSet(String setId) {
        wear(Map.of("action", "equipSet", "set", setId));
    }

    public static void favourite(String id, boolean on) {
        wear(Map.of("action", "favourite", "id", id, "on", on));
    }

    public static List<Item> forSale() {
        List<Item> out = new ArrayList<>();
        for (Item entry : catalogue()) {
            if (entry != null && "shop".equals(entry.obtained) && entry.price != null) {
                out.add(entry);
            }
        }
        return out;
    }

    private static final class Bought {
        Boolean ok;
        String error;
        Integer balance;
        List<Owned> owned;
        List<Worn> equipped;
        List<String> favourites;
    }

    private static volatile String buying;

    private static volatile String refusedId;
    private static volatile String refusal;

    public static String buying() {
        return buying;
    }

    public static String refusalFor(String id) {
        return id != null && id.equals(refusedId) ? refusal : null;
    }

    public static boolean buy(String id) {
        String token = MojangAuth.token();
        if (token == null || id == null || buying != null) return false;

        buying = id;
        refusedId = null;
        refusal = null;

        String url = MojangAuth.httpBase(ConfigManager.get().relayUrl) + "/shop/buy";
        HttpRequest request = MojangAuth.request(url)
                .timeout(TIMEOUT)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        GSON.toJson(Map.of("token", token, "id", id))))
                .build();

        MojangAuth.http().sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .whenComplete((answer, error) -> {
                    Bought bought = null;
                    try {
                        bought = answer == null ? null : GSON.fromJson(answer, Bought.class);
                    } catch (Exception ignored) {
                    }

                    if (bought != null && Boolean.TRUE.equals(bought.ok)) {
                        MojangAuth.noteCoins(bought.balance);

                        Locker previous = locker;
                        Locker fresh = new Locker();
                        fresh.catalogue = previous == null ? null : previous.catalogue;
                        fresh.uuid = previous == null ? null : previous.uuid;
                        fresh.owned = bought.owned;
                        fresh.equipped = bought.equipped;
                        fresh.favourites = bought.favourites;
                        locker = fresh;
                        known = true;
                        at = System.currentTimeMillis();
                    } else {
                        if (bought != null && bought.balance != null) {
                            MojangAuth.noteCoins(bought.balance);
                        }
                        refusedId = id;
                        refusal = bought == null || bought.error == null ? "" : bought.error;
                        VivetSpeedrun.LOGGER.debug("Purchase refused: {}",
                                bought == null ? error : bought.error);
                    }
                    buying = null;
                });
        return true;
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
