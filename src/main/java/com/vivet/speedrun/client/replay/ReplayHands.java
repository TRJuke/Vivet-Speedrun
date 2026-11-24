package com.vivet.speedrun.client.replay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState.HandRenderSelection;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

final class ReplayHands {
    private Player of;
    private ItemStack mainHandItem = ItemStack.EMPTY;
    private ItemStack offHandItem = ItemStack.EMPTY;
    private float mainHandHeight;
    private float oMainHandHeight;
    private float offHandHeight;
    private float oOffHandHeight;

    private float xBob;
    private float xBobO;
    private float yBob;
    private float yBobO;

    void tick(Player body) {
        if (body != this.of) {
            this.of = body;
            this.mainHandItem = body.getMainHandItem();
            this.offHandItem = body.getOffhandItem();
            this.mainHandHeight = this.oMainHandHeight = 1.0F;
            this.offHandHeight = this.oOffHandHeight = 1.0F;
            this.xBob = this.xBobO = body.getXRot();
            this.yBob = this.yBobO = body.getYRot();
        }

        this.xBobO = this.xBob;
        this.yBobO = this.yBob;
        this.xBob += (body.getXRot() - this.xBob) * 0.5F;
        this.yBob += Mth.wrapDegrees(body.getYRot() - this.yBob) * 0.5F;

        this.oMainHandHeight = this.mainHandHeight;
        this.oOffHandHeight = this.offHandHeight;
        ItemStack main = body.getMainHandItem();
        ItemStack off = body.getOffhandItem();
        if (replacesAtOnce(this.mainHandItem, main)) this.mainHandItem = main;
        if (replacesAtOnce(this.offHandItem, off)) this.offHandItem = off;

        if (body.getVehicle() instanceof AbstractBoat) {
            this.mainHandHeight = Mth.clamp(this.mainHandHeight - 0.4F, 0.0F, 1.0F);
            this.offHandHeight = Mth.clamp(this.offHandHeight - 0.4F, 0.0F, 1.0F);
        } else {
            float swap = body.getItemSwapScale(1.0F);
            float mainTarget = this.mainHandItem != main ? 0.0F : swap * swap * swap;
            float offTarget = this.offHandItem != off ? 0.0F : 1.0F;
            this.mainHandHeight += Mth.clamp(mainTarget - this.mainHandHeight, -0.4F, 0.4F);
            this.offHandHeight += Mth.clamp(offTarget - this.offHandHeight, -0.4F, 0.4F);
        }
        if (this.mainHandHeight < 0.1F) this.mainHandItem = main;
        if (this.offHandHeight < 0.1F) this.offHandItem = off;
    }

    void extract(Player body, float partialTick, FirstPersonHandsAndItemsRenderState state) {
        if (body != this.of) tick(body);
        Minecraft minecraft = Minecraft.getInstance();

        var swing = body.getCurrentSwing();
        state.attackHand = swing == null ? InteractionHand.MAIN_HAND : swing.hand();
        state.viewXRot = body.getViewXRot(partialTick);
        state.viewYRot = body.getViewYRot(partialTick);
        state.xBob = Mth.lerp(partialTick, this.xBobO, this.xBob);

        state.yBob = state.viewYRot - Mth.wrapDegrees(state.viewYRot - Mth.lerp(partialTick, this.yBobO, this.yBob));
        state.isScoping = body.isScoping();
        state.useItemRemainingTicks = body.getUseItemRemainingTicks();
        state.handRenderSelection = whichHands(body);
        state.mainHandItem = this.mainHandItem;
        state.offHandItem = this.offHandItem;
        state.mainHandHeight = this.mainHandHeight;
        state.oldMainHandHeight = this.oMainHandHeight;
        state.offHandHeight = this.offHandHeight;
        state.oldOffHandHeight = this.oOffHandHeight;

        boolean right = body.getMainArm() == HumanoidArm.RIGHT;
        ItemDisplayContext mainContext = right ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        ItemDisplayContext offContext = right ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        state.mainHandRenderState.clear();
        state.offHandRenderState.clear();
        var models = minecraft.getItemModelResolver();
        models.updateForTopItem(state.mainHandRenderState, state.mainHandItem, mainContext, body.level(), body,
                body.getId() + mainContext.ordinal());
        models.updateForTopItem(state.offHandRenderState, state.offHandItem, offContext, body.level(), body,
                body.getId() + offContext.ordinal());
        state.mainHandUseDuration = state.mainHandItem.getUseDuration(body);
        state.offHandUseDuration = state.offHandItem.getUseDuration(body);
        state.mainHandChargeDuration = CrossbowItem.getChargeDuration(state.mainHandItem, body);
        state.offHandChargeDuration = CrossbowItem.getChargeDuration(state.offHandItem, body);
        state.mainHandSwapScale = models.swapAnimationScale(state.mainHandItem);
        state.offHandSwapScale = models.swapAnimationScale(state.offHandItem);
        state.hasMainHandMapData = map(body, state.mainHandItem, state.mainHandMapRenderState);
        state.hasOffHandMapData = map(body, state.offHandItem, state.offHandMapRenderState);
    }

    private static boolean replacesAtOnce(ItemStack shown, ItemStack held) {
        if (ItemStack.matchesIgnoringComponents(shown, held, type -> type.ignoreSwapAnimation())) return true;
        return !Minecraft.getInstance().getItemModelResolver().shouldPlaySwapAnimation(held);
    }

    private static boolean map(Player body, ItemStack stack, MapRenderState into) {
        MapId id = stack.get(DataComponents.MAP_ID);
        MapItemSavedData data = id == null ? null : MapItem.getSavedData(id, body.level());
        if (id == null || data == null) return false;
        Minecraft.getInstance().getMapRenderer().extractRenderState(id, data, into);
        return true;
    }

    private static HandRenderSelection whichHands(Player body) {
        ItemStack main = body.getMainHandItem();
        ItemStack off = body.getOffhandItem();
        boolean bow = main.is(Items.BOW) || off.is(Items.BOW);
        boolean crossbow = main.is(Items.CROSSBOW) || off.is(Items.CROSSBOW);
        if (!bow && !crossbow) return HandRenderSelection.RENDER_BOTH_HANDS;
        if (body.isUsingItem()) {
            ItemStack using = body.getUseItem();
            InteractionHand hand = body.getUsedItemHand();
            if (using.is(Items.BOW) || using.is(Items.CROSSBOW)) return HandRenderSelection.onlyForHand(hand);
            return hand == InteractionHand.MAIN_HAND && charged(off)
                    ? HandRenderSelection.RENDER_MAIN_HAND_ONLY : HandRenderSelection.RENDER_BOTH_HANDS;
        }
        return charged(main) ? HandRenderSelection.RENDER_MAIN_HAND_ONLY : HandRenderSelection.RENDER_BOTH_HANDS;
    }

    private static boolean charged(ItemStack stack) {
        return stack.is(Items.CROSSBOW) && CrossbowItem.isCharged(stack);
    }
}
