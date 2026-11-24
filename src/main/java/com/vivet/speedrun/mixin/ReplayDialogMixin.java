package com.vivet.speedrun.mixin;

import com.vivet.speedrun.client.replay.ReplayPlayback;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.dialog.DialogConnectionAccess;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.core.Holder;
import net.minecraft.server.dialog.Dialog;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ReplayDialogMixin {
    @Inject(method = "showDialog(Lnet/minecraft/core/Holder;Lnet/minecraft/client/gui/screens/dialog/DialogConnectionAccess;Lnet/minecraft/client/gui/screens/Screen;)V",
            at = @At("HEAD"), cancellable = true)
    private void vivet$noDialogsInReplay(Holder<Dialog> dialog, DialogConnectionAccess access, Screen previous,
                                         CallbackInfo callback) {
        if (ReplayPlayback.isActive()) callback.cancel();
    }
}
