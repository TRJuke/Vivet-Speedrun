package com.vivet.speedrun.mixin;

import com.vivet.speedrun.client.HostedConnect;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.DisconnectionDetails;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientHandshakePacketListenerImpl.class)
public abstract class HandshakeRetryMixin {
    @Shadow
    @Final
    private ServerData serverData;

    @Unique
    private boolean vivet$tryingAgain;

    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void vivet$decide(DisconnectionDetails details, CallbackInfo callback) {
        this.vivet$tryingAgain = HostedConnect.retryRefusedLogin(this.serverData, details.reason());
    }

    @Redirect(method = "onDisconnect", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"))
    private void vivet$holdTheFailureScreen(Gui gui, Screen failure) {
        if (!this.vivet$tryingAgain) gui.setScreen(failure);
    }
}
