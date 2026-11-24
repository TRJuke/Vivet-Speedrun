package com.vivet.speedrun.client.ui;

import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.media.Media;
import com.vivet.speedrun.media.MediaClips;
import com.vivet.speedrun.media.MediaImages;
import com.vivet.speedrun.media.MediaSounds;
import com.vivet.speedrun.p2p.Achievements;
import com.vivet.speedrun.p2p.MojangAuth;
import com.vivet.speedrun.p2p.Wardrobe;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Locale;

public final class Cosmetics {
    private Cosmetics() {
    }

    public static final String DEFAULT = "__default";

    public static final String RANDOM = "__random";

    public static final String RANK = "__rank";

    public static boolean isStandIn(String id) {
        return DEFAULT.equals(id) || RANDOM.equals(id) || RANK.equals(id);
    }

    public static Wardrobe.Item standIn(String id, String kind) {
        Wardrobe.Item item = new Wardrobe.Item();
        item.id = id;
        item.kind = kind;
        item.set = null;
        item.rarity = null;
        item.obtained = null;
        return item;
    }

    public static String prefixOf(String id) {
        if (id == null) return "";
        int dot = id.indexOf('.');
        return dot < 0 ? id : id.substring(0, dot);
    }

    public static String tailOf(String id) {
        if (id == null) return "";
        int dot = id.indexOf('.');
        return dot < 0 ? "" : id.substring(dot + 1);
    }

    public static NameLabel labelOf(String id) {
        return "label".equals(prefixOf(id)) ? NameLabel.byId(tailOf(id)) : null;
    }

    public static String mediaOf(String id) {
        return switch (prefixOf(id)) {
            case "clip" -> Media.clipOf(tailOf(id));
            case "sound" -> Media.soundOf(tailOf(id));
            case "bg" -> Media.backgroundOf(tailOf(id));

            default -> null;
        };
    }

    public static String wornMedia(String name, String slot, String fallback) {
        String worn = com.vivet.speedrun.session.Session.lookOf(name, slot);
        if (worn == null) return fallback;
        if (worn.isEmpty()) return null;

        return mediaOf(worn);
    }

    public static String winMedia(String winner, String slot) {
        if (winner == null || winner.isEmpty()) return null;

        if (winner.equals(com.vivet.speedrun.session.Session.localName())) {
            return myMedia(slot, null);
        }
        return wornMedia(winner, slot, null);
    }

    public static String myMedia(String slot, String fallback) {
        String name = com.vivet.speedrun.session.Session.localName();

        if (com.vivet.speedrun.session.Session.lookKnown(name)) {
            return wornMedia(name, slot, fallback);
        }
        if (!Wardrobe.known()) return fallback;

        String worn = Wardrobe.inSlot(slot);
        return worn.isEmpty() ? null : mediaOf(worn);
    }

    public static String rankOf(String id) {
        return id != null && id.startsWith("emblem.rank.") ? id.substring("emblem.rank.".length())
                : null;
    }

    private static String myRank() {
        MojangAuth.Profile profile = MojangAuth.knownProfile();
        return profile == null ? null : Ranks.displayRank(profile);
    }

    public static Component nameOf(Wardrobe.Item item) {
        if (item == null) return Component.empty();

        if (DEFAULT.equals(item.id)) return Component.translatable("vivet.locker.default");
        if (RANDOM.equals(item.id)) return Component.translatable("vivet.locker.random");
        if (RANK.equals(item.id)) return Component.translatable("vivet.locker.rank");

        if (item.name != null && !item.name.isBlank()) return Component.literal(item.name);

        String set = Wardrobe.setName(item.set);
        return switch (item.kind == null ? "" : item.kind) {
            case "label" -> Component.literal(set);
            case "gif" -> Component.translatable("vivet.locker.piece.gif", set);
            case "audio" -> Component.translatable("vivet.locker.piece.audio", set);
            case "background" -> Component.translatable("vivet.locker.piece.background", set);
            case "emblem" -> Component.translatable("vivet.locker.piece.emblem", set);
            default -> Component.literal(set);
        };
    }

