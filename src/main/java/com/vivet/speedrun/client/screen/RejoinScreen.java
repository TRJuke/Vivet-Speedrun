package com.vivet.speedrun.client.screen;

import com.vivet.speedrun.client.Cues;
import com.vivet.speedrun.client.WorldActions;
import com.vivet.speedrun.client.ui.Bar;
import com.vivet.speedrun.client.ui.FlatButton;
import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.game.Time;
import com.vivet.speedrun.session.Session;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class RejoinScreen extends VivetScreen {
    private static final int WIDTH = 320;
    private static final int PADDING = 18;
    private static final int BUTTON_H = 20;

    private static final long RETRY_MS = 3_000L;

    private final long droppedAt;

    private long nextAttemptAt;
    private int attempts;

    private String status = "Reconnecting...";
    private boolean connecting;

    private boolean finished;

    private int left;
    private int top;
    private int panelHeight;

    public RejoinScreen() {
        this(System.currentTimeMillis());
    }

    public RejoinScreen(long droppedAt) {
        super(Component.translatable("vivet.rejoin.title"));
        this.droppedAt = droppedAt;
    }

    @Override
    protected void init() {
        super.init();

        this.panelHeight = PADDING + 22 + 16 + 18 + 16 + BUTTON_H + PADDING;
        this.left = (this.width - WIDTH) / 2;
        this.top = (this.height - this.panelHeight) / 2;

        Bar bar = new Bar(this.left + PADDING, WIDTH - PADDING * 2, 8);
        int y = this.top + this.panelHeight - PADDING - BUTTON_H;

        addRenderableWidget(new FlatButton(bar.left(150), y, 150, BUTTON_H,
                Component.translatable("vivet.rejoin.now"),
                this::attempt,
                FlatButton.Style.PRIMARY));

        addRenderableWidget(new FlatButton(bar.right(120), y, 120, BUTTON_H,
                Component.translatable("vivet.rejoin.give_up"),
                this::giveUp,
                FlatButton.Style.GHOST));
    }

    private boolean windowStillOpen() {
        return Session.rejoinable() != null && secondsLeft() > 0;
    }

    private int secondsLeft() {
        long spent = System.currentTimeMillis() - this.droppedAt;
        return (int) Math.max(0L, (Session.rejoinWindowMs() - spent) / 1000L);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.finished) return;

        if (!windowStillOpen()) {
            this.finished = true;
            Session.forgetRejoin();
            WorldActions.quitRace(Minecraft.getInstance());
            return;
        }

        if (!this.connecting && System.currentTimeMillis() >= this.nextAttemptAt) attempt();
    }

    private void attempt() {
        if (this.finished || this.connecting) return;

        this.connecting = true;
        this.attempts++;
        this.status = "Reconnecting... (" + this.attempts + ")";

        com.vivet.speedrun.client.LoadingTakeover.suppressNext();

        Session.rejoin(
                () -> {
                    this.finished = true;
                    Cues.opponentReturned();
                    Minecraft.getInstance().gui.setScreen(null);
                },
                reason -> {
                    this.connecting = false;
                    this.status = reason == null ? "Couldn't reconnect." : reason;
                    this.nextAttemptAt = System.currentTimeMillis() + RETRY_MS;
                });
    }

    private void giveUp() {
        if (this.finished) return;

        this.finished = true;
        Session.forgetRejoin();
        WorldActions.quitRace(Minecraft.getInstance());
    }

    @Override
    protected void drawScaled(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                              float partialTick) {
        Theme.fill(graphics, 0, 0, this.width, this.height, Theme.SCRIM);
        Theme.panel(graphics, this.left, this.top, WIDTH, this.panelHeight);

        int centre = this.left + WIDTH / 2;
        int cursor = this.top + PADDING;

        graphics.centeredText(this.font, this.title, centre, cursor, Theme.DANGER);
        cursor += 22;

        int left = secondsLeft();
        graphics.centeredText(this.font,
                Component.translatable("vivet.rejoin.countdown", Time.format(left)),
                centre, cursor, left <= 15 ? Theme.DANGER : Theme.WARNING);
        cursor += 16;

        graphics.centeredText(this.font, Component.translatable("vivet.rejoin.note"),
                centre, cursor, Theme.TEXT_MUTED);
        cursor += 18;

        graphics.centeredText(this.font, Component.literal(this.status),
                centre, cursor, Theme.TEXT_FAINT);

        drawWidgets(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
