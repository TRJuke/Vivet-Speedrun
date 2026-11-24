package com.vivet.speedrun.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

final class PlateEmblem {
    private PlateEmblem() {
    }

    private static final int GC_PAD = 7;
    private static final int GC_W = GrandChampionPlate.W + GC_PAD * 2;
    private static final int GC_H = GrandChampionPlate.H + GC_PAD * 2;

    private static final int N_PADX = 8;
    private static final int N_PADY = 6;
    private static final int N_W = NetheritePlate.W + N_PADX * 2;
    private static final int N_H = NetheritePlate.H + N_PADY;

    private static final int[] GC_BUF = new int[GC_W * GC_H];
    private static final int[] N_BUF = new int[N_W * N_H];
    private static int gcStep = Integer.MIN_VALUE;
    private static int nStep = Integer.MIN_VALUE;

    private static final byte[] MASK = new byte[GC_W * GC_H];
    private static final int[] DIST = new int[GC_W * GC_H];
    private static final int FAR = 1 << 20;

    private static final float CCX = GrandChampionPlate.CX + GC_PAD;
    private static final float CCY = GrandChampionPlate.CY + GC_PAD;

    private static final int[] C_OUTLINE = {147, 255, 115};

    private static final int OUTLINE_REACH = 4;

    private static final int[] GLOW = {255, 238, 190};
    private static final int AURA_R = 5;
    private static final int AURA_PEAK = 58;
    private static final int AURA_STEP = 9;

    private static final int[] HOVER = {
            0, 0, -1, -1, -2, -2, -2, -2, -3, -3, -3, -3, -3, -3, -3, -3, -2, -2, -2, -2, -1, -1,
            0, 0, 0, 0, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 2, 2, 2, 2, 1, 1, 0, 0,
    };

    private static final float[] GC_FLAP = {
            1.00F, 1.00F, 1.00F, 1.00F, 0.94F, 0.86F, 0.76F, 0.66F, 0.58F, 0.55F, 0.58F, 0.66F,
            0.76F, 0.86F, 0.93F, 0.98F, 1.00F, 1.00F, 1.00F, 1.00F, 1.00F, 1.00F,
    };

    private static final int[] SWAY = {0, 0, 0, 0, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, -1, -1, -1, -1,
            -1, 0, 0, 0, 0, 0};
    private static final int[] SWAY2 = {0, 0, 0, 0, 0, 0, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, -1, -1,
            -1, 0, 0, 0, 0, 0};

    private static final int[] SCATTER = {0, 5, 2, 9, 13, 6, 11, 1, 15, 8, 3, 12, 7, 14, 4, 10};
    private static final float[] SHIMMER = {0.86F, 1.16F, 0.94F, 1.00F, 1.24F, 0.90F, 1.08F, 0.96F};
    private static final float[] EYEPULSE = {1.00F, 1.00F, 1.08F, 1.16F, 1.22F, 1.16F, 1.08F,
            1.00F, 0.94F, 0.90F, 0.94F, 1.00F};
    private static final int MOTELIFE = 15;

    private static final float[] CROWNRAMP = {1.00F, 0.84F, 1.14F, 0.76F, 0.96F, 1.08F, 0.80F,
            1.02F};
    private static final float[] N_FLAP = {
            1.00F, 1.00F, 1.00F, 0.90F, 0.74F, 0.58F, 0.46F, 0.42F, 0.46F, 0.58F, 0.72F, 0.84F,
            0.92F, 0.97F, 1.00F, 1.00F, 1.00F, 1.00F,
    };

    private static final float[] SHAKET = {
            0.93F, 0.35F, 0.71F, 0.12F, 0.58F, 0.99F, 0.26F, 0.83F, 0.47F, 0.05F, 0.64F, 0.90F,
            0.19F, 0.76F, 0.41F, 0.68F, 0.09F, 0.55F, 0.87F, 0.31F, 0.97F, 0.22F, 0.79F, 0.50F,
    };
    private static final int[][] SHAKEDIR = {
            {1, 0}, {0, 1}, {-1, 0}, {1, 1}, {0, -1}, {-1, 1}, {1, -1}, {-1, -1},
    };

