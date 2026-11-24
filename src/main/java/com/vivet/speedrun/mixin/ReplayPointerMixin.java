package com.vivet.speedrun.mixin;

import com.vivet.speedrun.client.replay.ReplayPlayback;
import com.vivet.speedrun.replay.ReplayContainer;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Screen.class)
public abstract class ReplayPointerMixin {
    @ModifyVariable(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int vivet$racerX(int mouseX) {
        if (com.vivet.speedrun.client.replay.OwnScreens.isMirror(this)) {
            ReplayContainer.Pointer over = ReplayPlayback.mirrorPointer();
            return over == null ? -1000 : ((Screen) (Object) this).width / 2 + Math.floorDiv(over.x(), 4);
        }
        ReplayContainer.Pointer pointer = racerPointer();
        return pointer == null ? mouseX : ((ContainerScreenAccessor) (Object) this).vivet$leftPos() + Math.floorDiv(pointer.x(), 4);
    }

    @ModifyVariable(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int vivet$racerY(int mouseY) {
        if (com.vivet.speedrun.client.replay.OwnScreens.isMirror(this)) {
            ReplayContainer.Pointer over = ReplayPlayback.mirrorPointer();
            return over == null ? -1000 : ((Screen) (Object) this).height / 2 + Math.floorDiv(over.y(), 4);
        }
        ReplayContainer.Pointer pointer = racerPointer();
        return pointer == null ? mouseY : ((ContainerScreenAccessor) (Object) this).vivet$topPos() + Math.floorDiv(pointer.y(), 4);
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("RETURN"))
    private void vivet$racerCursor(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   float partialTick, org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback) {
        if (com.vivet.speedrun.client.replay.OwnScreens.isMirror(this)) {
            ReplayContainer.Pointer over = ReplayPlayback.mirrorPointer();
            Screen screen = (Screen) (Object) this;
            if (over != null) {
                com.vivet.speedrun.client.replay.RacerCursor.draw(graphics, screen.width / 2, screen.height / 2, over.x(), over.y());
            }
            return;
        }
        ReplayContainer.Pointer pointer = racerPointer();
        if (pointer == null) return;
        ContainerScreenAccessor corner = (ContainerScreenAccessor) (Object) this;
        com.vivet.speedrun.client.replay.RacerCursor.draw(graphics, corner.vivet$leftPos(), corner.vivet$topPos(),
                pointer.x(), pointer.y());
    }

    private ReplayContainer.Pointer racerPointer() {
        if (!((Object) this instanceof AbstractContainerScreen<?> screen)) return null;
        return ReplayPlayback.pointerOver(screen.getMenu());
    }
}
