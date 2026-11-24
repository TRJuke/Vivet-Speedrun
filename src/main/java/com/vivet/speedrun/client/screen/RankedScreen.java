package com.vivet.speedrun.client.screen;

import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.client.Cues;
import com.vivet.speedrun.client.LoadingTakeover;
import com.vivet.speedrun.client.ui.Dropdown;
import com.vivet.speedrun.client.ui.FlatButton;
import com.vivet.speedrun.client.ui.PickerRow;
import com.vivet.speedrun.client.ui.Pulse;
import com.vivet.speedrun.client.ui.RankBackdrop;
import com.vivet.speedrun.client.ui.RankBanner;
import com.vivet.speedrun.client.ui.Ranks;
import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.client.ui.UiButtons;
import com.vivet.speedrun.client.ui.VanillaIcon;
import com.vivet.speedrun.config.ConfigManager;
import com.vivet.speedrun.game.Difficulty;
import com.vivet.speedrun.game.Time;
import com.vivet.speedrun.p2p.HostedQueue;
import com.vivet.speedrun.p2p.Matchmaker;
import com.vivet.speedrun.p2p.MojangAuth;
import com.vivet.speedrun.p2p.RankedApi;
import com.vivet.speedrun.session.Session;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.Map;

public class RankedScreen extends VivetScreen {
    public enum Mode {
        VERSUS,
        SOLO,
    }

    private static final int MAX_WIDTH = 440;
    private static final int GAP = 8;

    private static final int CARD_H = 164;

    private static final int FOOT_H = 52;

    private static final int CARD_PAD = 10;
    private static final int ICON_CY = 54;

    private static final int ICON_TILE = 58;
    private static final int FIELD_Y = 86;
    private static final int FIELD_H = 20;
    private static final int FIELD_W = 130;
    private static final int SEED_Y = 112;
    private static final int CHECK = 12;
    private static final int PLAY_Y = 136;
    private static final int PLAY_H = 20;
    private static final int PLAY_W = 120;

    private static final int OPTION_H = 20;

    private static final long SETUP_TIMEOUT_MS = 45_000L;

    private enum Stage {
        BROWSING,
        SEARCHING,
        JOINING,
    }

    private enum Open {
        NONE,
        VERSUS_BAND,
        SOLO_BAND,
    }

    private final Screen parent;

    private static String carriedStatus = "";
    private static int carriedStatusColor = Theme.TEXT_MUTED;
    private static Mode carriedMode = Mode.VERSUS;

    private static Mode queueAgainPending;

    private Mode autoStart;

    private volatile MojangAuth.Profile profile;
    private volatile String problem;
    private volatile boolean signingIn = true;

    private boolean signInTried;

    private RankBackdrop.Choice background;
    private String backgroundFor;

    private Matchmaker matchmaker;

    private long redialAt;
    private long redialWaitMs;

    private String dropReason;
    private volatile Stage stage = Stage.BROWSING;
    private volatile Map<String, Integer> counts = new LinkedHashMap<>();
    private volatile boolean connected;
    private volatile long searchingSince;
    private volatile long joiningSince;
    private volatile String versusStatus = "";
    private volatile int versusStatusColor = Theme.TEXT_MUTED;

    private volatile int linePosition;

    private Difficulty band;

    private volatile boolean starting;
    private volatile String soloStatus = "";
    private volatile int soloStatusColor = Theme.TEXT_MUTED;

    private Difficulty soloBand;
    private boolean setSeed;

    private Open open = Open.NONE;
    private boolean overBack;
    private boolean overCheck;

    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int cardW;
    private int cardsY;

    public RankedScreen(Screen parent) {
        super(Component.translatable("vivet.screen.ranked"));
        this.parent = parent;

        var config = ConfigManager.get();
        this.band = config.rankedBand;
        this.soloBand = config.rankedSoloBand;
        this.setSeed = config.rankedSoloSetSeed;

        MojangAuth.Identity known = MojangAuth.identity();
        this.profile = known == null ? null : known.profile;
        this.signingIn = known == null;

        if (!carriedStatus.isEmpty()) {
            if (carriedMode == Mode.SOLO) {
                this.soloStatus = carriedStatus;
                this.soloStatusColor = carriedStatusColor;
            } else {
                this.versusStatus = carriedStatus;
                this.versusStatusColor = carriedStatusColor;
            }
            carriedStatus = "";
        }

        this.autoStart = queueAgainPending;
        queueAgainPending = null;
    }

