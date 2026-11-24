package com.vivet.speedrun.client.screen;

import com.vivet.speedrun.client.Cues;
import com.vivet.speedrun.client.ui.Backdrop;
import com.vivet.speedrun.client.ui.Clip;
import com.vivet.speedrun.client.ui.Cosmetics;
import com.vivet.speedrun.client.ui.Levels;
import com.vivet.speedrun.client.ui.NameLabel;
import com.vivet.speedrun.client.ui.RankTitle;
import com.vivet.speedrun.client.ui.Placements;
import com.vivet.speedrun.client.ui.PlayerHead;
import com.vivet.speedrun.client.ui.UiButtons;
import com.vivet.speedrun.client.ui.RankEmblem;
import com.vivet.speedrun.client.ui.Ranks;
import com.vivet.speedrun.client.ui.Plural;
import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.client.ui.Tip;
import com.vivet.speedrun.client.ui.VanillaIcon;
import com.vivet.speedrun.p2p.MojangAuth;
import com.vivet.speedrun.p2p.Profiles;
import com.vivet.speedrun.p2p.Social;
import com.vivet.speedrun.p2p.Wardrobe;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

public class ProfileScreen extends VivetScreen {
    private static final int MAX_W = 680;
    private static final int MAX_H = 383;
    private static final float ASPECT = 16.0F / 9.0F;

    private static final int MARGIN_X = 8;
    private static final int MARGIN_Y = 6;

    private static final float HEADER_W = 0.427F;
    private static final float HEADER_H = 0.128F;
    private static final float HEADER_GAP = 0.014F;

    private static final float LEFT_X = 0.030F;
    private static final float LEFT_RIGHT = 0.518F;
    private static final float GUTTER_X = 0.532F;

    private static final float ROW_Y = 0.038F;
    private static final float ROW_H = 0.075F;
    private static final float LEVEL_W = 0.290F;

    private static final float EMBLEM_Y = 0.138F;
    private static final float EMBLEM_W = 0.185F;
    private static final float EMBLEM_H = 0.351F;

    private static final float BADGES_X = 0.237F;
    private static final float BADGES_W = LEFT_RIGHT - BADGES_X;
    private static final float BADGES_H = 0.479F;

    private static final float TITLE_Y = 0.510F;
    private static final float TITLE_H = 0.106F;

    private static final float STATS_Y = 0.653F;
    private static final float STATS_W = LEFT_RIGHT - LEFT_X;

    private static final float ABOUT_X = 0.546F;
    private static final float ABOUT_W = 0.424F;
    private static final float ABOUT_TAB_W = 0.191F;

    private static final float ABOUT_Y = EMBLEM_Y;

    private static final float DOOR_ONE_X = ABOUT_X;

    private static final float DOOR_W = 0.068F;

    private static final float ABOUT_GAP = 0.023F;

    private static final float FLOOR = 0.968F;

    private static final float NAME_OF_PLATE = 0.45F;

    private static final float NAME_MIN = 0.5F;

    private static final int CARD_R = 8;
    private static final int PLATE_R = 5;
    private static final int CELL_R = 4;

    private static final int BUTTON_GAP = 5;

    private static final int BACK_GAP = 5;

    private final Screen parent;

    private final String uuid;

    private int cardX;
    private int cardY;
    private int cardW;
    private int cardH;

    private Region over;

    private Placements.Badge overBadge;

    private enum Region { BACK, BLOCK, REPORT, FOLLOW, ADD, DOOR_ONE, DOOR_TWO, ABOUT, WATCH }

    public ProfileScreen(Screen parent) {
        this(parent, null);
    }

    public ProfileScreen(Screen parent, String uuid) {
        super(Component.translatable("vivet.screen.profile"));
        this.parent = parent;

        String asked = Profiles.normalise(uuid);
        MojangAuth.Identity me = MojangAuth.identity();
        String own = me == null ? null : Profiles.normalise(me.uuid);

        this.uuid = asked != null && asked.equals(own) ? null : asked;
    }

    @Override
    protected void init() {
        super.init();

        Profiles.refreshMine();

        Wardrobe.refresh();

        Social.refresh();

        int roomW = Math.max(200,
                this.width - MARGIN_X * 2 - (UiButtons.SIDE + BACK_GAP));

        int roomH = Math.max(120,
                Math.round((this.height - MARGIN_Y * 2) / (1.0F + HEADER_H + HEADER_GAP)));

        this.cardW = Math.min(MAX_W, Math.min(roomW, Math.round(roomH * ASPECT)));
        this.cardH = Math.min(MAX_H, Math.min(roomH, Math.round(this.cardW / ASPECT)));
        this.cardW = Math.min(this.cardW, Math.round(this.cardH * ASPECT));

        this.cardX = Math.max(MARGIN_X,
                (this.width - this.cardW - (UiButtons.SIDE + BACK_GAP)) / 2);

        int above = fh(HEADER_H) + fh(HEADER_GAP);
        this.cardY = Math.max(above + 2, (this.height - this.cardH + above) / 2);
    }

