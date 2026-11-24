package com.vivet.speedrun.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

public class PickerRow extends AbstractWidget {
    private final Supplier<Component> note;
    private final Supplier<Integer> noteColor;
    private final java.util.function.BooleanSupplier live;
    private final int accent;
    private final boolean selected;
    private final Runnable onPick;

    private float hoverAmount;

    public PickerRow(int x, int y, int width, int height, Component title, Supplier<Component> note,
                     Supplier<Integer> noteColor, java.util.function.BooleanSupplier live,
                     int accent, boolean selected, Runnable onPick) {
        super(x, y, width, height, title);
        this.note = note;
        this.noteColor = noteColor;
        this.live = live;
        this.accent = accent;
        this.selected = selected;
        this.onPick = onPick;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                            float partialTick) {
        float target = isHoveredOrFocused() ? 1.0F : 0.0F;
        this.hoverAmount += (target - this.hoverAmount) * Math.min(1.0F, partialTick * 0.6F);

        var font = Minecraft.getInstance().font;

        int base = Theme.withAlpha(this.selected ? Theme.PANEL_RAISED : Theme.PANEL, 0xFF);

        int tinted = Theme.withAlpha(
                Theme.mix(base, this.accent, this.hoverAmount * 0.30F), 0xFF);
        Theme.fill(graphics, getX(), getY(), getX() + getWidth(), getY() + getHeight(), tinted);

        int textColor = this.selected ? Theme.TEXT : Theme.TEXT_MUTED;
        graphics.text(font, getMessage(), getX() + 12, getY() + (getHeight() - 8) / 2, textColor);

        Component detail = this.note.get();
        if (detail != null) {
            int noteX = getX() + getWidth() - 10 - font.width(detail);
            graphics.text(font, detail, noteX, getY() + (getHeight() - 8) / 2, this.noteColor.get());

            Theme.liveDot(graphics, noteX - 9, getY() + getHeight() / 2 - 2,
                    this.noteColor.get(), this.live.getAsBoolean());
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        playButtonClickSound(Minecraft.getInstance().getSoundManager());
        this.onPick.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