    public static void carryStatus(Mode mode, String message, int color) {
        carriedStatus = message == null ? "" : message;
        carriedStatusColor = color;
        carriedMode = mode;
    }

    public static void queueAgain(Mode mode) {
        queueAgainPending = mode;
    }

    public static Screen returnDestination() {
        return new RankedScreen(new VivetTitleScreen());
    }

    @Override
    protected void init() {
        super.init();

        this.panelWidth = Math.min(MAX_WIDTH, this.width - 16 - UiButtons.SIDE - 10);
        this.panelHeight = RankBanner.HEIGHT + GAP + CARD_H + FOOT_H;
        this.cardW = (this.panelWidth - GAP) / 2;

        this.left = (this.width - this.panelWidth) / 2;
        this.top = Math.max(0, (this.height - this.panelHeight) / 2);
        this.cardsY = this.top + RankBanner.HEIGHT + GAP;

        buildControls();

        if (!this.signInTried) signIn();

        if (this.autoStart == Mode.SOLO) {
            this.autoStart = null;
            startSoloNow();
        }
    }

    private int cardX(Mode mode) {
        return mode == Mode.VERSUS ? this.left : this.left + this.cardW + GAP;
    }

    private int backX() {
        return this.left + this.panelWidth + 10;
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private void signIn() {
        this.signInTried = true;
        RankedApi.loadConfig(config -> { }, reason -> { });

        if (MojangAuth.isSignedIn()) {
            this.signingIn = false;
            RankedApi.loadProfile(
                    loaded -> this.minecraft.execute(() -> this.profile = loaded),
                    reason -> this.minecraft.execute(() -> this.problem = reason));
            openLobby();
            return;
        }

        this.signingIn = true;
        MojangAuth.signIn(
                identity -> this.minecraft.execute(() -> {
                    this.signingIn = false;
                    this.problem = null;
                    this.profile = identity.profile;
                    openLobby();
                }),
                reason -> this.minecraft.execute(() -> {
                    this.signingIn = false;
                    this.problem = reason;
                    this.profile = null;

                    rebuild();
                }));
    }

    private void openLobby() {
        if (this.matchmaker != null) return;

        this.matchmaker = new Matchmaker(new Lobby());
        this.matchmaker.connect();
        rebuild();
    }

    private boolean banned() {
        MojangAuth.Profile current = this.profile;

        return current != null && current.banned
                && (current.bannedUntil <= 0 || current.bannedUntil > System.currentTimeMillis());
    }

    private void buildControls() {
        buildVersus();
        buildSolo();

        if (this.problem != null && !MojangAuth.isSignedIn()) {
            addRenderableWidget(new FlatButton(
                    this.left + this.panelWidth - CARD_PAD - 92, this.top + 76, 92, 20,
                    Component.translatable("vivet.ranked.retry_sign_in"),
                    () -> {
                        MojangAuth.retryNow();
                        this.signInTried = false;
                        this.problem = null;
                        rebuild();
                    },
                    FlatButton.Style.SECONDARY));
        }

        if (this.open != Open.NONE) buildOptions();
    }

    private void buildVersus() {
        int x = cardX(Mode.VERSUS);

        Dropdown picker = addRenderableWidget(new Dropdown(
                x + this.cardW - CARD_PAD - FIELD_W, this.cardsY + FIELD_Y, FIELD_W, FIELD_H,
                Component.empty(),
                () -> Component.literal(this.band.displayName()),
                () -> this.band.color(),
                () -> this.open == Open.VERSUS_BAND,
                () -> toggle(Open.VERSUS_BAND)));

        picker.active = this.stage == Stage.BROWSING;

        FlatButton play = addRenderableWidget(new FlatButton(
                x + (this.cardW - PLAY_W) / 2, this.cardsY + PLAY_Y, PLAY_W, PLAY_H,
                Component.translatable(switch (this.stage) {
                    case BROWSING -> "vivet.button.play";
                    case SEARCHING -> "vivet.button.cancel_search";
                    case JOINING -> "vivet.button.cancel_setup";
                }),
                this::toggleSearch,
                this.stage == Stage.BROWSING
                        ? FlatButton.Style.PRIMARY : FlatButton.Style.DANGER));

        play.active = (this.stage == Stage.JOINING || this.connected)
                && this.open == Open.NONE && !banned();
    }

    private void buildSolo() {
        int x = cardX(Mode.SOLO);

        Dropdown picker = addRenderableWidget(new Dropdown(
                x + this.cardW - CARD_PAD - FIELD_W, this.cardsY + FIELD_Y, FIELD_W, FIELD_H,
                Component.empty(),
                () -> Component.literal(this.soloBand.displayName()),
                () -> this.soloBand.color(),
                () -> this.open == Open.SOLO_BAND,
                () -> toggle(Open.SOLO_BAND)));

        picker.active = !this.starting && !HostedQueue.waiting();

        addRenderableWidget(new FlatButton(
                x + CARD_PAD, this.cardsY + SEED_Y - 2, this.cardW - CARD_PAD * 2, CHECK + 4,
                Component.empty(), this::toggleSeed, FlatButton.Style.INVISIBLE));

        boolean waiting = HostedQueue.waiting();
        this.shownWaiting = waiting;
        FlatButton play = addRenderableWidget(new FlatButton(
                x + (this.cardW - PLAY_W) / 2, this.cardsY + PLAY_Y, PLAY_W, PLAY_H,
                Component.translatable(waiting ? "vivet.button.cancel_search" : "vivet.button.play"),
                waiting ? this::leaveLine : this::startSolo,
                waiting ? FlatButton.Style.DANGER : FlatButton.Style.PRIMARY));

        play.active = MojangAuth.isSignedIn() && !this.starting && this.open == Open.NONE
                && !banned();
    }

    private boolean shownWaiting;

    private void leaveLine() {
        HostedQueue.leave();
        Cues.queueLeft();
        this.soloStatus = "";
        rebuild();
    }

    @Override
    public void tick() {
        super.tick();

        redialIfDue();

        RankedApi.HostedTicket turn = HostedQueue.takeReady();
        if (turn != null) {
            Cues.queuePop();
            this.starting = true;
            this.soloStatus = "Starting...";
            this.soloStatusColor = Theme.ACCENT;
            hostedOpened(turn);
            return;
        }

        String failure = HostedQueue.takeFailure();
        if (failure != null) {
            Cues.queueLeft();
            soloFailed(failure);
            return;
        }

        if (HostedQueue.waiting()) {
            String line = "#" + HostedQueue.position() + " in queue";
            if (!line.equals(this.soloStatus)) {
                this.soloStatus = line;
                this.soloStatusColor = Theme.ACCENT;
            }
        }
        if (HostedQueue.waiting() != this.shownWaiting) rebuild();
    }

    private void buildOptions() {
        boolean versus = this.open == Open.VERSUS_BAND;

        int x = cardX(versus ? Mode.VERSUS : Mode.SOLO) + CARD_PAD;
        int listWidth = this.cardW - CARD_PAD * 2;
        int listY = this.cardsY + FIELD_Y + FIELD_H + 2;

        sealUnderList();

        for (int i = 0; i < Difficulty.values().length; i++) {
            Difficulty option = Difficulty.values()[i];

            addRenderableWidget(new PickerRow(x, listY + i * OPTION_H, listWidth, OPTION_H,
                    Component.literal(option.displayName()),

                    () -> versus ? queueNote(option) : null,
                    () -> versus ? queueNoteColor(option) : Theme.TEXT_FAINT,
                    () -> versus && this.connected && waitingOn(option) > 0,
                    option.color(),
                    option == (versus ? this.band : this.soloBand),
                    () -> choose(option)));
        }

        addRenderableWidget(new FlatButton(0, 0, this.width, this.height,
                Component.empty(), () -> toggle(this.open), FlatButton.Style.INVISIBLE));
    }

    private void toggle(Open which) {
        if (which == Open.VERSUS_BAND && this.stage != Stage.BROWSING) return;
        if (which == Open.SOLO_BAND && this.starting) return;

        this.open = this.open == which ? Open.NONE : which;
        rebuild();
    }

    private void choose(Difficulty option) {
        if (this.open == Open.VERSUS_BAND) {
            this.band = option;
            ConfigManager.get().rankedBand = option;
        } else {
            this.soloBand = option;
            ConfigManager.get().rankedSoloBand = option;
        }

        this.open = Open.NONE;
        ConfigManager.save();
        Cues.tick();
        rebuild();
    }

    private void toggleSeed() {
        if (this.starting || this.open != Open.NONE || HostedQueue.waiting()) return;

        this.setSeed = !this.setSeed;
        ConfigManager.get().rankedSoloSetSeed = this.setSeed;
        ConfigManager.save();
        Cues.tick();
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
            this.versusStatus = "";
            Cues.queueLeft();
            rebuild();
            return;
        }
        startSearch();
    }

