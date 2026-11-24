package com.vivet.speedrun.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class Theme {
    public static void fill(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1,
                            int color) {
        graphics.fill(x0, y0, x1, y1, color);
    }

    private Theme() {
    }

    public static final int SCRIM = 0xC8080808;

    public static final int PANEL = 0xFF1E1F22;
    public static final int PANEL_RAISED = 0xFF2B2D31;
    public static final int PANEL_SUNKEN = 0xFF111214;

    public static final int BORDER = 0xFF555659;
    public static final int BORDER_STRONG = 0xFF77777A;
    public static final int HIGHLIGHT = 0x18FFFFFF;

    public static final int TEXT = 0xFFF2F2F2;
    public static final int TEXT_MUTED = 0xFFAAAAAA;
    public static final int TEXT_FAINT = 0xFF6E6E6E;

    public static final int PLATE = 0xFF1E1F22;
    public static final int PLATE_LIT = 0xFF2B2D31;
    public static final int PLATE_EDGE = 0xFF424346;
    public static final int PLATE_EDGE_LIT = 0xFF8C8D90;

    public static final int PANEL_PAD = 16;

    public static int textTop(int boxTop, int boxHeight) {
        return boxTop + (boxHeight - 6) / 2;
    }

    public static final int TITLE_PLATE = 0xFF1E1F22;
    public static final int TITLE_PLATE_LIT = 0xFF2B2D31;

    public static final int ACCENT = 0xFF5AA9F0;
    public static final int SUCCESS = 0xFF5FD35F;
    public static final int WARNING = 0xFFE8C34A;
    public static final int DANGER = 0xFFE0523C;

    public static void roundedRect(GuiGraphicsExtractor graphics, int x, int y, int width,
                                   int height, int color) {
        if (width <= 0 || height <= 0) return;

        if (width <= 2 || height <= 2) {
            Theme.fill(graphics, x, y, x + width, y + height, color);
            return;
        }

        Theme.fill(graphics, x, y + 1, x + width, y + height - 1, color);
        Theme.fill(graphics, x + 1, y, x + width - 1, y + 1, color);
        Theme.fill(graphics, x + 1, y + height - 1, x + width - 1, y + height, color);
    }

    public static void roundedOutline(GuiGraphicsExtractor graphics, int x, int y, int width,
                                      int height, int color) {
        if (width <= 2 || height <= 2) return;

        Theme.fill(graphics, x + 1, y, x + width - 1, y + 1, color);
        Theme.fill(graphics, x + 1, y + height - 1, x + width - 1, y + height, color);
        Theme.fill(graphics, x, y + 1, x + 1, y + height - 1, color);
        Theme.fill(graphics, x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    public static void roundedBox(GuiGraphicsExtractor graphics, int x, int y, int width,
                                  int height, int radius, int color) {
        if (width <= 0 || height <= 0) return;

        int r = clampRadius(width, height, radius);
        if (r <= 0) {
            Theme.fill(graphics, x, y, x + width, y + height, color);
            return;
        }

        Theme.fill(graphics, x, y + r, x + width, y + height - r, color);

        for (int row = 0; row < r; row++) {
            int inset = insetAt(r, height, row);
            Theme.fill(graphics, x + inset, y + row, x + width - inset, y + row + 1, color);

            int mirrored = height - 1 - row;
            int bottom = insetAt(r, height, mirrored);
            Theme.fill(graphics, x + bottom, y + mirrored, x + width - bottom, y + mirrored + 1, color);
        }
    }

    public static void roundedBoxOutline(GuiGraphicsExtractor graphics, int x, int y, int width,
                                         int height, int radius, int color) {
        if (width <= 2 || height <= 2) return;

        int r = clampRadius(width, height, radius);

        if (r <= 0) {
            Theme.fill(graphics, x, y, x + width, y + 1, color);
            Theme.fill(graphics, x, y + height - 1, x + width, y + height, color);
            Theme.fill(graphics, x, y + 1, x + 1, y + height - 1, color);
            Theme.fill(graphics, x + width - 1, y + 1, x + width, y + height - 1, color);
            return;
        }

        if (height - r * 2 > 0) {
            Theme.fill(graphics, x, y + r, x + 1, y + height - r, color);
            Theme.fill(graphics, x + width - 1, y + r, x + width, y + height - r, color);
        }

        for (int row = 0; row < r; row++) {
            outlineRow(graphics, x, y, width, height, r, row, color);
            outlineRow(graphics, x, y, width, height, r, height - 1 - row, color);
        }
    }

    private static void outlineRow(GuiGraphicsExtractor graphics, int x, int y, int width,
                                   int height, int r, int row, int color) {
        int inset = insetAt(r, height, row);

        if (row == 0 || row == height - 1) {
            Theme.fill(graphics, x + inset, y + row, x + width - inset, y + row + 1, color);
            return;
        }

        int reach = Math.max(inset + 1,
                Math.max(insetAt(r, height, row - 1), insetAt(r, height, row + 1)));

        Theme.fill(graphics, x + inset, y + row, x + reach, y + row + 1, color);
        Theme.fill(graphics, x + width - reach, y + row, x + width - inset, y + row + 1, color);
    }

    private static int clampRadius(int width, int height, int radius) {
        return Math.max(0, Math.min(radius, Math.min(width, height) / 2));
    }

    private static int insetAt(int r, int height, int row) {
        double from = row < r ? r - row - 0.5
                : (row >= height - r ? row - (height - r) + 0.5 : -1.0);

        return from < 0 ? 0
                : (int) Math.round(r - Math.sqrt(Math.max(0.0, r * r - from * from)));
    }

    public static int cornerInset(int width, int height, int radius, int row) {
        int r = clampRadius(width, height, radius);
        if (r <= 0 || row < 0 || row >= height) return 0;

        return insetAt(r, height, row);
    }

    public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                             int fill) {
        roundedRect(graphics, x, y, width, height, fill);
        roundedOutline(graphics, x, y, width, height, BORDER);
        Theme.fill(graphics, x + 2, y + 1, x + width - 2, y + 2, HIGHLIGHT);
    }

    public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        panel(graphics, x, y, width, height, PANEL);
    }

    public static void badge(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                             int color) {
        roundedRect(graphics, x, y, width, height, withAlpha(color, 0x33));
        roundedOutline(graphics, x, y, width, height, withAlpha(color, 0x88));
    }

    public static void divider(GuiGraphicsExtractor graphics, int x, int y, int width) {
        Theme.fill(graphics, x, y, x + width, y + 1, BORDER);
    }

    public static void progressBar(GuiGraphicsExtractor graphics, int x, int y, int width,
                                   int height, float progress, int color) {
        roundedRect(graphics, x, y, width, height, PANEL_SUNKEN);
        roundedOutline(graphics, x, y, width, height, BORDER);

        int filled = (int) (Math.max(0.0F, Math.min(1.0F, progress)) * (width - 2));
        if (filled > 0) {
            roundedRect(graphics, x + 1, y + 1, filled, height - 2, color);
        }
    }

    public static void liveDot(GuiGraphicsExtractor graphics, int x, int y, int color,
                               boolean live) {
        Theme.fill(graphics, x, y, x + 4, y + 4, withAlpha(color, 0xFF));
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    public static int fade(int color, float factor) {
        int alpha = (int) (((color >>> 24) & 0xFF) * Math.max(0.0F, Math.min(1.0F, factor)));
        return withAlpha(color, alpha);
    }

    public static int darken(int color, float factor) {
        float k = Math.max(0.0F, Math.min(1.0F, factor));
        int r = Math.round(((color >> 16) & 0xFF) * k);
        int g = Math.round(((color >> 8) & 0xFF) * k);
        int b = Math.round((color & 0xFF) * k);
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    public static int mix(int from, int to, float amount) {
        float t = Math.max(0.0F, Math.min(1.0F, amount));

        int a = lerpChannel(from >>> 24, to >>> 24, t);
        int r = lerpChannel((from >> 16) & 0xFF, (to >> 16) & 0xFF, t);
        int g = lerpChannel((from >> 8) & 0xFF, (to >> 8) & 0xFF, t);
        int b = lerpChannel(from & 0xFF, to & 0xFF, t);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerpChannel(int from, int to, float t) {
        return (int) (from + (to - from) * t) & 0xFF;
    }
}
