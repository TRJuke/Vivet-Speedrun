package com.vivet.speedrun.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

final class Plates {
    private Plates() {
    }

    static int[] ints(String... chunks) {
        byte[] raw = Base64.getDecoder().decode(String.join("", chunks));
        ByteBuffer in = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        int[] out = new int[raw.length / 4];
        for (int i = 0; i < out.length; i++) out[i] = in.getInt();
        return out;
    }

    static int x(int packed) {
        return packed & 0xFF;
    }

    static int y(int packed) {
        return (packed >> 8) & 0xFF;
    }

    static int colour(int packed) {
        return (packed >> 16) & 0x3FF;
    }

    static void put(int[] buf, int w, int h, int px, int py, int r, int g, int b, int a) {
        if (px < 0 || py < 0 || px >= w || py >= h || a <= 0) return;
        buf[py * w + px] = (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    static int clamp(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }

    static float mix(float v, float target, float amount) {
        return v + (target - v) * amount;
    }

    static void present(GuiGraphicsExtractor graphics, int[] buf, int bw,
                        int srcX, int srcY, int srcW, int srcH,
                        int left, int top, int dw, int dh, String key, long stamp, int tint) {
        if (dw <= 0 || dh <= 0) return;
        if (dw > SCRATCH_SIDE || dh > SCRATCH_SIDE) {
            rows(graphics, buf, bw, srcX, srcY, srcW, srcH, left, top, dw, dh, tint);
            return;
        }

        for (int dy = 0; dy < dh; dy++) {
            int row = (srcY + dy * srcH / dh) * bw + srcX;
            int out = dy * dw;
            for (int dx = 0; dx < dw; dx++) {
                SCRATCH[out + dx] = buf[row + dx * srcW / dw];
            }
        }

        if (key != null) {
            Identifier id = BadgeTexture.present(key + "_" + dw + "x" + dh, SCRATCH, dw, dh, stamp);
            if (id != null) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, id, left, top, 0F, 0F, dw, dh,
                        dw, dh, tint);
                return;
            }
        }

        java.util.Arrays.fill(TAKEN, 0, dw * dh, false);
        for (int y = 0; y < dh; y++) {
            for (int x = 0; x < dw; x++) {
                int i = y * dw + x;
                if (TAKEN[i]) continue;
                int colour = SCRATCH[i];
                if ((colour >>> 24) == 0) continue;

                int wide = 0;
                while (x + wide < dw && !TAKEN[i + wide] && SCRATCH[i + wide] == colour) wide++;

                int tall = 1;
                grow:
                while (y + tall < dh) {
                    int base = (y + tall) * dw + x;
                    for (int k = 0; k < wide; k++) {
                        if (TAKEN[base + k] || SCRATCH[base + k] != colour) break grow;
                    }
                    tall++;
                }

                for (int j = 0; j < tall; j++) {
                    int base = (y + j) * dw + x;
                    for (int k = 0; k < wide; k++) TAKEN[base + k] = true;
                }

                Theme.fill(graphics, left + x, top + y, left + x + wide, top + y + tall,
                        Theme.darken(colour, brightness(tint)));
            }
        }
    }

    private static void rows(GuiGraphicsExtractor graphics, int[] buf, int bw,
                             int srcX, int srcY, int srcW, int srcH,
                             int left, int top, int dw, int dh, int tint) {
        for (int dy = 0; dy < dh; dy++) {
            int row = (srcY + dy * srcH / dh) * bw;
            int runStart = 0;
            int runColour = 0;

            for (int dx = 0; dx <= dw; dx++) {
                int colour = dx == dw ? 0 : buf[row + srcX + dx * srcW / dw];
                if (dx > 0 && colour == runColour) continue;

                if ((runColour >>> 24) != 0 && dx > runStart) {
                    Theme.fill(graphics, left + runStart, top + dy, left + dx, top + dy + 1,
                            Theme.darken(runColour, brightness(tint)));
                }
                runStart = dx;
                runColour = colour;
            }
        }
    }

    private static float brightness(int tint) {
        return ((tint >> 16) & 0xFF) / 255.0F;
    }

    private static final int SCRATCH_SIDE = 192;
    private static final int[] SCRATCH = new int[SCRATCH_SIDE * SCRATCH_SIDE];
    private static final boolean[] TAKEN = new boolean[SCRATCH_SIDE * SCRATCH_SIDE];
}