    private void startSearch() {
        if (this.matchmaker == null) return;

        this.matchmaker.queue(this.band, true);
        this.stage = Stage.SEARCHING;
        this.searchingSince = System.currentTimeMillis();
        this.linePosition = 0;
        this.versusStatus = "";
        Cues.queueJoined();
        rebuild();
    }

    private final class Lobby implements Matchmaker.Listener {
        @Override
        public void onCounts(Map<String, Integer> casual, Map<String, Integer> ranked) {
            RankedScreen.this.minecraft.execute(() -> {
                boolean first = !RankedScreen.this.connected;

                RankedScreen.this.counts = ranked;
                RankedScreen.this.connected = true;
                RankedScreen.this.redialWaitMs = 0L;
                if (first && RankedScreen.this.dropReason != null
                        && RankedScreen.this.dropReason.equals(RankedScreen.this.versusStatus)) {
                    RankedScreen.this.versusStatus = "";
                }
                RankedScreen.this.dropReason = null;

                if (first && RankedScreen.this.autoStart == Mode.VERSUS) {
                    RankedScreen.this.autoStart = null;
                    if (!banned()) {
                        startSearch();
                        return;
                    }
                }

                if (first) rebuild();
            });
        }

        @Override
        public void onHostedMatched(Difficulty matchedBand, RankedApi.HostedTicket ticket) {
            Cues.queuePop();
            RankedScreen.this.minecraft.execute(() -> beginHostedMatch(matchedBand, ticket));
        }

