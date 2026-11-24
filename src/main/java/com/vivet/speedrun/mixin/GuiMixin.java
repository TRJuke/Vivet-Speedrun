package com.vivet.speedrun.mixin;

import com.vivet.speedrun.client.LoadingTakeover;
import com.vivet.speedrun.client.screen.MatchLoadingScreen;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void vivet$holdMatchIntro(Screen screen, CallbackInfo callback) {
        if (screen instanceof LevelLoadingScreen && com.vivet.speedrun.client.replay.ReplayPlayback.hidesLoading()) {
            callback.cancel();
            return;
        }

        if (!LoadingTakeover.isHolding()) return;

        if (LoadingTakeover.isReleasing() || screen instanceof MatchLoadingScreen) return;

        boolean vanillaTransition = screen == null
                || screen instanceof LevelLoadingScreen
                || screen instanceof GenericMessageScreen
                || screen instanceof ProgressScreen
                || screen instanceof TitleScreen;

        if (vanillaTransition) callback.cancel();
    }
}
