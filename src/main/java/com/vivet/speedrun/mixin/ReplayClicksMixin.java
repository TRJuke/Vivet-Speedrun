package com.vivet.speedrun.mixin;

import com.vivet.speedrun.client.replay.ReplayPlayback;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ReplayClicksMixin {
    @Inject(method = "defaultHandleGameClickEvent", at = @At("HEAD"), cancellable = true)
    private static void vivet$noClicksInReplay(ClickEvent event, Minecraft minecraft, Screen screen, CallbackInfo callback) {
        if (ReplayPlayback.isActive()) callback.cancel();
    }

    @Inject(method = "clickCommandAction", at = @At("HEAD"), cancellable = true)
    private static void vivet$noCommandsInReplay(LocalPlayer player, String command, Screen screen, CallbackInfo callback) {
        if (ReplayPlayback.isActive()) callback.cancel();
    }
}
