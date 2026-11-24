package com.vivet.speedrun.mixin;

import com.vivet.speedrun.client.replay.ReplayPlayback;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.GameType;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GameRenderer.class)
public abstract class ReplayFirstPersonMixin {
    @Redirect(method = { "renderItemInHand", "shouldRenderBlockOutline" },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;getPlayerMode()Lnet/minecraft/world/level/GameType;"))
    private GameType vivet$asTheRacer(MultiPlayerGameMode gameMode) {
        return ReplayPlayback.ridden() != null ? GameType.SURVIVAL : gameMode.getPlayerMode();
    }
}