    private static final int[][] TWINK = {
            {24, 17, 3}, {29, 20, 9}, {22, 23, 16}, {27, 15, 21}, {31, 24, 27},
            {21, 19, 33}, {26, 26, 38}, {30, 17, 44}, {23, 26, 48}, {28, 22, 51},
    };

    private static final int[][] WISP = {
            {10, 42, 31, 0}, {18, 46, 37, 5}, {26, 44, 29, 11}, {34, 47, 41, 2}, {42, 45, 23, 14},
            {50, 43, 43, 7}, {58, 41, 27, 19}, {8, 30, 33, 3}, {60, 29, 47, 12}, {13, 20, 25, 17},
            {55, 19, 39, 8}, {30, 10, 35, 22}, {38, 9, 45, 1}, {22, 12, 21, 15},
    };
    private static final int[] WISPDRIFT = {0, 0, 1, 1, 0, -1, -1, 0, 1, 1, 0, -1};
    private static final int WISPLIFE = 14;

    private static final int[] GC_WL = foldOrder(GrandChampionPlate.WING_L, true);
    private static final int[] GC_WR = foldOrder(GrandChampionPlate.WING_R, false);
    private static final int GC_PIV_L = pivot(GrandChampionPlate.WING_L, true);
    private static final int GC_PIV_R = pivot(GrandChampionPlate.WING_R, false);
    private static final int GC_SPAN_L = span(GrandChampionPlate.WING_L);
    private static final int GC_SPAN_R = span(GrandChampionPlate.WING_R);

    private static final int[] N_WL = foldOrder(NetheritePlate.WING_L, true);
    private static final int[] N_WR = foldOrder(NetheritePlate.WING_R, false);
    private static final int N_PIV_L = pivot(NetheritePlate.WING_L, true);
    private static final int N_PIV_R = pivot(NetheritePlate.WING_R, false);
    private static final int N_SPAN_L = span(NetheritePlate.WING_L);
    private static final int N_SPAN_R = span(NetheritePlate.WING_R);

    private static final int[] N_INDEX = rasterIndex();

    private static final int[] GC_CONTENT;
    private static final int[] N_CONTENT;

    static {
        int[] gc = bounds(GC_PAD, GC_PAD, GrandChampionPlate.BODY, GrandChampionPlate.LEGS,
                GrandChampionPlate.HALO, GrandChampionPlate.EYES, GrandChampionPlate.FOOT,
                GrandChampionPlate.WING_L, GrandChampionPlate.WING_R);

        gc[0] -= 1;
        gc[1] -= 4;
        gc[2] += 1;
        gc[3] += 4;
        GC_CONTENT = box(gc, GC_W, GC_H);

        int[] n = bounds(N_PADX, N_PADY, NetheritePlate.CORE, NetheritePlate.WING_L,
                NetheritePlate.WING_R, NetheritePlate.EMBER_L, NetheritePlate.EMBER_R);
        n[1] -= NetheritePlate.TIP_LIFT;
        n[3] += 2;
        N_CONTENT = box(n, N_W, N_H);
    }

    private static int[] box(int[] b, int w, int h) {
        int x = Math.max(0, b[0]);
        int y = Math.max(0, b[1]);
        return new int[]{x, y, Math.min(w, b[2] + 1) - x, Math.min(h, b[3] + 1) - y};
    }

    private static int[] bounds(int padX, int padY, int[]... layers) {
        int lo = Integer.MAX_VALUE, top = Integer.MAX_VALUE;
        int hi = Integer.MIN_VALUE, bottom = Integer.MIN_VALUE;
        for (int[] layer : layers) {
            for (int packed : layer) {
                int x = Plates.x(packed) + padX;
                int y = Plates.y(packed) + padY;
                lo = Math.min(lo, x);
                hi = Math.max(hi, x);
                top = Math.min(top, y);
                bottom = Math.max(bottom, y);
            }
        }
        return new int[]{lo, top, hi, bottom};
    }

