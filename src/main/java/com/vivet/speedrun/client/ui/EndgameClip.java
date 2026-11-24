package com.vivet.speedrun.client.ui;

import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.media.MediaClips;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class EndgameClip {
    private static final Identifier SHEET =
            Identifier.fromNamespaceAndPath(VivetSpeedrun.MOD_ID, "textures/gui/endgame_default.png");

    public static final int FRAME = 84;

    private static final int SHEET_FRAMES = 21;

    private static final int FRAMES = 16;

    private static final int FRAME_MS = 100;

    private static final int SHEET_W = FRAME * SHEET_FRAMES;

    private static final int CONTENT_X = 1;
    private static final int CONTENT_Y = 6;
    private static final int CONTENT_W = 78;
    private static final int CONTENT_H = 77;

    public static final int FRAME_PAD = 4;

    private EndgameClip() {
    }

    public static int widthAt(int scale) {
        return CONTENT_W * Math.max(1, scale) + FRAME_PAD * 2;
    }

    public static int heightAt(int scale) {
        return CONTENT_H * Math.max(1, scale) + FRAME_PAD * 2;
    }

    public static void draw(GuiGraphicsExtractor graphics, int x, int y, int scale, long intoMs,
                            int frameColor, String media) {
        int factor = Math.max(1, scale);
        int drawW = CONTENT_W * factor;
        int drawH = CONTENT_H * factor;

        Theme.roundedBox(graphics, x, y, widthAt(factor), heightAt(factor), 2, 0xFF090B10);
        Theme.roundedBoxOutline(graphics, x, y, widthAt(factor), heightAt(factor), 2, frameColor);

        long since = Math.max(0L, intoMs);

        int length = MediaClips.lengthMs(media);
        if (length > 0 && MediaClips.draw(graphics, media, x + FRAME_PAD, y + FRAME_PAD,
                                          drawW, drawH, since % length, 0xFFFFFFFF)) {
            return;
        }

        int index = (int) ((since / FRAME_MS) % FRAMES);

        graphics.blit(RenderPipelines.GUI_TEXTURED, SHEET, x + FRAME_PAD, y + FRAME_PAD,
                index * FRAME + CONTENT_X, CONTENT_Y, drawW, drawH, CONTENT_W, CONTENT_H,
                SHEET_W, FRAME, 0xFFFFFFFF);
    }
}