    private static final java.util.Set<String> RANK_TIPS = java.util.Set.of(
            "coal", "copper", "iron", "gold", "emerald", "diamond", "netherite", "grand_champion",
            "speed_squire", "speed_knight", "speed_prince", "speed_king");

    public static Component tipOf(Wardrobe.Item item, String rank) {
        if (item == null) return null;

        if (DEFAULT.equals(item.id)) return Component.translatable("vivet.locker.default.tip");
        if (RANK.equals(item.id)) {
            if (rank == null || rank.isBlank() || "unranked".equals(rank)) return null;

            return RANK_TIPS.contains(rank)
                    ? Component.translatable("vivet.locker.rank.tip." + rank) : null;
        }

        return item.tip == null || item.tip.isBlank() ? null : Component.literal(item.tip);
    }

    public static Component unlockLine(Wardrobe.Item item) {
        String key = item == null || item.obtained == null || item.obtained.isBlank()
                ? "granted" : item.obtained;
        key = "vivet.locker.unlock." + key.toLowerCase(Locale.ROOT);

        if (item != null && "achievement".equals(item.obtained) && item.achievement != null) {
            return "secret".equals(item.achievement) || Achievements.isSecret(item.achievement)
                    ? Component.translatable("vivet.locker.unlock.secret")
                    : Component.translatable(key, AchievementText.name(item.achievement, true));
        }

        if (item == null || item.need == null) return Component.translatable(key);

        return item.need == 1
                ? Component.translatable(key + ".one")
                : Component.translatable(key + ".many", item.need);
    }

    public static String rarityOf(Wardrobe.Item item) {
        if (item == null) return "";
        return isUnobtainable(item) ? "unobtainable" : item.rarity;
    }

    public static boolean isUnobtainable(Wardrobe.Item item) {
        return item != null && (item.obtained == null || item.obtained.isBlank()
                || "granted".equalsIgnoreCase(item.obtained));
    }

    public static Component tierName(String rarity) {
        String key = rarity == null || rarity.isBlank() ? "common" : rarity;
        return Component.translatable("vivet.locker.tier." + key.toLowerCase(Locale.ROOT));
    }

    public static int tierColour(String rarity) {
        return switch (rarity == null ? "" : rarity) {
            case "common" -> 0xFF55E05B;
            case "rare" -> 0xFF3FE0E0;
            case "epic" -> 0xFF9B6BFF;
            case "astral" -> 0xFFFF9A2E;
            case "unobtainable" -> 0xFFB4BAC6;
            case "secret" -> 0xFFFF52D9;
            default -> Theme.TEXT_MUTED;
        };
    }

    private static final Identifier PORTAL =
            Identifier.fromNamespaceAndPath("minecraft", "textures/block/nether_portal.png");

    private static final String[] DISC_NAMES = {
        "chirp", "13", "cat", "blocks", "far", "mall", "mellohi", "stal", "strad", "ward",
        "11", "wait", "otherside", "pigstep", "5", "relic", "creator", "creator_music_box",
        "precipice", "lava_chicken", "tears", "bounce",
    };

    private static final Identifier[] DISCS = buildDiscs();

    private static final String[] DRAWN_DISCS = {
        "burning_alive", "happy_birthday_daniel", "redditor", "weeb", "friends", "opsec",
        "economist", "womp_womp",
    };

    private static final java.util.Map<String, Identifier> DRAWN_DISC_TEXTURES = buildDrawnDiscs();

    private static java.util.Map<String, Identifier> buildDrawnDiscs() {
        java.util.Map<String, Identifier> out = new java.util.HashMap<>();
        for (String set : DRAWN_DISCS) {
            out.put(set, Identifier.fromNamespaceAndPath(
                    VivetSpeedrun.MOD_ID, "textures/gui/disc/" + set + ".png"));
        }
        return out;
    }