    private int fx(float f) {
        return this.cardX + Math.round(this.cardW * f);
    }

    private int fy(float f) {
        return this.cardY + Math.round(this.cardH * f);
    }

    private int fw(float f) {
        return Math.max(1, Math.round(this.cardW * f));
    }

    private int fh(float f) {
        return Math.max(1, Math.round(this.cardH * f));
    }

    private int doorSide() {
        return fw(DOOR_W);
    }

    private int doorsTop() {
        return fy(FLOOR) - doorSide();
    }

    private int aboutHeight() {
        return Math.max(this.font.lineHeight * 2,
                doorsTop() - fh(ABOUT_GAP) - fy(ABOUT_Y));
    }

    private int rowMiddle() {
        return fy(ROW_Y) + fh(ROW_H) / 2;
    }

    private int buttonTop() {
        return rowMiddle() - UiButtons.SIDE / 2;
    }

    private int badgesRight() {
        return fx(BADGES_X) + fw(BADGES_W);
    }

    private int aboutRight() {
        return fx(ABOUT_X) + fw(ABOUT_W);
    }

    private int addLeft() {
        return badgesRight() - UiButtons.SIDE;
    }

    private int followLeft() {
        return addLeft() - UiButtons.SIDE - BUTTON_GAP;
    }

    private int blockLeft() {
        return aboutRight() - UiButtons.SIDE;
    }

    private int reportLeft() {
        return blockLeft() - UiButtons.SIDE - BUTTON_GAP;
    }

    private int watchLeft() {
        return reportLeft() - UiButtons.SIDE - BUTTON_GAP;
    }

    private boolean watchable(MojangAuth.Profile profile) {
        if (profile == null || mine()) return false;

        boolean hub = com.vivet.speedrun.p2p.Hub.isWatchable(this.uuid);
        boolean live = com.vivet.speedrun.p2p.Hub.isStaff() ? hub || profile.watchable
                : standing(profile).friend() ? hub
                : profile.watchable;
        if (!live) return false;
        return watchLeft() >= fx(ABOUT_X) + fw(ABOUT_TAB_W) + BUTTON_GAP;
    }

    private boolean canWatch() {
        return this.minecraft != null && this.minecraft.level == null
                && !com.vivet.speedrun.client.Spectating.isActive();
    }

    private int backLeft() {
        return this.cardX + this.cardW + BACK_GAP;
    }

    private int backTop() {
        return this.cardY;
    }

    private boolean mine() {
        return this.uuid == null;
    }

    private MojangAuth.Profile profile() {
        return mine() ? Profiles.mine() : Profiles.of(this.uuid);
    }

    @Override
    protected void drawScaled(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                              float partialTick) {
        MojangAuth.Profile profile = profile();

        Theme.fill(graphics, 0, 0, this.width, this.height, Theme.SCRIM);

        Backdrop.draw(graphics, Cosmetics.mediaOf(profile == null ? "" : profile.background),
                this.cardX, this.cardY, this.cardW, this.cardH, CARD_R);

        Theme.roundedBoxOutline(graphics, this.cardX, this.cardY, this.cardW, this.cardH, CARD_R,
                Theme.BORDER_STRONG);

        this.over = regionAt(mouseX, mouseY, profile);
        this.overBadge = null;

        drawButtons(graphics, profile);

        if (profile == null) {
            drawEmpty(graphics);
            drawWidgets(graphics, mouseX, mouseY, partialTick);
            drawTip(graphics, null, mouseX, mouseY);
            return;
        }

        drawHeader(graphics, profile);
        drawGutter(graphics);
        drawLevel(graphics, profile);
        drawEmblem(graphics, profile);
        drawStanding(graphics, profile);
        drawBadges(graphics, profile, mouseX, mouseY);
        drawStats(graphics, profile);
        drawAbout(graphics, profile);
        drawDoors(graphics, profile);
        drawJoined(graphics, profile);

        drawWidgets(graphics, mouseX, mouseY, partialTick);

        drawTip(graphics, profile, mouseX, mouseY);
    }

    private void drawGutter(GuiGraphicsExtractor graphics) {
        int x = fx(GUTTER_X);
        int top = fy(1.0F - FLOOR);
        int bottom = fy(FLOOR);
        if (bottom - top < 8) return;

        Theme.fill(graphics, x, top, x + 1, bottom, Theme.withAlpha(0xFFFFFFFF, 0x33));
    }

