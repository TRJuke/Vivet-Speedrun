package com.vivet.speedrun.client.screen;

import com.vivet.speedrun.client.replay.ReplayPlayback;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class ReplayInventoryScreen extends InventoryScreen {
    private final Player holder;

    public ReplayInventoryScreen(Player holder) {
        super(holder);
        this.holder = holder;
    }

    public LivingEntity doll() {
        return ReplayPlayback.dollFor(this.holder);
    }
}
