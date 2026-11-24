package com.vivet.speedrun.client.screen;

import com.vivet.speedrun.client.Cues;
import com.vivet.speedrun.client.EasterEgg;
import com.vivet.speedrun.client.Network;
import com.vivet.speedrun.client.ui.Clip;
import com.vivet.speedrun.client.ui.RailRow;
import com.vivet.speedrun.client.ui.SettingAction;
import com.vivet.speedrun.client.ui.SettingChip;
import com.vivet.speedrun.client.ui.SettingChoice;
import com.vivet.speedrun.client.ui.SettingField;
import com.vivet.speedrun.client.ui.SettingHeading;
import com.vivet.speedrun.client.ui.SettingSwitch;
import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.client.ui.UiButtons;
import com.vivet.speedrun.client.ui.VanillaIcon;
import com.vivet.speedrun.config.ConfigManager;
import com.vivet.speedrun.config.ModConfig;
import com.vivet.speedrun.game.Difficulty;
import com.vivet.speedrun.game.DimensionPool;
import com.vivet.speedrun.game.ItemTiers;
import com.vivet.speedrun.game.Modifiers;
import com.vivet.speedrun.game.WorldRule;
import com.vivet.speedrun.session.Session;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class SettingsScreen extends VivetScreen {
    private static final int MAX_WIDTH = 344;
    private static final int MIN_WIDTH = 200;

    private static final int MARGIN_X = 6;
    private static final int MARGIN_Y = 8;

    private static final int RAIL_GAP = 8;
    private static final int PADDING = 10;

    private static final int RAIL_W = 96;

    private static final int RAIL_MIN = 64;

    private static final int RAIL_H = 26;
    private static final int RAIL_STEP = 4;

    private static final int RAIL_INDENT = 12;

    private static final int PANEL_R = 8;

    private static final int HEAD_H = 26;

    private static final int WARN_H = 22;

    private static final int ROW_H = 24;
    private static final int ROW_GAP = 5;

    private static final int BAR_ROOM = 10;
    private static final int BAR_W = 4;
    private static final int BAR_GRAB = 7;

    private static final int WANTED_ROWS = 9;

    private static final int PICK_H = 15;

    private enum Page {
        GENERAL("vivet.settings.page.general", VanillaIcon.Kind.COMPARATOR, Theme.ACCENT, 0),
        PRIVATE("vivet.settings.page.private", VanillaIcon.Kind.CLOCK, Theme.SUCCESS, 0),
        MODIFIERS("vivet.settings.page.modifiers", VanillaIcon.Kind.BREWING_STAND, Theme.WARNING, 1),
        EASY("vivet.settings.page.easy", VanillaIcon.Kind.FEATHER, Theme.SUCCESS, 2),
        HARD("vivet.settings.page.hard", VanillaIcon.Kind.HARDCORE_HEART, Theme.DANGER, 2);

        private final String key;
        private final VanillaIcon.Kind icon;
        private final int accent;

        private final int depth;

        Page(String key, VanillaIcon.Kind icon, int accent, int depth) {
            this.key = key;
            this.icon = icon;
            this.accent = accent;
            this.depth = depth;
        }

        WorldRule.Category category() {
            return switch (this) {
                case EASY -> WorldRule.Category.EASY;
                case HARD -> WorldRule.Category.HARD;
                default -> null;
            };
        }

        boolean isModifiers() {
            return this == MODIFIERS || this == EASY || this == HARD;
        }

        String headingKey() {
            return this.key;
        }

        boolean isPrivateOnly() {
            return this == PRIVATE || isModifiers();
        }
    }

    private record Picker(SettingChip.Anchor anchor, List<Component> options, int current,
                          IntConsumer onPick) {
    }

    private final Screen parent;

    private Page page = Page.GENERAL;

    private Picker picker;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int railW;

    private int listLeft;
    private int listTop;
    private int listWidth;
    private int listHeight;
    private int barX;

    private int scroll;
    private int contentHeight;
    private boolean draggingBar;
    private boolean overBack;

    private final List<AbstractWidget> rows = new ArrayList<>();

    public SettingsScreen(Screen parent) {
        super(Component.translatable("vivet.screen.settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int gutter = UiButtons.SIDE + RAIL_GAP;

        this.railW = Math.max(RAIL_MIN, Math.min(RAIL_W,
                this.width - MARGIN_X * 2 - gutter - RAIL_GAP - MIN_WIDTH));

        this.panelW = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH,
                this.width - MARGIN_X * 2 - gutter - RAIL_GAP - this.railW));

        int furniture = PADDING * 2 + headHeight();
        int wanted = furniture + WANTED_ROWS * (ROW_H + ROW_GAP) - ROW_GAP;

        int floor = Math.max(furniture + ROW_H, railHeight() + PADDING * 2);

        this.panelH = Math.min(this.height - MARGIN_Y * 2, Math.max(floor, wanted));

        int cluster = this.railW + RAIL_GAP + this.panelW + gutter;
        this.panelX = Math.max(MARGIN_X, (this.width - cluster) / 2) + this.railW + RAIL_GAP;
        this.panelY = Math.max(MARGIN_Y, (this.height - this.panelH) / 2);

        int room = PADDING + BAR_ROOM;
        this.listLeft = this.panelX + room;
        this.listWidth = this.panelW - room * 2;
        this.listTop = this.panelY + PADDING + headHeight();
        this.listHeight = this.panelH - PADDING * 2 - headHeight();
        this.barX = this.panelX + this.panelW - PADDING - BAR_W;

        buildRail();
        buildRows();

        this.scroll = Math.max(0, Math.min(maxScroll(), this.scroll));
    }

    private List<Page> railPages() {
        List<Page> shown = new ArrayList<>();
        for (Page value : Page.values()) {
            boolean show = switch (value.depth) {
                case 0 -> true;
                case 1 -> this.page.isPrivateOnly();
                default -> this.page.isModifiers();
            };
            if (show) shown.add(value);
        }
        return shown;
    }

    private int railHeight() {
        int count = railPages().size();
        return RAIL_H * count + RAIL_STEP * (count - 1);
    }

    private int railX(Page value) {
        return this.panelX - RAIL_GAP - this.railW + value.depth * RAIL_INDENT;
    }

    private int railY(int index) {
        return this.panelY + index * (RAIL_H + RAIL_STEP);
    }

    private int backX() {
        return this.panelX + this.panelW + RAIL_GAP;
    }

    private void buildRail() {
        List<Page> shown = railPages();

        for (int i = 0; i < shown.size(); i++) {
            Page value = shown.get(i);

            addRenderableWidget(new RailRow(railX(value), railY(i),
                    this.railW - value.depth * RAIL_INDENT, RAIL_H,
                    Component.translatable(value.key), null, value.icon, value.accent,
                    () -> this.page == value,
                    () -> select(value)));
        }
    }

    private void select(Page value) {
        if (this.page == value) return;

        this.page = value;
        this.scroll = 0;
        this.picker = null;
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private void buildRows() {
        this.rows.clear();

        Cursor cursor = new Cursor(this.listTop - this.scroll);
        if (this.page == Page.GENERAL) {
            buildGeneral(cursor);
        } else if (this.page == Page.PRIVATE) {
            buildGame(cursor);
        } else {
            buildModifiers(cursor);
        }
        this.contentHeight = cursor.height();

        this.rows.forEach(this::addWidget);
    }

    private void buildGeneral(Cursor cursor) {
        ModConfig config = ConfigManager.get();

        this.rows.add(new SettingField(this.listLeft, cursor.take(ROW_H), this.listWidth, ROW_H,
                Component.translatable("vivet.option.custom_audio_volume"),
                () -> config.customAudioVolume,
                value -> config.customAudioVolume = value,
                0, 100, "%"));

        this.rows.add(new SettingSwitch(this.listLeft, cursor.take(ROW_H), this.listWidth, ROW_H,
                Component.translatable("vivet.option.pity"),
                () -> config.pitySystem,
                value -> config.pitySystem = value));

        this.rows.add(new SettingSwitch(this.listLeft, cursor.take(ROW_H), this.listWidth, ROW_H,
                Component.translatable("vivet.option.discord_status"),
                () -> config.discordStatus,
                value -> {
                    config.discordStatus = value;
                    com.vivet.speedrun.client.discord.RichPresence.settingChanged();
                }));

        if (com.vivet.speedrun.update.Channel.hasUpdateSwitch()) {
            this.rows.add(new SettingSwitch(this.listLeft, cursor.take(ROW_H), this.listWidth, ROW_H,
                    Component.translatable("vivet.option.auto_updates"),
                    () -> config.autoUpdates,
                    value -> config.autoUpdates = value));
        }

        this.rows.add(new SettingAction(this.listLeft, cursor.take(ROW_H), this.listWidth, ROW_H,
                Component.translatable("vivet.button.does_nothing"),
                EasterEgg::press));
    }

    private void buildGame(Cursor cursor) {
        ModConfig config = ConfigManager.get();

        List<Component> bands = new ArrayList<>();
        for (Difficulty band : Difficulty.values()) {
            bands.add(Component.literal(band.displayName()));
        }

        this.rows.add(choice(cursor,
                Component.translatable("vivet.option.difficulty"),
                () -> Component.literal(config.difficulty.displayName()),
                () -> config.difficulty.color(),
                bands,
                () -> config.difficulty.ordinal(),
                index -> {
                    config.difficulty = Difficulty.values()[index];

                    moveOffEmptyBand(config);
                }));

        this.rows.add(new SettingSwitch(this.listLeft, cursor.take(ROW_H), this.listWidth, ROW_H,
                Component.translatable("vivet.option.include_easier"),
                () -> config.includeEasierItems,
                value -> {
                    config.includeEasierItems = value;
                    moveOffEmptyBand(config);
                    rebuild();
                }));

        this.rows.add(new SettingSwitch(this.listLeft, cursor.take(ROW_H), this.listWidth, ROW_H,
                Component.translatable("vivet.option.nether_only"),
                () -> config.dimensionPool.isNetherOnly(),
                value -> {
                    config.dimensionPool = value ? DimensionPool.NETHER : DimensionPool.ANY;
                    moveOffEmptyBand(config);
                    rebuild();
                }));

        net.minecraft.world.Difficulty[] worlds = net.minecraft.world.Difficulty.values();
        List<Component> worldNames = new ArrayList<>();
        for (net.minecraft.world.Difficulty value : worlds) {
            worldNames.add(value.getDisplayName());
        }

        this.rows.add(choice(cursor,
                Component.translatable("vivet.option.world_difficulty"),
                () -> config.privateWorldDifficulty().getDisplayName(),
                () -> Theme.ACCENT,
                worldNames,
                () -> config.privateWorldDifficulty().ordinal(),
                index -> config.setPrivateWorldDifficulty(worlds[index])));

        this.rows.add(new SettingSwitch(this.listLeft, cursor.take(ROW_H), this.listWidth, ROW_H,
                Component.translatable("vivet.option.allow_rerolls"),
                () -> config.allowRerolls,
                value -> config.allowRerolls = value));

        ModConfig.AfterRun[] afters = ModConfig.AfterRun.values();
        List<Component> afterNames = new ArrayList<>();
        for (ModConfig.AfterRun value : afters) {
            afterNames.add(Component.literal(value.title()));
        }

        this.rows.add(choice(cursor,
                Component.translatable("vivet.option.after_run"),
                () -> Component.literal(config.afterRun.title()),
                () -> Theme.ACCENT,
                afterNames,
                () -> config.afterRun.ordinal(),
                index -> config.afterRun = afters[index]));

        this.rows.add(new SettingSwitch(this.listLeft, cursor.take(ROW_H), this.listWidth, ROW_H,
                Component.translatable("vivet.option.play_to_last"),
                () -> config.playToLastFinisher,
                value -> config.playToLastFinisher = value));
    }

    private void buildModifiers(Cursor cursor) {
        WorldRule.Category only = this.page.category();

        for (WorldRule.Category category : WorldRule.Category.values()) {
            if (only != null && category != only) continue;

            if (only == null) {
                this.rows.add(new SettingHeading(this.listLeft, cursor.take(SettingHeading.H),
                        this.listWidth, Component.literal(category.title()), accentOf(category)));
            }
            buildCategory(cursor, category);
        }
    }

    private static int accentOf(WorldRule.Category category) {
        return category == WorldRule.Category.EASY ? Theme.SUCCESS : Theme.DANGER;
    }

    private void buildCategory(Cursor cursor, WorldRule.Category category) {
        ModConfig config = ConfigManager.get();

        boolean editable = Session.canEditModifiers();

        for (WorldRule rule : WorldRule.values()) {
            if (rule.category() != category) continue;

            SettingSwitch row = new SettingSwitch(this.listLeft, cursor.take(ROW_H),
                    this.listWidth, ROW_H, Component.literal(rule.title()),
                    () -> editable ? config.ownRule(rule)
                            : Modifiers.applyNow() && config.isRuleEnabled(rule),
                    value -> {
                        config.setRuleEnabled(rule, value);
                        Network.applyModifiers();
                    },
                    rule.maxLevel(),
                    () -> editable ? config.ownLevel(rule) : config.levelOf(rule),
                    () -> openLevels(rule));

            row.active = editable;
            this.rows.add(row);
        }

        SettingAction reset = new SettingAction(this.listLeft, cursor.take(ROW_H), this.listWidth,
                ROW_H, Component.translatable("vivet.button.reset_modifiers"),
                () -> {
                    ConfigManager.get().resetWorldRules(category);
                    Network.applyModifiers();
                    rebuild();
                });
        reset.active = editable;
        this.rows.add(reset);
    }

    private void moveOffEmptyBand(ModConfig config) {
        for (int tries = 0; tries < Difficulty.values().length; tries++) {
            if (ItemTiers.countFor(config.difficulty, config.includeEasierItems,
                    config.dimensionPool) > 0) {
                return;
            }
            config.difficulty = config.difficulty.next();
        }
    }

    private SettingChoice choice(Cursor cursor, Component title, Supplier<Component> value,
                                 Supplier<Integer> accent, List<Component> options,
                                 IntSupplier current, IntConsumer onPick) {
        SettingChoice[] holder = new SettingChoice[1];

        holder[0] = new SettingChoice(this.listLeft, cursor.take(ROW_H), this.listWidth, ROW_H,
                title, value, accent, options,
                () -> open(holder[0], options, current.getAsInt(), onPick));

        return holder[0];
    }

    private void open(SettingChip.Anchor anchor, List<Component> options, int current,
                      IntConsumer onPick) {
        this.picker = new Picker(anchor, options, current, onPick);
    }

    private void openLevels(WorldRule rule) {
        SettingChip.Anchor anchor = rowFor(rule);
        if (anchor == null) return;

        List<Component> levels = new ArrayList<>();
        for (int level = 1; level <= rule.maxLevel(); level++) {
            levels.add(Component.literal(SettingSwitch.roman(level)));
        }

        open(anchor, levels, ConfigManager.get().ownLevel(rule) - 1,
                index -> {
                    ConfigManager.get().setLevel(rule, index + 1);
                    Network.applyModifiers();
                });
    }

    private SettingChip.Anchor rowFor(WorldRule rule) {
        for (AbstractWidget widget : this.rows) {
            if (widget instanceof SettingSwitch row
                    && row.getMessage().getString().equals(rule.title())) {
                return row;
            }
        }
        return null;
    }

    private int[] pickerBox() {
        SettingChip.Anchor anchor = this.picker.anchor();

        int width = Math.max(anchor.chipWidth(), widestPick() + 16);
        int height = this.picker.options().size() * PICK_H + 2;

        int x = Math.max(this.panelX + 2,
                Math.min(anchor.chipX() + anchor.chipWidth() - width,
                        this.panelX + this.panelW - 2 - width));

        int below = anchor.chipY() + SettingChip.H + 2;
        int y = below + height <= this.panelY + this.panelH - 2
                ? below
                : anchor.chipY() - 2 - height;

        return new int[]{x, Math.max(this.panelY + 2, y), width, height};
    }

    private int widestPick() {
        int widest = 0;
        for (Component option : this.picker.options()) {
            widest = Math.max(widest, this.font.width(option));
        }
        return widest;
    }

    @Override
    protected void drawScaled(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                              float partialTick) {
        Theme.fill(graphics, 0, 0, this.width, this.height, Theme.SCRIM);

        Theme.roundedBox(graphics, this.panelX, this.panelY, this.panelW, this.panelH, PANEL_R,
                Theme.PANEL);
        Theme.roundedBoxOutline(graphics, this.panelX, this.panelY, this.panelW, this.panelH,
                PANEL_R, Theme.BORDER);

        drawHead(graphics);

        drawWidgets(graphics, mouseX, mouseY, partialTick);

        UiButtons.button(graphics, UiButtons.Kind.BACK, backX(), this.panelY, this.overBack,
                true, false);

        Clip.on(graphics, this.panelX + 1, this.listTop,
                this.panelX + this.panelW - 1, this.listTop + this.listHeight);
        for (AbstractWidget row : this.rows) {
            row.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }
        Clip.off(graphics);

        drawBar(graphics, mouseX, mouseY);
        drawPicker(graphics, mouseX, mouseY);

        EasterEgg.render(graphics);
    }

    private int headHeight() {
        return HEAD_H + (this.page.isPrivateOnly() ? WARN_H : 0);
    }

    private void drawHead(GuiGraphicsExtractor graphics) {
        int y = this.panelY + PADDING;

        graphics.text(this.font, Component.translatable(this.page.headingKey()),
                this.panelX + PADDING + BAR_ROOM, y, Theme.TEXT);

        String count = summary();
        graphics.text(this.font, Component.literal(count),
                this.panelX + this.panelW - PADDING - BAR_ROOM - this.font.width(count),
                y, Theme.TEXT_FAINT);

        Theme.divider(graphics, this.panelX + PADDING, y + 14, this.panelW - PADDING * 2);

        if (!this.page.isPrivateOnly()) return;

        int warnX = this.panelX + PADDING + BAR_ROOM;
        graphics.text(this.font, Component.translatable("vivet.settings.private_only"),
                warnX, y + 22, Theme.DANGER);
        graphics.text(this.font, Component.translatable("vivet.settings.private_no_progress"),
                warnX, y + 32, Theme.DANGER);
    }

    private String summary() {
        ModConfig config = ConfigManager.get();

        if (this.page == Page.GENERAL) return "";

        if (!this.page.isModifiers()) {
            return ItemTiers.countFor(config.difficulty, config.includeEasierItems,
                    config.dimensionPool) + " items";
        }

        WorldRule.Category only = this.page.category();
        boolean editable = Session.canEditModifiers();
        int on = 0;
        for (WorldRule rule : WorldRule.values()) {
            if (only != null && rule.category() != only) continue;
            boolean shown = editable ? config.ownRule(rule) : Modifiers.enabled(rule);
            if (shown) on++;
        }
        return on + " on";
    }

    private void drawPicker(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.picker == null) return;

        int[] box = pickerBox();

        Theme.roundedBox(graphics, box[0], box[1], box[2], box[3], 4,
                Theme.withAlpha(Theme.PANEL_RAISED, 0xFF));
        Theme.roundedBoxOutline(graphics, box[0], box[1], box[2], box[3], 4,
                Theme.withAlpha(Theme.ACCENT, 0xAA));

        for (int i = 0; i < this.picker.options().size(); i++) {
            int y = box[1] + 1 + i * PICK_H;
            boolean over = mouseX >= box[0] && mouseX < box[0] + box[2]
                    && mouseY >= y && mouseY < y + PICK_H;

            if (over) {
                Theme.fill(graphics, box[0] + 1, y, box[0] + box[2] - 1, y + PICK_H,
                        Theme.withAlpha(Theme.ACCENT, 0x33));
            }

            Component option = this.picker.options().get(i);
            graphics.text(this.font, option,
                    box[0] + (box[2] - this.font.width(option)) / 2, y + 4,
                    i == this.picker.current() ? Theme.ACCENT : Theme.TEXT_MUTED);
        }
    }

    private int maxScroll() {
        return Math.max(0, this.contentHeight - this.listHeight);
    }

    private void drawBar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (maxScroll() <= 0) return;

        boolean lit = this.draggingBar || overBar(mouseX, mouseY);

        Theme.roundedBox(graphics, this.barX, this.listTop, BAR_W, this.listHeight, BAR_W / 2,
                Theme.withAlpha(0xFFFFFFFF, 0x12));
        Theme.roundedBox(graphics, this.barX, this.listTop + thumbOffset(), BAR_W, thumbHeight(),
                BAR_W / 2, Theme.withAlpha(0xFFFFFFFF, lit ? 0xBB : 0x55));
    }

    private int thumbHeight() {
        return Math.max(18, this.listHeight * this.listHeight / Math.max(1, this.contentHeight));
    }

    private int thumbOffset() {
        int travel = this.listHeight - thumbHeight();
        return maxScroll() <= 0 ? 0 : travel * this.scroll / maxScroll();
    }

    private boolean overBar(int mouseX, int mouseY) {
        return mouseX >= this.barX - BAR_GRAB && mouseX <= this.barX + BAR_W + BAR_GRAB
                && mouseY >= this.listTop && mouseY < this.listTop + this.listHeight;
    }

    private void dragTo(int mouseY) {
        int travel = Math.max(1, this.listHeight - thumbHeight());
        int want = Math.max(0, Math.min(travel, mouseY - this.listTop - thumbHeight() / 2));

        this.scroll = Math.max(0, Math.min(maxScroll(),
                Math.round(maxScroll() * (float) want / travel)));
        rebuild();
    }

    private int scaled(double value) {
        return (int) Math.round(value / uiScale());
    }

    private static boolean in(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        this.overBack = in(scaled(mouseX), scaled(mouseY), backX(), this.panelY,
                UiButtons.SIDE, UiButtons.SIDE);

        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int x = scaled(event.x());
        int y = scaled(event.y());

        if (this.picker != null) {
            int[] box = pickerBox();
            if (in(x, y, box[0], box[1], box[2], box[3])) {
                int index = Math.min(this.picker.options().size() - 1,
                        Math.max(0, (y - box[1] - 1) / PICK_H));

                this.picker.onPick().accept(index);
                Cues.confirm();
            }
            this.picker = null;
            rebuild();
            return true;
        }

        if (in(x, y, backX(), this.panelY, UiButtons.SIDE, UiButtons.SIDE)) {
            onClose();
            return true;
        }

        if (maxScroll() > 0 && overBar(x, y)) {
            this.draggingBar = true;
            dragTo(y);
            return true;
        }

        if (in(x, y, this.panelX, this.panelY, this.panelW, this.panelH)
                && !in(x, y, this.panelX, this.listTop, this.panelW, this.listHeight)) {
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.draggingBar = false;
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
        if (this.picker != null) {
            this.picker = null;
            return true;
        }

        if (maxScroll() > 0) {
            int updated = Math.max(0, Math.min(maxScroll(),
                    this.scroll - (int) Math.signum(deltaY) * 18));

            if (updated != this.scroll) {
                this.scroll = updated;
                rebuild();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public void onClose() {
        ConfigManager.save();
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public void tick() {
        super.tick();
        EasterEgg.tickSettings();
    }

    @Override
    public void removed() {
        super.removed();
        EasterEgg.leftSettings();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class Cursor {
        private final int start;
        private int y;

        Cursor(int start) {
            this.start = start;
            this.y = start;
        }

        int take(int height) {
            int value = this.y;
            this.y += height + ROW_GAP;
            return value;
        }

        int height() {
            return Math.max(0, this.y - this.start - ROW_GAP);
        }
    }
}
