package com.vivet.speedrun.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.client.hud.ItemRoll;
import com.vivet.speedrun.client.hud.ReplayHud;
import com.vivet.speedrun.client.hud.RunHud;
import com.vivet.speedrun.client.screen.FindMatchScreen;
import com.vivet.speedrun.client.screen.GraceScreen;
import com.vivet.speedrun.client.screen.EndgameScreen;
import com.vivet.speedrun.client.screen.RejoinScreen;
import com.vivet.speedrun.client.screen.SpeedrunScreen;
import com.vivet.speedrun.client.screen.UpdateScreen;
import com.vivet.speedrun.client.screen.VivetTitleScreen;
import com.vivet.speedrun.client.screen.VictoryScreen;
import com.vivet.speedrun.client.ui.NameLabel;
import com.vivet.speedrun.client.screen.ReadyScreen;
import com.vivet.speedrun.client.screen.VoteScreen;
import com.vivet.speedrun.client.screen.WaitingScreen;
import com.vivet.speedrun.client.ui.FlatButton;
import com.vivet.speedrun.config.ConfigManager;
import com.vivet.speedrun.config.ModConfig;
import com.vivet.speedrun.game.Difficulty;
import com.vivet.speedrun.game.MatchRules;
import com.vivet.speedrun.game.Practice;
import com.vivet.speedrun.game.RunManager;
import com.vivet.speedrun.game.RunMode;
import com.vivet.speedrun.game.RunSummary;
import com.vivet.speedrun.game.Target;
import com.vivet.speedrun.game.ItemIcons;
import com.vivet.speedrun.game.ItemTiers;
import com.vivet.speedrun.game.SeedDifficulty;
import com.vivet.speedrun.net.Payloads;
import com.vivet.speedrun.p2p.Hub;
import com.vivet.speedrun.p2p.MojangAuth;
import com.vivet.speedrun.p2p.Standings;
import com.vivet.speedrun.session.Session;
import com.vivet.speedrun.session.SessionMessage;
import com.vivet.speedrun.update.UpdateChecker;
import com.vivet.speedrun.update.UpdateInstaller;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.WorldOptionsScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.HttpUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

public class VivetSpeedrunClient implements ClientModInitializer {
    public static final KeyMapping OPEN_SCREEN = new KeyMapping(
            "key.vivet_speedrun.open_screen",
            InputConstants.KEY_Y,
            KeyMapping.Category.MISC);

    public static final KeyMapping REROLL = new KeyMapping(
            "key.vivet_speedrun.reroll",
            InputConstants.KEY_R,
            KeyMapping.Category.MISC);

    public static final KeyMapping GIVE_UP = new KeyMapping(
            "key.vivet_speedrun.give_up",
            InputConstants.UNKNOWN.getValue(),
            KeyMapping.Category.MISC);

    public static final KeyMapping REPLAY_CONTROLS = new KeyMapping(
            "key.vivet_speedrun.replay_controls",
            InputConstants.KEY_LALT,
            KeyMapping.Category.MISC);

    private SessionMessage pendingStart;

    private boolean keepPublished;

    private boolean equalsWasDown;

    private long republishedAt;

    private boolean worldReady;
    private boolean goReceived;

    private int lastReportedSecond = -1;

    private Payloads.RunFinished heldResults;

    private long heldResultsAt;

    private static final long VERDICT_WAIT_MS = 45_000L;

    private volatile MojangAuth.Profile ratingBefore;

    private String publicMatchOver;
    private boolean publicMatchWon;

    private String lastRunDetail = "";

    private static final long START_SEND_TIMEOUT_MS = 30_000L;

    private static final long REVEAL_WAIT_MS = 30_000L;

    private static final int IDLE_CLOCK_SECONDS = 5;

    private boolean launchSignInStarted;

