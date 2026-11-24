package com.vivet.speedrun.client.ui;

import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.p2p.MojangAuth;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public final class Placements {
    public record Badge(String title, int count, Long last, Custom custom) {
    }

    public enum Custom {
        ADMIN("admin", 26, 26, 0xFFFF7272),
        BETA_TESTER("beta_tester", 26, 26, 0xFF628ADB);

        final String id;
        final Identifier texture;
        final int w;
        final int h;
        final int colour;

        Custom(String id, int w, int h, int colour) {
            this.id = id;
            this.texture = Identifier.fromNamespaceAndPath(VivetSpeedrun.MOD_ID,
                    "textures/gui/badge/" + id + ".png");
            this.w = w;
            this.h = h;
            this.colour = colour;
        }

        static Custom of(String source) {
            if (source == null) return null;
            for (Custom custom : values()) {
                if (custom.id.equals(source)) return custom;
            }
            return null;
        }
    }

    private static final String GRAND_CHAMPION = "grand_champion";

    private Placements() {
    }

    static String titleOf(int position) {
        if (position <= 0) return null;

        int band = 0;
        for (int place : Ranks.PLACES) {
            if (position <= place && (band == 0 || place < band)) band = place;
        }
        return Ranks.titleAtPlace(band);
    }

    public static List<Badge> fold(MojangAuth.Badge[] badges) {
        if (badges == null) return List.of();

        List<Badge> out = new ArrayList<>();

        for (MojangAuth.Badge row : badges) {
            if (row == null) continue;

            if (row.source != null && !row.source.isEmpty() && !"rating".equals(row.source)) {
                Custom custom = Custom.of(row.source);
                if (custom != null && indexOf(out, custom) < 0) {
                    out.add(new Badge(null, 1, null, custom));
                }
                continue;
            }

            if (!GRAND_CHAMPION.equals(row.rank)) continue;
            String title = titleOf(row.place);
            if (title == null) continue;

            int at = indexOf(out, title);
            if (at < 0) {
                out.add(new Badge(title, 1, row.awardedAt, null));
            } else {
                Badge held = out.get(at);
                out.set(at, new Badge(title, held.count() + 1, newer(held.last(), row.awardedAt), null));
            }
        }

        out.sort((a, b) -> Integer.compare(order(a), order(b)));
        return out;
    }

    private static int order(Badge badge) {
        if (badge.custom() != null) return badge.custom().ordinal();

        int best = Custom.values().length;

        for (int i = 0; i < Ranks.PLACES.length; i++) {
            if (badge.title().equals(Ranks.titleAtPlace(Ranks.PLACES[i]))) {
                return best + Ranks.PLACES.length - i;
            }
        }
        return Integer.MAX_VALUE;
    }

    private static int indexOf(List<Badge> held, String title) {
        for (int i = 0; i < held.size(); i++) {
            if (title.equals(held.get(i).title())) return i;
        }
        return -1;
    }

    private static int indexOf(List<Badge> held, Custom custom) {
        for (int i = 0; i < held.size(); i++) {
            if (held.get(i).custom() == custom) return i;
        }
        return -1;
    }

    private static Long newer(Long a, Long b) {
        if (a == null) return b;
        if (b == null) return a;
        return Math.max(a, b);
    }

    private static int colourOf(Badge badge) {
        return badge.custom() != null ? badge.custom().colour : Ranks.colorOf(badge.title());
    }

    public static void draw(GuiGraphicsExtractor graphics, Font font, Badge badge,
                            int x, int y, int width, int height, boolean over) {
        int colour = colourOf(badge);

        Theme.roundedBox(graphics, x, y, width, height, 4,
                Theme.mix(Theme.PANEL_SUNKEN, Theme.withAlpha(colour, 0xFF), over ? 0.20F : 0.10F));
        Theme.roundedBoxOutline(graphics, x, y, width, height, 4,
                Theme.withAlpha(colour, over ? 0xCC : 0x77));

        if (badge.custom() != null) {
            Custom custom = badge.custom();
            graphics.blit(RenderPipelines.GUI_TEXTURED, custom.texture,
                    x + (width - custom.w) / 2, y + (height - custom.h) / 2, 0.0F, 0.0F,
                    custom.w, custom.h, custom.w, custom.h);
            return;
        }

        int box = Math.max(8, Math.min(width, height) - 8);
        int cx = x + width / 2;
        int cy = y + height / 2;
        RankEmblem.drawAt(graphics, cx, cy, badge.title(), true, box);

        if (badge.count() > 1) {
            String count = "x" + badge.count();
            int tagW = font.width(count) + 6;
            int tagH = font.lineHeight + 3;
            int tagX = Math.min(cx + box / 2 - tagW + 2, x + width - tagW - 1);
            int tagY = Math.min(cy + box / 2 - tagH + 2, y + height - tagH - 1);

            Theme.roundedBox(graphics, tagX, tagY, tagW, tagH, 3, 0xF0101218);
            Theme.roundedBoxOutline(graphics, tagX, tagY, tagW, tagH, 3, Theme.withAlpha(colour, 0xDD));
            graphics.text(font, Component.literal(count), tagX + 3, tagY + 2, colour, true);
        }
    }

    public static List<Tip.Line> tip(Badge badge, Font font) {
        List<Tip.Line> lines = Tip.lines();

        if (badge.custom() != null) {
            lines.add(new Tip.Line(Component.translatable("vivet.badge.custom." + badge.custom().id),
                    badge.custom().colour));
            return lines;
        }

        lines.add(new Tip.Line(Component.literal(Ranks.nameOf(badge.title())), colourOf(badge)));
        if (badge.last() != null) {
            ZonedDateTime when = Instant.ofEpochMilli(badge.last()).atZone(ZoneId.systemDefault());
            lines.add(new Tip.Line(Component.translatable("vivet.badge.last_won",
                    when.getMonthValue() + "/" + when.getYear()),
                    Theme.TEXT_FAINT));
        }
        return lines;
    }
}
