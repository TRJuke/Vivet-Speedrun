package com.vivet.speedrun.mixin;

import com.vivet.speedrun.client.replay.ReplayPlayback;

import net.minecraft.client.gui.contextualbar.JumpableVehicleBar;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.PlayerRideableJumping;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(JumpableVehicleBar.class)
public abstract class ReplayJumpBarMixin {
    @Redirect(method = "<init>",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;jumpableVehicle()Lnet/minecraft/world/entity/PlayerRideableJumping;"))
    private PlayerRideableJumping vivet$watchedMount(LocalPlayer player) {
        PlayerRideableJumping watched = ReplayPlayback.watchedJumpable();
        return watched != null ? watched : player.jumpableVehicle();
    }

    @Redirect(method = "extractBackground",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getJumpRidingScale()F"))
    private float vivet$watchedCharge(LocalPlayer player) {
        return ReplayPlayback.hudPlayer() != null ? ReplayPlayback.watchedJumpCharge() : player.getJumpRidingScale();
    }
}
