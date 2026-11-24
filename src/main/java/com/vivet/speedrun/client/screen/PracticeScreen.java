package com.vivet.speedrun.client.screen;

import com.vivet.speedrun.client.Cues;
import com.vivet.speedrun.client.ui.Dropdown;
import com.vivet.speedrun.client.ui.FlatButton;
import com.vivet.speedrun.client.ui.PickerRow;
import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.client.ui.UiButtons;
import com.vivet.speedrun.game.Difficulty;
import com.vivet.speedrun.game.ItemTiers;
import com.vivet.speedrun.game.Practice;
import com.vivet.speedrun.game.Target;
import com.vivet.speedrun.game.Time;
import com.vivet.speedrun.p2p.CollectionBook;
import com.vivet.speedrun.p2p.RankedApi;
import com.vivet.speedrun.session.Session;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import org.joml.Matrix3x2fStack;

import java.util.function.Consumer;

public class PracticeScreen extends VivetScreen {
    private static final int MAX_WIDTH = 320;
    private static final int PADDING = 16;
    private static final int RAIL_GAP = 10;

    private static final int LINE = 14;
    private static final int FIELD_H = 18;
    private static final int PLAY_H = 22;
    private static final int OPTION_H = 20;

    private static Consumer<Minecraft> launcher = minecraft -> { };

    public static void bindStart(Consumer<Minecraft> start) {
        launcher = start;
    }

    public static void start(Minecraft minecraft) {
        if (Practice.isActive()) launcher.accept(minecraft);
    }

    public static Screen returnDestination() {
        return new PracticeScreen(new CollectionScreen(new VivetTitleScreen()));
    }

    private final Screen parent;

    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;

    private boolean overBack;

    private EditBox seedBox;

    private boolean worldListOpen;

