package com.vivet.speedrun.mixin;

import com.vivet.speedrun.client.replay.ReplayPlayback;
import com.vivet.speedrun.client.replay.ReplayScreens;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class ReplayContainerScreenMixin {
    @Shadow
    @Final
    protected AbstractContainerMenu menu;

    @Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V",
            at = @At("HEAD"), cancellable = true)
    private void vivet$lookOnly(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo callback) {
        if (ReplayPlayback.isReplayMenu(this.menu)) callback.cancel();
    }

    @Inject(method = "removed", at = @At("HEAD"), cancellable = true)
    private void vivet$keepMenu(CallbackInfo callback) {
        if (ReplayPlayback.isReplayMenu(this.menu)) callback.cancel();
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void vivet$replayKeys(KeyEvent event, CallbackInfoReturnable<Boolean> callback) {
        if (ReplayScreens.isFollowing((Screen) (Object) this) && ReplayScreens.keyOverFollowed(event)) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void vivet$noBundleScroll(double x, double y, double scrollX, double scrollY,
                                      CallbackInfoReturnable<Boolean> callback) {
        if (ReplayPlayback.isReplayMenu(this.menu)) callback.setReturnValue(true);
    }
}
