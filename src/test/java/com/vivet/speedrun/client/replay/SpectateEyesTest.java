package com.vivet.speedrun.client.replay;

import com.vivet.speedrun.net.Payloads;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundCooldownPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpectateEyesTest {
    private static RegistryAccess registries;

    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    private static List<Packet<? super ClientGamePacketListener>> whatTheServerSends() {
        NonNullList<ItemStack> items = NonNullList.withSize(46, ItemStack.EMPTY);

        return List.of(
                new ClientboundContainerSetContentPacket(0, 3, items, ItemStack.EMPTY),
                new ClientboundOpenScreenPacket(7, MenuType.FURNACE, Component.literal("Furnace")),
                new ClientboundContainerSetContentPacket(7, 1, NonNullList.withSize(39, ItemStack.EMPTY),
                        ItemStack.EMPTY),
                new ClientboundContainerSetDataPacket(7, 2, 120),
                new ClientboundContainerSetSlotPacket(0, 4, 37, ItemStack.EMPTY),
                new ClientboundSetCursorItemPacket(ItemStack.EMPTY),
                new ClientboundSetHeldSlotPacket(4),
                new ClientboundSetHealthPacket(13.5F, 17, 2.0F),
                new ClientboundSetExperiencePacket(0.25F, 40, 3),
                new ClientboundCooldownPacket(Identifier.withDefaultNamespace("ender_pearl"), 20),
                new ClientboundContainerClosePacket(7),
                new ClientboundUpdateMobEffectPacket(42, new MobEffectInstance(MobEffects.SPEED, 600, 1), false),
                new ClientboundRemoveMobEffectPacket(42, MobEffects.SPEED));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static byte[] encode(Packet<?> packet) {
        var protocol = GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(registries));
        ByteBuf buf = Unpooled.buffer();
        try {
            ((net.minecraft.network.codec.StreamCodec) protocol.codec()).encode(buf, packet);
            byte[] out = new byte[buf.readableBytes()];
            buf.readBytes(out);
            return out;
        } finally {
            buf.release();
        }
    }

    private static Object decode(byte[] frame) {
        var protocol = GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(registries));
        return protocol.codec().decode(Unpooled.wrappedBuffer(frame));
    }

    @Test
    void anEyesMessageSurvivesTheWire() {
        List<byte[]> frames = new ArrayList<>();
        for (var packet : whatTheServerSends()) frames.add(encode(packet));
        int flags = Payloads.Eyes.RESET | Payloads.Eyes.POSE | Payloads.Eyes.POINTER | Payloads.Eyes.POINTER_OPEN;
        Payloads.Eyes sent = new Payloads.Eyes(42, flags, 10.5, 64.0, -3.25, 91.5F, -12.25F, 90.0F,
                (short) 300, (short) -8, frames);

        ByteBuf buf = Unpooled.buffer();
        Payloads.Eyes.CODEC.encode(buf, sent);
        Payloads.Eyes read = Payloads.Eyes.CODEC.decode(buf);
        buf.release();

        assertEquals(42, read.entity());
        assertTrue(read.has(Payloads.Eyes.RESET) && read.has(Payloads.Eyes.POSE)
                && read.has(Payloads.Eyes.POINTER) && read.has(Payloads.Eyes.POINTER_OPEN));
        assertEquals(10.5, read.x());
        assertEquals(-3.25, read.z());
        assertEquals(91.5F, read.yaw());
        assertEquals(-12.25F, read.pitch());
        assertEquals(300, read.pointerX());
        assertEquals(-8, read.pointerY());
        assertEquals(frames.size(), read.frames().size());
        for (int i = 0; i < frames.size(); i++) assertArrayEquals(frames.get(i), read.frames().get(i));
    }

    @Test
    void everyFrameReadsBackAndTheHolderTakesIt() {
        for (var packet : whatTheServerSends()) {
            Object read = decode(encode(packet));
            assertInstanceOf(packet.getClass(), read, "read back as something else: " + packet.type());
            boolean effect = packet instanceof ClientboundUpdateMobEffectPacket
                    || packet instanceof ClientboundRemoveMobEffectPacket;

            assertEquals(!effect, HolderPackets.isPlayerState((Packet<?>) read), "holder state: " + packet.type());
        }
        assertFalse(HolderPackets.isPlayerState(new net.minecraft.network.protocol.game.ClientboundSetTimePacket(
                0L, java.util.Map.of())), "the world's own packets are never a holder's");
    }

    @Test
    void theFirstPersonMixinsApply() throws Exception {
        assertHandler(net.minecraft.client.renderer.extract.LevelExtractor.class, "racersHand");
        assertHandler(net.minecraft.client.gui.screens.inventory.InventoryScreen.class, "noNameTag");
        assertHandler(net.minecraft.client.gui.screens.Screen.class, "racerX");
    }

    private static void assertHandler(Class<?> target, String name) throws Exception {
        Class<?> loaded = Class.forName(target.getName(), true, SpectateEyesTest.class.getClassLoader());
        boolean found = java.util.Arrays.stream(loaded.getDeclaredMethods()).anyMatch(m -> m.getName().contains(name));
        assertTrue(found, target.getSimpleName() + " has no " + name + ": the mixin did not apply");
    }
}
