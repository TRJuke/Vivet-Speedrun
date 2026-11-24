package com.vivet.speedrun.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

import org.joml.Matrix3x2fStack;

public final class Clip {
    private Clip() {
    }

    public static void on(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1) {
        Matrix3x2fStack pose = graphics.pose();

        float scaleX = pose.m00();
        float scaleY = pose.m11();
        float shiftX = pose.m20();
        float shiftY = pose.m21();

        device(graphics,
                Mth.floor(scaleX * x0 + shiftX), Mth.floor(scaleY * y0 + shiftY),
                Mth.ceil(scaleX * x1 + shiftX), Mth.ceil(scaleY * y1 + shiftY));
    }

    public static void device(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1) {
        Matrix3x2fStack pose = graphics.pose();

        pose.pushMatrix();
        pose.identity();
        graphics.enableScissor(x0, y0, Math.max(x0, x1), Math.max(y0, y1));
        pose.popMatrix();
    }

    public static void off(GuiGraphicsExtractor graphics) {
        graphics.disableScissor();
    }
}
