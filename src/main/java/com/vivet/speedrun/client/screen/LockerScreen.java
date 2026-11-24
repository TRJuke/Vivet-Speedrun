package com.vivet.speedrun.client.screen;

import com.vivet.speedrun.client.Cues;
import com.vivet.speedrun.client.ui.Clip;
import com.vivet.speedrun.client.ui.Cosmetics;
import com.vivet.speedrun.client.ui.Marquee;
import com.vivet.speedrun.client.ui.Ranks;
import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.client.ui.UiButtons;
import com.vivet.speedrun.client.ui.Tip;
import com.vivet.speedrun.media.MediaSounds;
import com.vivet.speedrun.p2p.MojangAuth;
import com.vivet.speedrun.p2p.Profiles;
import com.vivet.speedrun.p2p.Visits;
import com.vivet.speedrun.p2p.Wardrobe;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class LockerScreen extends VivetScreen {
    private static final int MAX_WIDTH = 792;
    private static final int MIN_WIDTH = 420;

    private static final int PANEL_GAP = 10;

    private static final int PADDING = 14;

    private static final int GAP = 6;

    private static final int RAIL_GAP = 8;

    private static final int LEGEND_H = 14;

    private static final int TILE_MAX = 90;

    private static final int RAIL_W = TILE_MAX * 2 + GAP;

    private static final int HEAD_H = 30;

    private static final int CELL_TARGET = 80;

    private static final float WIDE_RATIO = 2.4F;

    private static final int PANEL_R = 8;
    private static final int CELL_R = 5;
    private static final int BANNER_R = 4;

    private static final int BANNER_H = 13;
    private static final int BANNER_INSET = 4;

    private static final int STANDIN_FILL = 0xFF101113;

    private static final int BANNER_FILL = 0xB4080A11;

    private static final int BAR_W = 4;
    private static final int BAR_GRAB = 7;

    private static final long HOLD_MS = 2000L;

    private static final float OWNED_TINT = 0.16F;

    private final Screen parent;

    private final String uuid;

    private final String who;

    private final Visits.Look look;

    private final Wardrobe.View view;

    private String slot = "emblem";

    private int scroll;
    private boolean draggingBar;

    private String holding;
    private long holdingSince;

    private String playing;
    private long playingSince;

    private String hovered;

    private long hoveredSince;

    private String previewing;

    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int railPanelWidth;

    private boolean overBack;

    private int gridPanelLeft;
    private int gridPanelWidth;
    private int railLeft;
    private int railTop;
    private int gridLeft;
    private int gridTop;
    private int gridWidth;
    private int gridHeight;
    private int columns;
    private int cellWidth;
    private int cellHeight;
    private int barX;

    private int tileSize;

    private long laidOutFrom = -1L;
    private int builtSlots = -1;

    private final long openedAt = System.currentTimeMillis();

    private final List<Wardrobe.Item> cells = new ArrayList<>();
    private int contentHeight;

    public LockerScreen(Screen parent) {
        this(parent, null, null);
    }

    public LockerScreen(Screen parent, String uuid, String name) {
        super(Component.translatable("vivet.screen.locker"));
        this.parent = parent;
        this.uuid = uuid;
        this.who = name == null ? "" : name;
        this.look = uuid == null ? null : Visits.locker(uuid);
        this.view = uuid == null ? Wardrobe.mine() : this.look;
    }

    private boolean mine() {
        return this.view.mine();
    }

    private String rankForTip() {
        MojangAuth.Profile profile = mine() ? MojangAuth.knownProfile() : Profiles.of(this.uuid);
        return profile == null || !profile.placed ? null : Ranks.displayRank(profile);
    }

    private String rankShown() {
        return mine() ? null : Ranks.displayRank(Profiles.of(this.uuid));
    }

    private static final int BACKGROUND_H = RAIL_W * 9 / 16;

    private static final int AFTER_TILES = 10;
    private static final int AFTER_TITLE = 12;
    private static final int AFTER_RULE = 10;
    private static final int HEADINGS_H = 13;

    private static final int RAIL_FIXED =
            HEAD_H
            + AFTER_TILES
            + BACKGROUND_H + AFTER_TITLE
            + AFTER_RULE
            + HEADINGS_H
            + GAP
            + 6;

    private int railHeight() {
        return RAIL_FIXED + this.tileSize * 3;
    }

    private int[] railRows() {
        int tiles = this.railTop;
        int background = tiles + this.tileSize + AFTER_TILES;
        int rule = background + BACKGROUND_H + AFTER_TITLE;
        int headings = rule + AFTER_RULE;
        int gifs = headings + HEADINGS_H;
        int sounds = gifs + this.tileSize + GAP;

        return new int[] { tiles, background, rule, headings, gifs, sounds };
    }

    @Override
    protected void init() {
        super.init();

        if (mine()) {
            Wardrobe.refresh();
        } else {
            Visits.locker(this.uuid);
        }

        int side = UiButtons.SIDE + RAIL_GAP;

        this.panelWidth = Math.max(MIN_WIDTH,
                Math.min(MAX_WIDTH, this.width - 16 - side * 2));

        int chrome = PADDING * 2;
        int room = this.height - LEGEND_H - 12;

        this.tileSize = Math.max(34,
                Math.min(TILE_MAX, (room - chrome - RAIL_FIXED) / 3));

        this.panelHeight = Math.min(room, railHeight() + chrome);

        this.left = (this.width - this.panelWidth) / 2;
        this.top = Math.max(LEGEND_H + 4, (this.height - this.panelHeight) / 2);

        this.railPanelWidth = RAIL_W + PADDING * 2;
        this.gridPanelLeft = this.left + this.railPanelWidth + PANEL_GAP;
        this.gridPanelWidth = this.left + this.panelWidth - this.gridPanelLeft;

        this.railLeft = this.left + PADDING;
        this.railTop = this.top + PADDING + HEAD_H;

        this.gridLeft = this.gridPanelLeft + PADDING;
        this.gridWidth = this.gridPanelWidth - PADDING * 2;
        this.gridTop = this.top + PADDING + HEAD_H + 20;
        this.gridHeight = this.top + this.panelHeight - PADDING - this.gridTop;

        this.builtSlots = this.view.slots().size();
        settleSlot();
        measureGrid();
        layout();
    }

    private void measureGrid() {
        int room = this.gridWidth - BAR_W - BAR_GRAB;

        float ratio = ratioOfPage();
        int target = Math.round(CELL_TARGET * Math.max(1.0F, ratio));

        this.columns = Math.max(1, (room + GAP) / (target + GAP));
        this.cellWidth = (room - (this.columns - 1) * GAP) / this.columns;
        this.cellHeight = Math.round(this.cellWidth / ratio);

        this.barX = this.gridLeft + this.gridWidth - BAR_W;
    }

    private float ratioOfPage() {
        if ("loading_background".equals(this.slot)) return 16.0F / 9.0F;
        if ("name_label".equals(this.slot)) return WIDE_RATIO;
        return 1.0F;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.builtSlots != this.view.slots().size()) {
            clearWidgets();
            init();
            return;
        }
        if (this.laidOutFrom != this.view.stamp()) layout();
    }

    private void settleSlot() {
        for (Wardrobe.Slot entry : this.view.slots()) {
            if (entry != null && this.slot.equals(entry.id)) return;
        }

        List<Wardrobe.Slot> slots = this.view.slots();
        if (!slots.isEmpty() && slots.get(0) != null) this.slot = slots.get(0).id;
    }

    private void choose(String id) {
        if (id.equals(this.slot)) return;

        this.previewing = null;
        this.slot = id;
        this.scroll = 0;
        Cues.confirm();

        measureGrid();
        layout();
    }

    private void layout() {
        this.cells.clear();

        String kind = kindOfSlot(this.slot);
        if (kind != null) {
            if (mine()) {
                if (!"emblem".equals(this.slot)) {
                    this.cells.add(Cosmetics.standIn(Cosmetics.DEFAULT, kind));
                }
                this.cells.add(Cosmetics.standIn(Cosmetics.RANDOM, kind));

                if (takesRank(this.slot)) {
                    this.cells.add(Cosmetics.standIn(Cosmetics.RANK, kind));
                }
            }

            List<Wardrobe.Item> locked = new ArrayList<>();
            for (Wardrobe.Item item : this.view.catalogue()) {
                if (item == null || !kind.equals(item.kind)) continue;

                if (this.view.owns(item.id)) {
                    this.cells.add(item);
                } else if (mine()) {
                    locked.add(item);
                }
            }
            this.cells.addAll(locked);
        }

        int rows = (this.cells.size() + this.columns - 1) / this.columns;
        this.contentHeight = Math.max(0, rows * (this.cellHeight + GAP) - GAP);
        this.scroll = Math.max(0, Math.min(maxScroll(), this.scroll));
        this.laidOutFrom = this.view.stamp();
    }

    private boolean takesRank(String id) {
        return "emblem".equals(id) || "name_label".equals(id);
    }

    private String kindOfSlot(String id) {
        for (Wardrobe.Slot entry : this.view.slots()) {
            if (entry != null && entry.id != null && entry.id.equals(id)) return entry.kind;
        }
        return null;
    }

    private int[] ownedOnPage() {
        String kind = kindOfSlot(this.slot);
        int owned = 0;
        int total = 0;

        for (Wardrobe.Item item : this.view.catalogue()) {
            if (item == null || kind == null || !kind.equals(item.kind)) continue;
            total++;
            if (this.view.owns(item.id)) owned++;
        }
        return new int[] { owned, total };
    }

    private int maxScroll() {
        return Math.max(0, this.contentHeight - this.gridHeight);
    }

    private long since() {
        return System.currentTimeMillis() - this.openedAt;
    }

    private long into(Wardrobe.Item item) {
        if (item == null) return since();
        if (!"gif".equals(item.kind)) {
            return item.id.equals(this.playing)
                    ? System.currentTimeMillis() - this.playingSince : since();
        }

        boolean previewing = item.id.equals(this.previewing);
        boolean pointed = item.id.equals(this.hovered) && this.view.owns(item.id);

        if (!previewing && !pointed) return 0L;

        String media = Cosmetics.mediaOf(item.id);
        int length = media == null ? 0 : com.vivet.speedrun.media.MediaClips.lengthMs(media);
        long elapsed = previewing ? since() : System.currentTimeMillis() - this.hoveredSince;

        return length <= 0 ? elapsed : elapsed % length;
    }

    private String wornIn(String id) {
        String mode = this.view.modeOf(id);

        if ("rank".equals(mode)) return Cosmetics.RANK;
        if ("random".equals(mode) || "favourites".equals(mode)) return Cosmetics.RANDOM;

        String worn = this.view.inSlot(id);
        return worn.isEmpty() ? Cosmetics.DEFAULT : worn;
    }

    @Override
    protected void drawScaled(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                              float partialTick) {
        Theme.fill(graphics, 0, 0, this.width, this.height, Theme.SCRIM);

        Component legend = mine()
                ? Component.translatable("vivet.locker.legend")
                : Component.translatable("vivet.locker.legend.theirs");
        graphics.text(this.font, legend,
                this.left + (this.panelWidth - this.font.width(legend)) / 2,
                this.top - LEGEND_H, Theme.TEXT_MUTED);

        panel(graphics, this.left, this.top, this.railPanelWidth, this.panelHeight);
        panel(graphics, this.gridPanelLeft, this.top, this.gridPanelWidth, this.panelHeight);

        this.overBack = onBack(mouseX, mouseY);
        UiButtons.button(graphics, UiButtons.Kind.BACK, backX(), this.top,
                this.overBack && this.previewing == null, true, false);

        drawRail(graphics, mouseX, mouseY);
        drawGridHeader(graphics, mouseX, mouseY);
        drawGrid(graphics, mouseX, mouseY);

        drawWidgets(graphics, mouseX, mouseY, partialTick);

        if (this.previewing != null) {
            drawPreview(graphics);
            return;
        }

        drawTip(graphics, mouseX, mouseY);
    }

    private void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        Theme.roundedBox(graphics, x, y, width, height, PANEL_R, Theme.PANEL);
        Theme.roundedBoxOutline(graphics, x, y, width, height, PANEL_R, Theme.BORDER);
    }

    private void drawBanner(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                            Component label, int colour) {
        int bw = width - BANNER_INSET * 2;
        int bx = x + BANNER_INSET;
        int by = y + height - BANNER_H - BANNER_INSET;

        Theme.roundedBox(graphics, bx, by, bw, BANNER_H, BANNER_R, BANNER_FILL);

        Marquee.drawSpotifyCentred(graphics, this.font, Component.literal(label.getString()),
                bx + bw / 2, by + 3, bw - 6, colour);
    }

    private void drawRail(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = this.railLeft;
        int tile = this.tileSize;

        Component heading = mine()
                ? Component.translatable("vivet.locker.mine")
                : Component.translatable("vivet.locker.theirs", this.who);

        graphics.text(this.font, heading.copy().withStyle(ChatFormatting.BOLD),
                x, this.top + PADDING + 4, Theme.TEXT);

        int[] rows = railRows();

        drawTile(graphics, "emblem", x, rows[0], tile, tile, true, mouseX, mouseY);
        drawTile(graphics, "name_label", x + tile + GAP, rows[0], tile, tile, true,
                 mouseX, mouseY);

        drawTile(graphics, "loading_background", x, rows[1], RAIL_W, BACKGROUND_H, true,
                 mouseX, mouseY);

        Theme.divider(graphics, x, rows[2], RAIL_W);

        Component intro = Component.translatable("vivet.locker.group.intro");
        Component outro = Component.translatable("vivet.locker.group.outro");

        graphics.text(this.font, intro, x + (tile - this.font.width(intro)) / 2, rows[3],
                Theme.TEXT_MUTED);
        graphics.text(this.font, outro, x + tile + GAP + (tile - this.font.width(outro)) / 2,
                rows[3], Theme.TEXT_MUTED);

        int between = x + tile + GAP / 2;
        graphics.fillGradient(between, rows[3] - 2, between + 1,
                rows[5] + tile, 0x14FFFFFF, 0x04FFFFFF);

        drawTile(graphics, "loading_gif", x, rows[4], tile, tile, false, mouseX, mouseY);
        drawTile(graphics, "win_gif", x + tile + GAP, rows[4], tile, tile, false, mouseX, mouseY);

        drawTile(graphics, "loading_audio", x, rows[5], tile, tile, false, mouseX, mouseY);
        drawTile(graphics, "win_audio", x + tile + GAP, rows[5], tile, tile, false, mouseX,
                 mouseY);
    }

    private void drawTile(GuiGraphicsExtractor graphics, String id, int x, int y, int width,
                          int height, boolean bySlotName, int mouseX, int mouseY) {
        boolean selected = id.equals(this.slot);
        boolean over = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;

        Theme.roundedBox(graphics, x, y, width, height, CELL_R,
                selected ? Theme.PLATE_LIT : over ? Theme.mix(Theme.PLATE, Theme.PLATE_LIT, 0.6F)
                        : Theme.PLATE);

        String worn = wornIn(id);
        Wardrobe.Item item = itemFor(worn, kindOfSlot(id));

        if (item != null) {
            Cosmetics.ready(item);

            Clip.on(graphics, x + 1, y + 1, x + width - 1, y + height - 1);
            try {
                Cosmetics.draw(graphics, this.font, item, x + 3, y + 3, width - 6, height - 6,
                        into(item), true, false, rankShown(), over);
            } finally {
                Clip.off(graphics);
            }
        }

        Component label = bySlotName
                ? Component.translatable("vivet.locker.slot." + id)
                : Cosmetics.nameOf(item);

        int colour = bySlotName ? Theme.TEXT_MUTED
                : Cosmetics.isStandIn(worn) ? Theme.TEXT_MUTED
                : Cosmetics.tierColour(item == null ? null : Cosmetics.rarityOf(item));

        drawBanner(graphics, x, y, width, height, label, colour);

        Theme.roundedBoxOutline(graphics, x, y, width, height, CELL_R,
                selected ? 0xFFD07BE0 : over ? Theme.PLATE_EDGE_LIT : Theme.PLATE_EDGE);
    }

    private Wardrobe.Item itemFor(String id, String kind) {
        if (Cosmetics.isStandIn(id)) return Cosmetics.standIn(id, kind);
        return this.view.item(id);
    }

    private void drawGridHeader(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        String kind = kindOfSlot(this.slot);
        int y = this.top + PADDING + 4;

        String page = kind == null ? "emblem" : kind;

        Component heading = mine()
                ? Component.translatable("vivet.locker.page." + page)
                : Component.translatable("vivet.locker.page.theirs." + page, this.who);

        String text = this.font.plainSubstrByWidth(heading.getString(), this.gridWidth);

        graphics.text(this.font, Component.literal(text).withStyle(ChatFormatting.BOLD),
                this.gridLeft, y, Theme.TEXT);

        Theme.divider(graphics, this.gridLeft, y + 14, this.gridWidth);

        int[] owned = ownedOnPage();
        Component count = this.view.known()
                ? Component.translatable("vivet.locker.owned", owned[0], owned[1])
                : waitingLine();

        graphics.text(this.font, count, this.gridLeft, y + 21,
                this.view.known() ? Theme.TEXT_MUTED : Theme.TEXT_FAINT);
    }
    private void drawGrid(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int bottom = this.gridTop + this.gridHeight;

        Clip.on(graphics, this.gridLeft, this.gridTop,
                this.gridLeft + this.gridWidth, bottom);
        try {
            for (int i = 0; i < this.cells.size(); i++) {
                int cellX = cellX(i);
                int cellY = cellY(i);

                if (cellY + this.cellHeight < this.gridTop || cellY > bottom) continue;

                drawCell(graphics, this.cells.get(i), cellX, cellY,
                         mouseX >= cellX && mouseX < cellX + this.cellWidth
                                 && mouseY >= cellY && mouseY < cellY + this.cellHeight
                                 && mouseY >= this.gridTop && mouseY < bottom);
            }

            if (this.cells.isEmpty()) {
                Component none = this.view.known()
                        ? Component.translatable(mine()
                                ? "vivet.locker.none_here" : "vivet.locker.none_theirs")
                        : waitingLine();

                graphics.text(this.font, none, this.gridLeft + 2, this.gridTop + 6,
                        Theme.TEXT_FAINT);
            }
        } finally {
            Clip.off(graphics);
        }

        drawScrollbar(graphics, bottom, mouseX, mouseY);
    }

    private Component waitingLine() {
        if (!mine() && this.look != null && this.look.refused()) {
            return Component.translatable("vivet.locker.private", this.who);
        }
        if (mine() && MojangAuth.token() == null) {
            return Component.translatable("vivet.locker.signed_out");
        }
        return Component.translatable(mine()
                ? "vivet.locker.waiting" : "vivet.locker.waiting.theirs");
    }

    private int cellX(int index) {
        return this.gridLeft + (index % this.columns) * (this.cellWidth + GAP);
    }

    private int cellY(int index) {
        return this.gridTop - this.scroll + (index / this.columns) * (this.cellHeight + GAP);
    }

    private void drawCell(GuiGraphicsExtractor graphics, Wardrobe.Item item, int x, int y,
                          boolean hovered) {
        boolean special = Cosmetics.isStandIn(item.id);
        boolean owned = special || this.view.owns(item.id);
        boolean mine = owned;
        boolean worn = item.id.equals(wornIn(this.slot));

        int accent = special ? Theme.PLATE_EDGE_LIT : Cosmetics.tierColour(Cosmetics.rarityOf(item));

        Theme.roundedBox(graphics, x, y, this.cellWidth, this.cellHeight, CELL_R,
                special ? STANDIN_FILL
                        : mine ? Theme.mix(Theme.PANEL_RAISED, accent, OWNED_TINT)
                        : Theme.PANEL_SUNKEN);

        Cosmetics.ready(item);

        Cosmetics.draw(graphics, this.font, item, x + 3, y + 3, this.cellWidth - 6,
                this.cellHeight - 6, into(item), mine, false, rankShown(),
                hovered || item.id.equals(this.holding));

        if (mine) {
            drawBanner(graphics, x, y, this.cellWidth, this.cellHeight, Cosmetics.nameOf(item),
                    special ? Theme.TEXT_MUTED : accent);
        } else {
            Theme.fill(graphics, x + 2, y + 2, x + this.cellWidth - 2, y + this.cellHeight - 2,
                    0xC0080A11);
            Cosmetics.drawLock(graphics, x, y, this.cellWidth, this.cellHeight);
        }

        if (worn) Theme.fill(graphics, x + 5, y + 5, x + 10, y + 10, 0xFFFFFFFF);
        if (this.view.isFavourite(item.id)) drawStar(graphics, x + this.cellWidth - 13, y + 4);

        if (owned && item.id.equals(this.holding)) {
            float held = Math.min(1.0F,
                    (System.currentTimeMillis() - this.holdingSince) / (float) HOLD_MS);
            int reach = Math.round((this.cellWidth - 10) * held);
            Theme.fill(graphics, x + 5, y + this.cellHeight - 3, x + 5 + reach,
                    y + this.cellHeight - 1, 0xFFD07BE0);
        }

        Theme.roundedBoxOutline(graphics, x, y, this.cellWidth, this.cellHeight, CELL_R,
                worn ? 0xFFFFFFFF : hovered ? Theme.BORDER_STRONG
                        : mine ? Theme.withAlpha(accent, 0x99) : Theme.BORDER);
    }

    private void drawStar(GuiGraphicsExtractor graphics, int x, int y) {
        int gold = 0xFFF5C542;

        Theme.fill(graphics, x + 4, y, x + 5, y + 3, gold);
        Theme.fill(graphics, x + 3, y + 2, x + 6, y + 4, gold);
        Theme.fill(graphics, x, y + 3, x + 9, y + 5, gold);
        Theme.fill(graphics, x + 1, y + 5, x + 8, y + 6, gold);
        Theme.fill(graphics, x + 2, y + 6, x + 7, y + 8, gold);
        Theme.fill(graphics, x + 1, y + 8, x + 3, y + 9, gold);
        Theme.fill(graphics, x + 6, y + 8, x + 8, y + 9, gold);
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics, int bottom, int mouseX, int mouseY) {
        if (maxScroll() <= 0) return;

        boolean lit = this.draggingBar || overBar(mouseX, mouseY);

        Theme.roundedBox(graphics, this.barX, this.gridTop, BAR_W, this.gridHeight, BAR_W / 2,
                Theme.withAlpha(0xFFFFFFFF, 0x12));
        Theme.roundedBox(graphics, this.barX, this.gridTop + thumbOffset(), BAR_W, thumbHeight(),
                BAR_W / 2, Theme.withAlpha(0xFFFFFFFF, lit ? 0xBB : 0x55));
    }

    private int thumbHeight() {
        return Math.max(18, this.gridHeight * this.gridHeight / Math.max(1, this.contentHeight));
    }

    private int thumbOffset() {
        int travel = this.gridHeight - thumbHeight();
        return maxScroll() <= 0 ? 0 : travel * this.scroll / maxScroll();
    }

    private boolean overBar(int mouseX, int mouseY) {
        return mouseX >= this.barX - BAR_GRAB && mouseX <= this.barX + BAR_W + BAR_GRAB
                && mouseY >= this.gridTop && mouseY < this.gridTop + this.gridHeight;
    }

    private void dragTo(int mouseY) {
        int travel = Math.max(1, this.gridHeight - thumbHeight());
        int want = Math.max(0, Math.min(travel, mouseY - this.gridTop - thumbHeight() / 2));

        this.scroll = Math.max(0, Math.min(maxScroll(),
                Math.round(maxScroll() * (float) want / travel)));
    }

    private Component tierLine(Wardrobe.Item item) {
        Component rarity = Component.literal(Cosmetics.tierName(Cosmetics.rarityOf(item)).getString()
                .toUpperCase(java.util.Locale.ROOT)).withStyle(ChatFormatting.BOLD);

        return Component.translatable("vivet.locker.tier_line", rarity, this.view.setName(item.set));
    }

    private void drawTip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Wardrobe.Item item = cellAt(mouseX, mouseY);

        if (item == null || Cosmetics.RANDOM.equals(item.id)) return;

        boolean special = Cosmetics.isStandIn(item.id);
        boolean mine = special || this.view.owns(item.id);

        List<Tip.Line> lines = Tip.lines();

        if (mine) {
            lines.add(new Tip.Line(Cosmetics.nameOf(item),
                    special ? Theme.TEXT : Cosmetics.tierColour(Cosmetics.rarityOf(item))));

            String rank = rankForTip();
            if (Cosmetics.RANK.equals(item.id) && rank != null && !"unranked".equals(rank)) {
                lines.add(new Tip.Line(Component.literal(Ranks.nameOf(rank)), Ranks.colorOf(rank)));
            }

            Component flavour = Cosmetics.tipOf(item, rank);
            if (flavour != null) Tip.wrap(lines, flavour, Theme.TEXT_MUTED, this.font);

            if (!special) {
                lines.add(Tip.gap());
                lines.add(new Tip.Line(tierLine(item),
                        Cosmetics.tierColour(Cosmetics.rarityOf(item))));
            }
        } else {
            lines.add(new Tip.Line(Cosmetics.nameOf(item), Theme.TEXT_MUTED));
            lines.add(new Tip.Line(Component.translatable("vivet.locker.locked"), 0xFFE0523C));
            Tip.wrap(lines, Cosmetics.unlockLine(item), 0xFFE8C34A, this.font);
        }

        Tip.draw(graphics, this.font, lines, mouseX, mouseY, this.width, this.height);
    }

    private int scaled(double value) {
        return (int) (value / uiScale());
    }

    private Wardrobe.Item cellAt(int mouseX, int mouseY) {
        int bottom = this.gridTop + this.gridHeight;
        if (mouseY < this.gridTop || mouseY >= bottom) return null;
        if (mouseX < this.gridLeft || mouseX >= this.gridLeft + this.gridWidth) return null;

        for (int i = 0; i < this.cells.size(); i++) {
            int x = cellX(i);
            int y = cellY(i);

            if (mouseX >= x && mouseX < x + this.cellWidth
                    && mouseY >= y && mouseY < y + this.cellHeight) {
                return this.cells.get(i);
            }
        }
        return null;
    }

    private String tileAt(int mouseX, int mouseY) {
        int x = this.railLeft;
        int tile = this.tileSize;

        if (mouseX < x || mouseX > x + RAIL_W) return null;

        int[] rows = railRows();
        boolean left = mouseX < x + tile + GAP / 2;

        if (in(mouseY, rows[0], tile)) {
            return left ? "emblem" : "name_label";
        }
        if (in(mouseY, rows[1], BACKGROUND_H)) return "loading_background";
        if (in(mouseY, rows[4], tile)) return left ? "loading_gif" : "win_gif";
        if (in(mouseY, rows[5], tile)) return left ? "loading_audio" : "win_audio";

        return null;
    }

    private static boolean in(int value, int start, int height) {
        return value >= start && value < start + height;
    }

    private void updateHover(int mouseX, int mouseY) {
        Wardrobe.Item item = cellAt(mouseX, mouseY);
        String id = item == null ? null : item.id;

        if (id == null) {
            String slot = tileAt(mouseX, mouseY);
            if (slot != null) id = wornIn(slot);
        }

        if (java.util.Objects.equals(id, this.hovered)) return;
        this.hovered = id;
        this.hoveredSince = System.currentTimeMillis();

        if (this.playing != null && !this.playing.equals(id)
                && !this.playing.equals(this.previewing)) {
            MediaSounds.stopAll();
            this.playing = null;
            this.playingSince = 0L;
        }

        if (item == null || !"audio".equals(item.kind)) return;

        if (!this.view.owns(item.id)) return;

        String media = Cosmetics.mediaOf(item.id);
        if (media == null) return;

        MediaSounds.stopAll();
        MediaSounds.play(media, 1.0F);

        this.playing = item.id;
        this.playingSince = System.currentTimeMillis();
    }

    private void reveal(Wardrobe.Item item) {
        this.previewing = item.id.equals(this.previewing) ? null : item.id;
        Cues.confirm();

        if (this.previewing == null) return;

        String media = Cosmetics.mediaOf(item.id);
        if (media == null || !"audio".equals(item.kind)) return;

        MediaSounds.stopAll();
        MediaSounds.play(media, 1.0F);
        this.playing = item.id;
        this.playingSince = System.currentTimeMillis();
    }

    private void drawPreview(GuiGraphicsExtractor graphics) {
        Wardrobe.Item item = this.view.item(this.previewing);
        if (item == null) {
            this.previewing = null;
            return;
        }

        Theme.fill(graphics, 0, 0, this.width, this.height, 0xC4060810);

        boolean owned = this.view.owns(item.id);
        int accent = Cosmetics.tierColour(Cosmetics.rarityOf(item));

        List<Tip.Line> lines = Tip.lines();

        lines.add(new Tip.Line(tierLine(item), accent));

        Component flavour = Cosmetics.tipOf(item, rankForTip());
        if (flavour != null) Tip.wrap(lines, flavour, Theme.TEXT_MUTED, this.font);

        if (mine()) {
            lines.add(new Tip.Line(Component.empty(), 0));
            lines.add(owned
                    ? new Tip.Line(Component.translatable("vivet.locker.preview.owned"),
                            Theme.SUCCESS)
                    : new Tip.Line(Cosmetics.unlockLine(item), 0xFFE8C34A));
        }

        int art = Math.min(150, Math.max(80, this.panelHeight / 3));
        int width = Math.min(300, this.panelWidth - 60);
        int height = 34 + art + 10 + lines.size() * (this.font.lineHeight + 2) + 26;

        int x = this.left + (this.panelWidth - width) / 2;
        int y = Math.max(4, this.top + (this.panelHeight - height) / 2);

        Theme.roundedBox(graphics, x, y, width, height, PANEL_R, Theme.PANEL_RAISED);
        Theme.roundedBoxOutline(graphics, x, y, width, height, PANEL_R,
                Theme.withAlpha(accent, 0x99));

        Component name = Cosmetics.nameOf(item);
        graphics.text(this.font, name.copy().withStyle(ChatFormatting.BOLD),
                x + (width - this.font.width(name)) / 2, y + 12, accent);

        int stage = y + 30;
        Theme.roundedBox(graphics, x + 12, stage, width - 24, art, CELL_R, Theme.PANEL_SUNKEN);

        Cosmetics.ready(item);
        Cosmetics.draw(graphics, this.font, item, x + 15, stage + 3, width - 30, art - 6,
                into(item), true, false, rankShown());

        int line = stage + art + 10;
        for (Tip.Line entry : lines) {
            String text = entry.text().getString();
            if (!text.isEmpty()) {
                Component shown = entry.text();
                if (this.font.width(shown) > width - 24) {
                    shown = Component.literal(this.font.plainSubstrByWidth(text, width - 24));
                }
                graphics.text(this.font, shown,
                        x + (width - this.font.width(shown)) / 2, line, entry.colour());
            }
            line += this.font.lineHeight + 2;
        }

        Component close = Component.translatable("vivet.locker.preview.close");
        graphics.text(this.font, close, x + (width - this.font.width(close)) / 2,
                y + height - 16, Theme.TEXT_FAINT);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        updateHover(scaled(mouseX), scaled(mouseY));
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int x = scaled(event.x());
        int y = scaled(event.y());

        if (this.previewing != null) {
            this.previewing = null;
            MediaSounds.stopAll();
            Cues.confirm();
            return true;
        }

        if (onBack(x, y)) {
            Cues.confirm();
            onClose();
            return true;
        }

        if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
            if (!mine()) return super.mouseClicked(event, doubleClick);

            Wardrobe.Item item = cellAt(x, y);
            if (item == null) return super.mouseClicked(event, doubleClick);

            if (!Cosmetics.isStandIn(item.id) && !this.view.owns(item.id)) {
                reveal(item);
                return true;
            }

            this.holding = item.id;
            this.holdingSince = System.currentTimeMillis();
            return true;
        }

        String tile = tileAt(x, y);
        if (tile != null) {
            choose(tile);
            return true;
        }

        if (maxScroll() > 0 && overBar(x, y)) {
            this.draggingBar = true;
            dragTo(y);
            return true;
        }

        Wardrobe.Item item = cellAt(x, y);
        if (item != null) {
            if (mine()) equip(item); else reveal(item);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void equip(Wardrobe.Item item) {
        if (Cosmetics.DEFAULT.equals(item.id)) {
            Wardrobe.equip(this.slot, "", "fixed");
            Cues.confirm();
            return;
        }

        if (Cosmetics.RANDOM.equals(item.id)) {
            Wardrobe.equip(this.slot, Wardrobe.inSlot(this.slot), "random");
            Cues.confirm();
            return;
        }

        if (Cosmetics.RANK.equals(item.id)) {
            Wardrobe.equipRank(this.slot);
            Cues.confirm();
            return;
        }

        if (!this.view.owns(item.id)) {
            Cues.deny();
            return;
        }

        Wardrobe.equip(this.slot, item.id, "fixed");
        Cues.confirm();
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.draggingBar = false;

        if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT && this.holding != null) {
            String id = this.holding;
            long held = System.currentTimeMillis() - this.holdingSince;
            this.holding = null;

            Wardrobe.Item item = this.view.item(id);

            if (held >= HOLD_MS && item != null && item.set != null
                    && this.view.ownsWholeSet(item.set)) {
                Wardrobe.equipSet(item.set);
                Cues.confirm();
            } else if (held < HOLD_MS && this.view.owns(id)) {
                Wardrobe.favourite(id, !Wardrobe.isFavourite(id));
                Cues.confirm();
            }
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.draggingBar) {
            dragTo(scaled(event.y()));
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (maxScroll() > 0) {
            this.scroll = Math.max(0, Math.min(maxScroll(),
                    this.scroll - (int) Math.signum(deltaY) * (this.cellHeight + GAP)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE && this.previewing != null) {
            this.previewing = null;
            MediaSounds.stopAll();
            return true;
        }
        return super.keyPressed(event);
    }

    private int backX() {
        return this.gridPanelLeft + this.gridPanelWidth + RAIL_GAP;
    }

    private boolean onBack(int mouseX, int mouseY) {
        return mouseX >= backX() && mouseX < backX() + UiButtons.SIDE
                && mouseY >= this.top && mouseY < this.top + UiButtons.SIDE;
    }

    @Override
    public void onClose() {
        MediaSounds.stopAll();
        this.minecraft.gui.setScreen(this.parent);
    }
}
