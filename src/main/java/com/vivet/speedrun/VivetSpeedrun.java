package com.vivet.speedrun;

import com.vivet.speedrun.config.ConfigManager;
import com.vivet.speedrun.game.DimensionPool;
import com.vivet.speedrun.game.MatchRules;
import com.vivet.speedrun.game.MilestoneTracker;
import com.vivet.speedrun.game.NetherStart;
import com.vivet.speedrun.game.ActivityTracker;
import com.vivet.speedrun.game.RunHistory;
import com.vivet.speedrun.game.RunManager;
import com.vivet.speedrun.game.RunRecord;
import com.vivet.speedrun.game.SeedDifficulty;
import com.vivet.speedrun.game.WorldRuleApplier;
import com.vivet.speedrun.net.Payloads;
import com.vivet.speedrun.session.Session;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.storage.LevelResource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VivetSpeedrun implements ModInitializer {
    public static final String MOD_ID = "vivet_speedrun";

    public static final String VERSION = net.fabricmc.loader.api.FabricLoader.getInstance()
            .getModContainer(MOD_ID)
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("dev");

    public static String shownVersion(String version) {
        int build = version == null ? -1 : version.indexOf('+');
        return build < 0 ? version : version.substring(0, build);
    }

    public static final int PROTOCOL = 21;
    public static final Logger LOGGER = LoggerFactory.getLogger("Vivet Speedrun");

    private MinecraftServer server;

    private final Set<UUID> owedTheNether = ConcurrentHashMap.newKeySet();

    @Override
    public void onInitialize() {
        ConfigManager.load();

        registerPayloads();
        registerReceivers();

        RunManager.bind(this::onRunEnded, this::onTargetChosen);

        ServerLifecycleEvents.SERVER_STARTED.register(started -> {
            this.server = started;

            RunHistory.openWorld(started.getWorldPath(LevelResource.ROOT));

            SeedDifficulty.measureAsync(started.overworld());

            RunManager.resumeFromHistory(started);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(stopping -> {
            RunManager.persist();
            RunManager.cancelPendingStart();
            RunHistory.closeWorld();
            SeedDifficulty.clear();
            this.server = null;
        });

        ServerTickEvents.END_SERVER_TICK.register(ticking -> {
            MatchRules.enforce(ticking);

            returnAnybodyOwedTheNether(ticking);
            RunManager.tick(ticking);
            trackMilestones(ticking);
            trackActivity(ticking);
            broadcastState(ticking);
        });

        disableSeedCommand();

        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, from, to) -> {
            if (!RunManager.isRunning()) return;

            if (isNetherOnly() && to.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                this.owedTheNether.add(player.getUUID());
                player.sendSystemMessage(
                        Component.translatable("vivet.nether.no_way_back"), true);
                return;
            }

            MilestoneTracker.dimensionChanged(
                    to.dimension().identifier().toString(), this::reportMilestone);
        });

        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.isClientSide() || !isNetherOnly()) return InteractionResult.PASS;
            if (!NetherStart.wouldLightPortal(level, hit.getBlockPos())) {
                return InteractionResult.PASS;
            }

            net.minecraft.world.item.Item held = player.getItemInHand(hand).getItem();
            if (held != net.minecraft.world.item.Items.FLINT_AND_STEEL
                    && held != net.minecraft.world.item.Items.FIRE_CHARGE) {
                return InteractionResult.PASS;
            }

            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(
                        Component.translatable("vivet.nether.no_portal"), true);
            }
            return InteractionResult.FAIL;
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((old, fresh, alive) -> {
            if (!RunManager.isRunning()) return;
            if (Session.activeDimension() != DimensionPool.NETHER) return;
            if (fresh.level().dimension() == net.minecraft.world.level.Level.NETHER) return;

            this.owedTheNether.add(fresh.getUUID());
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (RunManager.isRunning() && entity instanceof ServerPlayer) {
                MilestoneTracker.died(this::reportMilestone);
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, joined) ->
                sendHistory(handler.player));

        LOGGER.info("Loaded");
    }

    private void returnAnybodyOwedTheNether(MinecraftServer ticking) {
        if (this.owedTheNether.isEmpty()) return;

        List<UUID> owed = new ArrayList<>(this.owedTheNether);
        this.owedTheNether.clear();

        if (!isNetherOnly()) return;

        for (UUID id : owed) {
            ServerPlayer player = ticking.getPlayerList().getPlayer(id);
            if (player == null) continue;
            if (player.level().dimension() == net.minecraft.world.level.Level.NETHER) continue;

            NetherStart.returnToNether(player);
        }
    }

    private static boolean isNetherOnly() {
        return RunManager.isRunning() && Session.activeDimension() == DimensionPool.NETHER;
    }

    private void disableSeedCommand() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
                dispatcher.register(Commands.literal("seed").executes(context -> {
                    var source = context.getSource();
                    if (!seedIsSecret(source.getServer())) {
                        long seed = source.getLevel().getSeed();
                        Component shown = net.minecraft.network.chat.ComponentUtils
                                .copyOnClickText(String.valueOf(seed));
                        source.sendSuccess(() -> Component.translatable("commands.seed.success", shown), false);
                        return (int) seed;
                    }
                    source.sendFailure(Component.literal("The seed is hidden."));
                    return 0;
                })));
    }

    private static boolean seedIsSecret(MinecraftServer server) {
        if (RunManager.isRunning()) return true;
        if (server == null) return true;
        String levelId = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .normalize().getFileName().toString();
        return levelId.startsWith("Vivet Race");
    }

    private void registerPayloads() {
        PayloadTypeRegistry.serverboundPlay()
                .register(Payloads.RunAction.TYPE, Payloads.RunAction.CODEC);
        PayloadTypeRegistry.serverboundPlay()
                .register(Payloads.StartShared.TYPE, Payloads.StartShared.CODEC);
        PayloadTypeRegistry.serverboundPlay()
                .register(Payloads.SetTarget.TYPE, Payloads.SetTarget.CODEC);

        PayloadTypeRegistry.serverboundPlay()
                .register(Payloads.Pointer.TYPE, Payloads.Pointer.CODEC);

        PayloadTypeRegistry.clientboundPlay()
                .register(Payloads.RunState.TYPE, Payloads.RunState.CODEC);
        PayloadTypeRegistry.clientboundPlay()
                .register(Payloads.History.TYPE, Payloads.History.CODEC);
        PayloadTypeRegistry.clientboundPlay()
                .register(Payloads.RunFinished.TYPE, Payloads.RunFinished.CODEC);
        PayloadTypeRegistry.clientboundPlay()
                .register(Payloads.Reveal.TYPE, Payloads.Reveal.CODEC);
        PayloadTypeRegistry.clientboundPlay()
                .register(Payloads.Milestone.TYPE, Payloads.Milestone.CODEC);
        PayloadTypeRegistry.clientboundPlay()
                .register(Payloads.Go.TYPE, Payloads.Go.CODEC);

        PayloadTypeRegistry.clientboundPlay()
                .register(Payloads.Line.TYPE, Payloads.Line.CODEC);
        PayloadTypeRegistry.serverboundPlay()
                .register(Payloads.Line.TYPE, Payloads.Line.CODEC);

        PayloadTypeRegistry.clientboundPlay()
                .register(Payloads.Watch.TYPE, Payloads.Watch.CODEC);
        PayloadTypeRegistry.clientboundPlay()
                .register(Payloads.WatchEnd.TYPE, Payloads.WatchEnd.CODEC);

        PayloadTypeRegistry.clientboundPlay()
                .register(Payloads.Eyes.TYPE, Payloads.Eyes.CODEC);

        PayloadTypeRegistry.serverboundPlay()
                .register(Payloads.Book.TYPE, Payloads.Book.CODEC);
        PayloadTypeRegistry.clientboundPlay()
                .register(Payloads.Book.TYPE, Payloads.Book.CODEC);
        PayloadTypeRegistry.serverboundPlay()
                .register(Payloads.BookPick.TYPE, Payloads.BookPick.CODEC);
        PayloadTypeRegistry.clientboundPlay()
                .register(Payloads.BookPick.TYPE, Payloads.BookPick.CODEC);

        PayloadTypeRegistry.serverboundPlay()
                .register(Payloads.OwnScreen.TYPE, Payloads.OwnScreen.CODEC);
        PayloadTypeRegistry.clientboundPlay()
                .register(Payloads.OwnScreen.TYPE, Payloads.OwnScreen.CODEC);

        PayloadTypeRegistry.serverboundPlay()
                .register(Payloads.RideJump.TYPE, Payloads.RideJump.CODEC);
        PayloadTypeRegistry.clientboundPlay()
                .register(Payloads.RideJump.TYPE, Payloads.RideJump.CODEC);

        PayloadTypeRegistry.serverboundPlay()
                .register(Payloads.Drag.TYPE, Payloads.Drag.CODEC);
        PayloadTypeRegistry.clientboundPlay()
                .register(Payloads.Drag.TYPE, Payloads.Drag.CODEC);

        PayloadTypeRegistry.clientboundPlay()
                .register(Payloads.RaceOver.TYPE, Payloads.RaceOver.CODEC);
    }

    private void registerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(Payloads.RunAction.TYPE, (payload, context) ->
                context.server().execute(() -> handleAction(context.server(), payload)));

        ServerPlayNetworking.registerGlobalReceiver(Payloads.StartShared.TYPE, (payload, context) ->
                context.server().execute(() -> {
                    RunManager.abandonQuietly();
                    RunManager.startWith(context.server(), payload.itemId(),
                            payload.difficulty());
                }));

        ServerPlayNetworking.registerGlobalReceiver(Payloads.SetTarget.TYPE, (payload, context) ->
                context.server().execute(() ->
                        RunManager.reroll(context.server(), payload.itemId())));
    }

    private void handleAction(MinecraftServer server, Payloads.RunAction payload) {
        switch (payload.action()) {
            case TOGGLE_RUN -> RunManager.toggle(server);
            case REROLL -> RunManager.reroll(server, null);
            case FORFEIT -> RunManager.forfeit(server);
            case SPECTATE -> RunManager.spectate(server);
            case DRAW -> RunManager.draw(server);
            case PLAY_AGAIN -> RunManager.start(server);
            case APPLY_MODIFIERS -> WorldRuleApplier.applyAll(server);
            case WIN_BY_DEFAULT -> RunManager.winByDefault(server);
            case LOSE_RACE -> RunManager.loseRace(server);
            case REVEAL_DONE -> RunManager.releaseClock();

            case READY, STARTED, TELEPORT -> { }
        }
    }

    private void onRunEnded(RunRecord record) {
        if (this.server == null) return;

        Payloads.RunFinished finished = new Payloads.RunFinished(
                record.itemId, record.winnerName, record.outcome,
                record.seconds, record.centis, record.detail);

        for (ServerPlayer player : this.server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, finished);
            sendHistory(player);
        }
    }

    private void onTargetChosen(String itemId, boolean reroll) {
        if (this.server == null) return;

        broadcast(this.server, new Payloads.Reveal(itemId, reroll, RunManager.difficulty()));
    }

    private void trackActivity(MinecraftServer server) {
        if (!RunManager.isRunning()) {
            ActivityTracker.reset();
            return;
        }

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (!players.isEmpty()) ActivityTracker.tick(players.get(0));
    }

    private void trackMilestones(MinecraftServer server) {
        if (!RunManager.isRunning()) return;

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return;

        MilestoneTracker.tick(players.get(0), this::reportMilestone);
    }

    private void reportMilestone(String text) {
        if (this.server == null) return;

        broadcast(this.server, new Payloads.Milestone(text));
    }

    private void broadcastState(MinecraftServer server) {
        Payloads.RunState state = new Payloads.RunState(
                RunManager.isRunning(),
                RunManager.isRunning() ? RunManager.target().id() : "air",
                RunManager.elapsedCentis(),
                RunManager.difficulty(),
                RunManager.rerollsUsed(),
                ActivityTracker.counter());

        broadcast(server, state);
    }

    private void broadcast(MinecraftServer server, CustomPacketPayload payload) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    private void sendHistory(ServerPlayer player) {
        List<Payloads.HistoryEntry> entries = new ArrayList<>();
        for (RunRecord record : RunHistory.all()) {
            entries.add(Payloads.HistoryEntry.from(record));
        }
        ServerPlayNetworking.send(player, new Payloads.History(entries));
    }
}
