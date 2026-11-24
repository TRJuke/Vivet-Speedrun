package com.vivet.speedrun.client.screen;

import com.vivet.speedrun.client.ClientState;
import com.vivet.speedrun.client.Cues;
import com.vivet.speedrun.client.ResultsPrompt;
import com.vivet.speedrun.client.ui.FlatButton;
import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.game.Difficulty;
import com.vivet.speedrun.game.Time;
import com.vivet.speedrun.session.Session;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class GraceScreen extends VivetScreen {
    private static final int WIDTH = 340;
    private static final int PADDING = 18;
    private static final int BUTTON_H = 20;

    private static final int NOTE_H = 13;
    private static final int OPTION_GAP = 10;

    private static final int NOTE_TWO_H = 11;

    private static final int FOOTER_H = 15;

    private final Difficulty band;

    private int left;
    private int top;
    private int panelHeight;
    private int optionsY;

    private boolean answered;

    public GraceScreen(Difficulty band) {
        super(Component.translatable("vivet.grace.title"));
        this.band = band == null ? Difficulty.EXTREME : band;
    }

    @Override
    protected void init() {
        super.init();

        int options = (BUTTON_H + NOTE_H) * 2 + NOTE_TWO_H + OPTION_GAP + FOOTER_H;
        this.panelHeight = PADDING + 22 + 16 + 16 + 8 + options + PADDING;

        this.left = (this.width - WIDTH) / 2;
        this.top = Math.max(0, (this.height - this.panelHeight) / 2);
        this.optionsY = this.top + this.panelHeight - PADDING - options;

        int buttonWidth = WIDTH - PADDING * 2;
        int x = this.left + PADDING;

        addRenderableWidget(new FlatButton(x, this.optionsY, buttonWidth, BUTTON_H,
                Component.translatable("vivet.grace.keep_hunting"),
                () -> answer(true),
                FlatButton.Style.PRIMARY));

        addRenderableWidget(new FlatButton(x, secondOptionY(), buttonWidth, BUTTON_H,
                Component.translatable("vivet.grace.leave"),
                () -> answer(false),
                FlatButton.Style.SECONDARY));
    }

    private void answer(boolean keepPlaying) {
        if (this.answered) return;
        this.answered = true;

        Session.answerGrace(keepPlaying);

        if (!keepPlaying) Cues.confirm();

        ResultsPrompt.dismiss();
        this.minecraft.gui.setScreen(null);
    }

    private int secondOptionY() {
        return this.optionsY + BUTTON_H + NOTE_H + NOTE_TWO_H + OPTION_GAP;
    }

    @Override
    protected void drawScaled(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                              float partialTick) {
        Theme.fill(graphics, 0, 0, this.width, this.height, Theme.SCRIM);

        int accent = this.band.color();
        Theme.panel(graphics, this.left, this.top, WIDTH, this.panelHeight);

        int centerX = this.left + WIDTH / 2;
        int cursor = this.top + PADDING;

        graphics.centeredText(this.font, this.title, centerX, cursor, accent);
        cursor += 22;

        graphics.centeredText(this.font,
                Component.translatable("vivet.grace.line"), centerX, cursor, Theme.TEXT);
        cursor += 16;

        graphics.centeredText(this.font,
                Component.translatable("vivet.grace.elapsed",
                        Time.formatPrecise(ClientState.elapsedCentis())),
                centerX, cursor, Theme.TEXT_FAINT);
        cursor += 16;

        Theme.divider(graphics, this.left + PADDING, cursor, WIDTH - PADDING * 2);

        drawWidgets(graphics, mouseX, mouseY, partialTick);

        int clock = Session.carryOnSeconds();
        if (clock > 0) {
            drawNote(graphics, Component.translatable("vivet.grace.keep_hunting.note",
                    Time.format(clock)), this.optionsY + BUTTON_H + 3, Theme.SUCCESS);
            drawNote(graphics, Component.translatable("vivet.grace.keep_hunting.drain"),
                    this.optionsY + BUTTON_H + NOTE_H + 2, Theme.TEXT_MUTED);
        }

        drawNote(graphics, Component.translatable("vivet.grace.leave.note"),
                secondOptionY() + BUTTON_H + 3, Theme.TEXT_MUTED);

        drawNote(graphics, Component.translatable("vivet.grace.anytime"),
                secondOptionY() + BUTTON_H + NOTE_H + 5, Theme.TEXT_FAINT);
    }

    private void drawNote(GuiGraphicsExtractor graphics, Component line, int y, int color) {
        graphics.centeredText(this.font, line, this.left + WIDTH / 2, y, color);
    }

    @Override
    public void onClose() {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