    private static Identifier[] buildDiscs() {
        Identifier[] out = new Identifier[DISC_NAMES.length];
        for (int i = 0; i < DISC_NAMES.length; i++) {
            out[i] = Identifier.fromNamespaceAndPath(
                    "minecraft", "textures/item/music_disc_" + DISC_NAMES[i] + ".png");
        }
        return out;
    }

    private static final Identifier CROSS = Identifier.fromNamespaceAndPath(
            "minecraft", "textures/gui/sprites/container/beacon/cancel.png");

    private static final int CROSS_SRC = 18;

    private static final int ARROW_SRC = 32;

    private static final float ARROW_CENTRE = 15.5F;
    private static final float ARROW_OUTER = 14.5F;
    private static final float ARROW_INNER = 10.0F;

    private static final float ARROW_GAP_FROM = 26.0F;
    private static final float ARROW_GAP_TO = 102.0F;

    private static final float ARROW_HEAD_AT = 18.0F;
    private static final float ARROW_TIP = 9.5F;
    private static final float ARROW_HALF = 5.2F;

    private static int[] arrowRuns;

    private static int[] arrowRuns() {
        if (arrowRuns != null) return arrowRuns;

        double ha = Math.toRadians(ARROW_HEAD_AT);
        double mid = (ARROW_INNER + ARROW_OUTER) / 2.0;
        double hx = ARROW_CENTRE + Math.cos(ha) * mid;
        double hy = ARROW_CENTRE - Math.sin(ha) * mid;

        double tipX = hx - Math.sin(ha) * ARROW_TIP;
        double tipY = hy - Math.cos(ha) * ARROW_TIP;
        double aX = hx + Math.cos(ha) * ARROW_HALF;
        double aY = hy - Math.sin(ha) * ARROW_HALF;
        double bX = hx - Math.cos(ha) * ARROW_HALF;
        double bY = hy + Math.sin(ha) * ARROW_HALF;

        java.util.List<int[]> spans = new java.util.ArrayList<>();

        for (int row = 0; row < ARROW_SRC; row++) {
            int from = -1;

            for (int column = 0; column <= ARROW_SRC; column++) {
                boolean on = column < ARROW_SRC
                        && inside(column + 0.5, row + 0.5, tipX, tipY, aX, aY, bX, bY);

                if (on && from < 0) from = column;
                else if (!on && from >= 0) {
                    spans.add(new int[] { row, from, column });
                    from = -1;
                }
            }
        }

        int[] flat = new int[spans.size() * 3];
        for (int i = 0; i < spans.size(); i++) {
            flat[i * 3] = spans.get(i)[0];
            flat[i * 3 + 1] = spans.get(i)[1];
            flat[i * 3 + 2] = spans.get(i)[2];
        }
        arrowRuns = flat;
        return flat;
    }

    private static boolean inside(double x, double y, double tipX, double tipY,
                                  double aX, double aY, double bX, double bY) {
        double dx = x - ARROW_CENTRE;
        double dy = y - ARROW_CENTRE;
        double distance = Math.sqrt(dx * dx + dy * dy);

        if (distance >= ARROW_INNER && distance <= ARROW_OUTER) {
            double angle = Math.toDegrees(Math.atan2(-dy, dx));
            if (angle < 0) angle += 360.0;
            if (!(angle > ARROW_GAP_FROM && angle < ARROW_GAP_TO)) return true;
        }

        double d1 = cross(x, y, tipX, tipY, aX, aY);
        double d2 = cross(x, y, aX, aY, bX, bY);
        double d3 = cross(x, y, bX, bY, tipX, tipY);

        boolean negative = d1 < 0 || d2 < 0 || d3 < 0;
        boolean positive = d1 > 0 || d2 > 0 || d3 > 0;
        return !(negative && positive);
    }

    private static double cross(double px, double py, double qx, double qy,
                                double rx, double ry) {
        return (px - rx) * (qy - ry) - (qx - rx) * (py - ry);
    }

    public static void draw(GuiGraphicsExtractor graphics, Font font, Wardrobe.Item item,
                            int x, int y, int width, int height, long intoMs, boolean lit,
                            boolean full) {
        draw(graphics, font, item, x, y, width, height, intoMs, lit, full, null);
    }