    private static int pivot(int[] wing, boolean left) {
        int best = left ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        for (int packed : wing) {
            int x = Plates.x(packed);
            best = left ? Math.max(best, x) : Math.min(best, x);
        }
        return best;
    }

    private static int span(int[] wing) {
        int lo = Integer.MAX_VALUE;
        int hi = Integer.MIN_VALUE;
        for (int packed : wing) {
            int x = Plates.x(packed);
            lo = Math.min(lo, x);
            hi = Math.max(hi, x);
        }
        return Math.max(1, hi - lo);
    }

    private static int[] foldOrder(int[] wing, boolean left) {
        int piv = pivot(wing, left);
        int reach = span(wing);
        int[] count = new int[reach + 2];
        for (int packed : wing) count[distance(packed, piv, left) + 1]++;
        for (int i = 1; i < count.length; i++) count[i] += count[i - 1];

        int[] out = new int[wing.length];
        for (int packed : wing) out[count[distance(packed, piv, left)]++] = packed;
        return out;
    }

    private static int distance(int packed, int piv, boolean left) {
        return left ? piv - Plates.x(packed) : Plates.x(packed) - piv;
    }

    private static int[] rasterIndex() {
        int[] grid = new int[NetheritePlate.W * NetheritePlate.H];
        for (int[] layer : new int[][]{NetheritePlate.CORE, NetheritePlate.WING_L,
                NetheritePlate.WING_R, NetheritePlate.EMBER_L, NetheritePlate.EMBER_R}) {
            for (int packed : layer) {
                grid[Plates.y(packed) * NetheritePlate.W + Plates.x(packed)] = 1;
            }
        }
        int running = 0;
        for (int i = 0; i < grid.length; i++) grid[i] = grid[i] == 1 ? running++ : -1;
        return grid;
    }

    private static int indexAt(int packed) {
        return N_INDEX[Plates.y(packed) * NetheritePlate.W + Plates.x(packed)];
    }

    static void draw(GuiGraphicsExtractor graphics, int cx, int cy, boolean top, int box,
                     int bleed, int tint) {
        long now = System.currentTimeMillis();
        int step = (int) (now / RankEmblem.STEP_MS);

        int[] buf;
        int bw;
        int bh;
        int shakeX = 0;
        int shakeY = 0;

        if (top) {
            buf = GC_BUF;
            bw = GC_W;
            bh = GC_H;
            if (step != gcStep) {
                gcStep = step;
                grandChampion(step);
            }
        } else {
            buf = N_BUF;
            bw = N_W;
            bh = N_H;
            if (step != nStep) {
                nStep = step;
                netherite(step);
            }

            int sub = (int) (now * 4 / RankEmblem.STEP_MS);
            float cpos = Math.floorMod(step, NetheritePlate.CYCLE) / (float) NetheritePlate.CYCLE;
            float ramp = cpos < 0.55F ? 0F : (cpos - 0.55F) / 0.45F;
            if (SHAKET[Math.floorMod(sub, SHAKET.length)] < ramp * ramp) {
                int[] dir = SHAKEDIR[Math.floorMod(sub, SHAKEDIR.length)];
                shakeX = dir[0];
                shakeY = dir[1];
            }
        }

        int[] content = top ? GC_CONTENT : N_CONTENT;
        float scale = Math.min(box / (float) content[2], box / (float) content[3]);

        int margin = bleed <= 0 ? 0 : Math.max(1, Math.round(bleed / scale));
        int[] src = {
                Math.max(0, content[0] - margin),
                Math.max(0, content[1] - margin),
                0, 0,
        };
        src[2] = Math.min(bw, content[0] + content[2] + margin) - src[0];
        src[3] = Math.min(bh, content[1] + content[3] + margin) - src[1];

        int dw = Math.max(1, Math.round(src[2] * scale));
        int dh = Math.max(1, Math.round(src[3] * scale));

        float focusX = top ? CCX : NetheritePlate.CORE_CX + N_PADX;
        float focusY = top ? CCY : NetheritePlate.CORE_CY + N_PADY;

        Plates.present(graphics, buf, bw, src[0], src[1], src[2], src[3],
                cx - Math.round((focusX - src[0]) * scale) + shakeX,
                cy - Math.round((focusY - src[1]) * scale) + shakeY, dw, dh,
                top ? "grand_champion" : "netherite", step, tint);
    }

