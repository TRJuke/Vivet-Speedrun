package com.vivet.speedrun.client.ui;

import com.vivet.speedrun.VivetSpeedrun;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import org.joml.Matrix3x2fStack;

public final class VoidBackground {
    private static final Identifier STARS =
            Identifier.fromNamespaceAndPath(VivetSpeedrun.MOD_ID, "textures/gui/void_stars.png");

    private static final int SKY_TOP = 0xFF060F10;
    private static final int SKY_BOTTOM = 0xFF03080B;

    private record Layer(int tile, float driftX, float driftY, int colour) {
    }

    private static final Layer[] LAYERS = {
            new Layer(66, 0.5F, -0.3F, 0x3C16E0D8),
            new Layer(94, -0.4F, 0.7F, 0x442FC0FF),
            new Layer(126, 0.9F, 0.4F, 0x4E5AB8FF),
            new Layer(162, -0.7F, -0.6F, 0x5A86D0FF),
            new Layer(196, 0.4F, 1.1F, 0x6A6CFFE8),
            new Layer(228, -1.2F, 0.3F, 0x7CC0A8FF),
            new Layer(256, 0.3F, -1.4F, 0x92E8F4FF),
    };

    private VoidBackground() {
    }

    public static void draw(GuiGraphicsExtractor graphics, int width, int height) {
        if (width <= 0 || height <= 0) return;

        graphics.fillGradient(0, 0, width, height, SKY_TOP, SKY_BOTTOM);

        float seconds = (System.currentTimeMillis() % 86_400_000L) / 1000.0F;
        for (Layer layer : LAYERS) drawLayer(graphics, width, height, seconds, layer);
    }

    private static void drawLayer(GuiGraphicsExtractor graphics, int width, int height,
                                  float seconds, Layer layer) {
        int tile = layer.tile();

        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(wrap(seconds * layer.driftX(), tile),
                wrap(seconds * layer.driftY(), tile));

        for (int x = -tile; x <= width + tile; x += tile) {
            for (int y = -tile; y <= height + tile; y += tile) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, STARS, x, y, 0.0F, 0.0F,
                        tile, tile, tile, tile, layer.colour());
            }
        }

        pose.popMatrix();
    }

    private static float wrap(float value, int period) {
        float wrapped = value % period;
        return wrapped < 0.0F ? wrapped + period : wrapped;
    }

    public static void vignette(GuiGraphicsExtractor graphics, int width, int height) {
        graphics.fillGradient(0, 0, width, Math.min(height, 96), 0x99030507, 0x00000000);
        graphics.fillGradient(0, Math.max(0, height - 130), width, height, 0x00000000, 0xD8030507);

        sideFade(graphics, 0, Math.min(Math.round(width * 0.62F), 520), height, 0xE6, true);

        int right = Math.min(width / 4, 180);
        sideFade(graphics, width - right, right, height, 0x7A, false);
    }

    private static void sideFade(GuiGraphicsExtractor graphics, int x, int width, int height,
                                 int strength, boolean darkestOnTheLeft) {
        if (width <= 0) return;

        int strips = 24;
        for (int i = 0; i < strips; i++) {
            float away = (i + 0.5F) / strips;
            float depth = darkestOnTheLeft ? 1.0F - away : away;
            int alpha = Math.round(strength * depth * depth * depth);
            if (alpha <= 0) continue;

            int x0 = x + Math.round(width * (i / (float) strips));
            int x1 = x + Math.round(width * ((i + 1) / (float) strips));
            Theme.fill(graphics, x0, 0, x1, height, (alpha << 24) | 0x030507);
        }
    }
}
