package com.vivet.speedrun.client.screen;

import com.vivet.speedrun.client.ui.FlatButton;
import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.session.Session;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class VoteScreen extends VivetScreen {
    private static final int WIDTH = 260;
    private static final int HEIGHT = 116;

    private final Screen parent;
    private final String question;
    private final String proposer;

    private int left;
    private int top;

    public VoteScreen(Screen parent, Session.Vote vote) {
        super(Component.literal(vote.title()));
        this.parent = parent;
        this.question = vote.title();
        this.proposer = vote.proposer;
    }

    @Override
    protected void init() {
        super.init();
        this.left = (this.width - WIDTH) / 2;
        this.top = (this.height - HEIGHT) / 2;

        int buttonWidth = (WIDTH - 40 - 8) / 2;
        int y = this.top + HEIGHT - 42;

        addRenderableWidget(new FlatButton(this.left + 20, y, buttonWidth, 20,
                Component.translatable("vivet.button.vote_yes"),
                () -> answer(true),
                FlatButton.Style.PRIMARY));

        addRenderableWidget(new FlatButton(this.left + 28 + buttonWidth, y, buttonWidth, 20,
                Component.translatable("vivet.button.vote_no"),
                () -> answer(false),
                FlatButton.Style.DANGER));
    }

    private void answer(boolean agree) {
        Session.castVote(agree);
        onClose();
    }

    @Override
    public void tick() {
        super.tick();
        if (Session.openVote() == null) onClose();
    }

    @Override
    protected void drawScaled(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                              float partialTick) {
        Theme.fill(graphics, 0, 0, this.width, this.height, Theme.SCRIM);
        Theme.panel(graphics, this.left, this.top, WIDTH, HEIGHT);

        int centerX = this.left + WIDTH / 2;
        graphics.centeredText(this.font, Component.translatable("vivet.vote.heading"),
                centerX, this.top + 18, Theme.TEXT_MUTED);
        graphics.centeredText(this.font, Component.literal(this.question),
                centerX, this.top + 38, Theme.TEXT);
        graphics.centeredText(this.font,
                Component.translatable("vivet.vote.proposed_by", this.proposer),
                centerX, this.top + 54, Theme.TEXT_FAINT);

        Theme.divider(graphics, this.left + 20, this.top + HEIGHT - 52, WIDTH - 40);
        drawWidgets(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
