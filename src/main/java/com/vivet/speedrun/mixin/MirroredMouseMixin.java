package com.vivet.speedrun.mixin;

import com.vivet.speedrun.client.replay.OwnScreens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MirroredMouseMixin {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void vivet$noClicks(long window, MouseButtonInfo button, int action, CallbackInfo callback) {
        if (OwnScreens.isMirror(Minecraft.getInstance().gui.screen())) callback.cancel();
    }
}
