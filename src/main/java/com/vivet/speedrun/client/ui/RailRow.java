package com.vivet.speedrun.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

public class RailRow extends AbstractWidget {
    private static final int DIM = 0xFF666666;

    private static final int RADIUS = 5;

    private static final int ICON_BOX = 18;
    private static final int ICON_GAP = 8;

    private final BooleanSupplier selected;
    private final int accent;
    private final Runnable onPress;

    private final Component note;

    private final VanillaIcon.Kind icon;

    private float hoverAmount;

    public RailRow(int x, int y, int width, int height, Component title, Component note,
                   VanillaIcon.Kind icon, int accent, BooleanSupplier selected, Runnable onPress) {
        super(x, y, width, height, title);
        this.note = note;
        this.icon = icon;
        this.accent = accent;
        this.selected = selected;
        this.onPress = onPress;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                            float partialTick) {
        boolean on = this.selected.getAsBoolean();
        float target = isHoveredOrFocused() || on ? 1.0F : 0.0F;
        this.hoverAmount += (target - this.hoverAmount) * Math.min(1.0F, partialTick * 0.5F);

        var font = Minecraft.getInstance().font;
        int x = getX();
        int y = getY();
        int width = getWidth();
        int height = getHeight();

        int fill = on
                ? Theme.mix(Theme.PANEL_RAISED, this.accent, 0.16F)
                : Theme.mix(Theme.PANEL_SUNKEN, 0xFF2A2E3A, this.hoverAmount * 0.7F);

        Theme.roundedBox(graphics, x, y, width, height, RADIUS, fill);
        Theme.roundedBoxOutline(graphics, x, y, width, height, RADIUS,
                on ? Theme.withAlpha(this.accent, 0xCC) : Theme.BORDER);

        int text = x + 10;

        if (this.icon != null) {
            int tint = on ? 0xFFFFFFFF : Theme.mix(DIM, 0xFFFFFFFF, this.hoverAmount * 0.5F);

            VanillaIcon.draw(graphics, this.icon, x + 10 + ICON_BOX / 2, y + height / 2,
                    ICON_BOX, tint);
            text = x + 10 + ICON_BOX + ICON_GAP;
        }

        graphics.text(font, getMessage(), text, y + (height - 8) / 2,
                on ? Theme.TEXT : Theme.mix(Theme.TEXT_FAINT, Theme.TEXT_MUTED, this.hoverAmount));

        if (this.note != null
                && (text - x) + font.width(getMessage()) + font.width(this.note) + 16
                        <= width) {
            graphics.text(font, this.note, x + width - 8 - font.width(this.note),
                    y + (height - 8) / 2, on ? this.accent : Theme.TEXT_FAINT);
        }
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
