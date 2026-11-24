package com.vivet.speedrun.mixin;

import com.vivet.speedrun.client.HostedConnect;
import com.vivet.speedrun.client.WorldActions;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class HostedExitMixin {
    @Inject(method = "disconnectFromWorld", at = @At("HEAD"), cancellable = true)
    private void vivet$leaveRaceServer(Component reason, CallbackInfo callback) {
        Minecraft minecraft = (Minecraft) (Object) this;

        com.vivet.speedrun.client.replay.ReplayPlayback replay = com.vivet.speedrun.client.replay.ReplayPlayback.get();
        if (replay != null) {
            callback.cancel();
            replay.leave();
            return;
        }
        if (!HostedConnect.isRaceServer(minecraft.getCurrentServer())) return;

        callback.cancel();
        WorldActions.trace("pressed Disconnect");
        WorldActions.quitRaceTo(minecraft, HostedConnect::destination);
    }
}
