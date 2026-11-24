package com.vivet.speedrun.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

final class SpriteEmblem {
    private SpriteEmblem() {
    }

    private static final int SIDE = 192;
    private static final int[] DEST = new int[SIDE * SIDE];
    private static final String[] KEYS = {
            "", "coal", "copper", "iron", "gold", "emerald", "diamond", "", "", "",
            "speed_prince", "speed_knight", "speed_squire",
    };

    static void draw(GuiGraphicsExtractor graphics, int cx, int cy, String key, int box,
                     int tint) {
        switch (key == null ? "" : key) {
            case "speed_god" -> play(graphics, cx, cy, box, tint, key, SpeedGodSprite.FRAMES,
                    SpeedGodSprite.FRAME_MS, SpeedGodSprite.CONTENT, SpeedGodSprite.FRAME_OFF,
                    SpeedGodSprite.RUNS, SpeedGodSprite.PAL);
            case "valeska" -> play(graphics, cx, cy, box, tint, key, XpGoddessSprite.FRAMES,
                    XpGoddessSprite.FRAME_MS, XpGoddessSprite.CONTENT, XpGoddessSprite.FRAME_OFF,
                    XpGoddessSprite.RUNS, XpGoddessSprite.PAL);
            default -> {
            }
        }
    }

    static boolean knows(String key) {
        return "speed_god".equals(key) || "valeska".equals(key);
    }

    static void draw(GuiGraphicsExtractor graphics, int cx, int cy, int index, int box,
                     int tint) {
        switch (index) {
            case 1 -> play(graphics, cx, cy, box, tint, index, BrimstoneSprite.FRAMES, BrimstoneSprite.FRAME_MS,
                    BrimstoneSprite.CONTENT, BrimstoneSprite.FRAME_OFF, BrimstoneSprite.RUNS,
                    BrimstoneSprite.PAL);
            case 2 -> play(graphics, cx, cy, box, tint, index, CopperSprite.FRAMES, CopperSprite.FRAME_MS,
                    CopperSprite.CONTENT, CopperSprite.FRAME_OFF, CopperSprite.RUNS,
                    CopperSprite.PAL);
            case 3 -> play(graphics, cx, cy, box, tint, index, IronSprite.FRAMES, IronSprite.FRAME_MS,
                    IronSprite.CONTENT, IronSprite.FRAME_OFF, IronSprite.RUNS, IronSprite.PAL);
            case 4 -> play(graphics, cx, cy, box, tint, index, GoldSprite.FRAMES, GoldSprite.FRAME_MS,
                    GoldSprite.CONTENT, GoldSprite.FRAME_OFF, GoldSprite.RUNS, GoldSprite.PAL);
            case 5 -> play(graphics, cx, cy, box, tint, index, EmeraldSprite.FRAMES, EmeraldSprite.FRAME_MS,
                    EmeraldSprite.CONTENT, EmeraldSprite.FRAME_OFF, EmeraldSprite.RUNS,
                    EmeraldSprite.PAL);
            case 6 -> play(graphics, cx, cy, box, tint, index, DiamondSprite.FRAMES, DiamondSprite.FRAME_MS,
                    DiamondSprite.CONTENT, DiamondSprite.FRAME_OFF, DiamondSprite.RUNS,
                    DiamondSprite.PAL);

            case 10 -> play(graphics, cx, cy, box, tint, index, SpeedPrinceSprite.FRAMES,
                    SpeedPrinceSprite.FRAME_MS, SpeedPrinceSprite.CONTENT,
                    SpeedPrinceSprite.FRAME_OFF, SpeedPrinceSprite.RUNS, SpeedPrinceSprite.PAL);
            case 11 -> play(graphics, cx, cy, box, tint, index, SpeedKnightSprite.FRAMES,
                    SpeedKnightSprite.FRAME_MS, SpeedKnightSprite.CONTENT,
                    SpeedKnightSprite.FRAME_OFF, SpeedKnightSprite.RUNS, SpeedKnightSprite.PAL);
            case 12 -> play(graphics, cx, cy, box, tint, index, SpeedSquireSprite.FRAMES,
                    SpeedSquireSprite.FRAME_MS, SpeedSquireSprite.CONTENT,
                    SpeedSquireSprite.FRAME_OFF, SpeedSquireSprite.RUNS, SpeedSquireSprite.PAL);
            default -> {
            }
        }
    }

    private static void play(GuiGraphicsExtractor graphics, int cx, int cy, int box, int tint,
                             int index,
                             int frames, int frameMs, int[] content,
                             int[] offsets, int[] runs, int[] palette) {
        play(graphics, cx, cy, box, tint, KEYS[index], frames, frameMs, content, offsets, runs,
                palette);
    }

    private static void play(GuiGraphicsExtractor graphics, int cx, int cy, int box, int tint,
                             String key,
                             int frames, int frameMs, int[] content,
                             int[] offsets, int[] runs, int[] palette) {
        int frame = (int) (System.currentTimeMillis() / frameMs % frames);

        float scale = Math.min(box / (float) content[2], box / (float) content[3]);
        float focusX = content[0] + content[2] / 2F;
        float focusY = content[1] + content[3] / 2F;

        int left = Math.round((content[0] - focusX) * scale);
        int top = Math.round((content[1] - focusY) * scale);
        int dw = Math.round((content[0] + content[2] - focusX) * scale) - left;
        int dh = Math.round((content[1] + content[3] - focusY) * scale) - top;

        if (dw > 0 && dh > 0 && dw <= SIDE && dh <= SIDE) {
            java.util.Arrays.fill(DEST, 0, dw * dh, 0);
            paint(DEST, dw, dh, left, top, scale, focusX, focusY, offsets, runs, palette, frame);

            Identifier id = BadgeTexture.present(key + "_" + dw + "x" + dh,
                    DEST, dw, dh, frame);
            if (id != null) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, id,
                        cx + left, cy + top, 0F, 0F, dw, dh, dw, dh, tint);
                return;
            }
        }

        for (int i = offsets[frame]; i < offsets[frame + 1]; i++) {
            int run = runs[i];
            int x = run & 0x7F;
            int y = (run >> 7) & 0x7F;
            int w = (run >> 14) & 0x7F;
            int h = (run >> 21) & 0x1F;

            Theme.fill(graphics, cx + Math.round((x - focusX) * scale),
                    cy + Math.round((y - focusY) * scale),
                    cx + Math.round((x + w - focusX) * scale),
                    cy + Math.round((y + h - focusY) * scale),
                    Theme.darken(palette[(run >>> 26) & 0x3F], ((tint >> 16) & 0xFF) / 255.0F));
        }
    }

    private static void paint(int[] dest, int dw, int dh, int left, int top, float scale,
                              float focusX, float focusY, int[] offsets, int[] runs,
                              int[] palette, int frame) {
        for (int i = offsets[frame]; i < offsets[frame + 1]; i++) {
            int run = runs[i];
            int x = run & 0x7F;
            int y = (run >> 7) & 0x7F;
            int w = (run >> 14) & 0x7F;
            int h = (run >> 21) & 0x1F;
            int colour = palette[(run >>> 26) & 0x3F];

            int x0 = Math.max(0, Math.round((x - focusX) * scale) - left);
            int y0 = Math.max(0, Math.round((y - focusY) * scale) - top);
            int x1 = Math.min(dw, Math.round((x + w - focusX) * scale) - left);
            int y1 = Math.min(dh, Math.round((y + h - focusY) * scale) - top);

            for (int py = y0; py < y1; py++) {
                int row = py * dw;
                for (int px = x0; px < x1; px++) dest[row + px] = colour;
            }
        }
    }
}
