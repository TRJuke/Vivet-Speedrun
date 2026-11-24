package com.vivet.speedrun.client.screen;

import com.vivet.speedrun.client.ui.FlatButton;
import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.session.Racer;
import com.vivet.speedrun.session.Session;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;

public class ReadyScreen extends VivetScreen {
    private static final int WIDTH = 280;
    private static final int PADDING = 18;
    private static final int ROW_H = 12;
    private static final int BUTTON_H = 20;

    private int left;
    private int top;
    private int panelHeight;
    private int rosterY;
    private int buttonsY;

    private boolean wasReady;

    public ReadyScreen() {
        super(Component.translatable("vivet.screen.ready"));
    }

    @Override
    protected void init() {
        super.init();

        List<Racer> racers = Session.racers();

        int cursor = PADDING;
        cursor += 24;
        cursor += 16;
        this.rosterY = cursor;
        cursor += Math.max(1, racers.size()) * ROW_H;
        cursor += 16;
        this.buttonsY = cursor;
        cursor += BUTTON_H + PADDING;

        this.panelHeight = cursor;
        this.left = (this.width - WIDTH) / 2;
        this.top = Math.max(0, (this.height - this.panelHeight) / 2);

        this.rosterY += this.top;
        this.buttonsY += this.top;

        buildButtons();
    }

    private void buildButtons() {
        boolean host = Session.role() == Session.Role.HOST;
        this.wasReady = Session.isLocallyReady();

        int full = WIDTH - PADDING * 2;
        int width = host ? (full - 6) / 2 : full;
        int x = this.left + PADDING;

        addRenderableWidget(new FlatButton(x, this.buttonsY, width, BUTTON_H,
                Component.translatable(this.wasReady
                        ? "vivet.button.unready" : "vivet.button.ready_up"),
                this::toggleReady,
                this.wasReady ? FlatButton.Style.GHOST : FlatButton.Style.PRIMARY));

        if (host) {
            addRenderableWidget(new FlatButton(x + width + 6, this.buttonsY, width, BUTTON_H,
                    Component.translatable("vivet.button.start_now"),
                    Session::beginMatch));
        }
    }

    private void toggleReady() {
        if (Session.isLocallyReady()) {
            Session.unready();
        } else {
            Session.readyUp();
        }
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    public void tick() {
        super.tick();
        if (Session.phase() != Session.Phase.READY_CHECK) {
            onClose();
            return;
        }

        if (Session.isLocallyReady() != this.wasReady) rebuild();
    }

    @Override
    protected void drawScaled(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                              float partialTick) {
        Theme.fill(graphics, 0, 0, this.width, this.height, Theme.SCRIM);
        Theme.panel(graphics, this.left, this.top, WIDTH, this.panelHeight);

        int centerX = this.left + WIDTH / 2;
        graphics.centeredText(this.font, this.title, centerX, this.top + PADDING, Theme.TEXT);
        graphics.centeredText(this.font, Component.translatable("vivet.ready.heading"),
                centerX, this.top + PADDING + 18, Theme.TEXT_MUTED);

        int y = this.rosterY;
        for (Racer racer : Session.racers()) {
            String name = racer.name.equals(Session.localName()) ? racer.name + " (you)" : racer.name;
            graphics.text(this.font, Component.literal(name), this.left + PADDING, y,
                    racer.lobbyReady ? Theme.SUCCESS : Theme.TEXT_MUTED);

            String state = racer.lobbyReady ? "ready" : "waiting";
            graphics.text(this.font, Component.literal(state),
                    this.left + WIDTH - PADDING - this.font.width(state), y,
                    racer.lobbyReady ? Theme.SUCCESS : Theme.TEXT_FAINT);
            y += ROW_H;
        }

        graphics.centeredText(this.font,
                Component.translatable(Session.isLocallyReady()
                        ? "vivet.ready.you_are_ready" : "vivet.ready.waiting"),
                centerX, this.buttonsY - 14, Theme.TEXT_FAINT);

        drawWidgets(graphics, mouseX, mouseY, partialTick);
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