    public static void draw(GuiGraphicsExtractor graphics, Font font, Wardrobe.Item item,
                            int x, int y, int width, int height, long intoMs, boolean lit,
                            boolean full, String rank) {
        draw(graphics, font, item, x, y, width, height, intoMs, lit, full, rank, true);
    }

    public static void draw(GuiGraphicsExtractor graphics, Font font, Wardrobe.Item item,
                            int x, int y, int width, int height, long intoMs, boolean lit,
                            boolean full, String rank, boolean moving) {
        if (item == null || width <= 0 || height <= 0) return;

        int dim = lit ? 0xFFFFFFFF : 0xFF6A6A6A;

        if (DEFAULT.equals(item.id)) {
            drawCross(graphics, x, y, width, height, lit);
            return;
        }
        if (RANDOM.equals(item.id)) {
            drawArrow(graphics, x, y, width, height, lit ? 0xFFC9CFDA : 0xFF5A606B);
            return;
        }
        if (RANK.equals(item.id)) {
            drawRank(graphics, font, item, x, y, width, height, lit,
                    rank == null ? myRank() : rank);
            return;
        }

        switch (item.kind == null ? "" : item.kind) {
            case "label" -> drawLabel(graphics, font, item, x, y, width, height, lit, full, moving);
            case "gif" -> drawClip(graphics, font, item, x, y, width, height, intoMs, dim);
            case "background" -> drawBackground(graphics, font, item, x, y, width, height, dim);
            case "audio" -> drawDisc(graphics, item, x, y, width, height, dim);
            case "emblem" -> drawEmblem(graphics, font, item, x, y, width, height, dim, lit);
            default -> drawPlain(graphics, font, item, x, y, width, height, lit);
        }
    }

    private static void centred(GuiGraphicsExtractor graphics, Identifier texture, int source,
                               int x, int y, int width, int height, float fraction, int tint) {
        int room = Math.round(Math.min(width, height) * fraction);
        int steps = Math.max(1, room / source);
        int size = steps * source;

        graphics.blit(RenderPipelines.GUI_TEXTURED, texture,
                x + (width - size) / 2, y + (height - size) / 2, 0.0F, 0.0F,
                size, size, source, source, source, source, tint);
    }

    private static void drawCross(GuiGraphicsExtractor graphics, int x, int y, int width,
                                  int height, boolean lit) {
        centred(graphics, CROSS, CROSS_SRC, x, y, width, height, 0.55F,
                lit ? 0xFFFFFFFF : 0xFF6A6A6A);
    }

    private static void drawArrow(GuiGraphicsExtractor graphics, int x, int y, int width,
                                  int height, int colour) {
        int steps = Math.max(1, Math.round(Math.min(width, height) * 0.7F) / ARROW_SRC);
        int size = steps * ARROW_SRC;

        int left = x + (width - size) / 2;
        int top = y + (height - size) / 2;

        int[] runs = arrowRuns();
        for (int i = 0; i < runs.length; i += 3) {
            Theme.fill(graphics, left + runs[i + 1] * steps, top + runs[i] * steps,
                    left + runs[i + 2] * steps, top + (runs[i] + 1) * steps, colour);
        }
    }

    private static void drawRank(GuiGraphicsExtractor graphics, Font font, Wardrobe.Item item,
                                 int x, int y, int width, int height, boolean lit, String rank) {
        if ("label".equals(item.kind)) {
            Component name = rank == null
                    ? Component.translatable("vivet.locker.rank")
                    : Component.literal("[" + Ranks.nameOf(rank).toUpperCase(Locale.ROOT) + "]");

            int colour = rank == null ? Theme.TEXT_MUTED : Ranks.colorOf(rank);
            graphics.text(font, name, x + Math.max(2, (width - font.width(name)) / 2),
                    y + (height - font.lineHeight) / 2, lit ? colour : Theme.darken(colour, 0.45F));
            return;
        }

        if (rank == null) {
            drawPlain(graphics, font, item, x, y, width, height, lit);
            return;
        }
        drawRankArt(graphics, rank, x, y, width, height, lit);
    }