    private void drawEmpty(GuiGraphicsExtractor graphics) {
        boolean blocked = !mine() && Profiles.refused(this.uuid);

        String key = "vivet.profile.waiting";
        if (MojangAuth.token() == null) key = "vivet.profile.signed_out";
        else if (blocked) key = "vivet.profile.unavailable";

        int cx = this.cardX + this.cardW / 2;
        int cy = this.cardY + this.cardH / 2;

        if (blocked) {
            UiButtons.drawAt(graphics, UiButtons.Kind.BLOCK, cx, cy - 14,
                    UiButtons.toneOf(UiButtons.Kind.BLOCK, false, true));
        }

        Component line = Component.translatable(key);
        graphics.text(this.font, line, cx - this.font.width(line) / 2,
                blocked ? cy + 6 : cy - this.font.lineHeight / 2, Theme.TEXT_MUTED, true);
    }

    private void plate(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                       boolean lit) {
        Theme.roundedBox(graphics, x, y, width, height, PLATE_R,
                lit ? Theme.PLATE_LIT : Theme.PLATE);
        Theme.roundedBoxOutline(graphics, x, y, width, height, PLATE_R,
                lit ? Theme.PLATE_EDGE_LIT : Theme.PLATE_EDGE);
    }

    private void fit(GuiGraphicsExtractor graphics, Component text, int x, int y, int room,
                     int colour, boolean shadow) {
        int width = this.font.width(text);

        if (width <= room || room <= 0) {
            graphics.text(this.font, text, x, y, colour, shadow);
            return;
        }

        float scale = Math.max(0.66F, room / (float) width);

        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);

        String cut = this.font.plainSubstrByWidth(text.getString(), Math.round(room / scale));
        graphics.text(this.font, Component.literal(cut), 0, 0, colour, shadow);

