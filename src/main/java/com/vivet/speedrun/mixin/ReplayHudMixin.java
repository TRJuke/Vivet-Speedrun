package com.vivet.speedrun.mixin;

import com.vivet.speedrun.client.hud.ReplayHud;
import com.vivet.speedrun.client.replay.ReplayPlayback;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

@Mixin(Hud.class)
public abstract class ReplayHudMixin {
    @Inject(method = "getCameraPlayer", at = @At("HEAD"), cancellable = true)
    private void vivet$watchedPlayer(CallbackInfoReturnable<Player> callback) {
        Player player = ReplayPlayback.hudPlayer();
        if (player != null) callback.setReturnValue(player);
    }

    @Redirect(method = "extractHotbarAndDecorations",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;getPlayerMode()Lnet/minecraft/world/level/GameType;"))
    private GameType vivet$survivalBar(MultiPlayerGameMode gameMode) {
        return ReplayPlayback.hudPlayer() != null ? GameType.SURVIVAL : gameMode.getPlayerMode();
    }

    @Redirect(method = "extractCrosshair",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;getPlayerMode()Lnet/minecraft/world/level/GameType;"))
    private GameType vivet$racersCrosshair(MultiPlayerGameMode gameMode) {
        return ReplayPlayback.ridden() != null ? GameType.SURVIVAL : gameMode.getPlayerMode();
    }

    @Redirect(method = { "extractHotbarAndDecorations", "extractSelectedItemName" },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;canHurtPlayer()Z"))
    private boolean vivet$showHealth(MultiPlayerGameMode gameMode) {
        return ReplayPlayback.hudPlayer() != null || gameMode.canHurtPlayer();
    }

    @Redirect(method = { "extractHotbarAndDecorations", "nextContextualInfoState" },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;hasExperience()Z"))
    private boolean vivet$showExperience(MultiPlayerGameMode gameMode) {
        return ReplayPlayback.hudPlayer() != null || gameMode.hasExperience();
    }

    @Redirect(method = "nextContextualInfoState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/waypoints/ClientWaypointManager;hasWaypoints()Z"))
    private boolean vivet$noViewerLocator(net.minecraft.client.waypoints.ClientWaypointManager waypoints) {
        return ReplayPlayback.hudPlayer() == null && waypoints.hasWaypoints();
    }

    @Redirect(method = "getPlayerVehicleWithHealth",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getVehicle()Lnet/minecraft/world/entity/Entity;"))
    private net.minecraft.world.entity.Entity vivet$watchedVehicle(Player player) {
        Player body = ReplayPlayback.ridden();
        return body != null && player == ReplayPlayback.hudPlayer() ? body.getVehicle() : player.getVehicle();
    }

    @Redirect(method = { "nextContextualInfoState", "willPrioritizeJumpInfo" },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;jumpableVehicle()Lnet/minecraft/world/entity/PlayerRideableJumping;"))
    private net.minecraft.world.entity.PlayerRideableJumping vivet$watchedJumpable(LocalPlayer player) {
        return ReplayPlayback.hudPlayer() != null ? ReplayPlayback.watchedJumpable() : player.jumpableVehicle();
    }

    @Redirect(method = "willPrioritizeJumpInfo",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getJumpRidingScale()F"))
    private float vivet$watchedJumpCharge(LocalPlayer player) {
        return ReplayPlayback.hudPlayer() != null ? ReplayPlayback.watchedJumpCharge() : player.getJumpRidingScale();
    }

    @Redirect(method = "extractHotbarAndDecorations",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;experienceLevel:I"))
    private int vivet$level(LocalPlayer player) {
        Player watched = ReplayPlayback.hudPlayer();
        return watched != null ? watched.experienceLevel : player.experienceLevel;
    }

    @Redirect(method = "extractEffects",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getActiveEffects()Ljava/util/Collection;"))
    private Collection<MobEffectInstance> vivet$effects(LocalPlayer player) {
        Player watched = ReplayPlayback.hudPlayer();
        return watched != null ? watched.getActiveEffects() : player.getActiveEffects();
    }

    @Redirect(method = "extractCameraOverlays",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getTicksFrozen()I"))
    private int vivet$watchedFrozen(LocalPlayer player) {
        Player watched = ReplayPlayback.ridden();
        return (watched != null ? watched : player).getTicksFrozen();
    }

    @Redirect(method = "extractCameraOverlays",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getPercentFrozen()F"))
    private float vivet$watchedFrozenShare(LocalPlayer player) {
        Player watched = ReplayPlayback.ridden();
        return (watched != null ? watched : player).getPercentFrozen();
    }

    @Redirect(method = "extractCameraOverlays",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getEffectBlendFactor(Lnet/minecraft/core/Holder;F)F"))
    private float vivet$watchedSwirl(LocalPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,
                                     float partialTick) {
        Player watched = ReplayPlayback.ridden();
        return (watched != null ? watched : player).getEffectBlendFactor(effect, partialTick);
    }

    @Redirect(method = "tick()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getInventory()Lnet/minecraft/world/entity/player/Inventory;"))
    private Inventory vivet$heldName(LocalPlayer player) {
        Player watched = ReplayPlayback.hudPlayer();
        return watched != null ? watched.getInventory() : player.getInventory();
    }

    @Inject(method = "extractHotbarAndDecorations", at = @At("HEAD"))
    private void vivet$liftOverTimeline(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo callback) {
        graphics.pose().pushMatrix();
        if (ReplayPlayback.isActive()) graphics.pose().translate(0.0F, -ReplayHud.lift());
    }

    @Inject(method = "extractHotbarAndDecorations", at = @At("RETURN"))
    private void vivet$dropBack(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo callback) {
        graphics.pose().popMatrix();
    }
}