    private static void drawLabel(GuiGraphicsExtractor graphics, Font font, Wardrobe.Item item,
                                  int x, int y, int width, int height, boolean lit,
                                  boolean full, boolean moving) {
        NameLabel label = labelOf(item.id);

        if (label == null) {
            drawPlain(graphics, font, item, x, y, width, height, lit);
            return;
        }

        String name = full ? Wardrobe.previewName() : "";
        int total = label.tagWidth(font, name) + NameLabel.runWidthOf(font, name);

        int drawX = x + Math.max(0, (width - total) / 2);
        int drawY = y + (height - font.lineHeight) / 2;

        if (full && moving) {
            NameLabel.drawTaggedLeft(graphics, font, label, name, drawX, drawY, Theme.TEXT);
        } else if (full) {
            graphics.text(font, NameLabel.decorate(name, label), drawX, drawY, Theme.TEXT, true);
        } else if (moving) {
            label.drawTag(graphics, font, drawX, drawY, name);
        } else {
            label.drawTagStill(graphics, font, drawX, drawY, name);
        }

        if (!lit) Theme.fill(graphics, x, y, x + width, y + height, 0xB4101218);
    }

    private static void drawClip(GuiGraphicsExtractor graphics, Font font, Wardrobe.Item item,
                                 int x, int y, int width, int height, long intoMs, int tint) {
        String media = mediaOf(item.id);

        if (media == null || !MediaClips.draw(graphics, media, x + 1, y + 1, width - 2, height - 2,
                                              intoMs, tint)) {
            drawPlain(graphics, font, item, x, y, width, height, tint == 0xFFFFFFFF);
        }
    }

    private static void drawBackground(GuiGraphicsExtractor graphics, Font font,
                                       Wardrobe.Item item, int x, int y, int width, int height,
                                       int tint) {
        String media = mediaOf(item.id);

        if (!MediaImages.cover(graphics, media, x + 1, y + 1, width - 2, height - 2, tint)) {
            drawPlain(graphics, font, item, x, y, width, height, tint == 0xFFFFFFFF);
        }
    }

    private static void drawDisc(GuiGraphicsExtractor graphics, Wardrobe.Item item, int x, int y,
                                 int width, int height, int tint) {
        int box = Math.min(width, height);
        int size = Math.max(16, (box - 8) / 16 * 16);
        int left = x + (width - size) / 2;
        int top = y + (height - size) / 2;

        Identifier drawn = item.set == null ? null : DRAWN_DISC_TEXTURES.get(item.set);
        if (drawn != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, drawn, left, top, 0.0F, 0.0F,
                    size, size, 16, 16, 16, 16, tint);
            return;
        }

        int index = Wardrobe.setsInOrder().indexOf(item.set);

