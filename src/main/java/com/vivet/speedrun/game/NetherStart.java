package com.vivet.speedrun.game;

import com.vivet.speedrun.VivetSpeedrun;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelData;

import java.util.Set;

public final class NetherStart {
    private static final int SEARCH_TOP = 100;

    private static final int FLOOR = 32;

    private static final int IDEAL_Y = 68;

    private static final int SEARCH_RADIUS = 48;
    private static final int STEP = 8;

    private static final int MIN_HEADROOM = 4;

    private static final int HEADROOM_CAP = 8;

    private static final int ROOF_CLEARANCE = 6;

    private static final int HAZARD_REACH = 2;

    private static final int NO_SCORE = Integer.MIN_VALUE;

    private NetherStart() {
    }

    public static void begin(MinecraftServer server, DimensionPool pool) {
        if (server == null || pool != DimensionPool.NETHER) return;

        ServerLevel nether = server.getLevel(Level.NETHER);
        if (nether == null) {
            VivetSpeedrun.LOGGER.warn("No Nether found, starting in the Overworld");
            return;
        }

        BlockPos origin = originFor(server);

        BlockPos landing = findFooting(nether, origin);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            place(player, nether, landing);
        }
    }

    public static BlockPos originFor(MinecraftServer server) {
        BlockPos overworldSpawn = server.overworld().getRespawnData().pos();
        return new BlockPos(overworldSpawn.getX() / 8, IDEAL_Y, overworldSpawn.getZ() / 8);
    }

    public static void send(ServerPlayer player, ServerLevel nether, BlockPos origin) {
        place(player, nether, findFooting(nether, origin));
    }

    private static void place(ServerPlayer player, ServerLevel nether, BlockPos landing) {
        player.teleportTo(nether, landing.getX() + 0.5, landing.getY(), landing.getZ() + 0.5,
                Set.of(), player.getYRot(), player.getXRot(), true);

        player.setRespawnPosition(
                new ServerPlayer.RespawnConfig(
                        LevelData.RespawnData.of(Level.NETHER, landing,
                                player.getYRot(), player.getXRot()),
                        true),
                false);
    }

    private static BlockPos findFooting(ServerLevel nether, BlockPos origin) {
        BlockPos best = null;
        int bestScore = NO_SCORE;

        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx += STEP) {
            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz += STEP) {
                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;

                BlockPos candidate = bestInColumn(nether, x, z);
                if (candidate == null) continue;

                int score = score(nether, candidate, origin);
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }

        if (best != null) return best;

        VivetSpeedrun.LOGGER.info("No safe Nether spawn found, building a platform");
        return buildPlatform(nether, origin);
    }

    private static BlockPos bestInColumn(ServerLevel nether, int x, int z) {
        BlockPos best = null;
        int bestHeadroom = 0;

        for (int y = SEARCH_TOP; y > FLOOR; y--) {
            BlockPos feet = new BlockPos(x, y, z);
            if (!isFooting(nether, feet)) continue;

            int headroom = headroomAt(nether, feet);
            if (headroom < MIN_HEADROOM) continue;
            if (isRoofPocket(nether, feet, headroom)) continue;
            if (isHazardous(nether, feet)) continue;

            if (best == null || headroom > bestHeadroom) {
                best = feet;
                bestHeadroom = headroom;
            }
            if (bestHeadroom >= HEADROOM_CAP) break;
        }
        return best;
    }

    private static int score(ServerLevel nether, BlockPos feet, BlockPos origin) {
        int headroom = Math.min(HEADROOM_CAP, headroomAt(nether, feet));
        int openness = opennessAt(nether, feet);

        int heightPenalty = Math.abs(feet.getY() - IDEAL_Y) / 4;

        int dx = feet.getX() - origin.getX();
        int dz = feet.getZ() - origin.getZ();
        int distancePenalty = (int) Math.sqrt(dx * dx + dz * dz) / 8;

        return headroom * 4 + openness * 2 - heightPenalty - distancePenalty;
    }

    private static boolean isFooting(ServerLevel nether, BlockPos feet) {
        BlockPos ground = feet.below();

        if (!nether.getBlockState(ground).isSolidRender()) return false;
        if (!nether.getFluidState(ground).isEmpty()) return false;

        return nether.getBlockState(feet).isAir();
    }

    private static int headroomAt(ServerLevel nether, BlockPos feet) {
        int air = 0;
        for (int dy = 0; dy <= HEADROOM_CAP; dy++) {
            if (!nether.getBlockState(feet.above(dy)).isAir()) break;
            air++;
        }
        return air;
    }

    private static boolean isRoofPocket(ServerLevel nether, BlockPos feet, int headroom) {
        if (headroom > ROOF_CLEARANCE) return false;

        for (int dy = headroom; dy <= headroom + ROOF_CLEARANCE; dy++) {
            if (nether.getBlockState(feet.above(dy)).is(Blocks.BEDROCK)) return true;
        }
        return false;
    }

    private static boolean isHazardous(ServerLevel nether, BlockPos feet) {
        for (int dx = -HAZARD_REACH; dx <= HAZARD_REACH; dx++) {
            for (int dz = -HAZARD_REACH; dz <= HAZARD_REACH; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos at = feet.offset(dx, dy, dz);

                    if (!nether.getFluidState(at).isEmpty()) return true;
                    if (nether.getBlockState(at).is(Blocks.FIRE)) return true;
                    if (nether.getBlockState(at).is(Blocks.MAGMA_BLOCK)) return true;
                }
            }
        }
        return false;
    }

    private static int opennessAt(ServerLevel nether, BlockPos feet) {
        int open = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (nether.getBlockState(feet.offset(dx, 1, dz)).isAir()) open++;
            }
        }
        return open;
    }

    private static BlockPos buildPlatform(ServerLevel nether, BlockPos origin) {
        BlockPos base = new BlockPos(origin.getX(), IDEAL_Y, origin.getZ());

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                nether.setBlockAndUpdate(base.offset(dx, -1, dz),
                        Blocks.NETHERRACK.defaultBlockState());
                for (int dy = 0; dy <= 3; dy++) {
                    nether.setBlockAndUpdate(base.offset(dx, dy, dz),
                            Blocks.AIR.defaultBlockState());
                }
            }
        }
        return base;
    }

    public static boolean wouldLightPortal(Level level, BlockPos clicked) {
        if (level.getBlockState(clicked).is(Blocks.OBSIDIAN)) return true;

        for (BlockPos around : BlockPos.betweenClosed(clicked.offset(-1, -1, -1),
                clicked.offset(1, 1, 1))) {
            if (level.getBlockState(around).is(Blocks.OBSIDIAN)) return true;
        }
        return false;
    }

    public static void returnToNether(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;

        ServerLevel nether = server.getLevel(Level.NETHER);
        if (nether == null) return;

        send(player, nether, originFor(server));
    }
}
