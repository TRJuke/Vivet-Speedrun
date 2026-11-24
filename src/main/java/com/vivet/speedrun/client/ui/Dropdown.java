package com.vivet.speedrun.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

public class Dropdown extends AbstractWidget {
    private final Supplier<Component> value;
    private final Supplier<Integer> valueColor;
    private final Supplier<Boolean> open;
    private final Runnable onToggle;

    private float hoverAmount;

    public Dropdown(int x, int y, int width, int height, Component label,
                    Supplier<Component> value, Supplier<Integer> valueColor,
                    Supplier<Boolean> open, Runnable onToggle) {
        super(x, y, width, height, label);
        this.value = value;
        this.valueColor = valueColor;
        this.open = open;
        this.onToggle = onToggle;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                            float partialTick) {
        boolean expanded = Boolean.TRUE.equals(this.open.get());
        float target = isHoveredOrFocused() || expanded ? 1.0F : 0.0F;
        this.hoverAmount += (target - this.hoverAmount) * Math.min(1.0F, partialTick * 0.5F);

        int accent = this.valueColor.get();
        var font = Minecraft.getInstance().font;

        int fill = Theme.mix(Theme.PANEL_SUNKEN, Theme.PANEL_RAISED, this.hoverAmount);
        Theme.roundedRect(graphics, getX(), getY(), getWidth(), getHeight(), fill);
        Theme.roundedOutline(graphics, getX(), getY(), getWidth(), getHeight(),
                Theme.mix(Theme.BORDER, Theme.withAlpha(accent, 0xCC), this.hoverAmount));

        graphics.text(font, getMessage(), getX() + 12, getY() + (getHeight() - 8) / 2,
                Theme.TEXT_MUTED);

        Component current = this.value.get();
        int label = font.width(getMessage());
        int valueX = getX() + 12 + (label == 0 ? 0 : label + 8);
        graphics.text(font, current, valueX, getY() + (getHeight() - 8) / 2, accent);

        drawChevron(graphics, getX() + getWidth() - 13, getY() + getHeight() / 2, expanded, accent);
    }

    private void drawChevron(GuiGraphicsExtractor graphics, int centerX, int centerY,
                             boolean pointUp, int color) {
        int rows = 4;
        for (int i = 0; i < rows; i++) {
            int half = rows - i;
            int y = pointUp ? centerY + i - rows / 2 : centerY - i + rows / 2;
            Theme.fill(graphics, centerX - half, y, centerX + half, y + 1, color);
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        playButtonClickSound(Minecraft.getInstance().getSoundManager());
        this.onToggle.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