        if (index >= 0 && index < DISCS.length) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, DISCS[index], left, top, 0.0F, 0.0F,
                    size, size, 16, 16, 16, 16, tint);
            return;
        }
        drawMadeUpDisc(graphics, item, left, top, size, tint == 0xFFFFFFFF);
    }

    private static void drawMadeUpDisc(GuiGraphicsExtractor graphics, Wardrobe.Item item,
                                       int x, int y, int size, boolean lit) {
        int band = bandFor(item.set);
        if (!lit) band = Theme.darken(band, 0.45F);

        int radius = size / 2;

        Theme.roundedBox(graphics, x, y, size, size, radius, lit ? 0xFF121212 : 0xFF0C0C0C);
        Theme.roundedBoxOutline(graphics, x, y, size, size, radius,
                lit ? 0xFF3C3C3C : 0xFF262626);

        int inset = Math.max(2, size / 8);
        Theme.roundedBoxOutline(graphics, x + inset, y + inset, size - inset * 2,
                size - inset * 2, radius - inset, lit ? 0xFF2E2E2E : 0xFF1E1E1E);

        int hub = Math.max(3, size / 4);
        Theme.roundedBox(graphics, x + (size - hub) / 2, y + (size - hub) / 2, hub, hub,
                hub / 2, band);
    }

    private static int bandFor(String setId) {
        int hash = (setId == null ? "" : setId).hashCode();

        int sector = Math.floorMod(hash, 360) / 60;
        float remainder = (Math.floorMod(hash, 360) % 60) / 60.0F;

        int high = 242;
        int low = 36;
        int rising = low + Math.round((high - low) * remainder);
        int falling = low + Math.round((high - low) * (1.0F - remainder));

        return switch (sector) {
            case 0 -> rgb(high, rising, low);
            case 1 -> rgb(falling, high, low);
            case 2 -> rgb(low, high, rising);
            case 3 -> rgb(low, falling, high);
            case 4 -> rgb(rising, low, high);
            default -> rgb(high, low, falling);
        };
    }

    private static int rgb(int r, int g, int b) {
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static void drawEmblem(GuiGraphicsExtractor graphics, Font font, Wardrobe.Item item,
                                   int x, int y, int width, int height, int tint, boolean lit) {
        String rank = rankOf(item.id);
        if (rank != null) {
            drawRankArt(graphics, rank, x, y, width, height, lit);
            return;
        }

        String set = tailOf(item.id);
        if (!SpriteEmblem.knows(set)) {
            drawPlain(graphics, font, item, x, y, width, height, lit);
            return;
        }

        int box = Math.min(Math.min(width, height), RankEmblem.bodySize());
        SpriteEmblem.draw(graphics, x + width / 2, y + height / 2, set, box, tint);
    }

    static void drawRankArt(GuiGraphicsExtractor graphics, String rank, int x, int y,
                            int width, int height, boolean lit) {
        if (rank == null) return;

        int cx = x + width / 2;
        int cy = y + height / 2;

        if (Math.min(width, height) >= RankEmblem.bodySize()) {
            RankEmblem.draw(graphics, cx, cy, rank, true);
        } else {
            RankEmblem.drawCompact(graphics, cx, cy, rank, true);
        }

        if (!lit) Theme.fill(graphics, x, y, x + width, y + height, 0xB4101218);
    }

    private static void drawPlain(GuiGraphicsExtractor graphics, Font font, Wardrobe.Item item,
                                  int x, int y, int width, int height, boolean lit) {
        Component name = nameOf(item);
        String text = font.plainSubstrByWidth(name.getString(), Math.max(8, width - 6));

        graphics.text(font, Component.literal(text),
                x + Math.max(2, (width - font.width(text)) / 2),
                y + (height - font.lineHeight) / 2, lit ? Theme.TEXT : Theme.TEXT_FAINT);
    }

    private static final Identifier LOCK = Identifier.fromNamespaceAndPath(
            "minecraft", "textures/gui/sprites/container/cartography_table/locked.png");

    private static final int LOCK_W = 10;
    private static final int LOCK_H = 14;

    public static void drawLock(GuiGraphicsExtractor graphics, int x, int y, int width,
                                int height) {
        int steps = Math.max(1, Math.min(width / 2 / LOCK_W, height / 2 / LOCK_H));
        int w = steps * LOCK_W;
        int h = steps * LOCK_H;

        graphics.blit(RenderPipelines.GUI_TEXTURED, LOCK,
                x + (width - w) / 2, y + (height - h) / 2, 0.0F, 0.0F,
                w, h, LOCK_W, LOCK_H, LOCK_W, LOCK_H, 0xFFFFFFFF);
    }

    public static void ready(Wardrobe.Item item) {
        if (item == null) return;

        String media = mediaOf(item.id);
        if (media == null) return;

        try {
            if ("gif".equals(item.kind)) MediaClips.prepare(media);
            else if ("audio".equals(item.kind)) MediaSounds.prepare(media);
            else if ("background".equals(item.kind)) MediaImages.prepare(media);
        } catch (Exception e) {
            VivetSpeedrun.LOGGER.debug("Could not load cosmetic {}", item.id, e);
        }
    }
}
