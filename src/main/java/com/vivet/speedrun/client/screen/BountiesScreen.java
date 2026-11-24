package com.vivet.speedrun.client.screen;

import com.vivet.speedrun.client.Cues;
import com.vivet.speedrun.client.ui.BigText;
import com.vivet.speedrun.client.ui.RankEmblem;
import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.client.ui.Tip;
import com.vivet.speedrun.client.ui.UiButtons;
import com.vivet.speedrun.client.ui.VanillaIcon;
import com.vivet.speedrun.game.Difficulty;
import com.vivet.speedrun.p2p.Bounties;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class BountiesScreen extends VivetScreen {
    private static final int MAX_WIDTH = 860;
    private static final int MIN_WIDTH = 620;
    private static final int PANEL_R = 8;
    private static final int PAD = Theme.PANEL_PAD;
    private static final int PANEL_GAP = 10;
    private static final int BACK_GAP = 8;

    private static final int RAIL_W = 236;
    private static final int TAB_H = 24;
    private static final int TAB_GAP = 6;

    private static final int CARD_MIN = 58;
    private static final int CARD_MAX = 84;
    private static final int CARD_GAP = 6;
    private static final int CARD_R = 5;
    private static final int ICON = 32;
    private static final int LADDER_ROW = 16;
    private static final int TAKE_H = 24;

    public static final int CASH = 0xFF4FE08A;
    private static final int CASH_DIM = 0xFF2E8A55;
    private static final int BLURPLE = 0xFF5865F2;

    private enum Tab { FIRSTS, SECRETS, MONTHLY, WINNERS }

    private final Screen parent;
    private Tab tab = Tab.FIRSTS;

    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int pageLeft;
    private int pageWidth;

    private final float[] lift = new float[4];

    private int cardH = CARD_MIN;

    public BountiesScreen(Screen parent) {
        super(Component.translatable("vivet.bounties.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        Bounties.refresh();
        Bounties.refreshMe();

        int side = UiButtons.SIDE + BACK_GAP;
        this.panelWidth = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, this.width - 16 - side * 2));
        this.panelHeight = Math.max(360, Math.min(440, this.height - 24));
        this.left = (this.width - this.panelWidth) / 2;
        this.top = Math.max(4, (this.height - this.panelHeight) / 2);
        this.pageLeft = this.left + RAIL_W + PANEL_GAP;
        this.pageWidth = this.left + this.panelWidth - this.pageLeft;
    }

    @Override
    public void tick() {
        super.tick();
        Bounties.refresh();
        Bounties.refreshMe();
    }

    private static final int[] STEPS = { 0, 100, 1_000, 5_000, 10_000, 50_000 };

    private final List<int[]> ladderRows = new ArrayList<>();

    private static int shownBand() {
        int pick = com.vivet.speedrun.config.ConfigManager.get().bountiesPick;
        return pick >= 0 ? pick : 1_000;
    }

    private static int biggerBand(int shown) {
        if (shown < 10_000) return 10_000;
        if (shown < 50_000) return 50_000;
        return -1;
    }

    private static List<Integer> ladder(int now) {
        List<Integer> rows = new ArrayList<>();
        for (int step : STEPS) rows.add(step);
        if (!rows.contains(now)) rows.add(now);
        rows.sort(Integer::compare);
        return rows;
    }

    private static Component atPlayers(String key, int band) {
        return band == 0 ? Component.translatable(key + "_under") : Component.translatable(key, players(band));
    }

    private static String players(int band) {
        return String.format(java.util.Locale.ROOT, "%,d", band);
    }

    @Override
    protected void drawScaled(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Theme.fill(graphics, 0, 0, this.width, this.height, Theme.SCRIM);
        panel(graphics, this.left, this.top, RAIL_W, this.panelHeight);
        panel(graphics, this.pageLeft, this.top, this.pageWidth, this.panelHeight);
        UiButtons.button(graphics, UiButtons.Kind.BACK, backX(), this.top, onBack(mouseX, mouseY), true, false);

        Bounties.View view = Bounties.view();
        drawRail(graphics, view, mouseX, mouseY);
        List<Tip.Line> tip = drawPage(graphics, view, mouseX, mouseY, partialTick);

        drawWidgets(graphics, mouseX, mouseY, partialTick);

        if (tip != null) Tip.draw(graphics, this.font, tip, mouseX, mouseY, this.width, this.height);
    }

    private void panel(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        Theme.roundedBox(graphics, x, y, w, h, PANEL_R, Theme.PANEL);
        Theme.roundedBoxOutline(graphics, x, y, w, h, PANEL_R, Theme.BORDER);
    }

    private void drawRail(GuiGraphicsExtractor graphics, Bounties.View view, int mouseX, int mouseY) {
        int x = this.left + PAD;
        int w = RAIL_W - PAD * 2;
        int y = this.top + PAD;
        int now = Math.max(0, view.players);
        int target = shownBand();
        int bigger = biggerBand(target);

        graphics.text(this.font, this.title.copy().withStyle(ChatFormatting.BOLD), x, y, Theme.TEXT);
        y += 20;

        graphics.text(this.font, atPlayers("vivet.bounties.rail.now", target), x, y, Theme.TEXT_MUTED);
        y += 12;
        Component total = Component.literal(Bounties.money(Bounties.totalAt(view, target)));
        BigText.draw(graphics, this.font, total, x, y, CASH, BigText.fit(this.font, total, w, 3), 0);
        y += BigText.height(3) + 8;

        if (bigger > 0) {
            graphics.text(this.font, atPlayers("vivet.bounties.rail.at", bigger), x, y, Theme.TEXT_MUTED);
            y += 12;
            Component later = Component.literal(Bounties.money(Bounties.totalAt(view, bigger)));
            BigText.draw(graphics, this.font, later, x, y, CASH, BigText.fit(this.font, later, w, 2), 0);
        } else {
            y += 12;
        }
        y += BigText.height(2) + PAD;

        Theme.divider(graphics, x, y, w);
        y += PAD;

        graphics.text(this.font, Component.translatable("vivet.bounties.rail.ladder").withStyle(ChatFormatting.BOLD),
                x, y, Theme.TEXT);
        y += 15;
        graphics.text(this.font, Component.translatable("vivet.bounties.rail.players"), x + 6, y, Theme.TEXT_FAINT);
        Component prizeHead = Component.translatable("vivet.bounties.rail.top_prize");
        graphics.text(this.font, prizeHead, x + w - 6 - this.font.width(prizeHead), y, Theme.TEXT_FAINT);
        y += 12;

        int takeY = this.top + this.panelHeight - PAD - TAKE_H;
        int stepsY = takeY - 10 - 3 * 12;

        int band = Bounties.bandOf(now);
        List<Integer> rows = ladder(band);
        int rowH = Math.max(LADDER_ROW, Math.min(22, (stepsY - PAD - y) / rows.size()));
        this.ladderRows.clear();
        for (int i = 0; i < rows.size(); i++) {
            int ry = y + i * rowH;
            if (ry + LADDER_ROW > stepsY - 8) break;
            int step = rows.get(i);
            boolean today = step == band;
            boolean picked = step == target;
            boolean over = inside(mouseX, mouseY, x, ry, w, LADDER_ROW);
            this.ladderRows.add(new int[] { ry, LADDER_ROW, step });
            if (picked || over) {
                Theme.roundedBox(graphics, x, ry, w, LADDER_ROW, 4,
                        picked ? Theme.PLATE_LIT : Theme.mix(Theme.PLATE, Theme.PLATE_LIT, 0.5F));
            }
            if (picked) Theme.roundedBoxOutline(graphics, x, ry, w, LADDER_ROW, 4, CASH);
            Component who = step == 0 ? Component.translatable("vivet.bounties.rail.under")
                    : Component.literal(players(step));
            if (today) who = Component.translatable("vivet.bounties.rail.today", who);
            int ly = Theme.textTop(ry, LADDER_ROW);
            graphics.text(this.font, who, x + 6, ly, picked || today ? Theme.TEXT : Theme.TEXT_MUTED);
            Component prize = Component.literal(Bounties.money(Bounties.fullAt(step)));
            graphics.text(this.font, prize, x + w - 6 - this.font.width(prize), ly,
                    picked || today ? CASH : Theme.mix(CASH_DIM, CASH, i / (float) Math.max(1, rows.size() - 1)));
        }

        Bounties.Me me = Bounties.me();
        boolean in = me != null && me.optedIn;
        if (in) {
            graphics.text(this.font, Component.translatable("vivet.bounties.in").withStyle(ChatFormatting.BOLD), x,
                    stepsY, CASH);
            Component wonLabel = Component.translatable("vivet.bounties.in.won");
            graphics.text(this.font, wonLabel, x, stepsY + 12, Theme.TEXT_MUTED);
            Component won = Component.literal(Bounties.money(me.won));
            graphics.text(this.font, won, x + w - this.font.width(won), stepsY + 12, CASH);
            Component readyLabel = Component.translatable("vivet.bounties.in.balance");
            graphics.text(this.font, readyLabel, x, stepsY + 24, Theme.TEXT_MUTED);
            Component ready = Component.literal(Bounties.money(me.balance));
            graphics.text(this.font, ready, x + w - this.font.width(ready), stepsY + 24,
                    me.balance > 0 ? CASH : Theme.TEXT_FAINT);
        } else {
            for (int i = 0; i < 3; i++) {
                int sy = stepsY + i * 12;
                graphics.text(this.font, Component.literal(String.valueOf(i + 1)).withStyle(ChatFormatting.BOLD), x, sy, CASH);
                graphics.text(this.font, Component.translatable("vivet.bounties.step." + (i + 1)), x + 12, sy,
                        Theme.TEXT_MUTED);
            }
        }

        boolean ready = in && me.balance >= Bounties.CASHOUT_MIN_CENTS;
        boolean over = inside(mouseX, mouseY, x, takeY, w, TAKE_H) && (!in || ready);
        int edge = in ? (ready ? (over ? CASH : CASH_DIM) : Theme.PLATE_EDGE) : (over ? BLURPLE : Theme.PLATE_EDGE);
        Theme.roundedBox(graphics, x, takeY, w, TAKE_H, CARD_R, over ? Theme.PLATE_LIT : Theme.PLATE);
        Theme.roundedBoxOutline(graphics, x, takeY, w, TAKE_H, CARD_R, edge);
        Component take = Component.translatable(in ? "vivet.bounties.cash_out" : "vivet.bounties.take_part")
                .withStyle(ChatFormatting.BOLD);
        int groupW = 16 + 6 + this.font.width(take);
        int gx = x + (w - groupW) / 2;
        VanillaIcon.draw(graphics, VanillaIcon.Kind.DISCORD, gx + 8, takeY + TAKE_H / 2, 16,
                in && !ready ? 0xFF777777 : 0xFFFFFFFF);
        graphics.text(this.font, take, gx + 22, Theme.textTop(takeY, TAKE_H), in && !ready ? Theme.TEXT_FAINT : Theme.TEXT);
    }

    private List<Tip.Line> drawPage(GuiGraphicsExtractor graphics, Bounties.View view, int mouseX, int mouseY,
                                    float partialTick) {
        int x = this.pageLeft + PAD;
        int w = this.pageWidth - PAD * 2;
        int y = this.top + PAD;
        int band = shownBand();

        int tabW = (w - TAB_GAP * 3) / 4;
        for (Tab each : Tab.values()) {
            int tx = x + each.ordinal() * (tabW + TAB_GAP);
            boolean selected = each == this.tab;
            boolean over = inside(mouseX, mouseY, tx, y, tabW, TAB_H);
            Theme.roundedBox(graphics, tx, y, tabW, TAB_H, CARD_R,
                    selected ? Theme.PLATE_LIT : over ? Theme.mix(Theme.PLATE, Theme.PLATE_LIT, 0.6F) : Theme.PLATE);
            Theme.roundedBoxOutline(graphics, tx, y, tabW, TAB_H, CARD_R,
                    selected ? CASH : over ? Theme.PLATE_EDGE_LIT : Theme.PLATE_EDGE);
            Component name = Component.translatable("vivet.bounties.tab." + each.name().toLowerCase(java.util.Locale.ROOT));
            Component sum = Component.literal(Bounties.money(tabTotal(view, each, band)));
            int gw = this.font.width(name) + 8 + this.font.width(sum);
            int gx = tx + (tabW - gw) / 2;
            int ty = Theme.textTop(y, TAB_H);
            graphics.text(this.font, name, gx, ty, selected ? Theme.TEXT : Theme.TEXT_MUTED);
            graphics.text(this.font, sum, gx + this.font.width(name) + 8, ty, selected ? CASH : CASH_DIM);
        }
        y += TAB_H + PAD;

        if (this.tab == Tab.MONTHLY) {
            graphics.text(this.font, Component.translatable("vivet.bounties.monthly.when", untilMonthEnd()), x, y,
                    Theme.TEXT_MUTED);
            y += 9 + PAD - 4;
        }

        if (this.tab == Tab.WINNERS) {
            drawWinners(graphics, view, x, y, w);
            return null;
        }

        int room = this.top + this.panelHeight - PAD - y;
        this.cardH = Math.max(CARD_MIN, Math.min(CARD_MAX, (room - CARD_GAP * 3) / 4));
        List<Tip.Line> tip = null;
        for (int row = 0; row < 4; row++) {
            int cy = y + row * (this.cardH + CARD_GAP);
            if (cy + this.cardH > this.top + this.panelHeight - PAD + 2) break;
            boolean over = inside(mouseX, mouseY, x, cy, w, this.cardH);
            this.lift[row] += ((over ? 1.0F : 0.0F) - this.lift[row]) * Math.min(1.0F, partialTick * 0.6F);
            List<Tip.Line> lines = drawCard(graphics, view, row, x, cy, w, this.lift[row], band);
            if (over && lines != null) tip = lines;
        }
        return tip;
    }

    private long tabTotal(Bounties.View view, Tab which, int band) {
        long total = 0;
        switch (which) {
            case FIRSTS -> {
                for (Bounties.First first : view.firsts) if (first.open) total += Bounties.firstAt(first.id, band, false);
            }
            case SECRETS -> {
                for (Bounties.Secret secret : view.secrets) {
                    total += (long) Math.max(0, secret.total - secret.found) * Bounties.secretAt(secret.tier, band);
                }
            }
            case MONTHLY -> total = Bounties.eloAt(band) + Bounties.seedAt(band) * 5 + Bounties.clipAt(band) * 4;
            default -> total = view.totalWon;
        }
        return total;
    }

    private record Card(Kind kind, int nameColour, Component name, Component detail, long cents, long later,
                        Component unit, Status status, Bounties.Winner[] winners) {
    }

    private enum Kind { LEVEL, COLLECTION, SPEED_GOD, GRAND_CHAMPION, SECRET, ELO, SEED, CLIPS, LAST_MONTH }

    private record Status(boolean open, Component text, boolean bands) {
        boolean shown() {
            return this.bands || this.text != null;
        }
    }

    private List<Tip.Line> drawCard(GuiGraphicsExtractor graphics, Bounties.View view, int row, int x, int y, int w,
                                    float lifted, int band) {
        Card card = cardFor(view, row);
        if (card == null) return null;

        int up = Math.round(lifted * 2);
        if (up > 0) Theme.roundedBox(graphics, x, y + 1, w, this.cardH, CARD_R, Theme.PANEL_SUNKEN);
        int top = y - up;
        Theme.roundedBox(graphics, x, top, w, this.cardH, CARD_R, Theme.mix(Theme.PLATE, Theme.PLATE_LIT, lifted));
        Theme.roundedBoxOutline(graphics, x, top, w, this.cardH, CARD_R,
                Theme.mix(Theme.PLATE_EDGE, Theme.PLATE_EDGE_LIT, lifted));

        if (card.kind() == Kind.LAST_MONTH) {
            drawLastMonth(graphics, view, x, top, w);
            return null;
        }

        int cx = x + 14 + ICON / 2;
        int cy = top + this.cardH / 2;
        drawIcon(graphics, card, cx, cy);

        int right = x + w - 14;
        Component amount = Component.literal(Bounties.money(card.cents()));
        int amountW = BigText.width(this.font, amount, 3);
        Component unit = card.unit();
        int unitW = unit == null ? 0 : this.font.width(unit) + 4;
        int block = BigText.height(3) + 4 + 9;
        int ay = top + (this.cardH - block) / 2;
        BigText.draw(graphics, this.font, amount, right - unitW - amountW, ay,
                card.status().open() ? CASH : Theme.TEXT_FAINT, 3, 0);
        if (unit != null) {
            graphics.text(this.font, unit, right - this.font.width(unit), ay + BigText.height(3) - 8, Theme.TEXT_FAINT);
        }
        int at = biggerBand(shownBand());
        Component later = at > 0
                ? Component.translatable("vivet.bounties.card.later", Bounties.money(card.later()), players(at))
                : Component.empty();
        graphics.text(this.font, later, right - this.font.width(later), ay + BigText.height(3) + 4, CASH_DIM);

        int tx = x + 14 + ICON + 14;
        int room = Math.max(40, Math.min(right - amountW - unitW, right - this.font.width(later)) - 14 - tx);
        Status status = card.status();
        int lines = 1 + (card.detail() != null ? 1 : 0) + (status.shown() ? 1 : 0);
        int ty = top + (this.cardH - (lines * 9 + (lines - 1) * 5)) / 2;
        graphics.text(this.font, card.name().copy().withStyle(ChatFormatting.BOLD), tx, ty, card.nameColour());
        int sy = ty + 14;
        if (card.detail() != null) {
            graphics.text(this.font, Component.literal(this.font.plainSubstrByWidth(card.detail().getString(), room)),
                    tx, sy, Theme.TEXT_MUTED);
            sy += 14;
        }
        int sx = tx;
        if (status.bands()) {
            Difficulty[] bands = Difficulty.values();
            for (int i = 0; i < bands.length; i++) Theme.roundedBox(graphics, sx + i * 10, sy, 7, 7, 2, bands[i].color());
            sx += bands.length * 10 + 4;
        } else if (status.text() != null && status.open()) {
            Theme.fill(graphics, sx, sy + 2, sx + 4, sy + 6, CASH);
            sx += 8;
        }
        if (status.text() != null) {
            graphics.text(this.font, Component.literal(this.font.plainSubstrByWidth(status.text().getString(),
                    Math.max(10, room - (sx - tx)))), sx, sy, Theme.TEXT_FAINT);
        }

        Progress mine = progressOf(card);
        if (mine != null) {
            int textEnd = tx + Math.max(this.font.width(card.name().copy().withStyle(ChatFormatting.BOLD)),
                    Math.max(card.detail() == null ? 0 : this.font.width(card.detail()), (sx - tx)
                            + (status.text() == null ? 0 : this.font.width(status.text()))));

            int bw = 120;
            int bx = right - AMOUNT_COL - 16 - bw;
            if (bx >= textEnd + 12) {
                int py = top + (this.cardH - (9 + 5 + 5)) / 2;
                Component you = Component.translatable("vivet.bounties.you");
                graphics.text(this.font, you, bx, py, Theme.TEXT_FAINT);
                graphics.text(this.font, mine.value(), bx + bw - this.font.width(mine.value()), py, mine.colour());
                if (mine.fraction() >= 0) {
                    Theme.progressBar(graphics, bx, py + 14, bw, 5, mine.fraction(), CASH);
                }
            }
        }
        return winnersTip(card.winners());
    }

    private record Progress(Component value, int colour, float fraction) {
    }

    private Progress progressOf(Card card) {
        var profile = com.vivet.speedrun.p2p.MojangAuth.knownProfile();
        Bounties.Me me = Bounties.me();
        switch (card.kind()) {
            case LEVEL -> {
                if (profile == null) return null;
                int level = Math.max(1, profile.level);
                return new Progress(Component.literal(Math.min(level, 100) + "/100"), Theme.TEXT, Math.min(1.0F, level / 100.0F));
            }
            case COLLECTION -> {
                int found = com.vivet.speedrun.p2p.CollectionBook.found();
                int total = com.vivet.speedrun.game.ItemTiers.collection().size();
                if (total <= 0) return null;
                return new Progress(Component.literal(String.format(java.util.Locale.ROOT, "%,d/%,d", found, total)),
                        Theme.TEXT, Math.min(1.0F, found / (float) total));
            }
            case SPEED_GOD -> {
                if (profile == null) return null;
                int kings = 0;
                if (profile.badges != null) {
                    for (var badge : profile.badges) {
                        if (badge != null && badge.place == 1 && "grand_champion".equals(badge.rank)) kings++;
                    }
                }
                return new Progress(Component.literal(Math.min(kings, 3) + "/3"), Theme.TEXT, Math.min(1.0F, kings / 3.0F));
            }
            case GRAND_CHAMPION -> {
                if (profile == null || profile.rank == null) return null;
                Integer top = com.vivet.speedrun.client.ui.Ranks.thresholdOf("grand_champion");
                int line = top == null || top <= 0 ? 5_000 : top;
                int rating = profile.rating == null ? 0 : profile.rating;
                return new Progress(Component.literal(com.vivet.speedrun.client.ui.Ranks.nameOf(profile.rank)),
                        com.vivet.speedrun.client.ui.Ranks.colorOf(profile.rank),
                        profile.placed ? Math.min(1.0F, rating / (float) line) : 0.0F);
            }
            case SECRET -> {
                if (me == null || me.secrets == null) return null;
                Bounties.Secret secret = secretOfCard(card);
                if (secret == null) return null;
                int found = me.secrets.getOrDefault(secret.tier, 0);
                return new Progress(Component.translatable("vivet.bounties.you.found", found, secret.total),
                        Theme.TEXT, Math.min(1.0F, found / (float) Math.max(1, secret.total)));
            }
            case ELO -> {
                if (profile == null || profile.position == null) return null;
                return new Progress(Component.literal("#" + String.format(java.util.Locale.ROOT, "%,d", profile.position)),
                        Theme.TEXT, -1.0F);
            }
            default -> {
                return null;
            }
        }
    }

    private Bounties.Secret secretOfCard(Card card) {
        for (Bounties.Secret secret : Bounties.view().secrets) {
            Difficulty colour = Difficulty.byId(secret.tier);
            if (colour != null && colour.color() == card.nameColour()) return secret;
        }
        return null;
    }

    private static final int WIN_ROW = 26;

    private static final int AMOUNT_COL = 150;

    private void drawWinners(GuiGraphicsExtractor graphics, Bounties.View view, int x, int y, int w) {
        Bounties.Recent[] recent = view.recent == null ? new Bounties.Recent[0] : view.recent;
        int bottom = this.top + this.panelHeight - PAD;
        if (recent.length == 0) {
            Component none = Component.translatable("vivet.bounties.no_winners");
            graphics.centeredText(this.font, none, x + w / 2, y + (bottom - y) / 2 - 4, Theme.TEXT_FAINT);
            return;
        }
        java.time.format.DateTimeFormatter day = java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.ROOT);
        for (int i = 0; i < recent.length; i++) {
            int ry = y + i * (WIN_ROW + 4);
            if (ry + WIN_ROW > bottom) break;
            Bounties.Recent win = recent[i];
            Theme.roundedBox(graphics, x, ry, w, WIN_ROW, CARD_R, Theme.PLATE);
            Theme.roundedBoxOutline(graphics, x, ry, w, WIN_ROW, CARD_R, Theme.PLATE_EDGE);
            int ty = Theme.textTop(ry, WIN_ROW);
            Component what = winnerLabel(win);
            graphics.text(this.font, what.copy().withStyle(ChatFormatting.BOLD), x + 12, ty, winnerColour(win));
            Component who = win.name == null ? Component.translatable("vivet.bounties.anonymous") : Component.literal(win.name);
            graphics.text(this.font, who, x + 12 + 170, ty, win.name == null ? Theme.TEXT_FAINT : Theme.TEXT);
            String when = java.time.Instant.ofEpochMilli(win.at).atZone(java.time.ZoneOffset.UTC).format(day);
            graphics.text(this.font, Component.literal(when), x + 12 + 330, ty, Theme.TEXT_FAINT);
            Component amount = Component.literal(Bounties.money(win.cents));
            BigText.draw(graphics, this.font, amount, x + w - 12 - BigText.width(this.font, amount, 2),
                    ry + (WIN_ROW - BigText.height(2)) / 2 + 1, CASH, 2, 0);
        }
    }

    private static Component winnerLabel(Bounties.Recent win) {
        String id = win.bounty == null ? "" : win.bounty;
        String[] parts = id.split(":");
        Component name = switch (parts[0]) {
            case "level_100", "all_items", "speed_god", "grand_champion" ->
                    Component.translatable("vivet.bounties.first." + parts[0]);
            case "secret" -> Component.translatable("vivet.bounties.winners.secret." + (parts.length > 1 ? parts[1] : "easy"));
            case "elo" -> Component.translatable("vivet.bounties.monthly.elo");
            case "seed" -> {
                Difficulty band = parts.length > 1 ? Difficulty.byId(parts[1]) : null;
                yield Component.translatable("vivet.bounties.winners.seed", band == null ? "" : band.displayName());
            }
            case "clip" -> Component.translatable("vivet.bounties.winners.clip." + (parts.length > 1 ? parts[1] : "youtube"));
            default -> Component.translatable("vivet.bounties.winners.exploit");
        };
        return win.place > 1 ? Component.translatable("vivet.bounties.winners.place", name, win.place) : name;
    }

    private static int winnerColour(Bounties.Recent win) {
        String[] parts = (win.bounty == null ? "" : win.bounty).split(":");
        if ((parts[0].equals("secret") || parts[0].equals("seed")) && parts.length > 1) {
            Difficulty band = Difficulty.byId(parts[1]);
            if (band != null) return band.color();
        }
        return Theme.TEXT;
    }

    private void drawIcon(GuiGraphicsExtractor graphics, Card card, int cx, int cy) {
        switch (card.kind()) {
            case LEVEL -> VanillaIcon.draw(graphics, VanillaIcon.Kind.BOTTLE, cx, cy, ICON);
            case COLLECTION -> VanillaIcon.draw(graphics, VanillaIcon.Kind.KNOWLEDGE, cx, cy, ICON);
            case SPEED_GOD -> RankEmblem.drawSpeedGod(graphics, cx, cy, ICON + 6);
            case GRAND_CHAMPION -> RankEmblem.drawAt(graphics, cx, cy, "grand_champion", true, ICON + 6);
            case ELO -> VanillaIcon.draw(graphics, VanillaIcon.Kind.STAR, cx, cy, ICON);
            case SEED -> VanillaIcon.draw(graphics, VanillaIcon.Kind.SEEDS, cx, cy, ICON);
            case CLIPS -> VanillaIcon.draw(graphics, VanillaIcon.Kind.SPYGLASS, cx, cy, ICON);
            case SECRET -> {
                Theme.roundedBox(graphics, cx - ICON / 2, cy - ICON / 2, ICON, ICON, 4, Theme.PANEL_SUNKEN);
                BigText.centered(graphics, this.font, Component.literal("?"), cx, cy - BigText.height(2) / 2 + 1,
                        card.nameColour(), 2, 0);
            }
            default -> { }
        }
    }

    private Card cardFor(Bounties.View view, int row) {
        int shown = shownBand();
        int target = Math.max(0, biggerBand(shown));
        switch (this.tab) {
            case FIRSTS -> {
                Bounties.First first = row < view.firsts.length ? view.firsts[row] : null;
                if (first == null) return null;
                Kind kind = switch (first.id) {
                    case "level_100" -> Kind.LEVEL;
                    case "all_items" -> Kind.COLLECTION;
                    case "speed_god" -> Kind.SPEED_GOD;
                    default -> Kind.GRAND_CHAMPION;
                };

                boolean runners = first.runnerUp > 0;
                int places = runners ? 10 : 1;
                int claimed = Math.min(places, Math.max(first.finishers, first.open ? 0 : 1));
                Component text = runners
                        ? Component.translatable("vivet.bounties.card.claimed_runners", claimed, places,
                                Bounties.money(Bounties.firstAt(first.id, shown, true)))
                        : Component.translatable("vivet.bounties.card.claimed", claimed, places);
                return new Card(kind, Theme.TEXT, Component.translatable("vivet.bounties.first." + first.id), null,
                        Bounties.firstAt(first.id, shown, false),
                        Bounties.firstAt(first.id, target, false), null, new Status(first.open, text, false),
                        first.winners);
            }
            case SECRETS -> {
                Bounties.Secret secret = row < view.secrets.length ? view.secrets[row] : null;
                if (secret == null) return null;
                Difficulty colour = Difficulty.byId(secret.tier);
                boolean open = secret.found < secret.total;
                return new Card(Kind.SECRET, colour == null ? Theme.TEXT : colour.color(),
                        Component.translatable("vivet.bounties.tier." + secret.tier), null,
                        Bounties.secretAt(secret.tier, shown),
                        Bounties.secretAt(secret.tier, target), Component.translatable("vivet.bounties.each"),
                        new Status(open, Component.translatable("vivet.bounties.found", secret.found, secret.total), false),
                        secret.winners);
            }
            default -> {
                Bounties.Monthly month = view.monthly;
                if (month == null) return null;
                return switch (row) {
                    case 0 -> new Card(Kind.ELO, Theme.TEXT, Component.translatable("vivet.bounties.monthly.elo"),
                            Component.translatable("vivet.bounties.monthly.elo.detail"), Bounties.eloAt(shown),
                            Bounties.eloAt(target), null,
                            new Status(true, null, false), null);
                    case 1 -> new Card(Kind.SEED, Theme.TEXT, Component.translatable("vivet.bounties.monthly.seed"),
                            Component.translatable("vivet.bounties.monthly.seed.detail"), Bounties.seedAt(shown),
                            Bounties.seedAt(target), Component.translatable("vivet.bounties.each"),
                            new Status(true, null, true), null);
                    case 2 -> new Card(Kind.CLIPS, Theme.TEXT, Component.translatable("vivet.bounties.monthly.clips"),
                            Component.translatable("vivet.bounties.monthly.clips.detail"), Bounties.clipAt(shown),
                            Bounties.clipAt(target), Component.translatable("vivet.bounties.each"),
                            new Status(true, null, false), null);
                    default -> new Card(Kind.LAST_MONTH, Theme.TEXT, Component.empty(), Component.empty(), 0, 0,
                            null, new Status(false, null, false), null);
                };
            }
        }
    }

    private void drawLastMonth(GuiGraphicsExtractor graphics, Bounties.View view, int x, int top, int w) {
        Bounties.Monthly month = view.monthly;
        int lx = x + 14;
        boolean none = month == null || month.winners == null || month.winners.length == 0;
        int ty = top + (this.cardH - (9 + 5 + 9)) / 2;
        graphics.text(this.font, Component.translatable("vivet.bounties.last_month").withStyle(ChatFormatting.BOLD),
                lx, none ? ty : top + 8, Theme.TEXT);
        if (none) {
            graphics.text(this.font, Component.translatable("vivet.bounties.no_winners"), lx, ty + 14, Theme.TEXT_FAINT);
            return;
        }
        int ly = top + 22;
        int colW = (w - 28 - 14) / 2;
        for (int i = 0; i < month.winners.length && i < 6; i++) {
            Bounties.MonthlyWinner winner = month.winners[i];
            int wx = lx + (i % 2) * (colW + 14);
            int wy = ly + (i / 2) * 12;

            Bounties.Recent as = new Bounties.Recent();
            as.bounty = winner.bounty;
            as.place = 1;
            graphics.text(this.font, winnerLabel(as), wx, wy, winnerColour(as));
            String name = winner.name == null ? Component.translatable("vivet.bounties.anonymous").getString() : winner.name;
            graphics.text(this.font, Component.literal(name), wx + 120, wy, winner.name == null ? Theme.TEXT_FAINT : Theme.TEXT_MUTED);
            Component paid = Component.literal(Bounties.money(winner.cents));
            graphics.text(this.font, paid, wx + colW - 14 - this.font.width(paid), wy, CASH);
        }
    }

    private List<Tip.Line> winnersTip(Bounties.Winner[] winners) {
        if (winners == null || winners.length == 0) return null;
        List<Tip.Line> lines = Tip.lines();
        for (int i = 0; i < Math.min(10, winners.length); i++) {
            Bounties.Winner winner = winners[i];
            lines.add(new Tip.Line(Component.translatable("vivet.bounties.tip.place", winner.place, nameOf(winner),
                    Bounties.money(winner.cents)), i == 0 ? CASH : Theme.TEXT));
        }
        return lines;
    }

    private static Component nameOf(Bounties.Winner winner) {
        return winner.name == null ? Component.translatable("vivet.bounties.anonymous") : Component.literal(winner.name);
    }

    private static String untilMonthEnd() {
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC);
        java.time.ZonedDateTime end = now.withDayOfMonth(1).toLocalDate().plusMonths(1)
                .atStartOfDay(java.time.ZoneOffset.UTC);
        long minutes = Math.max(0, java.time.Duration.between(now, end).toMinutes());
        long days = minutes / (60 * 24);
        long hours = (minutes / 60) % 24;
        if (days > 0) return days + "d " + hours + "h";
        return hours + "h " + (minutes % 60) + "m";
    }

    private int scaled(double value) {
        return (int) (value / uiScale());
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private int backX() {
        return this.left + this.panelWidth + BACK_GAP;
    }

    private boolean onBack(int mouseX, int mouseY) {
        return inside(mouseX, mouseY, backX(), this.top, UiButtons.SIDE, UiButtons.SIDE);
    }

    private boolean onTakePart(int mouseX, int mouseY) {
        int x = this.left + PAD;
        int takeY = this.top + this.panelHeight - PAD - TAKE_H;
        return inside(mouseX, mouseY, x, takeY, RAIL_W - PAD * 2, TAKE_H);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = scaled(event.x());
        int my = scaled(event.y());

        if (onBack(mx, my)) {
            Cues.confirm();
            onClose();
            return true;
        }
        if (onTakePart(mx, my)) {
            Bounties.Me me = Bounties.me();
            if (me != null && me.optedIn && me.balance < Bounties.CASHOUT_MIN_CENTS) {
                Cues.nothingHappened();
                return true;
            }
            Cues.confirm();
            com.mojang.blaze3d.Blaze3D.openUri(java.net.URI.create(com.vivet.speedrun.p2p.DiscordLink.INVITE));
            return true;
        }
        for (int[] row : this.ladderRows) {
            if (inside(mx, my, this.left + PAD, row[0], RAIL_W - PAD * 2, row[1])) {
                var config = com.vivet.speedrun.config.ConfigManager.get();
                if (row[2] != shownBand()) {
                    config.bountiesPick = row[2];
                    com.vivet.speedrun.config.ConfigManager.save();
                    Cues.confirm();
                }
                return true;
            }
        }
        int x = this.pageLeft + PAD;
        int w = this.pageWidth - PAD * 2;
        int tabW = (w - TAB_GAP * 3) / 4;
        for (Tab each : Tab.values()) {
            if (inside(mx, my, x + each.ordinal() * (tabW + TAB_GAP), this.top + PAD, tabW, TAB_H)) {
                if (each != this.tab) {
                    this.tab = each;
                    java.util.Arrays.fill(this.lift, 0.0F);
                    Cues.confirm();
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    public void showTab(int index) {
        this.tab = Tab.values()[Math.max(0, Math.min(Tab.values().length - 1, index))];
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }
}
