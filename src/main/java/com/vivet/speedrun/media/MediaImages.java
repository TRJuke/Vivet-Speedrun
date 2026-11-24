package com.vivet.speedrun.media;

import com.mojang.blaze3d.platform.NativeImage;
import com.vivet.speedrun.VivetSpeedrun;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MediaImages {
    private static final Prepared FAILED = new Prepared(null, 1.0F, 1, 1);

    private record Prepared(Identifier id, float aspect, int width, int height) {
    }

    private static final Map<String, Prepared> uploaded = new ConcurrentHashMap<>();

    private static final Map<String, NativeImage> pending = new HashMap<>();

    private static final Map<String, Boolean> started = new ConcurrentHashMap<>();

    private MediaImages() {
    }

    private static boolean nothing(String id) {
        return id == null || id.isEmpty();
    }

    public static boolean isReady(String id) {
        if (nothing(id)) return false;

        Prepared prepared = uploaded.get(id);
        return prepared != null && prepared.id() != null;
    }

    public static boolean failed(String id) {
        return !nothing(id) && uploaded.get(id) == FAILED;
    }

    public static Identifier texture(String id) {
        if (nothing(id)) return null;

        Prepared prepared = uploaded.get(id);
        return prepared == null ? null : prepared.id();
    }

    public static float aspect(String id) {
        if (nothing(id)) return 1.0F;

        Prepared prepared = uploaded.get(id);
        return prepared == null ? 1.0F : prepared.aspect();
    }

    public static boolean stretch(GuiGraphicsExtractor graphics, String id, int x, int y,
                                  int width, int height, int tint) {
        if (nothing(id) || width <= 0 || height <= 0) return false;

        Prepared prepared = uploaded.get(id);
        if (prepared == null || prepared.id() == null) return false;

        graphics.blit(RenderPipelines.GUI_TEXTURED, prepared.id(), x, y, 0.0F, 0.0F,
                width, height, prepared.width(), prepared.height(),
                prepared.width(), prepared.height(), tint);
        return true;
    }

    public static boolean cover(GuiGraphicsExtractor graphics, String id, int x, int y,
                                int width, int height, int tint) {
        if (nothing(id) || width <= 0 || height <= 0) return false;

        Prepared prepared = uploaded.get(id);
        if (prepared == null || prepared.id() == null) return false;

        int sourceW = prepared.width();
        int sourceH = prepared.height();
        if (sourceW <= 0 || sourceH <= 0) return false;

        int takeW = Math.min(sourceW, Math.max(1, Math.round(sourceH * (width / (float) height))));
        int takeH = Math.min(sourceH, Math.max(1, Math.round(sourceW * (height / (float) width))));

        graphics.blit(RenderPipelines.GUI_TEXTURED, prepared.id(), x, y,
                (sourceW - takeW) / 2.0F, (sourceH - takeH) / 2.0F,
                width, height, takeW, takeH, sourceW, sourceH, tint);
        return true;
    }

    public static void prepare(String id) {
        if (nothing(id)) return;
        if (uploaded.containsKey(id) || started.containsKey(id)) return;

        Path file = MediaCache.path(id);
        if (file == null) return;

        started.put(id, Boolean.TRUE);

        Thread worker = new Thread(() -> decode(id, file), "vivet-media-decode");
        worker.setDaemon(true);
        worker.start();
    }

    private static void decode(String id, Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            NativeImage image = NativeImage.read(in);

            synchronized (pending) {
                pending.put(id, image);
            }
        } catch (Exception e) {
            VivetSpeedrun.LOGGER.warn("Could not decode image {}", id, e);
            uploaded.put(id, FAILED);
        }
    }

    public static void tick() {
        if (pending.isEmpty()) return;

        Map<String, NativeImage> batch;
        synchronized (pending) {
            if (pending.isEmpty()) return;
            batch = new HashMap<>(pending);
            pending.clear();
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getTextureManager() == null) return;

        batch.forEach((id, image) -> {
            try {
                Identifier texture = Identifier.fromNamespaceAndPath(
                        VivetSpeedrun.MOD_ID, "media/" + id.replace('.', '/'));

                minecraft.getTextureManager().register(
                        texture, new DynamicTexture(() -> "vivet media " + id, image));

                uploaded.put(id, new Prepared(texture,
                        image.getWidth() / (float) Math.max(1, image.getHeight()),
                        image.getWidth(), image.getHeight()));
            } catch (Exception e) {
                VivetSpeedrun.LOGGER.warn("Could not load image {}",
                        id, e);
                image.close();
                uploaded.put(id, FAILED);
            }
        });
    }
}
