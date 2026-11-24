package com.vivet.speedrun.client.screen;

import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.client.Cues;
import com.vivet.speedrun.client.LoadingTakeover;
import com.vivet.speedrun.client.ui.Dropdown;
import com.vivet.speedrun.client.ui.FlatButton;
import com.vivet.speedrun.client.ui.PickerRow;
import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.client.ui.UiButtons;
import com.vivet.speedrun.config.ConfigManager;
import com.vivet.speedrun.game.Difficulty;
import com.vivet.speedrun.game.Time;
import com.vivet.speedrun.p2p.InviteCode;
import com.vivet.speedrun.p2p.Matchmaker;
import com.vivet.speedrun.p2p.MojangAuth;
import com.vivet.speedrun.p2p.RankedApi;
import com.vivet.speedrun.session.Session;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.Map;

public class FindMatchScreen extends VivetScreen {
    private static final int MAX_WIDTH = 330;
    private static final int GAP = 10;

    private static final int QUEUE_H = 118;
    private static final int PRIVATE_H = 62;

    private static final int FOOT_H = 26;

    private static final int CARD_PAD = 10;
    private static final int RULE_Y = 19;

    private static final int FIELD_Y = 34;
    private static final int FIELD_H = 18;
    private static final int FIELD_W = 120;

    private static final int PLAY_Y = 70;
    private static final int PLAY_H = 20;
    private static final int PLAY_W = 120;

    private static final int COUNT_Y = 98;

    private static final int ROW_Y = 30;
    private static final int ROW_H = 20;
    private static final int JOIN_W = 46;
    private static final int CREATE_W = 62;

    private static final int OPTION_H = 20;

    private static final long SETUP_TIMEOUT_MS = 45_000L;

    private enum Stage {
        BROWSING,

        SEARCHING,

        JOINING,
    }

    private final Screen parent;

    private static String carriedStatus = "";
    private static int carriedStatusColor = Theme.TEXT_MUTED;

    private Matchmaker matchmaker;

    private long redialAt;
    private long redialWaitMs;

    private String dropReason;

    private volatile Stage stage = Stage.BROWSING;
    private volatile Map<String, Integer> counts = new LinkedHashMap<>();
    private volatile boolean connected;
    private volatile String status = "";
    private volatile int statusColor = Theme.TEXT_MUTED;
    private volatile long searchingSince;

    private volatile long joiningSince;

    private volatile int linePosition;

    private boolean signInTried;

    private Difficulty band;
    private boolean expanded;

    private EditBox codeBox;

    private String typedCode = "";

    private boolean overBack;
    private boolean overCode;

    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int privateY;

    public FindMatchScreen(Screen parent) {
        super(Component.translatable("vivet.screen.find_match"));
        this.parent = parent;
        this.band = ConfigManager.get().publicBand;

        this.status = carriedStatus;
        this.statusColor = carriedStatusColor;
        carriedStatus = "";
    }

    public static void carryStatus(String message, int color) {
        carriedStatus = message == null ? "" : message;
        carriedStatusColor = color;
    }

    public static Screen returnDestination() {
        return new FindMatchScreen(new VivetTitleScreen());
    }

    @Override
    protected void init() {
        super.init();

        this.panelWidth = Math.min(MAX_WIDTH, this.width - 16 - UiButtons.SIDE - 10);
        this.panelHeight = QUEUE_H + GAP + PRIVATE_H + FOOT_H;

        this.left = (this.width - this.panelWidth) / 2;
        this.top = Math.max(0, (this.height - this.panelHeight) / 2);
        this.privateY = this.top + QUEUE_H + GAP;

        buildQueue();
        buildPrivate();

        if (this.expanded) buildOptions();

        if (this.matchmaker == null) {
            if (MojangAuth.isSignedIn()) {
                openLobby();
            } else if (!this.signInTried) {
                ensureSignedIn();
            }
        }
    }

