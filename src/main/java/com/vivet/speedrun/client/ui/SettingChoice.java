package com.vivet.speedrun.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Supplier;

public class SettingChoice extends AbstractWidget implements SettingChip.Anchor {
    private static final int EDGE = 9;

    private final Supplier<Component> value;
    private final Supplier<Integer> accent;
    private final Component widest;
    private final Runnable onPress;

    private float hoverAmount;

    public SettingChoice(int x, int y, int width, int height, Component title,
                         Supplier<Component> value, Supplier<Integer> accent,
                         List<Component> options, Runnable onPress) {
        super(x, y, width, height, title);
        this.value = value;
        this.accent = accent;
        this.widest = widestOf(options);
        this.onPress = onPress;
    }

    private static Component widestOf(List<Component> options) {
        var font = Minecraft.getInstance().font;

        Component widest = Component.empty();
        for (Component option : options) {
            if (font.width(option) > font.width(widest)) widest = option;
        }
        return widest;
    }

    @Override
    public int chipX() {
        return getX() + getWidth() - EDGE - chipWidth();
    }

    @Override
    public int chipY() {
        return getY() + (getHeight() - SettingChip.H) / 2;
    }

    @Override
    public int chipWidth() {
        return SettingChip.width(this.widest);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                            float partialTick) {
        float target = isHoveredOrFocused() && this.active ? 1.0F : 0.0F;
        this.hoverAmount += (target - this.hoverAmount) * Math.min(1.0F, partialTick * 0.5F);

        var font = Minecraft.getInstance().font;

        int fill = Theme.mix(Theme.PANEL_SUNKEN, 0xFF2A2E3A, this.hoverAmount * 0.7F);
        Theme.roundedBox(graphics, getX(), getY(), getWidth(), getHeight(), SettingSwitch.RADIUS,
                fill);
        Theme.roundedBoxOutline(graphics, getX(), getY(), getWidth(), getHeight(),
                SettingSwitch.RADIUS, Theme.BORDER);

        graphics.text(font, getMessage(), getX() + 10, getY() + (getHeight() - 8) / 2,
                this.active ? Theme.TEXT : Theme.TEXT_FAINT);

        SettingChip.draw(graphics, chipX(), chipY(), chipWidth(), this.value.get(),
                this.active, overChip(mouseX, mouseY), this.accent.get());
    }

    public boolean overChip(double mouseX, double mouseY) {
        return mouseX >= chipX() && mouseX < chipX() + chipWidth()
                && mouseY >= chipY() && mouseY < chipY() + SettingChip.H;
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
