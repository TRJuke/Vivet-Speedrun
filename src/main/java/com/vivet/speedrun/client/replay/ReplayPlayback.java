package com.vivet.speedrun.client.replay;

import com.google.common.collect.LinkedHashMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.client.Cues;
import com.vivet.speedrun.replay.LoadedReplay;
import com.vivet.speedrun.replay.ReplayContainer;
import com.vivet.speedrun.replay.ReplayTrace;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientRegistryLayer;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.telemetry.TelemetryEventSender;
import net.minecraft.client.telemetry.WorldSessionTelemetryManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.CommonPacketTypes;
import net.minecraft.network.protocol.configuration.ConfigurationProtocols;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.CommonPlayerSpawnInfo;
import net.minecraft.network.protocol.game.GamePacketTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.ServerLinks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ReplayPlayback implements ReplayScreens.Watched {
    public static final int CAMERA_ID = -7_000_001;

    private static final ResourceKey<Level> RESET_LEVEL = ResourceKey.create(Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(VivetSpeedrun.MOD_ID, "replay_reset"));

    private static final long TICK_MS = 50L;

    private static final int[] SPEEDS = { 1, 2, 4, 8, 16 };

    private static final float BASE_FLY = 0.05F;
    public static final int[] FLY_SPEEDS = { 1, 2, 4, 8 };

    private static ReplayPlayback active;

    static final List<String> STALE = new ArrayList<>();

    private static final Set<Integer> DROPPED = ids(
            CommonPacketTypes.CLIENTBOUND_KEEP_ALIVE, CommonPacketTypes.CLIENTBOUND_PING,
            CommonPacketTypes.CLIENTBOUND_CUSTOM_PAYLOAD, CommonPacketTypes.CLIENTBOUND_DISCONNECT,
            CommonPacketTypes.CLIENTBOUND_TRANSFER, CommonPacketTypes.CLIENTBOUND_STORE_COOKIE,
            CommonPacketTypes.CLIENTBOUND_RESOURCE_PACK_PUSH, CommonPacketTypes.CLIENTBOUND_RESOURCE_PACK_POP,
            CommonPacketTypes.CLIENTBOUND_SERVER_LINKS, CommonPacketTypes.CLIENTBOUND_CUSTOM_REPORT_DETAILS,
            CommonPacketTypes.CLIENTBOUND_SHOW_DIALOG, CommonPacketTypes.CLIENTBOUND_CLEAR_DIALOG,
            GamePacketTypes.CLIENTBOUND_START_CONFIGURATION, GamePacketTypes.CLIENTBOUND_SET_CAMERA,
            GamePacketTypes.CLIENTBOUND_OPEN_SIGN_EDITOR, GamePacketTypes.CLIENTBOUND_OPEN_BOOK,
            GamePacketTypes.CLIENTBOUND_PLAYER_COMBAT_ENTER, GamePacketTypes.CLIENTBOUND_PLAYER_COMBAT_END,
            GamePacketTypes.CLIENTBOUND_PLAYER_COMBAT_KILL, GamePacketTypes.CLIENTBOUND_PLAYER_ABILITIES,
            GamePacketTypes.CLIENTBOUND_PLAYER_LOOK_AT, GamePacketTypes.CLIENTBOUND_PLAYER_ROTATION,
            GamePacketTypes.CLIENTBOUND_TICKING_STATE, GamePacketTypes.CLIENTBOUND_TICKING_STEP,
            GamePacketTypes.CLIENTBOUND_COMMANDS, GamePacketTypes.CLIENTBOUND_COMMAND_SUGGESTIONS,
            GamePacketTypes.CLIENTBOUND_CUSTOM_CHAT_COMPLETIONS,
            GamePacketTypes.CLIENTBOUND_AWARD_STATS, GamePacketTypes.CLIENTBOUND_DEBUG_SAMPLE,
            GamePacketTypes.CLIENTBOUND_DEBUG_BLOCK_VALUE, GamePacketTypes.CLIENTBOUND_DEBUG_CHUNK_VALUE,
            GamePacketTypes.CLIENTBOUND_DEBUG_ENTITY_VALUE, GamePacketTypes.CLIENTBOUND_DEBUG_EVENT,
            GamePacketTypes.CLIENTBOUND_GAME_TEST_HIGHLIGHT_POS, GamePacketTypes.CLIENTBOUND_TEST_INSTANCE_BLOCK_STATUS,
            GamePacketTypes.CLIENTBOUND_LOW_DISK_SPACE_WARNING, GamePacketTypes.CLIENTBOUND_TAG_QUERY);

    private static final Set<Integer> EFFECTS = ids(
            GamePacketTypes.CLIENTBOUND_SOUND, GamePacketTypes.CLIENTBOUND_SOUND_ENTITY,
            GamePacketTypes.CLIENTBOUND_LEVEL_PARTICLES, GamePacketTypes.CLIENTBOUND_LEVEL_EVENT,
            GamePacketTypes.CLIENTBOUND_EXPLODE, GamePacketTypes.CLIENTBOUND_ANIMATE,
            GamePacketTypes.CLIENTBOUND_HURT_ANIMATION, GamePacketTypes.CLIENTBOUND_DAMAGE_EVENT,
            GamePacketTypes.CLIENTBOUND_TAKE_ITEM_ENTITY, GamePacketTypes.CLIENTBOUND_BLOCK_DESTRUCTION,
            GamePacketTypes.CLIENTBOUND_SET_TITLE_TEXT, GamePacketTypes.CLIENTBOUND_SET_SUBTITLE_TEXT,
            GamePacketTypes.CLIENTBOUND_SET_ACTION_BAR_TEXT, GamePacketTypes.CLIENTBOUND_SET_TITLES_ANIMATION,
            GamePacketTypes.CLIENTBOUND_STOP_SOUND);

    private static final Set<Integer> OF_THE_WORLD = ids(
            GamePacketTypes.CLIENTBOUND_ADD_ENTITY, GamePacketTypes.CLIENTBOUND_REMOVE_ENTITIES,
            GamePacketTypes.CLIENTBOUND_MOVE_ENTITY_POS, GamePacketTypes.CLIENTBOUND_MOVE_ENTITY_POS_ROT,
            GamePacketTypes.CLIENTBOUND_MOVE_ENTITY_ROT, GamePacketTypes.CLIENTBOUND_ENTITY_POSITION_SYNC,
            GamePacketTypes.CLIENTBOUND_TELEPORT_ENTITY, GamePacketTypes.CLIENTBOUND_ROTATE_HEAD,
            GamePacketTypes.CLIENTBOUND_SET_ENTITY_DATA, GamePacketTypes.CLIENTBOUND_SET_EQUIPMENT,
            GamePacketTypes.CLIENTBOUND_SET_PASSENGERS, GamePacketTypes.CLIENTBOUND_SET_ENTITY_LINK,
            GamePacketTypes.CLIENTBOUND_SET_ENTITY_MOTION, GamePacketTypes.CLIENTBOUND_UPDATE_ATTRIBUTES,
            GamePacketTypes.CLIENTBOUND_UPDATE_MOB_EFFECT, GamePacketTypes.CLIENTBOUND_REMOVE_MOB_EFFECT,
            GamePacketTypes.CLIENTBOUND_ENTITY_EVENT, GamePacketTypes.CLIENTBOUND_PROJECTILE_POWER,
            GamePacketTypes.CLIENTBOUND_MOVE_MINECART_ALONG_TRACK, GamePacketTypes.CLIENTBOUND_MOVE_VEHICLE);

    private static final Set<Integer> ABOUT_THE_RACER = ids(
            GamePacketTypes.CLIENTBOUND_SET_ENTITY_DATA, GamePacketTypes.CLIENTBOUND_DAMAGE_EVENT,
            GamePacketTypes.CLIENTBOUND_HURT_ANIMATION, GamePacketTypes.CLIENTBOUND_UPDATE_ATTRIBUTES,
            GamePacketTypes.CLIENTBOUND_UPDATE_MOB_EFFECT, GamePacketTypes.CLIENTBOUND_REMOVE_MOB_EFFECT,
            GamePacketTypes.CLIENTBOUND_SET_ENTITY_MOTION);

    private static final int ENTITY_EVENT = WorldRecording.id(GamePacketTypes.CLIENTBOUND_ENTITY_EVENT);

    private static final Set<Integer> VEHICLE_MOVES = ids(
            GamePacketTypes.CLIENTBOUND_MOVE_ENTITY_POS, GamePacketTypes.CLIENTBOUND_MOVE_ENTITY_POS_ROT,
            GamePacketTypes.CLIENTBOUND_MOVE_ENTITY_ROT, GamePacketTypes.CLIENTBOUND_ENTITY_POSITION_SYNC,
            GamePacketTypes.CLIENTBOUND_TELEPORT_ENTITY, GamePacketTypes.CLIENTBOUND_ROTATE_HEAD,
            GamePacketTypes.CLIENTBOUND_SET_ENTITY_MOTION);

    private static final long STEERED_MS = 1000;

    private long bodyAt;

    private final Map<Integer, int[]> riders = new HashMap<>();
    private static final int LOGIN = WorldRecording.id(GamePacketTypes.CLIENTBOUND_LOGIN);
    private static final int RESPAWN = WorldRecording.id(GamePacketTypes.CLIENTBOUND_RESPAWN);
    private static final int FORGET_CHUNK = WorldRecording.id(GamePacketTypes.CLIENTBOUND_FORGET_LEVEL_CHUNK);
    private static final int ADD_ENTITY = WorldRecording.id(GamePacketTypes.CLIENTBOUND_ADD_ENTITY);
    private static final int ADVANCEMENTS = WorldRecording.id(GamePacketTypes.CLIENTBOUND_UPDATE_ADVANCEMENTS);
    private static final int RECIPES_ADD = WorldRecording.id(GamePacketTypes.CLIENTBOUND_RECIPE_BOOK_ADD);

    private static Set<Integer> ids(net.minecraft.network.protocol.PacketType<?>... types) {
        Set<Integer> out = new HashSet<>();
        for (var type : types) {
            int id = WorldRecording.id(type);
            if (id >= 0) {
                out.add(id);
            } else {
                VivetSpeedrun.LOGGER.warn("Replay packet {} is not a play packet", type);
                STALE.add(String.valueOf(type));
            }
        }
        return Set.copyOf(out);
    }

    static final Set<net.minecraft.network.protocol.PacketType<?>> CONFIG_ALLOWED = Set.of(
            net.minecraft.network.protocol.configuration.ConfigurationPacketTypes.CLIENTBOUND_REGISTRY_DATA,
            net.minecraft.network.protocol.configuration.ConfigurationPacketTypes.CLIENTBOUND_UPDATE_ENABLED_FEATURES,
            net.minecraft.network.protocol.configuration.ConfigurationPacketTypes.CLIENTBOUND_SELECT_KNOWN_PACKS,
            net.minecraft.network.protocol.configuration.ConfigurationPacketTypes.CLIENTBOUND_FINISH_CONFIGURATION,
            CommonPacketTypes.CLIENTBOUND_UPDATE_TAGS);

    static final Set<net.minecraft.network.protocol.PacketType<?>> PLAY_ALLOWED = Set.of(
            GamePacketTypes.CLIENTBOUND_ADD_ENTITY, GamePacketTypes.CLIENTBOUND_ANIMATE,
            GamePacketTypes.CLIENTBOUND_BLOCK_CHANGED_ACK, GamePacketTypes.CLIENTBOUND_BLOCK_DESTRUCTION,
            GamePacketTypes.CLIENTBOUND_BLOCK_ENTITY_DATA, GamePacketTypes.CLIENTBOUND_BLOCK_EVENT,
            GamePacketTypes.CLIENTBOUND_BLOCK_UPDATE, GamePacketTypes.CLIENTBOUND_BOSS_EVENT,
            GamePacketTypes.CLIENTBOUND_CHANGE_DIFFICULTY, GamePacketTypes.CLIENTBOUND_CHUNK_BATCH_FINISHED,
            GamePacketTypes.CLIENTBOUND_CHUNK_BATCH_START, GamePacketTypes.CLIENTBOUND_CHUNKS_BIOMES,
            GamePacketTypes.CLIENTBOUND_CLEAR_TITLES, GamePacketTypes.CLIENTBOUND_CONTAINER_CLOSE,
            GamePacketTypes.CLIENTBOUND_CONTAINER_SET_CONTENT, GamePacketTypes.CLIENTBOUND_CONTAINER_SET_DATA,
            GamePacketTypes.CLIENTBOUND_CONTAINER_SET_SLOT, GamePacketTypes.CLIENTBOUND_COOLDOWN,
            GamePacketTypes.CLIENTBOUND_DAMAGE_EVENT, GamePacketTypes.CLIENTBOUND_ENTITY_EVENT,
            GamePacketTypes.CLIENTBOUND_ENTITY_POSITION_SYNC, GamePacketTypes.CLIENTBOUND_EXPLODE,
            GamePacketTypes.CLIENTBOUND_ADD_TRANSIENT_BLOCK, GamePacketTypes.CLIENTBOUND_FORGET_LEVEL_CHUNK,
            GamePacketTypes.CLIENTBOUND_GAME_EVENT, GamePacketTypes.CLIENTBOUND_HURT_ANIMATION,
            GamePacketTypes.CLIENTBOUND_INITIALIZE_BORDER, GamePacketTypes.CLIENTBOUND_LEVEL_CHUNK_WITH_LIGHT,
            GamePacketTypes.CLIENTBOUND_LEVEL_EVENT, GamePacketTypes.CLIENTBOUND_LEVEL_PARTICLES,
            GamePacketTypes.CLIENTBOUND_LIGHT_UPDATE, GamePacketTypes.CLIENTBOUND_LOGIN,
            GamePacketTypes.CLIENTBOUND_MAP_ITEM_DATA, GamePacketTypes.CLIENTBOUND_MOVE_ENTITY_POS,
            GamePacketTypes.CLIENTBOUND_MOVE_ENTITY_POS_ROT, GamePacketTypes.CLIENTBOUND_MOVE_MINECART_ALONG_TRACK,
            GamePacketTypes.CLIENTBOUND_MOVE_ENTITY_ROT, GamePacketTypes.CLIENTBOUND_MOVE_VEHICLE,
            GamePacketTypes.CLIENTBOUND_OPEN_SCREEN, GamePacketTypes.CLIENTBOUND_GAME_RULE_VALUES,
            GamePacketTypes.CLIENTBOUND_PLAYER_INFO_REMOVE, GamePacketTypes.CLIENTBOUND_PLAYER_INFO_UPDATE,
            GamePacketTypes.CLIENTBOUND_PLAYER_POSITION, GamePacketTypes.CLIENTBOUND_RECIPE_BOOK_ADD,
            GamePacketTypes.CLIENTBOUND_RECIPE_BOOK_REMOVE, GamePacketTypes.CLIENTBOUND_RECIPE_BOOK_SETTINGS,
            GamePacketTypes.CLIENTBOUND_REMOVE_ENTITIES, GamePacketTypes.CLIENTBOUND_REMOVE_MOB_EFFECT,
            GamePacketTypes.CLIENTBOUND_RESPAWN, GamePacketTypes.CLIENTBOUND_ROTATE_HEAD,
            GamePacketTypes.CLIENTBOUND_SECTION_BLOCKS_UPDATE, GamePacketTypes.CLIENTBOUND_SET_ACTION_BAR_TEXT,
            GamePacketTypes.CLIENTBOUND_SET_BORDER_CENTER, GamePacketTypes.CLIENTBOUND_SET_BORDER_LERP_SIZE,
            GamePacketTypes.CLIENTBOUND_SET_BORDER_SIZE, GamePacketTypes.CLIENTBOUND_SET_BORDER_WARNING_DELAY,
            GamePacketTypes.CLIENTBOUND_SET_BORDER_WARNING_DISTANCE, GamePacketTypes.CLIENTBOUND_SET_CHUNK_CACHE_CENTER,
            GamePacketTypes.CLIENTBOUND_SET_CHUNK_CACHE_RADIUS, GamePacketTypes.CLIENTBOUND_SET_DEFAULT_SPAWN_POSITION,
            GamePacketTypes.CLIENTBOUND_SET_DISPLAY_OBJECTIVE, GamePacketTypes.CLIENTBOUND_SET_ENTITY_DATA,
            GamePacketTypes.CLIENTBOUND_SET_ENTITY_LINK, GamePacketTypes.CLIENTBOUND_SET_ENTITY_MOTION,
            GamePacketTypes.CLIENTBOUND_SET_EQUIPMENT, GamePacketTypes.CLIENTBOUND_SET_EXPERIENCE,
            GamePacketTypes.CLIENTBOUND_SET_HEALTH, GamePacketTypes.CLIENTBOUND_SET_HELD_SLOT,
            GamePacketTypes.CLIENTBOUND_SET_OBJECTIVE, GamePacketTypes.CLIENTBOUND_SET_PASSENGERS,
            GamePacketTypes.CLIENTBOUND_SET_PLAYER_TEAM, GamePacketTypes.CLIENTBOUND_SET_SCORE,
            GamePacketTypes.CLIENTBOUND_SET_SIMULATION_DISTANCE, GamePacketTypes.CLIENTBOUND_SET_SUBTITLE_TEXT,
            GamePacketTypes.CLIENTBOUND_SET_TIME, GamePacketTypes.CLIENTBOUND_SET_TITLE_TEXT,
            GamePacketTypes.CLIENTBOUND_SET_TITLES_ANIMATION, GamePacketTypes.CLIENTBOUND_SOUND_ENTITY,
            GamePacketTypes.CLIENTBOUND_SOUND, GamePacketTypes.CLIENTBOUND_STOP_SOUND,
            GamePacketTypes.CLIENTBOUND_SWING_ANIMATION, GamePacketTypes.CLIENTBOUND_SYSTEM_CHAT,
            GamePacketTypes.CLIENTBOUND_TAB_LIST, GamePacketTypes.CLIENTBOUND_TAKE_ITEM_ENTITY,
            GamePacketTypes.CLIENTBOUND_TELEPORT_ENTITY, GamePacketTypes.CLIENTBOUND_UPDATE_ADVANCEMENTS,
            GamePacketTypes.CLIENTBOUND_UPDATE_ATTRIBUTES, GamePacketTypes.CLIENTBOUND_UPDATE_MOB_EFFECT,
            GamePacketTypes.CLIENTBOUND_UPDATE_RECIPES, GamePacketTypes.CLIENTBOUND_PROJECTILE_POWER,
            GamePacketTypes.CLIENTBOUND_WAYPOINT, GamePacketTypes.CLIENTBOUND_RESET_SCORE,
            GamePacketTypes.CLIENTBOUND_SET_CURSOR_ITEM, GamePacketTypes.CLIENTBOUND_SET_PLAYER_INVENTORY,

            GamePacketTypes.CLIENTBOUND_MOUNT_SCREEN_OPEN, GamePacketTypes.CLIENTBOUND_PLACE_GHOST_RECIPE,
            GamePacketTypes.CLIENTBOUND_MERCHANT_OFFERS,
            CommonPacketTypes.CLIENTBOUND_UPDATE_TAGS);

    private static int refusedPackets;

    private static boolean allowed(Packet<?> packet) {
        var type = packet.type();
        if (CONFIG_ALLOWED.contains(type) || PLAY_ALLOWED.contains(type)) return true;
        if (refusedPackets++ < 20) {
            VivetSpeedrun.LOGGER.info("Replay packet skipped: {}", type);
        }
        return false;
    }

    private static boolean typed(net.minecraft.network.chat.Component line) {
        return line != null
                && line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents words
                && ("chat.type.text".equals(words.getKey()) || "chat.type.announcement".equals(words.getKey()));
    }

    private static net.minecraft.network.chat.Component harmless(net.minecraft.network.chat.Component line) {
        net.minecraft.network.chat.MutableComponent out = net.minecraft.network.chat.Component.empty();
        line.visit((style, text) -> {
            out.append(net.minecraft.network.chat.Component.literal(text).withStyle(
                    style.withClickEvent(null).withHoverEvent(null).withInsertion(null)));
            return Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return out;
    }

    private static final class Racer {
        final int index;
        final LoadedReplay.View view;
        final WorldRecording world;
        final GameProfile profile;

        final Set<Integer> ids = new HashSet<>();

        ReplayBody holder;

        ReplayBody body;

        int bodyDimension = -1;
        int bodyCursor;

        BlockPos mining;
        int miningTicks;

        int swungAt = Integer.MIN_VALUE / 2;

        ReplayContainer.Pointer pointer = ReplayContainer.Pointer.CLOSED;

        net.minecraft.network.chat.Component containerTitle;

        com.vivet.speedrun.net.Payloads.Book book;

        int bookPick = -1;

        com.vivet.speedrun.net.Payloads.Drag drag;

        int rideJump;

        com.vivet.speedrun.net.Payloads.OwnScreen ownScreen;

        final Map<Integer, Long> steered = new HashMap<>();

        Racer(int index, LoadedReplay.View view, WorldRecording world) {
            this.index = index;
            this.view = view;
            this.world = world;
            this.profile = profileOf(view, world);
            if (world != null) {
                int addEntity = ADD_ENTITY;
                for (ReplayContainer.Record record : world.body) {
                    if (ReplayContainer.packetId(record.frame()) == addEntity) {
                        int id = varintAfterId(record.frame());
                        if (id != Integer.MIN_VALUE) this.ids.add(id);
                    }
                }
            }
        }

        String name() {
            return this.view.name();
        }
    }

    private final Minecraft minecraft;
    private final LoadedReplay replay;
    private final List<Racer> racers = new ArrayList<>();
    private final Screen returnTo;

    private int watching;
    private EmbeddedChannel channel;
    private Connection connection;
    private final Router router = new Router();

    private final ReplayHands hands = new ReplayHands();

    private int cursor;
    private long wall;
    private final long durationMs;

    private int speedIndex;
    private boolean paused;
    private boolean ended;

    private boolean fast;

    private boolean rebuilding;

    private boolean settled;

    private boolean placeCamera;

    private boolean ridingAcross;

    private boolean joined;

    private boolean skippingConfig;

    private final Map<Integer, ResourceKey<Level>> dimensionAt = new HashMap<>();

    private int deliveringBody = -1;

    private float flySpeed = BASE_FLY;

    private float lastSetSpeed = -1.0F;

    private float lastRate = 1.0F;

    private int ticks;

    private final List<ReplayTrace.Moment> chat = new ArrayList<>();
    private int chatCursor;

    private ReplayPlayback(Minecraft minecraft, LoadedReplay replay, List<WorldRecording> worlds, Screen returnTo) {
        this.minecraft = minecraft;
        this.replay = replay;
        this.returnTo = returnTo;
        long duration = 1;
        for (int i = 0; i < replay.views().size(); i++) {
            WorldRecording world = i < worlds.size() ? worlds.get(i) : null;
            this.racers.add(new Racer(i, replay.views().get(i), world));
            if (world != null) duration = Math.max(duration, world.meta.durationMs());
        }
        this.durationMs = duration;
        gatherChat();
    }

    private void gatherChat() {
        for (Racer racer : this.racers) {
            for (ReplayTrace.Moment moment : racer.view.trace().moments()) {
                if (moment.type() != com.vivet.speedrun.replay.ReplayMoment.CHAT) continue;
                if (moment.text() == null || moment.text().isBlank()) continue;
                boolean twice = false;
                for (ReplayTrace.Moment kept : this.chat) {
                    if (java.util.Objects.equals(kept.who(), moment.who()) && kept.text().equals(moment.text())
                            && Math.abs(kept.centis() - moment.centis()) <= 100) {
                        twice = true;
                        break;
                    }
                }
                if (!twice) this.chat.add(moment);
            }
        }
        this.chat.sort(java.util.Comparator.comparingInt(ReplayTrace.Moment::centis));
    }

    private void sayChatTo(long target, boolean jumping) {
        int reached = watched().world.meta.raceCentis(Math.max(0, target));
        while (this.chatCursor < this.chat.size() && this.chat.get(this.chatCursor).centis() <= reached) {
            ReplayTrace.Moment line = this.chat.get(this.chatCursor++);
            if (!jumping) say(line);
        }
    }

    private void say(ReplayTrace.Moment line) {
        String who = line.who() == null || line.who().isEmpty() ? "?" : line.who();
        String uuid = null;
        for (Racer racer : this.racers) {
            if (who.equalsIgnoreCase(racer.name())) uuid = racer.view.uuid();
        }
        com.vivet.speedrun.client.ui.NameLabel label = null;
        if (uuid != null) {
            var profile = com.vivet.speedrun.p2p.Profiles.of(uuid);
            if (profile != null) label = com.vivet.speedrun.client.ui.NameLabel.byId(profile.label);
            if (label == null) label = com.vivet.speedrun.client.ui.NameLabel.byId(com.vivet.speedrun.p2p.Hub.labelOf(uuid));
        }
        if (label == null) label = com.vivet.speedrun.client.ui.NameLabel.of(who);

        net.minecraft.network.chat.Component name = label == null
                ? net.minecraft.network.chat.Component.literal(who)
                : com.vivet.speedrun.client.ui.NameLabel.decorate(who, label);
        this.minecraft.gui.hud.getChat().addClientSystemMessage(net.minecraft.network.chat.Component.empty()
                .withStyle(net.minecraft.ChatFormatting.WHITE)
                .append(name)
                .append(net.minecraft.network.chat.Component.literal(": " + line.text())));
    }

    public static boolean isActive() {
        return active != null;
    }

    public static ReplayPlayback get() {
        return active;
    }

    public static boolean open(Minecraft minecraft, LoadedReplay replay, List<WorldRecording> worlds,
                               int watch, int startCentis, Screen returnTo) {
        if (active != null || minecraft.level != null) {
            closeWorlds(worlds);
            return false;
        }
        ReplayPlayback playback = new ReplayPlayback(minecraft, replay, worlds, returnTo);
        int first = -1;
        for (int i = 0; i < playback.racers.size(); i++) {
            int at = Math.floorMod(watch + i, playback.racers.size());
            if (playback.racers.get(at).world != null) {
                first = at;
                break;
            }
        }
        if (first < 0) {
            closeWorlds(worlds);
            return false;
        }
        playback.watching = first;

        active = playback;
        try {
            playback.connect();
            WorldRecording world = playback.watched().world;
            long start = world.meta.wallMsFor(Math.max(0, startCentis));
            playback.playTo(start, true);
            playback.fast = false;
            playback.placeViewerOnRacer();
            playback.applySpeed();
            Cues.replayOpened();
            return true;
        } catch (RuntimeException error) {
            VivetSpeedrun.LOGGER.error("Could not open replay", error);
            active = null;
            playback.closeQuietly();
            closeWorlds(worlds);
            return false;
        }
    }

    private static void closeWorlds(List<WorldRecording> worlds) {
        if (worlds == null) return;
        for (WorldRecording world : worlds) {
            if (world == null) continue;
            try {
                world.close();
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void connect() {
        this.connection = new Connection(PacketFlow.CLIENTBOUND);
        Connection handler = this.connection;
        Router routing = this.router;
        this.channel = new EmbeddedChannel(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel channel) {
                Connection.configureInMemoryPipeline(channel.pipeline(), PacketFlow.CLIENTBOUND);
                handler.configurePacketHandler(channel.pipeline());
                channel.pipeline().addBefore("packet_handler", "vivet_replay_router", routing);

                channel.pipeline().addAfter("encoder", "vivet_replay_sink", new ChannelOutboundHandlerAdapter() {
                    @Override
                    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                        if (msg instanceof Packet<?>
                                || msg instanceof net.minecraft.network.UnconfiguredPipelineHandler.OutboundConfigurationTask) {
                            ReferenceCountUtil.release(msg);
                            promise.setSuccess();
                            return;
                        }
                        ctx.write(msg, promise);
                    }
                });
            }
        });

        GameProfile viewer = new GameProfile(UUID.randomUUID(), this.minecraft.getUser().getName());
        CommonListenerCookie cookie = new CommonListenerCookie(
                new LevelLoadTracker(),
                viewer,
                new WorldSessionTelemetryManager(TelemetryEventSender.DISABLED, false, null, null, UUID.randomUUID()),
                ClientRegistryLayer.createRegistryAccess().compositeAccess(),
                FeatureFlags.DEFAULT_FLAGS,
                null,
                null,
                this.returnTo,
                new HashMap<>(),
                null,
                Map.of(),
                ServerLinks.EMPTY,
                new HashMap<>(),
                true);
        this.connection.setupInboundProtocol(ConfigurationProtocols.CLIENTBOUND,
                new ClientConfigurationPacketListenerImpl(this.minecraft, this.connection, cookie));
        this.connection.setupOutboundProtocol(ConfigurationProtocols.SERVERBOUND);

        WorldRecording world = watched().world;
        int index = 0;
        while (index < world.count() && world.kind(index) == ReplayContainer.WORLD_CONFIG) {
            deliver(world.frame(index));
            index++;
        }
        this.cursor = index;
        this.wall = Long.MIN_VALUE;
    }

    public static void tick(Minecraft minecraft) {
        ReplayPlayback playback = active;
        if (playback == null) return;
        try {
            playback.tickOnce();
        } catch (RuntimeException | StackOverflowError error) {
            VivetSpeedrun.LOGGER.error("Replay error", error);
        }
    }

    private void tickOnce() {
        ClientLevel level = this.minecraft.level;
        if (level == null || this.minecraft.player == null) return;
        this.ticks++;
        Screen screen = this.minecraft.gui.screen();
        if (this.joined && !(screen instanceof net.minecraft.client.gui.screens.LevelLoadingScreen) && screen != this.returnTo) {
            this.settled = true;
        }

        holdCamera();
        followRacer();
        steeredFaceTheirRider();
        healBodies();
        syncHolders();
        ReplayScreens.follow(this);

        boolean stopped = this.paused || this.ended
                || this.minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.PauseScreen;
        level.tickRateManager().setFrozen(stopped);
        if (stopped) return;

        long next = this.wall + TICK_MS;
        if (next >= this.durationMs) {
            next = this.durationMs;
            this.ended = true;
            Cues.replayEnded();
        }
        playTo(next, false);
        miningEffects();
        if (this.minecraft.getCameraEntity() instanceof ReplayBody ridden) this.hands.tick(ridden);
    }

    private void playTo(long target, boolean jumping) {
        WorldRecording world = watched().world;
        int end = world.indexAt(target + 1);
        this.fast = jumping;

        Map<Long, Integer> lastForget = jumping ? lastForgets(this.cursor, end) : Map.of();

        for (int i = this.cursor; i < end; i++) {
            playWorld(world, i, jumping, lastForget);
        }
        this.cursor = end;
        this.wall = target;
        playBodies(target);
        sayChatTo(target, jumping);
        this.fast = false;
    }

    private void playWorld(WorldRecording world, int i, boolean jumping, Map<Long, Integer> lastForget) {
        int kind = world.kind(i);
        if (kind == ReplayContainer.WORLD_RECONNECT) {
            this.skippingConfig = true;
            return;
        }
        if (kind == ReplayContainer.WORLD_POINTER) {
            ReplayContainer.Pointer pointer = world.pointer(i);
            if (pointer != null) watched().pointer = pointer;
            return;
        }
        if (kind == ReplayContainer.WORLD_OWN_SCREEN) {
            com.vivet.speedrun.net.Payloads.OwnScreen screen = ReplayContainer.ownScreen(world.frame(i));
            if (screen != null) watched().ownScreen = screen;
            return;
        }
        if (kind == ReplayContainer.WORLD_RIDE_JUMP) {
            Integer charge = ReplayContainer.rideJump(world.frame(i));
            if (charge != null) watched().rideJump = charge;
            return;
        }
        if (kind == ReplayContainer.WORLD_DRAG) {
            com.vivet.speedrun.net.Payloads.Drag drag = ReplayContainer.drag(world.frame(i));
            if (drag != null) watched().drag = drag;
            return;
        }
        if (kind == ReplayContainer.WORLD_BOOK_PICK) {
            Integer pick = ReplayContainer.bookPick(world.frame(i));
            if (pick != null) watched().bookPick = pick;
            return;
        }
        if (kind == ReplayContainer.WORLD_BOOK) {
            com.vivet.speedrun.net.Payloads.Book book = ReplayContainer.book(world.frame(i));
            if (book != null) watched().book = book;
            return;
        }
        if (kind != ReplayContainer.WORLD_PLAY) return;

        int id = world.packetId(i);
        if (this.skippingConfig) {
            if (id != LOGIN) return;
            this.skippingConfig = false;
        }
        if (DROPPED.contains(id)) return;
        if (jumping && EFFECTS.contains(id)) return;

        long chunk = world.chunk(i);
        if (jumping && chunk != WorldRecording.NO_CHUNK) {
            Integer forgotten = lastForget.get(chunk);
            if (forgotten != null && forgotten > i) return;
        }

        byte[] frame = world.frame(i);
        if (frame == null) return;
        if (aboutARacer(id, frame, world.wall(i))) return;
        deliver(frame);
    }

    private boolean aboutARacer(int id, byte[] frame, long at) {
        if (VEHICLE_MOVES.contains(id)) {
            int vehicle = varintAfterId(frame);
            for (Racer racer : this.racers) {
                Long steeredAt = racer.steered.get(vehicle);
                if (steeredAt != null && at - steeredAt <= STEERED_MS && at >= steeredAt - STEERED_MS) return true;
            }
        }
        int target;
        if (ABOUT_THE_RACER.contains(id)) target = varintAfterId(frame);
        else if (id == ENTITY_EVENT) target = intAfterId(frame);
        else return false;
        for (Racer racer : this.racers) if (racer.ids.contains(target)) return true;
        return false;
    }

    private void playBodies(long target) {
        int shown = dimensionShown();
        for (Racer racer : this.racers) {
            if (racer.world == null) continue;
            List<ReplayContainer.Record> track = racer.world.body;
            long start = racer.world.meta.startedAt();
            while (racer.bodyCursor < track.size() && track.get(racer.bodyCursor).wall() - start <= target) {
                ReplayContainer.Record record = track.get(racer.bodyCursor++);
                int id = ReplayContainer.packetId(record.frame());
                boolean remove = id == WorldRecording.id(GamePacketTypes.CLIENTBOUND_REMOVE_ENTITIES);
                racer.bodyDimension = remove ? -1 : record.kind();
                if (record.kind() != shown && !remove) {
                    removeBody(racer);
                    continue;
                }
                if (this.fast && EFFECTS.contains(id)) continue;
                this.bodyAt = record.wall() - start;
                deliverBody(racer, record.frame());
            }
        }
    }

    private void rebuildBodies() {
        int shown = dimensionShown();
        for (Racer racer : this.racers) {
            removeBody(racer);
            if (racer.world == null) continue;
            List<ReplayContainer.Record> track = racer.world.body;
            long start = racer.world.meta.startedAt();
            int end = 0;
            while (end < track.size() && track.get(end).wall() - start <= this.wall) end++;
            racer.bodyCursor = end;

            int from = -1;
            for (int i = end - 1; i >= 0; i--) {
                int id = ReplayContainer.packetId(track.get(i).frame());
                if (id == WorldRecording.id(GamePacketTypes.CLIENTBOUND_REMOVE_ENTITIES)) break;
                if (id == ADD_ENTITY) {
                    from = i;
                    break;
                }
            }
            racer.bodyDimension = from < 0 ? -1 : track.get(from).kind();
            if (from < 0 || track.get(from).kind() != shown) continue;
            boolean was = this.fast;
            this.fast = true;
            for (int i = from; i < end; i++) {
                int id = ReplayContainer.packetId(track.get(i).frame());
                if (EFFECTS.contains(id)) continue;
                deliverBody(racer, track.get(i).frame());
            }
            this.fast = was;
            if (racer.body != null) settle(racer.body);
        }
    }

    private static void settle(ReplayBody body) {
        net.minecraft.world.entity.InterpolationHandler glide = body.getInterpolation();
        if (glide == null || !glide.hasActiveInterpolation()) return;
        net.minecraft.core.PositionAndRotation target = glide.target();
        Vec3 at = target.position();
        float yRot = target.yRot();
        float xRot = target.xRot();
        glide.cancel();
        body.snapTo(at.x, at.y, at.z, yRot, xRot);
        body.setOldPosAndRot();
    }

    public void seek(long target) {
        target = Math.max(0, Math.min(this.durationMs, target));
        this.ended = target >= this.durationMs;
        if (target >= this.wall) {
            playTo(target, true);
            afterJump(false);
            return;
        }
        rebuildTo(target);
    }

    public void seekCentis(int centis) {
        seek(watched().world.meta.wallMsFor(centis));
    }

    public void skip(int seconds) {
        seek(this.wall + seconds * 1000L);
    }

    private void rebuildTo(long target) {
        WorldRecording world = watched().world;
        int end = world.indexAt(target + 1);
        int keyframe = lastWorldChange(end);
        boolean wasPov = isRidingWatched();

        this.rebuilding = true;
        try {
            this.minecraft.gui.hud.getChat().clearMessages(false);
            this.chatCursor = 0;
            resetLevel();
            this.cursor = Math.max(0, keyframe);
            this.skippingConfig = false;
            this.wall = world.wall(this.cursor);
            if (keyframe >= 0 && world.packetId(keyframe) == LOGIN) {
                byte[] frame = world.frame(keyframe);
                this.router.loginAsRespawn = true;
                deliver(frame);
                this.cursor = keyframe + 1;
            }
            playTo(target, true);
        } finally {
            this.rebuilding = false;
        }
        afterJump(true);
        if (wasPov && watched().body != null) this.minecraft.setCameraEntity(watched().body);
    }

    private int lastWorldChange(int end) {
        WorldRecording world = watched().world;
        ResourceKey<Level> previous = null;
        int found = -1;
        for (int i = 0; i < end; i++) {
            int id = world.packetId(i);
            if (world.kind(i) != ReplayContainer.WORLD_PLAY || (id != LOGIN && id != RESPAWN)) continue;
            ResourceKey<Level> dimension = dimensionOf(world, i);
            if (id == LOGIN || dimension == null || !dimension.equals(previous)) found = i;
            previous = dimension;
        }
        return found;
    }

    private ResourceKey<Level> dimensionOf(WorldRecording world, int index) {
        if (this.dimensionAt.containsKey(index)) return this.dimensionAt.get(index);
        ResourceKey<Level> found = null;
        Packet<?> packet = decode(world.frame(index));
        if (packet instanceof ClientboundLoginPacket login) found = login.commonPlayerSpawnInfo().dimension();
        if (packet instanceof ClientboundRespawnPacket respawn) found = respawn.commonPlayerSpawnInfo().dimension();
        this.dimensionAt.put(index, found);
        return found;
    }

    private void resetLevel() {
        this.riders.clear();
        ClientLevel level = this.minecraft.level;
        if (level == null) return;
        for (Racer racer : this.racers) {
            racer.body = null;
            racer.mining = null;

            racer.pointer = ReplayContainer.Pointer.CLOSED;
            racer.containerTitle = null;
            racer.book = null;
            racer.bookPick = -1;
            racer.drag = null;
            racer.rideJump = 0;
            racer.ownScreen = null;
            racer.steered.clear();
            if (racer.holder != null) {
                racer.holder.containerMenu = racer.holder.inventoryMenu;
                racer.holder.inventoryMenu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
            }
        }
        handle(new ClientboundRespawnPacket(new CommonPlayerSpawnInfo(level.dimensionTypeRegistration(),
                RESET_LEVEL, 0L, GameType.SPECTATOR, Optional.of(GameType.SPECTATOR), false, false, Optional.empty(), 0,
                level.getSeaLevel()), (byte) 0));
    }

    private void afterJump(boolean rebuilt) {
        rebuildBodies();
        ReplayScreens.containerChanged();
        this.minecraft.gui.toastManager().clear();
        if (rebuilt) placeViewerOnRacer();
        applySpeed();
    }

    private Map<Long, Integer> lastForgets(int from, int end) {
        WorldRecording world = watched().world;
        Map<Long, Integer> out = new HashMap<>();
        for (int i = from; i < end; i++) {
            if (world.packetId(i) == FORGET_CHUNK && world.chunk(i) != WorldRecording.NO_CHUNK) {
                out.put(world.chunk(i), i);
            }
        }
        return out;
    }

    public boolean canSwitch() {
        int count = 0;
        for (Racer racer : this.racers) if (racer.world != null) count++;
        return count > 1;
    }

    public void firstPersonOrSwitch() {
        ReplayBody body = watched().body;
        if (body != null && this.minecraft.getCameraEntity() != body) {
            this.minecraft.setCameraEntity(body);
            Cues.confirm();
            return;
        }
        switchPlayer();
    }

    public void switchPlayer() {
        if (!canSwitch()) {
            Cues.deny();
            return;
        }
        int next = this.watching;
        do {
            next = Math.floorMod(next + 1, this.racers.size());
        } while (this.racers.get(next).world == null);
        switchTo(next, this.minecraft.getCameraEntity() instanceof ReplayBody);
    }

    private void switchTo(int next, boolean ride) {
        long at = this.wall;
        this.watching = next;
        this.dimensionAt.clear();
        this.rebuilding = true;
        try {
            this.minecraft.gui.hud.getChat().clearMessages(false);
            this.chatCursor = 0;
            resetLevel();
            this.skippingConfig = false;
            WorldRecording world = watched().world;
            int end = world.indexAt(at + 1);
            int keyframe = lastWorldChange(end);
            this.cursor = Math.max(0, keyframe);
            this.wall = keyframe < 0 ? 0 : world.wall(keyframe);
            if (keyframe >= 0 && world.packetId(keyframe) == LOGIN) {
                this.router.loginAsRespawn = true;
                deliver(world.frame(keyframe));
                this.cursor = keyframe + 1;
            }
            playTo(at, true);
        } finally {
            this.rebuilding = false;
        }
        afterJump(true);
        if (ride && watched().body != null) this.minecraft.setCameraEntity(watched().body);
        Cues.watchSwitched();
    }

    public void placeViewerOnRacer() {
        LocalPlayer camera = this.minecraft.player;
        ReplayBody body = watched().body;
        if (camera == null || body == null) return;
        if (this.minecraft.getCameraEntity() != camera) stopSpectating();
        double yaw = Math.toRadians(body.getYRot());
        double x = body.getX() + Math.sin(yaw) * 2.5;
        double z = body.getZ() - Math.cos(yaw) * 2.5;
        camera.snapTo(x, body.getY() + 1.0, z, body.getYRot(), 15.0F);
        camera.setDeltaMovement(Vec3.ZERO);
    }

    public void teleportToRacer() {
        if (watched().body == null) {
            Cues.deny();
            return;
        }
        placeViewerOnRacer();
        Cues.confirm();
    }

    public void spectate(Entity entity) {
        if (entity == null || entity == this.minecraft.player) return;
        for (Racer racer : this.racers) {
            if (racer.body == entity && racer.index != this.watching && racer.world != null) {
                switchTo(racer.index, true);
                return;
            }
        }
        this.minecraft.setCameraEntity(entity);
        Cues.confirm();
    }

    private boolean isRidingWatched() {
        ReplayBody body = watched().body;
        return body != null && this.minecraft.getCameraEntity() == body;
    }

    @Override
    public boolean ridingWatched() {
        return isRidingWatched();
    }

    @Override
    public boolean watchedHasScreenOpen() {
        Racer racer = watched();
        if (racer.pointer.open()) return true;
        return racer.holder != null && racer.holder.containerMenu != racer.holder.inventoryMenu;
    }

    @Override
    public boolean ownKey(net.minecraft.client.input.KeyEvent event) {
        if (!com.vivet.speedrun.client.VivetSpeedrunClient.REPLAY_CONTROLS.matches(event)) return false;
        this.minecraft.gui.setScreen(new com.vivet.speedrun.client.screen.ReplayControlsScreen());
        return true;
    }

    public static net.minecraft.world.entity.player.Player ridden() {
        ReplayPlayback playback = active;
        if (playback == null) return SpectateEyes.ridden();
        return playback.minecraft.getCameraEntity() instanceof ReplayBody body ? body : null;
    }

    public static void extractHands(net.minecraft.world.entity.player.Player body, float partialTick,
                                    net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState state) {
        ReplayPlayback playback = active;
        if (playback != null) playback.hands.extract(body, partialTick, state);
        else SpectateEyes.extractHands(body, partialTick, state);
    }

    private void stepBehind() {
        LocalPlayer camera = this.minecraft.player;
        if (camera == null) return;
        double yaw = Math.toRadians(camera.getYRot());
        camera.snapTo(camera.getX() + Math.sin(yaw) * 2.5, camera.getY() + 1.0, camera.getZ() - Math.cos(yaw) * 2.5,
                camera.getYRot(), 15.0F);
        camera.setDeltaMovement(Vec3.ZERO);
    }

    public void stopSpectating() {
        if (this.minecraft.player != null) this.minecraft.setCameraEntity(this.minecraft.player);
    }

    private void holdCamera() {
        LocalPlayer camera = this.minecraft.player;
        if (camera == null) return;
        if (this.minecraft.gameMode != null && this.minecraft.gameMode.getPlayerMode() != GameType.SPECTATOR) {
            this.minecraft.gameMode.setLocalMode(GameType.SPECTATOR, GameType.SPECTATOR);
        }
        if (!camera.isSpectator()) {
            ((com.vivet.speedrun.mixin.AbstractClientPlayerAccessor) (Object) camera).vivet$setPlayerInfo(
                    new PlayerInfo(camera.getGameProfile(), false) {
                        @Override
                        public GameType getGameMode() {
                            return GameType.SPECTATOR;
                        }
                    });
        }
        var abilities = camera.getAbilities();
        abilities.flying = true;
        abilities.mayfly = true;

        float current = abilities.getFlyingSpeed();
        if (this.lastSetSpeed >= 0 && current != this.lastSetSpeed) {
            this.flySpeed = Math.max(0.005F, Math.min(BASE_FLY * 16, this.flySpeed + (current - this.lastSetSpeed)));
        }
        ClientLevel level = this.minecraft.level;
        float rate = level != null && level.tickRateManager().runsNormally() ? speed() : 1.0F;

        if (rate != this.lastRate) camera.setDeltaMovement(camera.getDeltaMovement().scale(this.lastRate / rate));
        this.lastRate = rate;
        this.lastSetSpeed = this.flySpeed / rate;
        abilities.setFlyingSpeed(this.lastSetSpeed);
    }

    public int flySpeed() {
        int best = FLY_SPEEDS[0];
        for (int speed : FLY_SPEEDS) {
            if (Math.abs(this.flySpeed - BASE_FLY * speed) < Math.abs(this.flySpeed - BASE_FLY * best)) best = speed;
        }
        return Math.abs(this.flySpeed - BASE_FLY * best) < 0.001F ? best : 0;
    }

    public void setFlySpeed(int multiple) {
        this.flySpeed = BASE_FLY * Math.max(1, multiple);
    }

    private int healedAt = Integer.MIN_VALUE / 2;

    private int healWait = 20;

    private void healBodies() {
        if (this.fast || this.rebuilding || this.ticks - this.healedAt < this.healWait) return;
        int shown = dimensionShown();
        for (Racer racer : this.racers) {
            if (racer.world == null || racer.bodyDimension != shown) continue;
            if (racer.body != null && !racer.body.isRemoved()) continue;

            this.healWait = this.ticks - this.healedAt <= this.healWait + 1 ? Math.min(600, this.healWait * 2) : 20;
            this.healedAt = this.ticks;
            rebuildBodies();
            return;
        }
    }

    private void steeredFaceTheirRider() {
        for (Racer racer : this.racers) {
            ReplayBody body = racer.body;
            if (body == null || !(body.getVehicle() instanceof net.minecraft.world.entity.LivingEntity mount)) continue;
            if (mount.getControllingPassenger() != body) continue;
            float yaw = body.getYHeadRot();
            mount.setYRot(yaw);
            mount.setYBodyRot(yaw);
            mount.setYHeadRot(yaw);

            mount.positionRider(body);
        }
    }

    private void followRacer() {
        Entity camera = this.minecraft.getCameraEntity();
        LocalPlayer player = this.minecraft.player;
        if (camera == null || player == null || camera == player) return;
        if (camera.isRemoved() || camera.level() != player.level()) {
            stopSpectating();
            return;
        }
        if (this.minecraft.options.keyShift.isDown()) {
            stopSpectating();
            return;
        }
        player.setPos(camera.getX(), camera.getY(), camera.getZ());
        player.setDeltaMovement(Vec3.ZERO);
    }

    public static net.minecraft.world.entity.player.Player hudPlayer() {
        ReplayPlayback playback = active;
        if (playback == null) return SpectateEyes.hudPlayer();
        return playback.watched().holder;
    }

    private static ReplayBody holderFor(Racer racer, ClientLevel level) {
        ReplayBody holder = new ReplayBody(level, racer.profile);
        holder.setId(CAMERA_ID - 1 - racer.index);
        return holder;
    }

    private void syncHolders() {
        ClientLevel level = this.minecraft.level;
        for (Racer racer : this.racers) {
            if (racer.holder == null && level != null) racer.holder = holderFor(racer, level);
            if (racer.holder == null) continue;
            if (racer.holder.level() != level && level != null) racer.holder.moveInto(level);
            if (racer.body != null) racer.holder.copyFrom(racer.body);
            Inventory inventory = racer.holder.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                net.minecraft.world.item.ItemStack stack = inventory.getItem(slot);
                if (stack.getPopTime() > 0) stack.setPopTime(stack.getPopTime() - 1);
            }
        }
    }

    private void miningEffects() {
        if (!audible() || this.minecraft.level == null) return;
        for (Racer racer : this.racers) {
            if (racer.mining == null || racer.body == null) continue;

            if (this.ticks - racer.swungAt > 4) continue;
            BlockState state = this.minecraft.level.getBlockState(racer.mining);
            if (state.isAir()) {
                racer.mining = null;
                continue;
            }
            Vec3 eye = racer.body.getEyePosition();
            Direction face = Direction.getApproximateNearest(
                    eye.subtract(Vec3.atCenterOf(racer.mining)));
            this.minecraft.level.addBreakingBlockEffects(racer.mining, face, false);

            if (racer.miningTicks % 4 == 0) {
                SoundType sound = state.getSoundType();
                this.minecraft.level.playLocalSound(racer.mining, sound.getHitSound(),
                        net.minecraft.sounds.SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 8.0F,
                        sound.getPitch() * 0.5F, false);
            }
            racer.miningTicks++;
        }
    }

    public void togglePause() {
        if (this.ended) {
            seek(0);
            this.ended = false;
            this.paused = false;
            return;
        }
        this.paused = !this.paused;
    }

    public boolean isPaused() {
        return this.paused || this.ended;
    }

    public int speed() {
        return SPEEDS[this.speedIndex];
    }

    public void cycleSpeed(int by) {
        this.speedIndex = Math.floorMod(this.speedIndex + by, SPEEDS.length);
        applySpeed();
    }

    private void applySpeed() {
        ClientLevel level = this.minecraft.level;
        if (level != null) level.tickRateManager().setTickRate(20.0F * speed());
    }

    public static float tickMillis() {
        ReplayPlayback playback = active;
        return playback == null ? 50.0F : 50.0F / playback.speed();
    }

    public static boolean audible() {
        ReplayPlayback playback = active;
        return playback == null || (!playback.fast && playback.speed() <= 4);
    }

    public static boolean hidesLoading() {
        return active != null && (active.rebuilding || active.settled);
    }

    public LoadedReplay replay() {
        return this.replay;
    }

    public long wall() {
        return Math.max(0, this.wall);
    }

    public long durationMs() {
        return this.durationMs;
    }

    public int raceCentis() {
        return watched().world.meta.raceCentis(wall());
    }

    public int raceCentisAt(long wallMs) {
        return watched().world.meta.raceCentis(wallMs);
    }

    public String watchedName() {
        return watched().name();
    }

    public String otherName() {
        for (Racer racer : this.racers) {
            if (racer.index != this.watching && racer.world != null) return racer.name();
        }
        return "";
    }

    public boolean away() {
        return watched().body == null && this.wall > 0 && !this.ended;
    }

    public ReplayTrace trace() {
        return watched().view.trace();
    }

    @Override
    public com.vivet.speedrun.net.Payloads.Book book() {
        return watched().book;
    }

    @Override
    public int bookPick() {
        return watched().bookPick;
    }

    @Override
    public com.vivet.speedrun.net.Payloads.Drag drag() {
        return watched().drag;
    }

    @Override
    public com.vivet.speedrun.net.Payloads.OwnScreen ownScreen() {
        return watched().ownScreen;
    }

    public Screen inventoryScreen() {
        Racer racer = watched();
        if (racer.holder == null) return null;
        AbstractContainerMenu open = racer.holder.containerMenu;
        if (open != null && open != racer.holder.inventoryMenu) {
            Screen screen = ReplayScreens.forMenu(open, racer.holder.getInventory(),
                    racer.containerTitle == null ? net.minecraft.network.chat.Component.empty() : racer.containerTitle);
            if (screen != null) return screen;
        }
        return new com.vivet.speedrun.client.screen.ReplayInventoryScreen(racer.holder);
    }

    public static net.minecraft.world.entity.LivingEntity dollFor(net.minecraft.world.entity.player.Player holder) {
        ReplayPlayback playback = active;
        if (playback == null) return SpectateEyes.dollFor(holder);
        for (Racer racer : playback.racers) {
            if (racer.holder == holder && racer.body != null) return racer.body;
        }
        return holder;
    }

    public static ReplayContainer.Pointer pointerOver(AbstractContainerMenu menu) {
        ReplayPlayback playback = active;
        if (playback == null) return SpectateEyes.pointerOver(menu);
        if (menu == null) return null;
        Racer racer = playback.watched();
        if (racer.holder == null || !racer.pointer.open() || racer.holder.containerMenu != menu) return null;
        return racer.pointer;
    }

    public static ReplayContainer.Pointer mirrorPointer() {
        ReplayPlayback playback = active;
        ReplayContainer.Pointer pointer = playback != null ? playback.watched().pointer : SpectateEyes.pointer();
        return pointer != null && pointer.open() ? pointer : null;
    }

    public static net.minecraft.world.entity.PlayerRideableJumping watchedJumpable() {
        net.minecraft.world.entity.player.Player body = ridden();
        if (body == null) return null;
        return body.getVehicle() instanceof net.minecraft.world.entity.PlayerRideableJumping jumping ? jumping : null;
    }

    public static float watchedJumpCharge() {
        ReplayPlayback playback = active;
        int percent = playback != null ? playback.watched().rideJump : SpectateEyes.rideJump();
        return Math.max(0, Math.min(100, percent)) / 100.0F;
    }

    public static net.minecraft.world.entity.player.Player menuOwner(AbstractContainerMenu menu) {
        ReplayPlayback playback = active;
        if (playback == null) return SpectateEyes.menuOwner(menu);
        if (menu == null) return null;
        for (Racer racer : playback.racers) {
            if (racer.holder != null && (racer.holder.inventoryMenu == menu || racer.holder.containerMenu == menu)) {
                return racer.holder;
            }
        }
        return null;
    }

    public static boolean isReplayMenu(AbstractContainerMenu menu) {
        ReplayPlayback playback = active;
        if (playback == null) return SpectateEyes.isMenu(menu);
        if (menu == null) return false;
        for (Racer racer : playback.racers) {
            if (racer.holder != null && (racer.holder.inventoryMenu == menu || racer.holder.containerMenu == menu)) {
                return true;
            }
        }
        return false;
    }

    private Racer watched() {
        return this.racers.get(this.watching);
    }

    public void leave() {
        active = null;
        Screen back = this.returnTo;
        try {
            for (Racer racer : this.racers) {
                if (racer.world != null) racer.world.close();
            }
            this.minecraft.disconnect(back, false);
        } finally {
            closeQuietly();
        }
    }

    public static void forget() {
        ReplayPlayback playback = active;
        if (playback == null) return;
        active = null;
        for (Racer racer : playback.racers) {
            if (racer.world != null) racer.world.close();
        }
        playback.closeQuietly();
    }

    private void closeQuietly() {
        try {
            if (this.channel != null) this.channel.close();
        } catch (RuntimeException ignored) {
        }
    }

    private void deliver(byte[] frame) {
        if (frame == null || frame.length == 0 || this.channel == null) return;
        this.channel.writeInbound(Unpooled.wrappedBuffer(frame));
        this.channel.runPendingTasks();
    }

    private void deliverBody(Racer racer, byte[] frame) {
        this.deliveringBody = racer.index;
        try {
            deliver(frame);
        } finally {
            this.deliveringBody = -1;
        }
    }

    @SuppressWarnings("unchecked")
    private void handle(Packet<?> packet) {
        PacketListener listener = this.connection.getPacketListener();
        if (listener == null) return;
        try {
            ((Packet<PacketListener>) packet).handle(listener);
        } catch (RuntimeException error) {
            VivetSpeedrun.LOGGER.debug("Could not apply replay packet: {}", packet.type(), error);
        }
    }

    private Packet<?> decode(byte[] frame) {
        if (frame == null || !(this.connection.getPacketListener() instanceof net.minecraft.client.multiplayer.ClientPacketListener listener)) {
            return null;
        }
        try {
            var protocol = net.minecraft.network.protocol.game.GameProtocols.CLIENTBOUND_TEMPLATE.bind(
                    net.minecraft.network.RegistryFriendlyByteBuf.decorator(listener.registryAccess()));
            return protocol.codec().decode(Unpooled.wrappedBuffer(frame));
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    private int dimensionShown() {
        ClientLevel level = this.minecraft.level;
        if (level == null) return -1;
        var type = level.dimensionTypeRegistration();
        if (type.is(BuiltinDimensionTypes.NETHER)) return ReplayContainer.BODY_NETHER;
        if (type.is(BuiltinDimensionTypes.END)) return ReplayContainer.BODY_END;
        return ReplayContainer.BODY_OVERWORLD;
    }

    private void removeBody(Racer racer) {
        if (racer.body == null) return;
        if (this.minecraft.getCameraEntity() == racer.body) {
            if (racer == watched()) this.ridingAcross = true;
            stopSpectating();
        }
        ClientLevel level = this.minecraft.level;
        if (level != null) level.removeEntity(racer.body.getId(), Entity.RemovalReason.DISCARDED);
        racer.body = null;
        racer.mining = null;
    }

    private final class Router extends ChannelInboundHandlerAdapter {
        boolean loginAsRespawn;

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            try {
                if (msg instanceof ClientboundBundlePacket bundle) {
                    for (Packet<? super ClientGamePacketListener> packet : bundle.subPackets()) route(packet);
                } else if (msg instanceof Packet<?> packet) {
                    route(packet);
                }
            } catch (RuntimeException error) {
                VivetSpeedrun.LOGGER.debug("Replay packet skipped", error);
            } finally {
                ReferenceCountUtil.release(msg);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            VivetSpeedrun.LOGGER.debug("Could not decode replay frame", cause);
        }

        private void route(Packet<?> packet) {
            if (!allowed(packet)) return;
            if (packet instanceof net.minecraft.network.protocol.game.ClientboundSetPassengersPacket seats) {
                ReplayPlayback.this.riders.put(seats.getVehicle(), seats.getPassengers().clone());
            }
            if (packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket chat) {
                if (ReplayPlayback.this.replay.match().outsider && typed(chat.content())) return;
                handle(new net.minecraft.network.protocol.game.ClientboundSystemChatPacket(
                        harmless(chat.content()), chat.overlay()));
                return;
            }

            int body = ReplayPlayback.this.deliveringBody;
            if (body >= 0) {
                routeBody(ReplayPlayback.this.racers.get(body), packet);
                return;
            }

            if (packet instanceof ClientboundLoginPacket login) {
                login(login);
                return;
            }
            if (packet instanceof ClientboundRespawnPacket respawn) {
                noteRiding();
                handle(new ClientboundRespawnPacket(asSpectator(respawn.commonPlayerSpawnInfo()), respawn.dataToKeep()));
                afterNewLevel();
                return;
            }
            if (packet instanceof ClientboundPlayerPositionPacket) {
                if (ReplayPlayback.this.placeCamera) {
                    ReplayPlayback.this.placeCamera = false;
                    handle(packet);
                    if (!ReplayPlayback.this.rebuilding) stepBehind();
                }
                return;
            }
            if (packet instanceof ClientboundGameEventPacket event) {
                var type = event.getEvent();
                if (type == ClientboundGameEventPacket.CHANGE_GAME_MODE || type == ClientboundGameEventPacket.WIN_GAME
                        || type == ClientboundGameEventPacket.DEMO_EVENT
                        || type == ClientboundGameEventPacket.NO_RESPAWN_BLOCK_AVAILABLE
                        || type == ClientboundGameEventPacket.IMMEDIATE_RESPAWN) {
                    return;
                }
                handle(packet);
                return;
            }
            if (packet instanceof ClientboundExplodePacket explode) {
                handle(new ClientboundExplodePacket(explode.center(), explode.radius(), explode.blockCount(),
                        Optional.empty(), explode.explosionParticle(), explode.explosionSound(), explode.blockParticles(), explode.playSound()));
                return;
            }
            if (packet instanceof ClientboundBlockDestructionPacket cracks) {
                noteMining(cracks);
                handle(packet);
                return;
            }
            if (ofTheRacer(packet)) return;
            handle(packet);
        }

        private void login(ClientboundLoginPacket login) {
            CommonPlayerSpawnInfo spawn = asSpectator(login.commonPlayerSpawnInfo());
            if (!ReplayPlayback.this.joined) {
                ReplayPlayback.this.joined = true;
                handle(new ClientboundLoginPacket(CAMERA_ID, login.hardcore(), login.levels(), login.maxPlayers(),
                        login.chunkRadius(), login.simulationDistance(), login.reducedDebugInfo(), false,
                        login.doLimitedCrafting(), spawn, false, false));
                afterNewLevel();
                return;
            }

            noteRiding();
            if (!this.loginAsRespawn) resetLevel();
            this.loginAsRespawn = false;
            handle(new ClientboundRespawnPacket(spawn, (byte) 0));
            afterNewLevel();
        }

        private void noteRiding() {
            ReplayPlayback.this.ridingAcross = !ReplayPlayback.this.rebuilding && isRidingWatched();
        }

        private void afterNewLevel() {
            ReplayPlayback.this.placeCamera = true;
            ClientLevel level = ReplayPlayback.this.minecraft.level;
            if (level != null) level.tickRateManager().setTickRate(20.0F * speed());
            for (Racer racer : ReplayPlayback.this.racers) {
                racer.body = null;
                racer.mining = null;
                if (racer.holder != null && level != null) racer.holder.moveInto(level);
            }
            if (!ReplayPlayback.this.fast && !ReplayPlayback.this.rebuilding) rebuildBodies();
        }

        private boolean ofTheRacer(Packet<?> packet) {
            Racer racer = watched();

            if (packet instanceof net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket ghost) {
                if (!ReplayPlayback.this.fast) CraftingBook.ghost(ReplayPlayback.this.minecraft, ghost);
                return true;
            }
            if (!HolderPackets.isPlayerState(packet)) return false;
            ReplayBody holder = racer.holder;
            if (holder == null) {
                ClientLevel level = ReplayPlayback.this.minecraft.level;
                if (level == null) return true;
                racer.holder = holder = holderFor(racer, level);
            }
            return HolderPackets.apply(holder, packet, title -> racer.containerTitle = title);
        }

        private void routeBody(Racer racer, Packet<?> packet) {
            if (packet instanceof ClientboundAddEntityPacket add) {
                ClientLevel level = ReplayPlayback.this.minecraft.level;
                if (level == null) return;
                removeBody(racer);
                ReplayBody body = new ReplayBody(level, racer.profile);
                body.recreateFromPacket(add);
                ((com.vivet.speedrun.mixin.AbstractClientPlayerAccessor) (Object) body)
                        .vivet$setPlayerInfo(new PlayerInfo(racer.profile, false));
                level.addEntity(body);
                racer.body = body;
                racer.ids.add(add.getId());

                for (Map.Entry<Integer, int[]> seat : ReplayPlayback.this.riders.entrySet()) {
                    for (int rider : seat.getValue()) {
                        if (rider != add.getId()) continue;
                        Entity mount = level.getEntity(seat.getKey());
                        if (mount != null && body.getVehicle() != mount) body.startRiding(mount, true, false);
                    }
                }
                if (ReplayPlayback.this.ridingAcross && racer == watched()) {
                    ReplayPlayback.this.ridingAcross = false;
                    ReplayPlayback.this.minecraft.setCameraEntity(body);
                }
                return;
            }
            if (packet instanceof ClientboundRemoveEntitiesPacket) {
                removeBody(racer);
                return;
            }
            if (racer.body == null) return;
            ReplayBody body = racer.body;

            if (packet instanceof net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket sync
                    && !racer.ids.contains(sync.id())) {
                racer.steered.put(sync.id(), ReplayPlayback.this.bodyAt);
            }
            if (packet instanceof net.minecraft.network.protocol.game.ClientboundSwingAnimationPacket) {
                racer.swungAt = ReplayPlayback.this.ticks;
            }
            if (packet instanceof net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket data) {
                int pose = ReplayBody.poseDataId();
                for (var value : data.packedItems()) {
                    if (value.id() == pose) body.posedByRecording();
                }
            }
            Vec3 before = body.position();
            handle(packet);

            if (body.deathTime > 0 && !body.isDeadOrDying()) body.deathTime = 0;

            if (packet instanceof net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket sync
                    && sync.position().endPosition().distanceToSqr(before) > 16.0 * 16.0) {
                settle(body);
            }
        }

        private void noteMining(ClientboundBlockDestructionPacket cracks) {
            for (Racer racer : ReplayPlayback.this.racers) {
                if (racer.body == null || racer.body.getId() != cracks.getId()) continue;
                if (cracks.getProgress() < 0 || cracks.getProgress() > 9) {
                    racer.mining = null;
                } else if (!cracks.getPos().equals(racer.mining)) {
                    racer.mining = cracks.getPos();
                    racer.miningTicks = 0;
                }
            }
        }
    }

    private static CommonPlayerSpawnInfo asSpectator(CommonPlayerSpawnInfo info) {
        return new CommonPlayerSpawnInfo(info.dimensionType(), info.dimension(), info.seed(), GameType.SPECTATOR,
                Optional.of(GameType.SPECTATOR), info.isDebug(), info.isFlat(), info.lastDeathLocation(), info.portalCooldown(),
                info.seaLevel());
    }

    private static GameProfile profileOf(LoadedReplay.View view, WorldRecording world) {
        UUID uuid = uuidOf(view.uuid());
        if (uuid == null) uuid = UUID.nameUUIDFromBytes(view.name().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (world == null || world.meta.skinValue().isEmpty()) return new GameProfile(uuid, view.name());
        LinkedHashMultimap<String, Property> properties = LinkedHashMultimap.create();
        properties.put("textures", new Property("textures", world.meta.skinValue(),
                world.meta.skinSignature().isEmpty() ? null : world.meta.skinSignature()));
        return new GameProfile(uuid, view.name(), new PropertyMap(properties));
    }

    private static UUID uuidOf(String hex) {
        if (hex == null || hex.length() != 32) return null;
        try {
            return UUID.fromString(hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16)
                    + "-" + hex.substring(16, 20) + "-" + hex.substring(20));
        } catch (IllegalArgumentException bad) {
            return null;
        }
    }

    static int varintAfterId(byte[] frame) {
        try {
            int[] at = { 0 };
            ReplayContainer.readVarint(frame, at);
            return ReplayContainer.readVarint(frame, at);
        } catch (IndexOutOfBoundsException tooShort) {
            return Integer.MIN_VALUE;
        }
    }

    static int intAfterId(byte[] frame) {
        try {
            int[] at = { 0 };
            ReplayContainer.readVarint(frame, at);
            int i = at[0];
            return ((frame[i] & 0xFF) << 24) | ((frame[i + 1] & 0xFF) << 16) | ((frame[i + 2] & 0xFF) << 8)
                    | (frame[i + 3] & 0xFF);
        } catch (IndexOutOfBoundsException tooShort) {
            return Integer.MIN_VALUE;
        }
    }
}
