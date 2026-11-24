package com.vivet.speedrun.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class ActionIcon {
    public enum Kind {
        NO,
    }

    private ActionIcon() {
    }

    public static void draw(GuiGraphicsExtractor graphics, Kind kind, int cx, int cy, int box,
                            int colour) {
        switch (kind) {
            case NO -> cross(graphics, cx, cy, box, colour);
        }
    }

    private static void cross(GuiGraphicsExtractor graphics, int cx, int cy, int box, int colour) {
        int arm = Math.max(2, box / 2 - 1);
        int unit = Math.max(1, box / 8);

        for (int i = -arm; i <= arm; i++) {
            Theme.fill(graphics, cx + i, cy + i, cx + i + unit, cy + i + unit, colour);
            Theme.fill(graphics, cx + i, cy - i, cx + i + unit, cy - i + unit, colour);
        }
    }
}
