package com.vivet.speedrun.client.screen;

import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.client.Network;
import com.vivet.speedrun.client.ResultsPrompt;
import com.vivet.speedrun.client.WorldActions;
import com.vivet.speedrun.client.ui.Clip;
import com.vivet.speedrun.client.ui.FlatButton;
import com.vivet.speedrun.client.ui.Marquee;
import com.vivet.speedrun.client.ui.Ranks;
import com.vivet.speedrun.client.ui.NameLabel;
import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.game.ItemTiers;
import com.vivet.speedrun.game.RunMode;
import com.vivet.speedrun.game.RunSummary;
import com.vivet.speedrun.game.Target;
import com.vivet.speedrun.game.Time;
import com.vivet.speedrun.p2p.MojangAuth;
import com.vivet.speedrun.p2p.RankedApi;
import com.vivet.speedrun.p2p.Social;
import com.vivet.speedrun.p2p.Standings;
import com.vivet.speedrun.session.Racer;
import com.vivet.speedrun.session.Session;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class VictoryScreen extends VivetScreen {
    private static final int WIDTH_WIDE = 460;
    private static final int WIDTH_NARROW = 300;

    private static final int PADDING = 18;
    private static final int GUTTER = 16;

    private static final int HEADER_H = 62;
    private static final int ICON_SCALE = 3;
    private static final int ROW_H = 12;
    private static final int SECTION_GAP = 12;
    private static final int BUTTON_H = 20;
    private static final int BUTTON_GAP = 4;

    private static final int MAX_STANDING_ROWS = 12;

    private static final int ICON_IN_ROW = 10;

    private final RunSummary summary;

    private record Row(String label, String value, int valueColor, String note, int noteColor,
                       ItemStack icon, String who) {
        static Row of(String label, String value, int valueColor) {
            return new Row(label, value, valueColor, "", 0, ItemStack.EMPTY, null);
        }

        Row(String label, String value, int valueColor, String note, int noteColor) {
            this(label, value, valueColor, note, noteColor, ItemStack.EMPTY, null);
        }

        static Row ofRacer(String who, String label, String value, int valueColor,
                           String note, int noteColor) {
            return new Row(label, value, valueColor, note, noteColor, ItemStack.EMPTY, who);
        }

        static Row ofItem(String label, String value, ItemStack icon) {
            return new Row(label, value, Theme.TEXT, "", 0, icon, null);
        }
    }

    private record Section(String heading, List<Row> rows) {
    }

    private final List<Section> leftSections = new ArrayList<>();
    private final List<Section> rightSections = new ArrayList<>();

    private ItemStack stack = ItemStack.EMPTY;
    private Component stackName = Component.empty();

    private int left;
    private int top;
    private int panelHeight;
    private int columnWidth;
    private int bodyY;
    private int buttonsY;
    private int noticeY;

    private int accent;

    private boolean confirmingQuit;
    private FlatButton quitButton;

    private FlatButton rematchButton;

    private FlatButton addButton;

    private volatile MojangAuth.Profile ratingAfter;

    private boolean standingAsked;

    private int standingTries;
    private long standingNextAt;

    private static final int STANDING_TRIES = 8;
    private static final long STANDING_RETRY_MS = 700L;

    private static final long MATCH_OVER_HOLD_MS = 60_000L;

    private static final int NOTICE_BAND_H = 28;

    private static final int NOTICE_H = NOTICE_BAND_H + 14;

    private static final long LEAVE_TIMEOUT_MS = 30_000L;

    private static String pendingReason;
    private static boolean pendingWon;

    private String matchOverReason;
    private boolean matchOverWon;
    private long matchOverAt;

    private boolean leaving;

    public static void noticeMatchOver(String reason, boolean won) {
        pendingReason = reason == null ? "Match over" : reason;
        pendingWon = won;
    }

    public static void clearNotice() {
        pendingReason = null;
    }

    public VictoryScreen(RunSummary summary) {
        super(titleOf(summary));
        this.summary = summary;

        com.vivet.speedrun.p2p.PostMatch.Standing pushed =
                com.vivet.speedrun.p2p.PostMatch.current();
        if (pushed != null) {
            this.ratingAfter = pushed.profile();
            this.standingTries = STANDING_TRIES;
        }
    }

    private static Component titleOf(RunSummary summary) {
        if (Session.wasConceded() || Session.wasVoided()) {
            return Component.translatable("vivet.match_over.no_contest");
        }

        if (summary.undecided()) return Component.translatable("vivet.result.pending");

        if (summary.versus) {
            return Component.translatable(summary.won() ? "vivet.result.you_win"
                    : summary.drew() ? "vivet.result.draw" : "vivet.result.you_lose");
        }

        if (summary.drew()) return Component.translatable("vivet.result.draw");
        if (summary.found) return Component.translatable("vivet.result.won");

        return Component.translatable("gave up".equals(summary.detail)
                ? "vivet.result.gave_up"
                : "out of time".equals(summary.detail)
                        ? "vivet.result.out_of_time" : "vivet.result.lost");
    }

    @Override
    protected void init() {
        super.init();

        Target found = ItemTiers.byId(this.summary.itemId);
        this.stack = found == null ? ItemStack.EMPTY : found.displayStack();
        this.stackName = found == null
                ? Component.literal(this.summary.itemId) : found.displayName();

        this.accent = this.summary.undecided() ? Theme.WARNING
                : this.summary.won() ? Theme.SUCCESS
                : this.summary.drew() ? Theme.ACCENT
                : Theme.DANGER;

        this.leftSections.clear();
        this.rightSections.clear();
        buildSections();

        if (canAddOpponent()) Social.refresh();

        int buttons = buttonCount();
        int body = Math.max(columnHeight(this.leftSections), columnHeight(this.rightSections));

        this.panelHeight = PADDING + HEADER_H + body + SECTION_GAP + 8
                + BUTTON_H * buttons + BUTTON_GAP * (buttons - 1) + PADDING
                + (this.matchOverReason == null ? 0 : NOTICE_H);

        int maxHeight = Math.max(120, this.height - 8);
        this.panelHeight = Math.min(this.panelHeight, maxHeight);

        int width = panelWidth();
        this.left = (this.width - width) / 2;
        this.top = Math.max(4, (this.height - this.panelHeight) / 2);

        this.columnWidth = this.rightSections.isEmpty()
                ? width - PADDING * 2
                : (width - PADDING * 2 - GUTTER) / 2;

        this.bodyY = this.top + PADDING + HEADER_H;
        this.buttonsY = this.top + this.panelHeight - PADDING
                - BUTTON_H * buttons - BUTTON_GAP * (buttons - 1);

        this.noticeY = this.buttonsY - NOTICE_H;

        buildButtons();
        fetchStanding();
    }

    private int bodyBottom() {
        return this.matchOverReason == null ? this.buttonsY - 8 : this.noticeY - 4;
    }

    private int panelWidth() {
        int wanted = this.rightSections.isEmpty() ? WIDTH_NARROW : WIDTH_WIDE;
        return Math.min(wanted, this.width - 8);
    }

    private static int columnHeight(List<Section> sections) {
        int height = 0;
        for (Section section : sections) {
            height += 14 + section.rows().size() * ROW_H + SECTION_GAP;
        }
        return height;
    }

    private void buildSections() {
        buildRecordSection();
        buildBoardSection();
        buildMatchSection();
        buildRatingSection();
    }

    private void buildRecordSection() {
        List<Row> rows = new ArrayList<>();
        var record = this.summary.itemRecord;

        if (this.summary.personalBest) {
            int by = this.summary.improvedBy();
            rows.add(new Row("Personal Best", Time.formatPrecise(this.summary.centis),
                    Theme.SUCCESS,
                    by > 0 ? Time.formatPrecise(by) + " faster" : "First finish",
                    Theme.SUCCESS));
        } else if (record != null && record.hasBest()) {
            int off = this.summary.missedBestBy();
            rows.add(new Row("Personal Best", Time.formatPrecise(record.bestCentis()), Theme.TEXT,
                    off > 0 ? "+" + Time.formatPrecise(off) : "",
                    Theme.TEXT_FAINT));
        }

        if (record != null && record.attempts > 0) {
            rows.add(Row.of("Attempts", record.attempts
                    + (record.finishes > 0 ? "   " + record.finishes + " found" : ""),
                    Theme.TEXT_MUTED));
        }

        var mode = this.summary.modeRecord;
        if (mode != null && mode.attempts > 0 && this.summary.mode.isVersus()) {
            rows.add(Row.of("W/L",
                    mode.wins + "W  " + mode.losses + "L"
                            + (mode.draws > 0 ? "  " + mode.draws + "D" : ""),
                    Theme.TEXT_MUTED));

            if (mode.streak > 1) {
                rows.add(Row.of("Win Streak", String.valueOf(mode.streak), Theme.SUCCESS));
            } else if (mode.bestStreak > 1) {
                rows.add(Row.of("Best Streak", String.valueOf(mode.bestStreak), Theme.TEXT_MUTED));
            }
        }

        if (!rows.isEmpty()) this.leftSections.add(new Section("Your Record", rows));
    }

    private void buildBoardSection() {
        if (!this.summary.found || !this.summary.mode.isRecorded()) return;

        List<Row> rows = new ArrayList<>();

        if (this.summary.setSeed) {
            addBoardRows(rows, this.summary.seedBoard(), "Seed", true);
            if (!rows.isEmpty()) {
                this.leftSections.add(new Section("Monthly Seed", rows));
            }
            return;
        }

        addBoardRows(rows, this.summary.itemBoard(), "Item", false);
        addBoardRows(rows, this.summary.bandBoard(), this.summary.band.displayName(), true);

        if (!rows.isEmpty()) this.leftSections.add(new Section("Leaderboards", rows));
    }

    private void addBoardRows(List<Row> rows, Standings.Board board, String what,
                              boolean nameTheItem) {
        Integer boardBest = Standings.bestCentis(board);
        if (board == null || boardBest == null) return;

        boolean tookIt = this.summary.centis < boardBest;
        int record = tookIt ? this.summary.centis : boardBest;
        boolean yours = tookIt || boardBest >= this.summary.centis;

        rows.add(new Row(what + " Record", Time.formatPrecise(record),
                yours ? Theme.SUCCESS : Theme.TEXT,
                yours ? "You!" : "by " + shorten(board.holder),
                yours ? Theme.SUCCESS : Theme.TEXT_FAINT));

        if (nameTheItem && !tookIt
                && board.bestItem != null && !board.bestItem.equals(this.summary.itemId)) {
            Target held = ItemTiers.byId(board.bestItem);
            rows.add(Row.ofItem("Item",
                    held == null ? board.bestItem : held.displayName().getString(),
                    held == null ? ItemStack.EMPTY : held.displayStack()));
        }

        int gap = Standings.centisToBoard(board, this.summary.centis);

        Integer place = tookIt ? Integer.valueOf(1)
                : board.you == null ? null : board.you.place;

        if (gap == 0 && place != null) {
            rows.add(Row.of("Your Place", "#" + place + " of " + Math.max(1, board.capacity), Theme.SUCCESS));
        } else if (gap == 0) {
            rows.add(Row.of("Top 100", "Any finish", Theme.SUCCESS));
        } else if (gap > 0) {
            rows.add(new Row("Top 100", Time.formatPrecise(gap) + " faster",
                    Theme.WARNING,
                    "under " + Time.formatPrecise(Standings.cutoffCentis(board)),
                    Theme.TEXT_FAINT));
        }
    }

    private void buildMatchSection() {
        if (!this.summary.versus) return;

        List<Row> rows = new ArrayList<>();
        String you = Session.localName();

        List<Racer> shown = this.summary.standings;
        int hidden = Math.max(0, shown.size() - MAX_STANDING_ROWS);
        if (hidden > 0) shown = shown.subList(0, MAX_STANDING_ROWS);

        int winnerCentis = winningCentis();

        for (Racer racer : shown) {
            boolean mine = racer.name.equals(you);
            boolean finished = racer.status == Racer.Status.FINISHED;

            int color = mine ? Theme.TEXT
                    : finished ? Theme.TEXT_MUTED : Theme.TEXT_FAINT;

            String note = "";
            if (finished && winnerCentis > 0) {
                int behind = racer.centis - winnerCentis;
                note = behind <= 0 ? "winner" : "+" + Time.formatPrecise(behind);
            }

            rows.add(Row.ofRacer(racer.name,
                    mine ? racer.name + " (you)" : racer.name,
                    finished ? Time.formatPrecise(racer.centis) : racer.statusText(),
                    finished ? (mine ? this.accent : color) : Theme.TEXT_FAINT,
                    note, behindColor(note)));
        }

        if (hidden > 0) {
            rows.add(Row.of("+" + hidden + " more", "", Theme.TEXT_FAINT));
        }

        if (this.summary.matchSize > 2 && this.summary.place > 0) {
            rows.add(Row.of("You Placed",
                    ordinal(this.summary.place) + " of " + this.summary.matchSize, Theme.TEXT));
        }

        int marginCentis = marginToWinner();
        if (marginCentis > 0) {
            rows.add(new Row("Margin", Time.formatPrecise(marginCentis), Theme.DANGER,
                    "behind", Theme.TEXT_FAINT));
        } else if (this.summary.won() && this.summary.versus) {
            int lead = leadOverRunnerUp();
            if (lead > 0) {
                rows.add(new Row("Margin", Time.formatPrecise(lead), Theme.SUCCESS,
                        "ahead", Theme.TEXT_FAINT));
            }
        }

        this.rightSections.add(new Section("The Match", rows));
    }

    private static int behindColor(String note) {
        return "winner".equals(note) ? Theme.SUCCESS : Theme.TEXT_FAINT;
    }

    private int winningCentis() {
        int best = -1;
        for (Racer racer : this.summary.standings) {
            if (racer.status != Racer.Status.FINISHED) continue;
            if (best < 0 || racer.centis < best) best = racer.centis;
        }
        return Math.max(0, best);
    }

    private int marginToWinner() {
        if (this.summary.margin < 0 || !this.summary.found) return 0;

        int winner = winningCentis();
        return winner <= 0 ? 0 : Math.max(0, this.summary.centis - winner);
    }

    private int leadOverRunnerUp() {
        int best = -1;
        for (Racer racer : this.summary.standings) {
            if (racer.status != Racer.Status.FINISHED) continue;
            if (racer.name.equals(Session.localName())) continue;
            if (best < 0 || racer.centis < best) best = racer.centis;
        }
        return best < 0 ? 0 : Math.max(0, best - this.summary.centis);
    }

    private void buildRatingSection() {
        if (!this.summary.mode.isRecorded()) return;

        if (this.summary.mode == RunMode.RANKED_SOLO) return;

        MojangAuth.Profile before = this.summary.ratingBefore;
        MojangAuth.Profile after = this.ratingAfter;
        MojangAuth.Profile shown = after == null ? before : after;
        if (shown == null) return;

        List<Row> rows = new ArrayList<>();

        if (shown.placed && shown.rating != null) {
            boolean known = after != null && before != null
                    && before.rating != null && after.rating != null;
            int change = known ? after.rating - before.rating : 0;

            rows.add(new Row("Points", String.valueOf(shown.rating),
                    Ranks.colorOf(Ranks.displayRank(shown)),
                    !known ? "" : change == 0 ? "No change"
                            : (change > 0 ? "+" : "") + change,
                    change > 0 ? Theme.SUCCESS : change < 0 ? Theme.DANGER : Theme.TEXT_FAINT));

            rows.add(Row.of("Rank", Ranks.nameOf(Ranks.displayRank(shown)),
                    Ranks.colorOf(Ranks.displayRank(shown))));

            Integer next = Ranks.nextThreshold(shown.rank);
            if (next != null) {
                rows.add(new Row("To " + Ranks.nameOf(Ranks.nextRankId(shown.rank)),
                        String.valueOf(Math.max(0, next - shown.rating)), Theme.TEXT_MUTED,
                        "points", Theme.TEXT_FAINT));
            }
        } else {
            rows.add(Row.of("Placements", shown.placementGames + "/" + shown.placementMatches,
                    Theme.TEXT));
        }

        rows.add(Row.of("Record", shown.wins + "W  " + shown.losses + "L  " + shown.draws + "D",
                Theme.TEXT_MUTED));

        if (shown.streak > 1) {
            rows.add(Row.of("Streak", shown.streak + " in a row", Theme.SUCCESS));
        }

        this.rightSections.add(new Section("Ranked", rows));
    }

    private void fetchStanding() {
        if (this.standingAsked) return;
        if (!this.summary.mode.isRecorded() || !MojangAuth.isSignedIn()) return;
        if (this.standingTries >= STANDING_TRIES) return;

        this.standingAsked = true;
        this.standingTries++;

        RankedApi.loadProfile(
                profile -> this.minecraft.execute(() -> {
                    this.ratingAfter = profile;

                    this.standingAsked = false;
                    this.standingNextAt = System.currentTimeMillis() + STANDING_RETRY_MS;

                    if (standingSettled()) this.standingTries = STANDING_TRIES;
                    rebuild();
                }),
                reason -> this.minecraft.execute(() -> {
                    this.standingAsked = false;
                    this.standingNextAt = System.currentTimeMillis() + STANDING_RETRY_MS;
                    RankedApi.logging("Could not read the standing after the match").accept(reason);
                }));
    }

    private boolean standingSettled() {
        MojangAuth.Profile before = this.summary.ratingBefore;
        MojangAuth.Profile after = this.ratingAfter;

        return before != null && after != null
                && before.rating != null && after.rating != null
                && !before.rating.equals(after.rating);
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    public void tick() {
        super.tick();

        if (this.standingTries > 0 && this.standingTries < STANDING_TRIES
                && !this.standingAsked && System.currentTimeMillis() >= this.standingNextAt) {
            fetchStanding();
        }

        if (this.matchOverReason == null && pendingReason != null) {
            this.matchOverReason = pendingReason;
            this.matchOverWon = pendingWon;
            this.matchOverAt = System.currentTimeMillis();
            pendingReason = null;

            rebuild();
            return;
        }

        if (this.matchOverReason != null && !this.leaving && secondsLeft() <= 0) {
            leave();
        }
    }

    private int secondsLeft() {
        long remaining = MATCH_OVER_HOLD_MS - (System.currentTimeMillis() - this.matchOverAt);
        return (int) Math.max(0L, (remaining + 999L) / 1000L);
    }

    private void leave() {
        if (this.leaving) return;
        this.leaving = true;

        String reason = this.matchOverReason;
        boolean won = this.matchOverWon;
        clearNotice();

        WorldActions.waitFor(() -> true,
                () -> WorldActions.returnFromMatch(reason, won),
                LEAVE_TIMEOUT_MS, "the frame to end");
    }

    private int buttonCount() {
        int tail = (canReport() ? 1 : 0) + (canAddOpponent() ? 1 : 0);

        if (this.matchOverReason != null) return 2 + tail;
        return (Session.isMultiplayer() || Session.isPublicMatch() || Session.wasRanked()
                ? 2 : 3) + tail;
    }

    private boolean canReport() {
        String matchId = Session.lastMatchId();
        return matchId != null && !matchId.isEmpty()
                && Session.wasPublic() && Session.wasMultiplayer();
    }

    private boolean canAddOpponent() {
        return Session.wasPublic() && Session.wasMultiplayer() && opponentUuid() != null;
    }

    private String opponentUuid() {
        String name = opponentName();
        if (name.isEmpty()) return null;

        String uuid = Session.lastAccountIdOf(name);
        return uuid == null || uuid.isBlank() ? null : uuid;
    }

    private String opponentName() {
        String mine = Session.localName();

        for (com.vivet.speedrun.session.Racer racer : Session.finalStandings()) {
            if (racer == null || racer.name == null || racer.name.isEmpty()) continue;
            if (!racer.name.equals(mine)) return racer.name;
        }
        return "";
    }

    private void addReportButton(int x, int y, int buttonWidth) {
        if (!canReport()) return;

        addRenderableWidget(new FlatButton(x, y, buttonWidth, BUTTON_H,
                Component.translatable("vivet.report.button_opponent", opponentName()),
                () -> this.minecraft.gui.setScreen(
                        new ReportScreen(this, Session.lastMatchId(), opponentName())),
                FlatButton.Style.GHOST));
    }

    private void addTailButtons(int x, int y, int buttonWidth) {
        if (canAddOpponent()) {
            this.addButton = addRenderableWidget(new FlatButton(x, y, buttonWidth, BUTTON_H,
                    Component.translatable("vivet.button.add_opponent"),
                    this::addOpponent,
                    FlatButton.Style.GHOST));

            refreshAddButton();
            y += BUTTON_H + BUTTON_GAP;
        }

        addReportButton(x, y, buttonWidth);
    }

    private void addOpponent() {
        String uuid = opponentUuid();
        if (uuid == null) return;

        Social.Standing standing = Social.standingWith(uuid);
        if (standing.friend() || standing.outgoing() || standing.blocked()
                || standing.blockedBy()) {
            com.vivet.speedrun.client.Cues.nothingHappened();
            return;
        }

        com.vivet.speedrun.client.Cues.confirm();
        Social.act(standing.incoming() ? "accept" : "add", uuid);
    }

    private void refreshAddButton() {
        if (this.addButton == null) return;

        Social.Standing standing = Social.standingWith(opponentUuid());

        String key;
        boolean pressable = false;

        if (standing.friend()) {
            key = "vivet.button.add_opponent.friends";
        } else if (standing.outgoing()) {
            key = "vivet.button.add_opponent.sent";
        } else if (standing.incoming()) {
            key = "vivet.button.add_opponent.accept";
            pressable = true;
        } else if (standing.blocked() || standing.blockedBy()) {
            key = "vivet.button.add_opponent";
        } else {
            key = "vivet.button.add_opponent";
            pressable = true;
        }

        this.addButton.setMessage(Component.translatable(key, opponentName()));
        this.addButton.active = pressable;
    }

    private void buildButtons() {
        this.rematchButton = null;
        this.quitButton = null;
        this.addButton = null;

        int buttonWidth = panelWidth() - PADDING * 2;
        int x = this.left + PADDING;
        int y = this.buttonsY;
        boolean multiplayer = Session.isMultiplayer();

        if (Session.wasRanked()) {
            addRenderableWidget(new FlatButton(x, y, buttonWidth, BUTTON_H,
                    Component.translatable("vivet.button.queue_again"),
                    this::queueAgain,
                    FlatButton.Style.PRIMARY));
            y += BUTTON_H + BUTTON_GAP;

            this.quitButton = addRenderableWidget(new FlatButton(x, y, buttonWidth, BUTTON_H,
                    Component.translatable("vivet.button.leave_match"),
                    this::quit,
                    FlatButton.Style.DANGER));

            addTailButtons(x, y + BUTTON_H + BUTTON_GAP, buttonWidth);
            return;
        }

        if (Session.isPublicMatch() || this.matchOverReason != null) {
            if (this.matchOverReason == null && Session.canRematch()) {
                this.rematchButton = addRenderableWidget(new FlatButton(x, y, buttonWidth, BUTTON_H,
                        rematchLabel(), this::toggleRematch, FlatButton.Style.PRIMARY));
            } else {
                addRenderableWidget(new FlatButton(x, y, buttonWidth, BUTTON_H,
                        Component.translatable(Session.wasRanked()
                                ? "vivet.button.back_to_ranked" : "vivet.button.back_to_queue"),
                        this::leave,
                        FlatButton.Style.PRIMARY));
            }
            y += BUTTON_H + BUTTON_GAP;

            this.quitButton = addRenderableWidget(new FlatButton(x, y, buttonWidth, BUTTON_H,
                    Component.translatable("vivet.button.leave_match"),
                    this::quit,
                    FlatButton.Style.DANGER));

            addTailButtons(x, y + BUTTON_H + BUTTON_GAP, buttonWidth);
            return;
        }

        boolean guest = Session.isActive() && Session.role() != Session.Role.HOST;
        FlatButton again = addRenderableWidget(new FlatButton(x, y, buttonWidth, BUTTON_H,
                Component.translatable(guest
                        ? "vivet.button.waiting_host" : "vivet.button.play_again"),
                this::playAgain,
                FlatButton.Style.PRIMARY));
        again.active = !guest && (!Session.isActive() || Session.canHostStart());
        y += BUTTON_H + BUTTON_GAP;

        if (!multiplayer) {
            addRenderableWidget(new FlatButton(x, y, buttonWidth, BUTTON_H,
                    Component.translatable("vivet.button.keep_playing"),
                    this::onClose));
            y += BUTTON_H + BUTTON_GAP;
        }

        this.quitButton = addRenderableWidget(new FlatButton(x, y, buttonWidth, BUTTON_H,
                Component.translatable(multiplayer
                        ? "vivet.button.quit_game" : "vivet.button.return_to_menu"),
                this::quit,
                FlatButton.Style.DANGER));

        addTailButtons(x, y + BUTTON_H + BUTTON_GAP, buttonWidth);
    }

    private void playAgain() {
        ResultsPrompt.dismiss();

        if (Session.isActive()) {
            VivetSpeedrun.LOGGER.info("Starting a new match");
            Session.proposeNewMatch();
            return;
        }

        VivetSpeedrun.LOGGER.info("Play again: creating new world");
        WorldActions.recreateWorld(this.minecraft, null, Network::playAgain);
    }

    private void queueAgain() {
        ResultsPrompt.dismiss();

        boolean solo = Session.wasRankedSolo();
        if (Session.isActive()) Session.quit();

        RankedScreen.queueAgain(solo ? RankedScreen.Mode.SOLO : RankedScreen.Mode.VERSUS);
        WorldActions.quitRaceTo(this.minecraft, RankedScreen::returnDestination);
    }

    private Component rematchLabel() {
        return Component.translatable(Session.wantsRematch()
                        ? "vivet.button.rematch_ready" : "vivet.button.rematch_count",
                Session.rematchCount(), Session.matchPlayerCount());
    }

    private void toggleRematch() {
        if (Session.wantsRematch()) {
            Session.cancelRematch();
        } else {
            Session.requestRematch();
        }
    }

    private void quit() {
        boolean multiplayer = Session.isMultiplayer();
        ResultsPrompt.dismiss();

        if (Session.isPublicMatch() || Session.wasRanked()) {
            if (Session.isActive()) Session.quit();
            WorldActions.quitRaceTo(this.minecraft, Session.wasRanked()
                    ? RankedScreen::returnDestination
                    : FindMatchScreen::returnDestination);
            return;
        }

        if (multiplayer && !this.confirmingQuit) {
            this.confirmingQuit = true;
            this.quitButton.setMessage(Component.translatable("vivet.button.quit_confirm"));
            return;
        }

        if (Session.isActive()) {
            Session.quit();
            WorldActions.quitRace(this.minecraft);
        } else {
            WorldActions.deleteWorld(this.minecraft);
        }
    }

    @Override
    protected void drawScaled(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                              float partialTick) {
        Theme.fill(graphics, 0, 0, this.width, this.height, Theme.SCRIM);

        int width = panelWidth();
        Theme.panel(graphics, this.left, this.top, width, this.panelHeight);

        drawHeader(graphics, width);

        int leftX = this.left + PADDING;
        int rightX = leftX + this.columnWidth + GUTTER;

        Clip.on(graphics, this.left, this.bodyY - 2, this.left + width, bodyBottom());

        drawColumn(graphics, this.leftSections, leftX, this.bodyY, this.columnWidth);
        drawColumn(graphics, this.rightSections, rightX, this.bodyY, this.columnWidth);

        Clip.off(graphics);

        if (this.rematchButton != null) {
            boolean ready = Session.wantsRematch();
            this.rematchButton.setMessage(rematchLabel());
            this.rematchButton.setStyle(ready
                    ? FlatButton.Style.READY : FlatButton.Style.PRIMARY);
        }

        refreshAddButton();

        drawMatchOverNotice(graphics, width);

        Theme.divider(graphics, this.left + PADDING, this.buttonsY - 8, width - PADDING * 2);
        drawWidgets(graphics, mouseX, mouseY, partialTick);

        drawRematchMark(graphics);
    }

    private void drawMatchOverNotice(GuiGraphicsExtractor graphics, int width) {
        if (this.matchOverReason == null) return;

        int x = this.left + PADDING;
        int y = this.noticeY;
        int band = width - PADDING * 2;

        int tone = Session.wasVoided() || Session.wasConceded() ? Theme.TEXT_MUTED
                : this.matchOverWon ? Theme.SUCCESS : Theme.WARNING;

        Theme.roundedRect(graphics, x, y, band, NOTICE_BAND_H, Theme.withAlpha(tone, 0x1E));

        graphics.text(this.font, Component.literal(this.matchOverReason), x + 6, y + 5, tone);

        graphics.text(this.font,
                Component.translatable("vivet.result.returning_in", secondsLeft()),
                x + 6, y + 16, Theme.TEXT_FAINT);
    }

    private void drawHeader(GuiGraphicsExtractor graphics, int width) {
        int x = this.left + PADDING;
        int y = this.top + PADDING;
        Font font = this.font;

        int iconSpace = 0;
        if (!this.stack.isEmpty()) {
            var pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(x, y);
            pose.scale(ICON_SCALE, ICON_SCALE);
            graphics.item(this.stack, 0, 0);
            pose.popMatrix();

            iconSpace = 16 * ICON_SCALE + 12;
        }

        graphics.text(font, this.title.copy().withStyle(ChatFormatting.BOLD),
                x + iconSpace, y + 4, this.accent);

        Marquee.drawSpotify(graphics, font, this.stackName, x + iconSpace, y + 18,
                this.left + width - PADDING - headlineWidth() - 10 - (x + iconSpace), Theme.TEXT);
        graphics.text(font, Component.literal(subtitle()), x + iconSpace, y + 32, Theme.TEXT_FAINT);

        drawHeadlineTime(graphics, this.left + width - PADDING, y);

        Theme.divider(graphics, x, this.top + PADDING + HEADER_H - 10, width - PADDING * 2);
    }

    private int headlineWidth() {
        String caption = this.summary.found ? "elapsed"
                : this.summary.detail.isBlank() ? "elapsed"
                : com.vivet.speedrun.session.Racer.shown(this.summary.detail);
        return Math.max(this.font.width(Time.formatPrecise(this.summary.centis)) * 2,
                this.font.width(caption));
    }

    private void drawHeadlineTime(GuiGraphicsExtractor graphics, int right, int y) {
        String clock = Time.formatPrecise(this.summary.centis);
        int scale = 2;
        int clockWidth = this.font.width(clock) * scale;

        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(right - clockWidth, y + 2);
        pose.scale(scale, scale);
        graphics.text(this.font, Component.literal(clock), 0, 0, this.accent);
        pose.popMatrix();

        String caption = this.summary.found ? "elapsed"
                : this.summary.detail.isBlank() ? "elapsed"
                : com.vivet.speedrun.session.Racer.shown(this.summary.detail);

        graphics.text(this.font, Component.literal(caption),
                right - this.font.width(caption), y + 22, Theme.TEXT_FAINT);
    }

    private String subtitle() {
        StringBuilder text = new StringBuilder(this.summary.mode.displayName());
        text.append("   ").append(this.summary.band.displayName());

        if (this.summary.versus && this.summary.matchSize > 2) {
            text.append("   ").append(this.summary.matchSize).append(" players");
        }
        if (this.summary.rerolls > 0) {
            text.append("   ").append(this.summary.rerolls)
                    .append(this.summary.rerolls == 1 ? " reroll" : " rerolls");
        }
        return text.toString();
    }

    private void drawColumn(GuiGraphicsExtractor graphics, List<Section> sections,
                            int x, int y, int width) {
        int cursor = y;

        for (Section section : sections) {
            graphics.text(this.font,
                    Component.literal(section.heading().toUpperCase(java.util.Locale.ROOT)),
                    x, cursor, Theme.TEXT_FAINT);
            cursor += 14;

            for (Row row : section.rows()) {
                drawRow(graphics, row, x, cursor, width);
                cursor += ROW_H;
            }
            cursor += SECTION_GAP;
        }
    }

    private void drawRow(GuiGraphicsExtractor graphics, Row row, int x, int y, int width) {
        int right = x + width;

        int noteWidth = 0;
        if (!row.note().isEmpty()) {
            noteWidth = this.font.width(row.note());
            graphics.text(this.font, Component.literal(row.note()),
                    right - noteWidth, y, row.noteColor());
            noteWidth += 8;
        }

        String value = row.value();
        int valueWidth = this.font.width(value);
        graphics.text(this.font, Component.literal(value),
                right - noteWidth - valueWidth, y, row.valueColor());

        int iconWidth = 0;
        if (!row.icon().isEmpty()) {
            iconWidth = ICON_IN_ROW + 3;

            var pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(right - noteWidth - valueWidth - iconWidth, y - 1);
            pose.scale(ICON_IN_ROW / 16.0F, ICON_IN_ROW / 16.0F);
            graphics.item(row.icon(), 0, 0);
            pose.popMatrix();
        }

        int room = width - noteWidth - valueWidth - iconWidth - 8;

        if (row.who() != null) {
            NameLabel.drawTaggedScrolling(graphics, this.font, NameLabel.of(row.who()),
                    row.label(), x, y, room, Theme.TEXT_MUTED);
            return;
        }

        Marquee.drawLeft(graphics, this.font, row.label(), x, y, room, Theme.TEXT_MUTED);
    }

    private void drawRematchMark(GuiGraphicsExtractor graphics) {
        if (this.rematchButton == null) return;

        int size = 9;
        int x = this.rematchButton.getX() + 8;
        int y = this.rematchButton.getY() + (BUTTON_H - size) / 2;

        Theme.roundedOutline(graphics, x, y, size, size, 0xFFFFFFFF);
        if (!Session.wantsRematch()) return;

        Theme.roundedRect(graphics, x + 2, y + 2, size - 4, size - 4, 0xFFFFFFFF);
    }

    private static String shorten(String name) {
        if (name == null) return "?";
        return name.length() <= 14 ? name : name.substring(0, 13) + "...";
    }

    private static String ordinal(int place) {
        return switch (place) {
            case 1 -> "1st";
            case 2 -> "2nd";
            case 3 -> "3rd";
            default -> place + "th";
        };
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void onClose() {
        if (this.matchOverReason != null) {
            leave();
            return;
        }

        ResultsPrompt.dismiss();
        this.minecraft.gui.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