    private static void grandChampion(int step) {
        java.util.Arrays.fill(GC_BUF, 0);

        int lift = HOVER[Math.floorMod(step, HOVER.length)];
        float k = GC_FLAP[Math.floorMod(step, GC_FLAP.length)];
        int swayShin = SWAY[Math.floorMod(step, SWAY.length)];
        int swayThigh = SWAY2[Math.floorMod(step, SWAY2.length)];

        aura(k, lift, swayShin, swayThigh, 1F + 0.28F * (-lift / 3F));
        motes(step);

        wing(GC_WL, GC_PIV_L, GC_SPAN_L, true, k, lift);
        wing(GC_WR, GC_PIV_R, GC_SPAN_R, false, k, lift);

        paint(GrandChampionPlate.BODY, 0, lift, 1F);

        for (int packed : GrandChampionPlate.LEGS) {
            int py = Plates.y(packed);
            paintOne(packed, py >= 46 ? swayShin : swayThigh, lift, 1F);
        }

        paint(GrandChampionPlate.EYES, 0, lift,
                EYEPULSE[Math.floorMod(step + 5, EYEPULSE.length)]);

        int[] halo = GrandChampionPlate.HALO;
        for (int i = 0; i < halo.length; i++) {
            paintOne(halo[i], 0, lift,
                    SHIMMER[Math.floorMod(step + SCATTER[i & 15], SHIMMER.length)]);
        }

        paint(GrandChampionPlate.FOOT, 0, 0, 0.78F + 0.22F * ((lift + 3) / 6F));
    }

    private static void paint(int[] layer, int dx, int dy, float mul) {
        for (int packed : layer) paintOne(packed, dx, dy, mul);
    }

    private static void paintOne(int packed, int dx, int dy, float mul) {
        int colour = GrandChampionPlate.PAL[Plates.colour(packed)];
        Plates.put(GC_BUF, GC_W, GC_H, Plates.x(packed) + GC_PAD + dx,
                Plates.y(packed) + GC_PAD + dy,
                Math.round(((colour >> 16) & 0xFF) * mul),
                Math.round(((colour >> 8) & 0xFF) * mul),
                Math.round((colour & 0xFF) * mul),
                (colour >>> 24));
    }

    private static void aura(float k, int lift, int swayShin, int swayThigh, float mul) {
        if (mul <= 0) return;
        java.util.Arrays.fill(MASK, (byte) 0);

        stampWing(GC_WL, GC_PIV_L, GC_SPAN_L, true, k, lift);
        stampWing(GC_WR, GC_PIV_R, GC_SPAN_R, false, k, lift);
        stamp(GrandChampionPlate.BODY, 0, lift);
        stamp(GrandChampionPlate.HALO, 0, lift);
        stamp(GrandChampionPlate.FOOT, 0, 0);
        for (int packed : GrandChampionPlate.LEGS) {
            mark(Plates.x(packed) + GC_PAD + (Plates.y(packed) >= 46 ? swayShin : swayThigh),
                    Plates.y(packed) + GC_PAD + lift);
        }

        for (int i = 0; i < DIST.length; i++) DIST[i] = MASK[i] != 0 ? 0 : FAR;
        for (int y = 0; y < GC_H; y++) {
            for (int x = 0; x < GC_W; x++) {
                int i = y * GC_W + x;
                if (x > 0) DIST[i] = Math.min(DIST[i], DIST[i - 1] + 3);
                if (y > 0) DIST[i] = Math.min(DIST[i], DIST[i - GC_W] + 3);
                if (x > 0 && y > 0) DIST[i] = Math.min(DIST[i], DIST[i - GC_W - 1] + 4);
                if (x < GC_W - 1 && y > 0) DIST[i] = Math.min(DIST[i], DIST[i - GC_W + 1] + 4);
            }
        }
        for (int y = GC_H - 1; y >= 0; y--) {
            for (int x = GC_W - 1; x >= 0; x--) {
                int i = y * GC_W + x;
                if (x < GC_W - 1) DIST[i] = Math.min(DIST[i], DIST[i + 1] + 3);
                if (y < GC_H - 1) DIST[i] = Math.min(DIST[i], DIST[i + GC_W] + 3);
                if (x < GC_W - 1 && y < GC_H - 1) DIST[i] = Math.min(DIST[i], DIST[i + GC_W + 1] + 4);
                if (x > 0 && y < GC_H - 1) DIST[i] = Math.min(DIST[i], DIST[i + GC_W - 1] + 4);
            }
        }

        int limit = AURA_R * 3;
        for (int y = 0; y < GC_H; y++) {
            for (int x = 0; x < GC_W; x++) {
                int i = y * GC_W + x;
                if (MASK[i] != 0 || DIST[i] > limit) continue;

                if (DIST[i] <= OUTLINE_REACH) {
                    Plates.put(GC_BUF, GC_W, GC_H, x, y,
                            C_OUTLINE[0], C_OUTLINE[1], C_OUTLINE[2], 255);
                    continue;
                }

                float t = 1F - DIST[i] / (float) limit;
                int a = Math.round(AURA_PEAK * t * t * mul / AURA_STEP) * AURA_STEP;
                if (a > 0) Plates.put(GC_BUF, GC_W, GC_H, x, y, GLOW[0], GLOW[1], GLOW[2], a);
            }
        }
    }

