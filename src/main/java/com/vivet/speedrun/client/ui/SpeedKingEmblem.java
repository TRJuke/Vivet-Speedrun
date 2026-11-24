package com.vivet.speedrun.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Arrays;

final class SpeedKingEmblem {
    private SpeedKingEmblem() {
    }

    static final int ACCENT = 0xFFC77DFF;

    private static final int PAD = 8;
    private static final int W = SpeedKingSprite.W + PAD * 2;
    private static final int H = SpeedKingSprite.H + PAD * 2;

    private static final int GLOW_REACH = 15;
    private static final int GLOW_PEAK = 95;
    private static final int BLOOM_REACH = 15;
    private static final int BLOOM_PEAK = 175;
    private static final int BAND = 12;

    private static final float BOOST = 1.25F;

    private static final int FAR = 1 << 20;

    private static final int[] BUF = new int[W * H];
    private static final boolean[] SOLID = new boolean[W * H];
    private static final boolean[] WISP = new boolean[W * H];
    private static final int[] GLOW = new int[W * H];
    private static final int[] BLOOM = new int[W * H];
    private static int drawn = -1;

    private static final int[] CONTENT = {
            SpeedKingSprite.CONTENT[0] + PAD, SpeedKingSprite.CONTENT[1] + PAD,
            SpeedKingSprite.CONTENT[2], SpeedKingSprite.CONTENT[3],
    };

    static void draw(GuiGraphicsExtractor graphics, int cx, int cy, int box, int bleed,
                     int tint) {
        int frame = (int) (System.currentTimeMillis() / SpeedKingSprite.FRAME_MS
                % SpeedKingSprite.FRAMES);
        if (frame != drawn) {
            drawn = frame;
            compose(frame);
        }

        float scale = Math.min(box / (float) CONTENT[2], box / (float) CONTENT[3]);
        int margin = bleed <= 0 ? 0 : Math.max(1, Math.round(bleed / scale));

        int sx = Math.max(0, CONTENT[0] - margin);
        int sy = Math.max(0, CONTENT[1] - margin);
        int sw = Math.min(W, CONTENT[0] + CONTENT[2] + margin) - sx;
        int sh = Math.min(H, CONTENT[1] + CONTENT[3] + margin) - sy;

        float focusX = CONTENT[0] + CONTENT[2] / 2F;
        float focusY = CONTENT[1] + CONTENT[3] / 2F;

        Plates.present(graphics, BUF, W, sx, sy, sw, sh,
                cx - Math.round((focusX - sx) * scale),
                cy - Math.round((focusY - sy) * scale),
                Math.max(1, Math.round(sw * scale)), Math.max(1, Math.round(sh * scale)),
                "speed_king", frame, tint);
    }

    private static void compose(int frame) {
        Arrays.fill(BUF, 0);
        Arrays.fill(SOLID, false);
        Arrays.fill(WISP, false);

        int[] runs = SpeedKingSprite.RUNS;
        int[] pal = SpeedKingSprite.PAL;
        for (int i = SpeedKingSprite.FRAME_OFF[frame]; i < SpeedKingSprite.FRAME_OFF[frame + 1];
                i++) {
            int run = runs[i];
            int x = run & 0x7F;
            int y = (run >> 7) & 0x7F;
            int w = (run >> 14) & 0x7F;
            int h = (run >> 21) & 0x1F;
            int colour = pal[(run >>> 26) & 0x3F];
            boolean wisp = isWisp(colour);
            if (wisp) colour = brighten(colour);

            for (int j = 0; j < h; j++) {
                int row = (y + PAD + j) * W + x + PAD;
                for (int k = 0; k < w; k++) {
                    BUF[row + k] = colour;
                    SOLID[row + k] = true;
                    WISP[row + k] = wisp;
                }
            }
        }

        chamfer(SOLID, GLOW);
        chamfer(WISP, BLOOM);

        for (int i = 0; i < BUF.length; i++) {
            if (SOLID[i]) continue;
            int alpha = Math.max(band(GLOW[i], GLOW_REACH, GLOW_PEAK),
                    band(BLOOM[i], BLOOM_REACH, BLOOM_PEAK));
            if (alpha > 0) BUF[i] = (Math.min(alpha, 255) << 24) | (ACCENT & 0x00FFFFFF);
        }
    }

    private static boolean isWisp(int colour) {
        int r = (colour >> 16) & 0xFF;
        int b = colour & 0xFF;
        return b > 150 && b > r + 40 && r > 40;
    }

    private static int brighten(int colour) {
        return (colour & 0xFF000000)
                | (Plates.clamp(Math.round(((colour >> 16) & 0xFF) * BOOST)) << 16)
                | (Plates.clamp(Math.round(((colour >> 8) & 0xFF) * BOOST)) << 8)
                | Plates.clamp(Math.round((colour & 0xFF) * BOOST));
    }

    private static int band(int distance, int reach, int peak) {
        if (distance > reach) return 0;
        float t = 1F - distance / (float) reach;
        return Math.round(peak * t * t / BAND) * BAND;
    }

    private static void chamfer(boolean[] from, int[] into) {
        for (int i = 0; i < into.length; i++) into[i] = from[i] ? 0 : FAR;

        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int i = y * W + x;
                if (x > 0) into[i] = Math.min(into[i], into[i - 1] + 3);
                if (y > 0) into[i] = Math.min(into[i], into[i - W] + 3);
                if (x > 0 && y > 0) into[i] = Math.min(into[i], into[i - W - 1] + 4);
                if (x < W - 1 && y > 0) into[i] = Math.min(into[i], into[i - W + 1] + 4);
            }
        }
        for (int y = H - 1; y >= 0; y--) {
            for (int x = W - 1; x >= 0; x--) {
                int i = y * W + x;
                if (x < W - 1) into[i] = Math.min(into[i], into[i + 1] + 3);
                if (y < H - 1) into[i] = Math.min(into[i], into[i + W] + 3);
                if (x < W - 1 && y < H - 1) into[i] = Math.min(into[i], into[i + W + 1] + 4);
                if (x > 0 && y < H - 1) into[i] = Math.min(into[i], into[i + W - 1] + 4);
            }
        }
    }
}
