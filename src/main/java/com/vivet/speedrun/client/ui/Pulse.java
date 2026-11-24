package com.vivet.speedrun.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class Pulse {
    private static final long PERIOD_MS = 1_000L;

    private Pulse() {
    }

    public static void around(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                              int radius) {
        double phase = (System.currentTimeMillis() % PERIOD_MS) / (double) PERIOD_MS;
        float wave = (float) (0.5 - 0.5 * Math.cos(phase * Math.PI * 2.0));

        int alpha = 0x50 + Math.round(wave * 0xAF);
        Theme.roundedBoxOutline(graphics, x - 1, y - 1, width + 2, height + 2, radius + 1,
                Theme.withAlpha(Theme.DANGER, alpha));
        Theme.roundedBoxOutline(graphics, x - 2, y - 2, width + 4, height + 4, radius + 2,
                Theme.withAlpha(Theme.DANGER, alpha / 2));
        Theme.roundedBoxOutline(graphics, x - 3, y - 3, width + 6, height + 6, radius + 3,
                Theme.withAlpha(Theme.DANGER, Math.round(wave * 0x40)));
    }
}
