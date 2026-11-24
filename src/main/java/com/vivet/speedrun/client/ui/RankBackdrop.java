package com.vivet.speedrun.client.ui;

import com.vivet.speedrun.media.Media;
import com.vivet.speedrun.media.MediaImages;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.concurrent.ThreadLocalRandom;

public final class RankBackdrop {
    private static final int SCALE = 2;

    private static final int SRC = 16;

    private static final int TINT = 0xD96E6E6E;

    private static final int FADE = 22;

    private RankBackdrop() {
    }

    public record Choice(Identifier tile, String media) {
    }

    private static String[] poolFor(String rankId) {
        return switch (rankId == null ? "" : rankId) {
            case "coal" -> new String[] { "coal_block" };
            case "copper" -> new String[] { "copper_block" };
            case "iron" -> new String[] { "iron_block" };
            case "gold" -> new String[] { "gold_block" };
            case "emerald" -> new String[] { "emerald_block" };
            case "diamond" -> new String[] { "diamond_block" };
            case "netherite" -> new String[] { "netherite_block" };
            case "grand_champion" -> new String[] { "quartz_pillar_side" };
            case Ranks.SPEED_KING -> new String[] { "amethyst_block" };

            case "speed_prince" -> new String[] { "blue_ice" };
            case "speed_knight" -> new String[] { "red_nether_bricks" };
            case "speed_squire" -> new String[] { "pink_terracotta" };
            default -> null;
        };
    }

    public static Choice pick(String rankId) {
        String[] pool = poolFor(rankId);
        if (pool == null || pool.length == 0) return null;

        String chosen = pool.length == 1
                ? pool[0] : pool[ThreadLocalRandom.current().nextInt(pool.length)];

        Identifier tile = Identifier.withDefaultNamespace("textures/block/" + chosen + ".png");
        String media = pictureFor(rankId);
        if (media != null) MediaImages.prepare(media);

        return new Choice(tile, media);
    }

    private static String pictureFor(String rankId) {
        return Ranks.SPEED_KING.equals(rankId) ? Media.backgroundOf("speed_king") : null;
    }

    public static void draw(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                            Choice background) {
        if (background == null || width <= 0 || height <= 0) return;

        if (MediaImages.cover(graphics, background.media(), x, y, width, height, TINT)) {
            fade(graphics, x, y, width, height);
            return;
        }

        if (background.tile() == null) return;

        Clip.on(graphics, x, y, x + width, y + height);

        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(SCALE, SCALE);

        int across = (width + SCALE * SRC - 1) / (SCALE * SRC);
        int down = (height + SCALE * SRC - 1) / (SCALE * SRC);

        for (int row = 0; row < down; row++) {
            for (int column = 0; column < across; column++) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, background.tile(),
                        column * SRC, row * SRC, 0.0F, 0.0F, SRC, SRC, SRC, SRC, TINT);
            }
        }

        pose.popMatrix();
        Clip.off(graphics);

        fade(graphics, x, y, width, height);
    }

    private static void fade(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        int edge = Theme.withAlpha(Theme.PANEL, 0xFF);
        int gone = Theme.withAlpha(Theme.PANEL, 0x00);
        int fade = Math.min(FADE, height / 2);

        graphics.fillGradient(x, y, x + width, y + fade, edge, gone);
        graphics.fillGradient(x, y + height - fade, x + width, y + height, gone, edge);
    }
}
