package com.vivet.speedrun.mixin;

import com.vivet.speedrun.client.replay.OwnScreens;

import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractSignEditScreen.class)
public abstract class MirroredSignMixin {
    @Inject(method = "removed", at = @At("HEAD"), cancellable = true)
    private void vivet$saveNothing(CallbackInfo callback) {
        if (OwnScreens.isMirror(this)) callback.cancel();
    }

    @Inject(method = "isValid", at = @At("HEAD"), cancellable = true)
    private void vivet$stillValid(CallbackInfoReturnable<Boolean> callback) {
        if (OwnScreens.isMirror(this)) callback.setReturnValue(true);
    }
}
