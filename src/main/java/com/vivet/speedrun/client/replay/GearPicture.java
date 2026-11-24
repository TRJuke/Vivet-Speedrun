package com.vivet.speedrun.client.replay;

import com.vivet.speedrun.client.ui.PlayerHead;
import com.vivet.speedrun.replay.ReplayItems;
import com.vivet.speedrun.replay.ReplayTrace;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class GearPicture {
    public static final int WIDTH = 176;
    public static final int HEIGHT = 166;

    private static final Identifier INVENTORY = Identifier.withDefaultNamespace("textures/gui/container/inventory.png");
    private static final Identifier EFFECT_BACKGROUND = Identifier.withDefaultNamespace("container/inventory/effect_background");

    private static final int LABEL = 0xFF404040;

    private static Model.Simple wide;
    private static Model.Simple slim;

    private GearPicture() {
    }

    public static ItemStack draw(GuiGraphicsExtractor graphics, Font font, int x, int y, ReplayTrace.Sample sample,
                                 String name, String uuid, int mouseX, int mouseY) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, INVENTORY, x, y, 0.0F, 0.0F, WIDTH, HEIGHT, 256, 256);
        graphics.text(font, Component.translatable("container.crafting"), x + 97, y + 6, LABEL, false);
        figure(graphics, x + 26, y + 8, x + 75, y + 78, name, uuid, mouseX, mouseY);

        ItemStack hovered = ItemStack.EMPTY;
        if (sample == null) return hovered;

        int[] worn = { ReplayTrace.SLOT_HEAD, ReplayTrace.SLOT_CHEST, ReplayTrace.SLOT_LEGS, ReplayTrace.SLOT_FEET };
        for (int i = 0; i < worn.length; i++) {
            hovered = slot(graphics, font, x + 8, y + 8 + i * 18, sample.slot(worn[i]), mouseX, mouseY, hovered);
        }
        hovered = slot(graphics, font, x + 77, y + 62, sample.slot(ReplayTrace.SLOT_OFFHAND), mouseX, mouseY, hovered);
        for (int i = 0; i < 27; i++) {
            hovered = slot(graphics, font, x + 8 + (i % 9) * 18, y + 84 + (i / 9) * 18,
                    sample.slot(ReplayTrace.SLOT_MAIN + i), mouseX, mouseY, hovered);
        }
        for (int i = 0; i < 9; i++) {
            hovered = slot(graphics, font, x + 8 + i * 18, y + 142, sample.slot(ReplayTrace.SLOT_HOTBAR + i),
                    mouseX, mouseY, hovered);
        }

        effects(graphics, x + WIDTH + 2, y, sample.effects());
        return hovered;
    }

    private static ItemStack slot(GuiGraphicsExtractor graphics, Font font, int x, int y, ReplayTrace.Item entry,
                                  int mouseX, int mouseY, ItemStack hovered) {
        boolean over = mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16;

        if (over) com.vivet.speedrun.client.ui.Theme.fill(graphics, x, y, x + 16, y + 16, 0x80FFFFFF);
        if (entry == null) return hovered;
        ItemStack stack = ReplayItems.stackOf(entry);
        if (stack.isEmpty()) return hovered;
        graphics.item(stack, x, y);
        graphics.itemDecorations(font, stack, x, y);
        return over ? stack : hovered;
    }

    private static void figure(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, String name,
                               String uuid, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (wide == null) {
            wide = new Model.Simple(minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER), RenderTypes::entityTranslucent);
            slim = new Model.Simple(minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), RenderTypes::entityTranslucent);
        }
        PlayerSkin skin = PlayerHead.skinOf(name, uuid);
        Model.Simple model = skin.model() == PlayerModelType.SLIM ? slim : wide;
        float centerX = (x0 + x1) / 2.0F;
        float centerY = (y0 + y1) / 2.0F;
        float turn = (float) Math.atan((centerX - mouseX) / 40.0F) * 20.0F;
        float tilt = (float) Math.atan((centerY - mouseY) / 40.0F) * 20.0F;
        ModelPart head = model.root().getChild("head");
        head.yRot = turn * Mth.DEG_TO_RAD;
        head.xRot = -tilt * Mth.DEG_TO_RAD;

        graphics.skin(model, skin.body().texturePath(), 30.0F * 0.9375F, tilt, -turn, -1.0625F, x0, y0, x1, y1 - 3);
    }

    private static void effects(GuiGraphicsExtractor graphics, int x, int y, List<ReplayTrace.Effect> effects) {
        int step = effects.size() > 5 ? 132 / (effects.size() - 1) : 33;
        for (ReplayTrace.Effect effect : effects) {
            Identifier id = Identifier.tryParse(effect.id());
            MobEffect found = id == null ? null : BuiltInRegistries.MOB_EFFECT.getValue(id);
            if (found == null) continue;
            Holder<MobEffect> holder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(found);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, EFFECT_BACKGROUND, x, y, 32, 32);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, net.minecraft.client.gui.Hud.getMobEffectSprite(holder),
                    x + 7, y + 7, 18, 18);
            y += step;
        }
    }
}
