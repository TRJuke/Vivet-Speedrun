package com.vivet.speedrun.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class FlatButton extends AbstractWidget {
    public enum Style {
        PRIMARY,

        SECONDARY,

        DANGER,

        READY,

        GHOST,

        INVISIBLE,
    }

    private final Runnable onPress;

    private Style style;

    private float hoverAmount;

    public FlatButton(int x, int y, int width, int height, Component message, Runnable onPress) {
        this(x, y, width, height, message, onPress, Style.SECONDARY);
    }

    public FlatButton(int x, int y, int width, int height, Component message, Runnable onPress,
                      Style style) {
        super(x, y, width, height, message);
        this.onPress = onPress;
        this.style = style;
    }

    public void setStyle(Style style) {
        this.style = style;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                            float partialTick) {
        boolean hovered = isHoveredOrFocused() && this.active;
        float target = hovered ? 1.0F : 0.0F;
        this.hoverAmount += (target - this.hoverAmount) * Math.min(1.0F, partialTick * 0.5F);

        if (this.style == Style.INVISIBLE) return;

        int fill = baseFill();
        int hoverFill = hoverFill();
        int background = Theme.mix(fill, hoverFill, this.hoverAmount);
        int border = Theme.mix(Theme.BORDER, borderHover(), this.hoverAmount);

        if (!this.active) {
            background = Theme.fade(fill, 0.4F);
            border = Theme.fade(Theme.BORDER, 0.5F);
        }

        Theme.roundedRect(graphics, getX(), getY(), getWidth(), getHeight(), background);
        Theme.roundedOutline(graphics, getX(), getY(), getWidth(), getHeight(), border);

        if (this.style != Style.GHOST) {
            Theme.fill(graphics, getX() + 2, getY() + 1, getX() + getWidth() - 2, getY() + 2,
                    Theme.fade(Theme.HIGHLIGHT, this.active ? 1.0F : 0.4F));
        }

        int textColor = this.active ? textColor() : Theme.TEXT_FAINT;
        graphics.centeredText(
                Minecraft.getInstance().font,
                getMessage(),
                getX() + getWidth() / 2,
                getY() + (getHeight() - 8) / 2,
                textColor);
    }

    private int baseFill() {
        return switch (this.style) {
            case PRIMARY -> Theme.withAlpha(Theme.ACCENT, 0xCC);
            case READY -> Theme.withAlpha(Theme.SUCCESS, 0xCC);
            case DANGER -> Theme.withAlpha(Theme.DANGER, 0x99);
            case SECONDARY -> Theme.PANEL_RAISED;
            case GHOST, INVISIBLE -> 0x00000000;
        };
    }

    private int hoverFill() {
        return switch (this.style) {
            case PRIMARY -> Theme.withAlpha(Theme.ACCENT, 0xFF);
            case READY -> Theme.withAlpha(Theme.SUCCESS, 0xFF);
            case DANGER -> Theme.withAlpha(Theme.DANGER, 0xDD);
            case SECONDARY -> Theme.mix(Theme.PANEL_RAISED, 0xFF3A4152, 1.0F);
            case GHOST -> Theme.withAlpha(0xFFFFFFFF, 0x1A);
            case INVISIBLE -> 0x00000000;
        };
    }

    private int borderHover() {
        return switch (this.style) {
            case PRIMARY -> Theme.withAlpha(Theme.ACCENT, 0xFF);
            case READY -> Theme.withAlpha(Theme.SUCCESS, 0xFF);
            case DANGER -> Theme.withAlpha(Theme.DANGER, 0xFF);
            default -> Theme.BORDER_STRONG;
        };
    }

    private int textColor() {
        return switch (this.style) {
            case PRIMARY, DANGER, READY -> 0xFFFFFFFF;
            default -> Theme.TEXT;
        };
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (!this.active || this.onPress == null) return;
        playButtonClickSound(Minecraft.getInstance().getSoundManager());
        this.onPress.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
