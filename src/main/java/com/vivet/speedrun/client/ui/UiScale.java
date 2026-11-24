package com.vivet.speedrun.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import org.joml.Matrix3x2fStack;

public final class UiScale {
    public static final int DESIGN_SCALE = 2;

    public static final int MIN_WIDTH = 640;
    public static final int MIN_HEIGHT = 430;

    private UiScale() {
    }

    public static float factor() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) return 1.0F;

        int actual = minecraft.getWindow().getGuiScale();
        if (actual <= 0) return 1.0F;

        return (float) designScale(minecraft) / actual;
    }

    public static float screenFactor() {
        return factor();
    }

    public static int designScale(Minecraft minecraft) {
        var window = minecraft.getWindow();
        int scale = Math.max(1, Math.min(DESIGN_SCALE, window.calculateScale(0, false)));
        while (scale > 1 && (window.getWidth() / scale < MIN_WIDTH
                || window.getHeight() / scale < MIN_HEIGHT)) {
            scale--;
        }
        return scale;
    }

    public static int virtual(int actual, float scale) {
        return Math.max(1, Math.round(actual / scale));
    }

    public static int guiWidth(GuiGraphicsExtractor graphics) {
        return virtual(graphics.guiWidth(), factor());
    }

    public static int guiHeight(GuiGraphicsExtractor graphics) {
        return virtual(graphics.guiHeight(), factor());
    }

    public static float push(GuiGraphicsExtractor graphics) {
        return push(graphics, factor());
    }

    public static float push(GuiGraphicsExtractor graphics, float scale) {
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        if (scale != 1.0F) pose.scale(scale, scale);

        return scale;
    }

    public static void pop(GuiGraphicsExtractor graphics) {
        graphics.pose().popMatrix();
    }
}