    private static void stamp(int[] layer, int dx, int dy) {
        for (int packed : layer) {
            mark(Plates.x(packed) + GC_PAD + dx, Plates.y(packed) + GC_PAD + dy);
        }
    }

    private static void stampWing(int[] wing, int piv, int reach, boolean left, float k, int lift) {
        for (int packed : wing) {
            int d = distance(packed, piv, left);
            int nd = Math.round(d * k);
            mark(piv + (left ? -nd : nd) + GC_PAD,
                    Plates.y(packed) - Math.round((1 - k) * 4 * (d / (float) reach))
                            + lift + GC_PAD);
        }
    }

    private static void mark(int x, int y) {
        if (x >= 0 && y >= 0 && x < GC_W && y < GC_H) MASK[y * GC_W + x] = 1;
    }

    private static void motes(int step) {
        int[] motes = GrandChampionPlate.MOTES;
        for (int m = 0; m < motes.length; m += GrandChampionPlate.MOTE_STRIDE) {
            int period = motes[m + 3];
            int t = Math.floorMod(step + motes[m + 4], period);
            if (t >= MOTELIFE) continue;

            int rise = t / 2;
            float fade = t < 2 ? (t + 1) / 3F
                    : t > MOTELIFE - 5 ? (MOTELIFE - t) / 5F : 1F;

            int shape = motes[m + 2];
            int from = GrandChampionPlate.MOTE_SHAPE_OFF[shape];
            int to = GrandChampionPlate.MOTE_SHAPE_OFF[shape + 1];
            for (int s = from; s < to; s++) {
                int packed = GrandChampionPlate.MOTE_SHAPE[s];
                int colour = GrandChampionPlate.PAL[Plates.colour(packed)];
                Plates.put(GC_BUF, GC_W, GC_H,
                        motes[m] + Plates.x(packed) + GC_PAD,
                        motes[m + 1] + Plates.y(packed) - rise + GC_PAD,
                        (colour >> 16) & 0xFF, (colour >> 8) & 0xFF, colour & 0xFF,
                        Math.round((colour >>> 24) * fade));
            }
        }
    }

    private static void netherite(int step) {
        java.util.Arrays.fill(N_BUF, 0);

        int at = Math.floorMod(step, NetheritePlate.CYCLE);
        float cpos = at / (float) NetheritePlate.CYCLE;
        boolean slam = at >= NetheritePlate.CYCLE - 2;
        float k = N_FLAP[Math.floorMod(step, N_FLAP.length)];
        float open = openness(cpos);
        float reach = open * 8;

        wisps(step);

        netheriteWing(N_WL, N_PIV_L, N_SPAN_L, true, k, step, slam);
        netheriteWing(N_WR, N_PIV_R, N_SPAN_R, false, k, step, slam);

        embers(step, slam);
        medallion(step, cpos, open, reach, slam);

        for (int[] spot : TWINK) {
            if (spot[2] == at) star(spot[0] + N_PADX, spot[1] + N_PADY);
        }
    }

