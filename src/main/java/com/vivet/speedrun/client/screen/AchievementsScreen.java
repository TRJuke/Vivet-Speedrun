package com.vivet.speedrun.client.screen;

import com.vivet.speedrun.client.Cues;
import com.vivet.speedrun.client.ui.AchievementText;
import com.vivet.speedrun.client.ui.BigText;
import com.vivet.speedrun.client.ui.Clip;
import com.vivet.speedrun.client.ui.Cosmetics;
import com.vivet.speedrun.client.ui.Levels;
import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.client.ui.Tip;
import com.vivet.speedrun.client.ui.UiButtons;
import com.vivet.speedrun.client.ui.VanillaIcon;
import com.vivet.speedrun.p2p.Achievements;
import com.vivet.speedrun.p2p.MojangAuth;
import com.vivet.speedrun.p2p.Wardrobe;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class AchievementsScreen extends VivetScreen {
    private static final int MAX_WIDTH = 820;
    private static final int MIN_WIDTH = 460;
    private static final int PANEL_GAP = 10;
    private static final int PADDING = 14;
    private static final int RAIL_W = 168;
    private static final int PANEL_R = 8;
    private static final int CELL_R = 5;
    private static final int BACK_GAP = 8;

    private static final int TAB_H = 22;
    private static final int GROUP_H = 17;

    private static final int CARD_H = 50;
    private static final int CARD_GAP = 6;

    private static final int RIGHT_W = 70;
    private static final int CLAIM_W = 56;
    private static final int CLAIM_H = 16;

    private static final int PIP = 7;

    private static final int BAR_W = 4;
    private static final int BAR_GRAB = 7;

    private static final int NODE_W = 78;

    private static final int GOLD = 0xFFE8C34A;

    private static final int[] COLOUR_STEPS = { 5, 10, 25, 50, 75, 100 };

    private final Screen parent;

    private boolean roadTab;
    private AchievementText.Filter filter = AchievementText.Filter.ALL;

    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int railWidth;
    private int pageLeft;
    private int pageWidth;
    private int listTop;
    private int listHeight;

    private int scroll;
    private int roadScroll;
    private boolean roadPlaced;
    private boolean draggingBar;

    private boolean draggingRoad;
    private int roadBarX;
    private int roadBarY;
    private int roadBarW;
    private int roadThumbW;
    private int roadMax;

    private long laidOutFrom = -1L;
    private final List<Card> cards = new ArrayList<>();
    private int contentHeight;

    private record Card(Achievements.Entry entry, int node, boolean found, boolean claimable,
                        boolean claimed, boolean started) {
        boolean in(AchievementText.Filter filter) {
            return filter.has(Boolean.TRUE.equals(this.entry.hidden), this.found, this.claimable,
                    this.claimed, this.started);
        }
    }

    public AchievementsScreen(Screen parent) {
        super(Component.translatable("vivet.screen.achievements"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        Achievements.refresh();
        Wardrobe.refresh();

        int side = UiButtons.SIDE + BACK_GAP;
        this.panelWidth = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, this.width - 16 - side * 2));
        this.panelHeight = Math.max(220, Math.min(460, this.height - 24));
        this.left = (this.width - this.panelWidth) / 2;
        this.top = Math.max(4, (this.height - this.panelHeight) / 2);

        this.railWidth = RAIL_W + PADDING * 2;
        this.pageLeft = this.left + this.railWidth + PANEL_GAP;
        this.pageWidth = this.left + this.panelWidth - this.pageLeft;

        this.listTop = this.top + PADDING + 30;
        this.listHeight = this.top + this.panelHeight - PADDING - this.listTop;

        layout();
    }

    @Override
    public void tick() {
        super.tick();
        Achievements.refresh();
        if (this.laidOutFrom != Achievements.stamp()) layout();
    }

    private Achievements.Book book() {
        return Achievements.book();
    }

    private void layout() {
        this.cards.clear();
        Achievements.Book book = book();
        if (book != null && book.achievements != null) {
            List<Card> claimable = new ArrayList<>();
            List<Card> going = new ArrayList<>();
            List<Card> finished = new ArrayList<>();

            for (Achievements.Entry entry : book.achievements) {
                Card card = cardFor(entry);
                if (card == null || !card.in(this.filter)) continue;

                if (card.claimable()) claimable.add(card);
                else if (card.claimed()) finished.add(card);
                else going.add(card);
            }
            this.cards.addAll(claimable);
            this.cards.addAll(going);
            this.cards.addAll(finished);
        }

        this.contentHeight = Math.max(0, this.cards.size() * (CARD_H + CARD_GAP) - CARD_GAP);
        this.scroll = Math.max(0, Math.min(maxScroll(), this.scroll));
        this.laidOutFrom = Achievements.stamp();
    }

    private static Card cardFor(Achievements.Entry entry) {
        if (entry == null || entry.id == null || entry.nodes == null || entry.nodes.length == 0) {
            return null;
        }
        int node = shownNode(entry);
        Achievements.Node shown = entry.nodes[node];
        if (shown == null) return null;

        boolean found = anyDone(entry);
        boolean isClaimed = Achievements.claimed(entry.id, node, shown);
        boolean canClaim = Boolean.TRUE.equals(shown.done) && !isClaimed;
        boolean started = found || (entry.progress != null && entry.progress > 0)
                || Achievements.claimed(entry.id, 0, entry.nodes[0]);
        return new Card(entry, node, found, canClaim, isClaimed, started);
    }

    private static int shownNode(Achievements.Entry entry) {
        for (int i = 0; i < entry.nodes.length; i++) {
            if (!Achievements.claimed(entry.id, i, entry.nodes[i])) return i;
        }
        return entry.nodes.length - 1;
    }

    private static boolean anyDone(Achievements.Entry entry) {
        for (Achievements.Node node : entry.nodes) {
            if (node != null && Boolean.TRUE.equals(node.done)) return true;
        }
        return false;
    }

    private int[] counts(boolean hidden) {
        Achievements.Book book = book();
        int done = 0;
        int total = 0;
        if (book == null || book.achievements == null) return new int[] { 0, 0 };
        for (Achievements.Entry entry : book.achievements) {
            if (entry == null || entry.nodes == null) continue;
            if (Boolean.TRUE.equals(entry.hidden) != hidden) continue;
            if (hidden) {
                total++;
                if (anyDone(entry)) done++;
                continue;
            }
            for (Achievements.Node node : entry.nodes) {
                total++;
                if (node != null && Boolean.TRUE.equals(node.done)) done++;
            }
        }
        return new int[] { done, total };
    }

    private boolean claimableIn(AchievementText.Filter which) {
        Achievements.Book book = book();
        if (book == null || book.achievements == null) return false;
        for (Achievements.Entry entry : book.achievements) {
            Card card = cardFor(entry);
            if (card != null && card.claimable() && card.in(which)) return true;
        }
        return false;
    }

    private int maxScroll() {
        return Math.max(0, this.contentHeight - this.listHeight);
    }

    @Override
    protected void drawScaled(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                              float partialTick) {
        Theme.fill(graphics, 0, 0, this.width, this.height, Theme.SCRIM);

        panel(graphics, this.left, this.top, this.railWidth, this.panelHeight);
        panel(graphics, this.pageLeft, this.top, this.pageWidth, this.panelHeight);

        UiButtons.button(graphics, UiButtons.Kind.BACK, backX(), this.top,
                onBack(mouseX, mouseY), true, false);

        drawRail(graphics, mouseX, mouseY);

        if (this.roadTab) {
            drawRoad(graphics, mouseX, mouseY);
        } else {
            drawList(graphics, mouseX, mouseY);
        }

        drawWidgets(graphics, mouseX, mouseY, partialTick);
    }

    private void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        Theme.roundedBox(graphics, x, y, width, height, PANEL_R, Theme.PANEL);
        Theme.roundedBoxOutline(graphics, x, y, width, height, PANEL_R, Theme.BORDER);
    }

    private void drawRail(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = this.left + PADDING;
        int y = this.top + PADDING;

        graphics.text(this.font, this.title.copy().withStyle(ChatFormatting.BOLD), x, y + 4,
                Theme.TEXT);
        y += 22;

        int[] shown = counts(false);
        int[] secret = counts(true);
        if (Achievements.known()) {
            Component count = Component.translatable("vivet.achievements.count", shown[0], shown[1]);
            BigText.draw(graphics, this.font, count, x, y, GOLD, 2, 0);
            y += BigText.height(2) + 4;
            Theme.progressBar(graphics, x, y, RAIL_W, 6,
                    shown[1] == 0 ? 0.0F : shown[0] / (float) shown[1], GOLD);
            y += 12;
            graphics.text(this.font, Component.translatable("vivet.achievements.secret_count",
                    secret[0], secret[1]), x, y, Cosmetics.tierColour("secret"));
        } else {
            graphics.text(this.font, waitingLine(), x, y, Theme.TEXT_FAINT);
        }
        y = railTabsTop();

        Theme.divider(graphics, x, y - 8, RAIL_W);

        drawTab(graphics, x, y, false, mouseX, mouseY);
        drawTab(graphics, x, y + TAB_H + 4, true, mouseX, mouseY);

        if (this.roadTab) return;

        int groupsTop = groupsTop();
        for (AchievementText.Filter which : AchievementText.Filter.values()) {
            int gy = groupsTop + which.ordinal() * GROUP_H;
            boolean selected = which == this.filter;
            boolean over = inside(mouseX, mouseY, x, gy, RAIL_W, GROUP_H - 2);

            if (selected || over) {
                Theme.roundedBox(graphics, x, gy, RAIL_W, GROUP_H - 2, 4,
                        selected ? Theme.PLATE_LIT : Theme.mix(Theme.PLATE, Theme.PLATE_LIT, 0.5F));
            }
            graphics.text(this.font, which.label(), x + 8, gy + 4,
                    selected ? Theme.TEXT : Theme.TEXT_MUTED);

            if (claimableIn(which)) {
                Theme.roundedBox(graphics, x + RAIL_W - 12, gy + 5, 6, 6, 2, 0xFFD03A2C);
            }
        }
    }

    private int railTabsTop() {
        return this.top + PADDING + 22 + BigText.height(2) + 4 + 12 + 12 + 18;
    }

    private int groupsTop() {
        return railTabsTop() + (TAB_H + 4) * 2 + 10;
    }

    private void drawTab(GuiGraphicsExtractor graphics, int x, int y, boolean road, int mouseX,
                         int mouseY) {
        boolean selected = road == this.roadTab;
        boolean over = inside(mouseX, mouseY, x, y, RAIL_W, TAB_H);

        Theme.roundedBox(graphics, x, y, RAIL_W, TAB_H, CELL_R,
                selected ? Theme.PLATE_LIT : over ? Theme.mix(Theme.PLATE, Theme.PLATE_LIT, 0.6F)
                        : Theme.PLATE);
        Theme.roundedBoxOutline(graphics, x, y, RAIL_W, TAB_H, CELL_R,
                selected ? GOLD : over ? Theme.PLATE_EDGE_LIT : Theme.PLATE_EDGE);

        Component label = Component.translatable(road
                ? "vivet.achievements.tab.road" : "vivet.achievements.tab.list");
        graphics.centeredText(this.font, label, x + RAIL_W / 2, y + (TAB_H - 8) / 2,
                selected ? Theme.TEXT : Theme.TEXT_MUTED);
    }

    private Component waitingLine() {
        if (MojangAuth.token() == null) return Component.translatable("vivet.locker.signed_out");
        return Component.translatable("vivet.achievements.waiting");
    }

    private void drawList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = this.pageLeft + PADDING;
        int width = this.pageWidth - PADDING * 2;
        int y = this.top + PADDING;

        graphics.text(this.font, this.filter.label().copy().withStyle(ChatFormatting.BOLD), x, y + 4,
                Theme.TEXT);
        Theme.divider(graphics, x, y + 18, width);

        if (!Achievements.known()) {
            graphics.text(this.font, waitingLine(), x + 2, this.listTop + 6, Theme.TEXT_FAINT);
            return;
        }

        int cardW = width - BAR_W - BAR_GRAB;
        int bottom = this.listTop + this.listHeight;
        Card hovered = null;
        int hoveredPip = -1;

        Clip.on(graphics, x, this.listTop, x + width, bottom);
        try {
            for (int i = 0; i < this.cards.size(); i++) {
                int cy = this.listTop - this.scroll + i * (CARD_H + CARD_GAP);
                if (cy + CARD_H < this.listTop || cy > bottom) continue;

                boolean inList = mouseY >= this.listTop && mouseY < bottom;
                Card card = this.cards.get(i);
                int pip = drawCard(graphics, card, x, cy, cardW, inList ? mouseX : -1,
                        inList ? mouseY : -1);
                if (pip >= 0) {
                    hovered = card;
                    hoveredPip = pip;
                }
            }
        } finally {
            Clip.off(graphics);
        }

        drawScrollbar(graphics, x + width - BAR_W, bottom, mouseX, mouseY);

        if (hovered != null) drawPipTip(graphics, hovered, hoveredPip, mouseX, mouseY);
    }

    private int drawCard(GuiGraphicsExtractor graphics, Card card, int x, int y, int width,
                         int mouseX, int mouseY) {
        Achievements.Entry entry = card.entry();
        boolean hidden = Boolean.TRUE.equals(entry.hidden) && !card.found();
        Achievements.Node node = entry.nodes[card.node()];
        boolean claimed = Achievements.claimed(entry.id, card.node(), node);
        int colour = hidden ? Theme.TEXT_FAINT : AchievementText.colour(node.rarity);

        int fill = hidden ? 0xF0090A0E
                : Theme.mix(Theme.PANEL_RAISED, colour, claimed ? 0.04F : 0.10F);
        Theme.roundedBox(graphics, x, y, width, CARD_H, CELL_R, fill);
        Theme.roundedBoxOutline(graphics, x, y, width, CARD_H, CELL_R,
                card.claimable() ? Theme.withAlpha(colour, 0xDD)
                        : Theme.withAlpha(colour, hidden ? 0x30 : 0x66));

        int textX = x + 10;
        int room = width - 20 - RIGHT_W;

        Component name = AchievementText.nodeName(entry.id, card.node(), entry.nodes.length,
                card.found() || !Boolean.TRUE.equals(entry.hidden));

        Component bold = name.copy().withStyle(ChatFormatting.BOLD);
        com.vivet.speedrun.client.ui.Marquee.drawSpotify(graphics, this.font, bold, textX, y + 7,
                room, hidden ? Theme.TEXT_FAINT : colour);

        long target = node.target == null ? 1L : node.target;
        Component line = AchievementText.describe(entry.id, card.node(), target,
                card.found() || !Boolean.TRUE.equals(entry.hidden));
        graphics.text(this.font, Component.literal(this.font.plainSubstrByWidth(line.getString(),
                room)), textX, y + 19, hidden ? Theme.TEXT_FAINT : Theme.TEXT_MUTED);

        int hoveredPip = -1;
        if (!hidden) {
            long progress = entry.progress == null ? 0L : entry.progress;
            float fraction = Boolean.TRUE.equals(node.done) ? 1.0F
                    : Math.min(1.0F, progress / (float) Math.max(1L, target));
            int barW = Math.max(40, room - 90);
            Theme.progressBar(graphics, textX, y + 34, barW, 6, fraction, colour);

            boolean last = target > 1L && card.node() == entry.nodes.length - 1;
            String amount = Boolean.TRUE.equals(node.done)
                    ? (last ? AchievementText.progressPast(entry.id, Math.max(progress, target), target)
                            : AchievementText.progress(entry.id, target, target))
                    : AchievementText.progress(entry.id, progress, target);
            graphics.text(this.font, Component.literal(amount), textX + barW + 6, y + 33,
                    Theme.TEXT_FAINT);

            if (entry.nodes.length > 1) {
                int pipsW = entry.nodes.length * (PIP + 3) - 3;
                int px = x + width - RIGHT_W - 10 - pipsW;
                for (int i = 0; i < entry.nodes.length; i++) {
                    Achievements.Node each = entry.nodes[i];
                    int pcol = AchievementText.colour(each.rarity);
                    int pxi = px + i * (PIP + 3);
                    int py = y + 8;
                    boolean isClaimed = Achievements.claimed(entry.id, i, each);
                    if (isClaimed) {
                        Theme.fill(graphics, pxi, py, pxi + PIP, py + PIP, pcol);
                        tick(graphics, pxi, py);
                    } else if (Boolean.TRUE.equals(each.done)) {
                        Theme.fill(graphics, pxi, py, pxi + PIP, py + PIP, Theme.withAlpha(pcol, 0x55));
                        Theme.roundedOutline(graphics, pxi, py, PIP, PIP, pcol);
                    } else {
                        Theme.fill(graphics, pxi, py, pxi + PIP, py + PIP, Theme.PANEL_SUNKEN);
                        Theme.roundedOutline(graphics, pxi, py, PIP, PIP, Theme.BORDER);
                    }
                    if (inside(mouseX, mouseY, pxi - 1, py - 1, PIP + 2, PIP + 2)) hoveredPip = i;
                }
            }
        }

        int rx = x + width - RIGHT_W;
        if (!hidden && node.pay != null) {
            String pay = "+" + AchievementText.grouped(node.pay);
            int payW = this.font.width(pay);
            int iconSize = 16;
            int total = payW + 2 + iconSize;
            int px = rx + (RIGHT_W - total) / 2;
            graphics.text(this.font, Component.literal(pay), px, y + 8, GOLD);
            VanillaIcon.draw(graphics, VanillaIcon.Kind.COIN, px + payW + 2 + iconSize / 2, y + 11,
                    iconSize);
        }

        int bx = rx + (RIGHT_W - CLAIM_W) / 2;
        int by = y + CARD_H - CLAIM_H - 7;
        if (card.claimable()) {
            boolean over = inside(mouseX, mouseY, bx, by, CLAIM_W, CLAIM_H);
            Theme.roundedBox(graphics, bx, by, CLAIM_W, CLAIM_H, 4,
                    Theme.withAlpha(Theme.SUCCESS, over ? 0xFF : 0xCC));
            Theme.roundedBoxOutline(graphics, bx, by, CLAIM_W, CLAIM_H, 4, Theme.SUCCESS);
            graphics.centeredText(this.font, Component.translatable("vivet.achievements.claim"),
                    bx + CLAIM_W / 2, by + 4, 0xFFFFFFFF);
        } else if (claimed) {
            Theme.roundedBox(graphics, bx, by, CLAIM_W, CLAIM_H, 4, Theme.PANEL_SUNKEN);
            graphics.centeredText(this.font, Component.translatable("vivet.achievements.claimed"),
                    bx + CLAIM_W / 2, by + 4, Theme.TEXT_FAINT);
        }
        return hoveredPip;
    }

    private static void tick(GuiGraphicsExtractor graphics, int x, int y) {
        int white = 0xFFFFFFFF;
        Theme.fill(graphics, x + 1, y + 3, x + 2, y + 4, white);
        Theme.fill(graphics, x + 2, y + 4, x + 3, y + 5, white);
        Theme.fill(graphics, x + 3, y + 3, x + 4, y + 4, white);
        Theme.fill(graphics, x + 4, y + 2, x + 5, y + 3, white);
        Theme.fill(graphics, x + 5, y + 1, x + 6, y + 2, white);
    }

    private void drawPipTip(GuiGraphicsExtractor graphics, Card card, int index, int mouseX,
                            int mouseY) {
        Achievements.Entry entry = card.entry();
        Achievements.Node node = entry.nodes[index];
        List<Tip.Line> lines = Tip.lines();
        lines.add(new Tip.Line(AchievementText.nodeName(entry.id, index, entry.nodes.length, true),
                AchievementText.colour(node.rarity)));
        lines.add(new Tip.Line(AchievementText.describe(entry.id, index,
                node.target == null ? 1L : node.target, true), Theme.TEXT_MUTED));
        if (node.pay != null) {
            lines.add(new Tip.Line(Component.literal("+" + AchievementText.grouped(node.pay)), GOLD));
        }

        int cosmetics = node.grants == null ? 0 : node.grants.length;
        if (cosmetics > 0) {
            lines.add(new Tip.Line(com.vivet.speedrun.client.ui.Plural.of(cosmetics,
                    "vivet.achievements.cosmetics"), AchievementText.colour(node.rarity)));
        }
        Tip.draw(graphics, this.font, lines, mouseX, mouseY, this.width, this.height);
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics, int barX, int bottom, int mouseX,
                               int mouseY) {
        if (maxScroll() <= 0) return;
        boolean lit = this.draggingBar || overBar(mouseX, mouseY);
        Theme.roundedBox(graphics, barX, this.listTop, BAR_W, this.listHeight, BAR_W / 2,
                Theme.withAlpha(0xFFFFFFFF, 0x12));
        Theme.roundedBox(graphics, barX, this.listTop + thumbOffset(), BAR_W, thumbHeight(),
                BAR_W / 2, Theme.withAlpha(0xFFFFFFFF, lit ? 0xBB : 0x55));
    }

    private int thumbHeight() {
        return Math.max(18, this.listHeight * this.listHeight / Math.max(1, this.contentHeight));
    }

    private int thumbOffset() {
        int travel = this.listHeight - thumbHeight();
        return maxScroll() <= 0 ? 0 : travel * this.scroll / maxScroll();
    }

    private int barX() {
        return this.pageLeft + this.pageWidth - PADDING - BAR_W;
    }

    private boolean overBar(int mouseX, int mouseY) {
        return !this.roadTab && mouseX >= barX() - BAR_GRAB && mouseX <= barX() + BAR_W + BAR_GRAB
                && mouseY >= this.listTop && mouseY < this.listTop + this.listHeight;
    }

    private void dragTo(int mouseY) {
        int travel = Math.max(1, this.listHeight - thumbHeight());
        int want = Math.max(0, Math.min(travel, mouseY - this.listTop - thumbHeight() / 2));
        this.scroll = Math.max(0, Math.min(maxScroll(),
                Math.round(maxScroll() * (float) want / travel)));
    }

    private void drawRoad(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = this.pageLeft + PADDING;
        int width = this.pageWidth - PADDING * 2;
        int y = this.top + PADDING;

        Achievements.Book book = book();
        int level = book != null && book.level != null ? book.level : 1;

        graphics.text(this.font, Component.translatable("vivet.achievements.tab.road")
                .copy().withStyle(ChatFormatting.BOLD), x, y + 4, Theme.TEXT);
        Component now = Component.translatable("vivet.achievements.road.level", level);
        graphics.text(this.font, now, x + width - this.font.width(now), y + 4,
                Levels.colorOf(level));
        Theme.divider(graphics, x, y + 18, width);

        if (book == null || book.road == null) {
            graphics.text(this.font, waitingLine(), x + 2, this.listTop + 6, Theme.TEXT_FAINT);
            return;
        }

        int roadW = book.road.length * NODE_W;
        int maxRoad = Math.max(0, roadW - width);
        if (!this.roadPlaced) {
            int at = indexOf(book.road, level);
            this.roadScroll = Math.max(0, Math.min(maxRoad, at * NODE_W - width / 2 + NODE_W / 2));
            this.roadPlaced = true;
        }
        this.roadScroll = Math.max(0, Math.min(maxRoad, this.roadScroll));

        int bottom = this.top + this.panelHeight - PADDING;
        int trackY = this.listTop + 34;

        Achievements.Road hoveredStop = null;
        String hoveredUnlock = null;

        Clip.on(graphics, x, this.listTop, x + width, bottom);
        try {
            int startX = x - this.roadScroll + NODE_W / 2;
            int endX = x - this.roadScroll + roadW - NODE_W / 2;
            int reached = Math.max(0, indexOf(book.road, level));
            int fillTo = level < 2 ? startX : startX + reached * NODE_W;
            Theme.roundedBox(graphics, startX, trackY, Math.max(1, endX - startX), 6, 3,
                    Theme.PANEL_SUNKEN);
            if (fillTo > startX) {
                Theme.roundedBox(graphics, startX, trackY, fillTo - startX, 6, 3,
                        Theme.withAlpha(Levels.colorOf(level), 0xDD));
            }

            for (int i = 0; i < book.road.length; i++) {
                Achievements.Road stop = book.road[i];
                if (stop == null || stop.level == null) continue;
                int sx = x - this.roadScroll + i * NODE_W;
                if (sx + NODE_W < x || sx > x + width) continue;

                String over = drawStop(graphics, stop, sx, trackY, level, mouseX, mouseY, bottom);
                if (over != null) {
                    hoveredStop = stop;
                    hoveredUnlock = over;
                }
            }
        } finally {
            Clip.off(graphics);
        }

        this.roadMax = maxRoad;
        if (maxRoad > 0) {
            int barY = bottom - 4;
            int thumb = Math.max(24, width * width / roadW);
            int offset = (width - thumb) * this.roadScroll / maxRoad;
            this.roadBarX = x;
            this.roadBarY = barY;
            this.roadBarW = width;
            this.roadThumbW = thumb;
            boolean lit = this.draggingRoad || overRoadBar(mouseX, mouseY);
            Theme.roundedBox(graphics, x, barY, width, 4, 2, Theme.withAlpha(0xFFFFFFFF, 0x12));
            Theme.roundedBox(graphics, x + offset, barY, thumb, 4, 2,
                    Theme.withAlpha(0xFFFFFFFF, lit ? 0xBB : 0x55));
        }

        if (hoveredStop != null && hoveredUnlock != null) {
            drawUnlockTip(graphics, hoveredUnlock, mouseX, mouseY);
        }
    }

    private boolean overRoadBar(int mouseX, int mouseY) {
        return this.roadTab && this.roadMax > 0 && this.roadBarW > 0
                && mouseX >= this.roadBarX && mouseX < this.roadBarX + this.roadBarW
                && mouseY >= this.roadBarY - BAR_GRAB && mouseY <= this.roadBarY + 4 + BAR_GRAB;
    }

    private void dragRoadTo(int mouseX) {
        int travel = Math.max(1, this.roadBarW - this.roadThumbW);
        int want = Math.max(0, Math.min(travel, mouseX - this.roadBarX - this.roadThumbW / 2));
        this.roadScroll = Math.round(this.roadMax * (float) want / travel);
    }

    private static int indexOf(Achievements.Road[] road, int level) {
        for (int i = 0; i < road.length; i++) {
            if (road[i] != null && road[i].level != null && road[i].level == level) return i;
        }
        return level > 100 ? road.length - 1 : 0;
    }

    private String drawStop(GuiGraphicsExtractor graphics, Achievements.Road stop, int x,
                            int trackY, int level, int mouseX, int mouseY, int bottom) {
        int cx = x + NODE_W / 2;
        int at = stop.level;
        boolean reached = level >= at;
        int colour = Levels.colorOf(at);

        Component label = Component.literal(String.valueOf(at));
        BigText.centered(graphics, this.font, label, cx, trackY - 24, reached ? colour
                : Theme.fade(colour, 0.45F), 2, 0);

        int dot = at == level ? 12 : 8;
        Theme.roundedBox(graphics, cx - dot / 2, trackY + 3 - dot / 2, dot, dot, dot / 2,
                reached ? colour : Theme.PANEL_RAISED);
        Theme.roundedBoxOutline(graphics, cx - dot / 2, trackY + 3 - dot / 2, dot, dot, dot / 2,
                reached ? Theme.withAlpha(0xFFFFFFFF, 0x88) : Theme.BORDER);

        int y = trackY + 16;

        if (stop.coins != null && stop.coins > 0) {
            String pay = "+" + AchievementText.grouped(stop.coins);
            int payW = this.font.width(pay);
            int total = payW + 2 + 16;
            int px = cx - total / 2;
            graphics.text(this.font, Component.literal(pay), px, y + 4,
                    reached ? GOLD : Theme.fade(GOLD, 0.5F));
            VanillaIcon.draw(graphics, VanillaIcon.Kind.COIN, px + payW + 2 + 8, y + 7, 16);
            y += 18;
        }

        for (int step : COLOUR_STEPS) {
            if (step == at) {
                Component chip = Component.translatable("vivet.achievements.road.colour");
                graphics.centeredText(this.font, chip, cx, y + 2, colour);
                y += 14;
            }
        }

        String hovered = null;
        int boxW = NODE_W - 10;
        if (stop.unlocks != null) {
            for (String id : stop.unlocks) {
                Wardrobe.Item item = Wardrobe.item(id);
                int boxH = item != null && "background".equals(item.kind) ? boxW * 9 / 16 : boxW;
                if (y + boxH > bottom - 8) break;
                boolean over = inside(mouseX, mouseY, cx - boxW / 2, y, boxW, boxH);
                drawUnlock(graphics, item, cx - boxW / 2, y, boxW, boxH, reached, over);
                if (over) hovered = id;
                y += boxH + 4;
            }
        }
        if (stop.soon != null) {
            for (Achievements.Soon soon : stop.soon) {
                if (soon == null) continue;
                int boxH = boxW;
                if (y + boxH > bottom - 8) break;
                Theme.roundedBox(graphics, cx - boxW / 2, y, boxW, boxH, CELL_R, Theme.PANEL_SUNKEN);
                Theme.roundedBoxOutline(graphics, cx - boxW / 2, y, boxW, boxH, CELL_R, Theme.BORDER);
                Component soonText = Component.translatable("vivet.achievements.road.soon");
                graphics.centeredText(this.font, soonText, cx, y + boxH / 2 - 4, Theme.TEXT_FAINT);
                y += boxH + 4;
            }
        }
        return hovered;
    }

    private void drawUnlock(GuiGraphicsExtractor graphics, Wardrobe.Item item, int x, int y,
                            int width, int height, boolean reached, boolean over) {
        int accent = item == null ? Theme.BORDER : Cosmetics.tierColour(Cosmetics.rarityOf(item));
        Theme.roundedBox(graphics, x, y, width, height, CELL_R, Theme.PANEL_SUNKEN);
        if (item != null) {
            Cosmetics.ready(item);
            Clip.on(graphics, x + 1, y + 1, x + width - 1, y + height - 1);
            try {
                Cosmetics.draw(graphics, this.font, item, x + 2, y + 2, width - 4, height - 4,
                        0L, true, false);
            } finally {
                Clip.off(graphics);
            }
        }
        if (!reached) {
            Theme.fill(graphics, x + 1, y + 1, x + width - 1, y + height - 1, 0x90080A11);
        }
        Theme.roundedBoxOutline(graphics, x, y, width, height, CELL_R,
                over ? Theme.BORDER_STRONG : Theme.withAlpha(accent, 0x99));
    }

    private void drawUnlockTip(GuiGraphicsExtractor graphics, String id, int mouseX, int mouseY) {
        Wardrobe.Item item = Wardrobe.item(id);
        if (item == null) return;
        List<Tip.Line> lines = Tip.lines();
        lines.add(new Tip.Line(Cosmetics.nameOf(item), Cosmetics.tierColour(Cosmetics.rarityOf(item))));
        Component flavour = Cosmetics.tipOf(item, null);
        if (flavour != null) Tip.wrap(lines, flavour, Theme.TEXT_MUTED, this.font);
        lines.add(Tip.gap());
        lines.add(new Tip.Line(Cosmetics.unlockLine(item), GOLD));
        Tip.draw(graphics, this.font, lines, mouseX, mouseY, this.width, this.height);
    }

    private int scaled(double value) {
        return (int) (value / uiScale());
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private int backX() {
        return this.pageLeft + this.pageWidth + BACK_GAP;
    }

    private boolean onBack(int mouseX, int mouseY) {
        return inside(mouseX, mouseY, backX(), this.top, UiButtons.SIDE, UiButtons.SIDE);
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

        int railX = this.left + PADDING;
        int tabs = railTabsTop();
        if (inside(mx, my, railX, tabs, RAIL_W, TAB_H)) {
            if (this.roadTab) {
                this.roadTab = false;
                Cues.confirm();
            }
            return true;
        }
        if (inside(mx, my, railX, tabs + TAB_H + 4, RAIL_W, TAB_H)) {
            if (!this.roadTab) {
                this.roadTab = true;
                this.roadPlaced = false;
                Cues.confirm();
            }
            return true;
        }

        if (overRoadBar(mx, my)) {
            this.draggingRoad = true;
            dragRoadTo(mx);
            return true;
        }

        if (!this.roadTab) {
            int groupsTop = groupsTop();
            for (AchievementText.Filter which : AchievementText.Filter.values()) {
                if (inside(mx, my, railX, groupsTop + which.ordinal() * GROUP_H, RAIL_W,
                        GROUP_H - 2)) {
                    if (which != this.filter) {
                        this.filter = which;
                        this.scroll = 0;
                        Cues.confirm();
                        layout();
                    }
                    return true;
                }
            }

            if (maxScroll() > 0 && overBar(mx, my)) {
                this.draggingBar = true;
                dragTo(my);
                return true;
            }

            Card card = claimAt(mx, my);
            if (card != null) {
                if (Achievements.claim(card.entry().id, card.node())) {
                    Cues.claimed();
                    layout();
                } else {
                    Cues.deny();
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private Card claimAt(int mx, int my) {
        if (my < this.listTop || my >= this.listTop + this.listHeight) return null;
        int x = this.pageLeft + PADDING;
        int cardW = this.pageWidth - PADDING * 2 - BAR_W - BAR_GRAB;
        for (int i = 0; i < this.cards.size(); i++) {
            Card card = this.cards.get(i);
            if (!card.claimable()) continue;
            int cy = this.listTop - this.scroll + i * (CARD_H + CARD_GAP);
            int rx = x + cardW - RIGHT_W;
            int bx = rx + (RIGHT_W - CLAIM_W) / 2;
            int by = cy + CARD_H - CLAIM_H - 7;
            if (inside(mx, my, bx, by, CLAIM_W, CLAIM_H)) return card;
        }
        return null;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.draggingBar = false;
        this.draggingRoad = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.draggingBar) {
            dragTo(scaled(event.y()));
            return true;
        }
        if (this.draggingRoad) {
            dragRoadTo(scaled(event.x()));
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (this.roadTab) {
            double step = deltaX != 0.0 ? deltaX : deltaY;
            this.roadScroll -= (int) Math.signum(step) * NODE_W;
            return true;
        }
        if (maxScroll() > 0) {
            this.scroll = Math.max(0, Math.min(maxScroll(),
                    this.scroll - (int) Math.signum(deltaY) * (CARD_H + CARD_GAP)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }
}
