package com.vivet.speedrun.mixin;

import com.vivet.speedrun.client.replay.OwnScreens;

import net.minecraft.client.gui.screens.inventory.BookSignScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BookSignScreen.class)
public abstract class MirroredBookSignMixin {
    @Inject(method = "saveChanges", at = @At("HEAD"), cancellable = true)
    private void vivet$signNothing(CallbackInfo callback) {
        if (OwnScreens.isMirror(this)) callback.cancel();
    }
}
