package com.vivet.speedrun.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class Tip {
    private static final int PAD_X = 6;
    private static final int PAD_Y = 5;

    private static final int OFFSET_X = 10;
    private static final int OFFSET_Y = 10;

    private static final int MAX_WIDTH = 200;

    private static final int BACK = 0xFF111214;
    private static final int EDGE = Theme.BORDER_STRONG;

    private Tip() {
    }

    public record Line(Component text, int colour) {
    }

    public static Line gap() {
        return new Line(Component.empty(), 0);
    }

    public static void wrap(List<Line> into, Component text, int colour, Font font) {
        String whole = text == null ? "" : text.getString();
        if (whole.isEmpty()) return;

        int room = MAX_WIDTH - PAD_X * 2;

        StringBuilder line = new StringBuilder();
        for (String word : whole.split(" ")) {
            if (word.isEmpty()) continue;

            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && font.width(candidate) > room) {
                into.add(new Line(Component.literal(line.toString()), colour));
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(candidate);
            }
        }

        if (!line.isEmpty()) into.add(new Line(Component.literal(line.toString()), colour));
    }

    public static void draw(GuiGraphicsExtractor graphics, Font font, List<Line> lines,
                            int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (lines == null || lines.isEmpty()) return;

        int textWidth = 0;
        for (Line line : lines) textWidth = Math.max(textWidth, font.width(line.text()));

        int width = Math.min(textWidth + PAD_X * 2, Math.max(40, screenWidth - 8));
        int height = Math.min(lines.size() * font.lineHeight + PAD_Y * 2,
                Math.max(20, screenHeight - 8));

        int x = mouseX + OFFSET_X;
        int y = mouseY + OFFSET_Y;

        if (x + width > screenWidth - 2) x = mouseX - OFFSET_X - width;
        if (y + height > screenHeight - 2) y = mouseY - OFFSET_Y - height;

        x = Math.max(2, Math.min(x, screenWidth - width - 2));
        y = Math.max(2, Math.min(y, screenHeight - height - 2));

        Theme.roundedBox(graphics, x, y, width, height, 4, BACK);
        Theme.roundedBoxOutline(graphics, x, y, width, height, 4, EDGE);

        int room = width - PAD_X * 2;
        int line = y + PAD_Y;

        for (Line entry : lines) {
            String text = entry.text().getString();

            if (!text.isEmpty()) {
                Component shown = entry.text();
                if (font.width(shown) > room) {
                    shown = Component.literal(font.plainSubstrByWidth(text, room));
                }
                graphics.text(font, shown, x + PAD_X, line, entry.colour());
            }
            line += font.lineHeight;
        }
    }

    public static List<Line> lines() {
        return new ArrayList<>();
    }
}
