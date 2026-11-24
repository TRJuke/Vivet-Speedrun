package com.vivet.speedrun.mixin;

import com.vivet.speedrun.client.replay.ReplayPlayback;

import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ScreenEffectRenderer.class)
public abstract class ReplayScreenOverlayMixin {
    @Redirect(method = "submit",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;isSpectator:Z"))
    private boolean vivet$watchedOverlays(AvatarRenderState state) {
        return ReplayPlayback.ridden() == null && state.isSpectator;
    }
}
