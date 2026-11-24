package com.vivet.speedrun.client.ui;

import com.vivet.speedrun.VivetSpeedrun;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class Wordmark {
    private static final Identifier LOGO =
            Identifier.fromNamespaceAndPath(VivetSpeedrun.MOD_ID, "textures/gui/wordmark.png");

    private static final Identifier SHINE =
            Identifier.fromNamespaceAndPath(VivetSpeedrun.MOD_ID, "textures/gui/wordmark_shine.png");

    private static final int W = 74;
    private static final int H = 26;

    private static final long SWEEP_MS = 1100L;
    private static final long CYCLE_MS = 7000L;

    private static final float BAND = 0.14F;

    private static final int SLICE = 3;

    private Wordmark() {
    }

    public static int height() {
        return H;
    }

    public static int draw(GuiGraphicsExtractor graphics, int x, int y, float opacity) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, LOGO, x, y, 0.0F, 0.0F, W, H, W, H,
                Theme.fade(0xFFFFFFFF, opacity));
        shine(graphics, x, y, opacity);
        return W;
    }

    private static void shine(GuiGraphicsExtractor graphics, int x, int y, float opacity) {
        sweep(graphics, SHINE, x, y, W, H, opacity);
    }

    public static void sweep(GuiGraphicsExtractor graphics, Identifier shine, int x, int y,
                             int width, int height, float opacity) {
        long at = System.currentTimeMillis() % CYCLE_MS;
        if (at >= SWEEP_MS || opacity <= 0.0F) return;

        float centre = -BAND + (1.0F + BAND * 2.0F) * (at / (float) SWEEP_MS);

        int from = Math.max(0, Math.round((centre - BAND) * width));
        int to = Math.min(width, Math.round((centre + BAND) * width));

        for (int u = from; u < to; u += SLICE) {
            int wide = Math.min(SLICE, to - u);
            float across = (u + wide / 2.0F) / width;
            float strength = 1.0F - Math.abs(across - centre) / BAND;
            if (strength <= 0.0F) continue;

            int alpha = Math.round(0xFF * strength * opacity);
            if (alpha <= 0) continue;

            graphics.blit(RenderPipelines.GUI_TEXTURED, shine, x + u, y, u, 0.0F,
                    wide, height, wide, height, width, height, 0x00FFFFFF | (alpha << 24));
        }
    }
}
