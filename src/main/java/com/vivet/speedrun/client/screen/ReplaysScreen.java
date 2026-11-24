package com.vivet.speedrun.client.screen;

import com.vivet.speedrun.client.Cues;
import com.vivet.speedrun.client.ui.Clip;
import com.vivet.speedrun.client.ui.PlayerHead;
import com.vivet.speedrun.client.ui.RailRow;
import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.client.ui.UiButtons;
import com.vivet.speedrun.client.ui.VanillaIcon;
import com.vivet.speedrun.game.Difficulty;
import com.vivet.speedrun.game.ItemTiers;
import com.vivet.speedrun.game.Target;
import com.vivet.speedrun.game.Time;
import com.vivet.speedrun.p2p.MojangAuth;
import com.vivet.speedrun.p2p.ReplayApi;
import com.vivet.speedrun.replay.LoadedReplay;
import com.vivet.speedrun.replay.ReplayMatch;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class ReplaysScreen extends VivetScreen {
    private static final int MAX_WIDTH = 340;
    private static final int MIN_WIDTH = 230;

    private static final int MARGIN_X = 6;
    private static final int MARGIN_Y = 8;

    private static final int RAIL_GAP = 8;
    private static final int PADDING = 10;

    private static final int RAIL_W = 104;
    private static final int RAIL_MIN = 70;
    private static final int RAIL_H = 26;
    private static final int RAIL_STEP = 4;

    private static final int PANEL_R = 8;
    private static final int HEAD_H = 26;

    private static final int CARD_H = 36;
    private static final int CARD_GAP = 4;
    private static final int CARD_R = 5;
    private static final int CARD_PAD = 7;

    private static final int WANTED_CARDS = 7;

    private static final int BAR_ROOM = 10;
    private static final int BAR_W = 4;
    private static final int BAR_GRAB = 7;

    private static final long FAILED_MS = 3000L;

    private enum Page {
        MINE("vivet.replays.page_mine", VanillaIcon.Kind.ENDER_EYE, Theme.ACCENT),
        EVERYONE("vivet.replays.page_everyone", VanillaIcon.Kind.HEAD, Theme.SUCCESS),
        REPORTED("vivet.replays.page_reported", VanillaIcon.Kind.BOOK, Theme.WARNING);

        private final String key;
        private final VanillaIcon.Kind icon;
        private final int accent;

        Page(String key, VanillaIcon.Kind icon, int accent) {
            this.key = key;
            this.icon = icon;
            this.accent = accent;
        }
    }

    private static Page lastPage = Page.MINE;
    private static String lastQuery = "";

    private final Screen parent;

    private Page page = lastPage;
    private List<ReplayMatch> matches = List.of();
    private long builtFrom = -1L;
    private boolean staff;

    private int railW;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;

    private int listLeft;
    private int listTop;
    private int listWidth;
    private int listHeight;
    private int barX;

    private int scroll;
    private int contentHeight;
    private boolean draggingBar;
    private boolean overBack;

    private EditBox search;

    private String opening = "";
    private String failed = "";
    private long failedAt;

    public ReplaysScreen(Screen parent) {
        super(Component.translatable("vivet.screen.replays"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        this.staff = ReplayApi.isStaff();
        if (!this.staff) this.page = Page.MINE;
        if (this.builtFrom < 0) ask();
        this.matches = listed();
        this.builtFrom = ReplayApi.stamp();

        int gutter = UiButtons.SIDE + RAIL_GAP;
        this.railW = this.staff
                ? Math.max(RAIL_MIN, Math.min(RAIL_W, this.width - MARGIN_X * 2 - gutter - RAIL_GAP - MIN_WIDTH))
                : 0;
        int rail = this.staff ? this.railW + RAIL_GAP : 0;

        this.panelW = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, this.width - MARGIN_X * 2 - gutter - rail));

        int furniture = PADDING * 2 + HEAD_H;
        int wanted = furniture + WANTED_CARDS * (CARD_H + CARD_GAP) - CARD_GAP;
        this.panelH = Math.min(this.height - MARGIN_Y * 2, Math.max(furniture + CARD_H, wanted));

        int cluster = rail + this.panelW + gutter;
        this.panelX = Math.max(MARGIN_X, (this.width - cluster) / 2) + rail;
        this.panelY = Math.max(MARGIN_Y, (this.height - this.panelH) / 2);

        int room = PADDING + BAR_ROOM;
        this.listLeft = this.panelX + room;
        this.listWidth = this.panelW - room * 2;
        this.listTop = this.panelY + PADDING + HEAD_H;
        this.listHeight = this.panelH - PADDING * 2 - HEAD_H;
        this.barX = this.panelX + this.panelW - PADDING - BAR_W;

        this.contentHeight = this.matches.isEmpty()
                ? 0 : this.matches.size() * (CARD_H + CARD_GAP) - CARD_GAP;
        this.scroll = Math.max(0, Math.min(maxScroll(), this.scroll));

        if (this.staff) buildRail();
        buildSearch();
    }

    private void buildRail() {
        Page[] pages = Page.values();
        for (int i = 0; i < pages.length; i++) {
            Page value = pages[i];
            addRenderableWidget(new RailRow(this.panelX - RAIL_GAP - this.railW,
                    this.panelY + i * (RAIL_H + RAIL_STEP), this.railW, RAIL_H,
                    Component.translatable(value.key), null, value.icon, value.accent,
                    () -> this.page == value,
                    () -> switchTo(value)));
        }
    }

    private void buildSearch() {
        this.search = null;
        if (this.page != Page.EVERYONE) return;

        int left = this.panelX + PADDING + BAR_ROOM;
        int width = Math.min(160, this.panelW - PADDING * 2 - BAR_ROOM * 2);
        this.search = new EditBox(this.font, left, this.panelY + PADDING - 1, width, 12,
                Component.translatable("vivet.replays.search"));
        this.search.setBordered(false);
        this.search.setMaxLength(16);
        this.search.setTextColor(Theme.TEXT);
        this.search.setHint(Component.translatable("vivet.replays.search").copy()
                .withStyle(ChatFormatting.ITALIC));
        this.search.setValue(lastQuery);
        this.search.setResponder(typed -> {
            if (typed.equals(lastQuery)) return;
            lastQuery = typed;
            ReplayApi.loadEveryone(typed, false);
        });
        addRenderableWidget(this.search);
    }

    private void switchTo(Page next) {
        if (this.page == next) return;
        this.page = next;
        lastPage = next;
        this.scroll = 0;
        Cues.confirm();
        ask();
        rebuild();
    }

    private void ask() {
        switch (this.page) {
            case MINE -> ReplayApi.loadMine();
            case EVERYONE -> {
                ReplayApi.forgetEveryone();
                ReplayApi.loadEveryone(lastQuery, false);
            }
            case REPORTED -> {
                ReplayApi.forgetEveryone();
                ReplayApi.loadEveryone("", true);
            }
        }
    }

    private List<ReplayMatch> listed() {
        List<ReplayMatch> found = this.page == Page.MINE ? ReplayApi.mine() : ReplayApi.everyone();
        return found == null ? List.of() : found;
    }

    private boolean loading() {
        return (this.page == Page.MINE ? ReplayApi.mine() : ReplayApi.everyone()) == null
                && !(this.page == Page.MINE && ReplayApi.mineFailed());
    }

    private int backX() {
        return this.panelX + this.panelW + RAIL_GAP;
    }

    private void rebuild() {
        String typed = this.search == null ? null : this.search.getValue();
        boolean focused = this.search != null && this.search.isFocused();
        clearWidgets();
        init();
        if (this.search != null && typed != null) {
            this.search.setValue(typed);
            if (focused) setInitialFocus(this.search);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (ReplayApi.stamp() != this.builtFrom) rebuild();
    }

    private int cardY(int index) {
        return this.listTop - this.scroll + index * (CARD_H + CARD_GAP);
    }

    @Override
    protected void drawScaled(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Theme.fill(graphics, 0, 0, this.width, this.height, Theme.SCRIM);

        Theme.roundedBox(graphics, this.panelX, this.panelY, this.panelW, this.panelH, PANEL_R, Theme.PANEL);
        Theme.roundedBoxOutline(graphics, this.panelX, this.panelY, this.panelW, this.panelH, PANEL_R,
                Theme.BORDER);

        drawHead(graphics);
        drawWidgets(graphics, mouseX, mouseY, partialTick);

        UiButtons.button(graphics, UiButtons.Kind.BACK, backX(), this.panelY, this.overBack, true, false);

        Clip.on(graphics, this.panelX + 1, this.listTop, this.panelX + this.panelW - 1,
                this.listTop + this.listHeight);

        if (this.matches.isEmpty()) {
            String key = loading() ? "vivet.replays.loading"
                    : this.page == Page.MINE && ReplayApi.mineFailed() ? "vivet.replays.failed"
                    : "vivet.replays.empty";
            graphics.centeredText(this.font, Component.translatable(key),
                    this.panelX + this.panelW / 2, this.listTop + this.listHeight / 2 - 4, Theme.TEXT_FAINT);
        } else {
            for (int i = 0; i < this.matches.size(); i++) {
                int y = cardY(i);
                if (y + CARD_H < this.listTop || y > this.listTop + this.listHeight) continue;
                drawCard(graphics, this.matches.get(i), y, mouseX, mouseY);
            }
        }

        Clip.off(graphics);
        drawBar(graphics, mouseX, mouseY);
    }

    private void drawHead(GuiGraphicsExtractor graphics) {
        int y = this.panelY + PADDING;
        int left = this.panelX + PADDING + BAR_ROOM;

        if (this.search == null) {
            Component title = this.page == Page.MINE ? this.title : Component.translatable(this.page.key);
            graphics.text(this.font, title, left, y, Theme.TEXT);
        }

        Theme.divider(graphics, this.panelX + PADDING, y + 14, this.panelW - PADDING * 2);
    }

    private void drawCard(GuiGraphicsExtractor graphics, ReplayMatch match, int y, int mouseX, int mouseY) {
        int x = this.listLeft;
        int width = this.listWidth;

        boolean over = mouseX >= x && mouseX < x + width
                && mouseY >= Math.max(y, this.listTop)
                && mouseY < Math.min(y + CARD_H, this.listTop + this.listHeight);

        Theme.roundedBox(graphics, x, y, width, CARD_H, CARD_R, over ? Theme.PLATE_LIT : Theme.PLATE);
        Theme.roundedBoxOutline(graphics, x, y, width, CARD_H, CARD_R, over ? Theme.PLATE_EDGE_LIT : Theme.PLATE_EDGE);

        int left = x + CARD_PAD;
        int right = x + width - CARD_PAD;

        Difficulty band = Difficulty.byId(match.band);
        int accent = band == null ? Theme.TEXT_MUTED : band.color();

        int tile = CARD_H - 10;
        int tileY = y + 5;
        Theme.roundedRect(graphics, left, tileY, tile, tile, Theme.PANEL_SUNKEN);
        Theme.roundedOutline(graphics, left, tileY, tile, tile, Theme.BORDER);
        Target target = ItemTiers.byId(match.itemId);
        ItemStack stack = target == null ? ItemStack.EMPTY : target.displayStack();
        if (!stack.isEmpty()) graphics.item(stack, left + (tile - 16) / 2, tileY + (tile - 16) / 2);

        int textLeft = left + tile + 8;
        Component name = target == null
                ? Component.translatable("vivet.replays.unknown_item") : target.displayName();
        graphics.text(this.font, name.copy().withStyle(ChatFormatting.BOLD), textLeft, y + 6, Theme.TEXT);

        Component result;
        int colour;
        String clock = Time.formatPrecise(clockOf(match));
        if (match.match.equals(this.opening)) {
            result = Component.translatable("vivet.replays.opening");
            colour = Theme.ACCENT;
        } else if (match.match.equals(this.failed)
                && System.currentTimeMillis() - this.failedAt < FAILED_MS) {
            result = Component.translatable("vivet.replays.unavailable");
            colour = Theme.DANGER;
        } else {
            result = resultWord(match);
            colour = resultColour(match, accent);
        }
        graphics.text(this.font, result, right - this.font.width(result), y + 6, colour);
        graphics.text(this.font, Component.literal(clock), right - this.font.width(clock), y + 20,
                Theme.TEXT_MUTED);

        int at = textLeft;
        int limit = right - this.font.width(clock) - 8;
        String bandName = band == null ? "" : band.displayName();
        if (!bandName.isEmpty()) {
            int badge = this.font.width(bandName) + 10;
            Theme.badge(graphics, at, y + 17, badge, 12, accent);
            graphics.text(this.font, Component.literal(bandName), at + 5, y + 19, accent);
            at += badge + 5;
        }
        String mode = match.mode().displayName();
        if (at + this.font.width(mode) < limit) {
            graphics.text(this.font, Component.literal(mode), at, y + 19, Theme.TEXT_MUTED);
            at += this.font.width(mode) + 6;
        }

        ReplayMatch.Player other = this.page == Page.MINE && !match.isSolo() ? match.opponentOf(myUuid()) : null;
        String who = whoLine(match);
        if (other != null) {
            String vs = "vs ";
            if (at + this.font.width(vs) + 12 < limit) {
                graphics.text(this.font, Component.literal(vs), at, y + 19, Theme.TEXT_FAINT);
                at += this.font.width(vs);
                PlayerHead.draw(graphics, other.name, other.uuid, at, y + 18, 8);
                at += 11;
            }
            who = other.name;
        }
        if (!who.isEmpty()) {
            String cut = this.font.plainSubstrByWidth(who, Math.max(0, limit - at));
            if (!cut.isEmpty()) graphics.text(this.font, Component.literal(cut), at, y + 19, Theme.TEXT_FAINT);
        }
    }

    private String whoLine(ReplayMatch match) {
        if (this.page == Page.MINE) return "";
        StringBuilder names = new StringBuilder();
        for (ReplayMatch.Player player : match.players) {
            if (names.length() > 0) names.append(" vs ");
            names.append(player.name);
        }
        return names.toString();
    }

    private static int clockOf(ReplayMatch match) {
        return match.winnerCentis != null && match.winnerCentis > 0 ? match.winnerCentis : match.centis();
    }

    private Component resultWord(ReplayMatch match) {
        if (this.page != Page.MINE) {
            if ("draw".equals(match.outcome)) return Component.translatable("vivet.logs.draw");
            if (!match.winnerName.isEmpty()) return Component.literal(match.winnerName);
            return Component.translatable("vivet.replays.no_finish");
        }
        String result = match.resultFor(myUuid());
        if (match.isSolo()) {
            return Component.translatable("won".equals(result) ? "vivet.replays.found" : "vivet.replays.no_finish");
        }
        return switch (result) {
            case "won" -> Component.translatable("vivet.logs.win");
            case "lost" -> Component.translatable("vivet.logs.loss");
            case "draw" -> Component.translatable("vivet.logs.draw");
            default -> Component.translatable("vivet.replays.no_finish");
        };
    }

    private int resultColour(ReplayMatch match, int fallback) {
        if (this.page != Page.MINE) return match.winnerName.isEmpty() ? Theme.TEXT_FAINT : Theme.TEXT_MUTED;
        String result = match.resultFor(myUuid());
        if (match.isSolo()) return "won".equals(result) ? fallback : Theme.TEXT_FAINT;
        return switch (result) {
            case "won" -> Theme.SUCCESS;
            case "lost" -> Theme.DANGER;
            case "draw" -> Theme.TEXT_MUTED;
            default -> Theme.TEXT_FAINT;
        };
    }

    private static String myUuid() {
        MojangAuth.Identity identity = MojangAuth.identity();
        return identity == null || identity.uuid == null ? "" : identity.uuid;
    }

    private void open(ReplayMatch match) {
        if (!this.opening.isEmpty()) return;
        this.opening = match.match;
        this.failed = "";
        Cues.confirm();

        ReplayApi.open(match,
                loaded -> this.minecraft.execute(() -> opened(loaded)),
                problem -> this.minecraft.execute(() -> {
                    this.opening = "";
                    this.failed = match.match;
                    this.failedAt = System.currentTimeMillis();
                    Cues.deny();
                }));
    }

    private void opened(LoadedReplay loaded) {
        this.opening = "";

        if (this.minecraft.gui.screen() != this) return;
        this.minecraft.gui.setScreen(new ReplayViewScreen(this, loaded));
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
        this.scroll = Math.max(0, Math.min(maxScroll(), Math.round(maxScroll() * (float) want / travel)));
    }

    private int scaled(double value) {
        return (int) Math.round(value / uiScale());
    }

    private static boolean in(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        this.overBack = in(scaled(mouseX), scaled(mouseY), backX(), this.panelY, UiButtons.SIDE, UiButtons.SIDE);
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int x = scaled(event.x());
        int y = scaled(event.y());

        if (in(x, y, backX(), this.panelY, UiButtons.SIDE, UiButtons.SIDE)) {
            onClose();
            return true;
        }

        if (maxScroll() > 0 && overBar(x, y)) {
            this.draggingBar = true;
            dragTo(y);
            return true;
        }

        if (!in(x, y, this.panelX, this.listTop, this.panelW, this.listHeight)) {
            if (super.mouseClicked(event, doubleClick)) return true;
            return in(x, y, this.panelX, this.panelY, this.panelW, this.panelH);
        }
        if (x < this.listLeft || x >= this.listLeft + this.listWidth) return true;

        for (int i = 0; i < this.matches.size(); i++) {
            int top = cardY(i);
            if (y < top || y >= top + CARD_H) continue;
            open(this.matches.get(i));
            return true;
        }
        return true;
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
        if (maxScroll() > 0) {
            this.scroll = Math.max(0, Math.min(maxScroll(), this.scroll - (int) Math.signum(deltaY) * 18));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
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
