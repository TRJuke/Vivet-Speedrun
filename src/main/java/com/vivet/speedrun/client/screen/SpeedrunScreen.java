package com.vivet.speedrun.client.screen;

import com.vivet.speedrun.client.ClientState;
import com.vivet.speedrun.client.Network;
import com.vivet.speedrun.client.hud.ItemRoll;
import com.vivet.speedrun.client.ui.Bar;
import com.vivet.speedrun.client.ui.Clip;
import com.vivet.speedrun.client.ui.FlatButton;
import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.client.ui.UiButtons;
import com.vivet.speedrun.config.ConfigManager;
import com.vivet.speedrun.config.ModConfig;
import com.vivet.speedrun.game.Difficulty;
import com.vivet.speedrun.game.ItemTiers;
import com.vivet.speedrun.game.Practice;
import com.vivet.speedrun.game.RunMode;
import com.vivet.speedrun.game.RunRecord;
import com.vivet.speedrun.game.Target;
import com.vivet.speedrun.game.Time;
import com.vivet.speedrun.session.Racer;
import com.vivet.speedrun.session.Session;
import com.vivet.speedrun.session.SessionMessage;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class SpeedrunScreen extends VivetScreen {
    private static final int WIDTH = 440;

    private static final int HEIGHT = 292;
    private static final int MARGIN = 16;

    private static final int COMPACT_HEIGHT = 80;

    private static final int BACK_GAP = 6;

    private static final int COMPACT_MIN_WIDTH = 168;

    private static final int PRACTICE_WIDTH = 232;

    private static final int PRACTICE_ITEM_SCALE = 4;

    private static final int GIVE_UP_W = 68;
    private static final int REROLL_W = 68;
    private static final int DRAW_W = 62;
    private static final int ACTION_GAP = 8;
    private static final int LEFT_CARD = 180;
    private static final int GUTTER = 12;
    private static final int ROW_HEIGHT = 26;

    private static final int CARD_TAB_HEIGHT = 16;
    private static final int CARD_TAB_GAP = 20;

    private static boolean showingOpponent;

    private int left;
    private int top;
    private int scrollOffset;

    private FlatButton startButton;
    private FlatButton rerollButton;
    private FlatButton giveUpButton;

    private Plan plan;
    private int panelWidth;
    private int panelHeight;

    private boolean overBack;

    public SpeedrunScreen() {
        super(Component.translatable("vivet.screen.title"));
    }

    private record Plan(boolean body, boolean settings, boolean findMatch, boolean startStop,
                        boolean reroll, boolean draw, boolean card) { }

    private static Plan planFor(RunMode mode) {
        return switch (mode) {
            case SOLO        -> new Plan(false, true,  false, false, false, false, true);
            case PRIVATE     -> new Plan(true,  true,  false, true,  true,  true,  false);
            case PUBLIC      -> new Plan(false, false, false, false, true,  true,  false);
            case RANKED_MATCH-> new Plan(false, false, false, false, false, true,  false);
            case RANKED_SOLO -> new Plan(false, false, false, false, false, false, false);
        };
    }

    @Override
    protected void init() {
        super.init();
        this.plan = planFor(RunMode.current());

        if (this.plan.card()) {
            this.panelWidth = PRACTICE_WIDTH;
            this.panelHeight = practiceHeight();
        } else {
            this.panelWidth = this.plan.body() ? WIDTH : compactWidth();
            this.panelHeight = this.plan.body() ? HEIGHT : COMPACT_HEIGHT;
        }

        this.left = (this.width - this.panelWidth) / 2;
        this.top = (this.height - this.panelHeight) / 2;

        if (this.plan.body()) {
            buildHeaderButtons();
            buildCardTabs();
        } else if (this.plan.card()) {
            buildHeaderButtons();
        }
        buildActionBar();
    }

    private int compactWidth() {
        int title = this.font.width(RunMode.current().displayName()) + MARGIN * 2;
        return Math.max(Math.max(compactActionsWidth() + MARGIN * 2, title), COMPACT_MIN_WIDTH);
    }

    private int compactActionsWidth() {
        int content = GIVE_UP_W;
        if (this.plan.reroll()) content += REROLL_W + ACTION_GAP;
        if (this.plan.draw() && Session.isMultiplayer()) content += DRAW_W + ACTION_GAP;
        return content;
    }

    private int backX() {
        return this.left + this.panelWidth + BACK_GAP;
    }

    private boolean hasOpponentCard() {
        return !opponentName().isEmpty();
    }

    private String opponentName() {
        for (Racer racer : Session.racers()) {
            if (!Session.isLocalName(racer.name)) return racer.name;
        }
        return "";
    }

    private void buildCardTabs() {
        if (!hasOpponentCard()) {
            showingOpponent = false;
            return;
        }

        int width = rightColumnWidth();
        int half = (width - 4) / 2;
        int x = rightColumnX();
        int y = bodyTop();

        addRenderableWidget(new FlatButton(x, y, half, CARD_TAB_HEIGHT,
                Component.translatable("vivet.screen.tab_standings"),
                () -> selectCard(false),
                showingOpponent ? FlatButton.Style.GHOST : FlatButton.Style.PRIMARY));

        addRenderableWidget(new FlatButton(x + half + 4, y, half, CARD_TAB_HEIGHT,
                Component.translatable("vivet.screen.tab_opponent"),
                () -> selectCard(true),
                showingOpponent ? FlatButton.Style.PRIMARY : FlatButton.Style.GHOST));
    }

    private void selectCard(boolean opponent) {
        showingOpponent = opponent;
        if (opponent) OpponentCard.request(opponentName());
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private void buildHeaderButtons() {
        Bar bar = new Bar(this.left + MARGIN, this.panelWidth - MARGIN * 2, 6);
        int y = this.top + 12;

        if (this.plan.settings()) {
            int settingsWidth = this.font.width(label("vivet.button.settings")) + 18;
            addRenderableWidget(new FlatButton(bar.right(settingsWidth), y, settingsWidth, 18,
                    Component.translatable("vivet.button.settings"),
                    () -> this.minecraft.gui.setScreen(new SettingsScreen(this)),
                    FlatButton.Style.GHOST));
        }

        if (this.plan.findMatch()) {
            int friendsWidth = this.font.width(label("vivet.button.friends")) + 18;
            addRenderableWidget(new FlatButton(bar.right(friendsWidth), y, friendsWidth, 18,
                    Component.translatable("vivet.button.friends"),
                    () -> this.minecraft.gui.setScreen(new FindMatchScreen(this)),
                    FlatButton.Style.GHOST));
        }
    }

    private void buildActionBar() {
        if (!this.plan.body()) {
            buildCompactActions();
            return;
        }

        Bar bar = new Bar(this.left + MARGIN, this.panelWidth - MARGIN * 2, 8);
        int y = this.top + this.panelHeight - 34;
        boolean running = ClientState.isRunning();
        this.startButton = null;
        this.rerollButton = null;

        if (this.plan.startStop() && Session.canStartLocally()) {
            this.startButton = addRenderableWidget(new FlatButton(
                    bar.left(96), y, 96, 20,
                    Component.translatable(running ? "vivet.button.stop" : "vivet.button.start"),
                    Network::toggleRun,
                    FlatButton.Style.PRIMARY));
        }

        if (this.plan.reroll()) {
            this.rerollButton = addRenderableWidget(new FlatButton(
                    bar.left(68), y, 68, 20,
                    Component.translatable("vivet.button.reroll"),
                    this::requestReroll));
        }

        this.giveUpButton = addRenderableWidget(new FlatButton(
                bar.left(68), y, 68, 20,
                Component.translatable("vivet.button.give_up"),
                this::giveUp,
                FlatButton.Style.DANGER));

        if (this.plan.body()) {
            int doneWidth = 58;
            addRenderableWidget(new FlatButton(bar.right(doneWidth), y, doneWidth, 20,
                    Component.translatable("vivet.button.done"),
                    this::onClose,
                    FlatButton.Style.GHOST));
        }

        if (this.plan.draw() && Session.isMultiplayer() && bar.fits(62)) {
            addRenderableWidget(new FlatButton(bar.right(62), y, 62, 20,
                    Component.translatable("vivet.button.draw"),
                    () -> Session.proposeVote(SessionMessage.VoteKind.DRAW)));
        }

        refreshButtons();
    }

    private void buildCompactActions() {
        int y = this.top + this.panelHeight - 34;
        int x = this.left + (this.panelWidth - compactActionsWidth()) / 2;

        this.startButton = null;
        this.rerollButton = null;

        if (this.plan.reroll()) {
            this.rerollButton = addRenderableWidget(new FlatButton(x, y, REROLL_W, 20,
                    Component.translatable("vivet.button.reroll"),
                    this::requestReroll));
            x += REROLL_W + ACTION_GAP;
        }

        this.giveUpButton = addRenderableWidget(new FlatButton(x, y, GIVE_UP_W, 20,
                Component.translatable("vivet.button.give_up"),
                this::giveUp,
                FlatButton.Style.DANGER));
        x += GIVE_UP_W + ACTION_GAP;

        if (this.plan.draw() && Session.isMultiplayer()) {
            addRenderableWidget(new FlatButton(x, y, DRAW_W, 20,
                    Component.translatable("vivet.button.draw"),
                    () -> Session.proposeVote(SessionMessage.VoteKind.DRAW)));
        }

        refreshButtons();
    }

    private void requestReroll() {
        if (Session.isMultiplayer()) {
            Session.proposeVote(SessionMessage.VoteKind.REROLL);
        } else {
            Network.reroll();
        }
        refreshButtons();
    }

    private void giveUp() {
        Network.forfeit();
        onClose();
    }

    private void refreshButtons() {
        boolean running = ClientState.isRunning();

        if (this.startButton != null) {
            this.startButton.setMessage(Component.translatable(
                    running ? "vivet.button.stop" : "vivet.button.start"));
        }

        if (this.rerollButton != null) {
            this.rerollButton.active = running && ClientState.canReroll() && Session.openVote() == null;

            int rerollsLeft = Session.rerollsLeft();
            this.rerollButton.setMessage(rerollsLeft < 0
                    ? Component.translatable("vivet.button.reroll")
                    : Component.translatable("vivet.button.reroll_left", rerollsLeft));
        }

        this.giveUpButton.active = running;

        boolean alone = Session.isCarryingOn();
        this.giveUpButton.setMessage(Component.translatable(
                alone ? "vivet.grace.leave" : "vivet.button.give_up"));
        this.giveUpButton.setStyle(alone ? FlatButton.Style.SECONDARY : FlatButton.Style.DANGER);
    }

    @Override
    protected void drawScaled(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                              float partialTick) {
        Theme.fill(graphics, 0, 0, this.width, this.height, Theme.SCRIM);
        Theme.panel(graphics, this.left, this.top, this.panelWidth, this.panelHeight);

        refreshButtons();
        drawHeader(graphics);

        if (this.plan.card()) drawPracticeCard(graphics);

        if (!this.plan.body()) {
            drawWidgets(graphics, mouseX, mouseY, partialTick);

            this.overBack = in(mouseX, mouseY, backX(), this.top, UiButtons.SIDE, UiButtons.SIDE);
            UiButtons.button(graphics, UiButtons.Kind.BACK, backX(), this.top, this.overBack,
                    true, false);
            return;
        }

        drawTargetCard(graphics);

        if (showingOpponent && hasOpponentCard()) {
            OpponentCard.draw(graphics, this.font, rightColumnX(), cardTop(), rightColumnWidth(),
                    cardHeight(), opponentName());
        } else if (Session.isMultiplayer()) {
            drawStandings(graphics);
        } else {
            drawHistory(graphics, mouseX, mouseY);
        }

        drawWidgets(graphics, mouseX, mouseY, partialTick);
    }

    private void drawHeader(GuiGraphicsExtractor graphics) {
        if (!this.plan.body()) {
            graphics.centeredText(this.font, Component.literal(RunMode.current().displayName()),
                    this.left + this.panelWidth / 2, this.top + 14, Theme.TEXT);
            Theme.divider(graphics, this.left + MARGIN, this.top + 30, this.panelWidth - MARGIN * 2);
            return;
        }

        graphics.text(this.font, this.title, this.left + MARGIN, this.top + 14, Theme.TEXT);

        String stats = "%dW   %dL   Streak %d".formatted(
                countWins(), countLosses(), streak());
        graphics.text(this.font, Component.literal(stats),
                this.left + MARGIN, this.top + 28, Theme.TEXT_FAINT);

        Theme.divider(graphics, this.left + MARGIN, this.top + 44, this.panelWidth - MARGIN * 2);
    }

    private int bodyTop() {
        return this.top + 54;
    }

    private int bodyHeight() {
        return HEIGHT - 54 - 44;
    }

    private int cardTop() {
        return bodyTop() + (hasOpponentCard() ? CARD_TAB_GAP : 0);
    }

    private int cardHeight() {
        return bodyHeight() - (hasOpponentCard() ? CARD_TAB_GAP : 0);
    }

    private void drawTargetCard(GuiGraphicsExtractor graphics) {
        int x = this.left + MARGIN;
        int y = bodyTop();
        int height = bodyHeight();

        int accent = ClientState.isRunning()
                ? ClientState.difficulty().color()
                : ConfigManager.get().difficulty.color();
        Theme.panel(graphics, x, y, LEFT_CARD, height, Theme.PANEL_RAISED);

        if (!ClientState.isRunning()) {
            drawIdleCard(graphics, x, y, height);
            return;
        }

        boolean concealed = ItemRoll.isConcealing();

        int scale = 4;
        int itemX = x + (LEFT_CARD - 16 * scale) / 2;
        int itemY = y + 18;

        if (concealed) {
            int tile = 16 * scale;
            Theme.roundedRect(graphics, itemX, itemY, tile, tile, Theme.PANEL_SUNKEN);
            Theme.roundedOutline(graphics, itemX, itemY, tile, tile, Theme.BORDER);

            var mark = graphics.pose();
            mark.pushMatrix();
            mark.translate(itemX + tile / 2.0F, itemY + tile / 2.0F);
            mark.scale(3.0F, 3.0F);
            graphics.centeredText(this.font, Component.literal("?"), 0, -4, Theme.TEXT_FAINT);
            mark.popMatrix();
        } else {
            var pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(itemX, itemY);
            pose.scale(scale, scale);
            graphics.item(ClientState.target(), 0, 0);
            pose.popMatrix();
        }

        int cursor = itemY + 16 * scale + 12;
        graphics.centeredText(this.font,
                concealed ? Component.literal("???") : ClientState.targetName(),
                x + LEFT_CARD / 2, cursor, concealed ? Theme.TEXT_FAINT : Theme.TEXT);
        cursor += 16;

        String tier = ClientState.difficulty().displayName();
        int badgeWidth = this.font.width(tier) + 16;
        Theme.badge(graphics, x + (LEFT_CARD - badgeWidth) / 2, cursor, badgeWidth, 13, accent);
        graphics.centeredText(this.font, Component.literal(tier),
                x + LEFT_CARD / 2, cursor + 3, accent);
        cursor += 22;

        graphics.centeredText(this.font,
                Component.literal(Time.format(ClientState.elapsedSeconds())),
                x + LEFT_CARD / 2, cursor, Theme.TEXT);
        graphics.centeredText(this.font, Component.literal("elapsed"),
                x + LEFT_CARD / 2, cursor + 12, Theme.TEXT_FAINT);
    }

    private int practiceTop() {
        return this.top + 38;
    }

    private int practiceHeight() {
        int lines = 0;
        if (Practice.bestCentis() >= 0) lines++;
        if (practiceRealBest() > 0) lines++;

        int body = 16 * PRACTICE_ITEM_SCALE + 12 + 12 + 6 + 13 + 14 + 10 + 12;
        if (lines > 0) body += 8 + lines * 12;

        return 38 + body + 42;
    }

    private void drawPracticeCard(GuiGraphicsExtractor graphics) {
        int centre = this.left + this.panelWidth / 2;
        int cursor = practiceTop();

        ItemStack stack = ClientState.isRunning()
                ? ClientState.target()
                : practiceTargetStack();

        int tile = 16 * PRACTICE_ITEM_SCALE;
        if (!stack.isEmpty()) {
            var pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(centre - tile / 2.0F, cursor);
            pose.scale(PRACTICE_ITEM_SCALE, PRACTICE_ITEM_SCALE);
            graphics.item(stack, 0, 0);
            pose.popMatrix();
        }
        cursor += tile + 12;

        Target chosen = Practice.target();
        Component name = chosen != null ? chosen.displayName() : ClientState.targetName();
        graphics.centeredText(this.font, name, centre, cursor, Theme.TEXT);
        cursor += 12 + 6;

        Difficulty band = chosen != null ? ItemTiers.classify(chosen) : ClientState.difficulty();
        String tier = band.displayName();
        int badgeWidth = this.font.width(tier) + 16;
        Theme.badge(graphics, centre - badgeWidth / 2, cursor, badgeWidth, 13, band.color());
        graphics.centeredText(this.font, Component.literal(tier), centre, cursor + 3, band.color());
        cursor += 13 + 14;

        graphics.centeredText(this.font,
                Component.literal(Time.formatPrecise(ClientState.elapsedCentis())),
                centre, cursor, Theme.TEXT);
        cursor += 10;
        graphics.centeredText(this.font, Component.translatable("vivet.screen.elapsed"),
                centre, cursor, Theme.TEXT_FAINT);
        cursor += 12;

        int best = Practice.bestCentis();
        int real = practiceRealBest();
        if (best < 0 && real <= 0) return;

        Theme.divider(graphics, this.left + MARGIN, cursor, this.panelWidth - MARGIN * 2);
        cursor += 8;

        if (best >= 0) {
            graphics.centeredText(this.font, Component.translatable("vivet.practice.best",
                    Time.formatPrecise(best)), centre, cursor, Theme.TEXT_MUTED);
            cursor += 12;
        }
        if (real > 0) {
            graphics.centeredText(this.font, Component.translatable("vivet.practice.your_best",
                    Time.formatPrecise(real)), centre, cursor, Theme.WARNING);
        }
    }

    private ItemStack practiceTargetStack() {
        Target chosen = Practice.target();
        return chosen == null ? ItemStack.EMPTY : chosen.displayStack();
    }

    private int practiceRealBest() {
        Target chosen = Practice.target();
        if (chosen == null) return 0;

        var found = com.vivet.speedrun.p2p.CollectionBook.get(ItemTiers.ratingKey(chosen));
        return found == null || found.best == null ? 0 : found.best;
    }

    private void drawIdleCard(GuiGraphicsExtractor graphics, int x, int y, int height) {
        ModConfig config = ConfigManager.get();
        int centerX = x + LEFT_CARD / 2;
        int cursor = y + height / 2 - 30;

        graphics.centeredText(this.font, Component.translatable("vivet.screen.idle"),
                centerX, cursor, Theme.TEXT_MUTED);
        cursor += 18;

        graphics.centeredText(this.font, Component.literal(config.difficulty.displayName()),
                centerX, cursor, config.difficulty.color());
        cursor += 14;

        int pool = ItemTiers.countFor(config.difficulty, config.includeEasierItems,
                config.dimensionPool);
        graphics.centeredText(this.font, Component.literal(pool + " items"),
                centerX, cursor, Theme.TEXT_FAINT);
    }

    private int rightColumnX() {
        return this.left + MARGIN + LEFT_CARD + GUTTER;
    }

    private int rightColumnWidth() {
        return WIDTH - MARGIN * 2 - LEFT_CARD - GUTTER;
    }

    private void drawStandings(GuiGraphicsExtractor graphics) {
        int x = rightColumnX();
        int y = cardTop();
        int width = rightColumnWidth();
        int height = cardHeight();

        Theme.roundedRect(graphics, x, y, width, height, Theme.PANEL_SUNKEN);
        Theme.roundedOutline(graphics, x, y, width, height, Theme.BORDER);

        graphics.text(this.font, Component.translatable("vivet.screen.standings"),
                x + 10, y + 8, Theme.TEXT_MUTED);

        int cursor = y + 24;
        Clip.on(graphics, x + 1, y + 1, x + width - 1, y + height - 1);

        for (Racer racer : Session.racers()) {
            if (cursor > y + height - 12) break;

            int color = switch (racer.status) {
                case FINISHED -> Theme.SUCCESS;
                case OUT, DISCONNECTED -> Theme.TEXT_FAINT;
                case RACING -> Theme.TEXT;
            };

            String name = racer.name.equals(Session.localName()) ? racer.name + " (you)" : racer.name;
            graphics.text(this.font, Component.literal(name), x + 10, cursor, color);

            String right = racer.status == Racer.Status.FINISHED
                    ? Time.format(racer.seconds)
                    : racer.statusText();
            graphics.text(this.font, Component.literal(right),
                    x + width - 10 - this.font.width(right), cursor, color);
            cursor += 11;

            if (!racer.milestones.isEmpty()) {
                String last = racer.milestones.get(racer.milestones.size() - 1);
                graphics.text(this.font, Component.literal("  " + last),
                        x + 10, cursor, Theme.TEXT_FAINT);
                cursor += 11;
            }
            cursor += 4;
        }

        List<String> feed = Session.feed();
        if (!feed.isEmpty() && cursor < y + height - 24) {
            Theme.divider(graphics, x + 10, cursor, width - 20);
            cursor += 6;

            int start = Math.max(0, feed.size() - (y + height - cursor) / 10);
            for (int i = start; i < feed.size() && cursor <= y + height - 10; i++) {
                graphics.text(this.font, Component.literal(feed.get(i)),
                        x + 10, cursor, Theme.TEXT_FAINT);
                cursor += 10;
            }
        }

        Clip.off(graphics);
    }

    private void drawHistory(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = rightColumnX();
        int y = cardTop();
        int width = rightColumnWidth();
        int height = cardHeight();

        Theme.roundedRect(graphics, x, y, width, height, Theme.PANEL_SUNKEN);
        Theme.roundedOutline(graphics, x, y, width, height, Theme.BORDER);

        List<RunRecord> history = ClientState.history();
        if (history.isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("vivet.screen.no_history"),
                    x + width / 2, y + height / 2 - 4, Theme.TEXT_FAINT);
            return;
        }

        Clip.on(graphics, x + 1, y + 1, x + width - 1, y + height - 1);

        int cursor = y + 5 - this.scrollOffset;
        for (RunRecord record : history) {
            if (cursor + ROW_HEIGHT >= y && cursor <= y + height) {
                drawHistoryRow(graphics, record, x + 5, cursor, width - 10, mouseX, mouseY);
            }
            cursor += ROW_HEIGHT;
        }

        Clip.off(graphics);
    }

    private void drawHistoryRow(GuiGraphicsExtractor graphics, RunRecord record, int x, int y,
                                int width, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + width
                && mouseY >= y && mouseY <= y + ROW_HEIGHT - 2;
        if (hovered) {
            Theme.roundedRect(graphics, x, y, width, ROW_HEIGHT - 2,
                    Theme.withAlpha(0xFFFFFFFF, 0x10));
        }

        int statusColor = switch (record.outcome) {
            case WON -> Theme.SUCCESS;
            case LOST -> Theme.DANGER;
            case DRAW -> Theme.ACCENT;
            case IN_PROGRESS -> Theme.WARNING;
        };

        Theme.roundedRect(graphics, x, y, width, ROW_HEIGHT - 2,
                Theme.withAlpha(statusColor, 0x14));

        Target entry = ItemTiers.byId(record.itemId);
        ItemStack stack = entry == null ? ItemStack.EMPTY : entry.displayStack();
        if (!stack.isEmpty()) {
            graphics.item(stack, x + 8, y + 4);
        }

        String time = Time.format(record.seconds);
        int timeWidth = this.font.width(time);

        String name = entry == null ? record.itemId : entry.displayName().getString();
        name = trim(name, width - 32 - timeWidth - 8);
        graphics.text(this.font, Component.literal(name), x + 30, y + 4, Theme.TEXT);
        graphics.text(this.font, Component.literal(record.difficulty.displayName()),
                x + 30, y + 14, Theme.TEXT_FAINT);

        graphics.text(this.font, Component.literal(time),
                x + width - 8 - timeWidth, y + 4, statusColor);

        String when = new SimpleDateFormat("d MMM").format(new Date(record.date));
        graphics.text(this.font, Component.literal(when),
                x + width - 8 - this.font.width(when), y + 14, Theme.TEXT_FAINT);

        if (hovered && !stack.isEmpty()) {
            float scale = uiScale();
            graphics.setTooltipForNextFrame(this.font, stack,
                    Math.round(mouseX * scale), Math.round(mouseY * scale));
        }
    }

    private String trim(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) return text;

        StringBuilder builder = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (this.font.width(builder.toString() + c + "...") > maxWidth) break;
            builder.append(c);
        }
        return builder + "...";
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.plan == null || !this.plan.body()) return true;
        if (Session.isMultiplayer()) return true;

        int contentHeight = ClientState.history().size() * ROW_HEIGHT + 10;
        int maxScroll = Math.max(0, contentHeight - cardHeight());
        this.scrollOffset = (int) Math.max(0, Math.min(maxScroll, this.scrollOffset - scrollY * 14));
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.plan != null && !this.plan.body()) {
            int x = scaled(event.x());
            int y = scaled(event.y());
            if (in(x, y, backX(), this.top, UiButtons.SIDE, UiButtons.SIDE)) {
                onClose();
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    private int scaled(double value) {
        return (int) Math.round(value / uiScale());
    }

    private static boolean in(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private Component label(String key) {
        return Component.translatable(key);
    }

    private int countWins() {
        return (int) ClientState.history().stream().filter(RunRecord::isWin).count();
    }

    private int countLosses() {
        return (int) ClientState.history().stream()
                .filter(record -> record.outcome == RunRecord.Outcome.LOST).count();
    }

    private int streak() {
        int streak = 0;
        for (RunRecord record : ClientState.history()) {
            if (record.isInProgress()) continue;
            if (!record.isWin()) break;
            streak++;
        }
        return streak;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
