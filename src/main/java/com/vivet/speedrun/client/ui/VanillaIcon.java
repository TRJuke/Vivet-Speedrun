package com.vivet.speedrun.client.ui;

import com.vivet.speedrun.VivetSpeedrun;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class VanillaIcon {
    public enum Kind {
        MAIL,

        CHAT,

        INGOT,

        COIN,

        ARMOUR_STAND,

        KNOWLEDGE,

        HEAD,

        CLOCK,

        COMPASS,

        STAR,

        LADDER,

        COMPARATOR,

        ENDER_EYE,

        MAP,

        BOTTLE,

        DIAMOND,

        ENDER_PEARL,

        SPEED,

        SEEDS,

        LAPIS,

        BREWING_STAND,

        FEATHER,

        HARDCORE_HEART,

        SWORD,

        TOTEM,

        BOOK,

        QUEST,

        DISCORD,

        EMERALD,

        SPYGLASS,
    }

    private static final int SRC = 16;

    private static final int EFFECT = 18;

    private static final int HEART = 9;

    private static int srcOf(Kind kind) {
        return switch (kind) {
            case SPEED -> EFFECT;
            case HARDCORE_HEART -> HEART;
            default -> SRC;
        };
    }

    private static final Identifier PAPER = vanilla("textures/item/paper.png");
    private static final Identifier GOLD_INGOT = vanilla("textures/item/gold_ingot.png");
    private static final Identifier ARMOUR_STAND = vanilla("textures/item/armor_stand.png");
    private static final Identifier KNOWLEDGE_BOOK = vanilla("textures/item/knowledge_book.png");

    private static final Identifier CLOCK = vanilla("textures/item/clock_00.png");
    private static final Identifier COMPASS = vanilla("textures/item/compass_00.png");
    private static final Identifier NETHER_STAR = vanilla("textures/item/nether_star.png");
    private static final Identifier NETHERITE_SWORD = vanilla("textures/item/netherite_sword.png");
    private static final Identifier LADDER = vanilla("textures/block/ladder.png");
    private static final Identifier COMPARATOR = vanilla("textures/item/comparator.png");
    private static final Identifier ENDER_EYE = vanilla("textures/item/ender_eye.png");

    private static final Identifier FILLED_MAP = vanilla("textures/item/filled_map.png");

    private static final Identifier EXPERIENCE_BOTTLE = vanilla("textures/item/experience_bottle.png");
    private static final Identifier DIAMOND = vanilla("textures/item/diamond.png");
    private static final Identifier ENDER_PEARL = vanilla("textures/item/ender_pearl.png");
    private static final Identifier SPEED = vanilla("textures/mob_effect/speed.png");
    private static final Identifier WHEAT_SEEDS = vanilla("textures/item/wheat_seeds.png");
    private static final Identifier LAPIS_LAZULI = vanilla("textures/item/lapis_lazuli.png");
    private static final Identifier EMERALD = vanilla("textures/item/emerald.png");
    private static final Identifier SPYGLASS = vanilla("textures/item/spyglass.png");

    private static final Identifier TOTEM_OF_UNDYING =
            vanilla("textures/item/totem_of_undying.png");

    private static final Identifier BOOK = vanilla("textures/item/book.png");
    private static final Identifier WRITABLE_BOOK = vanilla("textures/item/writable_book.png");

    private static final Identifier COIN =
            Identifier.fromNamespaceAndPath(VivetSpeedrun.MOD_ID, "textures/gui/coin.png");
    private static final int COIN_FRAMES = 6;
    private static final long COIN_FRAME_MS = 100L;

    private static final Identifier DISCORD =
            Identifier.fromNamespaceAndPath(VivetSpeedrun.MOD_ID, "textures/gui/discord.png");

    private static final int DISCORD_SRC = 128;

    private static final Identifier BREWING_STAND = vanilla("textures/item/brewing_stand.png");
    private static final Identifier FEATHER = vanilla("textures/item/feather.png");
    private static final Identifier HARDCORE_HEART =
            vanilla("textures/gui/sprites/hud/heart/hardcore_full.png");

    private static final Identifier HEAD_ISO =
            Identifier.fromNamespaceAndPath(VivetSpeedrun.MOD_ID, "textures/gui/head_iso.png");

    private static final int HEAD_W = 32;
    private static final int HEAD_H = 40;

    private VanillaIcon() {
    }

    private static Identifier vanilla(String path) {
        return Identifier.withDefaultNamespace(path);
    }

    public static void draw(GuiGraphicsExtractor graphics, Kind kind, int cx, int cy, int box) {
        draw(graphics, kind, cx, cy, box, 0xFFFFFFFF);
    }

    public static void draw(GuiGraphicsExtractor graphics, Kind kind, int cx, int cy, int box,
                            int tint) {
        int src = srcOf(kind);
        int scale = box / src;
        if (scale < 1) return;

        int size = src * scale;
        int left = cx - size / 2;
        int top = cy - size / 2;

        if (kind == Kind.CHAT) {
            speech(graphics, left, top, scale, tint);
            return;
        }

        if (kind == Kind.HEAD) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, HEAD_ISO,
                    cx - HEAD_W / 2, cy - HEAD_H / 2, 0.0F, 0.0F,
                    HEAD_W, HEAD_H, HEAD_W, HEAD_H, tint);
            return;
        }

        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(left, top);
        pose.scale(scale, scale);

        switch (kind) {
            case MAIL -> flat(graphics, PAPER, tint);
            case INGOT -> flat(graphics, GOLD_INGOT, tint);
            case COIN -> {
                int frame = (int) ((System.currentTimeMillis() / COIN_FRAME_MS) % COIN_FRAMES);
                graphics.blit(RenderPipelines.GUI_TEXTURED, COIN, 0, 0, frame * (float) SRC, 0.0F,
                        SRC, SRC, SRC * COIN_FRAMES, SRC, tint);
            }
            case ARMOUR_STAND -> flat(graphics, ARMOUR_STAND, tint);
            case KNOWLEDGE -> flat(graphics, KNOWLEDGE_BOOK, tint);
            case CLOCK -> flat(graphics, CLOCK, tint);
            case SWORD -> flat(graphics, NETHERITE_SWORD, tint);
            case COMPASS -> flat(graphics, COMPASS, tint);
            case STAR -> flat(graphics, NETHER_STAR, tint);
            case LADDER -> flat(graphics, LADDER, tint);
            case COMPARATOR -> flat(graphics, COMPARATOR, tint);
            case ENDER_EYE -> flat(graphics, ENDER_EYE, tint);
            case MAP -> flat(graphics, FILLED_MAP, tint);
            case DIAMOND -> flat(graphics, DIAMOND, tint);
            case ENDER_PEARL -> flat(graphics, ENDER_PEARL, tint);
            case BOOK -> flat(graphics, BOOK, tint);
            case QUEST -> flat(graphics, WRITABLE_BOOK, tint);
            case DISCORD -> {
                pose.scale(SRC / (float) DISCORD_SRC, SRC / (float) DISCORD_SRC);
                flat(graphics, DISCORD, tint, DISCORD_SRC);
            }
            case SPEED -> flat(graphics, SPEED, tint, EFFECT);
            case SEEDS -> flat(graphics, WHEAT_SEEDS, tint);
            case EMERALD -> flat(graphics, EMERALD, tint);
            case SPYGLASS -> flat(graphics, SPYGLASS, tint);
            case TOTEM -> flat(graphics, TOTEM_OF_UNDYING, tint);
            case LAPIS -> flat(graphics, LAPIS_LAZULI, tint);
            case BREWING_STAND -> flat(graphics, BREWING_STAND, tint);
            case FEATHER -> flat(graphics, FEATHER, tint);
            case HARDCORE_HEART -> flat(graphics, HARDCORE_HEART, tint, HEART);
            case BOTTLE -> {
                flat(graphics, EXPERIENCE_BOTTLE, tint);
                shimmer(graphics, tint);
            }
            default -> { }
        }

        pose.popMatrix();
    }

    private static void flat(GuiGraphicsExtractor graphics, Identifier texture, int tint) {
        flat(graphics, texture, tint, SRC);
    }

    private static void flat(GuiGraphicsExtractor graphics, Identifier texture, int tint, int src) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0.0F, 0.0F,
                src, src, src, src, tint);
    }

    private static final int[] BOTTLE_MASK = {
            0x0000, 0x0380, 0x07C0, 0x0FE0, 0x0FE0, 0x07C0, 0x07C0, 0x0FE0,
            0x1FF0, 0x3FF8, 0x3FF8, 0x3FF8, 0x3FF8, 0x1FF0, 0x0FE0, 0x07C0,
    };

    private static final float BAND = 4.5F;

    private static final float LEAN = 0.7F;

    private static final int SHEEN = 0x00D2B4FF;

    private static void shimmer(GuiGraphicsExtractor graphics, int tint) {
        float lit = ((tint >> 16) & 0xFF) / 255.0F;
        if (lit <= 0.02F) return;

        long now = System.currentTimeMillis();

        band(graphics, sweep(now, 2600L), 0x74, lit);
        band(graphics, sweep(now, 4100L), 0x40, lit);
    }

    private static float sweep(long now, long period) {
        float phase = (now % period) / (float) period;
        float span = SRC + SRC * LEAN + BAND * 2.0F;
        return phase * span - BAND;
    }

    private static void band(GuiGraphicsExtractor graphics, float centre, int peak, float lit) {
        for (int y = 0; y < SRC; y++) {
            int bits = BOTTLE_MASK[y];
            if (bits == 0) continue;

            for (int x = 0; x < SRC; x++) {
                if ((bits & (1 << x)) == 0) continue;

                float distance = Math.abs(x + (SRC - 1 - y) * LEAN - centre);
                if (distance >= BAND) continue;

                float strength = 1.0F - distance / BAND;
                int alpha = Math.round(strength * strength * peak * lit);
                if (alpha <= 0) continue;

                Theme.fill(graphics, x, y, x + 1, y + 1, (alpha << 24) | SHEEN);
            }
        }
    }

    private static void speech(GuiGraphicsExtractor graphics, int l, int t, int s, int tint) {
        int body = shade(0xFFE8ECF2, tint);
        int edge = shade(0xFF8A93A2, tint);
        int dot = shade(0xFF4A5160, tint);

        px(graphics, l, t, s, 1, 2, 14, 9, body);
        px(graphics, l, t, s, 1, 2, 14, 1, edge);
        px(graphics, l, t, s, 1, 10, 14, 1, edge);
        px(graphics, l, t, s, 1, 2, 1, 9, edge);
        px(graphics, l, t, s, 14, 2, 1, 9, edge);

        px(graphics, l, t, s, 4, 11, 4, 2, body);
        px(graphics, l, t, s, 4, 13, 3, 1, edge);
        px(graphics, l, t, s, 7, 11, 1, 3, edge);

        px(graphics, l, t, s, 4, 5, 2, 2, dot);
        px(graphics, l, t, s, 7, 5, 2, 2, dot);
        px(graphics, l, t, s, 10, 5, 2, 2, dot);
    }

    private static int shade(int color, int tint) {
        int red = ((color >> 16) & 0xFF) * ((tint >> 16) & 0xFF) / 255;
        int green = ((color >> 8) & 0xFF) * ((tint >> 8) & 0xFF) / 255;
        int blue = (color & 0xFF) * (tint & 0xFF) / 255;

        return (color & 0xFF000000) | (red << 16) | (green << 8) | blue;
    }

    private static void px(GuiGraphicsExtractor graphics, int left, int top, int scale,
                           int x, int y, int w, int h, int color) {
        Theme.fill(graphics, left + x * scale, top + y * scale,
                left + (x + w) * scale, top + (y + h) * scale, color);
    }
}
