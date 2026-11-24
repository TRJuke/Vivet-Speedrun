package com.vivet.speedrun.client.ui;

import com.vivet.speedrun.VivetSpeedrun;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class InboxIcons {
    private static final Identifier SHEET = Identifier.fromNamespaceAndPath(
            VivetSpeedrun.MOD_ID, "textures/gui/inbox_buttons.png");

    private static final int SHEET_W = 128;
    private static final int SHEET_H = 72;

    public static final int W = 23;
    public static final int H = 22;

    private static final int PITCH = 24;

    private static final int DOT = 0xFFEB4A46;

    public enum Kind {
        NOTICES,

        SYSTEM,

        NEWS,
    }

    private InboxIcons() {
    }

    public static Kind kindOf(String kind) {
        return switch (kind == null ? "" : kind) {
            case "friend_request", "friend_accepted", "race_invite", "welcome" -> Kind.NOTICES;
            case "report", "ban", "cosmetic", "message", "coins", "achievement", "refund" -> Kind.SYSTEM;
            default -> Kind.NEWS;
        };
    }

    public static void button(GuiGraphicsExtractor graphics, Kind kind, int x, int y,
                              boolean hovered, boolean open, boolean dot) {
        UiButtons.Tone tone = hovered ? UiButtons.WHITE : UiButtons.GREY;
        int base = open ? Theme.PLATE_LIT : Theme.PLATE;

        Theme.roundedBox(graphics, x, y, W, H, 5,
                Theme.mix(base, tone.icon(), open ? 0.22F : (hovered ? 0.14F : 0.08F)));

        layer(graphics, x, y, kind.ordinal() * PITCH, 0, tone.border());
        layer(graphics, x, y, kind.ordinal() * PITCH, PITCH, tone.icon());

        if (dot) layer(graphics, x, y, 0, PITCH * 2, DOT);
    }

    public static void icon(GuiGraphicsExtractor graphics, Kind kind, int cx, int cy, int colour) {
        layer(graphics, cx - W / 2, cy - H / 2, kind.ordinal() * PITCH, PITCH, colour);
    }

    private static void layer(GuiGraphicsExtractor graphics, int x, int y, int u, int v,
                              int colour) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, SHEET, x, y, u, v, W, H, W, H,
                SHEET_W, SHEET_H, colour);
    }
}
