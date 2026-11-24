package com.vivet.speedrun.mixin;

import com.vivet.speedrun.client.replay.ReplayPlayback;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.ContextualBar;
import net.minecraft.client.gui.contextualbar.ExperienceBar;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ExperienceBar.class)
public abstract class ReplayExperienceBarMixin implements ContextualBar {
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("hud/experience_bar_background");
    private static final Identifier PROGRESS = Identifier.withDefaultNamespace("hud/experience_bar_progress");

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void vivet$watchedBar(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo callback) {
        Player player = ReplayPlayback.hudPlayer();
        if (player == null) return;
        callback.cancel();

        int left = this.left(this.minecraft.getWindow());
        int top = this.top(this.minecraft.getWindow());
        if (player.getXpNeededForNextLevel() <= 0) return;
        int progress = (int) (player.experienceProgress * 183.0F);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, left, top, 182, 5);
        if (progress > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PROGRESS, 182, 5, 0, 0, left, top, progress, 5);
        }
    }
}
