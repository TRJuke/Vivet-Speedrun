package com.vivet.speedrun.client.screen;

import com.vivet.speedrun.client.Cues;
import com.vivet.speedrun.client.ui.Chip;
import com.vivet.speedrun.client.ui.Clip;
import com.vivet.speedrun.client.ui.Dropdown;
import com.vivet.speedrun.client.ui.FlatButton;
import com.vivet.speedrun.client.ui.Levels;
import com.vivet.speedrun.client.ui.PickerRow;
import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.client.ui.UiButtons;
import com.vivet.speedrun.game.Difficulty;
import com.vivet.speedrun.game.ItemTiers;
import com.vivet.speedrun.game.Practice;
import com.vivet.speedrun.game.Time;
import com.vivet.speedrun.p2p.CollectionBook;
import com.vivet.speedrun.p2p.MojangAuth;
import com.vivet.speedrun.p2p.RankedApi;
import com.vivet.speedrun.p2p.Visits;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CollectionScreen extends VivetScreen {
    private static final int MAX_WIDTH = 900;
    private static final int MIN_WIDTH = 420;

    private static final int PADDING = 14;
    private static final int HEADER_H = 34;

    private static final int FILTER_H = 52;

    private static final int RAIL_GAP = 8;

    private static final int ITEM_SCALE = 3;

    private static final int ITEM = 16 * ITEM_SCALE;

    private static final int CELL = 3 * 20 + 2 * 2;
    private static final int GAP = 2;

    private static final int GROUP_H = 18;

    private static final int BAR_W = 4;
    private static final int BAR_GRAB = 6;

    private static final float FOUND_TINT = 0.14F;

    private static final int MISSING_SHADE = 0xDA101218;

    private static final int CARD_TOP = 60;
    private static final int CARD_LINE = 13;
    private static final int CARD_BOTTOM = 28;

    private final Screen parent;

    private final String uuid;

    private final String who;

    private final Visits.Book book;

    private EditBox searchBox;

    private static final int SEARCH_FRAME_H = 22;

    private int cardLeft;
    private int cardBottom;
    private int cardWidth;
    private String search = "";

    private boolean hideFound;
    private boolean hideMissing;

    private Sort sort = Sort.DIFFICULTY;
    private boolean sortOpen;

    private int scroll;

    private boolean draggingBar;

    private boolean overBack;

    private String opened;

    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int filterTop;
    private int gridTop;
    private int gridHeight;
    private int gridWidth;
    private int columns;
    private int barX;
    private int sortWidth;

    private final List<Group> groups = new ArrayList<>();

    private int contentHeight;

    private long laidOutFrom = -1L;

    private final Map<String, String> searchNames = new HashMap<>();

    private final Map<String, ItemStack> stacks = new HashMap<>();

    private record Group(Difficulty difficulty, List<ItemTiers.Entry> items, int found,
                         int matchedFound, int matched, int size) {
        boolean finished() {
            return size > 0 && found >= size;
        }
    }

    private enum Sort {
        DIFFICULTY("difficulty"),
        FOUND("found"),
        MISSING("missing"),
        ATTEMPTS("attempts"),
        FINISHES("finishes"),
        FASTEST("fastest"),
        FAMILY("family");

        private final String id;

        Sort(String id) {
            this.id = id;
        }

        Component label() {
            return Component.translatable("vivet.collection.sort." + this.id);
        }
    }

    public CollectionScreen(Screen parent) {
        this(parent, null, null);
    }

    public CollectionScreen(Screen parent, String uuid, String name) {
        super(uuid == null
                ? Component.translatable("vivet.screen.collection")
                : Component.translatable("vivet.screen.collection.theirs",
                        name == null ? "" : name));

        this.parent = parent;
        this.uuid = uuid;
        this.who = name == null ? "" : name;
        this.book = uuid == null ? null : Visits.collection(uuid);

        this.hideMissing = uuid != null;
    }

    private boolean mine() {
        return this.uuid == null;
    }

    private boolean known() {
        return mine() ? CollectionBook.known() : this.book.known();
    }

    private long stamp() {
        return mine() ? CollectionBook.stamp() : this.book.stamp();
    }

    @Override
    protected void init() {
        super.init();

        if (mine()) {
            CollectionBook.refresh();
        } else {
            Visits.collection(this.uuid);
        }

        int room = Math.min(MAX_WIDTH, this.width - 16 - (UiButtons.SIDE + RAIL_GAP) * 2)
                - PADDING * 2 - BAR_W - BAR_GRAB;
        this.columns = Math.max(1, (room + GAP) / (CELL + GAP));

        this.gridWidth = this.columns * (CELL + GAP) - GAP + BAR_W + BAR_GRAB;

        int side = UiButtons.SIDE + RAIL_GAP;

        this.panelWidth = Math.min(this.width - 16 - side * 2,
                Math.max(MIN_WIDTH, this.gridWidth + PADDING * 2));

        int chrome = PADDING + HEADER_H + filterHeight() + 8 + PADDING;
        this.gridHeight = Math.max(CELL + GROUP_H, this.height - 24 - chrome);
        this.panelHeight = chrome + this.gridHeight;

        this.left = (this.width - this.panelWidth) / 2;
        this.top = Math.max(0, (this.height - this.panelHeight) / 2);
        this.filterTop = this.top + PADDING + HEADER_H;
        this.gridTop = this.filterTop + filterHeight() + 8;

        this.gridWidth = this.panelWidth - PADDING * 2;

        this.barX = this.left + PADDING + this.gridWidth - BAR_W;

        buildFilters();
        layout();

        if (this.sortOpen) buildSortOptions();
    }

    @Override
    public void tick() {
        super.tick();

        if (this.laidOutFrom != stamp()) layout();
    }

    private void buildFilters() {
        int right = this.left + this.panelWidth - PADDING;

        Component missingLabel = Component.translatable("vivet.collection.hide_missing");
        Component foundLabel = Component.translatable("vivet.collection.hide_found");

        int missingWidth = Chip.widthFor(missingLabel);
        int foundWidth = Chip.widthFor(foundLabel);

        this.sortWidth = Math.max(150, missingWidth + 4 + foundWidth);

        int searchRight = right - (mine() ? missingWidth + 4 + foundWidth : this.sortWidth);

        if (mine()) {
            addRenderableWidget(new Chip(right - missingWidth, this.filterTop, missingWidth, 16,
                    missingLabel, Theme.TEXT_MUTED,
                    () -> this.hideMissing,
                    () -> {
                        this.hideMissing = !this.hideMissing;

                        if (this.hideMissing) this.hideFound = false;
                        onFilterChanged();
                    }));

            addRenderableWidget(new Chip(right - missingWidth - 4 - foundWidth, this.filterTop,
                    foundWidth, 16, foundLabel, Theme.SUCCESS,
                    () -> this.hideFound,
                    () -> {
                        this.hideFound = !this.hideFound;
                        if (this.hideFound) this.hideMissing = false;
                        onFilterChanged();
                    }));
        }

        int searchLeft = this.left + PADDING + 6;
        int searchWidth = Math.max(60, searchRight - 10 - searchLeft);

        this.searchBox = new EditBox(this.font, searchLeft, Theme.textTop(this.filterTop, SEARCH_FRAME_H),
                searchWidth, 13,
                Component.translatable("vivet.collection.search"));
        this.searchBox.setBordered(false);
        this.searchBox.setMaxLength(48);
        this.searchBox.setTextColor(Theme.TEXT);
        this.searchBox.setHint(Component.translatable("vivet.collection.search")
                .copy().withStyle(ChatFormatting.ITALIC));
        this.searchBox.setValue(this.search);
        this.searchBox.setResponder(text -> {
            this.search = text == null ? "" : text;
            onFilterChanged();
        });
        addRenderableWidget(this.searchBox);

        addRenderableWidget(new Dropdown(right - this.sortWidth, sortY(), this.sortWidth, 18,
                Component.translatable("vivet.collection.sort"),
                () -> this.sort.label(),
                () -> Theme.ACCENT,
                () -> this.sortOpen,
                this::toggleSort));
    }

    private int sortY() {
        return this.filterTop + (mine() ? 26 : 2);
    }

    private int filterHeight() {
        return mine() ? FILTER_H : FILTER_H - 26;
    }

    private void toggleSort() {
        this.sortOpen = !this.sortOpen;
        rebuild();
    }

    private void choose(Sort chosen) {
        this.sortOpen = false;
        Cues.confirm();

        if (this.sort != chosen) {
            this.sort = chosen;
            this.scroll = 0;
        }
        rebuild();
    }

    private void buildSortOptions() {
        sealUnderList();
        int right = this.left + this.panelWidth - PADDING;
        int listY = sortY() + 18 + 2;
        int rowH = 16;

        for (Sort option : Sort.values()) {
            addRenderableWidget(new PickerRow(right - this.sortWidth,
                    listY + option.ordinal() * rowH, this.sortWidth, rowH,
                    option.label(),

                    () -> null,
                    () -> Theme.TEXT_FAINT,
                    () -> false,
                    Theme.ACCENT,
                    option == this.sort,
                    () -> choose(option)));
        }

        addRenderableWidget(new FlatButton(0, 0, this.width, this.height,
                Component.empty(), this::toggleSort, FlatButton.Style.INVISIBLE));
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private void onFilterChanged() {
        this.scroll = 0;
        layout();
    }

    private void layout() {
        this.groups.clear();

        String needle = this.search.trim().toLowerCase(Locale.ROOT);
        Comparator<ItemTiers.Entry> order = order();

        for (Difficulty difficulty : Difficulty.values()) {
            List<ItemTiers.Entry> kept = new ArrayList<>();
            int found = 0;
            int matchedFound = 0;
            int matched = 0;
            int size = 0;

            for (ItemTiers.Entry entry : ItemTiers.collection()) {
                if (entry.difficulty() != difficulty) continue;
                size++;

                boolean has = finishes(entry) > 0;
                if (has) found++;

                if (!needle.isEmpty() && !matches(entry, needle)) continue;
                matched++;
                if (has) matchedFound++;

                if (this.hideFound && has) continue;
                if (this.hideMissing && !has) continue;

                kept.add(entry);
            }

            kept.sort(order);

            this.groups.add(new Group(difficulty, kept, found, matchedFound, matched, size));
        }

        int height = 0;
        for (Group group : this.groups) {
            height += GROUP_H;
            height += rowsIn(group) * (CELL + GAP);
        }
        this.contentHeight = Math.max(0, height);
        this.scroll = Math.max(0, Math.min(maxScroll(), this.scroll));
        this.laidOutFrom = stamp();
    }

    private Comparator<ItemTiers.Entry> order() {
        Comparator<ItemTiers.Entry> byName = Comparator.comparing(ItemTiers.Entry::key);

        return switch (this.sort) {
            case DIFFICULTY -> byName;

            case FOUND -> Comparator
                    .comparingInt((ItemTiers.Entry e) -> finishes(e) > 0 ? 0 : 1)
                    .thenComparing(byName);
            case MISSING -> Comparator
                    .comparingInt((ItemTiers.Entry e) -> finishes(e) > 0 ? 1 : 0)
                    .thenComparing(byName);

            case ATTEMPTS -> Comparator
                    .comparingInt((ItemTiers.Entry e) -> -attempts(e))
                    .thenComparing(byName);
            case FINISHES -> Comparator
                    .comparingInt((ItemTiers.Entry e) -> -finishes(e))
                    .thenComparing(byName);

            case FASTEST -> Comparator
                    .comparingInt((ItemTiers.Entry e) -> {
                        int best = best(e);
                        return best <= 0 ? Integer.MAX_VALUE : best;
                    })
                    .thenComparing(byName);

            case FAMILY -> Comparator
                    .comparing((ItemTiers.Entry e) -> ItemTiers.familyOf(e.target()))
                    .thenComparing(byName);
        };
    }

    private int rowsIn(Group group) {
        return group.items().isEmpty()
                ? 1
                : (group.items().size() + this.columns - 1) / this.columns;
    }

    private boolean matches(ItemTiers.Entry entry, String needle) {
        if (entry.key().toLowerCase(Locale.ROOT).contains(needle)) return true;
        return searchName(entry).contains(needle);
    }

    private String searchName(ItemTiers.Entry entry) {
        return this.searchNames.computeIfAbsent(entry.key(),
                key -> entry.target().displayName().getString().toLowerCase(Locale.ROOT));
    }

    private ItemStack stackFor(ItemTiers.Entry entry) {
        ItemStack cached = this.stacks.get(entry.key());
        if (cached != null) return cached;

        ItemStack built = entry.target().displayStack();
        if (!built.isEmpty()) this.stacks.put(entry.key(), built);
        return built;
    }

    private RankedApi.Found record(ItemTiers.Entry entry) {
        return mine() ? CollectionBook.get(entry.key()) : this.book.get(entry.key());
    }

    private int finishes(ItemTiers.Entry entry) {
        RankedApi.Found found = record(entry);
        return found == null || found.finishes == null ? 0 : found.finishes;
    }

    private int attempts(ItemTiers.Entry entry) {
        RankedApi.Found found = record(entry);
        if (found == null || found.attempts == null) return 0;

        return Math.max(found.attempts, finishes(entry));
    }

    private int best(ItemTiers.Entry entry) {
        RankedApi.Found found = record(entry);
        return found == null || found.best == null ? 0 : found.best;
    }

    private int total() {
        return ItemTiers.collection().size();
    }

    private int foundTotal() {
        int total = 0;
        for (Group group : this.groups) total += group.found();
        return total;
    }

    private int maxScroll() {
        return Math.max(0, this.contentHeight - this.gridHeight);
    }

    @Override
    protected void drawScaled(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                              float partialTick) {
        this.overBack = onBack(mouseX, mouseY);

        Theme.fill(graphics, 0, 0, this.width, this.height, Theme.SCRIM);
        Theme.panel(graphics, this.left, this.top, this.panelWidth, this.panelHeight);

        drawHeader(graphics);
        drawSearchFrame(graphics);
        drawGrid(graphics, mouseX, mouseY);

        UiButtons.button(graphics, UiButtons.Kind.BACK, backX(), this.top,
                this.overBack && this.opened == null, true, false);

        drawWidgets(graphics, mouseX, mouseY, partialTick);

        if (this.opened != null) drawCard(graphics, mouseX, mouseY);
    }

    private void drawHeader(GuiGraphicsExtractor graphics) {
        int x = this.left + PADDING;
        int y = this.top + PADDING;

        graphics.text(this.font, this.title.copy().withStyle(ChatFormatting.BOLD),
                x, y + 10, Theme.TEXT);

        Component count = known()
                ? Component.translatable("vivet.collection.found", foundTotal(), total())
                : waitingLine();

        int right = this.left + this.panelWidth - PADDING;
        graphics.text(this.font, count, right - this.font.width(count), y + 10,
                known() ? Theme.SUCCESS : Theme.TEXT_FAINT);
    }

    private Component waitingLine() {
        if (!mine() && this.book.refused()) {
            return Component.translatable("vivet.collection.private", this.who);
        }
        if (mine() && MojangAuth.token() == null) {
            return Component.translatable("vivet.collection.signed_out");
        }
        return Component.translatable("vivet.collection.waiting");
    }

    private void drawSearchFrame(GuiGraphicsExtractor graphics) {
        if (this.searchBox == null) return;

        int x = this.searchBox.getX() - 6;
        int y = this.filterTop;
        int width = this.searchBox.getWidth() + 12;

        Theme.roundedRect(graphics, x, y, width, SEARCH_FRAME_H, Theme.PANEL_SUNKEN);
        Theme.roundedOutline(graphics, x, y, width, SEARCH_FRAME_H, Theme.BORDER);
    }

    private void drawGrid(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = this.left + PADDING;
        int bottom = this.gridTop + this.gridHeight;

        Clip.on(graphics, x, this.gridTop, x + this.gridWidth, bottom);
        try {
            int y = this.gridTop - this.scroll;
            boolean everything = !this.groups.isEmpty() && this.groups.stream().allMatch(Group::finished);

            for (Group group : this.groups) {
                y = drawGroup(graphics, group, x, y, bottom, mouseX, mouseY, everything);
                if (y > bottom) break;
            }
        } finally {
            Clip.off(graphics);
        }

        drawScrollbar(graphics, bottom, mouseX, mouseY);
    }

    private int drawGroup(GuiGraphicsExtractor graphics, Group group, int x, int y,
                          int bottom, int mouseX, int mouseY, boolean everything) {
        int rows = rowsIn(group);
        int height = GROUP_H + rows * (CELL + GAP);

        if (y + height < this.gridTop || y > bottom) return y + height;

        Component name = Component.literal(group.difficulty().displayName())
                .copy().withStyle(ChatFormatting.BOLD);
        graphics.text(this.font, name, x, y + 4, group.difficulty().color());

        Component tally = Component.literal(group.matchedFound() + "/" + group.matched());

        graphics.text(this.font, tally, x + this.font.width(name) + 8, y + 4,
                group.finished() ? group.difficulty().color() : Theme.TEXT_FAINT);

        y += GROUP_H;

        if (group.items().isEmpty()) {
            graphics.text(this.font, Component.translatable("vivet.collection.none_here"),
                    x, y + 4, Theme.TEXT_FAINT);
            return y + CELL + GAP;
        }

        for (int i = 0; i < group.items().size(); i++) {
            int cellX = x + (i % this.columns) * (CELL + GAP);
            int cellY = y + (i / this.columns) * (CELL + GAP);

            if (cellY + CELL < this.gridTop || cellY > bottom) continue;

            drawCell(graphics, group.items().get(i), cellX, cellY,
                     mouseX >= cellX && mouseX < cellX + CELL
                             && mouseY >= cellY && mouseY < cellY + CELL
                             && mouseY >= this.gridTop && mouseY < bottom,
                     group.finished(), everything);
        }
        return y + rows * (CELL + GAP);
    }

    private void drawCell(GuiGraphicsExtractor graphics, ItemTiers.Entry entry, int x, int y,
                          boolean hovered, boolean finished, boolean everything) {
        boolean has = finishes(entry) > 0;
        int accent = everything ? pageChroma(x, y)
                : finished ? tierChroma(entry.difficulty(), x, y)
                : entry.difficulty().color();

        Theme.roundedRect(graphics, x, y, CELL, CELL,
                has ? Theme.mix(Theme.PANEL_SUNKEN, accent, FOUND_TINT) : Theme.PANEL_SUNKEN);

        ItemStack stack = stackFor(entry);
        int inset = (CELL - ITEM) / 2;

        if (!stack.isEmpty()) {
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(x + inset, y + inset);
            pose.scale(ITEM_SCALE, ITEM_SCALE);
            graphics.item(stack, 0, 0);
            pose.popMatrix();
        } else {
            Component initials = Component.literal(shortName(entry));
            graphics.text(this.font, initials,
                    x + (CELL - this.font.width(initials)) / 2, y + CELL / 2 - 4,
                    has ? Theme.TEXT : Theme.TEXT_FAINT);
        }

        if (!has) Theme.fill(graphics, x + 1, y + 1, x + CELL - 1, y + CELL - 1, MISSING_SHADE);

        if (!has && attempts(entry) > 0) {
            Component tries = Component.literal(String.valueOf(attempts(entry)));
            graphics.text(this.font, tries,
                    x + CELL - 5 - this.font.width(tries), y + CELL - 12, accent, true);
        }

        Theme.roundedOutline(graphics, x, y, CELL, CELL,
                hovered ? Theme.BORDER_STRONG
                        : has ? Theme.withAlpha(accent, 0x88) : Theme.BORDER);
    }

    private static final float CHROMA_SPAN = 720.0F;

    private float along(int x, int y) {
        float along = ((x - this.left) + (y + this.scroll - this.gridTop)) / CHROMA_SPAN;
        return along - (float) Math.floor(along);
    }

    private int pageChroma(int x, int y) {
        return Levels.chroma(along(x, y));
    }

    private static final float TIER_PERIOD = 3200.0F;

    private static float[][] tierStops(Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> new float[][] {{0.33F, 0.80F, 0.90F}, {0.44F, 0.80F, 0.92F}, {0.51F, 0.70F, 0.95F}};
            case MEDIUM -> new float[][] {{0.14F, 0.90F, 0.95F}, {0.24F, 0.85F, 0.95F}};
            case HARD -> new float[][] {{0.065F, 0.95F, 0.95F}, {0.12F, 0.25F, 1.00F}};
            case EXTREME -> new float[][] {{0.00F, 0.88F, 0.92F}, {0.92F, 0.65F, 1.00F}};
            case IMPOSSIBLE -> new float[][] {{0.79F, 0.70F, 0.92F}, {0.66F, 0.95F, 0.85F}};
        };
    }

    private int tierChroma(Difficulty difficulty, int x, int y) {
        float turn = (System.currentTimeMillis() % (long) TIER_PERIOD) / TIER_PERIOD;
        float t = 0.5F + 0.5F * (float) Math.sin(2.0 * Math.PI * (turn + along(x, y)));

        float[][] stops = tierStops(difficulty);
        int spans = stops.length - 1;
        int k = Math.min((int) (t * spans), spans - 1);
        float f = t * spans - k;
        float[] a = stops[k];
        float[] b = stops[k + 1];
        float turnTo = b[0] - a[0];
        turnTo -= (float) Math.floor(turnTo + 0.5F);
        float hue = a[0] + turnTo * f;
        return fromHsv(hue - (float) Math.floor(hue), a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f);
    }

    private static int fromHsv(float h, float s, float v) {
        float c = v * s;
        float hp = h * 6.0F;
        float xx = c * (1.0F - Math.abs(hp % 2.0F - 1.0F));
        float r = 0.0F;
        float g = 0.0F;
        float b = 0.0F;
        switch ((int) hp % 6) {
            case 0 -> { r = c; g = xx; }
            case 1 -> { r = xx; g = c; }
            case 2 -> { g = c; b = xx; }
            case 3 -> { g = xx; b = c; }
            case 4 -> { r = xx; b = c; }
            default -> { r = c; b = xx; }
        }
        float m = v - c;
        return 0xFF000000 | (Math.round((r + m) * 255.0F) << 16) | (Math.round((g + m) * 255.0F) << 8)
                | Math.round((b + m) * 255.0F);
    }

    private String shortName(ItemTiers.Entry entry) {
        String name = entry.target().displayName().getString().trim();
        if (name.isEmpty()) name = entry.key();
        return name.substring(0, Math.min(2, name.length())).toUpperCase(Locale.ROOT);
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics, int bottom, int mouseX, int mouseY) {
        if (maxScroll() <= 0) return;

        boolean lit = this.draggingBar || overBar(mouseX, mouseY);

        Theme.fill(graphics, this.barX, this.gridTop, this.barX + BAR_W, bottom,
                Theme.withAlpha(0xFFFFFFFF, 0x14));
        Theme.roundedRect(graphics, this.barX, this.gridTop + thumbOffset(), BAR_W, thumbHeight(),
                Theme.withAlpha(0xFFFFFFFF, lit ? 0xBB : 0x55));
    }

    private int thumbHeight() {
        return Math.max(16,
                this.gridHeight * this.gridHeight / Math.max(1, this.contentHeight));
    }

    private int thumbOffset() {
        int travel = this.gridHeight - thumbHeight();
        return maxScroll() <= 0 ? 0 : travel * this.scroll / maxScroll();
    }

    private int backX() {
        return this.left + this.panelWidth + RAIL_GAP;
    }

    private boolean onBack(int mouseX, int mouseY) {
        return mouseX >= backX() && mouseX < backX() + UiButtons.SIDE
                && mouseY >= this.top && mouseY < this.top + UiButtons.SIDE;
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

    private ItemTiers.Entry card() {
        if (this.opened == null) return null;

        for (ItemTiers.Entry entry : ItemTiers.collection()) {
            if (entry.key().equals(this.opened)) return entry;
        }
        return null;
    }

    private void drawCard(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        ItemTiers.Entry entry = card();
        if (entry == null) {
            this.opened = null;
            return;
        }

        Theme.fill(graphics, this.left, this.top, this.left + this.panelWidth,
                      this.top + this.panelHeight, 0xB4080A0F);

        boolean has = finishes(entry) > 0;
        int accent = entry.difficulty().color();

        RankedApi.Found record = record(entry);
        int wins = number(record == null ? null : record.wins);
        int losses = number(record == null ? null : record.losses);
        int draws = number(record == null ? null : record.draws);

        boolean showPlace = has && record != null && record.place != null;
        boolean showRecord = has && wins + losses + draws > 0;

        int lines = 1 + (has ? 1 : 1) + (showPlace ? 1 : 0) + (showRecord ? 1 : 0);

        int width = Math.min(260, this.panelWidth - 40);
        int height = CARD_TOP + lines * CARD_LINE + CARD_BOTTOM;
        int x = this.left + (this.panelWidth - width) / 2;
        int y = this.top + (this.panelHeight - height) / 2;

        Theme.panel(graphics, x, y, width, height, Theme.PANEL_RAISED);

        int inner = x + 12;
        int line = y + 12;

        ItemStack stack = stackFor(entry);
        if (!stack.isEmpty()) {
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(inner, line);
            pose.scale(2.0F, 2.0F);
            graphics.item(stack, 0, 0);
            pose.popMatrix();
        }

        int textX = inner + 38;
        graphics.text(this.font, entry.target().displayName().copy()
                        .withStyle(ChatFormatting.BOLD), textX, line + 2, Theme.TEXT);
        graphics.text(this.font, Component.literal(entry.difficulty().displayName()),
                textX, line + 14, accent);

        line += 40;
        Theme.divider(graphics, inner, line, width - 24);
        line += 8;

        graphics.text(this.font, Component.translatable("vivet.collection.attempts",
                        finishes(entry), attempts(entry)),
                inner, line, has ? Theme.TEXT : Theme.TEXT_MUTED);
        line += CARD_LINE;

        if (has) {
            Integer best = record == null ? null : record.best;
            graphics.text(this.font, best == null
                            ? Component.translatable("vivet.collection.no_time")
                            : Component.translatable("vivet.collection.best",
                                    Time.formatPrecise(best)),
                    inner, line, Theme.TEXT);
            line += CARD_LINE;

            if (showPlace) {
                graphics.text(this.font,
                        Component.translatable("vivet.collection.place", record.place),
                        inner, line, 0xFFE8C34A);
                line += CARD_LINE;
            }

            if (showRecord) {
                graphics.text(this.font, Component.translatable("vivet.collection.record",
                                wins, losses, draws),
                        inner, line, Theme.TEXT_MUTED);
            }
        }

        this.cardLeft = x;
        this.cardBottom = y + height;
        this.cardWidth = width;

        if (mine()) {
            Component practice = Component.translatable("vivet.collection.practice");
            graphics.text(this.font, practice, practiceX(), practiceY(),
                    overPractice(mouseX, mouseY) ? Theme.ACCENT : Theme.TEXT_MUTED);
        }

        Component close = Component.translatable("vivet.collection.close");
        graphics.text(this.font, close,
                x + width - 12 - this.font.width(close), y + height - 20, Theme.TEXT_FAINT);
    }

    private int practiceX() {
        return this.cardLeft + 12;
    }

    private int practiceY() {
        return this.cardBottom - 20;
    }

    private boolean overPractice(int mouseX, int mouseY) {
        if (this.opened == null || !mine()) return false;

        int width = this.font.width(Component.translatable("vivet.collection.practice"));
        return mouseX >= practiceX() - 4 && mouseX <= practiceX() + width + 4
                && mouseY >= practiceY() - 5 && mouseY <= practiceY() + 13;
    }

    private void startPractice() {
        ItemTiers.Entry entry = card();
        if (entry == null || !mine()) return;

        Cues.confirm();
        Practice.choose(entry.target().id());
        this.minecraft.gui.setScreen(new PracticeScreen(this));
    }

    private static int number(Integer value) {
        return value == null ? 0 : value;
    }

    private int scaled(double value) {
        return (int) (value / uiScale());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.opened != null) {
            int cardX = scaled(event.x());
            int cardY = scaled(event.y());

            if (overPractice(cardX, cardY)) {
                startPractice();
                return true;
            }

            this.opened = null;
            Cues.confirm();
            return true;
        }

        int x = scaled(event.x());
        int y = scaled(event.y());

        if (onBack(x, y)) {
            Cues.confirm();
            onClose();
            return true;
        }

        if (maxScroll() > 0 && overBar(x, y)) {
            this.draggingBar = true;
            dragTo(y);
            return true;
        }

        if (!this.sortOpen) {
            String hit = cellAt(x, y);
            if (hit != null) {
                this.opened = hit;
                Cues.confirm();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
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
    public boolean mouseReleased(MouseButtonEvent event) {
        this.draggingBar = false;
        return super.mouseReleased(event);
    }

    private String cellAt(int mouseX, int mouseY) {
        int bottom = this.gridTop + this.gridHeight;
        if (mouseY < this.gridTop || mouseY >= bottom) return null;

        int left = this.left + PADDING;
        int y = this.gridTop - this.scroll;

        for (Group group : this.groups) {
            y += GROUP_H;

            int rows = rowsIn(group);
            for (int i = 0; i < group.items().size(); i++) {
                int cellX = left + (i % this.columns) * (CELL + GAP);
                int cellY = y + (i / this.columns) * (CELL + GAP);

                if (mouseX >= cellX && mouseX < cellX + CELL
                        && mouseY >= cellY && mouseY < cellY + CELL) {
                    return group.items().get(i).key();
                }
            }
            y += rows * (CELL + GAP);
        }
        return null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (this.opened == null && !this.sortOpen && maxScroll() > 0) {
            this.scroll = Math.max(0, Math.min(maxScroll(),
                    this.scroll - (int) Math.signum(deltaY) * (CELL + GAP)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
            if (this.opened != null) {
                this.opened = null;
                return true;
            }
            if (this.sortOpen) {
                toggleSort();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }
}