        @Override
        public void onLine(int position) {
            RankedScreen.this.minecraft.execute(() -> {
                if (RankedScreen.this.stage != Stage.SEARCHING) return;

                int was = RankedScreen.this.linePosition;
                RankedScreen.this.linePosition = position;

                if (was == 0 && position > 0) {
                    Cues.queueWaiting();
                } else if (was > 0 && position == 0) {
                    Cues.queueLeft();
                    RankedScreen.this.searchingSince = System.currentTimeMillis();
                }
            });
        }

        @Override
        public void onBanned(long until, String reason) {
            RankedScreen.this.minecraft.execute(() -> {
                RankedScreen.this.stage = Stage.BROWSING;

                RankedScreen.this.versusStatus = reason;
                RankedScreen.this.versusStatusColor = Theme.DANGER;

                MojangAuth.Profile current = RankedScreen.this.profile;
                if (current != null) {
                    current.banned = true;
                    current.bannedUntil = until;
                }
                rebuild();
            });
        }

        @Override
        public void onRefused(String reason) {
            RankedScreen.this.minecraft.execute(() -> {
                RankedScreen.this.stage = Stage.BROWSING;
                RankedScreen.this.versusStatus = reason;
                RankedScreen.this.versusStatusColor = Theme.DANGER;
                rebuild();
            });
        }

