package com.vivet.speedrun.client.screen;

import com.vivet.speedrun.client.Network;
import com.vivet.speedrun.client.ResultsPrompt;
import com.vivet.speedrun.client.ui.FlatButton;
import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.game.Time;
import com.vivet.speedrun.session.Racer;
import com.vivet.speedrun.session.Session;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;

public class WaitingScreen extends VivetScreen {
    private static final int WIDTH = 300;
    private static final int PADDING = 18;
    private static final int ROW_H = 12;
    private static final int BUTTON_H = 20;

    private final int yourSeconds;
    private final boolean youFinished;

    private int left;
    private int top;
    private int panelHeight;
    private int rosterY;
    private int buttonsY;

    private java.util.List<net.minecraft.util.FormattedCharSequence> headingLines = List.of();

    public WaitingScreen(int yourSeconds, boolean youFinished) {
        super(Component.translatable("vivet.screen.waiting"));
        this.yourSeconds = yourSeconds;
        this.youFinished = youFinished;
    }

    @Override
    protected void init() {
        super.init();

        List<Racer> racers = Session.racers();

        this.headingLines = this.font.split(headingText(), WIDTH - PADDING * 2);

        int cursor = PADDING;
        cursor += 24;
        cursor += 16;
        cursor += 4 + this.headingLines.size() * 10;
        this.rosterY = cursor;
        cursor += Math.max(1, racers.size()) * ROW_H;
        cursor += 14;
        this.buttonsY = cursor;
        cursor += BUTTON_H + PADDING;

        this.panelHeight = cursor;
        this.left = (this.width - WIDTH) / 2;
        this.top = Math.max(0, (this.height - this.panelHeight) / 2);

        this.rosterY += this.top;
        this.buttonsY += this.top;

        if (!Session.isRanked() || this.youFinished) {
            addRenderableWidget(new FlatButton(this.left + PADDING, this.buttonsY,
                    WIDTH - PADDING * 2, BUTTON_H,
                    Component.translatable("vivet.button.spectate"),
                    this::spectate,
                    FlatButton.Style.SECONDARY));
        }
    }

    private void spectate() {
        Network.spectate();
        ResultsPrompt.park();
    }

    @Override
    protected void drawScaled(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                              float partialTick) {
        Theme.fill(graphics, 0, 0, this.width, this.height, Theme.SCRIM);
        Theme.panel(graphics, this.left, this.top, WIDTH, this.panelHeight);

        int accent = this.youFinished ? Theme.SUCCESS : Theme.WARNING;

        int centerX = this.left + WIDTH / 2;
        graphics.centeredText(this.font, this.title, centerX, this.top + PADDING, accent);

        graphics.centeredText(this.font,
                Component.translatable(this.youFinished
                                ? "vivet.waiting.your_time" : "vivet.waiting.your_run",
                        Time.format(this.yourSeconds)),
                centerX, this.top + PADDING + 20, Theme.TEXT);

        int cursor = this.top + PADDING + 40;
        for (var line : this.headingLines) {
            graphics.centeredText(this.font, line, centerX, cursor, Theme.TEXT_FAINT);
            cursor += 10;
        }

        drawRoster(graphics);
        drawWidgets(graphics, mouseX, mouseY, partialTick);
    }

    private void drawRoster(GuiGraphicsExtractor graphics) {
        int y = this.rosterY;

        for (Racer racer : Session.racers()) {
            boolean done = racer.isDone();
            boolean finished = racer.status == Racer.Status.FINISHED;
            int color = done ? Theme.TEXT : Theme.TEXT_MUTED;

            String name = racer.name.equals(Session.localName()) ? racer.name + " (you)" : racer.name;
            graphics.text(this.font, Component.literal(name), this.left + PADDING, y, color);

            String state = finished ? Time.format(racer.seconds)
                    : done ? racer.statusText() : "Still playing";

            graphics.text(this.font, Component.literal(state),
                    this.left + WIDTH - PADDING - this.font.width(state), y,
                    finished ? Theme.SUCCESS : done ? Theme.TEXT_FAINT : Theme.TEXT_MUTED);
            y += ROW_H;
        }
    }

    private Component headingText() {
        return Component.translatable(Session.playsToLastFinisher()
                ? "vivet.waiting.heading_last" : "vivet.waiting.heading");
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