    @Override
    public void onInitializeClient() {
        ConfigManager.load();

        RejoinStore.load();
        Session.bindForgetRejoin(RejoinStore::clear);

        HostedRejoin.load();

        registerReceivers();
        bindSession();

        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(VivetSpeedrun.MOD_ID, "run_hud"),
                (graphics, deltaTracker) -> {
                    com.vivet.speedrun.client.ui.UiScale.push(graphics);
                    try {
                        RunHud.render(graphics);
                    } finally {
                        com.vivet.speedrun.client.ui.UiScale.pop(graphics);
                    }
                });

        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(VivetSpeedrun.MOD_ID, "item_roll"),
                (graphics, deltaTracker) -> {
                    com.vivet.speedrun.client.ui.UiScale.push(graphics);
                    try {
                        ItemRoll.render(graphics);
                    } finally {
                        com.vivet.speedrun.client.ui.UiScale.pop(graphics);
                    }
                });

        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(VivetSpeedrun.MOD_ID, "replay_hud"),
                (graphics, deltaTracker) -> ReplayHud.render(graphics));

        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(VivetSpeedrun.MOD_ID, "notices"),
                (graphics, deltaTracker) -> {
                    com.vivet.speedrun.client.ui.UiScale.push(graphics);
                    try {
                        com.vivet.speedrun.client.hud.Notices.render(graphics,
                                com.vivet.speedrun.client.ui.UiScale.guiWidth(graphics));
                    } finally {
                        com.vivet.speedrun.client.ui.UiScale.pop(graphics);
                    }
                });

        com.vivet.speedrun.p2p.Hub.bindInvites(
                com.vivet.speedrun.client.hud.Notices::invited);

        com.vivet.speedrun.p2p.DiscordLink.bindLinked(
                com.vivet.speedrun.client.hud.Notices::discordLinked);

        com.vivet.speedrun.client.discord.RichPresence.start();

        com.vivet.speedrun.p2p.Hub.bindInviteGone(
                com.vivet.speedrun.client.hud.Notices::dismiss);

        com.vivet.speedrun.p2p.Hub.bindInviteRefused(
                com.vivet.speedrun.client.hud.Notices::inviteRefused);

        com.vivet.speedrun.p2p.Hub.bindOnline(
                com.vivet.speedrun.client.hud.Notices::cameOnline);

        com.vivet.speedrun.p2p.Inbox.bindArrivals(
                com.vivet.speedrun.client.hud.Notices::arrived);

        com.vivet.speedrun.p2p.Achievements.bindDone(done ->
                com.vivet.speedrun.client.hud.Notices.achieved(done.id,
                        done.node == null ? 0 : done.node, done.nodes == null ? 1 : done.nodes,
                        Boolean.TRUE.equals(done.hidden), done.rarity, done.sound));

        com.vivet.speedrun.p2p.Quests.bindDone(com.vivet.speedrun.client.hud.Notices::quest);

        com.vivet.speedrun.p2p.Chat.bindArrivals(
                com.vivet.speedrun.client.hud.Notices::messaged);

        KeyMappingHelper.registerKeyMapping(OPEN_SCREEN);
        KeyMappingHelper.registerKeyMapping(REROLL);
        KeyMappingHelper.registerKeyMapping(GIVE_UP);
        KeyMappingHelper.registerKeyMapping(REPLAY_CONTROLS);

        com.vivet.speedrun.replay.ReplayStore.sweepOld();

        com.vivet.speedrun.client.screen.PracticeScreen.bindStart(this::startSoloRun);
        VivetTitleScreen.bindRejoin(this::resumeSavedMatch);

        ClientSendMessageEvents.ALLOW_CHAT.register(this::onChatSent);

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        ClientTickEvents.START_CLIENT_TICK.register(minecraft -> {
            if (com.vivet.speedrun.client.replay.SpectateEyes.inside() && minecraft.gui.screen() == null) {
                while (minecraft.options.keyInventory.consumeClick()) {
                    com.vivet.speedrun.client.replay.ReplayScreens.open();
                }
                for (KeyMapping slot : minecraft.options.keyHotbarSlots) {
                    while (slot.consumeClick()) {
                    }
                }
                return;
            }
            if (!com.vivet.speedrun.client.replay.ReplayPlayback.isActive() || minecraft.gui.screen() != null) return;
            while (minecraft.options.keyInventory.consumeClick()) {
                com.vivet.speedrun.client.replay.ReplayScreens.open();
            }
            for (KeyMapping slot : minecraft.options.keyHotbarSlots) {
                while (slot.consumeClick()) {
                }
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            com.vivet.speedrun.client.replay.ReplayPlayback.forget();

            Spectating.clear();
            com.vivet.speedrun.client.replay.SpectateEyes.clear();

            WorldActions.disown();

            if (WorldActions.consumeExpectedSwap()) return;

            com.vivet.speedrun.game.MatchRules.clear();

            if (Session.isHostedMatch() && HostedRejoin.inRace()) return;

            if (Session.isActive()) Session.quit();

            Session.endHostedSolo();
        });

        ScreenEvents.AFTER_INIT.register(this::onScreenInit);

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING
                .register(stopping -> {
                    com.vivet.speedrun.p2p.Presence.goodbye();

                    com.vivet.speedrun.p2p.Hub.close();
                });

        UpdateChecker.checkOnce();

        com.vivet.speedrun.media.MediaCache.fetchOnce();

        UpdateInstaller.resumeStagedSwap();

        MatchRules.bindCommandRevoke(server -> {
            if (server instanceof IntegratedServer integrated) {
                integrated.setWorldAllowCommands(false);
                integrated.setGuestCommandAccess(false);
            } else {
                server.getWorldData().setAllowCommands(false);
            }
        });
    }

    private void onScreenInit(Minecraft minecraft, Screen screen, int width, int height) {
        if (screen instanceof PauseScreen) {
            relabelStopWatching(screen);
            sealWorldOptions(screen);
            return;
        }
        if (screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen) {
            relabelRaceServerExit(screen);
            return;
        }

        if (screen instanceof WorldOptionsScreen) {
            sealWorldOptionsScreen(screen);
            caughtOpening(screen);
            return;
        }
        if (!(screen instanceof TitleScreen)) return;

        Component label = Component.translatable("vivet.button.vivet_menu");

        Screens.getWidgets(screen).add(new FlatButton(
                4, height - 46, minecraft.font.width(label) + 20, 20,
                label,
                () -> VivetTitleScreen.showVivet(minecraft),
                FlatButton.Style.PRIMARY));
    }

    private void relabelRaceServerExit(Screen screen) {
        if (!HostedConnect.raced()) return;

        String serverList = Component.translatable("gui.toMenu").getString();
        for (AbstractWidget widget : Screens.getWidgets(screen)) {
            if (widget.getMessage().getString().equals(serverList)) {
                widget.setMessage(net.minecraft.network.chat.CommonComponents.GUI_BACK);
            }
        }
    }

    private void relabelStopWatching(Screen screen) {
        boolean replay = com.vivet.speedrun.client.replay.ReplayPlayback.isActive();
        if (!Spectating.isActive() && !replay) return;

        String disconnect = net.minecraft.network.chat.CommonComponents
                .disconnectButtonLabel(false).getString();
        for (AbstractWidget widget : Screens.getWidgets(screen)) {
            if (widget.getMessage().getString().equals(disconnect)) {
                widget.setMessage(Component.translatable(replay ? "vivet.replay.leave" : "vivet.watch.stop"));
            }
        }
    }

    private static final Component LOCKED_TOOLTIP =
            Component.translatable("vivet.tooltip.rules_locked");

    private void sealWorldOptions(Screen screen) {
        if (!MatchRules.isLocked()) return;

        String target = Component.translatable("options.worldOptions.button").getString();
        for (AbstractWidget widget : Screens.getWidgets(screen)) {
            if (widget.getMessage().getString().equals(target)) {
                widget.active = false;
                widget.setTooltip(Tooltip.create(LOCKED_TOOLTIP));
            }
        }
    }

    private void sealWorldOptionsScreen(Screen screen) {
        if (!this.keepPublished && !MatchRules.isLocked()) return;

        java.util.Set<String> wayOut = java.util.Set.of(
                Component.translatable("gui.done").getString(),
                Component.translatable("gui.back").getString(),
                Component.translatable("gui.cancel").getString());
        sealAllBut(screen.children(), wayOut);
    }

    private static void sealAllBut(java.util.List<? extends net.minecraft.client.gui.components.events.GuiEventListener> listeners,
                                   java.util.Set<String> wayOut) {
        for (var listener : listeners) {
            if (listener instanceof net.minecraft.client.gui.components.events.ContainerEventHandler container) {
                sealAllBut(container.children(), wayOut);
                continue;
            }
            if (!(listener instanceof AbstractWidget widget)) continue;
            if (wayOut.contains(widget.getMessage().getString())) continue;

            widget.active = false;
            widget.setTooltip(Tooltip.create(LOCKED_TOOLTIP));
        }
    }

    private void caughtOpening(Screen screen) {
        if (!MatchRules.isLocked()) return;

        EasterEgg.caught();
        ScreenEvents.afterExtract(screen).register(
                (drawn, graphics, mouseX, mouseY, partialTick) -> EasterEgg.render(graphics));
    }

    private void bindSession() {
        Session.bind(this::sendChat, this::onSessionStart, this::onSessionReroll, this::onSessionEnd);

        Session.bindClientEffects(this::sendNamedChat, VivetSpeedrunClient::playCue);
        Session.bindVotes(this::onVoteOpened, this::onVoteClosed, this::onDrawAgreed);
        Session.bindGo(this::onGo);
        Session.bindReadyCheck(this::onReadyCheck, this::onReadyCheckDone);
        Session.bindHostQuit(this::onHostQuit);
        Session.bindPublicMatch(this::onPublicMatchOver);
        Session.bindGraceOffer(this::onGraceOffered);

        Session.bindLines(message -> {
            if (ClientPlayNetworking.canSend(Payloads.Line.TYPE)) {
                ClientPlayNetworking.send(new Payloads.Line(message.encode()));
            }
        });
    }

    private void onVoteOpened(Session.Vote vote) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() instanceof VoteScreen) return;

        minecraft.gui.setScreen(new VoteScreen(minecraft.gui.screen(), vote));
    }

    private void onVoteClosed() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() instanceof VoteScreen screen) {
            screen.onClose();
        }
    }

    private void onDrawAgreed() {
        if (ClientState.isRunning()) Network.draw();
    }

    private void onHostQuit() {
        Minecraft minecraft = Minecraft.getInstance();

        this.pendingStart = null;
        this.heldResults = null;
        ResultsPrompt.dismiss();

        if (Session.rejoinable() != null) {
            minecraft.gui.setScreen(new com.vivet.speedrun.client.screen.RejoinScreen());
            return;
        }

        this.worldReady = false;
        this.goReceived = false;
        WorldActions.quitRace(minecraft);
    }

    private void onGraceOffered(com.vivet.speedrun.game.Difficulty band) {
        Cues.opponentLeft();
        ResultsPrompt.show(() -> new GraceScreen(band));
    }

    private void onPublicMatchOver(String reason, boolean won) {
        this.publicMatchOver = reason == null ? "Match over" : reason;
        this.publicMatchWon = won;

        if (!won && "gave up".equals(this.lastRunDetail)) {
            this.publicMatchOver = hadOpponent()
                    ? "You gave up"
                    : "You gave up. This run doesn't count";
        }

        if (Session.wasConceded()) {
            this.publicMatchOver = "No winner";
        }

        if (Session.wasVoided()) {
            this.publicMatchOver = reason == null ? "Opponent left early" : reason;
        }
        this.publicMatchOverAt = System.currentTimeMillis();
        VivetSpeedrun.LOGGER.info("Match over ({}): {}",
                won ? "won" : "not won", this.publicMatchOver);

        if (ClientState.isRunning()) {
            if (!Session.isHostedMatch()) {
                if (won) {
                    Network.winByDefault();
                } else {
                    Network.forfeit();
                }
            }
            return;
        }
        showMatchOver(null);
    }

    private long publicMatchOverAt;

    private static final long RUN_ENDING_WAIT_MS = 10_000L;

    private void showMatchOver(Payloads.RunFinished finished) {
        String reason = this.publicMatchOver;
        if (reason == null) return;

        boolean won = this.publicMatchWon;
        this.publicMatchOver = null;
        this.publicMatchOverAt = 0L;
        this.pendingStart = null;
        this.worldReady = false;
        this.goReceived = false;

        Payloads.RunFinished results = finished != null ? finished : this.heldResults;
        this.heldResults = null;
        this.heldResultsAt = 0L;

        if (results != null) {
            VivetSpeedrun.LOGGER.info("Showing match results");
            showResults(results, reason);
            VictoryScreen.noticeMatchOver(reason, won);
            return;
        }

        Screen current = Minecraft.getInstance().gui.screen();
        if (current instanceof VictoryScreen || current instanceof EndgameScreen) {
            VictoryScreen.noticeMatchOver(reason, won);
            return;
        }

        VivetSpeedrun.LOGGER.info("Match over with no run to show");
        ResultsPrompt.show(() -> new EndgameScreen(reason, won));
    }

    private void runEndingOverdue() {
        if (this.publicMatchOver == null || this.publicMatchOverAt == 0L) return;
        if (System.currentTimeMillis() - this.publicMatchOverAt < RUN_ENDING_WAIT_MS) return;

        VivetSpeedrun.LOGGER.info("Run never reported an ending, showing match results");
        ClientState.stopped();
        showMatchOver(null);
    }

    private void showWatchChat(String name, String uuid, String text) {
        long asked = System.currentTimeMillis();
        WorldActions.waitFor(
                () -> com.vivet.speedrun.p2p.Profiles.of(uuid) != null
                        || com.vivet.speedrun.p2p.Profiles.refused(uuid)
                        || System.currentTimeMillis() - asked > 2_000L,
                () -> {
                    MojangAuth.Profile profile = com.vivet.speedrun.p2p.Profiles.of(uuid);
                    sendNamedChat(name, profile == null ? null : profile.label, ": " + text,
                            Session.Tone.CHAT);
                },
                5_000L,
                "a chat line's label");
    }

    private void sendNamedChat(String name, String labelId, String suffix, Session.Tone tone) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        NameLabel label = NameLabel.byId(labelId);
        if (label == null) label = NameLabel.of(name);
        if (label == null) {
            sendChat(name + suffix, tone);
            return;
        }

        minecraft.player.sendSystemMessage(Component.empty().withStyle(colourFor(tone))
                .append(NameLabel.decorate(name, label))
                .append(Component.literal(suffix)));
    }

    private static void playCue(Session.Cue cue) {
        switch (cue) {
            case RIVAL_MOVED -> Cues.rivalMoved();
            case OPPONENT_LEFT -> Cues.opponentLeft();
            case OPPONENT_RETURNED -> Cues.opponentReturned();
        }
    }

    private static ChatFormatting colourFor(Session.Tone tone) {
        return switch (tone) {
            case GOOD -> ChatFormatting.GREEN;
            case BAD -> ChatFormatting.RED;
            case NEUTRAL -> ChatFormatting.GRAY;
            case CHAT -> ChatFormatting.WHITE;
        };
    }

    private void handleDisplacement() {
        if (!Hub.takeDisplacement()) return;

        if (Session.isActive()) Session.quit();
        MojangAuth.forget();

        com.vivet.speedrun.p2p.Visits.forget();

        sendChat("You signed in on another device.",
                Session.Tone.BAD);
    }

    private void sendChat(String text, Session.Tone tone) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        minecraft.player.sendSystemMessage(Component.literal(text).withStyle(colourFor(tone)));
    }

    private boolean onChatSent(String text) {
        if (text.isBlank()) return true;

        Minecraft minecraft = Minecraft.getInstance();
        if (!Session.isActive() && HostedConnect.isRaceServer(minecraft.getCurrentServer())) {
            sendNamedChat(minecraft.getUser().getName(), null, ": " + text, Session.Tone.CHAT);

            if (ClientPlayNetworking.canSend(Payloads.Line.TYPE)) {
                com.vivet.speedrun.session.SessionMessage said = com.vivet.speedrun.session.SessionMessage.of(
                        com.vivet.speedrun.session.SessionMessage.Type.CHAT, minecraft.getUser().getName());
                said.text = text;
                ClientPlayNetworking.send(new Payloads.Line(said.encode()));
            }
            return false;
        }

        if (!Session.isActive()) return true;

        Session.say(text);
        return false;
    }

    private void onReadyCheck() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() instanceof ReadyScreen) return;

        minecraft.gui.setScreen(new ReadyScreen());
    }

    private void reassertReadyCheck(Minecraft minecraft) {
        if (Session.phase() != Session.Phase.READY_CHECK) return;
        if (Session.isLocallyReady()) return;
        if (minecraft.gui.screen() != null) return;
        if (minecraft.player != null && !minecraft.player.isAlive()) return;

        minecraft.gui.setScreen(new ReadyScreen());
    }

    private void onReadyCheckDone() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() instanceof ReadyScreen screen) {
            screen.onClose();
        }
    }

    private void onSessionStart(SessionMessage message) {
        Minecraft minecraft = Minecraft.getInstance();
        this.pendingStart = message;
        ResultsPrompt.dismiss();

        VivetSpeedrun.LOGGER.info("Race starting");

        this.worldReady = false;
        this.goReceived = false;

        if (!LoadingTakeover.isArmed() && LoadingTakeover.wanted()) {
            LoadingTakeover.open(LoadingTakeover.Kind.PRIVATE);
        }
        if (LoadingTakeover.isArmed()) LoadingTakeover.setRoster(Session.racers());

        WorldActions.openSeededWorld(minecraft, Session.matchSeed(), this::onRaceWorldReady);
    }

    private static boolean canPublish(Minecraft minecraft) {
        return minecraft.getConnection() != null && minecraft.level != null;
    }

    private void keepWorldRunningWhilePaused(Minecraft minecraft) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (!(server instanceof IntegratedServer integrated) || integrated.isPublished()) return;
        if (!canPublish(minecraft)) return;

        boolean published = integrated.publishServer(
                MinecraftServer.MultiplayerScope.LAN, HttpUtil.getAvailablePort());

        if (published) {
            this.keepPublished = true;
            VivetSpeedrun.LOGGER.info("Race world will not pause");
        } else {
            VivetSpeedrun.LOGGER.warn("Could not stop the race world from pausing");
            sendChat("This world pauses when you press Escape.", Session.Tone.BAD);
        }
    }

    private void holdWorldPublished(Minecraft minecraft) {
        if (!this.keepPublished) return;

        MinecraftServer server = minecraft.getSingleplayerServer();
        if (!(server instanceof IntegratedServer integrated)) {
            this.keepPublished = false;
            return;
        }
        if (integrated.isPublished()) return;
        if (!canPublish(minecraft)) return;

        long now = System.currentTimeMillis();
        if (now - this.republishedAt < 1_000L) return;
        this.republishedAt = now;

        if (integrated.publishServer(
                MinecraftServer.MultiplayerScope.LAN, HttpUtil.getAvailablePort())) {
            VivetSpeedrun.LOGGER.info("Opened race world to LAN so it does not pause");
        }
    }

    private void onRaceWorldReady() {
        keepWorldRunningWhilePaused(Minecraft.getInstance());

        LoadingTakeover.worldReady();

        MatchRules.arm();

        WorldActions.waitFor(

                () -> ClientPlayNetworking.canSend(Payloads.StartShared.TYPE)
                        && SeedDifficulty.isReady(),
                () -> {
                    this.worldReady = true;
                    Session.reportReady();

                    VivetSpeedrun.LOGGER.info("World ready, waiting for start");
                    tryBeginPendingRun();
                },
                START_SEND_TIMEOUT_MS,
                "the network to connect");
    }

    private void onGo() {
        if (LoadingTakeover.isArmed()) {
            LoadingTakeover.whenReleased(() -> {
                this.goReceived = true;
                tryBeginPendingRun();
            });
            LoadingTakeover.go();
            return;
        }

        this.goReceived = true;
        tryBeginPendingRun();
    }

    private void tryBeginPendingRun() {
        SessionMessage message = this.pendingStart;
        if (message == null || !this.worldReady || !this.goReceived) return;

        this.pendingStart = null;
        sendSharedStart(message);
    }

    private void sendSharedStart(SessionMessage message) {
        WorldActions.waitFor(

                () -> ClientPlayNetworking.canSend(Payloads.StartShared.TYPE)
                        && SeedDifficulty.isReady(),
                () -> {
                    Target drawn = Session.drawTarget();
                    if (drawn == null) {
                        abandonUndrawableMatch(Minecraft.getInstance());
                        return;
                    }

                    Difficulty tier = ItemTiers.classify(drawn, Session.matchDimension());
                    Network.startShared(drawn.id(), tier);

                    VivetSpeedrun.LOGGER.info("Rolled {} ({}, {}), {}, start sent",
                            drawn.id(), tier.id(), Session.matchBand().id(),
                            ItemTiers.gateNote(drawn));
                },
                START_SEND_TIMEOUT_MS,
                "the network to connect");
    }

    private void abandonUndrawableMatch(Minecraft minecraft) {
        String reason = "No items for this difficulty. Pick another one.";

        VivetSpeedrun.LOGGER.warn("{}", reason);
        sendChat(reason, Session.Tone.BAD);
        Cues.deny();

        this.pendingStart = null;
        this.worldReady = false;
        this.goReceived = false;
        ItemRoll.cancel();

        if (Session.isActive()) Session.quit();
        WorldActions.quitRace(minecraft);
    }

    private void onSessionReroll(int rerollIndex) {
        Target drawn = Session.drawTarget();
        if (drawn == null) {
            sendChat("Nothing left to reroll to.", Session.Tone.BAD);
            return;
        }
        VivetSpeedrun.LOGGER.info("Reroll {}: {}", rerollIndex, drawn.id());
        Network.setTarget(drawn.id());
    }

    private void onSessionEnd(String reason) {
        sendChat(reason, Session.Tone.NEUTRAL);

        com.vivet.speedrun.client.screen.OpponentCard.clear();

        if (this.publicMatchOver != null) return;

        if (ClientState.isRunning()) {
            if (Session.localIsMatchWinner()) {
                Network.winByDefault();
            } else {
                Network.loseRace();
            }
        }

        releaseHeldResults();
    }

    private void releaseHeldResults() {
        Payloads.RunFinished results = this.heldResults;
        if (results == null) return;

        this.heldResults = null;
        this.heldResultsAt = 0L;
        showResults(results);
    }

    private void releaseSettledResults() {
        if (this.heldResults == null) return;

        boolean settled = !Session.isActive()
                || Session.isMatchDecided()
                || Session.phase() == Session.Phase.FINISHED;
        if (!settled) return;

        releaseHeldResults();
    }

    private void releaseStaleResults() {
        if (this.heldResults == null || this.heldResultsAt == 0L) return;
        if (System.currentTimeMillis() - this.heldResultsAt < VERDICT_WAIT_MS) return;

        VivetSpeedrun.LOGGER.info("No match result received, showing what is known");
        releaseHeldResults();
    }

    private void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(Payloads.RunState.TYPE, (payload, context) ->
                context.client().execute(() -> ClientState.update(payload)));

        ClientPlayNetworking.registerGlobalReceiver(Payloads.History.TYPE, (payload, context) ->
                context.client().execute(() -> ClientState.updateHistory(payload)));

        ClientPlayNetworking.registerGlobalReceiver(Payloads.Reveal.TYPE, (payload, context) ->
                context.client().execute(() ->
                        playReveal(context.client(), payload.itemId(), payload.reroll(),
                                payload.band())));

        ClientPlayNetworking.registerGlobalReceiver(Payloads.Go.TYPE, (payload, context) ->
                context.client().execute(HostedLoading::go));

        ClientPlayNetworking.registerGlobalReceiver(Payloads.Line.TYPE, (payload, context) -> {
            com.vivet.speedrun.session.SessionMessage said =
                    com.vivet.speedrun.session.SessionMessage.decode(payload.json());
            if (said != null && said.type == com.vivet.speedrun.session.SessionMessage.Type.CHAT
                    && said.uuid != null && said.name != null && said.text != null) {
                context.client().execute(() -> showWatchChat(said.name, said.uuid, said.text));
                return;
            }
            Session.receiveLine(payload.json());
        });

        ClientPlayNetworking.registerGlobalReceiver(Payloads.RunFinished.TYPE, (payload, context) ->
                context.client().execute(() -> onRunFinished(context.client(), payload)));

        ClientPlayNetworking.registerGlobalReceiver(Payloads.Watch.TYPE, (payload, context) ->
                context.client().execute(() -> Spectating.update(payload)));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.WatchEnd.TYPE, (payload, context) ->
                context.client().execute(() -> Spectating.ended(payload)));

        ClientPlayNetworking.registerGlobalReceiver(Payloads.Eyes.TYPE, (payload, context) ->
                context.client().execute(() -> com.vivet.speedrun.client.replay.SpectateEyes.receive(context.client(), payload)));

        ClientPlayNetworking.registerGlobalReceiver(Payloads.Book.TYPE, (payload, context) ->
                context.client().execute(() -> com.vivet.speedrun.client.replay.SpectateEyes.receive(payload)));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.BookPick.TYPE, (payload, context) ->
                context.client().execute(() -> com.vivet.speedrun.client.replay.SpectateEyes.receive(payload)));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.Drag.TYPE, (payload, context) ->
                context.client().execute(() -> com.vivet.speedrun.client.replay.SpectateEyes.receive(payload)));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.RideJump.TYPE, (payload, context) ->
                context.client().execute(() -> com.vivet.speedrun.client.replay.SpectateEyes.receive(payload)));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.OwnScreen.TYPE, (payload, context) ->
                context.client().execute(() -> com.vivet.speedrun.client.replay.SpectateEyes.receive(payload)));

        ClientPlayNetworking.registerGlobalReceiver(Payloads.RaceOver.TYPE, (payload, context) ->
                context.client().execute(() -> com.vivet.speedrun.game.MatchLogs.raceOver(
                        payload.matchId(), payload.seed(), payload.itemId())));

        ClientPlayNetworking.registerGlobalReceiver(Payloads.Milestone.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    VivetSpeedrun.LOGGER.info("Milestone: {}", payload.text());
                    Session.announce(payload.text());
                }));
    }

    private void playReveal(Minecraft minecraft, String itemId, boolean reroll,
                            com.vivet.speedrun.game.Difficulty band) {
        Target revealed = ItemTiers.byId(itemId);
        if (revealed == null) return;

        if (!reroll) {
            this.publicMatchOver = null;
            this.publicMatchOverAt = 0L;
        }

        prepareStanding(revealed, reroll, band);

        ItemRoll.conceal();

        if (!reroll) ItemRoll.holdStill();
        whenFullyLoaded(minecraft, () -> ItemRoll.start(revealed,
                ItemTiers.classify(revealed, Session.activeDimension()),
                () -> announceTarget(minecraft, revealed, reroll)));
    }

    private void prepareStanding(Target revealed, boolean reroll,
                                 com.vivet.speedrun.game.Difficulty band) {
        Standings.clear();

        com.vivet.speedrun.p2p.PostMatch.clear();

        if (!RunMode.current().isRecorded()) return;

        Standings.load(revealed.id(), band);

        if (reroll || !MojangAuth.isSignedIn()) return;

        this.ratingBefore = MojangAuth.knownProfile();

        com.vivet.speedrun.p2p.RankedApi.loadProfile(
                profile -> this.ratingBefore = profile,
                com.vivet.speedrun.p2p.RankedApi.logging(
                        "Could not read the standing before the match"));
    }

    private void announceTarget(Minecraft minecraft, Target revealed, boolean reroll) {
        Network.revealDone();

        if (minecraft.player == null) return;

        minecraft.player.sendSystemMessage(Component.translatable(
                        reroll ? "vivet.message.rerolled" : "vivet.message.started",
                        revealed.displayName())
                .withStyle(reroll ? ChatFormatting.YELLOW : ChatFormatting.GOLD));
    }

    private void whenFullyLoaded(Minecraft minecraft, Runnable action) {
        long queuedAt = System.currentTimeMillis();

        WorldActions.waitFor(
                () -> isFullyLoaded(minecraft)
                        || System.currentTimeMillis() - queuedAt > REVEAL_WAIT_MS,
                action,
                REVEAL_WAIT_MS + 5_000L,
                "the world to finish loading");
    }

    private static boolean isFullyLoaded(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) return false;

        Screen screen = minecraft.gui.screen();
        return !(screen instanceof LevelLoadingScreen) && !(screen instanceof ProgressScreen);
    }

    private void onRunFinished(Minecraft minecraft, Payloads.RunFinished payload) {
        if (Session.isHosted()) HostedRejoin.clear();

        ClientState.stopped();

        if (WorldActions.raceEndedOnTheWayOut()) return;

        this.lastRunDetail = payload.detail() == null ? "" : payload.detail();
        VivetSpeedrun.LOGGER.info("Run ended: {}", this.lastRunDetail);

        if (foundIt(payload)) {
            Session.reportFinish(payload.seconds(), payload.centis(), payload.itemId());
        } else if (!isWalkoverWin(payload)) {
            Session.reportOut(payload.detail(), payload.itemId());
        }

        this.lastReportedSecond = -1;

        if (this.publicMatchOver != null) {
            showMatchOver(payload);
            return;
        }

        if (Session.isMultiplayer() || Session.isRankedSolo()) {
            if (holdsResults()) {
                hold(payload, foundIt(payload));
            } else {
                showResults(payload);
            }
            return;
        }

        if (Practice.isActive()) {
            showResults(payload);
            return;
        }

        if (isDeliberate(this.lastRunDetail)) {
            showResults(payload);
            return;
        }

        switch (ConfigManager.get().afterRun) {
            case ASK -> showResults(payload);
            case AUTO_RESET -> WorldActions.recreateWorld(minecraft, null, Network::playAgain);
            case NOTHING -> {
            }
        }
    }

    private static boolean isDeliberate(String detail) {
        return "gave up".equals(detail) || "stopped the run".equals(detail);
    }

    private static boolean isWalkoverWin(Payloads.RunFinished payload) {
        return payload.outcome() == com.vivet.speedrun.game.RunRecord.Outcome.WON
                && "last one standing".equals(payload.detail());
    }

    private static boolean foundIt(Payloads.RunFinished payload) {
        return payload.outcome() == com.vivet.speedrun.game.RunRecord.Outcome.WON
                && !isWalkoverWin(payload);
    }

    private static boolean hadOpponent() {
        return Session.isMultiplayer() || Session.lastMatchSize() > 1;
    }

    private void showResults(Payloads.RunFinished payload) {
        showResults(payload, "");
    }

    private void showResults(Payloads.RunFinished payload, String line) {
        boolean found = foundIt(payload);

        RunSummary summary = RunSummary.of(payload.itemId(), ClientState.difficulty(),
                payload.seconds(), payload.centis(), found, payload.detail(),
                ClientState.rerollsUsed(),
                this.ratingBefore);

        boolean firstFind = firstFindOf(summary);

        logTheGame(summary, firstFind);

        if (firstFind) announceUnlock(summary);

        if (summary.mode.isRecorded() && found) {
            Standings.refresh(payload.itemId(), ClientState.difficulty());
        }

        if (summary.mode.isRecorded()) com.vivet.speedrun.p2p.CollectionBook.stale();

        if (Practice.isActive()) {
            Practice.noteEnded(summary.centis, summary.found, summary.personalBest);

            if (summary.personalBest) {
                Cues.practiceBest();
            } else if (summary.found) {
                Cues.collected();
            } else {
                Cues.matchEnded();
            }

            ResultsPrompt.dismiss();
            WorldActions.quitRaceTo(Minecraft.getInstance(),
                    com.vivet.speedrun.client.screen.PracticeScreen::returnDestination);
            return;
        }

        if (!EndgameScreen.outroTakesTheMoment(summary)) {
            if (summary.won()) {
                Cues.matchWon();
            } else if (summary.drew()) {
                Cues.matchEnded();
            } else {
                Cues.matchLost();
            }
        }

        ResultsPrompt.show(EndgameScreen.showsFor(summary.mode)
                ? () -> new EndgameScreen(summary, line)
                : () -> new VictoryScreen(summary));
    }

    private boolean firstFindOf(RunSummary summary) {
        if (!summary.found) return false;
        if (!com.vivet.speedrun.game.AccountXp.earnsXp(summary.mode)) return false;
        if (!com.vivet.speedrun.p2p.CollectionBook.known()) return false;

        String key = collectionKeyOf(summary.itemId);
        if (key == null) return false;

        var square = com.vivet.speedrun.p2p.CollectionBook.get(key);
        return square == null || square.finishes == null || square.finishes == 0;
    }

    private static String collectionKeyOf(String itemId) {
        Target target = com.vivet.speedrun.game.ItemTiers.byId(itemId);
        return target == null ? null : com.vivet.speedrun.game.ItemTiers.ratingKey(target);
    }

    private void announceUnlock(RunSummary summary) {
        Target target = com.vivet.speedrun.game.ItemTiers.byId(summary.itemId);
        if (target == null) return;

        com.vivet.speedrun.client.hud.Notices.collected(
                target.displayStack(), target.displayName());
    }

    private void logTheGame(RunSummary summary, boolean firstFind) {
        if (!com.vivet.speedrun.game.MatchLogs.logs(summary.mode)) return;

        boolean tookRecord = false;
        if (summary.found && summary.mode.isRecorded()) {
            Standings.Board board = summary.setSeed ? summary.seedBoard() : summary.itemBoard();
            Integer best = Standings.bestCentis(board);
            tookRecord = best != null && summary.centis < best;
        }

        boolean nether = !Session.wasHosted() && Session.activeDimension().isNetherOnly();

        com.vivet.speedrun.game.MatchLogs.record(summary, firstFind, tookRecord, nether,
                worldSeed());
    }

    private long worldSeed() {
        try {
            var server = Minecraft.getInstance().getSingleplayerServer();
            return server == null ? 0L : server.overworld().getSeed();
        } catch (RuntimeException e) {
            return 0L;
        }
    }

    private boolean holdsResults() {
        if (Session.phase() == Session.Phase.FINISHED || Session.isMatchDecided()) return false;

        if (!Session.isMultiplayer()) return false;

        return true;
    }

    private void hold(Payloads.RunFinished payload, boolean found) {
        this.heldResults = payload;
        this.heldResultsAt = System.currentTimeMillis();
        ResultsPrompt.show(() -> new WaitingScreen(payload.seconds(), found));
    }

    private void startSoloRun(Minecraft minecraft) {
        if (!Session.canStartLocally()) return;

        VivetSpeedrun.LOGGER.info("Practice: creating world for {}",
                Practice.itemId());
        this.pendingStart = null;
        this.heldResults = null;
        ResultsPrompt.dismiss();

        MatchRules.set(MatchRules.Policy.forPractice());

        LoadingTakeover.open(LoadingTakeover.Kind.PRACTICE);
        LoadingTakeover.whenReleased(Network::startRun);

        WorldActions.openSeededWorld(minecraft, Practice.seedForNextWorld(), () -> {
            LoadingTakeover.worldReady();
            WorldActions.waitFor(
                    () -> ClientPlayNetworking.canSend(Payloads.RunAction.TYPE),
                    LoadingTakeover::go,
                    START_SEND_TIMEOUT_MS,
                    "the network to connect");
        });
    }

    private void resumeSavedMatch(Minecraft minecraft) {
        RejoinStore.Saved saved = RejoinStore.offered(minecraft);
        if (saved == null) return;

        VivetSpeedrun.LOGGER.info("Rejoining match in room {}",
                saved.roomCode);

        this.pendingStart = null;
        this.heldResults = null;
        ResultsPrompt.dismiss();

        Session.adoptRejoin(RejoinStore.asRejoinable(saved));

        MatchRules.set(RejoinStore.policyFor(saved));

        RunManager.armResume(new RunManager.Resume(saved.targetId, saved.difficulty,
                saved.rerollsUsed, saved.runStartedAt));

        LoadingTakeover.suppressNext();

        this.worldReady = false;
        this.goReceived = false;

        WorldActions.openExistingWorld(minecraft, saved.levelId,
                () -> minecraft.gui.setScreen(new RejoinScreen(saved.aliveAt)));
    }

    private boolean runVoided;

    private static final String NO_REMATCH = "No rematch right now.";

    private void watchForTampering() {
        String why = MatchRules.tampered();
        if (why == null) {
            this.runVoided = false;
            return;
        }
        if (this.runVoided) return;
        this.runVoided = true;

        sendChat("Match rules changed (" + why + "). Run voided.", Session.Tone.BAD);
        if (Session.isActive()) {
            Target hunting = RunManager.target();
            Session.reportOut("void: " + why, hunting == null ? null : hunting.id());
        }
        Network.forfeit();
    }

    private void onClientTick(Minecraft minecraft) {
        Cues.tick();
        com.vivet.speedrun.client.discord.RichPresence.tick(minecraft);
        LoadingTakeover.tick();
        HostedLoading.tick(minecraft);
        Spectating.tick(minecraft);

        if (com.vivet.speedrun.p2p.Hub.takeHostedVoid() && Session.isHosted()) {
            WorldActions.returnFromMatch("Match cancelled.", false);
        }

        String calledOff = com.vivet.speedrun.p2p.Hub.takeCalledOff();
        if (calledOff != null && Session.isHostedMatch() && calledOff.equals(Session.hostedMatch())
                && !Session.isMatchDecided()) {
            boolean ranked = Session.isRanked();
            com.vivet.speedrun.client.LoadingTakeover.abort();
            if (ranked) {
                com.vivet.speedrun.client.screen.RankedScreen.queueAgain(
                        com.vivet.speedrun.client.screen.RankedScreen.Mode.VERSUS);
            } else {
                com.vivet.speedrun.client.screen.FindMatchScreen.searchAgain();
            }
            WorldActions.returnFromMatch("Match cancelled.", false);
        }

        String refusedRematch = com.vivet.speedrun.p2p.Hub.takeRematchRefused();
        if (refusedRematch != null && Session.isHostedMatch()
                && refusedRematch.equals(Session.hostedMatch())) {
            Session.refuseRematch();
            Cues.deny();
            sendChat(NO_REMATCH, Session.Tone.BAD);
            VictoryScreen.noticeMatchOver(NO_REMATCH, false);
        }

        com.vivet.speedrun.p2p.RankedApi.HostedTicket rematch = com.vivet.speedrun.p2p.Hub.takeRematch();
        if (rematch != null && Session.isHostedMatch() && !Session.isRanked()) {
            com.vivet.speedrun.game.Difficulty band = com.vivet.speedrun.game.Difficulty.byId(rematch.band);
            if (band == null) band = Session.publicBand();
            com.vivet.speedrun.game.Difficulty going = band;

            ResultsPrompt.dismiss();
            this.heldResults = null;
            WorldActions.hopRaceServer(minecraft, () -> HostedConnect.joinMatch(minecraft,
                    com.vivet.speedrun.client.screen.FindMatchScreen.returnDestination(), going,
                    rematch, false));
        }

        com.vivet.speedrun.media.Media.tick();

        signInAtLaunch(minecraft);
        keepSignedIn(minecraft);
        handleDisplacement();

        ItemIcons.warmUp(minecraft);

        takeOverTitleScreen(minecraft);

        com.vivet.speedrun.p2p.RankedApi.refreshConfig();

        com.vivet.speedrun.p2p.Inbox.refresh();

        com.vivet.speedrun.p2p.Chat.refresh();

        Session.setLocalName(localName(minecraft));
        Session.drain();
        WorldActions.tick();

        RejoinStore.tick(minecraft);
        HostedRejoin.tick(minecraft);

        HostedRejoin.lookForMissed(minecraft);

        com.vivet.speedrun.game.MatchLogs.settle();

        com.vivet.speedrun.client.replay.ReplayPlayback.tick(minecraft);
        com.vivet.speedrun.client.replay.SpectateEyes.tick(minecraft);

        if (com.vivet.speedrun.client.replay.ReplayPlayback.ridden() != null
                && !minecraft.options.getCameraType().isFirstPerson()) {
            minecraft.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        }
        PointerSender.tick(minecraft);
        while (REPLAY_CONTROLS.consumeClick()) {
            if (com.vivet.speedrun.client.replay.ReplayPlayback.isActive() && minecraft.gui.screen() == null) {
                minecraft.gui.setScreen(new com.vivet.speedrun.client.screen.ReplayControlsScreen());
            }
        }
        holdWorldPublished(minecraft);
        ResultsPrompt.tick(minecraft);
        reportOwnClock();
        watchForTampering();
        releaseSettledResults();
        releaseStaleResults();
        runEndingOverdue();
        reassertReadyCheck(minecraft);

        boolean modWorld = WorldActions.inModWorld(minecraft);

        boolean hosted = Session.isHosted();
        RunMode mode = RunMode.current();
        boolean hostedHub = hosted && (mode == RunMode.RANKED_SOLO
                || mode == RunMode.RANKED_MATCH || mode == RunMode.PUBLIC);

        while (OPEN_SCREEN.consumeClick()) {
            if (Spectating.isActive()) {
                Spectating.switchPlayer();
                continue;
            }

            if (com.vivet.speedrun.client.replay.ReplayPlayback.isActive()) {
                com.vivet.speedrun.client.replay.ReplayPlayback.get().firstPersonOrSwitch();
                continue;
            }

            if ((!modWorld || hosted) && !hostedHub) {
                Cues.deny();
                continue;
            }

            if (ResultsPrompt.isParked()) {
                ResultsPrompt.unpark();
            } else {
                minecraft.gui.setScreen(new SpeedrunScreen());
            }
        }
        while (REROLL.consumeClick()) {
            if (Spectating.isActive()) {
                Spectating.teleportToRacer();
                continue;
            }

            if (com.vivet.speedrun.client.replay.ReplayPlayback.isActive()) {
                com.vivet.speedrun.client.replay.ReplayPlayback.get().teleportToRacer();
                continue;
            }

            if (modWorld || Session.isHostedMatch()) requestReroll();
        }

        boolean equals = Spectating.isActive() && Spectating.staff() && minecraft.gui.screen() == null
                && com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_EQUALS);
        if (equals && !this.equalsWasDown) Spectating.nextPlayer();
        this.equalsWasDown = equals;

        while (GIVE_UP.consumeClick()) {
            if (modWorld || hosted) Network.forfeit();
        }
    }

    private void signInAtLaunch(Minecraft minecraft) {
        if (this.launchSignInStarted) return;
        if (minecraft.getUser() == null) return;

        this.launchSignInStarted = true;

        com.vivet.speedrun.p2p.RankedApi.loadConfig(config -> { }, reason -> { });

        com.vivet.speedrun.p2p.MojangAuth.signIn(
                identity -> {
                    VivetSpeedrun.LOGGER.info("Signed in as {}", identity.name);
                },
                reason -> VivetSpeedrun.LOGGER.warn("Sign in failed: {}", reason));
    }

    private long nextSignInCheck;

    private void keepSignedIn(Minecraft minecraft) {
        if (!this.launchSignInStarted || minecraft.getUser() == null) return;

        long now = System.currentTimeMillis();
        if (now < this.nextSignInCheck) return;
        this.nextSignInCheck = now + 1_000L;

        com.vivet.speedrun.p2p.MojangAuth.keepAlive();
    }

    private void takeOverTitleScreen(Minecraft minecraft) {
        if (minecraft.gui.screen() instanceof VivetTitleScreen && UpdateScreen.waiting()) {
            minecraft.gui.setScreen(UpdateScreen.inFrontOf(VivetTitleScreen::forShowing));
            return;
        }
        if (!VivetTitleScreen.wanted()) return;
        if (!(minecraft.gui.screen() instanceof TitleScreen)) return;

        minecraft.gui.setScreen(UpdateScreen.inFrontOf(VivetTitleScreen::forShowing));
    }

    private void reportOwnClock() {
        if (!Session.isActive() || !ClientState.isRunning()) {
            this.lastReportedSecond = -1;
            return;
        }

        int seconds = ClientState.elapsedSeconds();
        if (seconds == this.lastReportedSecond) return;

        int step = Session.someoneHasFinished() ? 1 : IDLE_CLOCK_SECONDS;
        if (this.lastReportedSecond >= 0 && seconds - this.lastReportedSecond < step) return;

        this.lastReportedSecond = seconds;

        Target hunting = RunManager.target();
        Session.reportTime(seconds, ClientState.elapsedCentis(), ClientState.activity(),
                hunting == null ? null : hunting.id());
    }

    private void requestReroll() {
        if (!ClientState.isRunning()) return;

        if (Session.isRanked()) {
            sendChat("No rerolls in Ranked.", Session.Tone.BAD);
            return;
        }

        if (!Session.isMultiplayer()) {
            Network.reroll();
            return;
        }

        if (Session.openVote() != null) return;

        long now = System.currentTimeMillis();
        if (now - this.lastRerollAskedAt < REROLL_ASK_COOLDOWN_MS) return;
        this.lastRerollAskedAt = now;

        if (!ClientState.canReroll()) {
            sendChat(Session.rerollsLeft() == 0 ? "No rerolls left." : "Too late to reroll.",
                    Session.Tone.BAD);
            return;
        }

        Session.proposeVote(SessionMessage.VoteKind.REROLL);
    }

    private static final long REROLL_ASK_COOLDOWN_MS = 1_000L;

    private long lastRerollAskedAt;

    private String localName(Minecraft minecraft) {
        String profile = minecraft.getUser() != null ? minecraft.getUser().getName() : "You";

        ModConfig config = ConfigManager.get();
        if (Session.isPublicMatch() || config.playerName.isBlank()) return profile;

        return config.playerName;
    }
}
