package com.vivet.speedrun.client.replay;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.HorseInventoryScreen;
import net.minecraft.client.gui.screens.inventory.NautilusInventoryScreen;
import net.minecraft.network.protocol.game.ClientboundMountScreenOpenPacket;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.nautilus.AbstractNautilus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractMountInventoryMenu;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.inventory.NautilusInventoryMenu;

import java.util.Map;
import java.util.WeakHashMap;

final class MountMenus {
    private record Mount(Entity animal, int columns) {
    }

    private static final Map<AbstractContainerMenu, Mount> OPEN = new WeakHashMap<>();

    private MountMenus() {
    }

    static AbstractContainerMenu open(Player holder, ClientboundMountScreenOpenPacket packet) {
        Entity animal = holder.level().getEntity(packet.getEntityId());
        int columns = packet.getInventoryColumns();
        SimpleContainer chest = new SimpleContainer(AbstractMountInventoryMenu.getInventorySize(columns));
        AbstractContainerMenu menu;
        if (animal instanceof AbstractHorse horse) {
            menu = new HorseInventoryMenu(packet.getContainerId(), holder.getInventory(), chest, horse, columns);
        } else if (animal instanceof AbstractNautilus nautilus) {
            menu = new NautilusInventoryMenu(packet.getContainerId(), holder.getInventory(), chest, nautilus, columns);
        } else {
            return null;
        }
        OPEN.put(menu, new Mount(animal, columns));
        return menu;
    }

    static Screen screenFor(AbstractContainerMenu menu, Inventory inventory) {
        Mount mount = OPEN.get(menu);
        if (mount == null) return null;
        if (menu instanceof HorseInventoryMenu horseMenu && mount.animal() instanceof AbstractHorse horse) {
            return new HorseInventoryScreen(horseMenu, inventory, horse, mount.columns());
        }
        if (menu instanceof NautilusInventoryMenu nautilusMenu && mount.animal() instanceof AbstractNautilus nautilus) {
            return new NautilusInventoryScreen(nautilusMenu, inventory, nautilus, mount.columns());
        }
        return null;
    }
}
