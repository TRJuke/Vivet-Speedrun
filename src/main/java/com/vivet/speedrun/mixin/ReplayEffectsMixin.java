package com.vivet.speedrun.mixin;

import com.vivet.speedrun.client.replay.ReplayPlayback;

import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collection;

@Mixin(EffectsInInventory.class)
public abstract class ReplayEffectsMixin {
    @Redirect(method = "*",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getActiveEffects()Ljava/util/Collection;"))
    private Collection<MobEffectInstance> vivet$watchedEffects(LocalPlayer player) {
        Player watched = ReplayPlayback.hudPlayer();
        return watched != null ? watched.getActiveEffects() : player.getActiveEffects();
    }
}
