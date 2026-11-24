package com.vivet.speedrun.client.screen;

import com.vivet.speedrun.client.Cues;
import com.vivet.speedrun.client.ui.BigText;
import com.vivet.speedrun.client.ui.Chip;
import com.vivet.speedrun.client.ui.Clip;
import com.vivet.speedrun.client.ui.Cosmetics;
import com.vivet.speedrun.client.ui.NameLabel;
import com.vivet.speedrun.client.ui.RankTitle;
import com.vivet.speedrun.client.ui.Plural;
import com.vivet.speedrun.client.ui.PlayerHead;
import com.vivet.speedrun.client.ui.RailRow;
import com.vivet.speedrun.client.ui.RankEmblem;
import com.vivet.speedrun.client.ui.Ranks;
import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.client.ui.Tip;
import com.vivet.speedrun.client.ui.UiButtons;
import com.vivet.speedrun.client.ui.VanillaIcon;
import com.vivet.speedrun.game.Difficulty;
import com.vivet.speedrun.game.ItemTiers;
import com.vivet.speedrun.game.Target;
import com.vivet.speedrun.game.Time;
import com.vivet.speedrun.media.MediaImages;
import com.vivet.speedrun.p2p.MojangAuth;
import com.vivet.speedrun.p2p.RankedApi;
import com.vivet.speedrun.p2p.ReplayApi;
import com.vivet.speedrun.p2p.Wardrobe;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LeaderboardScreen extends VivetScreen {
    private static final int MAX_WIDTH = 660;
    private static final int PADDING = 14;
    private static final int PANEL_R = 8;

    private static final int TAB_W = 152;
    private static final int TAB_H = 26;
    private static final int TAB_GAP = 4;
    private static final int TAB_HEADER_H = 32;
    private static final int COLUMN_GAP = 10;

    private static final int HEADER_H = 26;

    private static final int FILTER_NONE = 4;
    private static final int FILTER_BANDS = 26;
    private static final int FILTER_SEARCH = 56;

    private static final int CARD_TOP_H = 96;

    private static final int CARD_SECOND_H = 74;
    private static final int CARD_H = 52;
    private static final int ROW_H = 22;
    private static final int CARD_GAP = 6;
    private static final int CARD_R = 6;

    private static final int HEAD_TOP = 48;
    private static final int HEAD_SECOND = 32;
    private static final int HEAD = 16;

    private static final int EMBLEM = 36;

    private static final int EMBLEM_SECOND = 60;

    private static final int BACKDROP_TINT = 0xFF7A7A7A;
    private static final int BACKDROP_WASH = 0x8C000000;

    private static final int CELL = 30;
    private static final int CELL_GAP = 2;
    private static final int PICKER_ROWS = 3;

    private static final int PLAY = 16;

    private record Board(String id, String label, int color, VanillaIcon.Kind icon, String crown,
                         String unit, boolean times, boolean searchable, boolean live) {
    }

    private final Screen parent;

    private volatile RankedApi.Board board;
    private volatile String status = "";
    private volatile String loadedId = "";

    private List<Board> boards;
    private int chosen;

    private Difficulty band = Difficulty.HARD;

    private String pinnedItem = "";

    private Target pinned;

    private String search = "";
    private EditBox searchBox;

    private List<Target> suggestions = List.of();

    private int scroll;

    private int left;
    private int top;
    private int boardLeft;
    private int boardWidth;
    private int panelHeight;
    private int tabHeight = TAB_H;
    private int listTop;
    private int listHeight;

    private boolean overBack;
    private boolean overClear;
    private int overCell = -1;

    private RankedApi.Row overPlay;

    private boolean fetchingReplay;

    private boolean settingText;

    public LeaderboardScreen(Screen parent) {
        super(Component.translatable("vivet.screen.leaderboards"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        if (this.boards == null) this.boards = buildBoards();

        int gutter = UiButtons.SIDE + COLUMN_GAP;
        int total = Math.min(MAX_WIDTH, this.width - 16 - gutter);

        this.boardWidth = Math.max(210, total - TAB_W - COLUMN_GAP);
        this.panelHeight = Math.max(260, Math.min(620, this.height - 26));

        this.tabHeight = TAB_H;
        int needed = tabsHeight();
        if (needed > this.panelHeight) {
            this.tabHeight = Math.max(16,
                    (this.panelHeight - TAB_HEADER_H - 8 - (this.boards.size() - 1) * TAB_GAP)
                            / this.boards.size());
        }

        this.left = (this.width - (TAB_W + COLUMN_GAP + this.boardWidth + gutter)) / 2;
        this.top = Math.max(0, (this.height - this.panelHeight) / 2);
        this.boardLeft = this.left + TAB_W + COLUMN_GAP;

        this.listTop = this.top + PADDING + HEADER_H + filterHeight();
        this.listHeight = Math.max(ROW_H, this.top + this.panelHeight - PADDING - this.listTop);

        buildTabs();
        buildFilters();

        RankedApi.loadConfig(config -> { }, reason -> { });
        if (current().live() && this.board == null && this.status.isEmpty()) load();
    }

    private int tabsHeight() {
        return TAB_HEADER_H + 8
                + this.boards.size() * (this.tabHeight + TAB_GAP) - TAB_GAP;
    }

    private int filterHeight() {
        Board board = current();
        if (!board.times()) return FILTER_NONE;
        return board.searchable() ? FILTER_SEARCH : FILTER_BANDS;
    }

    private List<Board> buildBoards() {
        List<Board> list = new ArrayList<>();

        list.add(new Board("rating", "Ladder", Theme.ACCENT, VanillaIcon.Kind.LADDER,
                "vivet.leaderboards.crown.rating", "vivet.leaderboards.unit.points",
                false, false, true));
        list.add(new Board("xp", "Most XP", 0xFF9BE04A, VanillaIcon.Kind.BOTTLE,
                "vivet.leaderboards.crown.xp", "vivet.leaderboards.unit.xp",
                false, false, true));
        list.add(new Board("wins", "Wins", 0xFF4AD8E0, VanillaIcon.Kind.DIAMOND,
                "vivet.leaderboards.crown.wins", "vivet.leaderboards.unit.wins",
                false, false, true));
        list.add(new Board("streak", "Win Streak", Theme.SUCCESS, VanillaIcon.Kind.ENDER_EYE,
                "vivet.leaderboards.crown.streak", "vivet.leaderboards.unit.streak",
                false, false, true));
        list.add(new Board("best_streak", "Best Streak", Theme.WARNING, VanillaIcon.Kind.ENDER_PEARL,
                "vivet.leaderboards.crown.best_streak", "vivet.leaderboards.unit.streak",
                false, false, true));
        list.add(new Board("band", "Fastest Times", 0xFFE8863C, VanillaIcon.Kind.SPEED,
                "vivet.leaderboards.crown.band", "", true, true, true));
        list.add(new Board("playtime", "Playtime", 0xFFD9C27A, VanillaIcon.Kind.CLOCK,
                "vivet.leaderboards.crown.playtime", "", false, false, true));
        list.add(new Board("seed", "Monthly Seed", 0xFF9C7BE0, VanillaIcon.Kind.SEEDS,
                "vivet.leaderboards.crown.seed", "", true, false, true));
        list.add(new Board("ltm", "LTM", 0xFF5A72E0, VanillaIcon.Kind.LAPIS,
                "vivet.leaderboards.crown.ltm", "", false, false, false));

        return list;
    }

    private Board current() {
        return this.boards.get(Math.max(0, Math.min(this.chosen, this.boards.size() - 1)));
    }

    private void buildTabs() {
        int y = this.top + TAB_HEADER_H + 8;

        for (int i = 0; i < this.boards.size(); i++) {
            Board option = this.boards.get(i);
            int index = i;

            addRenderableWidget(new RailRow(this.left, y, TAB_W, this.tabHeight,
                    Component.literal(option.label()),
                    null,
                    option.icon(),
                    option.color(),
                    () -> this.chosen == index,
                    () -> choose(index)));
            y += this.tabHeight + TAB_GAP;
        }
    }

    private void buildFilters() {
        Board board = current();
        if (!board.times()) return;

        int x = this.boardLeft + PADDING;
        int y = this.top + PADDING + HEADER_H;

        for (Difficulty option : Difficulty.values()) {
            Component label = Component.literal(option.displayName());
            int width = Chip.widthFor(label);

            addRenderableWidget(new Chip(x, y, width, 16, label, option.color(),
                    () -> this.band == option,
                    () -> {
                        this.band = option;
                        this.pinnedItem = "";
                        clearSearch();
                        reload();
                    }));
            x += width + 4;
        }

        if (!board.searchable()) return;

        int fieldLeft = fieldX() + 26;
        int fieldRight = fieldX() + fieldWidth() - 24;

        this.searchBox = new EditBox(this.font, fieldLeft, Theme.textTop(fieldY(), FIELD_H),
                Math.max(40, fieldRight - fieldLeft), 14,
                Component.translatable("vivet.leaderboards.search"));
        this.searchBox.setBordered(false);
        this.searchBox.setMaxLength(48);
        this.searchBox.setTextColor(Theme.TEXT);
        this.searchBox.setHint(Component.translatable("vivet.leaderboards.search")
                .copy().withStyle(ChatFormatting.ITALIC));
        setText(this.searchBox, this.search);
        this.searchBox.setResponder(this::onSearch);
        addRenderableWidget(this.searchBox);
    }

    private int fieldX() {
        return this.boardLeft + PADDING;
    }

    private int fieldY() {
        return this.top + PADDING + HEADER_H + 24;
    }

    private int fieldWidth() {
        return this.boardWidth - PADDING * 2;
    }

    private static final int FIELD_H = 24;

    private int pickerColumns() {
        return Math.max(1, Math.min(9, (fieldWidth() - 8) / (CELL + CELL_GAP)));
    }

    private int pickerLimit() {
        return pickerColumns() * PICKER_ROWS;
    }

    private int pickerWidth() {
        return pickerColumns() * (CELL + CELL_GAP) - CELL_GAP + 8;
    }

    private boolean pickerShowing() {
        if (!current().searchable()) return false;
        if (!this.suggestions.isEmpty()) return true;

        return !this.search.isEmpty() && this.pinnedItem.isEmpty();
    }

    private static final int EMPTY_H = 24;

    private int pickerPanelHeight() {
        return this.suggestions.isEmpty() ? EMPTY_H : pickerHeight();
    }

    private int pickerHeight() {
        int rows = (this.suggestions.size() + pickerColumns() - 1) / pickerColumns();
        return Math.max(1, rows) * (CELL + CELL_GAP) - CELL_GAP + 8;
    }

    private int cellX(int index) {
        return fieldX() + 4 + (index % pickerColumns()) * (CELL + CELL_GAP);
    }

    private int cellY(int index) {
        return fieldY() + FIELD_H + 4 + 4 + (index / pickerColumns()) * (CELL + CELL_GAP);
    }

    private boolean overPicker(int mouseX, int mouseY) {
        if (!pickerShowing()) return false;

        return in(mouseX, mouseY, fieldX(), fieldY() + FIELD_H + 4,
                pickerWidth(), pickerPanelHeight());
    }

    private int clearX() {
        return fieldX() + fieldWidth() - 20;
    }

    private void choose(int index) {
        if (this.chosen == index) return;

        this.chosen = index;
        this.pinnedItem = "";
        clearSearch();

        if (!this.boards.get(index).live()) Cues.nothingHappened();

        reload();
    }

    private void onSearch(String typed) {
        if (this.settingText) return;

        this.search = typed == null ? "" : typed;
        this.suggestions = matches(this.search);
        this.overCell = -1;
    }

    private List<Target> matches(String typed) {
        String needle = typed == null ? "" : typed.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return List.of();

        List<Target> starts = new ArrayList<>();
        List<Target> contains = new ArrayList<>();
        int limit = pickerLimit();

        for (Target target : ItemTiers.catalogue(this.band)) {
            String path = pathOf(target.id()).replace('_', ' ');

            if (path.startsWith(needle)) starts.add(target);
            else if (path.contains(needle)) contains.add(target);
        }

        starts.sort(LeaderboardScreen::plainestFirst);
        contains.sort(LeaderboardScreen::plainestFirst);
        starts.addAll(contains);

        return starts.size() <= limit ? starts : starts.subList(0, limit);
    }

    private static int plainestFirst(Target a, Target b) {
        int byLength = Integer.compare(a.id().length(), b.id().length());
        return byLength != 0 ? byLength : a.id().compareTo(b.id());
    }

    private void pick(Target target) {
        this.pinnedItem = target.id();
        this.pinned = target;
        this.search = prettyId(target.id());
        this.suggestions = List.of();
        this.overCell = -1;
        Cues.confirm();
        reload();
    }

    private void clearSearch() {
        this.search = "";
        this.pinned = null;
        this.suggestions = List.of();
        this.overCell = -1;
        if (this.searchBox != null) setText(this.searchBox, "");
    }

    private void setText(EditBox box, String text) {
        this.settingText = true;
        try {
            box.setValue(text);
        } finally {
            this.settingText = false;
        }
    }

    private void reload() {
        this.board = null;
        this.status = "";
        this.scroll = 0;
        rebuild();
        if (current().live()) load();
    }

    private void rebuild() {
        String typed = this.search;
        boolean focused = this.searchBox != null && this.searchBox.isFocused();

        clearWidgets();
        init();

        if (this.searchBox != null && !typed.isEmpty()) {
            setText(this.searchBox, typed);
            if (focused) setInitialFocus(this.searchBox);
        }
    }

    private void load() {
        Board board = current();
        String key = board.id() + ":" + this.band.id() + ":" + this.pinnedItem;
        this.loadedId = key;
        this.status = "";

        java.util.function.Consumer<RankedApi.Board> loaded =
                result -> this.minecraft.execute(() -> {
                    if (!key.equals(this.loadedId)) return;

                    this.board = result;
                    this.status = result != null && result.error != null ? result.error : "";
                });
        java.util.function.Consumer<String> failed =
                reason -> this.minecraft.execute(() -> {
                    if (key.equals(this.loadedId)) this.status = reason;
                });

        if (board.times() && !this.pinnedItem.isEmpty()) {
            RankedApi.itemBoard(this.pinnedItem, loaded, failed);
        } else if (board.id().equals("band")) {
            RankedApi.bandBoard(this.band, loaded, failed);
        } else if (board.id().equals("seed")) {
            RankedApi.seedBoard(this.band, loaded, failed);
        } else if (board.id().equals("rating")) {
            RankedApi.ratingBoard(loaded, failed);
        } else {
            RankedApi.playerBoard(board.id(), loaded, failed);
        }
    }

    private List<RankedApi.Row> rows() {
        RankedApi.Board shown = this.board;
        if (shown == null || shown.rows == null) return List.of();

        List<RankedApi.Row> rows = new ArrayList<>(shown.rows.length);
        for (RankedApi.Row row : shown.rows) if (row != null) rows.add(row);
        return rows;
    }

    private int heightOf(RankedApi.Row row) {
        if (row.place == 1) return CARD_TOP_H;
        if (row.place == 2) return CARD_SECOND_H;
        return row.place == 3 ? CARD_H : ROW_H;
    }

    private int contentHeight(List<RankedApi.Row> rows) {
        int height = 0;
        for (RankedApi.Row row : rows) height += heightOf(row) + CARD_GAP;
        return Math.max(0, height - CARD_GAP);
    }

    private int maxScroll(List<RankedApi.Row> rows) {
        return Math.max(0, contentHeight(rows) - this.listHeight);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int x = scaled(mouseX);
        boolean overBoards = x >= this.boardLeft && x < this.boardLeft + this.boardWidth;

        int limit = overBoards ? maxScroll(rows()) : 0;
        if (limit <= 0) return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);

        this.scroll = Math.max(0, Math.min(limit,
                this.scroll - (int) Math.signum(deltaY) * ROW_H));
        return true;
    }

    @Override
    protected void drawScaled(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                              float partialTick) {
        Theme.fill(graphics, 0, 0, this.width, this.height, Theme.SCRIM);

        Board board = current();
        findHover(mouseX, mouseY);

        plate(graphics, this.left, this.top, TAB_W, TAB_HEADER_H);
        graphics.text(this.font, this.title.copy().withStyle(ChatFormatting.BOLD),
                this.left + 12, this.top + (TAB_HEADER_H - 8) / 2, Theme.TEXT);

        plate(graphics, this.boardLeft, this.top, this.boardWidth, this.panelHeight);

        int headerX = this.boardLeft + PADDING;
        graphics.text(this.font, Component.literal(board.label()), headerX,
                this.top + PADDING + 2, board.color());
        Theme.fill(graphics, headerX, this.top + PADDING + HEADER_H - 10,
                headerX + this.boardWidth - PADDING * 2, this.top + PADDING + HEADER_H - 9,
                Theme.BORDER);

        boolean sealed = overPicker(mouseX, mouseY);

        if (!board.live()) {
            drawComingSoon(graphics);
        } else {
            drawList(graphics, board, sealed ? -1 : mouseX, sealed ? -1 : mouseY);
        }

        if (board.searchable()) drawPicker(graphics);

        UiButtons.button(graphics, UiButtons.Kind.BACK, backX(), this.top, this.overBack,
                true, false);

        drawWidgets(graphics, mouseX, mouseY, partialTick);

        if (this.overCell >= 0 && this.overCell < this.suggestions.size()) {
            List<Tip.Line> lines = Tip.lines();
            Tip.wrap(lines, Component.literal(prettyId(this.suggestions.get(this.overCell).id())),
                    Theme.TEXT, this.font);
            Tip.draw(graphics, this.font, lines, mouseX, mouseY, this.width, this.height);
        } else if (this.overPlay != null) {
            List<Tip.Line> lines = Tip.lines();
            Tip.wrap(lines, Component.translatable(this.overPlay.replay.isEmpty()
                    ? "vivet.leaderboards.no_recording" : "vivet.leaderboards.watch"), Theme.TEXT, this.font);
            Tip.draw(graphics, this.font, lines, mouseX, mouseY, this.width, this.height);
        }
    }

    private void plate(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        Theme.roundedBox(graphics, x, y, width, height, PANEL_R, Theme.PANEL);
        Theme.roundedBoxOutline(graphics, x, y, width, height, PANEL_R, Theme.BORDER);
    }

    private int backX() {
        return this.boardLeft + this.boardWidth + COLUMN_GAP;
    }

    private void findHover(int mouseX, int mouseY) {
        boolean sealed = overPicker(mouseX, mouseY);

        this.overBack = !sealed
                && in(mouseX, mouseY, backX(), this.top, UiButtons.SIDE, UiButtons.SIDE);
        this.overClear = !sealed && current().searchable() && !this.search.isEmpty()
                && in(mouseX, mouseY, clearX(), fieldY() + 5, 14, 14);

        this.overCell = -1;
        for (int i = 0; i < this.suggestions.size(); i++) {
            if (in(mouseX, mouseY, cellX(i), cellY(i), CELL, CELL)) this.overCell = i;
        }

        this.overPlay = sealed || this.overCell >= 0 ? null : playAt(mouseX, mouseY);
    }

    private void drawPicker(GuiGraphicsExtractor graphics) {
        int x = fieldX();
        int y = fieldY();
        int width = fieldWidth();
        boolean pinned = !this.pinnedItem.isEmpty();

        Theme.roundedBox(graphics, x, y, width, FIELD_H, 5, Theme.PANEL_SUNKEN);
        Theme.roundedBoxOutline(graphics, x, y, width, FIELD_H, 5,
                pinned ? Theme.withAlpha(this.band.color(), 0xCC) : Theme.BORDER);

        if (pinned) {
            drawStack(graphics, this.pinned == null ? null : this.pinned.displayStack(),
                    x + 5, y + 4);
        } else {
            magnifier(graphics, x + 9, y + 8, Theme.TEXT_FAINT);
        }

        if (!this.search.isEmpty()) {
            cross(graphics, clearX(), fieldY() + 5,
                    this.overClear ? Theme.DANGER : Theme.TEXT_FAINT);
        }

        if (!pickerShowing()) return;

        int panelY = y + FIELD_H + 4;
        int panelW = pickerWidth();
        int panelH = pickerPanelHeight();

        Theme.fill(graphics, x + 3, panelY + panelH, x + panelW - 3, panelY + panelH + 3, 0x66000000);
        Theme.roundedBox(graphics, x, panelY, panelW, panelH, 5, Theme.withAlpha(Theme.PANEL, 0xFF));
        Theme.roundedBoxOutline(graphics, x, panelY, panelW, panelH, 5, Theme.BORDER_STRONG);

        if (this.suggestions.isEmpty()) {
            graphics.text(this.font, Component.translatable("vivet.leaderboards.no_items"),
                    x + 10, panelY + (EMPTY_H - 8) / 2, Theme.TEXT_FAINT);
            return;
        }

        for (int i = 0; i < this.suggestions.size(); i++) {
            int cx = cellX(i);
            int cy = cellY(i);
            boolean over = i == this.overCell;

            Theme.roundedBox(graphics, cx, cy, CELL, CELL, 4,
                    over ? Theme.mix(Theme.PANEL_SUNKEN, this.band.color(), 0.35F)
                            : Theme.PANEL_SUNKEN);
            if (over) {
                Theme.roundedBoxOutline(graphics, cx, cy, CELL, CELL, 4,
                        Theme.withAlpha(this.band.color(), 0xCC));
            }

            drawStack(graphics, this.suggestions.get(i).displayStack(),
                    cx + (CELL - 16) / 2, cy + (CELL - 16) / 2);
        }
    }

    private void drawStack(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) return;
        graphics.item(stack, x, y);
    }

    private void magnifier(GuiGraphicsExtractor graphics, int x, int y, int color) {
        Theme.fill(graphics, x + 1, y, x + 5, y + 1, color);
        Theme.fill(graphics, x + 1, y + 5, x + 5, y + 6, color);
        Theme.fill(graphics, x, y + 1, x + 1, y + 5, color);
        Theme.fill(graphics, x + 5, y + 1, x + 6, y + 5, color);
        Theme.fill(graphics, x + 5, y + 5, x + 8, y + 8, color);
    }

    private void cross(GuiGraphicsExtractor graphics, int x, int y, int color) {
        for (int i = 0; i < 8; i++) {
            Theme.fill(graphics, x + 3 + i, y + 3 + i, x + 4 + i, y + 4 + i, color);
            Theme.fill(graphics, x + 10 - i, y + 3 + i, x + 11 - i, y + 4 + i, color);
        }
    }

    private int scaled(double value) {
        return (int) Math.round(value / uiScale());
    }

    private static boolean in(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int x = scaled(event.x());
        int y = scaled(event.y());

        findHover(x, y);

        if (this.overBack) {
            onClose();
            return true;
        }

        if (this.overClear) {
            this.pinnedItem = "";
            clearSearch();
            reload();
            return true;
        }

        if (this.overCell >= 0 && this.overCell < this.suggestions.size()) {
            pick(this.suggestions.get(this.overCell));
            return true;
        }

        if (overPicker(x, y)) return true;

        if (this.overPlay != null) {
            watch(this.overPlay);
            return true;
        }

        RankedApi.Row row = rowAt(x, y);
        if (row != null && row.uuid != null && !row.uuid.isBlank()) {
            Cues.confirm();
            this.minecraft.gui.setScreen(new ProfileScreen(this, row.uuid));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private int rowsLeft() {
        return this.boardLeft + PADDING;
    }

    private int rowsWidth() {
        return this.boardWidth - PADDING * 2 - 6;
    }

    private RankedApi.Row rowAt(int mouseX, int mouseY) {
        int x = rowsLeft();
        if (mouseX < x || mouseX > x + rowsWidth()) return null;
        if (mouseY < this.listTop || mouseY >= this.listTop + this.listHeight) return null;

        int y = this.listTop - this.scroll;
        for (RankedApi.Row row : rows()) {
            int height = heightOf(row);
            if (mouseY >= y && mouseY < y + height) return row;
            y += height + CARD_GAP;
        }
        return null;
    }

    private boolean watchable(RankedApi.Row row) {
        return row.replay != null && "band".equals(current().id()) && this.pinnedItem.isEmpty();
    }

    private int[] playBox(RankedApi.Row row, int x, int y, int width, int height) {
        if (height == ROW_H) return new int[] { x + width - 4 - PLAY, y + (ROW_H - PLAY) / 2 };
        return new int[] { x + width - 6 - PLAY, y + height - 6 - PLAY };
    }

    private RankedApi.Row playAt(int mouseX, int mouseY) {
        if (mouseY < this.listTop || mouseY >= this.listTop + this.listHeight) return null;
        int x = rowsLeft();
        int width = rowsWidth();
        int y = this.listTop - this.scroll;
        for (RankedApi.Row row : rows()) {
            int height = heightOf(row);
            if (watchable(row)) {
                int[] box = playBox(row, x, y, width, height);
                if (in(mouseX, mouseY, box[0], box[1], PLAY, PLAY)) return row;
            }
            y += height + CARD_GAP;
        }
        return null;
    }

    private void drawPlay(GuiGraphicsExtractor graphics, RankedApi.Row row, int x, int y, int width,
                          int height) {
        int[] box = playBox(row, x, y, width, height);
        boolean live = !row.replay.isEmpty();
        boolean over = live && this.overPlay == row;

        Theme.roundedBox(graphics, box[0], box[1], PLAY, PLAY, 3,
                over ? Theme.withAlpha(current().color(), 0x66)
                        : Theme.withAlpha(0xFFFFFFFF, live ? 0x1A : 0x0A));

        int colour = !live ? Theme.withAlpha(Theme.TEXT_FAINT, 0x66) : over ? Theme.TEXT : Theme.TEXT_MUTED;
        int tx = box[0] + 6;
        int ty = box[1] + 4;
        for (int i = 0; i < 5; i++) {
            Theme.fill(graphics, tx + i, ty + i, tx + i + 1, ty + 9 - i, colour);
        }
    }

    private void watch(RankedApi.Row row) {
        if (row.replay == null) return;
        if (row.replay.isEmpty()) {
            Cues.nothingHappened();
            return;
        }
        if (this.fetchingReplay) return;
        this.fetchingReplay = true;
        Cues.confirm();

        java.util.function.Consumer<String> failed = problem -> this.minecraft.execute(() -> {
            this.fetchingReplay = false;
            Cues.deny();
        });
        ReplayApi.loadMatch(row.replay,
                match -> ReplayApi.open(match,
                        loaded -> this.minecraft.execute(() -> {
                            this.fetchingReplay = false;
                            if (this.minecraft.gui.screen() == this) {
                                this.minecraft.gui.setScreen(new ReplayViewScreen(this, loaded));
                            }
                        }),
                        failed),
                failed);
    }

    private void drawComingSoon(GuiGraphicsExtractor graphics) {
        Component soon = Component.translatable("vivet.leaderboards.soon");

        BigText.centered(graphics, this.font, soon,
                this.boardLeft + this.boardWidth / 2,
                this.listTop + this.listHeight / 2 - BigText.height(2) / 2,
                Theme.TEXT_MUTED, 2, 0);
    }

    private void drawList(GuiGraphicsExtractor graphics, Board board, int mouseX, int mouseY) {
        int x = rowsLeft();
        int width = rowsWidth();

        RankedApi.Board shown = this.board;
        if (shown == null && this.status.isEmpty()) {
            graphics.text(this.font, Component.translatable("vivet.leaderboards.loading"),
                    x, this.listTop + 6, Theme.TEXT_FAINT);
            return;
        }
        if (!this.status.isEmpty()) {
            graphics.textWithWordWrap(this.font, Component.literal(this.status),
                    x, this.listTop + 6, width, Theme.DANGER);
            return;
        }

        List<RankedApi.Row> rows = rows();
        if (rows.isEmpty()) {
            graphics.text(this.font, Component.translatable("vivet.leaderboards.empty"),
                    x, this.listTop + 6, Theme.TEXT_FAINT);
            return;
        }

        this.scroll = Math.max(0, Math.min(this.scroll, maxScroll(rows)));

        MojangAuth.Identity me = MojangAuth.identity();
        String myUuid = me == null ? null : me.uuid;

        Clip.on(graphics, x - 2, this.listTop, x + width + 8, this.listTop + this.listHeight);
        try {
            int y = this.listTop - this.scroll;

            for (RankedApi.Row row : rows) {
                int height = heightOf(row);

                if (y + height >= this.listTop && y < this.listTop + this.listHeight) {
                    boolean mine = myUuid != null && myUuid.equals(row.uuid);
                    boolean over = in(mouseX, mouseY, x, y, width, height)
                            && mouseY >= this.listTop
                            && mouseY < this.listTop + this.listHeight;

                    if (height == ROW_H) {
                        drawRow(graphics, row, board, x, y, width, mine, over);
                    } else {
                        drawCard(graphics, row, board, x, y, width, height, mine, over);
                    }
                }
                y += height + CARD_GAP;
            }
        } finally {
            Clip.off(graphics);
        }

        drawScrollbar(graphics, x + width + 4, rows);
    }

    private void drawCard(GuiGraphicsExtractor graphics, RankedApi.Row row, Board board,
                          int x, int y, int width, int height, boolean mine, boolean over) {
        int accent = placeColor(row.place);
        boolean first = row.place == 1;
        boolean second = row.place == 2;

        drawBackdrop(graphics, row.background, x, y, width, height);

        if (over) {
            Theme.roundedBox(graphics, x, y, width, height, CARD_R,
                    Theme.withAlpha(0xFFFFFFFF, 0x12));
        }
        Theme.roundedBoxOutline(graphics, x, y, width, height, CARD_R,
                Theme.withAlpha(accent, mine ? 0xFF : 0xAA));

        int emblem = first ? RankEmblem.size() : second ? EMBLEM_SECOND : EMBLEM;
        int emblemBox = Math.min(emblem, height - 4);
        boolean watch = watchable(row);
        int emblemX = x + width - 6 - (watch ? PLAY + 6 : 0) - emblemBox / 2;

        if (watch) drawPlay(graphics, row, x, y, width, height);

        drawEmblem(graphics, row, emblemX, y + height / 2, emblemBox,
                "rating".equals(board.id()));

        int rightEdge = emblemX - emblemBox / 2 - 6;

        String place = "#" + row.place;
        int placeScale = first ? 2 : 1;
        int placeWidth = BigText.width(this.font, Component.literal(place), placeScale);

        BigText.draw(graphics, this.font, Component.literal(place), x + 10,
                y + (height - BigText.height(placeScale)) / 2, Theme.TEXT, placeScale, 0);

        int head = first ? HEAD_TOP : second ? HEAD_SECOND : HEAD;
        int headX = x + 10 + placeWidth + 10;

        PlayerHead.draw(graphics, row.name, row.uuid, headX, y + (height - head) / 2, head);

        int textLeft = headX + head + 10;
        Component value = valueOf(row, board);

        if (first) {
            int cx = (textLeft + rightEdge) / 2;

            Component crown = crownOf(board, row);
            int crownScale = BigText.fit(this.font, crown, rightEdge - textLeft, 1);
            BigText.centered(graphics, this.font, crown, cx, y + 24, accent, crownScale, 0);

            int room = rightEdge - textLeft;
            int scale = nameWidth(row) * 2 <= room ? 2 : 1;
            int nameWidth = drawName(graphics, row, cx, y + (scale > 1 ? 38 : 42), scale, true);

            int rule = Math.min(nameWidth, room);
            Theme.fill(graphics, cx - rule / 2, y + 58, cx + rule / 2, y + 59,
                    Theme.withAlpha(accent, 0xCC));

            Component item = cardItem(row, board);
            if (item != null) {
                BigText.centered(graphics, this.font, item, cx, y + 64, Theme.TEXT_FAINT, 1, 0);
                BigText.centered(graphics, this.font, value, cx, y + 78, Theme.TEXT, 1, 0);
            } else {
                BigText.centered(graphics, this.font, value, cx, y + 64, Theme.TEXT, 1, 0);
            }
            return;
        }

        int valueWidth = this.font.width(value);
        graphics.text(this.font, value, rightEdge - valueWidth, y + (height - 8) / 2, Theme.TEXT);

        rule(graphics, rightEdge - valueWidth - 7, y + height / 2 - 5);
        int right = rightEdge - valueWidth - 14;
        int used = drawName(graphics, row, 0, y + (height - 8) / 2, 1, false, textLeft, right);

        Component item = cardItem(row, board);
        if (item != null) {
            int at = textLeft + used + 8;
            if (at + this.font.width(item) < right) {
                graphics.text(this.font, item, at, y + (height - 8) / 2, Theme.TEXT_FAINT);
            }
        }
    }

    private Component cardItem(RankedApi.Row row, Board board) {
        if (row.itemId == null || !"band".equals(board.id()) || !this.pinnedItem.isEmpty()) return null;
        return Component.literal(prettyId(row.itemId));
    }

    private void drawEmblem(GuiGraphicsExtractor graphics, RankedApi.Row row, int cx, int cy,
                            int box, boolean rankOnly) {
        Wardrobe.Item worn = rankOnly || row.emblem == null || row.emblem.isEmpty()
                ? null : Wardrobe.item(row.emblem);

        if (worn != null) {
            Cosmetics.ready(worn);
            Cosmetics.draw(graphics, this.font, worn, cx - box / 2, cy - box / 2, box, box,
                    System.currentTimeMillis(), true, false);
            return;
        }

        if (row.rank == null || row.rank.isBlank()) return;

        RankEmblem.drawAt(graphics, cx, cy, shownRank(row),
                row.placed == null || row.placed, Math.min(box, RankEmblem.bodySize()));
    }

    private void drawRow(GuiGraphicsExtractor graphics, RankedApi.Row row, Board board,
                         int x, int y, int width, boolean mine, boolean over) {
        Theme.roundedBox(graphics, x, y, width, ROW_H, 4,
                mine ? Theme.withAlpha(Theme.ACCENT, 0x22) : Theme.PANEL_SUNKEN);

        if (over) {
            Theme.roundedBox(graphics, x, y, width, ROW_H, 4, Theme.withAlpha(0xFFFFFFFF, 0x10));
        }

        if (mine) {
            Theme.roundedBox(graphics, x, y, width, ROW_H, 4, Theme.withAlpha(Theme.ACCENT, 0x1E));
        }

        String place = "#" + row.place;
        graphics.text(this.font, Component.literal(place), x + 9, y + 7, placeColor(row.place));

        int headX = x + 9 + this.font.width(place) + 8;
        PlayerHead.draw(graphics, row.name, row.uuid, headX, y + (ROW_H - HEAD) / 2, HEAD);

        int textLeft = headX + HEAD + 8;

        boolean watch = watchable(row);
        if (watch) drawPlay(graphics, row, x, y, width, ROW_H);
        int valueRight = watch ? x + width - 4 - PLAY - 6 : x + width - 8;

        Component value = valueOf(row, board);
        int valueWidth = this.font.width(value);
        graphics.text(this.font, value, valueRight - valueWidth, y + 7,
                mine ? Theme.ACCENT : Theme.TEXT_MUTED);

        int right = valueRight - valueWidth - 8;
        int used = drawName(graphics, row, 0, y + 7, 1, false, textLeft, right);

        if (row.itemId != null && board.times() && this.pinnedItem.isEmpty()) {
            Component item = Component.literal(prettyId(row.itemId));
            int at = textLeft + used + 8;
            if (at + this.font.width(item) < right) {
                graphics.text(this.font, item, at, y + 7, Theme.TEXT_FAINT);
            }
        }
    }

    private void rule(GuiGraphicsExtractor graphics, int x, int y) {
        Theme.fill(graphics, x, y, x + 1, y + 10, Theme.withAlpha(0xFFFFFFFF, 0x33));
    }

    private int drawName(GuiGraphicsExtractor graphics, RankedApi.Row row, int cx, int y,
                         int scale, boolean centred) {
        return drawName(graphics, row, cx, y, scale, centred, 0, 0);
    }

    private int nameWidth(RankedApi.Row row) {
        String name = row.name == null ? "?" : row.name;
        NameLabel label = labelOf(row, name);

        return NameLabel.tagWidthOf(this.font, label, name)
                + NameLabel.runWidthOf(this.font, name);
    }

    private static String shownRank(RankedApi.Row row) {
        if (row.speedTitle != null && !row.speedTitle.isBlank()) return row.speedTitle;
        return row.speedKing ? Ranks.SPEED_KING : row.rank;
    }

    private static NameLabel labelOf(RankedApi.Row row, String name) {
        RankTitle.remember(name, row.uuid, shownRank(row),
                row.rank != null && !row.rank.isBlank());

        return row.label == null || row.label.isBlank()
                ? NameLabel.of(name)
                : NameLabel.byId(row.label);
    }

    private int drawName(GuiGraphicsExtractor graphics, RankedApi.Row row, int cx, int y,
                         int scale, boolean centred, int left, int right) {
        String name = row.name == null ? "?" : row.name;
        NameLabel label = labelOf(row, name);
        int width = nameWidth(row);

        if (!centred) {
            return NameLabel.drawTaggedScrolling(graphics, this.font, label, name, left, y,
                    Math.max(20, right - left), Theme.TEXT_MUTED);
        }

        if (scale <= 1) {
            NameLabel.drawTaggedLeft(graphics, this.font, label, name, cx - width / 2, y,
                    Theme.TEXT);
            return width;
        }

        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(cx - (width * scale) / 2.0F, y);
        pose.scale(scale, scale);
        try {
            NameLabel.drawTaggedLeft(graphics, this.font, label, name, 0, 0, Theme.TEXT);
        } finally {
            pose.popMatrix();
        }
        return width * scale;
    }

    private void drawBackdrop(GuiGraphicsExtractor graphics, String cosmetic, int x, int y,
                              int width, int height) {
        String media = cosmetic == null || cosmetic.isBlank()
                ? null : Cosmetics.mediaOf(cosmetic);

        if (media == null) {
            Theme.roundedBox(graphics, x, y, width, height, CARD_R, Theme.PANEL_SUNKEN);
            return;
        }

        MediaImages.prepare(media);

        boolean drawn = com.vivet.speedrun.client.ui.RoundedPicture.draw(graphics, x, y, width,
                height, CARD_R,
                () -> MediaImages.cover(graphics, media, x, y, width, height, BACKDROP_TINT));

        if (!drawn) {
            Theme.roundedBox(graphics, x, y, width, height, CARD_R, Theme.PANEL_SUNKEN);
            return;
        }

        Theme.roundedBox(graphics, x, y, width, height, CARD_R, BACKDROP_WASH);
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics, int x, List<RankedApi.Row> rows) {
        int limit = maxScroll(rows);
        if (limit <= 0) return;

        int track = this.listHeight;
        Theme.fill(graphics, x, this.listTop, x + 2, this.listTop + track,
                Theme.withAlpha(0xFFFFFFFF, 0x14));

        int thumb = Math.max(14, track * track / Math.max(1, contentHeight(rows)));
        int offset = (track - thumb) * this.scroll / limit;

        Theme.fill(graphics, x, this.listTop + offset, x + 2, this.listTop + offset + thumb,
                Theme.withAlpha(current().color(), 0xAA));
    }

    private static int placeColor(int place) {
        return switch (place) {
            case 1 -> 0xFFE8C34A;
            case 2 -> 0xFFC8C8C8;
            case 3 -> 0xFFC06B3E;
            default -> Theme.TEXT_FAINT;
        };
    }

    private Component crownOf(Board board, RankedApi.Row top) {
        if (board.id().equals("rating")) {
            return Component.translatable(top.speedKing
                    ? "vivet.leaderboards.crown.rating"
                    : "vivet.leaderboards.crown.rating_plain");
        }
        if (board.id().equals("band")) {
            return Component.translatable(board.crown(), this.pinnedItem.isEmpty()
                    ? this.band.displayName() : prettyId(this.pinnedItem));
        }
        return Component.translatable(board.crown());
    }

    private Component valueOf(RankedApi.Row row, Board board) {
        if (row.centis != null) return Component.literal(Time.formatPrecise(row.centis));
        if (row.seconds != null) return Component.literal(Time.format(row.seconds));

        int value = row.rating != null ? row.rating : row.value == null ? 0 : row.value;
        if (board.id().equals("playtime")) return Component.literal(hours(value));

        return board.unit().isEmpty()
                ? Component.literal(String.valueOf(value))
                : Plural.of(value, board.unit());
    }

    private static String hours(int seconds) {
        if (seconds < 3600) return Math.max(0, seconds) / 60 + "m";
        return seconds / 3600 + "h " + (seconds % 3600) / 60 + "m";
    }

    private static String pathOf(String id) {
        return (id.contains(":") ? id.substring(id.indexOf(':') + 1) : id).toLowerCase(Locale.ROOT);
    }

    private static String prettyId(String id) {
        StringBuilder out = new StringBuilder();

        for (String word : pathOf(id).split("[_/]")) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
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