    private static float openness(float c) {
        if (c < 0.04F) return 0F;
        if (c < 0.62F) return (c - 0.04F) / 0.58F;
        if (c < 0.86F) return 1F;
        return Math.max(0F, 1F - (c - 0.86F) / 0.12F);
    }

    private static void wisps(int step) {
        for (int i = 0; i < WISP.length; i++) {
            int[] w = WISP[i];
            int t = Math.floorMod(step + w[3], w[2]);
            if (t >= WISPLIFE) continue;

            int wx = w[0] + WISPDRIFT[Math.floorMod(t + i, WISPDRIFT.length)];
            int wy = w[1] - (int) (t * 0.7F);
            float fade = t < 2 ? (t + 1) / 3F : t > WISPLIFE - 5 ? (WISPLIFE - t) / 5F : 1F;

            Plates.put(N_BUF, N_W, N_H, wx, wy, 205, 182, 228, Math.round(190 * fade));
            if (t % 4 == 1) {
                Plates.put(N_BUF, N_W, N_H, wx, wy - 1, 142, 111, 168, Math.round(120 * fade));
            }
        }
    }

    private static void netheriteWing(int[] wing, int piv, int reach, boolean left, float k,
                                      int step, boolean slam) {
        int[] pal = NetheritePlate.PAL;
        float shade = 1 - (1 - k) * NetheritePlate.FOLD_SHADE;

        for (int packed : wing) {
            int colour = pal[Plates.colour(packed)];
            float r = (colour >> 16) & 0xFF;
            float g = (colour >> 8) & 0xFF;
            float b = colour & 0xFF;

            if ((packed & (1 << 26)) != 0) {
                float m = CROWNRAMP[Math.floorMod(step + SCATTER[indexAt(packed) & 15],
                        CROWNRAMP.length)];
                if (slam) {
                    m = 1.28F;
                    r = Plates.mix(r, 255, 0.35F);
                    g = Plates.mix(g, 228, 0.35F);
                    b = Plates.mix(b, 150, 0.35F);
                }
                r *= m;
                g *= m;
                b *= m;
            }

            int d = distance(packed, piv, left);
            int nd = Math.round(d * k);
            Plates.put(N_BUF, N_W, N_H,
                    piv + (left ? -nd : nd) + N_PADX,
                    Plates.y(packed)
                            - Math.round((1 - k) * NetheritePlate.TIP_LIFT * (d / (float) reach))
                            + N_PADY,
                    Math.round(r * shade), Math.round(g * shade), Math.round(b * shade),
                    colour >>> 24);
        }
    }

    private static void embers(int step, boolean slam) {
        int phase = Math.floorMod(step, 10);
        int lift = Math.min(4, phase / 2);
        float fade = phase >= 8 ? 0F : 1F - phase / 14F;

        for (int side = 0; side < 2; side++) {
            int[] layer = side == 0 ? NetheritePlate.EMBER_L : NetheritePlate.EMBER_R;
            int drift = side == 0 ? -1 : 1;
            for (int packed : layer) {
                int colour = NetheritePlate.PAL[Plates.colour(packed)];
                float r = (colour >> 16) & 0xFF;
                float g = (colour >> 8) & 0xFF;
                float b = colour & 0xFF;
                int alpha = colour >>> 24;

                int ex = Plates.x(packed) + (phase >= 6 ? drift : 0) + N_PADX;
                int ey = Plates.y(packed) - lift + N_PADY;
                int ea = Math.round(alpha * fade);

                if (slam) {
                    ey -= 2;
                    ea = alpha;
                    r = Plates.mix(r, 255, 0.4F);
                    g = Plates.mix(g, 190, 0.4F);
                    b = Plates.mix(b, 90, 0.4F);
                }
                Plates.put(N_BUF, N_W, N_H, ex, ey, Math.round(r), Math.round(g), Math.round(b),
                        ea);
            }
        }
    }

