package com.vivet.speedrun.client.ui;

import com.mojang.authlib.GameProfile;

import com.mojang.authlib.services.ProfileResult;

import com.vivet.speedrun.VivetSpeedrun;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class PlayerHead {
    private static Supplier<PlayerSkin> lookup;

    private static String lookupFor = "";

    private PlayerHead() {
    }

    public static void drawLocal(GuiGraphicsExtractor graphics, int x, int y, int size) {
        PlayerFaceExtractor.extractRenderState(graphics, localSkin(), x, y, size);
    }

    public static void drawFramed(GuiGraphicsExtractor graphics, int x, int y, int size,
                                  int accent) {
        int pad = 3;
        int box = size + pad * 2;

        Theme.roundedRect(graphics, x, y, box, box, Theme.PANEL_RAISED);
        Theme.roundedOutline(graphics, x, y, box, box, Theme.withAlpha(accent, 0x99));
        drawLocal(graphics, x + pad, y + pad, size);
    }

    public static int framedSize(int size) {
        return size + 6;
    }

    public static PlayerSkin localSkin() {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player != null) return minecraft.player.getSkin();

        var profile = minecraft.getGameProfile();
        if (profile == null) return DefaultPlayerSkin.getDefaultSkin();

        if (minecraft.getProfileResult() == null && profile.id() != null) {
            return remoteSkin(profile.id(), profile.name());
        }

        String id = profile.id() == null ? profile.name() : profile.id().toString();
        if (lookup == null || !lookupFor.equals(id)) {
            lookupFor = id == null ? "" : id;
            lookup = minecraft.getSkinManager().createLookup(profile, false);
        }

        PlayerSkin skin = lookup.get();
        return skin == null ? DefaultPlayerSkin.get(profile) : skin;
    }

    private static final Set<UUID> fetching = ConcurrentHashMap.newKeySet();

    private static final Map<UUID, Long> retryAfter = new ConcurrentHashMap<>();

    private static final long RETRY_MS = 30_000L;

    private static final long MAX_RETRY_MS = 10L * 60L * 1000L;

    private static final Map<UUID, Integer> failures = new ConcurrentHashMap<>();

    private static final Map<UUID, Supplier<PlayerSkin>> remote = new ConcurrentHashMap<>();

    private static final int MAX_REMEMBERED = 1024;

    private static final int MAX_IN_FLIGHT = 2;
    private static final int STARTS_PER_MINUTE = 40;
    private static long startsSince;
    private static int starts;

    private static boolean startAllowed(long now) {
        if (now - startsSince >= 60_000L) {
            startsSince = now;
            starts = 0;
        }
        if (starts >= STARTS_PER_MINUTE) return false;
        starts += 1;
        return true;
    }

    public static void draw(GuiGraphicsExtractor graphics, String name, String uuid, int x, int y,
                            int size) {
        PlayerFaceExtractor.extractRenderState(graphics, skinOf(name, uuid), x, y, size);
    }

    public static void drawFramed(GuiGraphicsExtractor graphics, String name, String uuid,
                                  int x, int y, int size, int accent) {
        int pad = 3;
        int box = size + pad * 2;

        Theme.roundedRect(graphics, x, y, box, box, Theme.PANEL_RAISED);
        Theme.roundedOutline(graphics, x, y, box, box, Theme.withAlpha(accent, 0x99));
        draw(graphics, name, uuid, x + pad, y + pad, size);
    }

    public static PlayerSkin skinOf(String name, String uuid) {
        UUID id = parse(uuid);
        if (id == null) return DefaultPlayerSkin.getDefaultSkin();

        Minecraft minecraft = Minecraft.getInstance();
        var self = minecraft.getUser() == null ? null : minecraft.getUser().getProfileId();
        if (id.equals(self)) return localSkin();

        return remoteSkin(id, name);
    }

    private static PlayerSkin remoteSkin(UUID id, String name) {
        Supplier<PlayerSkin> held = remote.get(id);
        if (held != null) {
            PlayerSkin skin = held.get();
            if (skin != null) return skin;
        }

        PlayerSkin served = fromServer(minecraft(), id);
        if (served != null) return served;

        if (held == null) fetch(id, name);
        return DefaultPlayerSkin.get(id);
    }

    private static Minecraft minecraft() {
        return Minecraft.getInstance();
    }

    private static PlayerSkin fromServer(Minecraft minecraft, UUID id) {
        var connection = minecraft.getConnection();
        if (connection == null) return null;
        var info = connection.getPlayerInfo(id);
        if (info == null || info.getProfile() == null) return null;
        var textures = info.getProfile().properties().get("textures");
        if (textures == null || textures.isEmpty()) return null;
        return info.getSkin();
    }

    private static void fetch(UUID id, String name) {
        long now = System.currentTimeMillis();
        Long wait = retryAfter.get(id);
        if (wait != null && now < wait) return;

        if (fetching.contains(id) || fetching.size() >= MAX_IN_FLIGHT) return;
        if (!startAllowed(now)) return;
        if (!fetching.add(id)) return;

        Minecraft minecraft = Minecraft.getInstance();

        boolean retrying = failures.containsKey(id);

        Thread worker = new Thread(() -> {
            GameProfile resolved = null;
            try {
                ProfileResult answer = minecraft.services().sessionService().fetchProfile(id, retrying);
                if (answer != null) resolved = answer.profile();
            } catch (RuntimeException e) {
                VivetSpeedrun.LOGGER.debug("Could not load skin for {}", id, e);
            }

            if (resolved == null) {
                int failed = failures.merge(id, 1, Integer::sum);
                long backoff = failed == 1 ? 3_000L : failed == 2 ? 6_000L
                        : Math.min(MAX_RETRY_MS, RETRY_MS << Math.min(failed - 3, 5));
                retryAfter.put(id, System.currentTimeMillis() + backoff);
                fetching.remove(id);
                return;
            }

            GameProfile profile = resolved;
            minecraft.execute(() -> {
                if (remote.size() >= MAX_REMEMBERED) remote.clear();
                remote.put(id, minecraft.getSkinManager().createLookup(profile, false));
                retryAfter.remove(id);
                failures.remove(id);
                fetching.remove(id);
            });
        }, "vivet-skin-lookup");

        worker.setDaemon(true);
        worker.setPriority(Thread.NORM_PRIORITY - 1);
        worker.start();
    }

    public static boolean canDraw(String uuid) {
        return parse(uuid) != null;
    }

    private static UUID parse(String uuid) {
        if (uuid == null || uuid.isBlank()) return null;

        String text = uuid.trim();
        try {
            if (text.length() == 32) {
                text = text.substring(0, 8) + "-" + text.substring(8, 12) + "-"
                        + text.substring(12, 16) + "-" + text.substring(16, 20) + "-"
                        + text.substring(20);
            }
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
