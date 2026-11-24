package com.vivet.speedrun.mixin;

import com.vivet.speedrun.client.replay.ReplayPlayback;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LevelExtractor.class)
public abstract class ReplayNauseaMixin {
    @Redirect(method = "extractPlayerState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getEffectBlendFactor(Lnet/minecraft/core/Holder;F)F"))
    private float vivet$watchedNausea(LocalPlayer player, Holder<MobEffect> effect, float partialTick) {
        Player watched = ReplayPlayback.ridden();
        return (watched != null ? watched : player).getEffectBlendFactor(effect, partialTick);
    }
}
