package com.vivet.speedrun.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

public class Chip extends AbstractWidget {
    private final BooleanSupplier on;
    private final int accent;
    private final Runnable onPress;

    private float hoverAmount;

    public Chip(int x, int y, int width, int height, Component label, int accent,
                BooleanSupplier on, Runnable onPress) {
        super(x, y, width, height, label);
        this.accent = accent;
        this.on = on;
        this.onPress = onPress;
    }

    public static int widthFor(Component label) {
        return Minecraft.getInstance().font.width(label) + 18;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                            float partialTick) {
        boolean lit = this.on.getAsBoolean();
        float target = isHoveredOrFocused() ? 1.0F : 0.0F;
        this.hoverAmount += (target - this.hoverAmount) * Math.min(1.0F, partialTick * 0.6F);

        var font = Minecraft.getInstance().font;
        int x = getX();
        int y = getY();

        int fill = lit
                ? Theme.withAlpha(this.accent, 0x44)
                : Theme.withAlpha(0xFFFFFFFF, (int) (0x08 + this.hoverAmount * 0x10));

        Theme.roundedRect(graphics, x, y, getWidth(), getHeight(), fill);
        Theme.roundedOutline(graphics, x, y, getWidth(), getHeight(),
                lit ? Theme.withAlpha(this.accent, 0xEE)
                    : Theme.mix(Theme.BORDER, Theme.withAlpha(this.accent, 0x88),
                            this.hoverAmount));

        graphics.centeredText(font, getMessage(), x + getWidth() / 2,
                y + (getHeight() - 8) / 2,
                lit ? Theme.TEXT : Theme.mix(Theme.TEXT_FAINT, Theme.TEXT_MUTED, this.hoverAmount));
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
