package com.vivet.speedrun.client.ui;

import com.vivet.speedrun.client.Cues;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class AccountTile extends AbstractWidget {
    public static final int WIDTH = 132;
    public static final int HEIGHT = 46;

    public enum Facing {
        RIGHT,

        LEFT,
    }

    private static final int ICON_COLUMN = 48;

    private final VanillaIcon.Kind icon;
    private final int accent;
    private final Supplier<Component> note;
    private final Runnable onPress;
    private final Facing facing;

    private float hoverAmount;

    private IntSupplier noteTint;

    private IntSupplier noteDot;

    private boolean rule = true;

    private int fitEdge;
    private int fitMax;

    private static final int FIT_EDGE_AIR = 12;
    private static final int FIT_ICON_AIR = 4;

    public AccountTile(int x, int y, int width, Facing facing, Component title,
                       Supplier<Component> note, VanillaIcon.Kind icon, int accent,
                       Runnable onPress) {
        super(x, y, width, HEIGHT, title);
        this.facing = facing;
        this.note = note;
        this.icon = icon;
        this.accent = accent;
        this.onPress = onPress;
    }

    public AccountTile noteTint(IntSupplier tint) {
        this.noteTint = tint;
        return this;
    }

    public AccountTile noteDot(IntSupplier dot) {
        this.noteDot = dot;
        return this;
    }

    public AccountTile noRule() {
        this.rule = false;
        return this;
    }

    public AccountTile fit() {
        this.fitMax = getWidth();
        this.fitEdge = this.facing == Facing.LEFT ? getX() + getWidth() : getX();
        return this;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                            float partialTick) {
        float target = isHoveredOrFocused() ? 1.0F : 0.0F;
        this.hoverAmount += (target - this.hoverAmount) * Math.min(1.0F, partialTick * 0.5F);
        float lit = this.hoverAmount;

        Font font = Minecraft.getInstance().font;
        Component says = this.note == null ? null : this.note.get();
        int dot = this.noteDot == null ? 0 : this.noteDot.getAsInt();
        if (this.fitMax > 0) fitTo(font, says, dot);

        int x = getX();
        int y = getY();
        int width = getWidth();
        int height = getHeight();

        Theme.roundedBox(graphics, x, y, width, height, 4,
                Theme.mix(Theme.TITLE_PLATE, Theme.TITLE_PLATE_LIT, lit));

        int edge = Theme.mix(Theme.PLATE_EDGE, Theme.PLATE_EDGE_LIT, lit);
        Theme.roundedBoxOutline(graphics, x, y, width, height, 4, edge);

        boolean left = this.facing == Facing.LEFT;

        int iconCx = left ? x + ICON_COLUMN / 2 : x + width - ICON_COLUMN / 2;
        int ruleX = left ? x + ICON_COLUMN : x + width - ICON_COLUMN;

        if (this.rule) {
            Theme.fill(graphics, ruleX, y + 7, ruleX + 1, y + height - 7, edge);
        }

        int titleColor = Theme.mix(this.accent, Theme.TEXT, lit * 0.35F);
        int noteColor = this.noteTint != null ? this.noteTint.getAsInt()
                : Theme.mix(Theme.TEXT_MUTED, Theme.TEXT, lit);

        int textLeft = this.fitMax > 0 ? (left ? ruleX + FIT_ICON_AIR : x + FIT_EDGE_AIR)
                : (left ? ruleX + 1 : x) + 9;
        int textRight = this.fitMax > 0 ? (left ? x + width - FIT_EDGE_AIR : ruleX - FIT_ICON_AIR)
                : (left ? x + width : ruleX) - 9;
        int cx = (textLeft + textRight) / 2;

        if (says == null) {
            graphics.centeredText(font, getMessage(), cx, y + (height - 8) / 2, titleColor);
        } else {
            String[] lines = says.getString().split("\n");
            int rows = 1 + lines.length;
            int step = rows <= 2 ? 14 : 12;
            int top = y + (height - (step * (rows - 1) + 8)) / 2;

            graphics.centeredText(font, getMessage(), cx, top, titleColor);
            for (int i = 0; i < lines.length; i++) {
                int lineY = top + step * (i + 1);
                if (dot != 0 && i == 0) {
                    int start = cx - (7 + font.width(lines[i])) / 2;
                    Theme.fill(graphics, start, lineY + 2, start + 3, lineY + 5, dot);
                    graphics.text(font, Component.literal(lines[i]), start + 7, lineY, noteColor, true);
                } else {
                    graphics.centeredText(font, Component.literal(lines[i]), cx, lineY, noteColor);
                }
            }
        }

        VanillaIcon.draw(graphics, this.icon, iconCx, y + height / 2, 32);
    }

    private void fitTo(Font font, Component says, int dot) {
        int words = font.width(getMessage());
        if (says != null) {
            String[] lines = says.getString().split("\n");
            for (int i = 0; i < lines.length; i++) {
                words = Math.max(words, font.width(lines[i]) + (dot != 0 && i == 0 ? 7 : 0));
            }
        }
        int width = Math.min(this.fitMax, ICON_COLUMN + FIT_ICON_AIR + words + FIT_EDGE_AIR);
        setWidth(width);
        setX(this.facing == Facing.LEFT ? this.fitEdge - width : this.fitEdge);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (!this.active) return;

        if (this.onPress == null) {
            Cues.nothingHappened();
            return;
        }

        playButtonClickSound(Minecraft.getInstance().getSoundManager());
        this.onPress.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
