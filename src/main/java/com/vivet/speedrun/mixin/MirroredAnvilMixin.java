package com.vivet.speedrun.mixin;

import com.vivet.speedrun.client.replay.ReplayPlayback;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.network.protocol.Packet;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AnvilScreen.class)
public abstract class MirroredAnvilMixin {
    @Redirect(method = "onNameChanged",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void vivet$renameNothing(ClientPacketListener listener, Packet<?> packet) {
        if (ReplayPlayback.isReplayMenu(((AnvilScreen) (Object) this).getMenu())) return;
        listener.send(packet);
    }
}
