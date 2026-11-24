package com.vivet.speedrun.client.replay;

import com.mojang.authlib.GameProfile;
import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.client.Spectating;
import com.vivet.speedrun.net.Payloads;
import com.vivet.speedrun.replay.ReplayContainer;

import io.netty.buffer.Unpooled;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.ArrayDeque;
import java.util.UUID;

public final class SpectateEyes implements ReplayScreens.Watched {
    private static final SpectateEyes INSTANCE = new SpectateEyes();

    private static final int HOLDER_ID = ReplayPlayback.CAMERA_ID - 100;

    private static final int POSES_KEPT = 2;

    private int entity = Integer.MIN_VALUE;

    private ReplayBody holder;

    private Component containerTitle;
    private ReplayContainer.Pointer pointer = ReplayContainer.Pointer.CLOSED;

    private Payloads.Book book;

    private int bookPick = -1;

    private Payloads.Drag drag;

    private int rideJump;

    private Payloads.OwnScreen ownScreen;
    private final ReplayHands hands = new ReplayHands();

    private record Pose(double x, double y, double z, float yaw, float pitch, float head) {
    }

    private final ArrayDeque<Pose> poses = new ArrayDeque<>();
    private Pose shown;

    private int stale;
    private static final int STALE_TICKS = 5;

    private static final int LET_GO_TICKS = 40;

    private boolean letGo;

    private boolean adopt;

    private int ticks;
    private int heardAt;

    private int errors;

    private RegistryAccess boundTo;
    private net.minecraft.network.ProtocolInfo<?> protocol;

    private SpectateEyes() {
    }

    public static void receive(Minecraft minecraft, Payloads.Eyes eyes) {
        if (!Spectating.isActive() || minecraft.level == null) return;
        try {
            INSTANCE.take(minecraft, eyes);
        } catch (RuntimeException error) {
            INSTANCE.logError("Could not apply what the player sees", error);
        }
    }

