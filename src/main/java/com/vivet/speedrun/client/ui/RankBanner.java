package com.vivet.speedrun.client.ui;

import com.vivet.speedrun.p2p.MojangAuth;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class RankBanner {
    public static final int HEIGHT = 112;

    private static final int PAD = 10;

    private static final int BADGE_BOX = 82;
    private static final int BADGE = 72;

    private static final int NEXT_BADGE = 28;

    private static final int BAR_H = 9;

    private RankBanner() {
    }

    public static void draw(GuiGraphicsExtractor graphics, Font font, int x, int y, int width,
                            Component title, MojangAuth.Profile profile,
                            RankBackdrop.Choice background,
                            Component status, int statusColor) {
        Theme.roundedBox(graphics, x, y, width, HEIGHT, 4, Theme.PANEL);
        RankBackdrop.draw(graphics, x, y, width, HEIGHT, profile == null ? null : background);
        Theme.roundedBoxOutline(graphics, x, y, width, HEIGHT, 4, Theme.BORDER);

        graphics.text(font, title.copy().withStyle(ChatFormatting.BOLD), x + PAD, y + 7, Theme.TEXT);
        Theme.fill(graphics, x + PAD, y + 19, x + width - PAD, y + 20, Theme.BORDER);

        if (profile == null) {
            RankEmblem.drawAt(graphics, x + PAD + BADGE_BOX / 2, y + 24 + BADGE_BOX / 2,
                    "unranked", false, BADGE);
            graphics.textWithWordWrap(font, status, x + PAD + BADGE_BOX + 12, y + 54,
                    width - PAD * 2 - BADGE_BOX - 12, statusColor);
            return;
        }

        String rank = Ranks.displayRank(profile);
        boolean placed = profile.placed && profile.rating != null;
        int accent = placed ? Ranks.colorOf(rank) : Theme.TEXT_FAINT;

        badgeTile(graphics, x + PAD, y + 24, rank, placed);

        int textX = x + PAD + BADGE_BOX + 12;
        int right = x + width - PAD;

        NameLabel label = NameLabel.of(profile.name);
        if (label == null) {
            Component name = Component.literal(profile.name);
            int scale = BigText.fit(font, name, right - textX, 2);
            BigText.draw(graphics, font, name, textX, y + 26, Theme.TEXT, scale, 1);
        } else {
            int wide = label.tagWidth(font, profile.name) + NameLabel.runWidthOf(font, profile.name);
            int scale = wide * 2 <= right - textX ? 2 : 1;
            var pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(textX, y + 26);
            pose.scale(scale, scale);
            try {
                NameLabel.drawTaggedLeft(graphics, font, label, profile.name, 0, 0, Theme.TEXT);
            } finally {
                pose.popMatrix();
            }
        }

        Component rankName = Component.literal(placed ? Ranks.nameOf(rank) : "Unranked")
                .copy().withStyle(ChatFormatting.BOLD);
        graphics.text(font, rankName, textX, y + 47, accent);

        String next = placed ? nextBadge(profile, rank) : null;
        int column = next == null ? right : right - NEXT_BADGE - 4;

        if (placed) {
            Component points = Component.translatable("vivet.ranked.points", profile.rating);
            graphics.text(font, points, column - font.width(points), y + 47, Theme.TEXT);
        }

        drawBar(graphics, font, textX, y + 60, right, column, next, profile, placed, accent);
        drawRecord(graphics, font, textX, y + 74, profile, placed);

        if (profile.banned && (profile.bannedUntil <= 0 || profile.bannedUntil > System.currentTimeMillis())) {
            Component note = profile.bannedUntil <= 0
                    ? Component.translatable("vivet.ranked.banned_forever")
                    : Component.translatable("vivet.ranked.banned_note", Ranks.remaining(profile.bannedUntil));
            graphics.text(font, note, right - font.width(note), y + 96, Theme.DANGER);
        }
    }

    private static void badgeTile(GuiGraphicsExtractor graphics, int x, int y, String rank,
                                  boolean placed) {
        Theme.roundedBox(graphics, x, y, BADGE_BOX, BADGE_BOX, 4, Theme.withAlpha(0x000000, 0x55));
        Theme.roundedBoxOutline(graphics, x, y, BADGE_BOX, BADGE_BOX, 4, Theme.BORDER);
        RankEmblem.drawAt(graphics, x + BADGE_BOX / 2, y + BADGE_BOX / 2, rank, placed, BADGE);
    }

    private static void drawBar(GuiGraphicsExtractor graphics, Font font, int x, int y, int right,
                                int barRight, String next, MojangAuth.Profile profile,
                                boolean placed, int accent) {
        if (!placed) {
            float done = profile.placementMatches == 0
                    ? 0.0F : profile.placementGames / (float) profile.placementMatches;

            Theme.progressBar(graphics, x, y, barRight - x, BAR_H, done, Theme.TEXT_MUTED);

            Component placing = Component.translatable("vivet.ranked.placing",
                    profile.placementGames, profile.placementMatches);
            graphics.text(font, placing, barRight - font.width(placing), y + BAR_H + 5,
                    Theme.TEXT_FAINT);
            return;
        }

        Integer ceiling = Ranks.nextThreshold(profile.rank);
        Component note;

        if (ceiling == null) {
            Theme.progressBar(graphics, x, y, barRight - x, BAR_H, 1.0F, accent);
            shine(graphics, x + 1, y + 1, barRight - 1, y + BAR_H - 1);
            note = placeNote(profile);
        } else {
            int base = Ranks.thresholdOf(profile.rank) == null ? 0 : Ranks.thresholdOf(profile.rank);
            float done = Math.max(0.0F, Math.min(1.0F,
                    (profile.rating - base) / (float) Math.max(1, ceiling - base)));

            Theme.progressBar(graphics, x, y, barRight - x, BAR_H, done, accent);
            note = Component.translatable("vivet.ranked.points_to_rank",
                    Math.max(0, ceiling - profile.rating),
                    Ranks.nameOf(Ranks.nextRankId(profile.rank)));
        }

        if (next != null) {
            RankEmblem.drawAt(graphics, right - NEXT_BADGE / 2, y + BAR_H / 2, next, true,
                    NEXT_BADGE);
        }

        graphics.text(font, note, barRight - font.width(note), y + BAR_H + 5, Theme.TEXT_FAINT);
    }

    private static String nextBadge(MojangAuth.Profile profile, String rank) {
        if (Ranks.nextThreshold(profile.rank) != null) return Ranks.nextRankId(profile.rank);
        return Ranks.nextSpeedTitle(rank);
    }

    private static Component placeNote(MojangAuth.Profile profile) {
        Integer place = profile.position;
        if (place == null || place <= 1) return Component.translatable("vivet.ranked.max");

        int chasing = Ranks.chasedPlace(place);
        Integer cutoff = Ranks.placeCutoff(chasing, profile.ladder);

        if (cutoff == null || cutoff <= profile.rating) {
            return Component.translatable("vivet.ranked.place_now", place);
        }
        return Component.translatable("vivet.ranked.points_to_place",
                cutoff - profile.rating, chasing);
    }

    private static void shine(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1) {
        int width = x1 - x0;
        if (width <= 0) return;

        int band = 14;
        int travel = width + band * 2;
        int head = x0 - band + (int) ((System.currentTimeMillis() / 26L) % travel);

        Clip.on(graphics, x0, y0, x1, y1);
        for (int i = 0; i < band; i++) {
            int column = head + i;
            if (column < x0 || column >= x1) continue;

            float along = i / (float) (band - 1);
            int alpha = (int) (0x5A * Math.sin(along * Math.PI));
            Theme.fill(graphics, column, y0, column + 1, y1, Theme.withAlpha(0xFFFFFF, alpha));
        }
        Clip.off(graphics);
    }

    private static void drawRecord(GuiGraphicsExtractor graphics, Font font, int x, int y,
                                   MojangAuth.Profile profile, boolean placed) {
        graphics.text(font, Component.translatable("vivet.ranked.record",
                        profile.wins, profile.losses, profile.draws),
                x, y, Theme.TEXT_MUTED);

        if (!placed) return;

        streak(graphics, font, x, y + 11, "vivet.ranked.streak_current", profile.streak,
                profile.streak > 0 ? Theme.SUCCESS : Theme.TEXT_MUTED);
        streak(graphics, font, x, y + 22, "vivet.ranked.streak_best", profile.bestStreak,
                Theme.TEXT);
    }

    private static void streak(GuiGraphicsExtractor graphics, Font font, int x, int y, String key,
                               int value, int color) {
        Component label = Component.translatable(key);
        graphics.text(font, label, x, y, Theme.TEXT_FAINT);
        graphics.text(font, Component.literal(String.valueOf(value)),
                x + font.width(label) + 4, y, color);
    }
}