        @Override
        public void onFailed(String reason) {
            RankedScreen.this.minecraft.execute(() -> {
                if (RankedScreen.this.stage == Stage.JOINING) return;

                RankedScreen.this.matchmaker = null;
                RankedScreen.this.connected = false;
                RankedScreen.this.stage = Stage.BROWSING;
                RankedScreen.this.versusStatus = reason;
                RankedScreen.this.versusStatusColor = Theme.DANGER;
                RankedScreen.this.dropReason = reason;
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

    private void beginHostedMatch(Difficulty matchedBand, RankedApi.HostedTicket ticket) {
        this.stage = Stage.JOINING;
        this.joiningSince = System.currentTimeMillis();
        this.linePosition = 0;
        this.open = Open.NONE;
        this.band = matchedBand;
        this.versusStatus = "Match found!";
        this.versusStatusColor = Theme.SUCCESS;
        rebuild();

        if (this.matchmaker != null) {
            this.matchmaker.close();
            this.matchmaker = null;
        }

        VivetSpeedrun.LOGGER.info("Ranked {} match on {}:{}",
                matchedBand.id(), ticket.host, ticket.port);
        com.vivet.speedrun.client.HostedConnect.joinMatch(this.minecraft, this, matchedBand, ticket,
                false);
    }

    private void matchFailed(String reason) {
        LoadingTakeover.abort();
        Session.stop();

        this.stage = Stage.BROWSING;
        this.linePosition = 0;
        this.versusStatus = reason;
        this.versusStatusColor = Theme.DANGER;
        this.connected = false;

        this.matchmaker = new Matchmaker(new Lobby());
        this.matchmaker.connect();
        rebuild();
    }

    private void startSoloNow() {
        if (!MojangAuth.isSignedIn()) {
            this.soloStatus = "Sign in to play Ranked.";
            this.soloStatusColor = Theme.DANGER;
            rebuild();
            return;
        }
        startSolo();
    }

    private void startSolo() {
        if (this.starting || !MojangAuth.isSignedIn()) return;

        this.starting = true;

        if (ConfigManager.get().tutorialPlayPulse) {
            ConfigManager.get().tutorialPlayPulse = false;
            ConfigManager.save();
        }
        this.soloStatus = "Starting...";
        this.soloStatusColor = Theme.ACCENT;
        Cues.confirm();
        rebuild();

        RankedApi.startHostedSolo(this.soloBand, this.setSeed,
                com.vivet.speedrun.config.ConfigManager.get().pitySystem,
                ticket -> this.minecraft.execute(() -> hostedOpened(ticket)),
                reason -> this.minecraft.execute(() -> soloFailed(reason)));
    }

    private void hostedOpened(RankedApi.HostedTicket ticket) {
        if (ticket.queued) {
            HostedQueue.joined(ticket.position, this.soloBand, this.setSeed);
            Cues.queueJoined();
            this.starting = false;
            this.soloStatus = "#" + Math.max(1, ticket.position) + " in queue";
            this.soloStatusColor = Theme.ACCENT;
            rebuild();
            return;
        }

        if (ticket.racing) {
            this.soloStatus = "Rejoining...";
            this.soloStatusColor = Theme.ACCENT;
            com.vivet.speedrun.client.HostedRejoin.rejoin(this.minecraft, this, this::soloFailed);
            return;
        }

        if (ticket.error != null || ticket.host == null || ticket.port <= 0) {
            soloFailed(ticket.error == null ? "Couldn't start. Try again."
                    : ticket.error);
            return;
        }

        if (this.minecraft.getUser() != null) {
            Session.setLocalName(this.minecraft.getUser().getName());
        }

        VivetSpeedrun.LOGGER.info("Ranked solo {} on {}:{}",
                this.soloBand.id(), ticket.host, ticket.port);

        Session.beginHostedSolo(this.setSeed);
        this.starting = false;
        com.vivet.speedrun.client.HostedRejoin.joined(ticket, this.soloBand, this.setSeed);
        com.vivet.speedrun.client.HostedConnect.join(this.minecraft, this, ticket);

        com.vivet.speedrun.client.HostedLoading.begin(this.minecraft, this);
    }

    private void soloFailed(String reason) {
        Session.stop();

        this.starting = false;
        this.soloStatus = reason;
        this.soloStatusColor = Theme.DANGER;
        rebuild();
    }

    private String monthSeed() {
        RankedApi.Config config = RankedApi.cachedConfig();
        if (config == null || config.setSeed == null || config.setSeed.isBlank()) return null;

        return config.setSeed.trim();
    }

    private String monthItem() {
        RankedApi.Config config = RankedApi.cachedConfig();
        if (config == null || config.setSeedItems == null) return null;

        String id = config.setSeedItems.get(this.soloBand.id());
        return id == null || id.isBlank() ? null : id;
    }

    private static String prettyId(String id) {
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;

        StringBuilder out = new StringBuilder();
        for (String word : path.split("[_/]")) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    @Override
    protected void drawScaled(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                              float partialTick) {
        Theme.fill(graphics, 0, 0, this.width, this.height, Theme.SCRIM);

        MojangAuth.Profile shown = this.profile;
        refreshBackground(shown);

        Component waiting = Component.literal(this.problem != null ? this.problem
                : this.signingIn ? "Signing in..." : "Not signed in");

        RankBanner.draw(graphics, this.font, this.left, this.top, this.panelWidth,
                Component.translatable("vivet.ranked.banner"), shown, this.background,
                waiting, this.problem != null ? Theme.DANGER : Theme.TEXT_MUTED);

        if (this.stage == Stage.JOINING
                && System.currentTimeMillis() - this.joiningSince > SETUP_TIMEOUT_MS) {
            Session.quit();
            matchFailed("Your opponent didn't show up");
        }

        drawVersusCard(graphics);
        drawSoloCard(graphics, mouseX, mouseY);

        this.overBack = in(mouseX, mouseY, backX(), this.top, UiButtons.SIDE, UiButtons.SIDE);
        UiButtons.button(graphics, UiButtons.Kind.BACK, backX(), this.top, this.overBack,
                true, false);

        if (this.open != Open.NONE) drawOpenListFrame(graphics);

        drawWidgets(graphics, mouseX, mouseY, partialTick);

        if (ConfigManager.get().tutorialPlayPulse && this.open == Open.NONE && !this.shownWaiting) {
            Pulse.around(graphics, cardX(Mode.SOLO) + (this.cardW - PLAY_W) / 2,
                    this.cardsY + PLAY_Y, PLAY_W, PLAY_H, 3);
        }
    }

    private void refreshBackground(MojangAuth.Profile shown) {
        String rank = shown == null || !shown.placed ? null : Ranks.displayRank(shown);

        if (rank == null) {
            this.background = null;
            this.backgroundFor = null;
            return;
        }
        if (rank.equals(this.backgroundFor)) return;

        this.backgroundFor = rank;
        this.background = RankBackdrop.pick(rank);
    }

    private void card(GuiGraphicsExtractor graphics, int x, Component title, int accent) {
        Theme.roundedBox(graphics, x, this.cardsY, this.cardW, CARD_H, 4, Theme.PANEL);
        Theme.roundedBoxOutline(graphics, x, this.cardsY, this.cardW, CARD_H, 4, Theme.BORDER);

        graphics.text(this.font, title.copy().withStyle(ChatFormatting.BOLD),
                x + CARD_PAD, this.cardsY + 7, Theme.TEXT);
        Theme.fill(graphics, x + CARD_PAD, this.cardsY + 19,
                x + this.cardW - CARD_PAD, this.cardsY + 20, Theme.BORDER);

        Theme.divider(graphics, x + CARD_PAD, this.cardsY + 128, this.cardW - CARD_PAD * 2);

        graphics.text(this.font, Component.translatable("vivet.random_match.difficulty"),
                x + CARD_PAD, this.cardsY + FIELD_Y + (FIELD_H - 8) / 2, Theme.TEXT_MUTED);

        int tile = ICON_TILE;
        int tileX = x + (this.cardW - tile) / 2;
        int tileY = this.cardsY + ICON_CY - tile / 2;

        Theme.roundedBox(graphics, tileX, tileY, tile, tile, 4, Theme.withAlpha(accent, 0x1E));
        Theme.roundedBoxOutline(graphics, tileX, tileY, tile, tile, 4,
                Theme.withAlpha(accent, 0xAA));
    }

    private void drawVersusCard(GuiGraphicsExtractor graphics) {
        int x = cardX(Mode.VERSUS);
        card(graphics, x, Component.translatable("vivet.ranked.versus"), Theme.ACCENT);

        VanillaIcon.draw(graphics, VanillaIcon.Kind.SWORD, x + this.cardW / 2,
                this.cardsY + ICON_CY, 48);

        int y = this.cardsY + CARD_H + 6;
        int total = totalWaiting();
        boolean anybody = this.connected && total > 0;

        Component label = !this.connected
                ? Component.translatable("vivet.random_match.offline")
                : Component.translatable("vivet.ranked.in_queue", total);

        Theme.liveDot(graphics, x + CARD_PAD, y + 2, anybody ? Theme.SUCCESS : Theme.TEXT_MUTED,
                anybody);
        graphics.text(this.font, label, x + CARD_PAD + 8, y,
                !this.connected ? Theme.TEXT_FAINT : anybody ? Theme.SUCCESS : Theme.TEXT_MUTED);

        Component message;
        int color = this.versusStatusColor;

        if (this.stage == Stage.SEARCHING && this.linePosition > 0) {
            message = Component.translatable("vivet.random_match.in_line", this.linePosition);
            color = this.band.color();
        } else if (this.stage == Stage.SEARCHING) {
            int seconds = (int) ((System.currentTimeMillis() - this.searchingSince) / 1000L);
            message = Component.translatable("vivet.random_match.searching",
                    this.band.displayName(), Time.format(seconds));
            color = this.band.color();
        } else if (!this.versusStatus.isEmpty()) {
            message = Component.literal(this.versusStatus);
        } else {
            return;
        }

        graphics.textWithWordWrap(this.font, message, x + CARD_PAD, y + 12,
                this.cardW - CARD_PAD * 2, color);
    }

    private void drawSoloCard(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = cardX(Mode.SOLO);
        card(graphics, x, Component.translatable("vivet.ranked.solo"), Theme.SUCCESS);

        VanillaIcon.draw(graphics, VanillaIcon.Kind.CLOCK, x + this.cardW / 2,
                this.cardsY + ICON_CY, 48);

        int checkX = x + this.cardW - CARD_PAD - CHECK;
        int checkY = this.cardsY + SEED_Y;

        this.overCheck = in(mouseX, mouseY, x + CARD_PAD, checkY - 2,
                this.cardW - CARD_PAD * 2, CHECK + 4);

        graphics.text(this.font, Component.translatable("vivet.ranked.monthly_seed"),
                x + CARD_PAD, checkY + 2, this.overCheck ? Theme.TEXT : Theme.TEXT_MUTED);
        drawCheck(graphics, checkX, checkY);

        int y = this.cardsY + CARD_H + 6;

        if (this.setSeed) {
            String seed = monthSeed();
            String item = monthItem();

            graphics.text(this.font, Component.translatable("vivet.ranked.seed_line",
                            seed == null ? "Monthly" : seed),
                    x + CARD_PAD, y, this.soloBand.color());
            graphics.text(this.font, Component.translatable("vivet.ranked.item_line",
                            item == null ? "Monthly" : prettyId(item)),
                    x + CARD_PAD, y + 11, this.soloBand.color());
            y += 23;
        }

        if (this.soloStatus.isBlank()) return;

        graphics.textWithWordWrap(this.font, Component.literal(this.soloStatus),
                x + CARD_PAD, y, this.cardW - CARD_PAD * 2, this.soloStatusColor);
    }

    private void drawCheck(GuiGraphicsExtractor graphics, int x, int y) {
        int edge = this.overCheck ? Theme.BORDER_STRONG : Theme.BORDER;

        Theme.roundedRect(graphics, x, y, CHECK, CHECK, Theme.PANEL_SUNKEN);
        Theme.roundedOutline(graphics, x, y, CHECK, CHECK, this.setSeed ? Theme.SUCCESS : edge);

        if (!this.setSeed) return;

        for (int i = 0; i < 3; i++) {
            Theme.fill(graphics, x + 3 + i, y + 5 + i, x + 4 + i, y + 7 + i, Theme.SUCCESS);
        }
        for (int i = 0; i < 4; i++) {
            Theme.fill(graphics, x + 5 + i, y + 7 - i, x + 6 + i, y + 9 - i, Theme.SUCCESS);
        }
    }

    private void drawOpenListFrame(GuiGraphicsExtractor graphics) {
        boolean versus = this.open == Open.VERSUS_BAND;
        int x = cardX(versus ? Mode.VERSUS : Mode.SOLO) + CARD_PAD - 1;
        int y = this.cardsY + FIELD_Y + FIELD_H + 1;
        int width = this.cardW - CARD_PAD * 2 + 2;
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
        if (this.overBack && this.open == Open.NONE) {
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClose() {
        if (this.matchmaker != null) {
            this.matchmaker.close();
            this.matchmaker = null;
        }

        ConfigManager.save();
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public void removed() {
        super.removed();

        if (HostedQueue.waiting()) {
            HostedQueue.leave();
            Cues.queueLeft();
        }

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