        pose.popMatrix();
    }

    private void fitCentred(GuiGraphicsExtractor graphics, Component text, int cx, int y, int room,
                            int colour, boolean shadow) {
        int width = Math.min(room, this.font.width(text));
        fit(graphics, text, cx - width / 2, y, room, colour, shadow);
    }

    private int heading(GuiGraphicsExtractor graphics, Component text, int x, int y, int width) {
        fit(graphics, text.copy().withStyle(ChatFormatting.BOLD), x, y, width, Theme.TEXT, true);

        int rule = y + this.font.lineHeight + 4;
        Theme.divider(graphics, x, rule, width);

        return rule + 5;
    }

    private int headerHeight() {
        return fh(HEADER_H);
    }

    private int headerTop() {
        return this.cardY - fh(HEADER_GAP) - headerHeight();
    }

    private int headSize() {
        int room = headerHeight() - 10;
        return Math.max(8, room / 8 * 8);
    }

    private float nameScale(int height, int room, int natural) {
        float wanted = height * NAME_OF_PLATE / this.font.lineHeight;
        float fits = natural <= 0 ? wanted : room / (float) natural;

        return Math.max(NAME_MIN, Math.min(wanted, fits));
    }

    private void drawHeader(GuiGraphicsExtractor graphics, MojangAuth.Profile profile) {
        int x = this.cardX;
        int y = headerTop();
        int height = headerHeight();
        int width = fw(HEADER_W);

        plate(graphics, x, y, width, height, false);

        int head = headSize();
        int framed = PlayerHead.framedSize(head);
        int rank = Ranks.colorOf(Ranks.displayRank(profile));

        PlayerHead.drawFramed(graphics, profile.name, profile.uuid,
                x + width - 8 - framed, y + (height - framed) / 2, head,
                profile.placed ? rank : Theme.TEXT_FAINT);

        RankTitle.remember(profile.name, profile.uuid, Ranks.displayRank(profile),
                profile.placed);

        NameLabel label = NameLabel.byId(profile.label);
        String name = profile.name == null ? "?" : profile.name;
        Component suffix = Component.translatable("vivet.profile.possessive");

        int room = width - 12 - 10 - framed - 8;
        int natural = NameLabel.tagWidthOf(this.font, label, name)
                + NameLabel.runWidthOf(this.font, name)
                + this.font.width(suffix);

        float scale = nameScale(height, room, natural);

        int textY = y + Math.round((height - this.font.lineHeight * scale) / 2.0F);

        Clip.on(graphics, x + 12, y + 1, x + 12 + room, y + height - 1);
        try {
            var pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(x + 12, textY);
            pose.scale(scale, scale);

            int drawn = NameLabel.drawTaggedLeft(graphics, this.font, label, name, 0, 0,
                    Theme.TEXT);
            graphics.text(this.font, suffix, drawn, 0, Theme.TEXT_MUTED, true);

            pose.popMatrix();
        } finally {
            Clip.off(graphics);
        }
    }

    private void drawButtons(GuiGraphicsExtractor graphics, MojangAuth.Profile profile) {
        button(graphics, Region.BACK, UiButtons.Kind.BACK, backLeft(), backTop(), false);

        if (profile == null || mine()) return;

        boolean blocked = standing(profile).blocked();

        button(graphics, Region.REPORT, UiButtons.Kind.REPORT, reportLeft(), buttonTop(), false);

        if (watchable(profile)) {
            UiButtons.button(graphics, UiButtons.Kind.WATCH, watchLeft(), buttonTop(),
                    this.over == Region.WATCH, canWatch(), false);
        }
        button(graphics, Region.BLOCK, UiButtons.blockSign(blocked), blockLeft(), buttonTop(),
               blocked);
    }

    private Social.Standing standing(MojangAuth.Profile profile) {
        return Social.standingWith(profile);
    }

    private void button(GuiGraphicsExtractor graphics, Region region, UiButtons.Kind kind,
                        int x, int y, boolean on) {
        UiButtons.button(graphics, kind, x, y, this.over == region, true, on);
    }

    private void drawLevel(GuiGraphicsExtractor graphics, MojangAuth.Profile profile) {
        int x = fx(LEFT_X);
        int y = fy(ROW_Y);
        int width = fw(LEVEL_W);
        int height = fh(ROW_H);

        plate(graphics, x, y, width, height, false);

        int level = Math.max(1, profile.level);
        int colour = Levels.colorOf(level);

        int textY = y + (height - this.font.lineHeight) / 2;
        int cursor = x + 10;

        Component lvl = Component.translatable("vivet.profile.level", level);
        graphics.text(this.font, lvl, cursor, textY, colour, true);
        cursor += this.font.width(lvl) + 9;

        Theme.fill(graphics, cursor, y + 6, cursor + 1, y + height - 6,
                Theme.withAlpha(0xFFFFFFFF, 0x33));
        cursor += 10;

        fit(graphics, Plural.of(profile.followers, "vivet.profile.followers"),
                cursor, textY, x + width - 8 - cursor, Theme.TEXT, true);

        if (mine()) return;

        Social.Standing standing = standing(profile);

        button(graphics, Region.FOLLOW,
               standing.following() ? UiButtons.Kind.MINUS : UiButtons.Kind.PLUS,
               followLeft(), buttonTop(), standing.following());

        UiButtons.button(graphics, addIcon(standing), addLeft(), buttonTop(),
                this.over == Region.ADD, !shut(profile, standing),
                standing.friend() || standing.outgoing());
    }

    private static boolean shut(MojangAuth.Profile profile, Social.Standing standing) {
        return profile != null && !profile.allowsRequests && "add".equals(addAction(standing));
    }

    private static UiButtons.Kind addIcon(Social.Standing standing) {
        if (standing.friend()) return UiButtons.Kind.REMOVE;
        if (standing.incoming()) return UiButtons.Kind.ADD;
        if (standing.outgoing()) return UiButtons.Kind.REMOVE;
        return UiButtons.Kind.ADD;
    }

    private static String addAction(Social.Standing standing) {
        if (standing.friend()) return "unadd";
        if (standing.incoming()) return "accept";
        if (standing.outgoing()) return "cancel";
        return "add";
    }

    private void drawEmblem(GuiGraphicsExtractor graphics, MojangAuth.Profile profile) {
        int x = fx(LEFT_X);
        int y = fy(EMBLEM_Y);
        int width = fw(EMBLEM_W);
        int height = fh(EMBLEM_H);

        Theme.roundedBox(graphics, x, y, width, height, PLATE_R, Theme.PANEL_SUNKEN);
        Theme.roundedBoxOutline(graphics, x, y, width, height, PLATE_R, Theme.PLATE_EDGE);

        Wardrobe.Item worn = profile.emblem == null || profile.emblem.isEmpty()
                ? null : Wardrobe.item(profile.emblem);

        if (worn != null) {
            Cosmetics.ready(worn);
            Cosmetics.draw(graphics, this.font, worn, x + 4, y + 4, width - 8, height - 8,
                    System.currentTimeMillis(), true, false);
            return;
        }

        int cx = x + width / 2;
        int cy = y + height / 2;
        String rank = Ranks.displayRank(profile);

        if (Math.min(width, height) >= RankEmblem.bodySize() + 8) {
            RankEmblem.draw(graphics, cx, cy, rank, profile.placed);
        } else {
            RankEmblem.drawCompact(graphics, cx, cy, rank, profile.placed);
        }
    }

    private void drawStanding(GuiGraphicsExtractor graphics, MojangAuth.Profile profile) {
        int x = fx(LEFT_X);
        int width = fw(EMBLEM_W);
        int y = fy(TITLE_Y);
        int height = fh(TITLE_H);

        plate(graphics, x, y, width, height, false);

        String rank = Ranks.displayRank(profile);
        int colour = profile.placed ? Ranks.colorOf(rank) : Theme.TEXT_FAINT;

        int badge = Math.max(8, Math.min(height - 8, width / 4));
        int badgeX = x + width - 6 - badge / 2;

        RankEmblem.drawAt(graphics, badgeX, y + height / 2, rank, profile.placed, badge);

        int room = width - 10 - badge - 8;

        Component title = Component.literal(profile.placed
                ? Ranks.nameOf(rank).toUpperCase(java.util.Locale.ROOT)
                : Component.translatable("vivet.profile.unranked").getString());

        Component under = profile.placed && profile.position != null
                ? Component.translatable("vivet.profile.position", profile.position)
                : Component.translatable("vivet.profile.placements",
                        profile.placementGames, profile.placementMatches);

        int lineY = y + height / 2 - this.font.lineHeight - 1;

        fit(graphics, title.copy().withStyle(ChatFormatting.BOLD), x + 10, lineY, room, colour,
                true);
        fit(graphics, under, x + 10, lineY + this.font.lineHeight + 3, room,
                Theme.fade(colour, 0.7F), true);
    }

    private static final int BADGE_COLUMNS = 4;

    private void drawBadges(GuiGraphicsExtractor graphics, MojangAuth.Profile profile,
                            int mouseX, int mouseY) {
        int x = fx(BADGES_X);
        int y = fy(EMBLEM_Y);
        int width = fw(BADGES_W);
        int height = fh(BADGES_H);

        plate(graphics, x, y, width, height, false);

        int pad = 6;
        int top = heading(graphics, Component.translatable("vivet.profile.badges"),
                x + pad, y + 6, width - pad * 2);

        List<Placements.Badge> badges = Placements.fold(profile.badges);

        if (badges.isEmpty()) {
            Component none = Component.translatable("vivet.profile.no_badges");
            fitCentred(graphics, none, x + width / 2, y + height / 2 - this.font.lineHeight / 2,
                    width - 10, Theme.TEXT_FAINT, false);
            return;
        }

        int gap = 4;
        int cell = (width - pad * 2 - gap * (BADGE_COLUMNS - 1)) / BADGE_COLUMNS;
        int rows = Math.max(1, (y + height - pad - top + gap) / (cell + gap));

        for (int i = 0; i < badges.size() && i < rows * BADGE_COLUMNS; i++) {
            int cellX = x + pad + (i % BADGE_COLUMNS) * (cell + gap);
            int cellY = top + (i / BADGE_COLUMNS) * (cell + gap);

            boolean on = mouseX >= cellX && mouseX < cellX + cell
                    && mouseY >= cellY && mouseY < cellY + cell;

            Placements.draw(graphics, this.font, badges.get(i), cellX, cellY, cell, cell, on);
            if (on) this.overBadge = badges.get(i);
        }
    }

    private void statCell(GuiGraphicsExtractor graphics, VanillaIcon.Kind icon, int x, int y,
                          int width, int height, Component text, int colour, Runnable extra) {
        Theme.roundedBox(graphics, x, y, width, height, CELL_R, Theme.PLATE);
        Theme.roundedBoxOutline(graphics, x, y, width, height, CELL_R, Theme.PLATE_EDGE);

        int box = Math.min(16, height - 4);
        VanillaIcon.draw(graphics, icon, x + 6 + box / 2, y + height / 2, box);

        int rule = x + 6 + box + 5;
        Theme.fill(graphics, rule, y + 4, rule + 1, y + height - 4,
                Theme.withAlpha(0xFFFFFFFF, 0x2A));

        int textX = rule + 6;
        int textY = y + (height - this.font.lineHeight) / 2;

        if (text != null) {
            fit(graphics, text, textX, textY, x + width - 5 - textX, colour, true);
            return;
        }
        if (extra != null) extra.run();
    }

    private void drawStats(GuiGraphicsExtractor graphics, MojangAuth.Profile profile) {
        MojangAuth.Shows shows = Profiles.showsOf(profile);

        int x = fx(LEFT_X);
        int y = fy(STATS_Y);
        int width = fw(STATS_W);
        int height = fy(FLOOR) - y;

        plate(graphics, x, y, width, height, false);

        int pad = 6;
        int top = heading(graphics, Component.translatable("vivet.profile.stats"),
                x + pad, y + 6, width - pad * 2);

        int gap = 4;
        int cell = (width - pad * 2 - gap) / 2;
        int rowH = Math.max(this.font.lineHeight + 4, (y + height - pad - top - gap * 2) / 3);

        int left = x + pad;
        int right = left + cell + gap;

        Component elo = profile.rating == null
                ? Component.translatable("vivet.profile.stat.elo_none")
                : Component.translatable("vivet.profile.stat.elo", profile.rating);

        statCell(graphics, VanillaIcon.Kind.TOTEM, left, top, cell, rowH, elo,
                profile.rating == null ? Theme.TEXT_MUTED : Theme.TEXT, null);

        int recordY = top;
        statCell(graphics, VanillaIcon.Kind.BOOK, right, recordY, cell, rowH,
                shows.record ? null : Component.translatable("vivet.profile.record_hidden"),
                Theme.TEXT_FAINT,
                shows.record ? () -> drawRecord(graphics, profile, right, recordY, cell, rowH)
                             : null);

        Component hours = !shows.hours
                ? Component.translatable("vivet.profile.hours_hidden")
                : profile.hours == null
                        ? Component.translatable("vivet.profile.stat.hours_none")
                        : Component.translatable("vivet.profile.hours", profile.hours);

        statCell(graphics, VanillaIcon.Kind.CLOCK, left, top + rowH + gap, cell, rowH, hours,
                shows.hours && profile.hours != null ? Theme.TEXT : Theme.TEXT_FAINT, null);

        Component streak = shows.streak
                ? Component.translatable("vivet.profile.streak", profile.streak)
                : Component.translatable("vivet.profile.streak_hidden");

        statCell(graphics, VanillaIcon.Kind.ENDER_EYE, right, top + rowH + gap, cell, rowH, streak,
                !shows.streak ? Theme.TEXT_FAINT
                        : profile.streak > 0 ? Theme.SUCCESS : Theme.TEXT_MUTED, null);

        Component matches = !shows.matches
                ? Component.translatable("vivet.profile.matches_hidden")
                : Component.translatable("vivet.profile.matches",
                        profile.matches == null ? 0 : profile.matches);

        statCell(graphics, VanillaIcon.Kind.SWORD, left, top + (rowH + gap) * 2, cell, rowH,
                matches, shows.matches ? Theme.TEXT : Theme.TEXT_FAINT, null);

        Component best = shows.bestStreak
                ? Component.translatable("vivet.profile.stat.best_streak", profile.bestStreak)
                : Component.translatable("vivet.profile.stat.best_streak_hidden");

        statCell(graphics, VanillaIcon.Kind.ENDER_PEARL, right, top + (rowH + gap) * 2, cell, rowH,
                best, !shows.bestStreak ? Theme.TEXT_FAINT
                        : profile.bestStreak > 0 ? Theme.WARNING : Theme.TEXT_MUTED, null);
    }

    private void drawRecord(GuiGraphicsExtractor graphics, MojangAuth.Profile profile,
                            int x, int y, int width, int height) {
        int box = Math.min(16, height - 4);
        int cursor = x + 6 + box + 5 + 6;
        int textY = y + (height - this.font.lineHeight) / 2;
        int stop = x + width - 5;

        int[] counts = { profile.wins, profile.losses, profile.draws };
        String[] keys = { "vivet.profile.wld.w", "vivet.profile.wld.l", "vivet.profile.wld.d" };
        int[] colours = { Theme.SUCCESS, Theme.DANGER, Theme.TEXT_MUTED };

        for (int i = 0; i < 3; i++) {
            if (i > 0) {
                Component slash = Component.literal(" / ");
                if (cursor + this.font.width(slash) > stop) return;
                graphics.text(this.font, slash, cursor, textY, Theme.TEXT_FAINT, true);
                cursor += this.font.width(slash);
            }

            Component part = Component.translatable(keys[i], counts[i]);
            if (cursor + this.font.width(part) > stop) return;

            graphics.text(this.font, part, cursor, textY, colours[i], true);
            cursor += this.font.width(part);
        }
    }

    private void drawAbout(GuiGraphicsExtractor graphics, MojangAuth.Profile profile) {
        int tabX = fx(ABOUT_X);
        int tabY = fy(ROW_Y);
        int tabW = fw(ABOUT_TAB_W);
        int tabH = fh(ROW_H);

        plate(graphics, tabX, tabY, tabW, tabH, false);
        fit(graphics, Component.translatable("vivet.profile.about"), tabX + 10,
                tabY + (tabH - this.font.lineHeight) / 2, tabW - 16, Theme.TEXT, true);

        int x = fx(ABOUT_X);
        int y = fy(ABOUT_Y);
        int width = fw(ABOUT_W);
        int height = aboutHeight();

        boolean lit = this.over == Region.ABOUT;
        Theme.roundedBox(graphics, x, y, width, height, PLATE_R,
                Theme.withAlpha(lit ? Theme.PLATE_LIT : Theme.PANEL, 0x80));
        Theme.roundedBoxOutline(graphics, x, y, width, height, PLATE_R,
                lit ? Theme.PLATE_EDGE_LIT : Theme.PLATE_EDGE);

        int room = width - 16;
        int line = y + 6;
        int bottom = y + height - 6;

        String bio = profile.bio == null ? "" : profile.bio;

        if (bio.isBlank()) {
            fit(graphics, Component.translatable(mine()
                            ? "vivet.profile.bio_empty_mine" : "vivet.profile.bio_empty"),
                    x + 8, line, room, Theme.TEXT_FAINT, false);
        } else {
            for (String entry : wrap(bio, room)) {
                if (line + this.font.lineHeight > bottom) break;
                graphics.text(this.font, Component.literal(entry), x + 8, line, Theme.TEXT, true);
                line += this.font.lineHeight;
            }
        }
    }

    private List<String> wrap(String text, int room) {
        List<String> lines = new java.util.ArrayList<>();
        if (room <= 0) return lines;

        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (word.isEmpty()) continue;

            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && this.font.width(candidate) > room) {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(candidate);
            }

            while (this.font.width(line.toString()) > room && line.length() > 1) {
                String cut = this.font.plainSubstrByWidth(line.toString(), room);
                if (cut.isEmpty()) break;
                lines.add(cut);
                line.delete(0, cut.length());
            }
        }

        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    private int doorGap() {
        return Math.max(4, fh(ABOUT_GAP));
    }

    private void drawDoors(GuiGraphicsExtractor graphics, MojangAuth.Profile profile) {
        int y = doorsTop();
        int width = doorSide();
        int height = width;

        int first = fx(DOOR_ONE_X);

        int second = first + width + doorGap();

        if (mine()) {
            door(graphics, Region.DOOR_ONE, VanillaIcon.Kind.COMPARATOR, first, y, width, height);
            return;
        }

        MojangAuth.Shows shows = Profiles.showsOf(profile);

        if (shows.collection) {
            door(graphics, Region.DOOR_ONE, VanillaIcon.Kind.KNOWLEDGE, first, y, width, height);
        }
        if (shows.locker) {
            door(graphics, Region.DOOR_TWO, VanillaIcon.Kind.ARMOUR_STAND, second, y, width,
                 height);
        }
    }

    private void door(GuiGraphicsExtractor graphics, Region region, VanillaIcon.Kind icon,
                      int x, int y, int width, int height) {
        plate(graphics, x, y, width, height, this.over == region);

        int box = Math.max(16, Math.min(width - 8, height - 8) / 16 * 16);
        VanillaIcon.draw(graphics, icon, x + width / 2, y + height / 2, box);
    }

    private void drawJoined(GuiGraphicsExtractor graphics, MojangAuth.Profile profile) {
        if (profile.joined == null) return;

        ZonedDateTime when = Instant.ofEpochMilli(profile.joined).atZone(ZoneId.systemDefault());

        Component text = Component.translatable("vivet.profile.joined",
                when.getMonthValue() + "/" + when.getDayOfMonth() + "/" + when.getYear());

        int width = this.font.width(text);

        graphics.text(this.font, text, aboutRight() - width,
                fy(FLOOR) - this.font.lineHeight, Theme.TEXT_FAINT, true);
    }

    private void drawTip(GuiGraphicsExtractor graphics, MojangAuth.Profile profile,
                         int mouseX, int mouseY) {
        if (this.overBadge != null) {
            Tip.draw(graphics, this.font, Placements.tip(this.overBadge, this.font),
                     mouseX, mouseY, this.width, this.height);
            return;
        }

        if (this.over == null) return;

        Social.Standing standing = standing(profile);

        String key = switch (this.over) {
            case BACK -> "vivet.profile.tip.back";
            case BLOCK -> standing.blocked()
                    ? "vivet.profile.tip.unblock" : "vivet.profile.tip.block";
            case REPORT -> "vivet.profile.tip.report";
            case WATCH -> "vivet.profile.tip.watch";
            case FOLLOW -> standing.following()
                    ? "vivet.profile.tip.unfollow" : "vivet.profile.tip.follow";
            case ADD -> shut(profile, standing) ? "vivet.profile.tip.add_shut"
                    : switch (addAction(standing)) {
                        case "unadd" -> "vivet.profile.tip.unadd";
                        case "accept" -> "vivet.profile.tip.accept";
                        case "cancel" -> "vivet.profile.tip.cancel";
                        default -> "vivet.profile.tip.add";
                    };
            case ABOUT -> mine() ? "vivet.profile.tip.about" : null;

            case DOOR_ONE -> mine() ? "vivet.profile.tip.settings" : "vivet.profile.collections";
            case DOOR_TWO -> "vivet.profile.locker";
        };

        if (key == null) return;

        List<Tip.Line> lines = Tip.lines();
        Tip.wrap(lines, Component.translatable(key), Theme.TEXT_MUTED, this.font);
        Tip.draw(graphics, this.font, lines, mouseX, mouseY, this.width, this.height);
    }

    private Region regionAt(int mouseX, int mouseY, MojangAuth.Profile profile) {
        int side = UiButtons.SIDE;
        int buttonY = buttonTop();

        if (in(mouseX, mouseY, backLeft(), backTop(), side, side)) return Region.BACK;

        if (profile == null) return null;

        if (!mine()) {
            if (in(mouseX, mouseY, reportLeft(), buttonY, side, side)) return Region.REPORT;
            if (watchable(profile) && in(mouseX, mouseY, watchLeft(), buttonY, side, side)) {
                return Region.WATCH;
            }
            if (in(mouseX, mouseY, blockLeft(), buttonY, side, side)) return Region.BLOCK;
            if (in(mouseX, mouseY, followLeft(), buttonY, side, side)) return Region.FOLLOW;
            if (in(mouseX, mouseY, addLeft(), buttonY, side, side)) return Region.ADD;
        }

        int doorY = doorsTop();
        int door = doorSide();
        int first = fx(DOOR_ONE_X);
        int second = first + door + doorGap();

        if (mine()) {
            if (in(mouseX, mouseY, first, doorY, door, door)) return Region.DOOR_ONE;
        } else {
            MojangAuth.Shows shows = Profiles.showsOf(profile);
            if (shows.collection && in(mouseX, mouseY, first, doorY, door, door)) {
                return Region.DOOR_ONE;
            }
            if (shows.locker && in(mouseX, mouseY, second, doorY, door, door)) {
                return Region.DOOR_TWO;
            }
        }

        if (mine() && in(mouseX, mouseY, fx(ABOUT_X), fy(ABOUT_Y), fw(ABOUT_W), aboutHeight())) {
            return Region.ABOUT;
        }
        return null;
    }

    private static boolean in(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private int scaled(double value) {
        return (int) Math.round(value / uiScale());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        MojangAuth.Profile profile = profile();
        Region region = regionAt(scaled(event.x()), scaled(event.y()), profile);
        if (region == null) return super.mouseClicked(event, doubleClick);

        Social.Standing standing = standing(profile);

        switch (region) {
            case BACK -> {
                Cues.confirm();
                onClose();
            }

            case FOLLOW -> {
                Cues.confirm();
                Social.act(standing.following() ? "unfollow" : "follow", this.uuid);
            }
            case ADD -> {
                if (shut(profile, standing)) {
                    Cues.nothingHappened();
                } else {
                    Cues.confirm();
                    Social.act(addAction(standing), this.uuid);
                }
            }
            case BLOCK -> {
                Cues.confirm();
                Social.act(standing.blocked() ? "unblock" : "block", this.uuid);
            }

            case WATCH -> {
                if (!canWatch()) {
                    Cues.nothingHappened();
                } else {
                    com.vivet.speedrun.client.Spectating.start(this.minecraft, this, this.uuid,
                            nameOn(profile));
                }
            }

            case REPORT -> {
                Cues.confirm();
                this.minecraft.gui.setScreen(
                        ReportScreen.aboutPlayer(this, this.uuid,
                                profile == null || profile.name == null ? "" : profile.name));
            }

            case DOOR_ONE -> {
                Cues.confirm();
                this.minecraft.gui.setScreen(mine()
                        ? new ProfileSettingsScreen(this)
                        : new CollectionScreen(this, this.uuid, nameOn(profile)));
            }
            case DOOR_TWO -> {
                Cues.confirm();
                this.minecraft.gui.setScreen(new LockerScreen(this, this.uuid, nameOn(profile)));
            }
            case ABOUT -> {
                Cues.confirm();
                this.minecraft.gui.setScreen(new ProfileSettingsScreen(this));
            }
            default -> Cues.nothingHappened();
        }
        return true;
    }

    private static String nameOn(MojangAuth.Profile profile) {
        return profile == null || profile.name == null ? "" : profile.name;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
