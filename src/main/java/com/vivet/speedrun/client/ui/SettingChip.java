package com.vivet.speedrun.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class SettingChip {
    public static final int H = 14;
    public static final int MIN_W = 24;

    private static final int PAD = 14;

    private SettingChip() {
    }

    public static int width(Component widest) {
        return Math.max(MIN_W, Minecraft.getInstance().font.width(widest) + PAD);
    }

    public static void draw(GuiGraphicsExtractor graphics, int x, int y, int width,
                            Component value, boolean lit, boolean hovered, int accent) {
        var font = Minecraft.getInstance().font;

        Theme.roundedBox(graphics, x, y, width, H, 3,
                lit && hovered ? Theme.PANEL_RAISED : Theme.withAlpha(0xFF000000, 0x55));
        Theme.roundedBoxOutline(graphics, x, y, width, H, 3,
                lit ? Theme.withAlpha(accent, hovered ? 0xDD : 0x88) : Theme.BORDER);

        graphics.text(font, value, x + (width - 6 - font.width(value)) / 2, y + 3,
                lit ? Theme.TEXT : Theme.TEXT_FAINT);

        chevron(graphics, x + width - 6, y + H / 2 + 1, lit ? Theme.TEXT_MUTED : Theme.TEXT_FAINT);
    }

    private static void chevron(GuiGraphicsExtractor graphics, int centerX, int centerY,
                                int color) {
        for (int row = 0; row < 3; row++) {
            Theme.fill(graphics, centerX - 2 + row, centerY - 2 + row,
                    centerX + 3 - row, centerY - 1 + row, color);
        }
    }

    public interface Anchor {
        int chipX();

        int chipY();

        int chipWidth();
    }
}
