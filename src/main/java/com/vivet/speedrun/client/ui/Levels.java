package com.vivet.speedrun.client.ui;

import net.minecraft.util.Mth;

public final class Levels {
    public static final int CHROMA_FROM = 100;

    private static final float CHROMA_PERIOD = 3200.0F;

    private Levels() {
    }

    public static int colorOf(int level) {
        if (level >= CHROMA_FROM) return chroma(0.0F);
        if (level >= 75) return 0xFFB03030;
        if (level >= 50) return 0xFFC49BF0;
        if (level >= 25) return 0xFF4FD6D6;
        if (level >= 10) return 0xFF5FD35F;
        if (level >= 5) return Theme.TEXT;
        return Theme.TEXT_MUTED;
    }

    public static int chroma(float offset) {
        float turn = (System.currentTimeMillis() % (long) CHROMA_PERIOD) / CHROMA_PERIOD;
        return fromHue((turn + offset) % 1.0F);
    }

    private static int fromHue(float hue) {
        float h = (hue % 1.0F) * 6.0F;
        int sector = (int) h;
        float f = h - sector;

        float top = 0.94F;
        float bottom = 0.20F;
        float rise = bottom + (top - bottom) * f;
        float fall = bottom + (top - bottom) * (1.0F - f);

        float r;
        float g;
        float b;
        switch (sector) {
            case 0 -> { r = top;    g = rise;   b = bottom; }
            case 1 -> { r = fall;   g = top;    b = bottom; }
            case 2 -> { r = bottom; g = top;    b = rise; }
            case 3 -> { r = bottom; g = fall;   b = top; }
            case 4 -> { r = rise;   g = bottom; b = top; }
            default -> { r = top;   g = bottom; b = fall; }
        }

        return 0xFF000000
                | (Mth.clamp((int) (r * 255.0F), 0, 255) << 16)
                | (Mth.clamp((int) (g * 255.0F), 0, 255) << 8)
                | Mth.clamp((int) (b * 255.0F), 0, 255);
    }
}