    private int backX() {
        return this.left + this.panelWidth + 10;
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private void buildQueue() {
        Dropdown picker = addRenderableWidget(new Dropdown(
                this.left + this.panelWidth - CARD_PAD - FIELD_W, this.top + FIELD_Y,
                FIELD_W, FIELD_H,
                Component.empty(),
                () -> Component.literal(this.band.displayName()),
                () -> this.band.color(),
                () -> this.expanded,
                this::toggleExpanded));

        picker.active = this.stage == Stage.BROWSING;

        boolean closed = this.stage == Stage.BROWSING && Matchmaker.casualClosed();
        this.closedShown = Matchmaker.casualClosed();

        FlatButton play = addRenderableWidget(new FlatButton(
                this.left + (this.panelWidth - PLAY_W) / 2, this.top + PLAY_Y, PLAY_W, PLAY_H,
                Component.translatable(closed ? "vivet.find_match.closed" : switch (this.stage) {
                    case BROWSING -> "vivet.button.play";
                    case SEARCHING -> "vivet.button.cancel_search";
                    case JOINING -> "vivet.button.cancel_setup";
                }),
                this::toggleSearch,
                this.stage == Stage.BROWSING
                        ? FlatButton.Style.PRIMARY : FlatButton.Style.DANGER));

        play.active = (this.stage == Stage.JOINING || this.connected) && !this.expanded && !closed;
    }

    private boolean closedShown;

    private void buildPrivate() {
        int y = this.privateY + ROW_Y;

        int boxX = this.left + CARD_PAD;
        int boxWidth = codeWidth();
        int joinX = separatorX() - 11 - JOIN_W;
        int createX = this.left + this.panelWidth - CARD_PAD - CREATE_W;

        this.codeBox = new EditBox(this.font, boxX + 7, Theme.textTop(y, ROW_H), boxWidth - 14, 10,
                Component.translatable("vivet.find_match.code"));
        this.codeBox.setBordered(false);
        this.codeBox.setMaxLength(64);
        this.codeBox.setTextColor(Theme.TEXT);
        this.codeBox.setHint(Component.translatable("vivet.find_match.code_hint"));
        this.codeBox.setValue(this.typedCode);
        this.codeBox.setResponder(value -> this.typedCode = value);
        addRenderableWidget(this.codeBox);

        addRenderableWidget(new FlatButton(joinX, y, JOIN_W, ROW_H,
                Component.translatable("vivet.button.join"),
                this::joinPrivate,
                FlatButton.Style.SECONDARY));

        addRenderableWidget(new FlatButton(createX, y, CREATE_W, ROW_H,
                Component.translatable("vivet.button.create"),
                this::createPrivate,
                FlatButton.Style.SECONDARY));
    }

    private int separatorX() {
        return this.left + this.panelWidth - CARD_PAD - CREATE_W - 11;
    }

    private int codeWidth() {
        return Math.max(40, separatorX() - 11 - JOIN_W - 6 - (this.left + CARD_PAD));
    }

    private void buildOptions() {
        int x = this.left + CARD_PAD;
        int listWidth = this.panelWidth - CARD_PAD * 2;
        int listY = this.top + FIELD_Y + FIELD_H + 2;

        sealUnderList();

        for (int i = 0; i < Difficulty.values().length; i++) {
            Difficulty option = Difficulty.values()[i];

            addRenderableWidget(new PickerRow(x, listY + i * OPTION_H, listWidth, OPTION_H,
                    Component.literal(option.displayName()),
                    () -> queueNote(option),
                    () -> queueNoteColor(option),
                    () -> this.connected && waitingOn(option) > 0,
                    option.color(),
                    option == this.band,
                    () -> choose(option)));
        }

        addRenderableWidget(new FlatButton(0, 0, this.width, this.height,
                Component.empty(), this::toggleExpanded, FlatButton.Style.INVISIBLE));
    }

    private void toggleExpanded() {
        if (this.stage != Stage.BROWSING) return;

        this.expanded = !this.expanded;
        rebuild();
    }

    private void choose(Difficulty option) {
        this.band = option;
        this.expanded = false;

        ConfigManager.get().publicBand = option;
        ConfigManager.save();
        Cues.tick();
        rebuild();
    }

    private void joinPrivate() {
        String code = InviteCode.normalize(this.typedCode);

        if (code == null) {
            this.status = "Invalid code. Codes look like VIVET-XXXXX-XXXXX";
            this.statusColor = Theme.DANGER;
            Cues.deny();
            return;
        }

        Cues.confirm();
        leaveQueue();
        this.minecraft.gui.setScreen(SessionScreen.joining(this, code));
    }

    private void createPrivate() {
        Cues.confirm();
        leaveQueue();
        this.minecraft.gui.setScreen(SessionScreen.creating(this));
    }

    private void leaveQueue() {
        if (this.matchmaker != null) {
            this.matchmaker.close();
            this.matchmaker = null;
        }
        this.stage = Stage.BROWSING;
        this.linePosition = 0;
    }

    private void ensureSignedIn() {
        this.signInTried = true;

        if (MojangAuth.isSignedIn()) {
            openLobby();
            return;
        }

        this.status = "Signing in...";
        this.statusColor = Theme.TEXT_FAINT;

        MojangAuth.signIn(
                identity -> this.minecraft.execute(this::openLobby),
                reason -> this.minecraft.execute(() -> {
                    this.status = reason;
                    this.statusColor = Theme.DANGER;
                    rebuild();
                }));
    }

    private void openLobby() {
        if (this.matchmaker != null) return;

        this.matchmaker = new Matchmaker(new Lobby());
        this.matchmaker.connect();
        rebuild();
    }

    private Component queueNote(Difficulty option) {
        return Component.literal(String.valueOf(waitingOn(option)));
    }

    private int queueNoteColor(Difficulty option) {
        if (!this.connected) return Theme.TEXT_FAINT;
        return waitingOn(option) > 0 ? option.color() : Theme.TEXT_FAINT;
    }

    private int waitingOn(Difficulty option) {
        Integer waiting = this.counts.get(option.id());
        return waiting == null ? 0 : waiting;
    }

    private int totalWaiting() {
        int total = 0;
        for (Difficulty option : Difficulty.values()) {
            total += waitingOn(option);
        }
        return total;
    }

    private void toggleSearch() {
        if (this.stage == Stage.JOINING) {
            Session.quit();
            matchFailed("Match didn't start");
            return;
        }

        if (this.matchmaker == null) return;

        if (this.stage == Stage.SEARCHING) {
            this.matchmaker.unqueue();
            this.stage = Stage.BROWSING;
            this.linePosition = 0;
            this.status = "";
            Cues.queueLeft();
            rebuild();
            return;
        }

        this.matchmaker.queue(this.band);
        this.stage = Stage.SEARCHING;
        this.searchingSince = System.currentTimeMillis();
        this.linePosition = 0;
        this.status = "";
        Cues.queueJoined();
        rebuild();
    }

    private final class Lobby implements Matchmaker.Listener {
        @Override
        public void onCounts(Map<String, Integer> casual, Map<String, Integer> ranked) {
            FindMatchScreen.this.minecraft.execute(() -> {
                boolean first = !FindMatchScreen.this.connected;

                FindMatchScreen.this.counts = casual;
                FindMatchScreen.this.connected = true;
                FindMatchScreen.this.redialWaitMs = 0L;
                if (first && FindMatchScreen.this.dropReason != null
                        && FindMatchScreen.this.dropReason.equals(FindMatchScreen.this.status)) {
                    FindMatchScreen.this.status = "";
                }
                FindMatchScreen.this.dropReason = null;

                if (first && searchAgainPending && FindMatchScreen.this.stage == Stage.BROWSING
                        && !Matchmaker.casualClosed()) {
                    searchAgainPending = false;
                    toggleSearch();
                    return;
                }

                if (first || FindMatchScreen.this.closedShown != Matchmaker.casualClosed()) rebuild();
            });
        }

        @Override
        public void onHostedMatched(Difficulty matchedBand, RankedApi.HostedTicket ticket) {
            Cues.queuePop();
            FindMatchScreen.this.minecraft.execute(() -> beginHostedMatch(matchedBand, ticket));
        }

        @Override
        public void onLine(int position) {
            FindMatchScreen.this.minecraft.execute(() -> {
                if (FindMatchScreen.this.stage != Stage.SEARCHING) return;

                int was = FindMatchScreen.this.linePosition;
                FindMatchScreen.this.linePosition = position;

                if (was == 0 && position > 0) {
                    Cues.queueWaiting();
                } else if (was > 0 && position == 0) {
                    Cues.queueLeft();
                    FindMatchScreen.this.searchingSince = System.currentTimeMillis();
                }
            });
        }

        @Override
        public void onRefused(String reason) {
            FindMatchScreen.this.minecraft.execute(() -> {
                FindMatchScreen.this.stage = Stage.BROWSING;
                FindMatchScreen.this.status = reason;
                FindMatchScreen.this.statusColor = Theme.DANGER;
                rebuild();
            });
        }

        @Override
        public void onFailed(String reason) {
            FindMatchScreen.this.minecraft.execute(() -> {
                if (FindMatchScreen.this.stage == Stage.JOINING) return;

                FindMatchScreen.this.matchmaker = null;
                FindMatchScreen.this.connected = false;
                FindMatchScreen.this.stage = Stage.BROWSING;
                FindMatchScreen.this.status = reason;
                FindMatchScreen.this.statusColor = Theme.DANGER;
                FindMatchScreen.this.dropReason = reason;
                scheduleRedial();
                rebuild();
            });
        }
    }

    private void scheduleRedial() {
        this.redialWaitMs = this.redialWaitMs == 0L ? 2_000L : Math.min(60_000L, this.redialWaitMs * 2L);
        this.redialAt = System.currentTimeMillis() + this.redialWaitMs;
    }

    private void redialIfDue() {
        if (this.redialAt == 0L || System.currentTimeMillis() < this.redialAt) return;
        if (this.matchmaker != null || this.stage != Stage.BROWSING) {
            this.redialAt = 0L;
            return;
        }
        if (!MojangAuth.isSignedIn()) return;

        this.redialAt = 0L;
        openLobby();
    }

    @Override
    public void tick() {
        super.tick();
        redialIfDue();
    }

    private void beginHostedMatch(Difficulty matchedBand, RankedApi.HostedTicket ticket) {
        this.stage = Stage.JOINING;
        this.joiningSince = System.currentTimeMillis();
        this.linePosition = 0;
        this.expanded = false;
        this.band = matchedBand;
        this.status = "Match found!";
        this.statusColor = Theme.SUCCESS;
        rebuild();

        if (this.matchmaker != null) {
            this.matchmaker.close();
            this.matchmaker = null;
        }

        VivetSpeedrun.LOGGER.info("Unranked {} match on {}:{}",
                matchedBand.id(), ticket.host, ticket.port);
        com.vivet.speedrun.client.HostedConnect.joinMatch(this.minecraft, this, matchedBand, ticket,
                false);
    }

    private static boolean searchAgainPending;

    public static void searchAgain() {
        searchAgainPending = true;
    }

    private void matchFailed(String reason) {
        LoadingTakeover.abort();
        Session.stop();

        this.stage = Stage.BROWSING;
        this.linePosition = 0;
        this.status = reason;
        this.statusColor = Theme.DANGER;
        this.connected = false;

        this.matchmaker = new Matchmaker(new Lobby());
        this.matchmaker.connect();
        rebuild();
    }

    @Override
    protected void drawScaled(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                              float partialTick) {
        Theme.fill(graphics, 0, 0, this.width, this.height, Theme.SCRIM);

        if (this.stage == Stage.JOINING
                && System.currentTimeMillis() - this.joiningSince > SETUP_TIMEOUT_MS) {
            Session.quit();
            matchFailed("Your opponent didn't show up");
        }

        drawQueueCard(graphics);
        drawPrivateCard(graphics);
        drawStatus(graphics);

        this.overCode = in(mouseX, mouseY, this.left + CARD_PAD, this.privateY + ROW_Y,
                codeWidth(), ROW_H);
        this.overBack = in(mouseX, mouseY, backX(), this.top, UiButtons.SIDE, UiButtons.SIDE);
        UiButtons.button(graphics, UiButtons.Kind.BACK, backX(), this.top, this.overBack,
                true, false);

        if (this.expanded) drawOpenListFrame(graphics);

        drawWidgets(graphics, mouseX, mouseY, partialTick);
    }

    private void card(GuiGraphicsExtractor graphics, int y, int height, Component title) {
        Theme.roundedBox(graphics, this.left, y, this.panelWidth, height, 4, Theme.PANEL);
        Theme.roundedBoxOutline(graphics, this.left, y, this.panelWidth, height, 4, Theme.BORDER);

        graphics.text(this.font, title.copy().withStyle(ChatFormatting.BOLD),
                this.left + CARD_PAD, y + 7, Theme.TEXT);
        Theme.fill(graphics, this.left + CARD_PAD, y + RULE_Y,
                this.left + this.panelWidth - CARD_PAD, y + RULE_Y + 1, Theme.BORDER);
    }

    private void drawQueueCard(GuiGraphicsExtractor graphics) {
        card(graphics, this.top, QUEUE_H, Component.translatable("vivet.find_match.unranked"));

        graphics.text(this.font, Component.translatable("vivet.find_match.difficulty"),
                this.left + CARD_PAD, this.top + FIELD_Y + (FIELD_H - 8) / 2, Theme.TEXT_MUTED);

        Theme.divider(graphics, this.left + CARD_PAD, this.top + PLAY_Y - 8,
                this.panelWidth - CARD_PAD * 2);

        int total = totalWaiting();
        boolean anybody = this.connected && total > 0;

        Component label = !this.connected
                ? Component.translatable("vivet.random_match.offline")
                : Component.translatable(total == 1
                        ? "vivet.random_match.total_one" : "vivet.random_match.total", total);

        int color = !this.connected ? Theme.TEXT_FAINT
                : anybody ? Theme.SUCCESS : Theme.TEXT_MUTED;

        Theme.liveDot(graphics, this.left + CARD_PAD, this.top + COUNT_Y + 2, color, anybody);
        graphics.text(this.font, label, this.left + CARD_PAD + 9, this.top + COUNT_Y, color);
    }

    private void drawPrivateCard(GuiGraphicsExtractor graphics) {
        card(graphics, this.privateY, PRIVATE_H,
                Component.translatable("vivet.find_match.private"));

        Component capacity = Component.translatable("vivet.find_match.capacity");
        graphics.text(this.font, capacity,
                this.left + this.panelWidth - CARD_PAD - this.font.width(capacity),
                this.privateY + 7, Theme.TEXT_FAINT);

        int y = this.privateY + ROW_Y;
        int boxX = this.left + CARD_PAD;
        int boxWidth = codeWidth();

        Theme.roundedBox(graphics, boxX, y, boxWidth, ROW_H, 4, Theme.PANEL_SUNKEN);
        Theme.roundedBoxOutline(graphics, boxX, y, boxWidth, ROW_H, 4,
                this.codeBox != null && this.codeBox.isFocused()
                        ? Theme.BORDER_STRONG : Theme.BORDER);

        Theme.fill(graphics, separatorX(), y + 1, separatorX() + 1, y + ROW_H - 1, Theme.BORDER);
    }

    private void drawStatus(GuiGraphicsExtractor graphics) {
        int y = this.privateY + PRIVATE_H + 8;

        Component message;
        int color = this.statusColor;

        if (this.stage == Stage.SEARCHING && this.linePosition > 0) {
            message = Component.translatable("vivet.random_match.in_line", this.linePosition);
            color = this.band.color();
        } else if (this.stage == Stage.SEARCHING) {
            int seconds = (int) ((System.currentTimeMillis() - this.searchingSince) / 1000L);
            message = Component.translatable("vivet.random_match.searching",
                    this.band.displayName(), Time.format(seconds));
            color = this.band.color();
        } else if (!this.status.isEmpty()) {
            message = Component.literal(this.status);
        } else if (!this.connected) {
            message = Component.translatable("vivet.random_match.connecting");
            color = Theme.TEXT_FAINT;
        } else {
            return;
        }

        graphics.textWithWordWrap(this.font, message, this.left + CARD_PAD, y,
                this.panelWidth - CARD_PAD * 2, color);
    }

    private void drawOpenListFrame(GuiGraphicsExtractor graphics) {
        int x = this.left + CARD_PAD - 1;
        int y = this.top + FIELD_Y + FIELD_H + 1;
        int width = this.panelWidth - CARD_PAD * 2 + 2;
        int height = Difficulty.values().length * OPTION_H + 2;

        Theme.fill(graphics, x + 2, y + height, x + width - 2, y + height + 3, 0x66000000);
        Theme.fill(graphics, x, y, x + width, y + height, Theme.withAlpha(Theme.PANEL, 0xFF));
        Theme.roundedOutline(graphics, x, y, width, height, Theme.BORDER_STRONG);
    }

    private static boolean in(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.overBack && !this.expanded) {
            onClose();
            return true;
        }

        if (this.overCode && !this.expanded && this.codeBox != null) {
            setFocused(this.codeBox);
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean enter = event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER;

        if (enter && this.codeBox != null && this.codeBox.isFocused()) {
            joinPrivate();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        leaveQueue();

        ConfigManager.save();
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public void removed() {
        super.removed();

        if (this.stage != Stage.JOINING && this.matchmaker != null) {
            this.matchmaker.close();
            this.matchmaker = null;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public boolean searching() {
        return this.stage == Stage.SEARCHING;
    }
}
