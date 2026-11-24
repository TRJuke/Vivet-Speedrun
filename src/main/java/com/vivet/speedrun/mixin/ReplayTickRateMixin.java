package com.vivet.speedrun.mixin;

import com.vivet.speedrun.client.replay.ReplayPlayback;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class ReplayTickRateMixin {
    @Shadow
    public ClientLevel level;

    @Inject(method = "getTickTargetMillis", at = @At("HEAD"), cancellable = true)
    private void vivet$replaySpeed(float defaultMillis, CallbackInfoReturnable<Float> callback) {
        if (!ReplayPlayback.isActive() || this.level == null || !this.level.tickRateManager().runsNormally()) return;
        callback.setReturnValue(ReplayPlayback.tickMillis());
    }
}
