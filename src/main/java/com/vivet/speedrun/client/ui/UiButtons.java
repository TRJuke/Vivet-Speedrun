package com.vivet.speedrun.client.ui;

import com.vivet.speedrun.VivetSpeedrun;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class UiButtons {
    private static final Identifier SHEET = Identifier.fromNamespaceAndPath(
            VivetSpeedrun.MOD_ID, "textures/gui/ui_buttons.png");

    private static final int SHEET_W = 256;
    private static final int SHEET_H = 144;

    public static final int SIDE = 23;

    private static final int PITCH = 24;
    private static final int COLS = 8;

    private static final int ICON_LAYER = 48;

    public enum Kind {
        PLUS,

        ADD,

        MINUS,

        REMOVE,

        SEARCH,

        MESSAGE,

        PEOPLE,

        PERSON_PLUS,

        BACK,

        BLOCK,

        UNBLOCK,

        REPORT,

        BLANK,

        WATCH;

        private boolean removal() {
            return this == MINUS || this == REMOVE;
        }
    }

    public record Tone(int border, int icon) {
    }

    public static final Tone GREY = new Tone(0xFF797A87, 0xFFBDBECD);

    public static final Tone WHITE = new Tone(0xFFC9CAD6, 0xFFFFFFFF);

    public static final Tone RED = new Tone(0xFFBC3D3A, 0xFFEB4A46);

    public static final Tone AMBER = new Tone(0xFFB87A28, 0xFFEBA33A);

    public static final Tone RED_LIT = new Tone(0xFFE0524E, 0xFFFF6B66);

    public static final Tone GREEN = new Tone(0xFF54865B, 0xFF52B260);

    public static final Tone DIM = new Tone(0xFF44454E, 0xFF63646F);

    private UiButtons() {
    }

    public static Tone toneOf(Kind kind, boolean hovered, boolean live) {
        if (!live) return DIM;

        if (kind.removal()) return hovered ? RED_LIT : GREY;
        if (hovered) return WHITE;

        return switch (kind) {
            case PLUS -> GREEN;

            case BLOCK -> RED;
            case REPORT -> AMBER;
            default -> GREY;
        };
    }

    private static final Identifier EYE = Identifier.fromNamespaceAndPath(
            VivetSpeedrun.MOD_ID, "textures/gui/spectate.png");
    private static final int EYE_SIDE = 19;
    private static final int EYE_INSET = (SIDE - EYE_SIDE) / 2;

    public static void draw(GuiGraphicsExtractor graphics, Kind kind, int x, int y, Tone tone) {
        if (kind == Kind.WATCH) {
            draw(graphics, Kind.BLANK, x, y, tone);
            graphics.blit(RenderPipelines.GUI_TEXTURED, EYE, x + EYE_INSET, y + EYE_INSET, 0, 0,
                    EYE_SIDE, EYE_SIDE, EYE_SIDE, EYE_SIDE, EYE_SIDE, EYE_SIDE,
                    tone == DIM ? 0xFF6E6E6E : 0xFFFFFFFF);
            return;
        }

        int u = (kind.ordinal() % COLS) * PITCH;
        int v = (kind.ordinal() / COLS) * PITCH;

        layer(graphics, x, y, u, v, tone.border());
        layer(graphics, x, y, u, v + ICON_LAYER, tone.icon());
    }

    public static void drawAt(GuiGraphicsExtractor graphics, Kind kind, int cx, int cy, Tone tone) {
        draw(graphics, kind, cx - SIDE / 2, cy - SIDE / 2, tone);
    }

    private static void layer(GuiGraphicsExtractor graphics, int x, int y, int u, int v,
                              int colour) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, SHEET, x, y, u, v, SIDE, SIDE, SIDE, SIDE,
                SHEET_W, SHEET_H, colour);
    }

    public static void button(GuiGraphicsExtractor graphics, Kind kind, int x, int y,
                              boolean hovered, boolean live, boolean open) {
        Tone tone = toneOf(kind, hovered, live);
        int base = open ? Theme.PLATE_LIT : Theme.PLATE;

        Theme.roundedBox(graphics, x, y, SIDE, SIDE, 5,
                Theme.mix(base, tone.icon(), open ? 0.22F : (hovered ? 0.14F : 0.08F)));
        draw(graphics, kind, x, y, tone);
    }

    public static Kind blockSign(boolean blocked) {
        return blocked ? Kind.BLOCK : Kind.UNBLOCK;
    }
}
