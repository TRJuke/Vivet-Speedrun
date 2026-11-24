package com.vivet.speedrun.mixin;

import com.vivet.speedrun.client.LoadingTakeover;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Minecraft.class)
public class MinecraftRenderMixin {
    @ModifyVariable(method = "renderFrame", at = @At("HEAD"), argsOnly = true, index = 1)
    private boolean vivet$holdLevelRendering(boolean renderLevel) {
        return renderLevel && !LoadingTakeover.hidesTheWorld();
    }
}
