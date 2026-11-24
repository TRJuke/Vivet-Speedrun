package com.vivet.speedrun.client.replay;

import com.vivet.speedrun.replay.ReplayContainer;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ReplaySafetyTest {
    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyListedPacketIsInThePlayProtocol() throws Exception {
        Class.forName(ReplayPlayback.class.getName(), true, ReplayPlayback.class.getClassLoader());
        assertTrue(ReplayPlayback.STALE.isEmpty(), "stale replay packet types: " + ReplayPlayback.STALE);
    }

    @Test
    void everyAllowedTypeIsInItsProtocol() throws Exception {
        Class.forName(ReplayPlayback.class.getName(), true, ReplayPlayback.class.getClassLoader());
        java.util.Set<net.minecraft.network.protocol.PacketType<?>> config = new java.util.HashSet<>();
        net.minecraft.network.protocol.configuration.ConfigurationProtocols.CLIENTBOUND_TEMPLATE.details()
                .listPackets((type, id) -> config.add(type));

        java.util.List<String> missing = new java.util.ArrayList<>();
        for (var type : ReplayPlayback.PLAY_ALLOWED) if (!WorldRecording.PLAY.containsKey(type)) missing.add("play " + type);
        for (var type : ReplayPlayback.CONFIG_ALLOWED) if (!config.contains(type)) missing.add("config " + type);
        assertTrue(missing.isEmpty(), "allowed but not in the protocol: " + missing);
    }

    @Test
    void theReplayClickAndDialogLocksApply() throws Exception {
        assertHandler(net.minecraft.client.gui.screens.Screen.class, "noClicksInReplay");
        assertHandler(net.minecraft.client.gui.screens.Screen.class, "noCommandsInReplay");
        assertHandler(net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl.class, "noDialogsInReplay");
    }

    @Test
    void theWatchingHooksApply() throws Exception {
        assertHandler(net.minecraft.client.gui.Hud.class, "noViewerLocator");
        assertHandler(net.minecraft.client.gui.Hud.class, "watchedFrozen");
        assertHandler(net.minecraft.client.gui.Hud.class, "watchedSwirl");
        assertHandler(net.minecraft.client.renderer.LightmapRenderStateExtractor.class, "watchedNightVision");
        assertHandler(net.minecraft.client.renderer.extract.LevelExtractor.class, "watchedNausea");
        assertHandler(net.minecraft.client.gui.screens.recipebook.RecipeBookComponent.class, "watchedInventory");
        assertImplements(net.minecraft.client.gui.screens.recipebook.RecipeBookComponent.class,
                com.vivet.speedrun.mixin.RecipeBookComponentAccessor.class);
        assertImplements(net.minecraft.client.gui.screens.recipebook.RecipeBookPage.class,
                com.vivet.speedrun.mixin.RecipeBookPageAccessor.class);
        assertImplements(net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen.class,
                com.vivet.speedrun.mixin.RecipeBookScreenAccessor.class);
        assertImplements(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class,
                com.vivet.speedrun.mixin.QuickCraftAccessor.class);
        assertHandler(net.minecraft.client.gui.screens.Screen.class, "racerCursor");
        assertHandler(net.minecraft.client.renderer.extract.LevelExtractor.class, "watchedInWall");
        assertHandler(net.minecraft.client.Camera.class, "notThroughWalls");
        assertHandler(net.minecraft.client.gui.Hud.class, "watchedVehicle");
        assertHandler(net.minecraft.client.gui.Hud.class, "watchedJumpCharge");
        assertHandler(net.minecraft.client.gui.contextualbar.JumpableVehicleBar.class, "watchedMount");
        assertHandler(net.minecraft.client.renderer.extract.LevelExtractor.class, "watchedOnFire");
        assertHandler(net.minecraft.client.renderer.ScreenEffectRenderer.class, "watchedOverlays");

        assertHandler(net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen.class, "saveNothing");
        assertHandler(net.minecraft.client.gui.screens.inventory.BookEditScreen.class, "saveNothing");
        assertHandler(net.minecraft.client.gui.screens.inventory.AnvilScreen.class, "renameNothing");
        assertHandler(net.minecraft.client.KeyboardHandler.class, "noTyping");
        assertHandler(net.minecraft.client.MouseHandler.class, "noClicks");
        assertHandler(net.minecraft.client.gui.screens.inventory.BookSignScreen.class, "signNothing");
        assertHandler(net.minecraft.client.renderer.extract.LevelExtractor.class, "watchedYaw");
        assertImplements(net.minecraft.client.gui.screens.inventory.BookSignScreen.class,
                com.vivet.speedrun.mixin.BookSignAccessor.class);
        assertImplements(net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen.class,
                com.vivet.speedrun.mixin.SignEditAccessor.class);
        assertImplements(net.minecraft.client.gui.screens.inventory.BookEditScreen.class,
                com.vivet.speedrun.mixin.BookEditAccessor.class);
        assertImplements(net.minecraft.client.gui.screens.inventory.BookViewScreen.class,
                com.vivet.speedrun.mixin.BookViewAccessor.class);
        assertImplements(net.minecraft.client.gui.screens.inventory.AnvilScreen.class,
                com.vivet.speedrun.mixin.AnvilNameAccessor.class);
    }

    private static void assertImplements(Class<?> target, Class<?> accessor) throws Exception {
        Class<?> loaded = Class.forName(target.getName(), true, ReplaySafetyTest.class.getClassLoader());
        assertTrue(accessor.isAssignableFrom(loaded), target.getSimpleName() + " is not a " + accessor.getSimpleName());
    }

    private static void assertHandler(Class<?> target, String name) throws Exception {
        Class<?> loaded = Class.forName(target.getName(), true, ReplaySafetyTest.class.getClassLoader());
        boolean found = java.util.Arrays.stream(loaded.getDeclaredMethods()).anyMatch(m -> m.getName().contains(name));
        assertTrue(found, target.getSimpleName() + " has no " + name + ": the mixin did not apply");
    }

    @Test
    void anotherProtocolIsRefusedAtTheGate() throws Exception {
        int running = SharedConstants.getCurrentVersion().protocolVersion();

        Path newer = recording(running + 1);
        WorldRecording.lastRefusal = "";
        assertNull(WorldRecording.open(newer, running));
        assertTrue(WorldRecording.lastRefusal.startsWith("wrong version"),
                "refused for: " + WorldRecording.lastRefusal);

        Path same = recording(running);
        WorldRecording.lastRefusal = "";
        assertNull(WorldRecording.open(same, running));
        assertFalse(WorldRecording.lastRefusal.startsWith("wrong version"));
    }

    private static Path recording(int protocol) throws Exception {
        byte[] meta = ReplayContainer.encodeMeta(new ReplayContainer.Meta(protocol, 0L, 1000L, 100, List.of(),
                "00000000000000000000000000000000", "Test", "", "", false));

        int head = ReplayContainer.headerLength(3);
        long at = head;
        ReplayContainer.Section metaSection = new ReplayContainer.Section(ReplayContainer.SECTION_META, at, meta.length);
        at += meta.length;
        ReplayContainer.Section index = new ReplayContainer.Section(ReplayContainer.SECTION_WORLD_INDEX, at, 0);
        ReplayContainer.Section world = new ReplayContainer.Section(ReplayContainer.SECTION_WORLD, at, 1);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(ReplayContainer.writeHeader(List.of(metaSection, index, world)));
        out.write(meta);
        out.write(0);

        Path file = Files.createTempFile("vivet-replay-test", ".vr");
        Files.write(file, out.toByteArray());
        file.toFile().deleteOnExit();
        return file;
    }
}
