package com.vivet.speedrun.game;

import com.vivet.speedrun.VivetSpeedrun;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SeedDifficulty {
    public static final int RADIUS = 256;

    public static final int NEAR = 100;
    public static final int WALK = 200;

    private static final int HORIZONTAL_STEP = 8;
    private static final int VERTICAL_STEP = 32;

    private static final int PATCH = 5;

    private static final int WINDOW = 2;

    private static final int SWEEP = 3;

    private static final int NOT_FOUND = Integer.MAX_VALUE;

    static final Difficulty GATED_UP_TO = Difficulty.MEDIUM;

    public enum Group {
        OAK("oak",
                Biomes.FOREST, Biomes.FLOWER_FOREST, Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS,
                Biomes.SWAMP, Biomes.DARK_FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST,
                Biomes.WINDSWEPT_FOREST, Biomes.WINDSWEPT_HILLS,
                Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.MEADOW, Biomes.WOODED_BADLANDS),
        BIRCH("birch",
                Biomes.BIRCH_FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST, Biomes.FOREST,
                Biomes.FLOWER_FOREST),
        TAIGA("taiga (spruce)",
                Biomes.TAIGA, Biomes.SNOWY_TAIGA, Biomes.OLD_GROWTH_PINE_TAIGA,
                Biomes.OLD_GROWTH_SPRUCE_TAIGA, Biomes.GROVE),

        BERRY_TAIGA("taiga (berries)",
                Biomes.TAIGA, Biomes.SNOWY_TAIGA, Biomes.OLD_GROWTH_PINE_TAIGA,
                Biomes.OLD_GROWTH_SPRUCE_TAIGA),
        JUNGLE("jungle",
                Biomes.JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.BAMBOO_JUNGLE),
        BAMBOO("bamboo jungle",
                Biomes.BAMBOO_JUNGLE, Biomes.JUNGLE),
        SAVANNA("savanna",
                Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU, Biomes.WINDSWEPT_SAVANNA),
        DARK_FOREST("dark forest", Biomes.DARK_FOREST),
        MANGROVE("mangrove swamp", Biomes.MANGROVE_SWAMP),
        CHERRY("cherry grove", Biomes.CHERRY_GROVE),
        PALE_GARDEN("pale garden", Biomes.PALE_GARDEN),

        GRASSY("grassy",
                Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW, Biomes.FOREST,
                Biomes.FLOWER_FOREST, Biomes.BIRCH_FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST,
                Biomes.DARK_FOREST, Biomes.TAIGA, Biomes.SNOWY_TAIGA,
                Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA,
                Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU, Biomes.WINDSWEPT_SAVANNA,
                Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_FOREST,
                Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.SWAMP, Biomes.JUNGLE,
                Biomes.SPARSE_JUNGLE, Biomes.BAMBOO_JUNGLE),

        FLOWER_FOREST("flower forest", Biomes.FLOWER_FOREST),

        PLAINS_FLOWERS("plains / flower forest",
                Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.FLOWER_FOREST, Biomes.MEADOW),

        FOREST_FLOWERS("forest / flower forest",
                Biomes.FOREST, Biomes.FLOWER_FOREST, Biomes.BIRCH_FOREST,
                Biomes.OLD_GROWTH_BIRCH_FOREST),

        PINK_SOURCES("forest / cherry grove / desert",
                Biomes.FOREST, Biomes.FLOWER_FOREST, Biomes.BIRCH_FOREST,
                Biomes.OLD_GROWTH_BIRCH_FOREST, Biomes.CHERRY_GROVE,
                Biomes.DESERT, Biomes.BADLANDS, Biomes.ERODED_BADLANDS, Biomes.WOODED_BADLANDS),

        ALLIUM("flower forest / meadow", Biomes.FLOWER_FOREST, Biomes.MEADOW),

        LEAF_LITTER("forest / dark forest / wooded badlands",
                Biomes.FOREST, Biomes.DARK_FOREST, Biomes.WOODED_BADLANDS),

        WHITE_SOURCES("flower forest / meadow / leaf litter",
                Biomes.FLOWER_FOREST, Biomes.MEADOW,
                Biomes.FOREST, Biomes.DARK_FOREST, Biomes.WOODED_BADLANDS),
        SUNFLOWER_PLAINS("sunflower plains", Biomes.SUNFLOWER_PLAINS),
        WILDFLOWERS("birch forest / meadow",
                Biomes.BIRCH_FOREST, Biomes.MEADOW, Biomes.OLD_GROWTH_BIRCH_FOREST),
        SWAMP("swamp", Biomes.SWAMP),

        LUSH_CAVES("lush caves", Biomes.LUSH_CAVES),

        WETLAND("swamps", Biomes.SWAMP, Biomes.MANGROVE_SWAMP),
        MUSHROOMS("dark forest / mushroom fields", Biomes.DARK_FOREST, Biomes.MUSHROOM_FIELDS),
        MUSHROOM_FIELDS("mushroom fields", Biomes.MUSHROOM_FIELDS),

        CACTUS("desert / badlands",
                Biomes.DESERT, Biomes.BADLANDS, Biomes.ERODED_BADLANDS, Biomes.WOODED_BADLANDS),
        RED_SAND("badlands",
                Biomes.BADLANDS, Biomes.ERODED_BADLANDS, Biomes.WOODED_BADLANDS),
        SNOWY("snowy / ice",
                Biomes.SNOWY_PLAINS, Biomes.SNOWY_TAIGA, Biomes.SNOWY_SLOPES, Biomes.SNOWY_BEACH,
                Biomes.FROZEN_PEAKS, Biomes.JAGGED_PEAKS, Biomes.GROVE, Biomes.ICE_SPIKES,
                Biomes.FROZEN_RIVER, Biomes.FROZEN_OCEAN, Biomes.DEEP_FROZEN_OCEAN),

        LIVESTOCK("livestock",
                Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW, Biomes.FOREST,
                Biomes.FLOWER_FOREST, Biomes.BIRCH_FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST,
                Biomes.DARK_FOREST, Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU,
                Biomes.WINDSWEPT_SAVANNA, Biomes.SWAMP, Biomes.WINDSWEPT_HILLS,
                Biomes.WINDSWEPT_FOREST, Biomes.WINDSWEPT_GRAVELLY_HILLS),

        BROWN_SHEEP("savanna / jungle",
                Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU, Biomes.WINDSWEPT_SAVANNA,
                Biomes.JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.BAMBOO_JUNGLE),

        CHICKEN("chickens",
                Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW, Biomes.FOREST,
                Biomes.FLOWER_FOREST, Biomes.BIRCH_FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST,
                Biomes.DARK_FOREST, Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU,
                Biomes.WINDSWEPT_SAVANNA, Biomes.SWAMP, Biomes.JUNGLE, Biomes.SPARSE_JUNGLE,
                Biomes.BAMBOO_JUNGLE),
        OCEAN("ocean",
                Biomes.OCEAN, Biomes.DEEP_OCEAN, Biomes.WARM_OCEAN, Biomes.LUKEWARM_OCEAN,
                Biomes.DEEP_LUKEWARM_OCEAN, Biomes.COLD_OCEAN, Biomes.DEEP_COLD_OCEAN,
                Biomes.FROZEN_OCEAN, Biomes.DEEP_FROZEN_OCEAN),

        SQUID("ocean / river",
                Biomes.OCEAN, Biomes.DEEP_OCEAN, Biomes.WARM_OCEAN, Biomes.LUKEWARM_OCEAN,
                Biomes.DEEP_LUKEWARM_OCEAN, Biomes.COLD_OCEAN, Biomes.DEEP_COLD_OCEAN,
                Biomes.FROZEN_OCEAN, Biomes.DEEP_FROZEN_OCEAN, Biomes.RIVER, Biomes.FROZEN_RIVER),

        VINES("jungle / swamp",
                Biomes.JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.BAMBOO_JUNGLE, Biomes.SWAMP,
                Biomes.MANGROVE_SWAMP),

        DAPPLED_FOREST("dappled forest", Biomes.DAPPLED_FOREST),

        WARM_OCEAN("warm ocean", Biomes.WARM_OCEAN),

        RABBITS("rabbits",
                Biomes.DESERT, Biomes.SNOWY_PLAINS, Biomes.ICE_SPIKES, Biomes.SNOWY_SLOPES,
                Biomes.SNOWY_TAIGA, Biomes.GROVE, Biomes.TAIGA, Biomes.OLD_GROWTH_PINE_TAIGA,
                Biomes.OLD_GROWTH_SPRUCE_TAIGA, Biomes.MEADOW, Biomes.FLOWER_FOREST,
                Biomes.CHERRY_GROVE, Biomes.DAPPLED_FOREST),

        MOSSY("jungle / old growth taiga",
                Biomes.JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.BAMBOO_JUNGLE,
                Biomes.OLD_GROWTH_SPRUCE_TAIGA, Biomes.OLD_GROWTH_PINE_TAIGA);

        private final String label;
        private final List<ResourceKey<Biome>> biomes;

        @SafeVarargs
        Group(String label, ResourceKey<Biome>... biomes) {
            this.label = label;
            this.biomes = List.of(biomes);
        }

        public String label() {
            return this.label;
        }

        boolean matches(Holder<Biome> holder) {
            for (ResourceKey<Biome> key : this.biomes) {
                if (holder.is(key)) return true;
            }
            return false;
        }
    }

    public record Gate(Group group, int radius) {
    }

    private static final Map<String, Gate> BY_FAMILY = new LinkedHashMap<>();

    private static final Map<String, List<Gate>> BY_ITEM = new LinkedHashMap<>();

    private static void family(String family, Group group, int radius) {
        BY_FAMILY.put(family, new Gate(group, radius));
    }

    private static void item(Group group, int radius, String... ids) {
        for (String id : ids) {
            BY_ITEM.computeIfAbsent(id, key -> new ArrayList<>()).add(new Gate(group, radius));
        }
    }

    private static final Map<String, List<List<Gate>>> EITHER = new LinkedHashMap<>();

    @SafeVarargs
    private static void either(String id, List<Gate>... recipes) {
        EITHER.computeIfAbsent(id, key -> new ArrayList<>()).addAll(List.of(recipes));
    }

    private static final String[] DYED_ONLY_COLOURS = {
        "red", "orange", "yellow", "lime", "green",
        "cyan", "light_blue", "blue", "purple", "magenta",

        "pink", "gray", "light_gray", "black",
    };

    private static final String[] EVERY_COLOUR = {
        "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black",
    };

    private static final String[] DYED_SUFFIXES = {
        "_concrete", "_concrete_powder", "_stained_glass", "_stained_glass_pane",
        "_glazed_terracotta", "_candle", "_bundle", "_harness",

        "_concrete_stairs", "_concrete_slab",
    };

    private static final String[] WOOL_SUFFIXES = {
        "_wool", "_carpet", "_bed", "_banner", "_wool_stairs", "_wool_slab", "_cushion",
    };

    private static final java.util.Set<String> FAR_DYED_COLOURS =
            java.util.Set.of("green", "lime", "cyan");

    private static final java.util.Set<String> NO_FAMILY_GATE = new java.util.HashSet<>();

    static {
        family("oak", Group.OAK, WALK);
        family("birch", Group.BIRCH, WALK);
        family("spruce", Group.TAIGA, WALK);
        family("jungle", Group.JUNGLE, WALK);
        family("acacia", Group.SAVANNA, WALK);
        family("dark_oak", Group.DARK_FOREST, WALK);
        family("mangrove", Group.MANGROVE, WALK);
        family("cherry", Group.CHERRY, WALK);
        family("pale_oak", Group.PALE_GARDEN, WALK);
        family("bamboo", Group.BAMBOO, WALK);

        family("poplar", Group.DAPPLED_FOREST, WALK);
        item(Group.DAPPLED_FOREST, WALK, "red_shrub", "shelf_mushroom");

        item(Group.GRASSY, NEAR, "dandelion", "poppy");
        item(Group.PLAINS_FLOWERS, NEAR, "cornflower", "azure_bluet", "oxeye_daisy");
        item(Group.FLOWER_FOREST, NEAR,
                "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "lily_of_the_valley");
        item(Group.ALLIUM, NEAR, "allium");
        item(Group.FOREST_FLOWERS, NEAR, "lilac", "rose_bush", "peony");
        item(Group.SWAMP, NEAR, "blue_orchid");
        item(Group.SUNFLOWER_PLAINS, NEAR, "sunflower");
        item(Group.WILDFLOWERS, NEAR, "wildflowers");
        item(Group.CHERRY, NEAR, "pink_petals");
        item(Group.PALE_GARDEN, NEAR, "closed_eyeblossom", "open_eyeblossom");
        item(Group.LUSH_CAVES, NEAR,
                "spore_blossom", "flowering_azalea", "flowering_azalea_leaves");
        item(Group.CACTUS, NEAR, "cactus_flower");

        item(Group.GRASSY, NEAR, "yellow_dye", "red_dye", "purple_dye");

        item(Group.GRASSY, NEAR, "orange_dye");
        item(Group.PLAINS_FLOWERS, NEAR, "light_gray_dye");
        item(Group.FOREST_FLOWERS, NEAR, "magenta_dye");

        item(Group.PINK_SOURCES, NEAR, "pink_dye");

        either("light_blue_dye",
                List.of(new Gate(Group.SWAMP, NEAR)),
                List.of(new Gate(Group.PLAINS_FLOWERS, NEAR), new Gate(Group.WHITE_SOURCES, NEAR)));
        item(Group.CACTUS, WALK, "green_dye", "lime_dye", "cyan_dye");
        item(Group.JUNGLE, WALK, "brown_dye");

        item(Group.PLAINS_FLOWERS, NEAR, "purple_dye", "cyan_dye");

        item(Group.MUSHROOMS, NEAR,
                "brown_mushroom", "red_mushroom", "mushroom_stew",
                "brown_mushroom_block", "red_mushroom_block", "mushroom_stem");
        item(Group.CACTUS, NEAR, "short_dry_grass", "tall_dry_grass");

        item(Group.WARM_OCEAN, NEAR, "sea_pickle");
        item(Group.CACTUS, NEAR, "sea_pickle");

        item(Group.JUNGLE, WALK, "melon", "melon_slice", "melon_seeds", "cocoa_beans");
        item(Group.BERRY_TAIGA, WALK, "sweet_berries");
        item(Group.RED_SAND, WALK, "red_sand");
        item(Group.SNOWY, WALK, "snow", "snow_block", "snowball", "ice", "packed_ice", "blue_ice");
        item(Group.MUSHROOM_FIELDS, WALK, "mycelium");
        item(Group.LEAF_LITTER, WALK, "leaf_litter");
        item(Group.PALE_GARDEN, WALK,
                "pale_moss_block", "pale_moss_carpet", "pale_hanging_moss", "creaking_heart");
        item(Group.CACTUS, WALK, "cactus");
        item(Group.MANGROVE, WALK, "mud", "muddy_mangrove_roots");

        item(Group.MANGROVE, WALK, "moss_carpet");

        item(Group.MOSSY, WALK,
                "mossy_cobblestone", "mossy_cobblestone_slab", "mossy_cobblestone_stairs",
                "mossy_cobblestone_wall", "mossy_stone_bricks", "mossy_stone_brick_slab",
                "mossy_stone_brick_stairs", "mossy_stone_brick_wall");
        item(Group.WETLAND, WALK, "lily_pad", "firefly_bush");
        item(Group.OAK, WALK, "apple");
        item(Group.OCEAN, WALK,
                "kelp", "cod", "cooked_cod", "salmon", "cooked_salmon", "ink_sac", "seagrass");

        family("red_sandstone", Group.RED_SAND, WALK);
        item(Group.PLAINS_FLOWERS, NEAR, "flower_banner_pattern");

        item(Group.VINES, WALK, "bordure_indented_banner_pattern");

        item(Group.LIVESTOCK, WALK,
                "beef", "cooked_beef", "porkchop", "cooked_porkchop",
                "mutton", "cooked_mutton", "leather");
        item(Group.CHICKEN, WALK, "chicken", "cooked_chicken", "feather", "egg");

        item(Group.CHICKEN, WALK, "arrow");

        item(Group.RABBITS, WALK, "rabbit", "rabbit_hide", "rabbit_foot");
        family("wool", Group.LIVESTOCK, WALK);

        item(Group.SQUID, NEAR, "black_dye");

        item(Group.SQUID, NEAR, "gray_dye");
        item(Group.PLAINS_FLOWERS, NEAR, "blue_dye");

        item(Group.WHITE_SOURCES, NEAR, "white_dye");
        item(Group.GRASSY, NEAR, "pumpkin", "pumpkin_seeds");
        item(Group.RED_SAND, WALK, "terracotta");

        item(Group.LIVESTOCK, NEAR, "item_frame", "painting");

        dyedItemsInheritTheirDye();
    }

    private static void dyedItemsInheritTheirDye() {
        int given = 0;

        for (String colour : EVERY_COLOUR) {
            List<Gate> dye = BY_ITEM.getOrDefault(colour + "_dye", List.of());
            List<List<Gate>> recipes = EITHER.get(colour + "_dye");
            if (dye.isEmpty() && recipes == null) continue;

            for (String suffix : DYED_SUFFIXES) {
                given += give(colour + suffix, dye) + giveEither(colour + suffix, recipes);
            }
        }

        for (String colour : DYED_ONLY_COLOURS) {
            List<Gate> dye = BY_ITEM.getOrDefault(colour + "_dye", List.of());
            List<List<Gate>> recipes = EITHER.get(colour + "_dye");
            if (dye.isEmpty() && recipes == null) continue;

            boolean far = FAR_DYED_COLOURS.contains(colour);

            for (String suffix : WOOL_SUFFIXES) {
                given += give(colour + suffix, dye) + giveEither(colour + suffix, recipes);

                if (far) NO_FAMILY_GATE.add(colour + suffix);
            }
        }

        given += brownWoolFollowsTheSheep();

        VivetSpeedrun.LOGGER.info("{} dyed items use their dye's biome rules",
                given);
    }

    private static int brownWoolFollowsTheSheep() {
        List<Gate> sheep = List.of(new Gate(Group.BROWN_SHEEP, WALK));

        int given = 0;
        for (String suffix : WOOL_SUFFIXES) {
            String id = "brown" + suffix;
            given += give(id, sheep);
            NO_FAMILY_GATE.add(id);
        }
        return given;
    }

    private static int giveEither(String id, List<List<Gate>> recipes) {
        if (recipes == null || recipes.isEmpty() || EITHER.containsKey(id)) return 0;
        EITHER.put(id, new ArrayList<>(recipes));
        return 1;
    }

    private static int give(String id, List<Gate> gates) {
        if (gates.isEmpty()) return 0;
        List<Gate> mine = BY_ITEM.computeIfAbsent(id, key -> new ArrayList<>());

        int added = 0;
        for (Gate gate : gates) {
            if (!mine.contains(gate)) {
                mine.add(gate);
                added++;
            }
        }
        return added;
    }

    private static final Map<String, Group> AT_HOME = new LinkedHashMap<>();

    static {
        for (String id : new String[]{
                "sand", "sandstone", "sandstone_slab", "sandstone_stairs", "sandstone_wall",
                "cut_sandstone", "smooth_sandstone"}) {
            AT_HOME.put(id, Group.CACTUS);
        }
    }

    private static volatile Map<Group, Integer> distances;

    private static volatile Map<Group, Integer> slivers;

    private static volatile BlockPos origin;

    private static final java.util.concurrent.atomic.AtomicLong epoch =
            new java.util.concurrent.atomic.AtomicLong();

    static {
        if (Group.values().length > Long.SIZE) {
            throw new IllegalStateException("SeedDifficulty.Group has outgrown its bitmask: "
                    + Group.values().length + " groups, " + Long.SIZE + " bits. See groupsOf.");
        }
    }

    private SeedDifficulty() {
    }

    public static void measureAsync(ServerLevel level) {
        measureAsync(level, level.getRespawnData().pos());
    }

    public static void measureAsync(ServerLevel level, BlockPos spawn) {
        final long mine = epoch.incrementAndGet();
        distances = null;
        slivers = null;
        origin = null;

        Thread worker = new Thread(() -> measure(level, spawn, mine), "vivet-seed-difficulty");
        worker.setDaemon(true);
        worker.setPriority(Thread.NORM_PRIORITY - 1);
        worker.start();
    }

    public static void clear() {
        epoch.incrementAndGet();
        distances = null;
        slivers = null;
        origin = null;
    }

    public static boolean isReady() {
        return distances != null;
    }

    private static void measure(ServerLevel level, BlockPos spawn, long mine) {
        long startedAt = System.currentTimeMillis();
        Group[] all = Group.values();

        int[] best = new int[all.length];
        int[] anywhere = new int[all.length];
        java.util.Arrays.fill(best, NOT_FOUND);
        java.util.Arrays.fill(anywhere, NOT_FOUND);

        int quartRadius = QuartPos.fromBlock(RADIUS);
        int quartStep = Math.max(1, QuartPos.fromBlock(HORIZONTAL_STEP));

        int span = (2 * quartRadius) / quartStep + 1;
        long[] cells = new long[span * span];

        try {
            BiomeSource source = level.getChunkSource().getGenerator().getBiomeSource();

            net.minecraft.world.level.biome.BiomeResolver resolver =
                    source.createCachingResolver(level.getChunkSource().randomState());

            Map<Holder<Biome>, Long> memo = new java.util.IdentityHashMap<>();

            int originX = QuartPos.fromBlock(spawn.getX());
            int originZ = QuartPos.fromBlock(spawn.getZ());

            for (int y = level.getMinY() + 1; y < level.getMaxY(); y += VERTICAL_STEP) {
                if (epoch.get() != mine) {
                    VivetSpeedrun.LOGGER.info("Seed scan cancelled, world closed");
                    return;
                }
                int quartY = QuartPos.fromBlock(y);

                for (int col = 0; col < span; col++) {
                    int dx = col * quartStep - quartRadius;

                    for (int row = 0; row < span; row++) {
                        int dz = row * quartStep - quartRadius;

                        Holder<Biome> holder =
                                resolver.getNoiseBiome(originX + dx, quartY, originZ + dz);

                        Long groups = memo.get(holder);
                        if (groups == null) {
                            groups = groupsOf(holder);
                            memo.put(holder, groups);
                        }

                        cells[col * span + row] |= groups;
                    }
                }
            }
        } catch (Throwable t) {
            VivetSpeedrun.LOGGER.warn("Could not scan the seed", t);
        }

        for (int col = 0; col < span; col++) {
            for (int row = 0; row < span; row++) {
                long mask = cells[col * span + row];
                if (mask == 0L) continue;

                int blockX = QuartPos.toBlock(col * quartStep - quartRadius);
                int blockZ = QuartPos.toBlock(row * quartStep - quartRadius);
                int distance = (int) Math.sqrt(blockX * blockX + blockZ * blockZ);

                for (long rest = mask; rest != 0L; rest &= rest - 1) {
                    int group = Long.numberOfTrailingZeros(rest);

                    if (distance < anywhere[group]) anywhere[group] = distance;
                    if (distance < best[group] && isPatch(cells, span, col, row, group)) {
                        best[group] = distance;
                    }
                }
            }
        }

        Map<Group, Integer> found = new EnumMap<>(Group.class);
        Map<Group, Integer> seen = new EnumMap<>(Group.class);
        for (Group group : all) {
            found.put(group, best[group.ordinal()]);
            seen.put(group, anywhere[group.ordinal()]);
        }

        synchronized (SeedDifficulty.class) {
            if (epoch.get() != mine) return;

            origin = spawn;
            slivers = Collections.unmodifiableMap(seen);
            distances = Collections.unmodifiableMap(found);
        }
        log(level, spawn, found, seen, System.currentTimeMillis() - startedAt);
    }

    private static boolean isPatch(long[] cells, int span, int col, int row, int group) {
        long bit = 1L << group;
        int hits = 0;

        for (int dc = -WINDOW; dc <= WINDOW; dc++) {
            int c = col + dc;
            if (c < 0 || c >= span) continue;

            for (int dr = -WINDOW; dr <= WINDOW; dr++) {
                int r = row + dr;
                if (r < 0 || r >= span) continue;

                if ((cells[c * span + r] & bit) != 0L && ++hits >= PATCH) return true;
            }
        }
        return false;
    }

    private static long groupsOf(Holder<Biome> holder) {
        long mask = 0L;
        for (Group group : Group.values()) {
            if (group.matches(holder)) mask |= 1L << group.ordinal();
        }
        return mask;
    }

    public static boolean allows(String ratingKey, String family) {
        List<Gate> gates = gatesFor(ratingKey, family);
        List<List<Gate>> recipes = EITHER.get(ratingKey);
        if (gates.isEmpty() && recipes == null) return true;

        Map<Group, Integer> table = distances;
        if (table == null) return false;

        if (!allPass(gates, table)) return false;
        if (recipes == null) return true;

        for (List<Gate> recipe : recipes) {
            if (allPass(recipe, table)) return true;
        }
        return false;
    }

    private static boolean allPass(List<Gate> gates, Map<Group, Integer> table) {
        for (Gate gate : gates) {
            Integer distance = table.get(gate.group());
            if (distance == null || distance > gate.radius()) return false;
        }
        return true;
    }

    public static List<Gate> gatesFor(String ratingKey, String family) {
        List<Gate> own = BY_ITEM.get(ratingKey);

        Gate familyGate = family == null || NO_FAMILY_GATE.contains(ratingKey)
                ? null : BY_FAMILY.get(family);

        if (own == null) return familyGate == null ? List.of() : List.of(familyGate);
        if (familyGate == null || own.contains(familyGate)) return own;

        List<Gate> both = new ArrayList<>(own);
        both.add(familyGate);
        return both;
    }

    public static Gate gateFor(String ratingKey, String family) {
        List<Gate> gates = gatesFor(ratingKey, family);
        if (!gates.isEmpty()) return gates.get(0);
        List<List<Gate>> recipes = EITHER.get(ratingKey);
        return recipes == null || recipes.isEmpty() || recipes.get(0).isEmpty() ? null : recipes.get(0).get(0);
    }

    private static final int HOME = 32;

    public static boolean isHome(String ratingKey, String family) {
        Map<Group, Integer> table = distances;
        if (table == null) return false;

        Group group = AT_HOME.get(ratingKey);
        if (group == null) {
            List<List<Gate>> recipes = EITHER.get(ratingKey);
            if (gatesFor(ratingKey, family).isEmpty() && recipes != null) {
                for (List<Gate> recipe : recipes) {
                    if (!recipe.isEmpty() && near(table, recipe.get(0).group())) return true;
                }
                return false;
            }
            Gate gate = gateFor(ratingKey, family);
            if (gate == null) return false;
            group = gate.group();
        }
        return near(table, group);
    }

    private static boolean near(Map<Group, Integer> table, Group group) {
        Integer distance = table.get(group);
        return distance != null && distance <= HOME;
    }

    private static final char NEWLINE = (char) 10;

    static String tableFingerprint() {
        StringBuilder out = new StringBuilder(2048);
        out.append(NEAR).append(':').append(WALK).append(':').append(HOME).append('\n');

        out.append("sweep ").append(SWEEP)
                .append(' ').append(RADIUS)
                .append(' ').append(HORIZONTAL_STEP)
                .append(' ').append(VERTICAL_STEP)
                .append(' ').append(PATCH)
                .append(' ').append(WINDOW)
                .append('\n');

        BY_ITEM.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            out.append("i ").append(e.getKey());
            for (Gate gate : e.getValue()) {
                out.append(' ').append(gate.group().name()).append(' ').append(gate.radius());
            }
            out.append('\n');
        });

        NO_FAMILY_GATE.stream().sorted()
                .forEach(id -> out.append("n ").append(id).append(NEWLINE));

        EITHER.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            out.append("e ").append(e.getKey());
            for (List<Gate> recipe : e.getValue()) {
                out.append(" |");
                for (Gate gate : recipe) out.append(' ').append(gate.group().name()).append(' ').append(gate.radius());
            }
            out.append(NEWLINE);
        });

        BY_FAMILY.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e ->
                out.append("f ").append(e.getKey()).append(' ')
                        .append(e.getValue().group().name()).append(' ')
                        .append(e.getValue().radius()).append('\n'));
        AT_HOME.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e ->
                out.append("h ").append(e.getKey()).append(' ')
                        .append(e.getValue().name()).append('\n'));

        for (Group group : Group.values()) {
            out.append("g ").append(group.name());
            for (ResourceKey<Biome> key : group.biomes) out.append(' ').append(key.identifier());
            out.append('\n');
        }
        return out.toString();
    }

    public static String explain(String ratingKey, String family) {
        List<Gate> gates = gatesFor(ratingKey, family);
        List<List<Gate>> recipes = EITHER.get(ratingKey);
        if (gates.isEmpty() && recipes == null) return "ungated";

        Map<Group, Integer> table = distances;
        if (table == null) return "gated, and this world was never measured";

        Map<Group, Integer> raw = slivers;

        StringBuilder out = new StringBuilder(96);
        for (Gate gate : gates) {
            if (out.length() > 0) out.append("; ");
            describe(out, gate, table, raw);
        }

        if (recipes != null) {
            for (int i = 0; i < recipes.size(); i++) {
                out.append(i == 0 ? (out.length() > 0 ? "; and either (" : "either (") : " or (");
                List<Gate> recipe = recipes.get(i);
                for (int j = 0; j < recipe.size(); j++) {
                    if (j > 0) out.append(" and ");
                    describe(out, recipe.get(j), table, raw);
                }
                out.append(')');
            }
        }
        return out.toString();
    }

    private static void describe(StringBuilder out, Gate gate, Map<Group, Integer> table,
                                 Map<Group, Integer> raw) {
        Integer distance = table.get(gate.group());
        out.append(gate.group().label()).append(" within ").append(gate.radius())
                .append(", nearest ")
                .append(distance == null || distance == NOT_FOUND
                        ? "none inside " + RADIUS
                        : distance + " blocks");

        Integer speck = raw == null ? null : raw.get(gate.group());
        if (speck != null && speck != NOT_FOUND && !speck.equals(distance)) {
            out.append(" (specks from ").append(speck).append(", too small to count)");
        }
    }

    private static void log(ServerLevel level, BlockPos spawn, Map<Group, Integer> found,
                            Map<Group, Integer> seen, long millis) {
        StringBuilder out = new StringBuilder(1024);
        out.append("Seed scan for ").append(level.getSeed())
                .append(" from spawn ").append(spawn.getX()).append(", ")
                .append(spawn.getZ()).append(" (").append(millis).append(" ms):");

        for (Group group : Group.values()) {
            int distance = found.get(group);
            int raw = seen.get(group);

            out.append(String.format("%n  %-32s %12s   near %-3s walk %-3s %s",
                    group.label(),
                    distance == NOT_FOUND ? "> " + RADIUS : distance + " blocks",
                    distance <= NEAR ? "yes" : "no",
                    distance <= WALK ? "yes" : "no",
                    raw == distance ? ""
                            : raw == NOT_FOUND ? ""
                            : "(too small at " + raw + " blocks)"));
        }

        VivetSpeedrun.LOGGER.info("Scanned the seed in {} ms", millis);

        VivetSpeedrun.LOGGER.debug("{}", out);
    }
}