    public PracticeScreen(Screen parent) {
        super(Component.translatable("vivet.screen.practice"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        this.panelWidth = Math.min(MAX_WIDTH, this.width - 16 - UiButtons.SIDE - RAIL_GAP);
        this.panelHeight = contentHeight();
        this.left = (this.width - this.panelWidth - UiButtons.SIDE - RAIL_GAP) / 2;
        this.top = (this.height - this.panelHeight) / 2;

        build();
    }

    private int contentHeight() {
        int lines = 1;
        if (bestCentis() >= 0) lines++;
        if (Practice.hasLastRun()) lines++;

        return PADDING + 34 + 10 + lines * LINE + 12 + FIELD_H
                + (typingSeed() ? FIELD_H + 6 : 0) + 14 + PLAY_H + PADDING;
    }

    private void build() {
        clearWidgets();
        this.seedBox = null;

        int inner = this.left + PADDING;
        int innerWidth = this.panelWidth - PADDING * 2;
        int y = fieldTop();

        addRenderableWidget(new Dropdown(inner, y, innerWidth, FIELD_H,
                Component.empty(),
                () -> Component.translatable(worldKey(Practice.world())),
                () -> Theme.TEXT,
                () -> this.worldListOpen,
                this::toggleWorldList));

        y += FIELD_H + 6;

        if (typingSeed()) {
            this.seedBox = new EditBox(this.font, inner + 6, Theme.textTop(y, FIELD_H), innerWidth - 12, 12,
                    Component.translatable("vivet.practice.seed"));
            this.seedBox.setBordered(false);
            this.seedBox.setMaxLength(64);
            this.seedBox.setTextColor(Theme.TEXT);
            this.seedBox.setHint(Component.translatable("vivet.practice.seed")
                    .copy().withStyle(ChatFormatting.ITALIC));
            this.seedBox.setValue(Practice.seed() == null ? "" : String.valueOf(Practice.seed()));
            this.seedBox.setResponder(text -> Practice.setSeed(Practice.parseSeed(text)));
            addRenderableWidget(this.seedBox);

            y += FIELD_H + 6;
        }

        y += 14;

        FlatButton play = addRenderableWidget(new FlatButton(inner, y, innerWidth, PLAY_H,
                Component.translatable(Practice.hasLastRun()
                        ? "vivet.button.retry" : "vivet.button.play"),
                this::start,
                FlatButton.Style.PRIMARY));

        play.active = Practice.isActive() && !this.worldListOpen && Session.canStartLocally();

        if (this.worldListOpen) buildWorldList(inner, innerWidth);
    }

    private void buildWorldList(int x, int width) {
        sealUnderList();

        int y = fieldTop() + FIELD_H + 2;

        for (Practice.World option : Practice.World.values()) {
            addRenderableWidget(new PickerRow(x, y, width, OPTION_H,
                    Component.translatable(worldKey(option)),

                    () -> null,
                    () -> Theme.TEXT_FAINT,
                    () -> false,
                    Theme.ACCENT,
                    option == Practice.world(),
                    () -> chooseWorld(option)));
            y += OPTION_H;
        }

        addRenderableWidget(new FlatButton(0, 0, this.width, this.height,
                Component.empty(), this::toggleWorldList, FlatButton.Style.INVISIBLE));
    }

    private int fieldTop() {
        int lines = 1;
        if (bestCentis() >= 0) lines++;
        if (Practice.hasLastRun()) lines++;

        return this.top + PADDING + 34 + 10 + lines * LINE + 12;
    }

    private static String worldKey(Practice.World option) {
        return switch (option) {
            case FRESH -> "vivet.practice.world_fresh";
            case SET -> "vivet.practice.world_set";
        };
    }

    private boolean typingSeed() {
        return Practice.world() == Practice.World.SET;
    }

    private void toggleWorldList() {
        this.worldListOpen = !this.worldListOpen;
        Cues.unfolded(this.worldListOpen);
        build();
    }

    private void chooseWorld(Practice.World option) {
        Practice.setWorld(option);
        this.worldListOpen = false;
        Cues.confirm();

        init();
    }

    private void start() {
        if (!Practice.isActive()) return;

        Cues.confirm();
        launcher.accept(this.minecraft);
    }

    @Override
    protected void drawScaled(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                              float partialTick) {
        this.overBack = onBack(mouseX, mouseY);

        Theme.fill(graphics, 0, 0, this.width, this.height, Theme.SCRIM);
        Theme.panel(graphics, this.left, this.top, this.panelWidth, this.panelHeight);

        drawItem(graphics);
        drawTimes(graphics);
        drawSeedFrame(graphics);

        UiButtons.button(graphics, UiButtons.Kind.BACK, backX(), this.top, this.overBack,
                true, false);

        drawWidgets(graphics, mouseX, mouseY, partialTick);
    }

    private void drawItem(GuiGraphicsExtractor graphics) {
        Target chosen = Practice.target();
        if (chosen == null) return;

        int x = this.left + PADDING;
        int y = this.top + PADDING;

        ItemStack stack = chosen.displayStack();
        if (!stack.isEmpty()) {
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(x, y);
            pose.scale(2.0F, 2.0F);
            graphics.item(stack, 0, 0);
            pose.popMatrix();
        }

        Difficulty band = ItemTiers.classify(chosen);
        int textX = x + 40;

        graphics.text(this.font, chosen.displayName().copy().withStyle(ChatFormatting.BOLD),
                textX, y + 3, Theme.TEXT);
        graphics.text(this.font, Component.literal(band.displayName()),
                textX, y + 17, band.color());

        Theme.divider(graphics, x, y + 34, this.panelWidth - PADDING * 2);
    }

    private void drawTimes(GuiGraphicsExtractor graphics) {
        int x = this.left + PADDING;
        int y = this.top + PADDING + 34 + 10;

        int best = bestCentis();
        if (best >= 0) {
            graphics.text(this.font, Component.translatable("vivet.practice.best",
                    Time.formatPrecise(best)), x, y, Theme.TEXT);
            y += LINE;
        }

        int real = realBestCentis();
        if (real > 0) {
            graphics.text(this.font, Component.translatable("vivet.practice.your_best",
                    Time.formatPrecise(real)), x, y, Theme.WARNING);
            y += LINE;
        }

        int last = Practice.lastCentis();
        if (Practice.hasLastRun()) {
            Component line = Practice.lastFinished()
                    ? Component.translatable(Practice.lastWasBest()
                            ? "vivet.practice.last_best" : "vivet.practice.last",
                            Time.formatPrecise(last))
                    : Component.translatable("vivet.practice.last_gave_up");

            graphics.text(this.font, line, x, y, Practice.lastWasBest()
                    ? Theme.SUCCESS : Theme.TEXT_MUTED);
        }
    }

    private void drawSeedFrame(GuiGraphicsExtractor graphics) {
        if (this.seedBox == null) return;

        Theme.panel(graphics, this.seedBox.getX() - 6, this.seedBox.getY() - Theme.textTop(0, FIELD_H),
                this.seedBox.getWidth() + 12, FIELD_H, Theme.PANEL_SUNKEN);
    }

    private int bestCentis() {
        return Practice.bestCentis();
    }

    private int realBestCentis() {
        Target chosen = Practice.target();
        if (chosen == null) return 0;

        RankedApi.Found found = CollectionBook.get(ItemTiers.ratingKey(chosen));
        return found == null || found.best == null ? 0 : found.best;
    }

    private int backX() {
        return this.left + this.panelWidth + RAIL_GAP;
    }

    private boolean onBack(int mouseX, int mouseY) {
        return mouseX >= backX() && mouseX < backX() + UiButtons.SIDE
                && mouseY >= this.top && mouseY < this.top + UiButtons.SIDE;
    }

    private int scaled(double value) {
        return (int) (value / uiScale());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!this.worldListOpen && onBack(scaled(event.x()), scaled(event.y()))) {
            Cues.confirm();
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE && this.worldListOpen) {
            toggleWorldList();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Practice.clear();
        this.minecraft.gui.setScreen(this.parent);
    }
}
