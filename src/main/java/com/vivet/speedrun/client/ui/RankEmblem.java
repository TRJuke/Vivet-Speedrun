package com.vivet.speedrun.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class RankEmblem {
    private static final int FULL_BOX = 74;

    private static final int FULL_BLEED = 5;

    private static final int COMPACT_BOX = 26;

    private static boolean drawn(int index) {
        return index >= 1;
    }

    private static void drawArt(GuiGraphicsExtractor graphics, int cx, int cy, int index, int box,
                                int bleed, int tint) {
        if (index >= 10) {
            SpriteEmblem.draw(graphics, cx, cy, index, box, tint);
        } else if (index == 9) {
            SpeedKingEmblem.draw(graphics, cx, cy, box, bleed, tint);
        } else if (index >= 7) {
            PlateEmblem.draw(graphics, cx, cy, index == 8, box, bleed, tint);
        } else {
            SpriteEmblem.draw(graphics, cx, cy, index, box, tint);
        }
    }

    static final long STEP_MS = 170L;

    private static final String[] ORDER = {
            "unranked", "coal", "copper", "iron", "gold", "emerald", "diamond", "netherite",
            "grand_champion",

            "speed_king", "speed_prince", "speed_knight", "speed_squire",
    };

    private RankEmblem() {
    }

    public static void draw(GuiGraphicsExtractor graphics, int cx, int cy, String rankId,
                            boolean placed) {
        draw(graphics, cx, cy, rankId, placed, 0);
    }

    public static void draw(GuiGraphicsExtractor graphics, int cx, int cy, String rankId,
                            boolean placed, int dim) {
        int index = indexOf(placed ? rankId : "unranked");
        int tint = tintFor(dim);

        if (drawn(index)) {
            drawArt(graphics, cx, cy, index, FULL_BOX, FULL_BLEED, tint);
        } else {
            questionMark(graphics, cx, cy, FULL_BOX, tint);
        }
    }

    private static int tintFor(int dim) {
        int level = 255 - Math.max(0, Math.min(255, dim));
        return 0xFF000000 | (level << 16) | (level << 8) | level;
    }

    public static void drawCompact(GuiGraphicsExtractor graphics, int cx, int cy, String rankId,
                                   boolean placed) {
        drawAt(graphics, cx, cy, rankId, placed, COMPACT_BOX);
    }

    public static void drawAt(GuiGraphicsExtractor graphics, int cx, int cy, String rankId,
                              boolean placed, int box) {
        int index = indexOf(placed ? rankId : "unranked");

        if (drawn(index)) {
            drawArt(graphics, cx, cy, index, box, 0, 0xFFFFFFFF);
        } else {
            questionMark(graphics, cx, cy, box, 0xFFFFFFFF);
        }
    }

    public static void drawSpeedGod(GuiGraphicsExtractor graphics, int cx, int cy, int box) {
        SpriteEmblem.draw(graphics, cx, cy, "speed_god", box, 0xFFFFFFFF);
    }

    public static int size() {
        return FULL_BOX + FULL_BLEED * 2;
    }

    public static int bodySize() {
        return FULL_BOX;
    }

    private static int indexOf(String rankId) {
        for (int i = 0; i < ORDER.length; i++) {
            if (ORDER[i].equals(rankId)) return i;
        }
        return 0;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((Math.max(0, Math.min(0xFF, alpha))) << 24);
    }

    private static void questionMark(GuiGraphicsExtractor graphics, int cx, int cy, int box,
                                     int tint) {
        int[] runs = markRuns(box);
        int tick = (int) (System.currentTimeMillis() / STEP_MS);
        int colour = Theme.darken(withAlpha(MARK_COLOUR, BREATHE[Math.floorMod(tick, BREATHE.length)]),
                ((tint >> 16) & 0xFF) / 255.0F);

        int left = cx - box / 2;
        int top = cy - box / 2;
        for (int run : runs) {
            int x = run & 0xFF;
            int y = (run >> 8) & 0xFF;
            int len = (run >> 16) & 0xFF;
            Theme.fill(graphics, left + x, top + y, left + x + len, top + y + 1, colour);
        }
    }

    private static final int MARK_COLOUR = 0xFF8B97AC;

    private static final int[] BREATHE = {
            0x86, 0x8E, 0x9A, 0xA8, 0xB6, 0xC2, 0xCC, 0xD2,
            0xD2, 0xCC, 0xC2, 0xB6, 0xA8, 0x9A, 0x8E, 0x86,
    };

    private static int[] markFull;
    private static int[] markCompact;

    private static int markOtherBox;
    private static int[] markOther;

    private static int[] markRuns(int box) {
        if (box == COMPACT_BOX) {
            if (markCompact == null) markCompact = rasterise(box);
            return markCompact;
        }
        if (box == FULL_BOX) {
            if (markFull == null) markFull = rasterise(box);
            return markFull;
        }
        if (markOther == null || markOtherBox != box) {
            markOtherBox = box;
            markOther = rasterise(box);
        }
        return markOther;
    }

    private static int[] rasterise(int box) {
        float hookY = -0.20F * box;
        float outer = 0.26F * box;
        float inner = 0.135F * box;
        float middle = (outer + inner) / 2F;
        float half = (outer - inner) / 2F;

        double start = Math.toRadians(-25);
        double end = Math.toRadians(205);
        float px = (float) (middle * Math.cos(start));
        float py = (float) (hookY - middle * Math.sin(start));
        float qx = 0F;
        float qy = 0.06F * box;

        int[] runs = new int[box * 4];
        int count = 0;

        for (int gy = 0; gy < box; gy++) {
            int runStart = -1;
            for (int gx = 0; gx <= box; gx++) {
                boolean on = gx < box && inMark(gx - box / 2F + 0.5F, gy - box / 2F + 0.5F,
                        hookY, outer, inner, half, start, end, px, py, qx, qy, box);
                if (on && runStart < 0) {
                    runStart = gx;
                } else if (!on && runStart >= 0) {
                    if (count == runs.length) runs = java.util.Arrays.copyOf(runs, count * 2);
                    runs[count++] = runStart | (gy << 8) | ((gx - runStart) << 16);
                    runStart = -1;
                }
            }
        }
        return java.util.Arrays.copyOf(runs, count);
    }

    private static boolean inMark(float x, float y, float hookY, float outer, float inner,
                                  float half, double start, double end,
                                  float px, float py, float qx, float qy, int box) {
        float dy = y - hookY;
        float d = (float) Math.sqrt(x * x + dy * dy);
        if (d >= inner && d <= outer) {
            double a = Math.atan2(-dy, x);
            if (a < start) a += Math.PI * 2;
            if (a >= start && a <= end) return true;
        }

        float vx = qx - px;
        float vy = qy - py;
        float t = ((x - px) * vx + (y - py) * vy) / (vx * vx + vy * vy);
        t = t < 0 ? 0 : Math.min(t, 1);
        float tx = x - (px + t * vx);
        float ty = y - (py + t * vy);
        if (tx * tx + ty * ty <= half * half) return true;

        if (Math.abs(x) <= half && y >= 0.04F * box && y <= 0.24F * box) return true;
        float dot = half * 1.05F;
        return Math.abs(x) <= dot && y >= 0.34F * box && y <= 0.34F * box + dot * 2;
    }
}
