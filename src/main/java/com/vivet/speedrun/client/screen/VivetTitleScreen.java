package com.vivet.speedrun.client.screen;

import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.client.RejoinStore;
import com.vivet.speedrun.client.ui.AccountBanner;
import com.vivet.speedrun.client.ui.AccountTile;
import com.vivet.speedrun.client.ui.CommsButton;
import com.vivet.speedrun.client.ui.FlatButton;
import com.vivet.speedrun.client.ui.VanillaIcon;
import com.vivet.speedrun.game.ItemTiers;
import com.vivet.speedrun.game.Time;
import com.vivet.speedrun.client.ui.Plural;
import com.vivet.speedrun.client.ui.QuestPlate;
import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.client.ui.VoidBackground;
import com.vivet.speedrun.client.ui.Wordmark;
import com.vivet.speedrun.p2p.CollectionBook;
import com.vivet.speedrun.p2p.Online;
import com.vivet.speedrun.p2p.Social;
import com.vivet.speedrun.p2p.Wardrobe;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class VivetTitleScreen extends VivetScreen {
    private static boolean showingVanilla;

    private static final int COLUMN_INSET = 20;

    private static final int ACCOUNT_INSET = 20;
    private static final int ACCOUNT_TOP = 14;

    private static final int ACCOUNT_GAP = 8;

    private static final int PER_COLUMN = 5;

    private static final Identifier LOGO =
            Identifier.fromNamespaceAndPath(VivetSpeedrun.MOD_ID, "textures/gui/logo_title.png");

    private static final Identifier LOGO_SHINE =
            Identifier.fromNamespaceAndPath(VivetSpeedrun.MOD_ID, "textures/gui/logo_title_shine.png");

    private static final int LOGO_W = 214;
    private static final int LOGO_H = 85;

    private static final int LOGO_AIR = 28;

    private static final int FOOTER_UP = 40;
    private static final int VERSION_UP = 18;

    private int commsX;
    private int commsY;
    private int commsW;

    private CommsButton trophy;

    private QuestPlate quests;

    private boolean questsOpen;

    private boolean questsRoom;

    private boolean slideQuests;

    private static final int COMMS_CELLS = 4;

    private CommsButton bounties;

    private int logoX;
    private int logoY;

    private RejoinStore.Saved rejoinable;

    private boolean hostedBack;

    private AccountTile rejoinTile;

    public VivetTitleScreen() {
        super(Component.literal("Vivet Speedrun"));
    }

    public static boolean wanted() {
        return !showingVanilla;
    }

    public static void showVanilla(Minecraft minecraft) {
        showingVanilla = true;
        minecraft.gui.setScreen(new TitleScreen());
    }

    public static void showVivet(Minecraft minecraft) {
        minecraft.gui.setScreen(forShowing());
    }

    public static VivetTitleScreen forShowing() {
        showingVanilla = false;
        return new VivetTitleScreen();
    }

    @Override
    protected void init() {
        super.init();

        this.rejoinable = RejoinStore.offered(this.minecraft);

        this.hostedBack = this.rejoinable == null
                && com.vivet.speedrun.client.HostedRejoin.offered() != null;

        Online.refresh();

        CollectionBook.refresh();

        Wardrobe.refresh();

        Social.refresh();

        com.vivet.speedrun.p2p.Achievements.refresh();

        com.vivet.speedrun.p2p.Quests.refresh();

        int blockH = PER_COLUMN * AccountTile.HEIGHT + (PER_COLUMN - 1) * ACCOUNT_GAP;

        int longest = PER_COLUMN + (this.rejoinable != null || this.hostedBack ? 1 : 0);
        int longestH = longest * AccountTile.HEIGHT + (longest - 1) * ACCOUNT_GAP;
        int cluster = AccountBanner.widthFor(this.font) + ACCOUNT_GAP + CommsButton.WIDTH * COMMS_CELLS;
        int room = this.width - ACCOUNT_INSET - cluster - 16 - COLUMN_INSET;
        int belowQuests = ACCOUNT_TOP + QuestPlate.HEIGHT + 12;
        boolean tallEnough = belowQuests + longestH <= this.height - FOOTER_UP - 6;

        this.questsRoom = tallEnough && room >= QuestPlate.OPEN_WIDTH;
        this.questsOpen = !com.vivet.speedrun.config.ConfigManager.get().questsCollapsed
                && this.questsRoom;
        boolean questsFit = this.questsOpen;

        int foldBottom = ACCOUNT_TOP + (QuestPlate.HEIGHT - QuestPlate.FOLD) / 2 + QuestPlate.FOLD + 12;
        int top = Math.max(questsFit ? Math.max(belowQuests, ACCOUNT_TOP + AccountBanner.HEIGHT + 12)
                : Math.max(foldBottom, ACCOUNT_TOP + AccountBanner.HEIGHT + 12),
                (this.height - blockH) / 2);

        int leftX = COLUMN_INSET;
        int rightX = this.width - COLUMN_INSET - AccountTile.WIDTH;

        buildColumn(DOING, leftX, buildComeback(leftX, top), AccountTile.Facing.LEFT);
        int rightEnd = buildColumn(OWNING, rightX, top, AccountTile.Facing.RIGHT);

        placeLogo(leftX + AccountTile.WIDTH, rightX, top, blockH);
        buildDiscord(rightX + AccountTile.WIDTH, top, rightEnd);
        buildAccount();
        buildFooter();
    }

    private static final int DISCORD_W = 176;

    private static final int BLURPLE = 0xFF5865F2;

    private AccountTile discordTile;
    private boolean discordLinkedShown;

    private void buildDiscord(int columnRight, int columnTop, int columnEnd) {
        this.discordTile = null;
        int x = columnRight - DISCORD_W;
        int y = ACCOUNT_TOP + AccountBanner.HEIGHT + ACCOUNT_GAP;
        if (y + AccountTile.HEIGHT + ACCOUNT_GAP > columnTop) y = columnEnd;
        if (y + AccountTile.HEIGHT > this.height - FOOTER_UP - 6) return;

        this.discordLinkedShown = com.vivet.speedrun.p2p.DiscordLink.isLinked();
        this.discordTile = addRenderableWidget(new AccountTile(x, y, DISCORD_W, AccountTile.Facing.LEFT,
                discordTitle(this.discordLinkedShown), VivetTitleScreen::discordNote, VanillaIcon.Kind.DISCORD,
                BLURPLE, this::pressDiscord)).noteTint(VivetTitleScreen::discordNoteTint)
                .noteDot(VivetTitleScreen::discordDot).noRule().fit();
    }

    private static int discordNoteTint() {
        if (com.vivet.speedrun.p2p.DiscordLink.isLinked()) return Theme.SUCCESS;
        double phase = (System.currentTimeMillis() % 1500L) / 1500.0 * Math.PI * 2.0;
        return Theme.mix(0xFFFFC23D, 0xFFFFF3B0, (float) (0.5 + 0.5 * Math.sin(phase)));
    }

    private static Component discordTitle(boolean linked) {
        return Component.translatable(linked ? "vivet.button.title_discord_open" : "vivet.button.title_discord");
    }

    private static int discordDot() {
        return com.vivet.speedrun.p2p.DiscordLink.isLinked() ? Theme.SUCCESS : 0;
    }

    private static Component discordNote() {
        if (!com.vivet.speedrun.p2p.DiscordLink.isLinked()) {
            return Component.translatable("vivet.title.discord_coins");
        }
        int online = Online.discord();
        return online < 0 ? null : Component.translatable("vivet.title.discord_online",
                java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(online));
    }

    private void pressDiscord() {
        if (com.vivet.speedrun.p2p.DiscordLink.isLinked()) {
            com.mojang.blaze3d.Blaze3D.openUri(java.net.URI.create(com.vivet.speedrun.p2p.DiscordLink.INVITE));
        } else {
            this.minecraft.gui.setScreen(new DiscordLinkScreen(this));
        }
    }

    private void placeLogo(int leftEdge, int rightEdge, int columnTop, int columnHeight) {
        boolean between = rightEdge - leftEdge >= LOGO_W + LOGO_AIR * 2;

        this.logoX = (this.width - LOGO_W) / 2;
        this.logoY = between
                ? columnTop + (columnHeight - LOGO_H) / 2
                : Math.max(6, columnTop - LOGO_H - 12);
    }

    private int buildColumn(Entry[] entries, int x, int top, AccountTile.Facing facing) {
        int y = top;
        for (Entry entry : entries) {
            addRenderableWidget(new AccountTile(x, y, AccountTile.WIDTH, facing,
                    Component.translatable("vivet.button.title_" + entry.key()),
                    entry.note(), entry.icon(), Theme.WARNING, entry.action(this)));
            y += AccountTile.HEIGHT + ACCOUNT_GAP;
        }
        return y;
    }

    private int buildComeback(int x, int y) {
        this.rejoinTile = null;
        if (this.rejoinable == null && !this.hostedBack) return y;

        this.rejoinTile = addRenderableWidget(new AccountTile(x, y, AccountTile.WIDTH,
                AccountTile.Facing.LEFT, Component.translatable("vivet.button.title_rejoin"),
                this::rejoinNote, VanillaIcon.Kind.CLOCK, Theme.DANGER, this::resumeMatch));

        return y + AccountTile.HEIGHT + ACCOUNT_GAP;
    }

    private record Entry(String key, Supplier<Component> note, VanillaIcon.Kind icon,
                         java.util.function.Function<VivetTitleScreen, Runnable> open) {
        Runnable action(VivetTitleScreen screen) {
            return this.open == null ? null : this.open.apply(screen);
        }
    }

    private static final Entry[] DOING = {
        new Entry("ranked", null, VanillaIcon.Kind.STAR,
                screen -> () -> screen.open(new RankedScreen(screen))),
        new Entry("find_match", null, VanillaIcon.Kind.COMPASS,
                screen -> () -> screen.open(new FindMatchScreen(screen))),
        new Entry("leaderboards", null, VanillaIcon.Kind.LADDER,
                screen -> () -> screen.open(new LeaderboardScreen(screen))),

        new Entry("replays", null, VanillaIcon.Kind.ENDER_EYE,
                screen -> () -> screen.open(new ReplaysScreen(screen))),
        new Entry("settings", null, VanillaIcon.Kind.COMPARATOR,
                screen -> () -> screen.open(new SettingsScreen(screen))),
    };

    private static final Entry[] OWNING = {
        new Entry("shop", VivetTitleScreen::coinsLine,
                VanillaIcon.Kind.COIN,
                screen -> () -> screen.open(new ShopScreen(screen))),

        new Entry("locker",
                () -> Plural.of(Wardrobe.ownedCount(), "vivet.title.items"),
                VanillaIcon.Kind.ARMOUR_STAND,
                screen -> () -> screen.open(new LockerScreen(screen))),

        new Entry("collection",
                () -> Component.translatable("vivet.title.found",
                        CollectionBook.found(), ItemTiers.collection().size()),
                VanillaIcon.Kind.KNOWLEDGE,
                screen -> () -> screen.open(new CollectionScreen(screen))),

        new Entry("social", VivetTitleScreen::socialLine,
                VanillaIcon.Kind.HEAD,
                screen -> () -> screen.open(new SocialScreen(screen))),

        new Entry("logs",
                () -> Component.translatable("vivet.title.logged",
                        com.vivet.speedrun.game.MatchLogs.count()),
                VanillaIcon.Kind.MAP,
                screen -> () -> screen.open(new LogsScreen(screen))),
    };

    private static Component socialLine() {
        int invites = com.vivet.speedrun.p2p.Hub.invites().size();
        if (invites > 0) return Component.translatable("vivet.title.invites", invites);

        if (Social.waitingCount() > 0) {
            return Component.translatable("vivet.title.requests", Social.waitingCount());
        }

        int online = com.vivet.speedrun.p2p.Hub.onlineCount();
        if (online > 0) return Component.translatable("vivet.title.online_friends", online);

        return Plural.of(Social.friendCount(), "vivet.title.friends");
    }

    private static Component coinsLine() {
        Integer coins = com.vivet.speedrun.p2p.MojangAuth.coins();
        if (coins == null) return Component.empty();
        return Component.translatable("vivet.title.coins",
                java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(coins));
    }

    private void open(Screen screen) {
        this.minecraft.gui.setScreen(screen);
    }

    private void buildAccount() {
        int right = this.width - ACCOUNT_INSET;

        int bannerW = AccountBanner.widthFor(this.font);
        int bannerX = right - bannerW;

        addRenderableWidget(new AccountBanner(bannerX, ACCOUNT_TOP, bannerW,
                () -> this.minecraft.gui.setScreen(new ProfileScreen(this))));

        this.commsW = CommsButton.WIDTH * COMMS_CELLS;
        this.commsX = bannerX - ACCOUNT_GAP - this.commsW;
        this.commsY = ACCOUNT_TOP;

        this.trophy = addRenderableWidget(new CommsButton(this.commsX, this.commsY,
                AccountBanner.HEIGHT, com.vivet.speedrun.client.ui.AchievementText.TROPHY,
                com.vivet.speedrun.client.ui.AchievementText.TROPHY_SIZE,
                Component.translatable("vivet.title.achievements"),
                com.vivet.speedrun.p2p.Achievements::unclaimed,
                () -> this.minecraft.gui.setScreen(new AchievementsScreen(this))));

        this.bounties = addRenderableWidget(new CommsButton(this.commsX + CommsButton.WIDTH, this.commsY,
                AccountBanner.HEIGHT, Component.translatable("vivet.title.bounties.icon"),
                BountiesScreen.CASH, Component.translatable("vivet.title.bounties"),
                () -> com.vivet.speedrun.config.ConfigManager.get().bountiesSeen ? 0 : 1,
                () -> {
                    var config = com.vivet.speedrun.config.ConfigManager.get();
                    if (!config.bountiesSeen) {
                        config.bountiesSeen = true;
                        com.vivet.speedrun.config.ConfigManager.save();
                    }
                    this.minecraft.gui.setScreen(new BountiesScreen(this));
                }));
        int inboxX = this.commsX + CommsButton.WIDTH * 2;

        addRenderableWidget(new CommsButton(inboxX, this.commsY, AccountBanner.HEIGHT,
                VanillaIcon.Kind.MAIL, Component.translatable("vivet.title.inbox"),
                () -> com.vivet.speedrun.p2p.Inbox.unread()
                        + com.vivet.speedrun.p2p.Hub.invites().size(),
                () -> this.minecraft.gui.setScreen(new InboxScreen(this))))

                .pulsing(() -> com.vivet.speedrun.p2p.Inbox.welcome() != null);

        addRenderableWidget(new CommsButton(inboxX + CommsButton.WIDTH, this.commsY,
                AccountBanner.HEIGHT, VanillaIcon.Kind.CHAT,
                Component.translatable("vivet.title.chat"),
                com.vivet.speedrun.p2p.Chat::unread,
                () -> this.minecraft.gui.setScreen(new ChatScreen(this))));

        this.quests = addRenderableWidget(new QuestPlate(COLUMN_INSET, ACCOUNT_TOP, this.questsOpen,
                this.slideQuests && this.questsOpen, this::foldQuests));
        if (!this.questsRoom) this.quests.noRoom();
        this.slideQuests = false;
    }

    private void foldQuests() {
        var config = com.vivet.speedrun.config.ConfigManager.get();
        config.questsCollapsed = !config.questsCollapsed;
        com.vivet.speedrun.config.ConfigManager.save();

        if (config.questsCollapsed || this.questsOpen) return;
        this.slideQuests = true;
        this.minecraft.execute(() -> {
            clearWidgets();
            init();
        });
    }

    private void buildFooter() {
        int height = 18;
        int bottom = this.height - FOOTER_UP;
        int right = this.width - 20;

        right -= addFooter(right, bottom, height, Component.translatable("menu.quit"),
                this.minecraft::stop);
        right -= addFooter(right, bottom, height,
                Component.translatable("vivet.button.vanilla_menu"),
                () -> showVanilla(this.minecraft));
        addFooter(right, bottom, height, Component.translatable("menu.options"),
                () -> this.minecraft.gui.setScreen(
                        new OptionsScreen(this, this.minecraft.options)));
    }

    private int addFooter(int right, int y, int height, Component label, Runnable action) {
        int width = this.font.width(label) + 20;
        addRenderableWidget(new FlatButton(right - width, y, width, height, label, action,
                FlatButton.Style.GHOST));
        return width + 6;
    }

    private Component rejoinNote() {
        if (this.hostedBack) {
            return Component.translatable("vivet.title.rejoin_note",
                    Time.format(com.vivet.speedrun.client.HostedRejoin.secondsLeft()));
        }
        RejoinStore.Saved saved = this.rejoinable;
        if (saved == null) return Component.empty();

        return Component.translatable("vivet.title.rejoin_note",
                Time.format(RejoinStore.secondsLeft(saved)));
    }

    private void resumeMatch() {
        if (this.hostedBack) {
            this.minecraft.gui.setScreen(new HostedDroppedScreen());
            return;
        }
        if (this.rejoinable == null) return;
        rejoinLauncher.accept(this.minecraft);
    }

    private static Consumer<Minecraft> rejoinLauncher = minecraft -> { };

    public static void bindRejoin(Consumer<Minecraft> launcher) {
        rejoinLauncher = launcher;
    }

    @Override
    protected void drawScaled(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                              float partialTick) {
        drawBackground(graphics);
        drawLogo(graphics);
        CommsButton.frame(graphics, this.commsX, this.commsY, this.commsW,
                AccountBanner.HEIGHT, COMMS_CELLS);
        drawFooterLine(graphics);

        drawWidgets(graphics, mouseX, mouseY, partialTick);

        CommsButton named = this.trophy != null && this.trophy.isHovered() ? this.trophy : null;
        if (named != null) {
            java.util.List<com.vivet.speedrun.client.ui.Tip.Line> lines =
                    com.vivet.speedrun.client.ui.Tip.lines();
            lines.add(new com.vivet.speedrun.client.ui.Tip.Line(named.getMessage(), Theme.TEXT));
            com.vivet.speedrun.client.ui.Tip.draw(graphics, this.font, lines, mouseX, mouseY,
                    this.width, this.height);
        }
    }

    private void drawBackground(GuiGraphicsExtractor graphics) {
        VoidBackground.draw(graphics, this.width, this.height);
        VoidBackground.vignette(graphics, this.width, this.height);
    }

    private void drawLogo(GuiGraphicsExtractor graphics) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, LOGO, this.logoX, this.logoY,
                0.0F, 0.0F, LOGO_W, LOGO_H, LOGO_W, LOGO_H, 0xFFFFFFFF);

        Wordmark.sweep(graphics, LOGO_SHINE, this.logoX, this.logoY, LOGO_W, LOGO_H, 1.0F);
    }

    private void drawFooterLine(GuiGraphicsExtractor graphics) {
        int y = this.height - VERSION_UP;
        int x = 20;

        Component build = Component.literal("Vivet Speedrun " + VivetSpeedrun.shownVersion(VivetSpeedrun.VERSION)
                + "   Minecraft " + SharedConstants.getCurrentVersion().name());
        graphics.text(this.font, build, x, y, Theme.TEXT_FAINT, true);

        drawOnline(graphics, x + this.font.width(build) + 10, y);
    }

    private void drawOnline(GuiGraphicsExtractor graphics, int x, int y) {
        int online = Online.count();
        if (online < 0) return;

        int color = online > 0 ? Theme.SUCCESS : Theme.TEXT_FAINT;

        Theme.fill(graphics, x, y + 2, x + 3, y + 5, color);

        graphics.text(this.font, Component.translatable("vivet.title.online", online),
                x + 7, y, color, true);
    }

    @Override
    public void tick() {
        super.tick();

        Online.refresh();
        CollectionBook.refresh();
        com.vivet.speedrun.p2p.Quests.refresh();

        if (this.questsOpen && this.quests != null && this.quests.foldedAway()) {
            clearWidgets();
            init();
            return;
        }

        boolean linked = com.vivet.speedrun.p2p.DiscordLink.isLinked();
        if (this.discordTile != null && linked != this.discordLinkedShown) {
            this.discordLinkedShown = linked;
            this.discordTile.setMessage(discordTitle(linked));
        }

        boolean gone = this.hostedBack
                ? com.vivet.speedrun.client.HostedRejoin.offered() == null
                : RejoinStore.offered(this.minecraft) == null;
        if (this.rejoinTile != null && gone) {
            this.rejoinable = null;
            this.hostedBack = false;
            clearWidgets();
            init();
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
