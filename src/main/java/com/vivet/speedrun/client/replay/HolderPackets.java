package com.vivet.speedrun.client.replay;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundCooldownPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.function.Consumer;

final class HolderPackets {
    private HolderPackets() {
    }

    static boolean isPlayerState(Packet<?> packet) {
        return packet instanceof ClientboundSetHealthPacket || packet instanceof ClientboundSetExperiencePacket
                || packet instanceof ClientboundContainerSetContentPacket
                || packet instanceof ClientboundContainerSetSlotPacket || packet instanceof ClientboundSetCursorItemPacket
                || packet instanceof ClientboundSetPlayerInventoryPacket || packet instanceof ClientboundSetHeldSlotPacket
                || packet instanceof ClientboundContainerSetDataPacket || packet instanceof ClientboundOpenScreenPacket
                || packet instanceof ClientboundContainerClosePacket || packet instanceof ClientboundCooldownPacket
                || packet instanceof net.minecraft.network.protocol.game.ClientboundMountScreenOpenPacket
                || packet instanceof net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
    }

    static boolean apply(ReplayBody holder, Packet<?> packet, Consumer<Component> title) {
        if (packet instanceof ClientboundSetHealthPacket health) {
            holder.setHealth(health.getHealth());
            holder.getFoodData().setFoodLevel(health.getFood());
            holder.getFoodData().setSaturation(health.getSaturation());
            return true;
        }
        if (packet instanceof ClientboundSetExperiencePacket xp) {
            holder.experienceProgress = xp.getExperienceProgress();
            holder.totalExperience = xp.getTotalExperience();
            holder.experienceLevel = xp.getExperienceLevel();
            return true;
        }
        if (packet instanceof ClientboundContainerSetContentPacket content) {
            if (content.containerId() == 0) {
                holder.inventoryMenu.initializeContents(content.stateId(), content.items(), content.carriedItem());
            } else if (content.containerId() == holder.containerMenu.containerId) {
                holder.containerMenu.initializeContents(content.stateId(), content.items(), content.carriedItem());
            }
            return true;
        }
        if (packet instanceof ClientboundContainerSetSlotPacket slot) {
            var stack = slot.getItem();
            if (slot.getContainerId() == 0) {
                if (net.minecraft.world.inventory.InventoryMenu.isHotbarSlot(slot.getSlot()) && !stack.isEmpty()) {
                    var last = holder.inventoryMenu.getSlot(slot.getSlot()).getItem();
                    if (last.isEmpty() || last.getCount() < stack.getCount()) stack.setPopTime(5);
                }
                holder.inventoryMenu.setItem(slot.getSlot(), slot.getStateId(), stack);
            } else if (slot.getContainerId() == holder.containerMenu.containerId) {
                holder.containerMenu.setItem(slot.getSlot(), slot.getStateId(), stack);
            }
            return true;
        }
        if (packet instanceof ClientboundSetCursorItemPacket cursor) {
            holder.containerMenu.setCarried(cursor.contents());
            return true;
        }
        if (packet instanceof ClientboundSetPlayerInventoryPacket inventory) {
            holder.getInventory().setItem(inventory.slot(), inventory.contents());
            return true;
        }
        if (packet instanceof ClientboundSetHeldSlotPacket held) {
            if (Inventory.isHotbarSlot(held.slot())) holder.getInventory().setSelectedSlot(held.slot());
            return true;
        }
        if (packet instanceof ClientboundContainerSetDataPacket data) {
            if (holder.containerMenu.containerId == data.getContainerId()) {
                holder.containerMenu.setData(data.getId(), data.getValue());
            }
            return true;
        }
        if (packet instanceof ClientboundOpenScreenPacket open) {
            try {
                holder.containerMenu = open.getType().create(open.getContainerId(), holder.getInventory());
                title.accept(open.getTitle());
            } catch (RuntimeException unsupported) {
                holder.containerMenu = holder.inventoryMenu;
            }
            ReplayScreens.containerChanged();
            return true;
        }
        if (packet instanceof net.minecraft.network.protocol.game.ClientboundMountScreenOpenPacket mount) {
            AbstractContainerMenu menu = MountMenus.open(holder, mount);
            holder.containerMenu = menu == null ? holder.inventoryMenu : menu;
            title.accept(null);
            ReplayScreens.containerChanged();
            return true;
        }
        if (packet instanceof net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket trades) {
            if (holder.containerMenu.containerId == trades.getContainerId()
                    && holder.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu merchant) {
                merchant.setOffers(trades.getOffers());
                merchant.setXp(trades.getVillagerXp());
                merchant.setMerchantLevel(trades.getVillagerLevel());
                merchant.setShowProgressBar(trades.showProgress());
                merchant.setCanRestock(trades.canRestock());
            }
            return true;
        }
        if (packet instanceof ClientboundContainerClosePacket close) {
            if (close.getContainerId() != holder.containerMenu.containerId) return true;
            holder.containerMenu = holder.inventoryMenu;
            title.accept(null);
            ReplayScreens.containerChanged();
            return true;
        }
        if (packet instanceof ClientboundCooldownPacket cooldown) {
            if (cooldown.duration() == 0) holder.getCooldowns().removeCooldown(cooldown.cooldownGroup());
            else holder.getCooldowns().addCooldown(cooldown.cooldownGroup(), cooldown.duration());
            return true;
        }
        return false;
    }
}
