package com.vivet.speedrun.mixin;

import com.vivet.speedrun.client.screen.ReplayInventoryScreen;

import com.vivet.speedrun.client.replay.ReplayBody;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InventoryScreen.class)
public abstract class ReplayInventoryMixin {
    @ModifyArg(method = "extractBackground",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/InventoryScreen;extractEntityInInventoryFollowsMouse(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIIIFFFLnet/minecraft/world/entity/LivingEntity;)V"),
            index = 9)
    private LivingEntity vivet$watchedPlayer(LivingEntity entity) {
        return (Object) this instanceof ReplayInventoryScreen replay ? replay.doll() : entity;
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
            at = @At("RETURN"))
    private static void vivet$noNameTag(LivingEntity entity, CallbackInfoReturnable<EntityRenderState> callback) {
        if (entity instanceof ReplayBody || com.vivet.speedrun.client.replay.SpectateEyes.isBody(entity)) {
            callback.getReturnValue().nameTag = null;
        }
    }
}
