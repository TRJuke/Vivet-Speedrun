package com.vivet.speedrun.client.screen;

import com.vivet.speedrun.client.Cues;
import com.vivet.speedrun.client.ui.FlatButton;
import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.replay.ReplayMatch;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class ReportScreen extends VivetScreen {
    private static final int WIDTH = 300;
    private static final int PADDING = Theme.PANEL_PAD;

    private static final int LINE_H = 10;

    private static final String[] REASONS = {
            "vivet.report.reason_cheating",
            "vivet.report.reason_chat",
            "vivet.report.reason_other",
            "vivet.report.reason_boosting",
    };

    private static final int[] SHOWN = { 0, 3, 1, 2 };

    private final Screen parent;

    private final String matchId;
    private final String opponent;

    private final String accused;

    private int reason;
    private EditBox note;

    private int left;
    private int top;
    private int panelHeight;

    private static final int HEAD = Theme.PANEL_PAD + 7 + Theme.PANEL_PAD;

    private int warningY;
    private int outcomeY;

    private boolean sending;
    private String outcome = "";

    public ReportScreen(Screen parent, String matchId, String opponent) {
        this(parent, matchId, opponent, "");
    }

    private ReportScreen(Screen parent, String matchId, String opponent, String accused) {
        super(Component.translatable("vivet.report.title",
                opponent == null || opponent.isEmpty() ? "your opponent" : opponent));
        this.parent = parent;
        this.matchId = matchId == null ? "" : matchId;
        this.opponent = opponent == null || opponent.isEmpty() ? "your opponent" : opponent;
        this.accused = accused == null ? "" : accused;
    }

    public static ReportScreen aboutPlayer(Screen parent, String uuid, String name) {
        return new ReportScreen(parent, "", name, uuid == null ? "" : uuid);
    }

    private boolean aboutSomebody() {
        return !this.accused.isEmpty();
    }

    public static boolean canReport(ReplayMatch match) {
        if (match == null || match.isSolo()) return false;
        var identity = com.vivet.speedrun.p2p.MojangAuth.identity();
        String me = identity == null ? "" : identity.uuid;
        return match.played(me) && match.opponentOf(me) != null;
    }

    @Override
    protected void init() {
        super.init();

        int width = WIDTH - PADDING * 2;

        int warningLines = this.font.split(
                Component.translatable("vivet.report.warning"), width).size();

        int outcomeLines = this.outcome.isEmpty() ? 1
                : Math.max(1, this.font.split(Component.translatable(this.outcome), width).size());

        int outcomeRoom = this.outcome.isEmpty() ? 0 : 5 + outcomeLines * LINE_H;
        this.panelHeight = HEAD + REASONS.length * 24 + 32
                + warningLines * LINE_H + 6 + 20 + outcomeRoom + PADDING;

        this.left = (this.width - WIDTH) / 2;
        this.top = Math.max(0, (this.height - this.panelHeight) / 2);

        int x = this.left + PADDING;
        int y = this.top + HEAD;

        for (int i : SHOWN) {
            final int pick = i;
            addRenderableWidget(new FlatButton(x, y, width, 20,
                    Component.translatable(REASONS[i]),
                    () -> {
                        this.reason = pick;
                        Cues.confirm();
                        rebuild();
                    },
                    i == this.reason ? FlatButton.Style.READY : FlatButton.Style.GHOST));
            y += 24;
        }

        this.note = new EditBox(this.font, x, y + 6, width, 18,
                Component.translatable("vivet.report.note"));
        this.note.setBordered(false);
        this.note.setMaxLength(200);
        this.note.setTextColor(Theme.TEXT);
        this.note.setHint(Component.translatable("vivet.report.note")
                .copy().withStyle(ChatFormatting.ITALIC));
        addRenderableWidget(this.note);

        y += 32;

        this.warningY = y;
        y += warningLines * LINE_H + 6;

        int half = (width - 6) / 2;
        addRenderableWidget(new FlatButton(x, y, half, 20,
                Component.translatable("vivet.button.cancel"),
                this::onClose,
                FlatButton.Style.GHOST));

        addRenderableWidget(new FlatButton(x + half + 6, y, half, 20,
                Component.translatable(this.sending
                        ? "vivet.report.sending" : "vivet.report.send"),
                this::send,
                this.sending ? FlatButton.Style.GHOST : FlatButton.Style.PRIMARY));

        this.outcomeY = y + 25;
    }

    private void rebuild() {
        String held = this.note == null ? "" : this.note.getValue();

        clearWidgets();
        init();

        if (this.note != null) this.note.setValue(held);
    }

    private void send() {
        if (this.sending) return;

        this.sending = true;
        this.outcome = "";
        Cues.confirm();

        String text = this.note == null ? "" : this.note.getValue();

        Consumer<String> answered = problem -> {
            if (this.minecraft == null) return;

            this.minecraft.execute(() -> {
                boolean filed = problem == null || problem.isEmpty();

                this.sending = false;
                this.outcome = filed ? "vivet.report.sent" : problem;

                if (filed) {
                    Cues.confirm();
                } else {
                    Cues.deny();
                }
                rebuild();
            });
        };

        if (aboutSomebody()) {
            com.vivet.speedrun.p2p.Reports.reportPlayer(this.accused, this.reason, text, answered);
        } else {
            com.vivet.speedrun.p2p.Reports.report(this.matchId, this.reason, text, answered);
        }

        rebuild();
    }

    @Override
    protected void drawScaled(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                              float partialTick) {
        Theme.fill(graphics, 0, 0, this.width, this.height, Theme.SCRIM);
        Theme.panel(graphics, this.left, this.top, WIDTH, this.panelHeight);

        int x = this.left + PADDING;

        graphics.text(this.font, this.title.copy().withStyle(ChatFormatting.BOLD),
                x, this.top + Theme.PANEL_PAD, Theme.TEXT);

        int width = WIDTH - PADDING * 2;
        int line = this.warningY;

        for (var row : this.font.split(
                Component.translatable("vivet.report.warning"), width)) {
            graphics.text(this.font, row, x, line, Theme.TEXT_FAINT);
            line += LINE_H;
        }

        if (!this.outcome.isEmpty()) {
            int colour = "vivet.report.sent".equals(this.outcome) ? Theme.SUCCESS : Theme.DANGER;
            int at = this.outcomeY;
            for (var row : this.font.split(Component.translatable(this.outcome), WIDTH - PADDING * 2)) {
                graphics.text(this.font, row, x, at, colour);
                at += LINE_H;
            }
        }

        drawWidgets(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }
}
