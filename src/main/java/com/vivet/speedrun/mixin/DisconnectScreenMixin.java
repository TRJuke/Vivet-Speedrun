package com.vivet.speedrun.mixin;

import com.vivet.speedrun.client.HostedConnect;

import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class DisconnectScreenMixin {
    @Shadow
    @Final
    protected ServerData serverData;

    @Inject(method = "createDisconnectScreen", at = @At("HEAD"), cancellable = true)
    private void vivet$raceServerLost(DisconnectionDetails details,
                                      CallbackInfoReturnable<Screen> callback) {
        if (!HostedConnect.isRaceServer(this.serverData)) return;
        com.vivet.speedrun.client.WorldActions.trace("race server disconnected ("
                + details.reason().getString() + ")");

        if (com.vivet.speedrun.client.HostedRejoin.pending()) {
            callback.setReturnValue(new com.vivet.speedrun.client.screen.HostedDroppedScreen());
            return;
        }

        callback.setReturnValue(new DisconnectedScreen(HostedConnect.destination(),
                Component.translatable("disconnect.lost"), details, CommonComponents.GUI_BACK));
    }
}
