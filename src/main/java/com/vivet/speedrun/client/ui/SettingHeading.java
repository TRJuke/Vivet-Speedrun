package com.vivet.speedrun.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class SettingHeading extends AbstractWidget {
    public static final int H = 16;

    private final int accent;

    public SettingHeading(int x, int y, int width, Component title, int accent) {
        super(x, y, width, H, title);
        this.accent = accent;
        this.active = false;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                            float partialTick) {
        var font = Minecraft.getInstance().font;

        int textY = getY() + getHeight() - 9;
        graphics.text(font, getMessage(), getX(), textY, this.accent);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return false;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