    private static void medallion(int step, float cpos, float open, float reach, boolean slam) {
        int[] pal = NetheritePlate.PAL;
        int[] core = NetheritePlate.CORE;
        int[] extra = NetheritePlate.CORE_EXTRA;

        for (int i = 0; i < core.length; i++) {
            int packed = core[i];
            int colour = pal[Plates.colour(packed)];
            float r = (colour >> 16) & 0xFF;
            float g = (colour >> 8) & 0xFF;
            float b = colour & 0xFF;

            int crack = ((extra[i] >> 8) & 0xFF) - 1;
            boolean face = (packed & (1 << 26)) != 0;

            if (crack >= 0 && open > 0 && crack <= reach) {
                float age = reach - crack;
                if (age < 1) {
                    r = 255;
                    g = 244;
                    b = 214;
                } else if (age < 2.5F) {
                    r = 255;
                    g = 196;
                    b = 96;
                } else {
                    float sh = CROWNRAMP[Math.floorMod(step + SCATTER[indexAt(packed) & 15],
                            CROWNRAMP.length)];
                    r = 255 * sh;
                    g = 132 * sh;
                    b = 26 * sh;
                }
            } else if (!face) {
                float u = (extra[i] & 0xFF) / 255F;
                float gap = Math.abs(u - cpos);
                if (gap < 0.14F) {
                    float heat = (1 - gap / 0.14F) * 0.8F;
                    r = Plates.mix(r, 255, heat);
                    g = Plates.mix(g, 196, heat);
                    b = Plates.mix(b, 84, heat);
                }
                float wash = 0.10F * cpos;
                r = Plates.mix(r, 255, wash);
                g = Plates.mix(g, 140, wash * 0.6F);
                b = Plates.mix(b, 50, wash * 0.4F);
                if (slam) {
                    r = Plates.mix(r, 255, 0.5F);
                    g = Plates.mix(g, 210, 0.5F);
                    b = Plates.mix(b, 110, 0.5F);
                }
            } else {
                float warm = 0.16F * open;
                r = Plates.mix(r, 210, warm);
                g = Plates.mix(g, 110, warm * 0.5F);
                b = Plates.mix(b, 60, warm * 0.3F);
            }

            Plates.put(N_BUF, N_W, N_H, Plates.x(packed) + N_PADX, Plates.y(packed) + N_PADY,
                    Math.round(r), Math.round(g), Math.round(b), colour >>> 24);
        }
    }

    private static void star(int x, int y) {
        starPixel(x, y, 255, 244, 226);
        starPixel(x - 1, y, 255, 206, 158);
        starPixel(x + 1, y, 255, 206, 158);
        starPixel(x, y - 1, 255, 206, 158);
        starPixel(x, y + 1, 255, 206, 158);
    }

    private static void starPixel(int x, int y, int r, int g, int b) {
        if (x < 0 || y < 0 || x >= N_W || y >= N_H) return;
        if ((N_BUF[y * N_W + x] >>> 24) < 40) return;
        Plates.put(N_BUF, N_W, N_H, x, y, r, g, b, 255);
    }

    private static void wing(int[] wing, int piv, int reach, boolean left, float k, int lift) {
        float shade = 1 - (1 - k) * 0.28F;
        for (int packed : wing) {
            int colour = GrandChampionPlate.PAL[Plates.colour(packed)];
            int d = distance(packed, piv, left);
            int nd = Math.round(d * k);
            Plates.put(GC_BUF, GC_W, GC_H,
                    piv + (left ? -nd : nd) + GC_PAD,
                    Plates.y(packed) - Math.round((1 - k) * 4 * (d / (float) reach)) + lift + GC_PAD,
                    Math.round(((colour >> 16) & 0xFF) * shade),
                    Math.round(((colour >> 8) & 0xFF) * shade),
                    Math.round((colour & 0xFF) * shade),
                    colour >>> 24);
        }
    }
}
