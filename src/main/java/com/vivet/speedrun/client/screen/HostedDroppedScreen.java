package com.vivet.speedrun.client.screen;

import com.vivet.speedrun.client.Cues;
import com.vivet.speedrun.client.HostedConnect;
import com.vivet.speedrun.client.HostedRejoin;
import com.vivet.speedrun.client.ui.Bar;
import com.vivet.speedrun.client.ui.FlatButton;
import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.game.Time;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class HostedDroppedScreen extends VivetScreen {
    private static final int WIDTH = 320;
    private static final int PADDING = 18;
    private static final int BUTTON_H = 20;

    private static final long FIRST_TRY_MS = 2_000L;
    private static final long RETRY_MS = 5_000L;

    private static int attempts;
    private static long nextAttemptAt;

    private String status = "Reconnecting...";
    private boolean asking;
    private boolean finished;

    private boolean askedLast;

    private int left;
    private int top;
    private int panelHeight;

    public HostedDroppedScreen() {
        super(Component.translatable("vivet.rejoin.title"));
        long now = System.currentTimeMillis();
        if (nextAttemptAt == 0L || nextAttemptAt < now) {
            nextAttemptAt = now + (attempts == 0 ? FIRST_TRY_MS : RETRY_MS);
        }
        if (attempts > 0) this.status = "Couldn't reconnect. Retrying...";
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
                Component.translatable("vivet.rejoin.now"), this::attempt, FlatButton.Style.PRIMARY));
        addRenderableWidget(new FlatButton(bar.right(120), y, 120, BUTTON_H,
                Component.translatable("vivet.rejoin.give_up"), this::giveUp, FlatButton.Style.GHOST));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.finished) return;

        if (!HostedRejoin.pending()) {
            if (HostedRejoin.missed() != null && !this.askedLast) {
                this.askedLast = true;
                attempt();
                return;
            }
            if (!this.asking) done();
            return;
        }
        if (!this.asking && System.currentTimeMillis() >= nextAttemptAt) attempt();
    }

    private void attempt() {
        if (this.finished || this.asking) return;

        this.asking = true;
        attempts++;
        nextAttemptAt = System.currentTimeMillis() + RETRY_MS;
        this.status = "Reconnecting... (" + attempts + ")";

        Minecraft minecraft = Minecraft.getInstance();
        HostedRejoin.rejoin(minecraft, this, reason -> {
            this.asking = false;
            this.status = reason;
            Cues.deny();
            done();
        });
    }

    private void giveUp() {
        if (this.finished) return;
        HostedRejoin.giveUp();
        done();
    }

    private void done() {
        com.vivet.speedrun.client.WorldActions.trace("rejoin window closed");
        this.finished = true;
        reset();
        Minecraft.getInstance().gui.setScreen(HostedConnect.destination());
    }

    public static void reset() {
        attempts = 0;
        nextAttemptAt = 0L;
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

        int left = HostedRejoin.secondsLeft();
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
