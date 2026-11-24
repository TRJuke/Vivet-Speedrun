package com.vivet.speedrun.mixin;

import com.vivet.speedrun.client.ui.VoidBackground;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Panorama;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Panorama.class)
public class PanoramaMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void vivet$voidInsteadOfPanorama(GuiGraphicsExtractor graphics, int width, int height,
                                             CallbackInfo callback) {
        VoidBackground.draw(graphics, width, height);
        VoidBackground.vignette(graphics, width, height);

        callback.cancel();
    }
}
