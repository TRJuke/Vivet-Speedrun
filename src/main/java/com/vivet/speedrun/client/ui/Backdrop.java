package com.vivet.speedrun.client.ui;

import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.media.MediaImages;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class Backdrop {
    private static final Identifier PLATE = Identifier.fromNamespaceAndPath(
            VivetSpeedrun.MOD_ID, "textures/gui/loading_default.png");

    private static final int PLATE_W = 216;
    private static final int PLATE_H = 120;

    private static final int TINT = 0xFF8C8C8C;

    private Backdrop() {
    }

    public static void draw(GuiGraphicsExtractor graphics, String media, int x, int y,
                            int width, int height, int radius) {
        MediaImages.prepare(media);

        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));

        if (r <= 0) {
            picture(graphics, media, x, y, width, height);
        } else {
            RoundedPicture.draw(graphics, x, y, width, height, r, () -> {
                picture(graphics, media, x, y, width, height);
                return true;
            });
        }

        fade(graphics, x, y, width, height, r);
    }

    private static void picture(GuiGraphicsExtractor graphics, String media, int x, int y,
                                int width, int height) {
        if (MediaImages.cover(graphics, media, x, y, width, height, TINT)) return;

        graphics.blit(RenderPipelines.GUI_TEXTURED, PLATE, x, y, 0.0F, 0.0F,
                width, height, PLATE_W, PLATE_H, PLATE_W, PLATE_H, TINT);
    }

    private static void fade(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                             int radius) {
        int band = Math.min(28, Math.max(4, height / 8));

        for (int i = 0; i < band; i++) {
            float towards = 1.0F - i / (float) band;
            int alpha = Math.round(0xFF * towards * towards);
            if (alpha <= 0) continue;

            int colour = Theme.withAlpha(0xFF000000, alpha);

            int top = Theme.cornerInset(width, height, radius, i);
            Theme.fill(graphics, x + top, y + i, x + width - top, y + i + 1, colour);

            int mirrored = height - 1 - i;
            int low = Theme.cornerInset(width, height, radius, mirrored);
            Theme.fill(graphics, x + low, y + mirrored, x + width - low, y + mirrored + 1, colour);
        }
    }
}
