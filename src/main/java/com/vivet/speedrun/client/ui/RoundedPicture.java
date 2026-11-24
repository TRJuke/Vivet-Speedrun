package com.vivet.speedrun.client.ui;

import java.util.function.BooleanSupplier;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class RoundedPicture {
    private RoundedPicture() {
    }

    public static boolean draw(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                               int radius, BooleanSupplier blit) {
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));

        if (!through(graphics, x, y + r, x + width, y + height - r, blit)) return false;
        if (r == 0) return true;

        band(graphics, x, y, width, height, r, 0, r, blit);
        band(graphics, x, y, width, height, r, height - r, height, blit);
        return true;
    }

    private static void band(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                             int r, int from, int to, BooleanSupplier blit) {
        int start = from;
        int inset = Theme.cornerInset(width, height, r, from);

        for (int row = from + 1; row <= to; row++) {
            int next = row < to ? Theme.cornerInset(width, height, r, row) : -1;
            if (next == inset) continue;

            if (width - inset * 2 > 0) {
                through(graphics, x + inset, y + start, x + width - inset, y + row, blit);
            }
            start = row;
            inset = next;
        }
    }

    private static boolean through(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1,
                                   BooleanSupplier blit) {
        if (x1 <= x0 || y1 <= y0) return true;

        Clip.on(graphics, x0, y0, x1, y1);
        try {
            return blit.getAsBoolean();
        } finally {
            Clip.off(graphics);
        }
    }
}
