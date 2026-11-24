package com.vivet.speedrun.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import org.joml.Matrix3x2fStack;

public final class BigText {
    private BigText() {
    }

    public static int width(Font font, Component text, int scale) {
        return font.width(text.getVisualOrderText()) * Math.max(1, scale);
    }

    public static int height(int scale) {
        return 8 * Math.max(1, scale);
    }

    public static void draw(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y,
                            int color, int scale, int outline) {
        draw(graphics, font, text, x, y, color, scale, outline, 0xFF000000);
    }

    public static void draw(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y,
                            int color, int scale, int outline, int outlineColor) {
        int factor = Math.max(1, scale);

        if (outline > 0) {
            for (int dy = -outline; dy <= outline; dy++) {
                for (int dx = -outline; dx <= outline; dx++) {
                    if (dx == 0 && dy == 0) continue;
                    pass(graphics, font, text, x + dx, y + dy, outlineColor, factor);
                }
            }
        } else {
            pass(graphics, font, text, x + 1, y + 1, shadowOf(color), factor);
        }

        pass(graphics, font, text, x, y, color, factor);
    }

    private static void pass(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y,
                             int color, int factor) {
        if (factor == 1) {
            graphics.text(font, text, x, y, color, false);
            return;
        }

        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(factor, factor);
        try {
            graphics.text(font, text, 0, 0, color, false);
        } finally {
            pose.popMatrix();
        }
    }

    private static int shadowOf(int color) {
        return (color & 0xFF000000)
                | (((color >> 16) & 0xFF) >> 2) << 16
                | (((color >> 8) & 0xFF) >> 2) << 8
                | ((color & 0xFF) >> 2);
    }

    public static void centered(GuiGraphicsExtractor graphics, Font font, Component text, int cx,
                                int y, int color, int scale, int outline) {
        draw(graphics, font, text, cx - width(font, text, scale) / 2, y, color, scale, outline);
    }

    public static int widthAt(Font font, Component text, float scale) {
        return (int) Math.ceil(font.width(text.getVisualOrderText()) * Math.max(1.0F, scale));
    }

    public static int heightAt(float scale) {
        return (int) Math.ceil(8 * Math.max(1.0F, scale));
    }

    public static void centeredAt(GuiGraphicsExtractor graphics, Font font, Component text, int cx,
                                  int y, int color, float scale, int outline) {
        drawAt(graphics, font, text, cx - widthAt(font, text, scale) / 2, y, color, scale, outline);
    }

    public static void drawAt(GuiGraphicsExtractor graphics, Font font, Component text, int x,
                              int y, int color, float scale, int outline) {
        float factor = Math.max(1.0F, scale);

        if (outline > 0) {
            for (int dy = -outline; dy <= outline; dy++) {
                for (int dx = -outline; dx <= outline; dx++) {
                    if (dx == 0 && dy == 0) continue;
                    fine(graphics, font, text, x + dx, y + dy, 0xFF000000, factor);
                }
            }
        } else {
            fine(graphics, font, text, x + 1, y + 1, shadowOf(color), factor);
        }

        fine(graphics, font, text, x, y, color, factor);
    }

    private static void fine(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y,
                             int color, float factor) {
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(factor, factor);
        try {
            graphics.text(font, text, 0, 0, color, false);
        } finally {
            pose.popMatrix();
        }
    }

    public static float fitAt(Font font, Component text, int room, float preferred) {
        return widthAt(font, text, preferred) <= room ? Math.max(1.0F, preferred) : 1.0F;
    }

    public static int fit(Font font, Component text, int room, int preferred) {
        int scale = Math.max(1, preferred);
        while (scale > 1 && width(font, text, scale) > room) scale--;
        return scale;
    }
}
