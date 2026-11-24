package com.vivet.speedrun.mixin;

import com.vivet.speedrun.client.replay.ReplayPlayback;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LightmapRenderStateExtractor.class)
public abstract class ReplayLightEffectsMixin {
    @Redirect(method = "extract",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getEffectBlendFactor(Lnet/minecraft/core/Holder;F)F"))
    private float vivet$watchedBlend(LocalPlayer player, Holder<MobEffect> effect, float partialTick) {
        Player watched = ReplayPlayback.ridden();
        return (watched != null ? watched : player).getEffectBlendFactor(effect, partialTick);
    }

    @Redirect(method = "extract",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/core/Holder;)Z"))
    private boolean vivet$watchedHas(LocalPlayer player, Holder<MobEffect> effect) {
        Player watched = ReplayPlayback.ridden();
        return (watched != null ? watched : player).hasEffect(effect);
    }

    @Redirect(method = "extract",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;nightVisionScale(Lnet/minecraft/world/entity/LivingEntity;F)F"))
    private float vivet$watchedNightVision(LivingEntity entity, float partialTick) {
        Player watched = ReplayPlayback.ridden();
        return GameRenderer.nightVisionScale(watched != null ? watched : entity, partialTick);
    }
}
