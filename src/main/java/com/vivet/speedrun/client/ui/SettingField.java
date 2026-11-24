package com.vivet.speedrun.client.ui;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class SettingField extends AbstractWidget {
    private static final int EDGE = 9;

    private static final int BOX_H = 14;
    private static final int PAD = 5;

    private final IntSupplier getter;
    private final IntConsumer setter;
    private final int min;
    private final int max;
    private final String suffix;
    private final int maxDigits;

    private String typing = "";
    private boolean editing;

    private float hoverAmount;

    public SettingField(int x, int y, int width, int height, Component title,
                        IntSupplier getter, IntConsumer setter, int min, int max, String suffix) {
        super(x, y, width, height, title);
        this.getter = getter;
        this.setter = setter;
        this.min = min;
        this.max = max;
        this.suffix = suffix == null ? "" : suffix;
        this.maxDigits = String.valueOf(Math.max(Math.abs(min), Math.abs(max))).length();
    }

    private int boxWidth() {
        var font = Minecraft.getInstance().font;
        return font.width("0".repeat(this.maxDigits) + this.suffix) + PAD * 2;
    }

    private int boxX() {
        return getX() + getWidth() - EDGE - boxWidth();
    }

    private int boxY() {
        return getY() + (getHeight() - BOX_H) / 2;
    }

    private String shown() {
        return this.editing ? this.typing : String.valueOf(this.getter.getAsInt());
    }

    public void commit() {
        if (!this.editing) return;
        setEditing(false);

        if (this.typing.isEmpty()) return;

        try {
            int typed = Integer.parseInt(this.typing);
            this.setter.accept(Math.max(this.min, Math.min(this.max, typed)));
        } catch (NumberFormatException ignored) {
        }
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                            float partialTick) {
        float target = isHoveredOrFocused() && this.active ? 1.0F : 0.0F;
        this.hoverAmount += (target - this.hoverAmount) * Math.min(1.0F, partialTick * 0.5F);

        var font = Minecraft.getInstance().font;

        int fill = Theme.mix(Theme.PANEL_SUNKEN, 0xFF2A2E3A, this.hoverAmount * 0.7F);
        Theme.roundedBox(graphics, getX(), getY(), getWidth(), getHeight(),
                SettingSwitch.RADIUS, fill);
        Theme.roundedBoxOutline(graphics, getX(), getY(), getWidth(), getHeight(),
                SettingSwitch.RADIUS, Theme.BORDER);

        graphics.text(font, getMessage(), getX() + EDGE + 2,
                getY() + (getHeight() - 8) / 2, this.active ? Theme.TEXT : Theme.TEXT_MUTED);

        int boxX = boxX();
        int boxW = boxWidth();

        Theme.roundedBox(graphics, boxX, boxY(), boxW, BOX_H, 3, Theme.PANEL);
        Theme.roundedBoxOutline(graphics, boxX, boxY(), boxW, BOX_H, 3,
                this.editing ? Theme.ACCENT : Theme.BORDER);

        String text = shown() + this.suffix;
        int textX = boxX + boxW - PAD - font.width(text);
        graphics.text(font, Component.literal(text), textX, boxY() + (BOX_H - 8) / 2,
                this.editing ? Theme.TEXT : Theme.TEXT_MUTED);

        if (this.editing) {
            int caretX = textX + font.width(shown());
            Theme.fill(graphics, caretX, boxY() + 3, caretX + 1, boxY() + BOX_H - 3, Theme.ACCENT);
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        setEditing(true);
        this.typing = "";
    }

    private void setEditing(boolean editing) {
        if (this.editing == editing) return;
        this.editing = editing;
        Minecraft.getInstance().onTextInputFocusChange(this, editing);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (!this.editing) return false;

        int codepoint = event.codepoint();
        if (codepoint < '0' || codepoint > '9') return false;
        if (this.typing.length() >= this.maxDigits) return true;

        char digit = (char) codepoint;

        this.typing = this.typing.equals("0") ? String.valueOf(digit) : this.typing + digit;
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (!this.editing) return false;

        int key = event.key();
        if (key == InputConstants.KEY_BACKSPACE) {
            if (!this.typing.isEmpty()) {
                this.typing = this.typing.substring(0, this.typing.length() - 1);
            }
            return true;
        }

        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER
                || key == InputConstants.KEY_ESCAPE) {
            commit();
            return key != InputConstants.KEY_ESCAPE;
        }

        return false;
    }

    @Override
    public void setFocused(boolean focused) {
        if (!focused) commit();
        super.setFocused(focused);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
