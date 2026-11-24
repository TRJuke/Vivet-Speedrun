package com.vivet.speedrun.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class SettingAction extends AbstractWidget {
    private final Runnable onPress;

    private float hoverAmount;

    public SettingAction(int x, int y, int width, int height, Component title, Runnable onPress) {
        super(x, y, width, height, title);
        this.onPress = onPress;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                            float partialTick) {
        float target = isHoveredOrFocused() && this.active ? 1.0F : 0.0F;
        this.hoverAmount += (target - this.hoverAmount) * Math.min(1.0F, partialTick * 0.5F);

        var font = Minecraft.getInstance().font;

        Theme.roundedBox(graphics, getX(), getY(), getWidth(), getHeight(), SettingSwitch.RADIUS,
                Theme.mix(Theme.withAlpha(0xFF000000, 0x44), Theme.PANEL_SUNKEN,
                        this.hoverAmount));
        Theme.roundedBoxOutline(graphics, getX(), getY(), getWidth(), getHeight(),
                SettingSwitch.RADIUS,
                Theme.mix(Theme.BORDER, Theme.BORDER_STRONG, this.hoverAmount));

        graphics.text(font, getMessage(),
                getX() + (getWidth() - font.width(getMessage())) / 2,
                getY() + (getHeight() - 8) / 2,
                this.active
                        ? Theme.mix(Theme.TEXT_FAINT, Theme.TEXT_MUTED, this.hoverAmount)
                        : Theme.TEXT_FAINT);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (!this.active) return;

        playButtonClickSound(Minecraft.getInstance().getSoundManager());
        this.onPress.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
