package com.vivet.speedrun.mixin;

import com.vivet.speedrun.client.replay.OwnScreens;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class MirroredInputMixin {
    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void vivet$noTyping(long window, CharacterEvent event, CallbackInfo callback) {
        if (OwnScreens.isMirror(Minecraft.getInstance().gui.screen())) callback.cancel();
    }

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void vivet$noKeys(long window, int action, KeyEvent event, CallbackInfo callback) {
        if (!OwnScreens.isMirror(Minecraft.getInstance().gui.screen())) return;

        if (action == 1 && com.vivet.speedrun.client.replay.ReplayScreens.keyOverFollowed(event)) {
            callback.cancel();
            return;
        }
        int key = event.key();
        boolean own = key == 256 || (key >= 290 && key <= 301);
        if (!own) callback.cancel();
    }
}
