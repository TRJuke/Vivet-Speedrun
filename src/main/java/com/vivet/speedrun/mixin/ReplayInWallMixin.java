package com.vivet.speedrun.mixin;

import com.vivet.speedrun.client.replay.ReplayPlayback;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LevelExtractor.class)
public abstract class ReplayInWallMixin {
    @Shadow
    private static BlockState getViewBlockingState(LocalPlayer player, Frustum frustum) {
        throw new AssertionError();
    }

    @Redirect(method = "extractPlayerState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/extract/LevelExtractor;getViewBlockingState(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/client/renderer/culling/Frustum;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState vivet$watchedInWall(LocalPlayer player, Frustum frustum) {
        Player watched = ReplayPlayback.ridden();
        return watched == null ? getViewBlockingState(player, frustum) : blockAtEyes(watched, frustum);
    }

    @Redirect(method = "extractPlayerState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isOnFire()Z"))
    private boolean vivet$watchedOnFire(LocalPlayer player) {
        Player watched = ReplayPlayback.ridden();
        return watched == null ? player.isOnFire() : watched.isOnFire();
    }

    @Redirect(method = "extractPlayerState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F"))
    private float vivet$watchedYaw(LocalPlayer player) {
        Player watched = ReplayPlayback.ridden();
        return watched == null ? player.getYRot() : watched.getYHeadRot();
    }

    @Redirect(method = "extractPlayerState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F"))
    private float vivet$watchedPitch(LocalPlayer player) {
        Player watched = ReplayPlayback.ridden();
        return watched == null ? player.getXRot() : watched.getXRot();
    }

    @Redirect(method = "extractPlayerState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean vivet$watchedEyeInFluid(LocalPlayer player, net.minecraft.tags.TagKey<net.minecraft.world.level.material.Fluid> fluid) {
        Player watched = ReplayPlayback.ridden();
        return watched == null ? player.isEyeInFluid(fluid) : watched.isEyeInFluid(fluid);
    }

    private static BlockState blockAtEyes(Player body, Frustum frustum) {
        net.minecraft.world.phys.AABB near = frustum.getNearPlaneBounds().move(body.getEyePosition());
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        float width = body.getBbWidth() * 0.8F;
        float height = 0.1F * body.getScale();
        for (int corner = 0; corner < 8; corner++) {
            double x = body.getX() + ((corner & 1) - 0.5F) * width;
            double y = body.getEyeY() + (((corner >> 1) & 1) - 0.5F) * height;
            double z = body.getZ() + (((corner >> 2) & 1) - 0.5F) * width;
            at.set(x, y, z);
            BlockState state = body.level().getBlockState(at);
            if (state.getRenderShape() != RenderShape.INVISIBLE && state.isViewBlocking(body.level(), at, near)) return state;
        }
        return null;
    }
}
