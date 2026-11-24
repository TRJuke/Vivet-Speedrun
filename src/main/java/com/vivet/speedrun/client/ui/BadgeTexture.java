package com.vivet.speedrun.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

final class BadgeTexture {
    private BadgeTexture() {
    }

    private static final class Held {
        private final NativeImage image;
        private final DynamicTexture texture;
        private final Identifier id;
        private long stamp = Long.MIN_VALUE;

        private Held(NativeImage image, DynamicTexture texture, Identifier id) {
            this.image = image;
            this.texture = texture;
            this.id = id;
        }
    }

    private static final Map<String, Held> HELD = new HashMap<>();

    private static boolean given;

    static Identifier present(String key, int[] argb, int w, int h, long stamp) {
        if (given || w <= 0 || h <= 0) return null;

        try {
            Held held = HELD.get(key);
            if (held == null) {
                NativeImage image = new NativeImage(w, h, true);
                DynamicTexture texture = new DynamicTexture(() -> "vivet/badge/" + key, image);
                Identifier id = Identifier.fromNamespaceAndPath(
                        "vivet_speedrun", "badge/" + key.toLowerCase(java.util.Locale.ROOT));

                Minecraft.getInstance().getTextureManager().register(id, texture);
                held = new Held(image, texture, id);
                HELD.put(key, held);
            }

            if (held.stamp != stamp) {
                held.stamp = stamp;
                for (int y = 0; y < h; y++) {
                    int row = y * w;
                    for (int x = 0; x < w; x++) {
                        held.image.setPixelABGR(x, y, abgr(argb[row + x]));
                    }
                }
                held.texture.upload();
            }
            return held.id;
        } catch (Throwable failure) {
            given = true;
            HELD.clear();
            return null;
        }
    }

    private static int abgr(int argb) {
        return (argb & 0xFF00FF00)
                | ((argb & 0x00FF0000) >>> 16)
                | ((argb & 0x000000FF) << 16);
    }
}
