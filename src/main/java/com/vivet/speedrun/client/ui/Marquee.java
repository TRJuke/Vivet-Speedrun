package com.vivet.speedrun.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class Marquee {
    private static final long HOLD_MS = 1600L;

    private static final float SPEED = 26.0F;

    public static final int TAIL = 6;

    private Marquee() {
    }

    public static int drawLeft(GuiGraphicsExtractor graphics, Font font, Component text,
                               int x, int y, int maxWidth, int color) {
        int width = font.width(text);
        if (maxWidth <= 0) return 0;

        if (width <= maxWidth) {
            graphics.text(font, text, x, y, color);
            return width;
        }

        int travel = width - maxWidth + TAIL;

        Clip.on(graphics, x, y - 1, x + maxWidth, y + font.lineHeight + 1);
        graphics.text(font, text, x - offset(travel), y, color);
        Clip.off(graphics);

        return maxWidth;
    }

    public static int drawLeft(GuiGraphicsExtractor graphics, Font font, String text,
                               int x, int y, int maxWidth, int color) {
        return drawLeft(graphics, font, Component.literal(text), x, y, maxWidth, color);
    }

    public static int drawCentred(GuiGraphicsExtractor graphics, Font font, String text,
                                  int centreX, int y, int maxWidth, int color) {
        int width = font.width(text);
        if (width <= maxWidth) {
            graphics.centeredText(font, Component.literal(text), centreX, y, color);
            return width;
        }
        return drawLeft(graphics, font, text, centreX - maxWidth / 2, y, maxWidth, color);
    }

    private static final long SPOTIFY_WAIT_MS = 1000L;

    private static final long SPOTIFY_END_MS = 600L;

    private static final float SPOTIFY_RETURN = 2.5F;

    public static int drawSpotify(GuiGraphicsExtractor graphics, Font font, Component text,
                                  int x, int y, int maxWidth, int color) {
        int width = font.width(text);
        if (maxWidth <= 0) return 0;

        if (width <= maxWidth) {
            graphics.text(font, text, x, y, color);
            return width;
        }

        int travel = width - maxWidth + TAIL;
        Clip.on(graphics, x, y - 1, x + maxWidth, y + font.lineHeight + 1);
        graphics.text(font, text, x - spotifyOffset(travel), y, color);
        Clip.off(graphics);
        return maxWidth;
    }

    public static int drawSpotifyCentred(GuiGraphicsExtractor graphics, Font font, Component text,
                                         int centreX, int y, int maxWidth, int color) {
        int width = font.width(text);
        if (width <= maxWidth) {
            graphics.text(font, text, centreX - width / 2, y, color);
            return width;
        }
        return drawSpotify(graphics, font, text, centreX - maxWidth / 2, y, maxWidth, color);
    }

    private static int spotifyOffset(int travel) {
        long outMs = (long) (travel / SPEED * 1000.0F);
        if (outMs <= 0) return 0;
        long backMs = Math.max(1L, (long) (outMs / SPOTIFY_RETURN));

        long cycle = SPOTIFY_WAIT_MS + outMs + SPOTIFY_END_MS + backMs;
        long at = System.currentTimeMillis() % cycle;

        if (at < SPOTIFY_WAIT_MS) return 0;
        at -= SPOTIFY_WAIT_MS;

        if (at < outMs) return (int) (travel * at / (float) outMs);
        at -= outMs;

        if (at < SPOTIFY_END_MS) return travel;
        at -= SPOTIFY_END_MS;

        return (int) (travel * (1.0F - at / (float) backMs));
    }

    public static int offsetFor(int travel) {
        return offset(travel);
    }

    private static int offset(int travel) {
        long scrollMs = (long) (travel / SPEED * 1000.0F);
        if (scrollMs <= 0) return 0;

        long cycle = HOLD_MS * 2 + scrollMs * 2;
        long at = System.currentTimeMillis() % cycle;

        if (at < HOLD_MS) return 0;
        at -= HOLD_MS;

        if (at < scrollMs) return (int) (travel * at / (float) scrollMs);
        at -= scrollMs;

        if (at < HOLD_MS) return travel;
        at -= HOLD_MS;

        return (int) (travel * (1.0F - at / (float) scrollMs));
    }
}