    private void take(Minecraft minecraft, Payloads.Eyes eyes) {
        ClientLevel level = minecraft.level;
        if (eyes.has(Payloads.Eyes.RESET) || this.holder == null || eyes.entity() != this.entity) {
            if (!eyes.has(Payloads.Eyes.RESET)) return;
            this.entity = eyes.entity();
            this.holder = new ReplayBody(level, profileOf(level, eyes.entity()));
            this.holder.setId(HOLDER_ID);
            this.containerTitle = null;
            this.pointer = ReplayContainer.Pointer.CLOSED;
            this.book = null;
            this.bookPick = -1;
            this.drag = null;
            this.rideJump = 0;
            this.ownScreen = null;
            this.poses.clear();
            this.shown = null;
            this.stale = 0;

            Entity camera = minecraft.getCameraEntity();
            this.adopt = camera == null || camera.getId() != eyes.entity();
        }
        this.heardAt = this.ticks;

        if (this.letGo) {
            this.letGo = false;
            this.adopt = true;
        }

        if (eyes.has(Payloads.Eyes.POSE)) {
            this.poses.add(new Pose(eyes.x(), eyes.y(), eyes.z(), eyes.yaw(), eyes.pitch(), eyes.head()));
            while (this.poses.size() > POSES_KEPT + 2) this.poses.poll();
        }
        if (eyes.has(Payloads.Eyes.POINTER)) {
            this.pointer = eyes.has(Payloads.Eyes.POINTER_OPEN)
                    ? new ReplayContainer.Pointer(true, eyes.pointerX(), eyes.pointerY())
                    : ReplayContainer.Pointer.CLOSED;
        }

        boolean screens = false;
        for (byte[] frame : eyes.frames()) {
            Packet<?> packet = decode(minecraft, frame);
            if (packet == null) continue;
            if (packet instanceof ClientboundUpdateMobEffectPacket effect) {
                if (effect.getEntityId() != this.entity) continue;
                MobEffectInstance instance = new MobEffectInstance(effect.getEffect(), effect.getEffectDurationTicks(),
                        effect.getEffectAmplifier(), effect.isEffectAmbient(), effect.isEffectVisible(),
                        effect.effectShowsIcon(), null);
                if (!effect.shouldBlend()) instance.skipBlending();
                this.holder.forceAddEffect(instance, null);
                continue;
            }
            if (packet instanceof ClientboundRemoveMobEffectPacket removed) {
                if (removed.entityId() == this.entity) this.holder.removeEffectNoUpdate(removed.effect());
                continue;
            }
            if (HolderPackets.isPlayerState(packet)) {
                HolderPackets.apply(this.holder, packet, title -> this.containerTitle = title);
                continue;
            }

            if (packet instanceof net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket ghost) {
                CraftingBook.ghost(minecraft, ghost);
                continue;
            }

            if (packet instanceof net.minecraft.network.protocol.game.ClientboundMapItemDataPacket) {
                ClientPacketListener listener = minecraft.getConnection();
                if (listener != null) recipes(packet, listener);
                continue;
            }

            if (packet instanceof net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket
                    || packet instanceof net.minecraft.network.protocol.game.ClientboundRecipeBookRemovePacket) {
                ClientPacketListener listener = minecraft.getConnection();
                if (listener != null) recipes(packet, listener);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void recipes(Packet<?> packet, ClientPacketListener listener) {
        ((Packet<net.minecraft.network.protocol.game.ClientGamePacketListener>) packet).handle(listener);
    }

    private Packet<?> decode(Minecraft minecraft, byte[] frame) {
        ClientPacketListener listener = minecraft.getConnection();
        if (frame == null || frame.length == 0 || listener == null) return null;
        try {
            RegistryAccess registries = listener.registryAccess();
            if (this.protocol == null || this.boundTo != registries) {
                this.protocol = GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(registries));
                this.boundTo = registries;
            }
            Object packet = this.protocol.codec().decode(Unpooled.wrappedBuffer(frame));
            return packet instanceof Packet<?> read ? read : null;
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    private static GameProfile profileOf(ClientLevel level, int id) {
        Entity body = level.getEntity(id);
        if (body instanceof AbstractClientPlayer player) return player.getGameProfile();
        String name = Spectating.racer();
        return new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                name.isEmpty() ? "?" : name);
    }

    public static void tick(Minecraft minecraft) {
        if (!Spectating.isActive() || INSTANCE.holder == null) return;
        try {
            INSTANCE.tickOnce(minecraft);
        } catch (RuntimeException error) {
            INSTANCE.logError("Spectating from inside", error);
        }
    }

    private void logError(String what, RuntimeException error) {
        if (this.errors++ < 5) VivetSpeedrun.LOGGER.warn(what, error);
    }

    private void tickOnce(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null) return;
        this.ticks++;
        adoptCamera(minecraft, level);
        AbstractClientPlayer body = riddenBody();
        if (body != null && this.ticks - this.heardAt > LET_GO_TICKS) {
            minecraft.setCameraEntity(minecraft.player);
            this.letGo = true;
            body = null;
        }
        if (body == null) {
            this.poses.clear();
            this.shown = null;
        } else {
            place(body);
            this.hands.tick(body);
        }
        screenEffects(body);

        ReplayBody held = this.holder;
        if (held.level() != level) held.moveInto(level);
        if (body != null) held.copyFrom(body, false);
        held.getCooldowns().tick();
        var inventory = held.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            var stack = inventory.getItem(slot);
            if (stack.getPopTime() > 0) stack.setPopTime(stack.getPopTime() - 1);
        }
        ReplayScreens.follow(this);
    }

    private void adoptCamera(Minecraft minecraft, ClientLevel level) {
        if (!this.adopt) return;
        if (this.ticks - this.heardAt > 2 || riddenBody() != null) {
            this.adopt = false;
            return;
        }
        if (minecraft.getCameraEntity() != minecraft.player) return;
        if (level.getEntity(this.entity) instanceof AbstractClientPlayer player && player != minecraft.player) {
            minecraft.setCameraEntity(player);
            this.adopt = false;
        }
    }

    private void place(AbstractClientPlayer body) {
        while (this.poses.size() > POSES_KEPT) this.poses.poll();
        Pose next = this.poses.poll();
        if (next == null) {
            if (++this.stale > STALE_TICKS) return;
            next = this.shown;
        } else {
            this.stale = 0;
        }
        if (next == null) return;
        boolean first = this.shown == null;
        this.shown = next;

        if (!steering(body)) body.setPos(next.x(), next.y(), next.z());
        body.setYRot(next.yaw());
        body.setXRot(next.pitch());
        body.setYHeadRot(next.head());
        if (first) {
            body.setOldPosAndRot();
            body.yHeadRotO = next.head();
        }
    }

    private static final java.util.List<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>> SCREEN_EFFECTS =
            java.util.List.of(net.minecraft.world.effect.MobEffects.BLINDNESS, net.minecraft.world.effect.MobEffects.DARKNESS,
                    net.minecraft.world.effect.MobEffects.NAUSEA, net.minecraft.world.effect.MobEffects.NIGHT_VISION,
                    net.minecraft.world.effect.MobEffects.CONDUIT_POWER);

    private static final int EFFECT_DRIFT = 40;

    private AbstractClientPlayer effectsOn;

    private void screenEffects(AbstractClientPlayer body) {
        if (this.effectsOn != null && this.effectsOn != body) {
            for (var effect : SCREEN_EFFECTS) this.effectsOn.removeEffectNoUpdate(effect);
            this.effectsOn = null;
        }
        if (body == null) return;
        this.effectsOn = body;
        for (var effect : SCREEN_EFFECTS) {
            MobEffectInstance theirs = this.holder.getEffect(effect);
            MobEffectInstance shown = body.getEffect(effect);
            if (theirs == null) {
                if (shown != null) body.removeEffectNoUpdate(effect);
                continue;
            }
            if (shown != null && shown.getAmplifier() == theirs.getAmplifier()
                    && Math.abs(shown.getDuration() - theirs.getDuration()) <= EFFECT_DRIFT) {
                continue;
            }
            MobEffectInstance copy = new MobEffectInstance(theirs);

            if (shown != null) copy.copyBlendState(shown);
            body.forceAddEffect(copy, null);
        }
    }

    static boolean steering(Entity body) {
        Entity vehicle = body.getVehicle();
        return vehicle != null && vehicle.getControllingPassenger() == body;
    }

    private AbstractClientPlayer riddenBody() {
        Minecraft minecraft = Minecraft.getInstance();
        if (this.holder == null || !Spectating.isActive()) return null;
        Entity camera = minecraft.getCameraEntity();
        if (camera == null || camera == minecraft.player || camera.getId() != this.entity) return null;
        return camera instanceof AbstractClientPlayer player ? player : null;
    }

    static Player ridden() {
        return INSTANCE.riddenBody();
    }

    static Player hudPlayer() {
        return INSTANCE.riddenBody() == null ? null : INSTANCE.holder;
    }

    static void extractHands(Player body, float partialTick, FirstPersonHandsAndItemsRenderState state) {
        INSTANCE.hands.extract(body, partialTick, state);
    }

    static ReplayContainer.Pointer pointerOver(AbstractContainerMenu menu) {
        SpectateEyes eyes = INSTANCE;
        if (menu == null || eyes.riddenBody() == null || !eyes.pointer.open() || eyes.holder.containerMenu != menu) {
            return null;
        }
        return eyes.pointer;
    }

    static ReplayContainer.Pointer pointer() {
        return INSTANCE.riddenBody() == null ? null : INSTANCE.pointer;
    }

    static boolean isMenu(AbstractContainerMenu menu) {
        ReplayBody held = INSTANCE.holder;
        return menu != null && held != null && Spectating.isActive()
                && (held.inventoryMenu == menu || held.containerMenu == menu);
    }

    static Player menuOwner(AbstractContainerMenu menu) {
        return isMenu(menu) ? INSTANCE.holder : null;
    }

    static LivingEntity dollFor(Player holder) {
        AbstractClientPlayer body = INSTANCE.riddenBody();
        return body != null && holder == INSTANCE.holder ? body : holder;
    }

    public static boolean isBody(Entity entity) {
        return entity != null && entity == INSTANCE.riddenBody();
    }

    public static boolean inside() {
        return INSTANCE.riddenBody() != null;
    }

    public static boolean available() {
        return INSTANCE.holder != null && Spectating.isActive();
    }

    static ReplayScreens.Watched watched() {
        return INSTANCE.holder != null && Spectating.isActive() ? INSTANCE : null;
    }

    public static void clear() {
        SpectateEyes eyes = INSTANCE;
        eyes.holder = null;
        eyes.entity = Integer.MIN_VALUE;
        eyes.containerTitle = null;
        eyes.pointer = ReplayContainer.Pointer.CLOSED;
        eyes.book = null;
        eyes.bookPick = -1;
        eyes.drag = null;
        eyes.rideJump = 0;
        eyes.ownScreen = null;
        eyes.poses.clear();
        eyes.shown = null;
        eyes.adopt = false;
        eyes.letGo = false;
        eyes.effectsOn = null;
        eyes.protocol = null;
        eyes.boundTo = null;
    }

    @Override
    public Payloads.Book book() {
        return this.book;
    }

    @Override
    public int bookPick() {
        return this.bookPick;
    }

    @Override
    public Payloads.Drag drag() {
        return this.drag;
    }

    static int rideJump() {
        return INSTANCE.riddenBody() == null ? 0 : INSTANCE.rideJump;
    }

    @Override
    public Payloads.OwnScreen ownScreen() {
        return this.ownScreen;
    }

    public static void receive(Payloads.OwnScreen screen) {
        if (!Spectating.isActive() || INSTANCE.holder == null) return;
        INSTANCE.ownScreen = screen;
    }

    public static void receive(Payloads.RideJump jump) {
        if (!Spectating.isActive() || INSTANCE.holder == null) return;
        INSTANCE.rideJump = jump.percent();
    }

    public static void receive(Payloads.Drag drag) {
        if (!Spectating.isActive() || INSTANCE.holder == null) return;
        INSTANCE.drag = drag;
    }

    public static void receive(Payloads.BookPick pick) {
        if (!Spectating.isActive() || INSTANCE.holder == null) return;
        INSTANCE.bookPick = pick.button();
    }

    public static void receive(Payloads.Book book) {
        if (!Spectating.isActive() || INSTANCE.holder == null) return;
        INSTANCE.book = book;
    }

    @Override
    public boolean ridingWatched() {
        return riddenBody() != null;
    }

    @Override
    public boolean watchedHasScreenOpen() {
        if (this.holder == null) return false;
        return this.pointer.open() || this.holder.containerMenu != this.holder.inventoryMenu;
    }

    @Override
    public Screen inventoryScreen() {
        ReplayBody held = this.holder;
        if (held == null) return null;
        AbstractContainerMenu open = held.containerMenu;
        if (open != null && open != held.inventoryMenu) {
            Screen screen = ReplayScreens.forMenu(open, held.getInventory(),
                    this.containerTitle == null ? Component.empty() : this.containerTitle);
            if (screen != null) return screen;
        }
        return new com.vivet.speedrun.client.screen.ReplayInventoryScreen(held);
    }

    @Override
    public boolean canLeave() {
        return Spectating.staff();
    }

    @Override
    public void stopSpectating() {
        Minecraft.getInstance().options.keyShift.setDown(true);
    }
}
