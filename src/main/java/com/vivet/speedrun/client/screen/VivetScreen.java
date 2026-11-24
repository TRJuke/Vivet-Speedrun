package com.vivet.speedrun.client.screen;

import com.vivet.speedrun.client.ui.UiScale;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public abstract class VivetScreen extends Screen {
    private float scale = 1.0F;

    protected VivetScreen(Component title) {
        super(title);
    }

    protected final void sealUnderList() {
        for (var child : children()) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
                widget.active = false;
            }
        }
    }

    protected final float uiScale() {
        return this.scale;
    }

    @Override
    protected void init() {
        super.init();

        this.scale = UiScale.screenFactor();
        if (this.minecraft == null || this.minecraft.getWindow() == null) return;

        this.width = UiScale.virtual(this.minecraft.getWindow().getGuiScaledWidth(), this.scale);
        this.height = UiScale.virtual(this.minecraft.getWindow().getGuiScaledHeight(), this.scale);
    }

    @Override
    public final void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                         float partialTick) {
        this.scale = UiScale.screenFactor();

        UiScale.push(graphics, this.scale);
        try {
            drawScaled(graphics, scaled(mouseX), scaled(mouseY), partialTick);

            com.vivet.speedrun.client.hud.Notices.render(graphics, this.width);
        } finally {
            UiScale.pop(graphics);
        }
    }

    protected void drawScaled(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                              float partialTick) {
        drawWidgets(graphics, mouseX, mouseY, partialTick);
    }

    protected final void drawWidgets(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                     float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private int scaled(int value) {
        return this.scale == 1.0F ? value : Math.round(value / this.scale);
    }

    private double scaled(double value) {
        return this.scale == 1.0F ? value : value / this.scale;
    }

    private MouseButtonEvent scaled(MouseButtonEvent event) {
        if (this.scale == 1.0F) return event;
        return new MouseButtonEvent(scaled(event.x()), scaled(event.y()), event.buttonInfo());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return super.mouseClicked(scaled(event), doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return super.mouseReleased(scaled(event));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return super.mouseDragged(scaled(event), scaled(dragX), scaled(dragY));
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(scaled(mouseX), scaled(mouseY));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        return super.mouseScrolled(scaled(mouseX), scaled(mouseY), deltaX, deltaY);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return super.isMouseOver(scaled(mouseX), scaled(mouseY));
    }
}
