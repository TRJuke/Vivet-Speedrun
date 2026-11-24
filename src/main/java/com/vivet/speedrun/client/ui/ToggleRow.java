package com.vivet.speedrun.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class ToggleRow extends AbstractWidget {
    private static final int SWITCH_WIDTH = 26;
    private static final int SWITCH_HEIGHT = 14;

    private final Component description;
    private final BooleanSupplier getter;
    private final Consumer<Boolean> setter;

    private float knobAmount;
    private boolean initialised;

    public ToggleRow(int x, int y, int width, int height, Component title, Component description,
                     BooleanSupplier getter, Consumer<Boolean> setter) {
        super(x, y, width, height, title);
        this.description = description;
        this.getter = getter;
        this.setter = setter;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                            float partialTick) {
        boolean value = this.getter.getAsBoolean();
        if (!this.initialised) {
            this.knobAmount = value ? 1.0F : 0.0F;
            this.initialised = true;
        }
        this.knobAmount += ((value ? 1.0F : 0.0F) - this.knobAmount)
                * Math.min(1.0F, partialTick * 0.45F);

        boolean hovered = isHoveredOrFocused();
        if (hovered) {
            Theme.roundedRect(graphics, getX(), getY(), getWidth(), getHeight(),
                    Theme.withAlpha(0xFFFFFFFF, 0x0E));
        }

        var font = Minecraft.getInstance().font;
        int textY = this.description == null
                ? getY() + (getHeight() - 8) / 2
                : getY() + 5;

        graphics.text(font, getMessage(), getX() + 8, textY, Theme.TEXT);
        if (this.description != null) {
            graphics.text(font, this.description, getX() + 8, textY + 11, Theme.TEXT_MUTED);
        }

        drawSwitch(graphics, value);
    }

    private void drawSwitch(GuiGraphicsExtractor graphics, boolean value) {
        int switchX = getX() + getWidth() - SWITCH_WIDTH - 8;
        int switchY = getY() + (getHeight() - SWITCH_HEIGHT) / 2;

        int track = Theme.mix(Theme.PANEL_SUNKEN, Theme.withAlpha(Theme.SUCCESS, 0xAA),
                this.knobAmount);
        Theme.roundedRect(graphics, switchX, switchY, SWITCH_WIDTH, SWITCH_HEIGHT, track);
        Theme.roundedOutline(graphics, switchX, switchY, SWITCH_WIDTH, SWITCH_HEIGHT,
                value ? Theme.withAlpha(Theme.SUCCESS, 0xFF) : Theme.BORDER);

        int travel = SWITCH_WIDTH - SWITCH_HEIGHT;
        int knobX = switchX + 1 + (int) (travel * this.knobAmount);
        int knobSize = SWITCH_HEIGHT - 2;
        Theme.roundedRect(graphics, knobX, switchY + 1, knobSize, knobSize,
                value ? 0xFFFFFFFF : Theme.TEXT_MUTED);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        playButtonClickSound(Minecraft.getInstance().getSoundManager());
        this.setter.accept(!this.getter.getAsBoolean());
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
