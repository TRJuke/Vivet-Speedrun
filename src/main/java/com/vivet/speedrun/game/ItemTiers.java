package com.vivet.speedrun.game;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vivet.speedrun.VivetSpeedrun;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class ItemTiers {
    private static final String DATA_PATH = "/vivet_speedrun/tiers.json";

    private static final String DIMENSIONS_PATH = "/vivet_speedrun/dimensions.json";

    private static final int LINGERING_SHIFT = 2;

    private static Map<String, Difficulty> tiers = Map.of();
    private static Map<String, String> families = Map.of();
    private static Set<String> blacklist = Set.of();

    private static Map<String, Set<String>> dimensions = Map.of();

    private static Map<String, Difficulty> netherTiers = Map.of();

    private static String dataHash = "none";
    private static boolean loaded;

    private ItemTiers() {
    }

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;

        try (InputStream in = ItemTiers.class.getResourceAsStream(DATA_PATH)) {
            if (in == null) {
                VivetSpeedrun.LOGGER.error("{} is missing, every item defaults to Medium", DATA_PATH);
                return;
            }

            byte[] raw = in.readAllBytes();

            JsonObject root = JsonParser
                    .parseString(new String(raw, StandardCharsets.UTF_8))
                    .getAsJsonObject();

            Map<String, Difficulty> t = new LinkedHashMap<>();
            for (var e : root.getAsJsonObject("items").entrySet()) {
                t.put(e.getKey(), Difficulty.byId(e.getValue().getAsString()));
            }
            Map<String, String> f = new LinkedHashMap<>();
            for (var e : root.getAsJsonObject("families").entrySet()) {
                f.put(e.getKey(), e.getValue().getAsString());
            }
            Set<String> b = new LinkedHashSet<>();
            root.getAsJsonArray("blacklist").forEach(x -> b.add(x.getAsString()));

            tiers = t;
            families = f;
            blacklist = b;

            byte[] rawDimensions = loadDimensions();

            byte[] rawGates = (SeedDifficulty.tableFingerprint() + tuningFingerprint())
                    .getBytes(StandardCharsets.UTF_8);

            byte[] combined = new byte[raw.length + rawDimensions.length + rawGates.length];
            System.arraycopy(raw, 0, combined, 0, raw.length);
            System.arraycopy(rawDimensions, 0, combined, raw.length, rawDimensions.length);
            System.arraycopy(rawGates, 0, combined, raw.length + rawDimensions.length,
                    rawGates.length);
            dataHash = shortHash(combined);

            VivetSpeedrun.LOGGER.info("Loaded {} items in {} families ({} blacklisted, {} in the Nether), ratings {}",
                    tiers.size(), Set.copyOf(families.values()).size(), blacklist.size(),
                    dimensionKeys(DimensionPool.NETHER).size(), dataHash);
        } catch (Exception e) {
            VivetSpeedrun.LOGGER.error("Could not read {}", DATA_PATH, e);
        }
    }

    private static byte[] loadDimensions() {
        try (InputStream in = ItemTiers.class.getResourceAsStream(DIMENSIONS_PATH)) {
            if (in == null) {
                VivetSpeedrun.LOGGER.error("{} is missing, dimension modes will be empty",
                        DIMENSIONS_PATH);
                return new byte[0];
            }

            byte[] raw = in.readAllBytes();
            JsonObject root = JsonParser
                    .parseString(new String(raw, StandardCharsets.UTF_8))
                    .getAsJsonObject();

            Map<String, Set<String>> byMode = new LinkedHashMap<>();
            for (DimensionPool pool : DimensionPool.values()) {
                if (pool == DimensionPool.ANY) continue;

                Set<String> allowed = new LinkedHashSet<>();
                if (root.has(pool.id())) {
                    root.getAsJsonArray(pool.id()).forEach(x -> allowed.add(x.getAsString()));
                }
                byMode.put(pool.id(), allowed);
            }

            dimensions = byMode;

            Map<String, Difficulty> netherBands = new LinkedHashMap<>();
            if (root.has("netherTiers")) {
                root.getAsJsonObject("netherTiers").entrySet().forEach(entry ->
                        netherBands.put(entry.getKey(),
                                Difficulty.byId(entry.getValue().getAsString())));
            }
            netherTiers = netherBands;

            return raw;
        } catch (Exception e) {
            VivetSpeedrun.LOGGER.error("Could not read {}", DIMENSIONS_PATH, e);
            return new byte[0];
        }
    }

    private static Set<String> dimensionKeys(DimensionPool pool) {
        return dimensions.getOrDefault(pool.id(), Set.of());
    }

    public static boolean allowedIn(DimensionPool pool, Target target) {
        load();
        if (pool == null || pool == DimensionPool.ANY) return true;

        if (pool == DimensionPool.NETHER && target.item() == Items.LINGERING_POTION) return false;

        return dimensionKeys(pool).contains(ratingKey(target));
    }

    public static String dataHash() {
        load();
        return dataHash;
    }

    private static String shortHash(byte[] raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw);
            StringBuilder hex = new StringBuilder(16);
            for (int i = 0; i < 8; i++) hex.append(String.format("%02x", digest[i]));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return "none";
        }
    }

    public static String ratingKey(Target target) {
        if (target.potion() != null) return "potion_of_" + target.potion();
        return idOf(target.item());
    }

    public record Entry(String key, Difficulty difficulty, Target target) {
    }

    public static synchronized List<Entry> collection() {
        load();
        if (collection != null) return collection;

        List<Entry> built = new ArrayList<>(tiers.size());
        for (Map.Entry<String, Difficulty> rated : tiers.entrySet()) {
            if (blacklist.contains(rated.getKey())) continue;

            Target target = targetForRating(rated.getKey());
            if (target == null) continue;

            built.add(new Entry(rated.getKey(), rated.getValue(), target));
        }

        built.sort(Comparator
                .comparingInt((Entry e) -> e.difficulty().ordinal())
                .thenComparing(Entry::key));

        collection = List.copyOf(built);
        return collection;
    }

    public static List<Target> catalogue(Difficulty band) {
        List<Target> out = new ArrayList<>();
        for (Entry entry : collection()) {
            if (entry.difficulty() == band) out.add(entry.target());
        }
        return out;
    }

    private static List<Entry> collection;

    public static Target targetForRating(String key) {
        if (key == null || key.isEmpty()) return null;

        if (key.startsWith("potion_of_")) {
            return new Target(Items.POTION, key.substring("potion_of_".length()));
        }
        return Target.parse(key);
    }

    public static boolean isObtainable(String ratingKey) {
        load();
        return tiers.containsKey(ratingKey) && !blacklist.contains(ratingKey);
    }

    public static Difficulty classify(Target target) {
        load();
        Difficulty base = tiers.getOrDefault(ratingKey(target), Difficulty.MEDIUM);
        if (target.item() != Items.LINGERING_POTION) return base;

        int lingering = Math.min(base.ordinal() + LINGERING_SHIFT, Difficulty.values().length - 1);
        return Difficulty.values()[lingering];
    }

    public static Difficulty classify(Target target, DimensionPool dimension) {
        if (dimension != DimensionPool.NETHER) return classify(target);

        load();
        Difficulty band = netherTiers.get(ratingKey(target));
        return band == null ? classify(target) : band;
    }

    public static String familyOf(Target target) {
        load();
        String key = ratingKey(target);
        return families.getOrDefault(key, key);
    }

    public static String gateNote(Target target) {
        load();
        String key = ratingKey(target);
        return SeedDifficulty.explain(key, families.getOrDefault(key, key));
    }

    public static List<Target> pool(Difficulty difficulty, boolean includeEasier,
                                    DimensionPool dimension, Collection<String> excludedIds) {
        load();
        Set<String> excluded = excludedIds == null ? Set.of() : new LinkedHashSet<>(excludedIds);
        List<Target> pool = new ArrayList<>();

        for (Item item : BuiltInRegistries.ITEM) {
            if (Target.POTION_ITEMS.contains(item)) continue;
            String id = idOf(item);
            if (id == null || !isObtainable(id) || excluded.contains(id)) continue;

            Target target = Target.of(item);
            if (!allowedIn(dimension, target)) continue;

            Difficulty tier = classify(target, dimension);
            if (!passesGate(target, tier, dimension)) continue;
            if (fits(tier, difficulty, includeEasier)) pool.add(target);
        }

        for (Item bottle : List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION)) {
            for (String key : tiers.keySet()) {
                if (!key.startsWith("potion_of_") || blacklist.contains(key)) continue;
                Target target = new Target(bottle, key.substring("potion_of_".length()));
                if (excluded.contains(target.id())) continue;
                if (!allowedIn(dimension, target)) continue;
                if (fits(classify(target, dimension), difficulty, includeEasier)) pool.add(target);
            }
        }
        return pool;
    }

    private static boolean passesGate(Target target, Difficulty tier, DimensionPool dimension) {
        if (dimension == DimensionPool.NETHER) return true;
        if (tier.ordinal() > SeedDifficulty.GATED_UP_TO.ordinal()) return true;

        String key = ratingKey(target);
        return SeedDifficulty.allows(key, families.getOrDefault(key, key));
    }

    public static List<Target> pool(Difficulty difficulty, boolean includeEasier,
                                    Collection<String> excludedIds) {
        return pool(difficulty, includeEasier, DimensionPool.ANY, excludedIds);
    }

    private static boolean fits(Difficulty tier, Difficulty band, boolean includeEasier) {
        return includeEasier ? tier.ordinal() <= band.ordinal() : tier == band;
    }

    private static final double HOME_SHARE = 0.6;

    private static final double WOOD_WEIGHT = 2.0;

    private static final Set<String> WOOD_FAMILIES = Set.of(
            "oak", "birch", "spruce", "jungle", "acacia",
            "dark_oak", "mangrove", "cherry", "pale_oak", "bamboo", "poplar");

    private static double weightOf(String family) {
        return WOOD_FAMILIES.contains(family) ? WOOD_WEIGHT : 1.0;
    }

    private static String tuningFingerprint() {
        StringBuilder out = new StringBuilder(256);
        out.append("home ").append(HOME_SHARE).append('\n');
        out.append("wood ").append(WOOD_WEIGHT);
        WOOD_FAMILIES.stream().sorted().forEach(family -> out.append(' ').append(family));
        return out.append('\n').toString();
    }

    public static Target pick(Difficulty difficulty, boolean includeEasier, DimensionPool dimension,
                              Collection<String> excludedIds, Random random) {
        List<Target> pool = pool(difficulty, includeEasier, dimension, excludedIds);
        if (pool.isEmpty()) return null;

        Map<String, Integer> sizes = new LinkedHashMap<>();
        for (Target t : pool) sizes.merge(familyOf(t), 1, Integer::sum);

        boolean favourHome = dimension != DimensionPool.NETHER
                && difficulty.ordinal() <= SeedDifficulty.GATED_UP_TO.ordinal();

        double[] weights = new double[pool.size()];
        boolean[] home = new boolean[pool.size()];
        double homeTotal = 0.0;
        double awayTotal = 0.0;

        for (int i = 0; i < pool.size(); i++) {
            Target target = pool.get(i);
            String key = ratingKey(target);
            String family = familyOf(target);
            weights[i] = weightOf(family) / sizes.get(family);

            home[i] = favourHome && SeedDifficulty.isHome(key, families.getOrDefault(key, key));
            if (home[i]) homeTotal += weights[i];
            else awayTotal += weights[i];
        }

        if (homeTotal > 0.0 && awayTotal > 0.0) {
            double homeScale = HOME_SHARE / homeTotal;
            double awayScale = (1.0 - HOME_SHARE) / awayTotal;
            for (int i = 0; i < pool.size(); i++) weights[i] *= home[i] ? homeScale : awayScale;
        }

        double total = 0.0;
        for (double weight : weights) total += weight;

        double roll = random.nextDouble() * total;
        for (int i = 0; i < pool.size(); i++) {
            roll -= weights[i];
            if (roll <= 0.0) return pool.get(i);
        }
        return pool.get(pool.size() - 1);
    }

    private static final long REROLL_STRIDE = 0x9E3779B97F4A7C15L;

    public static Target draw(Difficulty band, boolean includeEasier, DimensionPool dimension,
                              long salt, int rerollIndex) {
        List<String> seen = new ArrayList<>();
        Target last = null;

        for (int i = 0; i <= Math.max(0, rerollIndex); i++) {
            Target next = pick(band, includeEasier, dimension, seen,
                    new Random(salt + i * REROLL_STRIDE));

            if (next == null) return last;

            last = next;
            seen.add(next.id());
        }
        return last;
    }

    public static List<String> bandTargets(Difficulty band) {
        load();
        List<String> out = new ArrayList<>();

        for (Map.Entry<String, Difficulty> entry : tiers.entrySet()) {
            String key = entry.getKey();
            if (blacklist.contains(key)) continue;

            if (!key.startsWith("potion_of_")) {
                if (entry.getValue() == band) out.add(key);
                continue;
            }

            String effect = key.substring("potion_of_".length());
            if (entry.getValue() == band) {
                out.add("potion#" + effect);
                out.add("splash_potion#" + effect);
            }

            int lifted = Math.min(entry.getValue().ordinal() + LINGERING_SHIFT,
                    Difficulty.values().length - 1);
            if (Difficulty.values()[lifted] == band) out.add("lingering_potion#" + effect);
        }

        out.sort(Comparator.naturalOrder());
        return out;
    }

    public static String listFingerprint(Difficulty band) {
        int hash = 0x811c9dc5;
        for (String id : bandTargets(band)) {
            for (int i = 0; i < id.length(); i++) {
                hash = (hash ^ id.charAt(i)) * 0x01000193;
            }
            hash = (hash ^ 0x0a) * 0x01000193;
        }
        return String.format("%08x", hash);
    }

    public record PoolReport(String pool, String home, String list) {
    }

    public static PoolReport poolReport(Difficulty band, boolean includeEasier,
                                        DimensionPool dimension) {
        List<String> order = bandTargets(band);
        Map<String, Integer> index = new LinkedHashMap<>();
        for (int i = 0; i < order.size(); i++) index.put(order.get(i), i);

        boolean[] inPool = new boolean[order.size()];
        boolean[] atHome = new boolean[order.size()];

        boolean favourHome = dimension != DimensionPool.NETHER
                && band.ordinal() <= SeedDifficulty.GATED_UP_TO.ordinal();

        for (Target target : pool(band, includeEasier, dimension, Set.of())) {
            Integer at = index.get(target.id());
            if (at == null) continue;

            inPool[at] = true;
            String key = ratingKey(target);
            atHome[at] = favourHome
                    && SeedDifficulty.isHome(key, families.getOrDefault(key, key));
        }
        return new PoolReport(bits(inPool), bits(atHome), listFingerprint(band));
    }

    private static String bits(boolean[] flags) {
        byte[] bytes = new byte[(flags.length + 7) / 8];
        for (int i = 0; i < flags.length; i++) {
            if (flags[i]) bytes[i >> 3] |= (byte) (1 << (i & 7));
        }
        return java.util.Base64.getEncoder().encodeToString(bytes);
    }

    public static String idOf(Item item) {
        var key = BuiltInRegistries.ITEM.getKey(item);
        return key == null ? null : key.getPath();
    }

    public static Target byId(String id) {
        return Target.parse(id);
    }

    public static int countFor(Difficulty difficulty, boolean includeEasier,
                               DimensionPool dimension) {
        int count = 0;
        for (Entry entry : collection()) {
            Target target = entry.target();
            if (!allowedIn(dimension, target)) continue;
            if (fits(classify(target, dimension), difficulty, includeEasier)) count++;
        }
        return count;
    }
}
