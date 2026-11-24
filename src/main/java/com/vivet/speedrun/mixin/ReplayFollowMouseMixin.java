package com.vivet.speedrun.mixin;

import com.vivet.speedrun.client.replay.ReplayScreens;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.Gui;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Gui.class)
public abstract class ReplayFollowMouseMixin {
    @Redirect(method = "setScreen",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;releaseMouse()V"))
    private void vivet$keepMouseWhileFollowing(MouseHandler mouse) {
        if (!ReplayScreens.opening()) mouse.releaseMouse();
    }
}
