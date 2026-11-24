package com.vivet.speedrun.mixin;

import com.vivet.speedrun.client.replay.ReplayPlayback;

import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.RecipeBookMenu;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RecipeBookComponent.class)
public abstract class ReplayRecipeBookMixin {
    @Shadow
    @Final
    protected RecipeBookMenu menu;

    @Redirect(method = { "init", "initVisuals", "tick", "updateStackedContents" },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getInventory()Lnet/minecraft/world/entity/player/Inventory;"))
    private Inventory vivet$watchedInventory(LocalPlayer player) {
        Player owner = ReplayPlayback.menuOwner(this.menu);
        return owner != null ? owner.getInventory() : player.getInventory();
    }
}
