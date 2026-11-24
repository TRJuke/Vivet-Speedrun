package com.vivet.speedrun.client.replay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class RacerCursor {
    private static final int RED = 0xFFE8282B;
    private static final int EDGE = 0xFF000000;

    private static final String[] ARROW = {
            "B",
            "BB",
            "BWB",
            "BWWB",
            "BWWWB",
            "BWWWWB",
            "BWWWWWB",
            "BWWWWWWB",
            "BWWWWWWWB",
            "BWWWWWWWWB",
            "BWWWWWWWWWB",
            "BWWWWWWBBBBB",
            "BWWWBWWB",
            "BWWBBWWB",
            "BWB  BWWB",
            "BB   BWWB",
            "B     BWWB",
            "      BWWB",
            "       BB",
    };

    private RacerCursor() {
    }

    public static void draw(GuiGraphicsExtractor graphics, int cornerX, int cornerY, int quarterX, int quarterY) {
        int scale = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());

        int cell = Math.max(1, Math.round(Minecraft.getInstance().getWindow().getHeight() / 1080.0F));
        int left = cornerX * scale + Math.floorDiv(quarterX * scale, 4);
        int top = cornerY * scale + Math.floorDiv(quarterY * scale, 4);

        graphics.nextStratum();
        graphics.pose().pushMatrix();
        graphics.pose().scale(1.0F / scale, 1.0F / scale);
        for (int row = 0; row < ARROW.length; row++) {
            String line = ARROW[row];
            for (int col = 0; col < line.length(); col++) {
                char c = line.charAt(col);
                if (c != 'B' && c != 'W') continue;
                int px = left + col * cell;
                int py = top + row * cell;
                graphics.fill(px, py, px + cell, py + cell, c == 'B' ? EDGE : RED);
            }
        }
        graphics.pose().popMatrix();
    }
}
