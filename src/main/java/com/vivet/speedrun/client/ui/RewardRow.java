package com.vivet.speedrun.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class RewardRow {
    public static final int HEIGHT = 36;

    private static final int ICON_BOX = 24;
    private static final int ICON_SRC = 16;
    private static final int ICON_CELL = HEIGHT;

    private static final int RADIUS = 3;

    private static final int SEP_INSET = 7;

    private static final int TEXT_GAP = 9;
    private static final int EDGE_GAP = 9;

    private static final long FADE_MS = 220L;

    private static final float LABEL_SCALE = 1.5F;

    private static final float TAG_SCALE = 1.5F;

    private final VanillaIcon.Kind icon;
    private final String label;
    private final int colour;
    private final String tag;

    public RewardRow(VanillaIcon.Kind icon, String label, int colour, String tag) {
        this.icon = icon;
        this.label = label == null ? "" : label;
        this.colour = colour;
        this.tag = tag == null ? "" : tag;
    }

    public float tagFit(Font font, int width) {
        if (this.tag.isEmpty()) return 0.0F;

        int left = room(width) - BigText.widthAt(font, line(), labelScale(font, width)) - TEXT_GAP;
        Component line = Component.literal(this.tag);

        if (BigText.widthAt(font, line, TAG_SCALE) <= left) return TAG_SCALE;
        return BigText.widthAt(font, line, 1.0F) <= left ? 1.0F : 0.0F;
    }

    private Component line() {
        return Component.literal(this.label);
    }

    private int room(int width) {
        return Math.max(0, width - EDGE_GAP - (ICON_CELL + 1 + TEXT_GAP));
    }

    private float labelScale(Font font, int width) {
        return BigText.fitAt(font, line(), room(width), LABEL_SCALE);
    }

    public void draw(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, long age,
                     float tagScale) {
        float white = white(age);
        int alpha = Math.round(255 * Math.min(1.0F, Math.max(0L, age) / (float) FADE_MS));

        plate(graphics, x, y, width, alpha);

        icon(graphics, x + ICON_CELL / 2, y + HEIGHT / 2, Theme.withAlpha(0xFFFFFFFF, alpha));

        int sepX = x + ICON_CELL;
        Theme.fill(graphics, sepX, y + SEP_INSET, sepX + 1, y + HEIGHT - SEP_INSET,
                Theme.withAlpha(0xFFFFFFFF, alpha));

        float labelScale = labelScale(font, width);
        int lit = Theme.withAlpha(Theme.mix(this.colour, 0xFFFFFFFF, white), alpha);

        BigText.drawAt(graphics, font, line(), x + ICON_CELL + 1 + TEXT_GAP,
                y + (HEIGHT - BigText.heightAt(labelScale)) / 2, lit, labelScale, 0);

        tag(graphics, font, x, y, width, tagScale, alpha);
    }

    private void plate(GuiGraphicsExtractor graphics, int x, int y, int width, int alpha) {
        Theme.roundedBox(graphics, x, y, width, HEIGHT, RADIUS,
                Theme.withAlpha(Theme.PANEL_RAISED, alpha));
        Theme.roundedBoxOutline(graphics, x, y, width, HEIGHT, RADIUS,
                Theme.withAlpha(0xFFFFFFFF, alpha));
    }

    private void tag(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, float scale,
                     int alpha) {
        if (this.tag.isEmpty() || scale <= 0.0F) return;

        Component line = Component.literal(this.tag);
        BigText.drawAt(graphics, font, line,
                x + width - EDGE_GAP - BigText.widthAt(font, line, scale),
                y + (HEIGHT - BigText.heightAt(scale)) / 2,
                Theme.withAlpha(Theme.TEXT_MUTED, alpha), scale, 0);
    }

    private void icon(GuiGraphicsExtractor graphics, int cx, int cy, int tint) {
        float factor = ICON_BOX / (float) ICON_SRC;

        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.scale(factor, factor);
        try {
            VanillaIcon.draw(graphics, this.icon, 0, 0, ICON_SRC, tint);
        } finally {
            pose.popMatrix();
        }
    }

    private static float white(long age) {
        if (age >= FADE_MS) return 0.0F;
        float left = 1.0F - Math.max(0L, age) / (float) FADE_MS;
        return left * left;
    }
}
