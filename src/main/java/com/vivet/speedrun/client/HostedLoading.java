package com.vivet.speedrun.client;

import com.vivet.speedrun.VivetSpeedrun;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public final class HostedLoading {
    private static final int READY_RADIUS = 6;

    private static final long SETTLE_MS = 500L;

    private static final String RACE_WORLD_PREFIX = "race_";

    private static boolean active;
    private static ConnectScreen connecting;
    private static boolean readySent;
    private static long allHereSince;

    private HostedLoading() {
    }

    public static void begin(Minecraft minecraft, Screen menu) {
        begin(minecraft, menu, LoadingTakeover.Kind.SOLO);
    }

    public static void begin(Minecraft minecraft, Screen menu, LoadingTakeover.Kind kind) {
        Screen current = minecraft.gui.screen();
        connecting = current instanceof ConnectScreen screen ? screen : null;
        active = true;
        readySent = false;
        allHereSince = 0L;

        LoadingTakeover.open(kind, menu);
        if (kind != LoadingTakeover.Kind.SOLO) {
            LoadingTakeover.setRoster(com.vivet.speedrun.session.Session.racers());
        }
        LoadingTakeover.whenReleased(() -> {
            com.vivet.speedrun.session.Session.hostedRacing();
            Network.started();
        });
    }

    static void forgetConnection() {
        connecting = null;
    }

    public static boolean isActive() {
        return active;
    }

    public static void go() {
        if (!active) return;
        VivetSpeedrun.LOGGER.info("Race server started the race");
        LoadingTakeover.go();
    }

    public static void tick(Minecraft minecraft) {
        if (!active) return;

        if (!LoadingTakeover.isArmed()) {
            active = false;
            connecting = null;
            return;
        }

        if (HostedConnect.retryDue(System.currentTimeMillis())) {
            HostedConnect.reconnect(minecraft);
            Screen opened = minecraft.gui.screen();
            connecting = opened instanceof ConnectScreen screen ? screen : null;
            minecraft.gui.setScreen(new com.vivet.speedrun.client.screen.MatchLoadingScreen());
        }

        if (connecting != null) {
            if (minecraft.getConnection() != null) connecting = null;
            else connecting.tick();
        }

        if (readySent || !inRaceWorld(minecraft)) return;

        if (!groundAround(minecraft)) {
            allHereSince = 0L;
            return;
        }

        long now = System.currentTimeMillis();
        if (allHereSince == 0L) {
            allHereSince = now;
            return;
        }
        if (now - allHereSince < SETTLE_MS) return;

        readySent = true;
        Network.ready();
        VivetSpeedrun.LOGGER.info("Race world loaded");
    }

    public static boolean inRaceWorld(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        return level != null && minecraft.player != null
                && level.dimension().identifier().getPath().startsWith(RACE_WORLD_PREFIX);
    }

    private static boolean groundAround(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) return false;

        int radius = Math.max(1, Math.min(READY_RADIUS,
                minecraft.options.getEffectiveRenderDistance() - 1));
        ChunkPos centre = player.chunkPosition();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (level.getChunkSource().getChunk(centre.x() + dx, centre.z() + dz,
                        ChunkStatus.FULL, false) == null) {
                    return false;
                }
            }
        }
        return true;
    }
}
