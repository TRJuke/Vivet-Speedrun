package com.vivet.speedrun.client.screen;

import com.vivet.speedrun.client.Cues;
import com.vivet.speedrun.client.ui.Clip;
import com.vivet.speedrun.client.ui.Cosmetics;
import com.vivet.speedrun.client.ui.FlatButton;
import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.client.ui.ToggleRow;
import com.vivet.speedrun.p2p.MojangAuth;
import com.vivet.speedrun.p2p.Profiles;
import com.vivet.speedrun.p2p.Wardrobe;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProfileSettingsScreen extends VivetScreen {
    private static final int WIDTH = 320;
    private static final int PADDING = 14;
    private static final int ROW_H = 22;

    private static final int BIO_H = 68;

    private final Screen parent;

    private MultiLineEditBox bio;

    private final Map<String, Boolean> shows = new LinkedHashMap<>();

    private String background = "";

    private boolean requests = true;

    private boolean watch = true;

    private String wasBio = "";
    private String wasBackground = "";
    private boolean wasRequests = true;
    private boolean wasWatch = true;
    private Map<String, Boolean> wasShows = new HashMap<>();

    private int left;
    private int top;
    private int panelHeight;
    private int bioY;
    private int backgroundsY;

    private final List<String> backgrounds = new ArrayList<>();

    private int backgroundsScroll;
    private boolean draggingBackgrounds;

    private static final int CELL = 52;
    private static final int CELL_GAP = 5;
    private static final int CELL_H = 30;
    private static final int STRIP_BAR = 3;

    public ProfileSettingsScreen(Screen parent) {
        super(Component.translatable("vivet.screen.profile_settings"));
        this.parent = parent;
    }

    private static final String[] KEYS = {
            "hours", "matches", "record", "streak", "bestStreak", "collection", "locker",
    };

    @Override
    protected void init() {
        super.init();

        Wardrobe.refresh();

        MojangAuth.Profile mine = Profiles.mine();
        MojangAuth.Shows current = Profiles.showsOf(mine);

        if (this.shows.isEmpty()) {
            this.shows.put("hours", current.hours);
            this.shows.put("matches", current.matches);
            this.shows.put("record", current.record);
            this.shows.put("streak", current.streak);
            this.shows.put("bestStreak", current.bestStreak);
            this.shows.put("collection", current.collection);
            this.shows.put("locker", current.locker);

            this.requests = mine == null || mine.allowsRequests;
            this.wasRequests = this.requests;
            this.watch = mine == null || mine.allowsWatching;
            this.wasWatch = this.watch;

            this.wasShows = new HashMap<>(this.shows);
            this.wasBio = mine == null || mine.bio == null ? "" : mine.bio;
            this.wasBackground = mine == null || mine.background == null ? "" : mine.background;
            this.background = this.wasBackground;
        }

        buildBackgrounds();

        int width = Math.min(WIDTH, this.width - 16) - PADDING * 2;

        this.panelHeight = Math.min(this.height - 8,
                PADDING + 20
                + 12 + BIO_H
                + 16 + (KEYS.length + 2) * ROW_H
                + 20 + 38
                + 12 + 20 + PADDING);

        this.left = (this.width - Math.min(WIDTH, this.width - 16)) / 2;
        this.top = Math.max(4, (this.height - this.panelHeight) / 2);

        int x = this.left + PADDING;
        int y = this.top + PADDING + 20;

        String held = this.bio == null ? this.wasBio : this.bio.getValue();

        this.bioY = y + 12;
        this.bio = MultiLineEditBox.builder()
                .setX(x)
                .setY(this.bioY)
                .setShowBackground(false)
                .setShowDecorations(false)
                .setTextColor(Theme.TEXT)
                .setPlaceholder(Component.translatable("vivet.profile.set.bio_hint")
                        .copy().withStyle(ChatFormatting.ITALIC))
                .build(this.font, width, BIO_H, Component.translatable("vivet.profile.set.bio"));

        this.bio.setCharacterLimit(Profiles.bioMax());
        this.bio.setValue(held == null ? "" : held);
        addRenderableWidget(this.bio);

        y = this.bioY + BIO_H + 16;

        for (String key : KEYS) {
            addRenderableWidget(new ToggleRow(x, y, width, ROW_H,
                    Component.translatable("vivet.profile.set." + key), null,
                    () -> Boolean.TRUE.equals(this.shows.get(key)),
                    on -> {
                        this.shows.put(key, on);
                        Cues.confirm();
                    }));
            y += ROW_H;
        }

        addRenderableWidget(new ToggleRow(x, y, width, ROW_H,
                Component.translatable("vivet.profile.set.requests"), null,
                () -> this.requests,
                on -> {
                    this.requests = on;
                    Cues.confirm();
                }));
        y += ROW_H;

        addRenderableWidget(new ToggleRow(x, y, width, ROW_H,
                Component.translatable("vivet.profile.set.watch"), null,
                () -> this.watch,
                on -> {
                    this.watch = on;
                    Cues.confirm();
                }));
        y += ROW_H;

        y += 20;
        this.backgroundsY = y;
        y += 38;

        y += 12;
        addRenderableWidget(new FlatButton(x, y, width, 20,
                Component.translatable("vivet.button.done"), this::onClose,
                FlatButton.Style.PRIMARY));
    }

    private void buildBackgrounds() {
        this.backgrounds.clear();
        this.backgrounds.add("");

        for (Wardrobe.Item item : Wardrobe.catalogue()) {
            if (item == null || !"background".equals(item.kind)) continue;
            if (Wardrobe.owns(item.id)) this.backgrounds.add(item.id);
        }

        if (this.backgrounds.contains(this.background)) return;

        if (Wardrobe.known()) {
            this.background = "";
            return;
        }

        if (!this.background.isEmpty()) this.backgrounds.add(this.background);
    }

    @Override
    protected void drawScaled(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                              float partialTick) {
        Theme.fill(graphics, 0, 0, this.width, this.height, Theme.SCRIM);

        int width = Math.min(WIDTH, this.width - 16);

        Theme.roundedBox(graphics, this.left, this.top, width, this.panelHeight, 8, Theme.PANEL);
        Theme.roundedBoxOutline(graphics, this.left, this.top, width, this.panelHeight, 8,
                Theme.BORDER);

        graphics.text(this.font, Component.translatable("vivet.profile.set.title")
                        .copy().withStyle(ChatFormatting.BOLD),
                this.left + PADDING, this.top + PADDING, Theme.TEXT, true);

        if (this.bio != null) {
            graphics.text(this.font, Component.translatable("vivet.profile.set.bio"),
                    this.bio.getX(), this.bio.getY() - 11, Theme.TEXT_FAINT);

            Theme.roundedBox(graphics, this.bio.getX() - 4, this.bio.getY() - 3,
                    this.bio.getWidth() + 8, BIO_H + 6, 4, Theme.PANEL_SUNKEN);
            Theme.roundedBoxOutline(graphics, this.bio.getX() - 4, this.bio.getY() - 3,
                    this.bio.getWidth() + 8, BIO_H + 6, 4, Theme.BORDER);

            String left = (Profiles.bioMax() - this.bio.getValue().length()) + "";
            graphics.text(this.font, Component.literal(left),
                    this.bio.getX() + this.bio.getWidth() - this.font.width(left),
                    this.bio.getY() - 11, Theme.TEXT_FAINT);
        }

        drawBackgrounds(graphics, mouseX, mouseY);

        drawWidgets(graphics, mouseX, mouseY, partialTick);
    }

    private int stripX() {
        return this.left + PADDING;
    }

    private int stripWidth() {
        return Math.min(WIDTH, this.width - 16) - PADDING * 2;
    }

    private int stripContent() {
        return Math.max(0, this.backgrounds.size() * (CELL + CELL_GAP) - CELL_GAP);
    }

    private int stripMax() {
        return Math.max(0, stripContent() - stripWidth());
    }

    private int stripBarY() {
        return this.backgroundsY + CELL_H + 3;
    }

    private boolean overStrip(int mouseX, int mouseY) {
        return mouseX >= stripX() && mouseX < stripX() + stripWidth()
                && mouseY >= this.backgroundsY && mouseY < stripBarY() + STRIP_BAR + 2;
    }

    private int thumbWidth() {
        return Math.max(16, stripWidth() * stripWidth() / Math.max(1, stripContent()));
    }

    private void drawBackgrounds(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = stripX();
        int width = stripWidth();
        this.backgroundsScroll = Math.max(0, Math.min(stripMax(), this.backgroundsScroll));

        graphics.text(this.font, Component.translatable("vivet.profile.set.background"),
                x, this.backgroundsY - 11, Theme.TEXT_FAINT);

        Clip.on(graphics, x, this.backgroundsY, x + width, this.backgroundsY + CELL_H);
        try {
            for (int i = 0; i < this.backgrounds.size(); i++) {
                int cellX = x + i * (CELL + CELL_GAP) - this.backgroundsScroll;
                if (cellX + CELL < x || cellX > x + width) continue;

                String id = this.backgrounds.get(i);
                boolean chosen = id.equals(this.background);
                boolean over = mouseX >= Math.max(x, cellX) && mouseX < Math.min(x + width, cellX + CELL)
                        && mouseY >= this.backgroundsY && mouseY < this.backgroundsY + CELL_H;

                Theme.roundedBox(graphics, cellX, this.backgroundsY, CELL, CELL_H, 4,
                        Theme.PANEL_SUNKEN);

                Wardrobe.Item item = id.isEmpty() ? null : Wardrobe.item(id);
                if (item != null) {
                    Cosmetics.ready(item);
                    Cosmetics.draw(graphics, this.font, item, cellX + 2, this.backgroundsY + 2,
                            CELL - 4, CELL_H - 4, System.currentTimeMillis(), true, false);
                } else {
                    Component none = Component.translatable("vivet.profile.set.background_default");
                    String text = this.font.plainSubstrByWidth(none.getString(), CELL - 6);
                    graphics.text(this.font, Component.literal(text),
                            cellX + (CELL - this.font.width(text)) / 2,
                            this.backgroundsY + (CELL_H - this.font.lineHeight) / 2,
                            Theme.TEXT_MUTED);
                }

                Theme.roundedBoxOutline(graphics, cellX, this.backgroundsY, CELL, CELL_H, 4,
                        chosen ? 0xFFD07BE0 : over ? Theme.PLATE_EDGE_LIT : Theme.PLATE_EDGE);
            }
        } finally {
            Clip.off(graphics);
        }

        if (stripMax() > 0) {
            int barY = stripBarY();
            boolean lit = this.draggingBackgrounds || (mouseY >= barY - 3 && mouseY < barY + STRIP_BAR + 3
                    && mouseX >= x && mouseX < x + width);
            Theme.roundedBox(graphics, x, barY, width, STRIP_BAR, 1, Theme.withAlpha(0xFFFFFFFF, 0x12));
            int travel = width - thumbWidth();
            int at = travel * this.backgroundsScroll / Math.max(1, stripMax());
            Theme.roundedBox(graphics, x + at, barY, thumbWidth(), STRIP_BAR, 1,
                    Theme.withAlpha(0xFFFFFFFF, lit ? 0xBB : 0x55));
        }
    }

    private void dragStripTo(int mouseX) {
        int travel = Math.max(1, stripWidth() - thumbWidth());
        int want = Math.max(0, Math.min(travel, mouseX - stripX() - thumbWidth() / 2));
        this.backgroundsScroll = Math.round(stripMax() * (float) want / travel);
    }

    private int scaled(double value) {
        return (int) Math.round(value / uiScale());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mouseX = scaled(event.x());
        int mouseY = scaled(event.y());

        int x = stripX();
        int width = stripWidth();

        if (stripMax() > 0 && mouseX >= x && mouseX < x + width
                && mouseY >= stripBarY() - 3 && mouseY < stripBarY() + STRIP_BAR + 3) {
            this.draggingBackgrounds = true;
            dragStripTo(mouseX);
            return true;
        }

        if (mouseX >= x && mouseX < x + width) {
            for (int i = 0; i < this.backgrounds.size(); i++) {
                int cellX = x + i * (CELL + CELL_GAP) - this.backgroundsScroll;
                if (mouseX >= cellX && mouseX < cellX + CELL
                        && mouseY >= this.backgroundsY && mouseY < this.backgroundsY + CELL_H) {
                    this.background = this.backgrounds.get(i);
                    Cues.confirm();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.draggingBackgrounds) {
            dragStripTo(scaled(event.x()));
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.draggingBackgrounds = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (stripMax() > 0 && overStrip(scaled(mouseX), scaled(mouseY))) {
            double step = deltaX != 0 ? -deltaX : deltaY;
            this.backgroundsScroll = Math.max(0, Math.min(stripMax(),
                    this.backgroundsScroll - (int) Math.signum(step) * (CELL + CELL_GAP)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public void onClose() {
        save();
        this.minecraft.gui.setScreen(this.parent);
    }

    private void save() {
        String nowBio = this.bio == null ? this.wasBio : this.bio.getValue();

        boolean changed = !nowBio.equals(this.wasBio)
                || !this.background.equals(this.wasBackground)
                || this.requests != this.wasRequests
                || this.watch != this.wasWatch
                || !this.shows.equals(this.wasShows);

        if (!changed) return;

        Profiles.save(Map.of(
                "bio", nowBio,
                "background", this.background,
                "allowRequests", this.requests,
                "allowWatch", this.watch,
                "shows", new HashMap<>(this.shows)));

        Cues.confirm();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
