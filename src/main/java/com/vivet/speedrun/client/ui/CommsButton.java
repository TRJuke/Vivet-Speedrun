package com.vivet.speedrun.client.ui;

import com.vivet.speedrun.client.Cues;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class CommsButton extends AbstractWidget {
    public static final int WIDTH = 44;

    private final VanillaIcon.Kind icon;
    private final Component caption;

    private final net.minecraft.resources.Identifier art;
    private final int artSize;

    private final java.util.function.IntSupplier waiting;

    private final Runnable onPress;

    private float hoverAmount;

    private java.util.function.BooleanSupplier pulse;

    public CommsButton pulsing(java.util.function.BooleanSupplier when) {
        this.pulse = when;
        return this;
    }

    public CommsButton(int x, int y, int height, VanillaIcon.Kind icon, Component caption,
                       java.util.function.IntSupplier waiting, Runnable onPress) {
        super(x, y, WIDTH, height, caption);
        this.icon = icon;
        this.caption = caption;
        this.waiting = waiting;
        this.onPress = onPress;
        this.art = null;
        this.artSize = 0;
    }

    private int iconBox = 16;

    private Component glyph;
    private int glyphColour;

    public CommsButton(int x, int y, int height, Component glyph, int glyphColour, Component caption,
                       java.util.function.IntSupplier waiting, Runnable onPress) {
        this(x, y, height, (VanillaIcon.Kind) null, caption, waiting, onPress);
        this.glyph = glyph;
        this.glyphColour = glyphColour;
    }

    public CommsButton(int x, int y, int height, net.minecraft.resources.Identifier art, int artSize,
                       Component tooltip, Component caption, java.util.function.IntSupplier waiting,
                       Runnable onPress) {
        super(x, y, WIDTH, height, tooltip);
        this.icon = null;
        this.caption = caption;
        this.art = art;
        this.artSize = artSize;
        this.waiting = waiting;
        this.onPress = onPress;
    }

    public CommsButton(int x, int y, int height, VanillaIcon.Kind icon, int iconBox, Component tooltip,
                       java.util.function.IntSupplier waiting, Runnable onPress) {
        super(x, y, WIDTH, height, tooltip);
        this.icon = icon;
        this.iconBox = iconBox;
        this.caption = Component.empty();
        this.art = null;
        this.artSize = 0;
        this.waiting = waiting;
        this.onPress = onPress;
    }

    public CommsButton(int x, int y, int height, net.minecraft.resources.Identifier art, int artSize,
                       Component tooltip, java.util.function.IntSupplier waiting, Runnable onPress) {
        super(x, y, WIDTH, height, tooltip);
        this.icon = null;
        this.caption = Component.empty();
        this.art = art;
        this.artSize = artSize;
        this.waiting = waiting;
        this.onPress = onPress;
    }

    public static void frame(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                             int cells) {
        Theme.roundedBox(graphics, x, y, width, height, 4, Theme.TITLE_PLATE);
        Theme.roundedBoxOutline(graphics, x, y, width, height, 4, Theme.PLATE_EDGE);

        for (int cell = 1; cell < cells; cell++) {
            int at = x + Math.round(width * (cell / (float) cells));
            Theme.fill(graphics, at, y + 5, at + 1, y + height - 5, Theme.withAlpha(0xFFFFFFFF, 0x26));
        }
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                            float partialTick) {
        float target = isHoveredOrFocused() ? 1.0F : 0.0F;
        this.hoverAmount += (target - this.hoverAmount) * Math.min(1.0F, partialTick * 0.5F);

        Font font = Minecraft.getInstance().font;
        int x = getX();
        int y = getY();
        int width = getWidth();
        int height = getHeight();

        if (this.hoverAmount > 0.01F) {
            Theme.fill(graphics, x + 2, y + 3, x + width - 2, y + height - 3,
                    Theme.withAlpha(Theme.ACCENT, (int) (this.hoverAmount * 0x28)));
        }

        boolean worded = !this.caption.getString().isEmpty();
        if (this.art != null) {
            int top = worded ? y + 3 : y + (height - this.artSize) / 2;
            graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, this.art,
                    x + (width - this.artSize) / 2, top, 0.0F, 0.0F,
                    this.artSize, this.artSize, this.artSize, this.artSize, 0xFFFFFFFF);
            if (worded) {
                word(graphics, font, this.caption, x + width / 2, y + height - 16,
                        Theme.mix(Theme.TEXT_FAINT, Theme.TEXT, this.hoverAmount));
            }
        } else if (this.glyph != null) {
            BigText.draw(graphics, font, this.glyph, x + width / 2 - (BigText.width(font, this.glyph, 2) - 2) / 2,
                    y + height / 2 - 6 - BigText.height(2) / 2 + 1, this.glyphColour, 2, 0);
            word(graphics, font, this.caption, x + width / 2, y + height - 16,
                    Theme.mix(Theme.TEXT_FAINT, Theme.TEXT, this.hoverAmount));
        } else if (this.caption.getString().isEmpty()) {
            VanillaIcon.draw(graphics, this.icon, x + width / 2, y + height / 2, this.iconBox);
        } else {
            VanillaIcon.draw(graphics, this.icon, x + width / 2, y + height / 2 - 6, 16);

            word(graphics, font, this.caption, x + width / 2, y + height - 16,
                    Theme.mix(Theme.TEXT_FAINT, Theme.TEXT, this.hoverAmount));
        }

        drawPip(graphics, font, x + width, y);

        if (this.pulse != null && this.pulse.getAsBoolean()) {
            Pulse.around(graphics, x + 1, y + 1, width - 2, height - 2, 4);
        }
    }

    private static void word(GuiGraphicsExtractor graphics, Font font, Component text, int cx, int y, int colour) {
        graphics.text(font, text, cx - (font.width(text) - 1) / 2, y, colour, true);
    }

    private void drawPip(GuiGraphicsExtractor graphics, Font font, int right, int y) {
        int count = this.waiting == null ? 0 : this.waiting.getAsInt();
        if (count <= 0) return;

        String text = count > 9 ? "9+" : String.valueOf(count);
        int width = font.width(text) + 6;
        int x = right - width - 3;

        Theme.roundedBox(graphics, x, y + 3, width, 10, 2, 0xFFD03A2C);
        graphics.centeredText(font, Component.literal(text), x + width / 2, y + 4, 0xFFFFFFFF);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (!this.active) return;

        if (this.onPress == null) {
            Cues.nothingHappened();
            return;
        }

        playButtonClickSound(Minecraft.getInstance().getSoundManager());
        this.onPress.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
