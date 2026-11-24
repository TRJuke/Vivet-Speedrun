package com.vivet.speedrun.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

public class SettingSwitch extends AbstractWidget implements SettingChip.Anchor {
    public static final int RADIUS = 5;

    private static final int SWITCH_W = 24;
    private static final int SWITCH_H = 13;

    private static final int EDGE = 9;
    private static final int CHIP_GAP = 7;

    private final BooleanSupplier getter;
    private final Consumer<Boolean> setter;

    private final int maxLevel;
    private final IntSupplier level;
    private final Runnable onLevelPressed;

    private float hoverAmount;
    private float knobAmount;
    private boolean settled;

    public SettingSwitch(int x, int y, int width, int height, Component title,
                         BooleanSupplier getter, Consumer<Boolean> setter) {
        this(x, y, width, height, title, getter, setter, 1, () -> 1, null);
    }

    public SettingSwitch(int x, int y, int width, int height, Component title,
                         BooleanSupplier getter, Consumer<Boolean> setter,
                         int maxLevel, IntSupplier level, Runnable onLevelPressed) {
        super(x, y, width, height, title);
        this.getter = getter;
        this.setter = setter;
        this.maxLevel = maxLevel;
        this.level = level;
        this.onLevelPressed = onLevelPressed;
    }

    public boolean hasChip() {
        return this.maxLevel > 1 && this.onLevelPressed != null;
    }

    @Override
    public int chipX() {
        return getX() + getWidth() - EDGE - SWITCH_W - CHIP_GAP - chipWidth();
    }

    @Override
    public int chipY() {
        return getY() + (getHeight() - SettingChip.H) / 2;
    }

    @Override
    public int chipWidth() {
        return SettingChip.width(Component.literal(roman(this.maxLevel)));
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                            float partialTick) {
        boolean value = this.getter.getAsBoolean();
        if (!this.settled) {
            this.knobAmount = value ? 1.0F : 0.0F;
            this.settled = true;
        }
        this.knobAmount += ((value ? 1.0F : 0.0F) - this.knobAmount)
                * Math.min(1.0F, partialTick * 0.45F);

        float target = isHoveredOrFocused() && this.active ? 1.0F : 0.0F;
        this.hoverAmount += (target - this.hoverAmount) * Math.min(1.0F, partialTick * 0.5F);

        var font = Minecraft.getInstance().font;

        int fill = Theme.mix(Theme.PANEL_SUNKEN, 0xFF2A2E3A, this.hoverAmount * 0.7F);
        Theme.roundedBox(graphics, getX(), getY(), getWidth(), getHeight(), RADIUS, fill);
        Theme.roundedBoxOutline(graphics, getX(), getY(), getWidth(), getHeight(), RADIUS,
                value ? Theme.withAlpha(Theme.SUCCESS, 0x77) : Theme.BORDER);

        int text = this.active
                ? (value ? Theme.TEXT : Theme.TEXT_MUTED)
                : Theme.TEXT_FAINT;
        graphics.text(font, getMessage(), getX() + 10, getY() + (getHeight() - 8) / 2, text);

        if (hasChip()) {
            SettingChip.draw(graphics, chipX(), chipY(), chipWidth(),
                    Component.literal(roman(this.level.getAsInt())),
                    value && this.active, overChip(mouseX, mouseY), Theme.ACCENT);
        }
        drawSwitch(graphics, value);
    }

    private void drawSwitch(GuiGraphicsExtractor graphics, boolean value) {
        int x = getX() + getWidth() - EDGE - SWITCH_W;
        int y = getY() + (getHeight() - SWITCH_H) / 2;

        int track = Theme.mix(Theme.withAlpha(0xFF000000, 0x66),
                Theme.withAlpha(Theme.SUCCESS, this.active ? 0xBB : 0x55), this.knobAmount);
        Theme.roundedBox(graphics, x, y, SWITCH_W, SWITCH_H, SWITCH_H / 2, track);
        Theme.roundedBoxOutline(graphics, x, y, SWITCH_W, SWITCH_H, SWITCH_H / 2,
                value ? Theme.withAlpha(Theme.SUCCESS, 0xCC) : Theme.BORDER);

        int knob = SWITCH_H - 4;
        int knobX = x + 2 + (int) ((SWITCH_W - 4 - knob) * this.knobAmount);
        Theme.roundedBox(graphics, knobX, y + 2, knob, knob, knob / 2,
                value ? 0xFFFFFFFF : Theme.TEXT_MUTED);
    }

    public boolean overChip(double mouseX, double mouseY) {
        if (!hasChip()) return false;

        return mouseX >= chipX() && mouseX < chipX() + chipWidth()
                && mouseY >= chipY() && mouseY < chipY() + SettingChip.H;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (!this.active) return;

        playButtonClickSound(Minecraft.getInstance().getSoundManager());

        if (overChip(event.x(), event.y()) && this.getter.getAsBoolean()) {
            this.onLevelPressed.run();
            return;
        }
        this.setter.accept(!this.getter.getAsBoolean());
    }

    public static String roman(int level) {
        return switch (Math.max(1, Math.min(10, level))) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            default -> "X";
        };
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
