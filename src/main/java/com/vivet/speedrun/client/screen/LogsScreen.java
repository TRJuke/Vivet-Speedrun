package com.vivet.speedrun.client.screen;

import com.vivet.speedrun.client.Cues;
import com.vivet.speedrun.client.ui.Clip;
import com.vivet.speedrun.client.ui.PlayerHead;
import com.vivet.speedrun.client.ui.RailRow;
import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.client.ui.UiButtons;
import com.vivet.speedrun.client.ui.VanillaIcon;
import com.vivet.speedrun.game.ItemTiers;
import com.vivet.speedrun.game.LogImport;
import com.vivet.speedrun.game.MatchLog;
import com.vivet.speedrun.game.MatchLogs;
import com.vivet.speedrun.game.Practice;
import com.vivet.speedrun.game.RunMode;
import com.vivet.speedrun.game.Target;
import com.vivet.speedrun.game.Time;
import com.vivet.speedrun.p2p.MojangAuth;
import com.vivet.speedrun.p2p.Social;
import com.vivet.speedrun.p2p.ReplayApi;
import com.vivet.speedrun.replay.ReplayMatch;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class LogsScreen extends VivetScreen {
    private static final int MAX_WIDTH = 380;
    private static final int MIN_WIDTH = 240;

    private static final int MARGIN_X = 6;
    private static final int MARGIN_Y = 8;

    private static final int RAIL_GAP = 8;
    private static final int PADDING = 10;

    private static final int RAIL_W = 116;
    private static final int RAIL_MIN = 64;
    private static final int RAIL_H = 26;
    private static final int RAIL_STEP = 4;

    private static final int PANEL_R = 8;

    private static final int HEAD_H = 26;

    private static final int CARD_H = 34;
    private static final int CARD_GAP = 4;
    private static final int CARD_R = 5;

    private static final int CARD_PAD = 7;

    private static final int WANTED_CARDS = 7;

    private static final int BAR_ROOM = 10;
    private static final int BAR_W = 4;
    private static final int BAR_GRAB = 7;

    private static final int RACER_H = 20;

    private static final int FACT_H = 11;

    private static final int MARKS_H = 15;
    private static final int ACTIONS_H = 24;

    private static final int HEAD_SIZE = 12;

    private static final SimpleDateFormat WHEN = new SimpleDateFormat("d MMM, HH:mm");

    private static final long ARMED_MS = 4000L;

    private enum Page {
        ALL("vivet.logs.page.all", VanillaIcon.Kind.MAP, Theme.ACCENT, null),
        RANKED("vivet.logs.page.ranked", VanillaIcon.Kind.STAR, Theme.WARNING,
                RunMode.RANKED_MATCH),
        RANKED_SOLO("vivet.logs.page.ranked_solo", VanillaIcon.Kind.CLOCK, 0xFFE8C34A,
                RunMode.RANKED_SOLO),
        PUBLIC("vivet.logs.page.public", VanillaIcon.Kind.COMPASS, Theme.SUCCESS, RunMode.PUBLIC),

        IMPORT("vivet.logs.page.import", VanillaIcon.Kind.ENDER_PEARL, 0xFF4FC3A1, null);

        private final String key;
        private final VanillaIcon.Kind icon;
        private final int accent;

        private final RunMode mode;

        Page(String key, VanillaIcon.Kind icon, int accent, RunMode mode) {
            this.key = key;
            this.icon = icon;
            this.accent = accent;
            this.mode = mode;
        }

        List<MatchLog> logs() {
            if (this == IMPORT) return List.of();
            return this.mode == null ? MatchLogs.all() : MatchLogs.inMode(this.mode);
        }
    }

    private record Card(MatchLog log, int y, int height, boolean open) {
        boolean holds(int mouseY) {
            return mouseY >= this.y && mouseY < this.y + this.height;
        }
    }

    private record Line(Kind kind, MatchLog.Entry racer, Component label, Component value,
                        int colour, Component note) {
        private enum Kind { GAP, RULE, RACER, FACT, NOTE, MARKS, ACTIONS }

        int height() {
            return switch (this.kind) {
                case GAP -> 4;
                case RULE -> 5;
                case RACER -> RACER_H;
                case FACT, NOTE -> FACT_H;
                case MARKS -> MARKS_H;
                case ACTIONS -> ACTIONS_H;
            };
        }

        static Line gap() {
            return new Line(Kind.GAP, null, null, null, 0, null);
        }

        static Line rule() {
            return new Line(Kind.RULE, null, null, null, 0, null);
        }

        static Line racer(MatchLog.Entry entry, Component value, int colour, Component note) {
            return new Line(Kind.RACER, entry, null, value, colour, note);
        }

        static Line fact(Component label, Component value, int colour) {
            return new Line(Kind.FACT, null, label, value, colour, null);
        }

        static Line note(Component value) {
            return new Line(Kind.NOTE, null, null, value, Theme.TEXT_MUTED, null);
        }

        static Line marks() {
            return new Line(Kind.MARKS, null, null, null, 0, null);
        }

        static Line actions() {
            return new Line(Kind.ACTIONS, null, null, null, 0, null);
        }
    }

    private final Screen parent;

    private Page page = Page.ALL;

    private String open;

    private List<Line> lines = List.of();

    private MatchLog openLog;

    private boolean askedReplays;
    private boolean fetchingReplay;

    private List<MatchLog> shown = List.of();
    private final List<Card> cards = new ArrayList<>();

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int railW;

    private int listLeft;
    private int listTop;
    private int listWidth;
    private int listHeight;
    private int barX;

    private int scroll;
    private int contentHeight;
    private boolean draggingBar;
    private boolean overBack;

    private List<LogImport.Source> sources;

    private final java.util.Set<String> brought = new java.util.HashSet<>();

    private long armedAt;

    public LogsScreen(Screen parent) {
        super(Component.translatable("vivet.screen.logs"));
        this.parent = parent;

        Social.refresh();
    }

    @Override
    protected void init() {
        super.init();

        if (!this.askedReplays) {
            this.askedReplays = true;
            ReplayApi.loadMine();
        }

        int gutter = UiButtons.SIDE + RAIL_GAP;

        this.railW = Math.max(RAIL_MIN, Math.min(RAIL_W,
                this.width - MARGIN_X * 2 - gutter - RAIL_GAP - MIN_WIDTH));

        this.panelW = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH,
                this.width - MARGIN_X * 2 - gutter - RAIL_GAP - this.railW));

        int furniture = PADDING * 2 + HEAD_H;
        int wanted = furniture + WANTED_CARDS * (CARD_H + CARD_GAP) - CARD_GAP;
        int floor = Math.max(furniture + CARD_H, railHeight() + PADDING * 2);

        this.panelH = Math.min(this.height - MARGIN_Y * 2, Math.max(floor, wanted));

        int cluster = this.railW + RAIL_GAP + this.panelW + gutter;
        this.panelX = Math.max(MARGIN_X, (this.width - cluster) / 2) + this.railW + RAIL_GAP;
        this.panelY = Math.max(MARGIN_Y, (this.height - this.panelH) / 2);

        int room = PADDING + BAR_ROOM;
        this.listLeft = this.panelX + room;
        this.listWidth = this.panelW - room * 2;
        this.listTop = this.panelY + PADDING + HEAD_H;
        this.listHeight = this.panelH - PADDING * 2 - HEAD_H;
        this.barX = this.panelX + this.panelW - PADDING - BAR_W;

        this.shown = this.page.logs();

        if (this.page == Page.IMPORT && this.sources == null) this.sources = LogImport.find();
        this.contentHeight = this.page == Page.IMPORT
                ? Math.max(0, importCount() * (CARD_H + CARD_GAP) - CARD_GAP)
                : measure();
        this.scroll = Math.max(0, Math.min(maxScroll(), this.scroll));

        buildRail();
        buildCards();
    }

    private int measure() {
        if (this.shown.isEmpty()) return 0;

        int total = this.shown.size() * (CARD_H + CARD_GAP) - CARD_GAP;
        return total + (this.open == null ? 0 : openHeight());
    }

    private int railHeight() {
        int count = Page.values().length;
        return RAIL_H * count + RAIL_STEP * (count - 1);
    }

    private int railY(int index) {
        return this.panelY + index * (RAIL_H + RAIL_STEP);
    }

    private int backX() {
        return this.panelX + this.panelW + RAIL_GAP;
    }

    private void buildRail() {
        Page[] pages = Page.values();

        for (int i = 0; i < pages.length; i++) {
            Page value = pages[i];

            int count = value == Page.IMPORT ? 0 : value.logs().size();

            addRenderableWidget(new RailRow(this.panelX - RAIL_GAP - this.railW, railY(i),
                    this.railW, RAIL_H,
                    Component.translatable(value.key),
                    count == 0 ? null : Component.literal(count > 99 ? "99+" : String.valueOf(count)),
                    value.icon, value.accent,
                    () -> this.page == value,
                    () -> select(value)));
        }
    }

    private void select(Page value) {
        if (this.page == value) return;

        this.page = value;
        this.scroll = 0;
        this.sources = null;
        this.open = null;
        this.lines = List.of();
        this.openLog = null;
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private void buildCards() {
        this.cards.clear();

        int y = this.listTop - this.scroll;

        for (MatchLog log : this.shown) {
            boolean opened = log.id.equals(this.open);
            int height = CARD_H + (opened ? openHeight() : 0);

            this.cards.add(new Card(log, y, height, opened));
            y += height + CARD_GAP;
        }
    }

    private int openHeight() {
        int total = 0;
        for (Line line : this.lines) {
            total += line.height();
        }
        return total;
    }

    private List<Line> linesFor(MatchLog log) {
        List<Line> built = new ArrayList<>();
        built.add(Line.rule());

        int winner = winningCentis(log);

        for (MatchLog.Entry racer : log.racers) {
            Component value = racer.finished
                    ? Component.literal(Time.formatPrecise(racer.centis))
                    : Component.literal(com.vivet.speedrun.session.Racer.shown(
                            racer.status.isEmpty() ? "did not finish" : racer.status));

            int colour = !racer.finished ? Theme.TEXT_FAINT
                    : racer.you ? Theme.TEXT : Theme.TEXT_MUTED;

            Component note = null;
            if (racer.finished && winner > 0) {
                int behind = racer.centis - winner;
                note = behind <= 0
                        ? Component.translatable("vivet.logs.winner")
                        : Component.literal("+" + Time.formatPrecise(behind));
            }

            built.add(Line.racer(racer, value, colour, note));
        }

        if (log.racers.isEmpty()) {
            built.add(Line.fact(Component.translatable("vivet.logs.time"),
                    Component.literal(Time.formatPrecise(log.centis)), Theme.TEXT));
        }

        String ending = endingOf(log);
        if (ending != null) {
            built.add(Line.note(Component.literal(ending)));
        }

        built.add(Line.gap());

        built.add(Line.fact(Component.translatable("vivet.logs.difficulty"),
                Component.literal(log.difficulty().displayName()
                        + (log.nether ? " Nether" : "")),
                log.difficulty().color()));

        if (log.setSeed) {
            built.add(Line.fact(Component.translatable("vivet.logs.seed"),
                    Component.translatable("vivet.logs.monthly"), Theme.ACCENT));
        } else if (log.seed != 0L) {
            built.add(Line.fact(Component.translatable("vivet.logs.seed"),
                    Component.literal(String.valueOf(log.seed)), Theme.TEXT_MUTED));
        }

        if (log.rerolls > 0) {
            built.add(Line.fact(Component.translatable("vivet.logs.rerolls"),
                    Component.literal(String.valueOf(log.rerolls)), Theme.TEXT_MUTED));
        }

        if (log.personalBest && log.previousBestCentis > 0) {
            int by = log.previousBestCentis - log.centis;
            built.add(Line.fact(Component.translatable("vivet.logs.beat"),
                    Component.literal(Time.formatPrecise(log.previousBestCentis)
                            + (by > 0 ? "  by " + Time.formatPrecise(by) : "")),
                    Theme.SUCCESS));
        }

        if (log.ratingKnown && log.ratingDelta != 0) {
            built.add(Line.fact(Component.translatable("vivet.logs.rating"),
                    Component.literal((log.ratingDelta > 0 ? "+" : "") + log.ratingDelta
                            + (log.ratingAfter > 0 ? "  to " + log.ratingAfter : "")),
                    log.ratingDelta > 0 ? Theme.SUCCESS : Theme.DANGER));
        }

        if (log.xpGained > 0) {
            built.add(Line.fact(Component.translatable("vivet.logs.xp"),
                    Component.literal("+" + log.xpGained), Theme.ACCENT));
        }

        if (log.hasMarks() || log.leaderboard) {
            built.add(Line.gap());
            built.add(Line.marks());
        }

        built.add(Line.gap());
        built.add(Line.actions());
        built.add(Line.gap());

        return built;
    }

    private static String endingOf(MatchLog log) {
        String reason = log.endReason == null ? "" : log.endReason;

        if (!reason.isEmpty() && !NO_NEWS.contains(reason)) return reason;

        if (log.detail == null || log.detail.isEmpty() || "found it".equals(log.detail)) return null;
        return com.vivet.speedrun.session.Racer.shown(log.detail);
    }

    private static final java.util.Set<String> NO_NEWS =
            java.util.Set.of("Race complete.", "The match ended.", "Match complete", "Match over");

    private static int winningCentis(MatchLog log) {
        int best = 0;
        for (MatchLog.Entry racer : log.racers) {
            if (!racer.finished || racer.centis <= 0) continue;
            if (best == 0 || racer.centis < best) best = racer.centis;
        }
        return best;
    }

    private static List<Component> marksOf(MatchLog log) {
        List<Component> marks = new ArrayList<>();
        if (log.tookRecord) marks.add(Component.translatable("vivet.logs.mark.record"));
        if (log.personalBest) marks.add(Component.translatable("vivet.logs.mark.best"));
        if (log.firstFind) marks.add(Component.translatable("vivet.logs.mark.first"));
        if (log.leaderboard && !log.tookRecord) {
            marks.add(Component.translatable("vivet.logs.mark.board"));
        }
        return marks;
    }

    private static int markColour(int index, MatchLog log) {
        if (log.tookRecord && index == 0) return Theme.WARNING;
        return Theme.SUCCESS;
    }

    private void toggle(MatchLog log) {
        boolean opening = !log.id.equals(this.open);

        this.open = opening ? log.id : null;
        this.lines = opening ? linesFor(log) : List.of();
        this.openLog = opening ? log : null;

        Cues.unfolded(opening);
        rebuild();
    }

    @Override
    protected void drawScaled(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                              float partialTick) {
        Theme.fill(graphics, 0, 0, this.width, this.height, Theme.SCRIM);

        Theme.roundedBox(graphics, this.panelX, this.panelY, this.panelW, this.panelH, PANEL_R,
                Theme.PANEL);
        Theme.roundedBoxOutline(graphics, this.panelX, this.panelY, this.panelW, this.panelH,
                PANEL_R, Theme.BORDER);

        drawHead(graphics, mouseX, mouseY);

        drawWidgets(graphics, mouseX, mouseY, partialTick);

        UiButtons.button(graphics, UiButtons.Kind.BACK, backX(), this.panelY, this.overBack,
                true, false);

        Clip.on(graphics, this.panelX + 1, this.listTop,
                this.panelX + this.panelW - 1, this.listTop + this.listHeight);

        if (this.page == Page.IMPORT) {
            drawImports(graphics, mouseX, mouseY);
        } else if (this.shown.isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("vivet.logs.empty"),
                    this.panelX + this.panelW / 2, this.listTop + this.listHeight / 2 - 4,
                    Theme.TEXT_FAINT);
        } else {
            for (Card card : this.cards) {
                if (card.y() + card.height() < this.listTop
                        || card.y() > this.listTop + this.listHeight) {
                    continue;
                }
                drawCard(graphics, card, mouseX, mouseY);
            }
        }

        Clip.off(graphics);

        drawBar(graphics, mouseX, mouseY);
    }

    private void drawHead(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int y = this.panelY + PADDING;
        int left = this.panelX + PADDING + BAR_ROOM;

        graphics.text(this.font, Component.translatable(this.page.key), left, y, Theme.TEXT);

        if (!this.shown.isEmpty()) {
            Component label = armed()
                    ? Component.translatable("vivet.logs.clear_sure")
                    : Component.translatable("vivet.logs.clear");

            int[] box = clearBox(label);
            boolean over = in(mouseX, mouseY, box[0], box[1], box[2], box[3]);

            Theme.roundedBox(graphics, box[0], box[1], box[2], box[3], 3,
                    over || armed() ? Theme.withAlpha(Theme.DANGER, 0x33) : Theme.PANEL_SUNKEN);

            graphics.text(this.font, label, box[0] + 6, box[1] + 4,
                    over || armed() ? Theme.DANGER : Theme.TEXT_MUTED);
        }

        Theme.divider(graphics, this.panelX + PADDING, y + 14, this.panelW - PADDING * 2);
    }

    private int[] clearBox(Component label) {
        int width = this.font.width(label) + 12;
        int right = this.panelX + this.panelW - PADDING - BAR_ROOM;
        return new int[] { right - width, this.panelY + PADDING - 3, width, 16 };
    }

    private boolean armed() {
        return this.armedAt > 0 && System.currentTimeMillis() - this.armedAt < ARMED_MS;
    }

    private void drawCard(GuiGraphicsExtractor graphics, Card card, int mouseX, int mouseY) {
        MatchLog log = card.log();

        int x = this.listLeft;
        int width = this.listWidth;
        int y = card.y();

        boolean over = card.holds(mouseY) && mouseY >= this.listTop
                && mouseY < this.listTop + this.listHeight
                && mouseX >= x && mouseX < x + width;

        boolean lit = over && (!card.open() || mouseY < y + CARD_H);

        Theme.roundedBox(graphics, x, y, width, card.height(), CARD_R,
                card.open() ? Theme.PANEL_RAISED : lit ? Theme.mix(Theme.PANEL_SUNKEN, 0xFF2A2E3A, 0.7F)
                        : Theme.PANEL_SUNKEN);
        Theme.roundedBoxOutline(graphics, x, y, width, card.height(), CARD_R,
                card.open() ? Theme.withAlpha(this.page.accent, 0x88) : Theme.BORDER);

        drawSummary(graphics, log, x, y, width);

        if (card.open()) {
            drawOpen(graphics, log, x, y + CARD_H, width, mouseX, mouseY);
        }
    }

    private void drawSummary(GuiGraphicsExtractor graphics, MatchLog log, int x, int y, int width) {
        int left = x + CARD_PAD;
        int right = x + width - CARD_PAD;

        Target target = ItemTiers.byId(log.itemId);
        ItemStack stack = target == null ? ItemStack.EMPTY : target.displayStack();

        if (!stack.isEmpty()) {
            graphics.item(stack, left, y + CARD_H / 2 - 8);
        } else {
            Theme.roundedBox(graphics, left + 4, y + CARD_H / 2 - 4, 8, 8, 2,
                    log.difficulty().color());
        }

        int textLeft = left + 22;

        Component name = target == null
                ? Component.translatable("vivet.logs.unknown_item")
                : target.displayName();

        Component result = resultWord(log);
        int resultWidth = this.font.width(result);

        graphics.text(this.font, name.copy().withStyle(ChatFormatting.BOLD),
                textLeft, y + 6, Theme.TEXT);

        graphics.text(this.font, result, right - resultWidth, y + 6, resultColour(log));

        if (log.hasMarks()) {
            Theme.roundedBox(graphics, right - resultWidth - 8, y + 8, 4, 4, 2,
                    log.tookRecord ? Theme.WARNING : Theme.SUCCESS);
        }

        String clock = Time.formatPrecise(log.centis);
        graphics.text(this.font, Component.literal(clock),
                right - this.font.width(clock), y + 19, Theme.TEXT_MUTED);

        drawUnderline(graphics, log, textLeft, y + 19,
                right - this.font.width(clock) - 8 - textLeft);
    }

    private void drawUnderline(GuiGraphicsExtractor graphics, MatchLog log, int x, int y,
                               int room) {
        int at = x;

        String mode = log.runMode().displayName();
        graphics.text(this.font, Component.literal(mode), at, y, Theme.TEXT_MUTED);
        at += this.font.width(mode) + 6;

        String band = log.difficulty().displayName() + (log.nether ? " Nether" : "");
        if (at - x + this.font.width(band) <= room) {
            graphics.text(this.font, Component.literal(band), at, y, log.difficulty().color());
            at += this.font.width(band) + 6;
        }

        MatchLog.Entry them = log.opponent();
        if (them == null) return;

        Component versus = Component.translatable("vivet.logs.versus", them.name);
        if (at - x + this.font.width(versus) <= room) {
            graphics.text(this.font, versus, at, y, Theme.TEXT_FAINT);
        }
    }

    private static Component resultWord(MatchLog log) {
        if (log.won()) return Component.translatable("vivet.logs.win");
        if (log.lost()) return Component.translatable("vivet.logs.loss");
        if (log.drew()) return Component.translatable("vivet.logs.draw");
        if (log.noContest()) return Component.translatable("vivet.logs.no_contest");

        return Component.translatable("vivet.logs.unknown");
    }

    private static int resultColour(MatchLog log) {
        if (log.won()) return Theme.SUCCESS;
        if (log.lost()) return Theme.DANGER;
        if (log.noContest()) return Theme.WARNING;
        if (log.drew()) return Theme.TEXT_MUTED;
        return Theme.TEXT_FAINT;
    }

    private int importCount() {
        return this.sources == null ? 0 : this.sources.size();
    }

    private int importY(int index) {
        return this.listTop - this.scroll + index * (CARD_H + CARD_GAP);
    }

    private int[] bringBox(int index) {
        Component label = bringLabel(this.sources.get(index));
        int width = this.font.width(label) + 14;
        int right = this.listLeft + this.listWidth - CARD_PAD;
        return new int[] { right - width, importY(index) + (CARD_H - 18) / 2, width, 18 };
    }

    private Component bringLabel(LogImport.Source source) {
        return Component.translatable(this.brought.contains(source.name())
                ? "vivet.logs.import.done" : "vivet.logs.import.bring");
    }

    private void drawImports(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (importCount() == 0) {
            graphics.centeredText(this.font, Component.translatable("vivet.logs.import.none"),
                    this.panelX + this.panelW / 2, this.listTop + this.listHeight / 2 - 4,
                    Theme.TEXT_FAINT);
            return;
        }

        for (int i = 0; i < this.sources.size(); i++) {
            LogImport.Source source = this.sources.get(i);
            int y = importY(i);
            if (y + CARD_H < this.listTop || y > this.listTop + this.listHeight) continue;

            Theme.roundedBox(graphics, this.listLeft, y, this.listWidth, CARD_H, CARD_R, Theme.PANEL_SUNKEN);
            Theme.roundedBoxOutline(graphics, this.listLeft, y, this.listWidth, CARD_H, CARD_R, Theme.BORDER);

            int left = this.listLeft + CARD_PAD;
            graphics.text(this.font, Component.literal(source.name()).withStyle(ChatFormatting.BOLD),
                    left, y + 6, Theme.TEXT);
            graphics.text(this.font, Component.translatable("vivet.logs.import.counts",
                    source.games(), source.bests()), left, y + 19, Theme.TEXT_MUTED);

            boolean done = this.brought.contains(source.name());
            int[] box = bringBox(i);
            boolean over = !done && in(mouseX, mouseY, box[0], box[1], box[2], box[3]);
            int colour = done ? Theme.SUCCESS : over ? this.page.accent : Theme.TEXT_MUTED;

            Theme.roundedBox(graphics, box[0], box[1], box[2], box[3], 3,
                    done ? Theme.withAlpha(Theme.SUCCESS, 0x28)
                            : over ? Theme.withAlpha(this.page.accent, 0x33) : Theme.PANEL_RAISED);
            graphics.text(this.font, bringLabel(source), box[0] + 7, box[1] + 5, colour);
        }
    }

    private boolean pressedImport(int x, int y) {
        for (int i = 0; i < importCount(); i++) {
            LogImport.Source source = this.sources.get(i);
            int[] box = bringBox(i);
            if (!in(x, y, box[0], box[1], box[2], box[3])) continue;

            if (this.brought.contains(source.name())) {
                Cues.deny();
                return true;
            }
            LogImport.bring(source);
            this.brought.add(source.name());
            Cues.confirm();
            rebuild();
            return true;
        }
        return true;
    }

    private void drawOpen(GuiGraphicsExtractor graphics, MatchLog log, int x, int top, int width,
                          int mouseX, int mouseY) {
        int left = x + CARD_PAD;
        int right = x + width - CARD_PAD;
        int y = top;

        for (Line line : this.lines) {
            switch (line.kind()) {
                case RULE -> Theme.divider(graphics, left, y + 2, right - left);
                case RACER -> drawRacer(graphics, line, left, y, right, mouseX, mouseY);
                case FACT -> {
                    graphics.text(this.font, line.label(), left, y + 1, Theme.TEXT_FAINT);
                    graphics.text(this.font, line.value(),
                            right - this.font.width(line.value()), y + 1, line.colour());
                }
                case NOTE -> graphics.text(this.font, line.value(), left, y + 1, line.colour());
                case MARKS -> drawMarks(graphics, log, left, y, right);
                case ACTIONS -> drawActions(graphics, log, left, y, right, mouseX, mouseY);
                case GAP -> { }
            }
            y += line.height();
        }
    }

    private void drawRacer(GuiGraphicsExtractor graphics, Line line, int left, int y, int right,
                           int mouseX, int mouseY) {
        MatchLog.Entry racer = line.racer();
        boolean actionable = !racer.you && racer.hasAccount() && MojangAuth.isSignedIn();

        int faceX = left;
        int nameX = faceX + HEAD_SIZE + 6;

        PlayerHead.draw(graphics, racer.name, racer.uuid, faceX, y + (RACER_H - HEAD_SIZE) / 2,
                HEAD_SIZE);

        int nameWidth = this.font.width(racer.name);
        boolean overName = actionable
                && in(mouseX, mouseY, faceX, y, HEAD_SIZE + 6 + nameWidth, RACER_H);

        int textY = y + (RACER_H - 8) / 2;

        graphics.text(this.font, Component.literal(racer.name), nameX, textY,
                racer.you ? Theme.TEXT : overName ? Theme.ACCENT : Theme.TEXT_MUTED);

        if (racer.you) {
            graphics.text(this.font, Component.translatable("vivet.logs.you"),
                    nameX + nameWidth + 5, textY, Theme.TEXT_FAINT);
        }

        int at = right;

        if (actionable) {
            int buttonY = y + (RACER_H - UiButtons.SIDE) / 2;

            at -= UiButtons.SIDE;
            boolean overAdd = in(mouseX, mouseY, at, buttonY, UiButtons.SIDE, UiButtons.SIDE);

            UiButtons.button(graphics, addKind(racer.uuid), at, buttonY, overAdd, true, false);
            at -= 6;
        }

        if (line.note() != null) {
            at -= this.font.width(line.note());
            graphics.text(this.font, line.note(), at, textY, noteColour(line.note()));
            at -= 6;
        }

        Component value = line.value();
        graphics.text(this.font, value, at - this.font.width(value), textY, line.colour());
    }

    private static int noteColour(Component note) {
        return note.getString().startsWith("+") ? Theme.TEXT_FAINT : Theme.SUCCESS;
    }

    private static UiButtons.Kind addKind(String uuid) {
        Social.Standing standing = Social.standingWith(uuid);

        if (standing.friend() || standing.outgoing()) return UiButtons.Kind.REMOVE;
        return UiButtons.Kind.ADD;
    }

    private static String addAction(String uuid) {
        Social.Standing standing = Social.standingWith(uuid);

        if (standing.friend()) return "unadd";
        if (standing.incoming()) return "accept";
        if (standing.outgoing()) return "cancel";
        return "add";
    }

    private void drawMarks(GuiGraphicsExtractor graphics, MatchLog log, int left, int y,
                           int right) {
        List<Component> marks = marksOf(log);
        int at = left;

        for (int i = 0; i < marks.size(); i++) {
            Component mark = marks.get(i);
            int width = this.font.width(mark) + 10;

            if (at + width > right) return;

            int colour = markColour(i, log);
            Theme.roundedBox(graphics, at, y, width, 12, 3, Theme.withAlpha(colour, 0x28));
            graphics.text(this.font, mark, at + 5, y + 2, colour);

            at += width + 4;
        }
    }

    private void drawActions(GuiGraphicsExtractor graphics, MatchLog log, int left, int y,
                             int right, int mouseX, int mouseY) {
        int[] watch = watchBox(left, y);
        if (watch != null) {
            boolean over = in(mouseX, mouseY, watch[0], watch[1], watch[2], watch[3]);

            Theme.roundedBox(graphics, watch[0], watch[1], watch[2], watch[3], 3,
                    over ? Theme.withAlpha(Theme.ACCENT, 0x33) : Theme.PANEL_SUNKEN);
            graphics.text(this.font, Component.translatable("vivet.logs.watch"),
                    watch[0] + 7, watch[1] + 5, over ? Theme.ACCENT : Theme.TEXT_MUTED);
        }

        int[] practice = practiceBox(log, left, y);
        if (practice != null) {
            boolean over = in(mouseX, mouseY, practice[0], practice[1], practice[2], practice[3]);

            Theme.roundedBox(graphics, practice[0], practice[1], practice[2], practice[3], 3,
                    over ? Theme.withAlpha(Theme.ACCENT, 0x33) : Theme.PANEL_SUNKEN);
            graphics.text(this.font, Component.translatable("vivet.logs.practice"),
                    practice[0] + 7, practice[1] + 5, over ? Theme.ACCENT : Theme.TEXT_MUTED);
        }

        int[] remove = removeBox(right, y);
        boolean overRemove = in(mouseX, mouseY, remove[0], remove[1], remove[2], remove[3]);

        Theme.roundedBox(graphics, remove[0], remove[1], remove[2], remove[3], 3,
                overRemove ? Theme.withAlpha(Theme.DANGER, 0x33) : Theme.PANEL_SUNKEN);
        graphics.text(this.font, Component.translatable("vivet.logs.remove"),
                remove[0] + 7, remove[1] + 5, overRemove ? Theme.DANGER : Theme.TEXT_MUTED);

        String when = WHEN.format(new Date(log.endedAt));
        int middle = remove[0] - 8 - this.font.width(when);

        int[] last = practice != null ? practice : watch;

        if (last == null || middle > last[0] + last[2] + 6) {
            graphics.text(this.font, Component.literal(when), middle, remove[1] + 5,
                    Theme.TEXT_FAINT);
        }
    }

    private int[] practiceBox(MatchLog log, int left, int y) {
        if (log == null || log.itemId == null || log.itemId.isBlank() || log.seed == 0L) {
            return null;
        }

        int[] watch = watchBox(left, y);
        int x = watch == null ? left : watch[0] + watch[2] + 6;
        int width = this.font.width(Component.translatable("vivet.logs.practice")) + 14;
        return new int[] { x, y, width, 18 };
    }

    private void practiceAgain(MatchLog log) {
        Cues.confirm();

        Practice.choose(log.itemId);
        Practice.setSeed(log.seed);
        Practice.setWorld(Practice.World.SET);

        PracticeScreen.start(this.minecraft);
    }

    private int[] watchBox(int left, int y) {
        if (openReplay() == null) return null;

        int width = this.font.width(Component.translatable("vivet.logs.watch")) + 14;
        return new int[] { left, y, width, 18 };
    }

    private ReplayMatch openReplay() {
        MatchLog log = this.openLog;
        List<ReplayMatch> mine = ReplayApi.mine();
        if (log == null || log.matchId == null || log.matchId.isEmpty() || mine == null) return null;
        for (ReplayMatch match : mine) {
            if (log.matchId.equals(match.match)) return match;
        }
        return null;
    }

    private void watch(ReplayMatch match) {
        if (match == null || this.fetchingReplay) return;
        this.fetchingReplay = true;
        Cues.confirm();
        ReplayApi.open(match,
                loaded -> this.minecraft.execute(() -> {
                    this.fetchingReplay = false;
                    if (this.minecraft.gui.screen() == this) {
                        this.minecraft.gui.setScreen(new ReplayViewScreen(this, loaded));
                    }
                }),
                problem -> this.minecraft.execute(() -> {
                    this.fetchingReplay = false;
                    Cues.deny();
                }));
    }

    private int[] removeBox(int right, int y) {
        int width = this.font.width(Component.translatable("vivet.logs.remove")) + 14;
        return new int[] { right - width, y, width, 18 };
    }

    private int actionsTop(Card card) {
        int y = card.y() + CARD_H;

        for (Line line : this.lines) {
            if (line.kind() == Line.Kind.ACTIONS) return y;
            y += line.height();
        }
        return -1;
    }

    private int racerTop(Card card, MatchLog.Entry racer) {
        int y = card.y() + CARD_H;

        for (Line line : this.lines) {
            if (line.kind() == Line.Kind.RACER && line.racer() == racer) return y;
            y += line.height();
        }
        return -1;
    }

    private int maxScroll() {
        return Math.max(0, this.contentHeight - this.listHeight);
    }

    private void drawBar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (maxScroll() <= 0) return;

        boolean lit = this.draggingBar || overBar(mouseX, mouseY);

        Theme.roundedBox(graphics, this.barX, this.listTop, BAR_W, this.listHeight, BAR_W / 2,
                Theme.withAlpha(0xFFFFFFFF, 0x12));
        Theme.roundedBox(graphics, this.barX, this.listTop + thumbOffset(), BAR_W, thumbHeight(),
                BAR_W / 2, Theme.withAlpha(0xFFFFFFFF, lit ? 0xBB : 0x55));
    }

    private int thumbHeight() {
        return Math.max(18, this.listHeight * this.listHeight / Math.max(1, this.contentHeight));
    }

    private int thumbOffset() {
        int travel = this.listHeight - thumbHeight();
        return maxScroll() <= 0 ? 0 : travel * this.scroll / maxScroll();
    }

    private boolean overBar(int mouseX, int mouseY) {
        return mouseX >= this.barX - BAR_GRAB && mouseX <= this.barX + BAR_W + BAR_GRAB
                && mouseY >= this.listTop && mouseY < this.listTop + this.listHeight;
    }

    private void dragTo(int mouseY) {
        int travel = Math.max(1, this.listHeight - thumbHeight());
        int want = Math.max(0, Math.min(travel, mouseY - this.listTop - thumbHeight() / 2));

        this.scroll = Math.max(0, Math.min(maxScroll(),
                Math.round(maxScroll() * (float) want / travel)));
        rebuild();
    }

    private int scaled(double value) {
        return (int) Math.round(value / uiScale());
    }

    private static boolean in(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        this.overBack = in(scaled(mouseX), scaled(mouseY), backX(), this.panelY,
                UiButtons.SIDE, UiButtons.SIDE);

        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int x = scaled(event.x());
        int y = scaled(event.y());

        if (in(x, y, backX(), this.panelY, UiButtons.SIDE, UiButtons.SIDE)) {
            onClose();
            return true;
        }

        if (!this.shown.isEmpty()) {
            Component label = armed()
                    ? Component.translatable("vivet.logs.clear_sure")
                    : Component.translatable("vivet.logs.clear");

            int[] box = clearBox(label);
            if (in(x, y, box[0], box[1], box[2], box[3])) {
                clearPressed();
                return true;
            }
        }

        if (maxScroll() > 0 && overBar(x, y)) {
            this.draggingBar = true;
            dragTo(y);
            return true;
        }

        if (!in(x, y, this.panelX, this.listTop, this.panelW, this.listHeight)) {
            if (in(x, y, this.panelX, this.panelY, this.panelW, this.panelH)) return true;
            return super.mouseClicked(event, doubleClick);
        }

        if (this.page == Page.IMPORT) return pressedImport(x, y);

        for (Card card : this.cards) {
            if (!card.holds(y) || x < this.listLeft || x >= this.listLeft + this.listWidth) {
                continue;
            }
            return pressed(card, x, y);
        }
        return true;
    }

    private boolean pressed(Card card, int x, int y) {
        MatchLog log = card.log();

        if (card.open() && y >= card.y() + CARD_H) {
            int left = this.listLeft + CARD_PAD;
            int right = this.listLeft + this.listWidth - CARD_PAD;

            int actions = actionsTop(card);
            if (actions >= 0) {
                int[] watch = watchBox(left, actions);
                if (watch != null && in(x, y, watch[0], watch[1], watch[2], watch[3])) {
                    watch(openReplay());
                    return true;
                }

                int[] practice = practiceBox(log, left, actions);
                if (practice != null
                        && in(x, y, practice[0], practice[1], practice[2], practice[3])) {
                    practiceAgain(log);
                    return true;
                }

                int[] remove = removeBox(right, actions);
                if (in(x, y, remove[0], remove[1], remove[2], remove[3])) {
                    MatchLogs.remove(log.id);
                    Cues.discarded();

                    this.open = null;
                    this.lines = List.of();
                    this.openLog = null;
                    rebuild();
                    return true;
                }
            }

            for (MatchLog.Entry racer : log.racers) {
                if (racer.you || !racer.hasAccount() || !MojangAuth.isSignedIn()) continue;

                int top = racerTop(card, racer);
                if (top < 0 || y < top || y >= top + RACER_H) continue;

                int addX = right - UiButtons.SIDE;
                int addY = top + (RACER_H - UiButtons.SIDE) / 2;
                if (in(x, y, addX, addY, UiButtons.SIDE, UiButtons.SIDE)) {
                    Social.act(addAction(racer.uuid), racer.uuid);
                    Cues.confirm();
                    return true;
                }

                int nameWidth = HEAD_SIZE + 6 + this.font.width(racer.name);
                if (in(x, y, left, top, nameWidth, RACER_H)) {
                    this.minecraft.gui.setScreen(new ProfileScreen(this, racer.uuid));
                    return true;
                }
                return true;
            }

            toggle(log);
            return true;
        }

        toggle(log);
        return true;
    }

    private void clearPressed() {
        if (!armed()) {
            this.armedAt = System.currentTimeMillis();
            Cues.tick();
            return;
        }

        this.armedAt = 0L;

        if (this.page.mode == null) {
            MatchLogs.clear();
        } else {
            for (MatchLog log : this.page.logs()) {
                MatchLogs.remove(log.id);
            }
        }

        Cues.swept();
        this.open = null;
        this.lines = List.of();
        this.openLog = null;
        this.scroll = 0;
        rebuild();
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.draggingBar = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.draggingBar) {
            dragTo(scaled(event.y()));
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (maxScroll() > 0) {
            int updated = Math.max(0, Math.min(maxScroll(),
                    this.scroll - (int) Math.signum(deltaY) * 18));

            if (updated != this.scroll) {
                this.scroll = updated;
                rebuild();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
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
