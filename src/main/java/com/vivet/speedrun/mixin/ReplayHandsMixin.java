package com.vivet.speedrun.mixin;

import com.vivet.speedrun.client.replay.ReplayPlayback;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public abstract class ReplayHandsMixin {
    @Shadow
    private EntityRenderState extractEntity(Entity entity, float partialTick) {
        throw new AssertionError();
    }

    @Inject(method = "extractPlayerState", at = @At("TAIL"))
    private void vivet$racersHand(Camera camera, DeltaTracker delta, float partialTicks, PlayerRenderState state,
                                  CallbackInfo callback) {
        net.minecraft.world.entity.player.Player body = ReplayPlayback.ridden();
        Minecraft minecraft = Minecraft.getInstance();
        if (body == null || !state.hasPlayer || minecraft.level == null) return;
        float partial = delta.getGameTimeDeltaPartialTick(!minecraft.level.tickRateManager().isEntityFrozen(body));
        if (extractEntity(body, partial) instanceof AvatarRenderState avatar) state.avatarRenderState = avatar;
        ReplayPlayback.extractHands(body, partial, state.firstPersonHandsAndItems);
    }
}
