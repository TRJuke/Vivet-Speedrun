package com.vivet.speedrun.mixin;

import com.vivet.speedrun.client.replay.ReplayPlayback;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class ReplaySpectateMixin {
    @Inject(method = "spectate", at = @At("HEAD"), cancellable = true)
    private void vivet$spectateInReplay(Entity entity, CallbackInfo callback) {
        ReplayPlayback playback = ReplayPlayback.get();
        if (playback == null) return;
        callback.cancel();
        playback.spectate(entity);
    }
}
