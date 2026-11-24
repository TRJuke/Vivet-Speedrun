package com.vivet.speedrun.client.screen;

import com.vivet.speedrun.client.Cues;
import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.client.ui.Tip;
import com.vivet.speedrun.client.ui.UiButtons;
import com.vivet.speedrun.game.Time;
import com.vivet.speedrun.p2p.DiscordLink;
import com.vivet.speedrun.p2p.MojangAuth;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

public class DiscordLinkScreen extends VivetScreen {
    private static final int WIDTH = 272;
    private static final int PAD = 14;
    private static final int PANEL_R = 8;
    private static final int BACK_GAP = 8;

    private static final int BUTTON_W = 170;
    private static final int BUTTON_H = 20;
    private static final int CODE_W = 150;
    private static final int CODE_H = 23;

    private static final int MIDDLE_H = 50;

    private final Screen parent;

    private int left;
    private int top;
    private int panelH;

    private boolean revealed;
    private boolean wasLive;
    private boolean pending;
    private long seenStamp;

    private int buttonX;
    private int buttonY;
    private int codeX;
    private int codeY;
    private int eyeX;
    private int eyeY;
    private int joinX;
    private int joinY;
    private int joinW;

    public DiscordLinkScreen(Screen parent) {
        super(Component.translatable("vivet.discord.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.panelH = PAD + 12 + 16 + MIDDLE_H + 16 + this.font.lineHeight + PAD;
        this.left = (this.width - WIDTH) / 2;
        this.top = Math.max(4, (this.height - this.panelH) / 2);
        this.wasLive = DiscordLink.code() != null;
        this.seenStamp = DiscordLink.stamp();
    }

    @Override
    public void tick() {
        super.tick();

        if (DiscordLink.isLinked()) {
            this.minecraft.gui.setScreen(this.parent);
            return;
        }

        boolean live = DiscordLink.code() != null;
        if (this.wasLive && !live) {
            this.revealed = false;
            Cues.linkCodeExpired();
        }
        this.wasLive = live;

        if (this.pending && DiscordLink.stamp() != this.seenStamp && !DiscordLink.asking()) {
            this.pending = false;
            if (live) Cues.linkCode();
            else Cues.deny();
        }
        this.seenStamp = DiscordLink.stamp();
    }

    @Override
    protected void drawScaled(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Theme.fill(graphics, 0, 0, this.width, this.height, Theme.SCRIM);
        Theme.roundedBox(graphics, this.left, this.top, WIDTH, this.panelH, PANEL_R, Theme.PANEL);
        Theme.roundedBoxOutline(graphics, this.left, this.top, WIDTH, this.panelH, PANEL_R, Theme.BORDER);

        UiButtons.button(graphics, UiButtons.Kind.BACK, backX(), this.top, onBack(mouseX, mouseY), true, false);

        int cx = this.left + WIDTH / 2;
        Component title = this.title.copy().withStyle(ChatFormatting.BOLD);
        graphics.text(this.font, title, cx - this.font.width(title) / 2, this.top + PAD, Theme.TEXT);
        Theme.divider(graphics, this.left + PAD, this.top + PAD + 16, WIDTH - PAD * 2);

        int middle = this.top + PAD + 12 + 16;
        if (DiscordLink.code() != null) drawCode(graphics, cx, middle, mouseX, mouseY);
        else drawGenerate(graphics, cx, middle, mouseX, mouseY);

        drawJoin(graphics, cx, middle + MIDDLE_H + 16, mouseX, mouseY);

        drawWidgets(graphics, mouseX, mouseY, partialTick);

        if (DiscordLink.code() != null && this.revealed && onCode(mouseX, mouseY)) {
            List<Tip.Line> lines = Tip.lines();
            lines.add(new Tip.Line(Component.translatable("vivet.discord.copy"), Theme.TEXT));
            Tip.draw(graphics, this.font, lines, mouseX, mouseY, this.width, this.height);
        }
    }

    private void drawGenerate(GuiGraphicsExtractor graphics, int cx, int y, int mouseX, int mouseY) {
        Component above = DiscordLink.expired() ? Component.translatable("vivet.discord.expired")
                : DiscordLink.failed() ? Component.translatable("vivet.discord.failed") : null;
        if (above != null) {
            graphics.text(this.font, above, cx - this.font.width(above) / 2, y + 4, Theme.DANGER);
        }

        this.buttonX = cx - BUTTON_W / 2;
        this.buttonY = y + 20;
        boolean over = onButton(mouseX, mouseY);
        boolean busy = DiscordLink.asking();
        boolean signedIn = MojangAuth.token() != null;

        int fill = !signedIn ? Theme.PANEL_SUNKEN
                : Theme.withAlpha(0xFF5865F2, busy ? 0x66 : over ? 0xFF : 0xCC);
        Theme.roundedBox(graphics, this.buttonX, this.buttonY, BUTTON_W, BUTTON_H, 4, fill);
        Theme.roundedBoxOutline(graphics, this.buttonX, this.buttonY, BUTTON_W, BUTTON_H, 4,
                signedIn ? Theme.withAlpha(0xFF5865F2, 0xFF) : Theme.BORDER);
        Component word = Component.translatable(signedIn ? "vivet.discord.generate" : "vivet.locker.signed_out");
        graphics.centeredText(this.font, word, cx, this.buttonY + 6, signedIn ? 0xFFFFFFFF : Theme.TEXT_MUTED);
    }

    private void drawCode(GuiGraphicsExtractor graphics, int cx, int y, int mouseX, int mouseY) {
        int total = CODE_W + 6 + UiButtons.SIDE;
        this.codeX = cx - total / 2;
        this.codeY = y + 2;
        this.eyeX = this.codeX + CODE_W + 6;
        this.eyeY = this.codeY;

        boolean over = this.revealed && onCode(mouseX, mouseY);
        Theme.roundedBox(graphics, this.codeX, this.codeY, CODE_W, CODE_H, 4,
                over ? Theme.PANEL_RAISED : Theme.PANEL_SUNKEN);
        Theme.roundedBoxOutline(graphics, this.codeX, this.codeY, CODE_W, CODE_H, 4,
                over ? Theme.BORDER_STRONG : Theme.BORDER);

        String code = DiscordLink.code();
        String shown = this.revealed && code != null ? code.substring(0, 4) + "-" + code.substring(4) : "••••-••••";
        Component text = Component.literal(shown).withStyle(ChatFormatting.BOLD);
        graphics.text(this.font, text, this.codeX + (CODE_W - this.font.width(text)) / 2,
                this.codeY + (CODE_H - 8) / 2, this.revealed ? Theme.TEXT : Theme.TEXT_MUTED);

        UiButtons.button(graphics, UiButtons.Kind.WATCH, this.eyeX, this.eyeY, onEye(mouseX, mouseY), true,
                this.revealed);

        Component warning = Component.translatable("vivet.discord.warning", Time.format(DiscordLink.secondsLeft()));
        graphics.text(this.font, warning, cx - this.font.width(warning) / 2, this.codeY + CODE_H + 10,
                Theme.TEXT_MUTED);
    }

    private void drawJoin(GuiGraphicsExtractor graphics, int cx, int y, int mouseX, int mouseY) {
        Component join = Component.translatable("vivet.discord.join");
        this.joinW = this.font.width(join);
        this.joinX = cx - this.joinW / 2;
        this.joinY = y;
        boolean over = onJoin(mouseX, mouseY);
        Component drawn = over ? join.copy().withStyle(ChatFormatting.UNDERLINE) : join;
        graphics.text(this.font, drawn, this.joinX, this.joinY, over ? 0xFF8C96FF : 0xFF7289DA);
    }

    private int scaled(double value) {
        return (int) (value / uiScale());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick);
        int x = scaled(event.x());
        int y = scaled(event.y());

        if (onBack(x, y)) {
            Cues.confirm();
            onClose();
            return true;
        }
        if (onJoin(x, y)) {
            Cues.confirm();
            com.mojang.blaze3d.Blaze3D.openUri(java.net.URI.create(DiscordLink.INVITE));
            return true;
        }
        if (DiscordLink.code() != null) {
            if (onEye(x, y)) {
                this.revealed = !this.revealed;
                Cues.linkCodeShown(this.revealed);
                return true;
            }
            if (this.revealed && onCode(x, y)) {
                this.minecraft.keyboardHandler.setClipboard(DiscordLink.code());
                Cues.confirm();
                return true;
            }
        } else if (onButton(x, y)) {
            if (MojangAuth.token() == null || DiscordLink.asking()) {
                Cues.nothingHappened();
                return true;
            }
            Cues.confirm();
            this.revealed = false;
            this.pending = true;
            this.seenStamp = DiscordLink.stamp();
            DiscordLink.generate();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean onButton(int x, int y) {
        return x >= this.buttonX && x < this.buttonX + BUTTON_W && y >= this.buttonY && y < this.buttonY + BUTTON_H;
    }

    private boolean onCode(int x, int y) {
        return x >= this.codeX && x < this.codeX + CODE_W && y >= this.codeY && y < this.codeY + CODE_H;
    }

    private boolean onEye(int x, int y) {
        return x >= this.eyeX && x < this.eyeX + UiButtons.SIDE && y >= this.eyeY && y < this.eyeY + UiButtons.SIDE;
    }

    private boolean onJoin(int x, int y) {
        return x >= this.joinX && x < this.joinX + this.joinW && y >= this.joinY - 2 && y < this.joinY + 10;
    }

    private int backX() {
        return this.left + WIDTH + BACK_GAP;
    }

    private boolean onBack(int x, int y) {
        return x >= backX() && x < backX() + UiButtons.SIDE && y >= this.top && y < this.top + UiButtons.SIDE;
    }

    @Override
    public void onClose() {
        DiscordLink.forgetExpired();
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
