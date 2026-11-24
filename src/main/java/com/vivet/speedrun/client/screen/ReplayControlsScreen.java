package com.vivet.speedrun.client.screen;

import com.vivet.speedrun.client.Cues;
import com.vivet.speedrun.client.VivetSpeedrunClient;
import com.vivet.speedrun.client.replay.ReplayPlayback;
import com.vivet.speedrun.client.replay.ReplayScreens;
import com.vivet.speedrun.client.replay.ReplayWords;
import com.vivet.speedrun.client.ui.Chip;
import com.vivet.speedrun.client.ui.Clip;
import com.vivet.speedrun.client.ui.FlatButton;
import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.client.ui.Tip;
import com.vivet.speedrun.game.Time;
import com.vivet.speedrun.replay.LoadedReplay;
import com.vivet.speedrun.replay.ReplayEvent;
import com.vivet.speedrun.replay.ReplayTrace;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class ReplayControlsScreen extends VivetScreen {
    private static final int MAX_WIDTH = 540;
    private static final int PADDING = 10;
    private static final int HEIGHT = 66;
    private static final int BOTTOM = 8;
    private static final int ROW_H = 18;
    private static final int BAR_Y = 24;
    private static final int[] SPEEDS = { 1, 2, 4, 8, 16 };

    private static final int LINE_H = 11;
    private static final int EVENTS_GAP = 6;
    private static final int EVENTS_MAX_H = 150;

    private static boolean eventsOpen;

    private int left;
    private int top;
    private int panelW;

    private float dragging = -1.0F;

    private final List<ReplayWords.EventLine> lines = new ArrayList<>();
    private final List<String> names = new ArrayList<>();
    private int linesScroll;
    private long scrolledAt;

    public ReplayControlsScreen() {
        super(Component.translatable("vivet.replay.controls"));
    }

    @Override
    protected void init() {
        super.init();
        ReplayPlayback playback = ReplayPlayback.get();
        if (playback == null) return;

        this.panelW = Math.min(MAX_WIDTH, this.width - 16);
        this.left = (this.width - this.panelW) / 2;
        this.top = this.height - HEIGHT - BOTTOM;

        if (this.lines.isEmpty()) {
            List<ReplayTrace> traces = new ArrayList<>();
            for (LoadedReplay.View view : playback.replay().views()) {
                traces.add(view.trace());
                this.names.add(view.name());
            }
            this.lines.addAll(ReplayWords.events(traces));
        }

        int y = this.top + HEIGHT - PADDING - ROW_H;
        int x = this.left + PADDING;

        addRenderableWidget(new FlatButton(x, y, 52, ROW_H,
                Component.translatable(playback.isPaused() ? "vivet.replay.play" : "vivet.replay.pause"),
                () -> {
                    playback.togglePause();
                    Cues.confirm();
                    rebuild();
                }, FlatButton.Style.PRIMARY));
        x += 58;

        for (int speed : SPEEDS) {
            Component label = Component.literal(speed + "x");
            int width = Chip.widthFor(label) - 6;
            addRenderableWidget(new Chip(x, y + 1, width, ROW_H - 2, label, Theme.ACCENT,
                    () -> playback.speed() == speed,
                    () -> {
                        while (playback.speed() != speed) playback.cycleSpeed(1);
                        Cues.confirm();
                    }));
            x += width + 3;
        }

        x += 8 + this.font.width(Component.translatable("vivet.replay.fly")) + 4;
        for (int fly : ReplayPlayback.FLY_SPEEDS) {
            Component label = Component.literal(fly + "x");
            int width = Chip.widthFor(label) - 6;
            addRenderableWidget(new Chip(x, y + 1, width, ROW_H - 2, label, Theme.SUCCESS,
                    () -> playback.flySpeed() == fly,
                    () -> {
                        playback.setFlySpeed(fly);
                        Cues.confirm();
                    }));
            x += width + 3;
        }

        int cursor = this.left + this.panelW - PADDING;

        Component leave = Component.translatable("vivet.replay.leave");
        int leaveW = this.font.width(leave) + 16;
        cursor -= leaveW;
        addRenderableWidget(new FlatButton(cursor, y, leaveW, ROW_H, leave, () -> {
            Cues.confirm();
            playback.leave();
        }, FlatButton.Style.DANGER));

        Component inventory = Component.translatable("vivet.replay.inventory");
        int inventoryW = this.font.width(inventory) + 16;
        cursor -= inventoryW + 4;
        addRenderableWidget(new FlatButton(cursor, y, inventoryW, ROW_H, inventory, () -> {
            Cues.confirm();
            ReplayScreens.open();
        }, FlatButton.Style.SECONDARY));

        Component events = Component.translatable("vivet.replay.tab_events");
        int eventsW = this.font.width(events) + 16;
        cursor -= eventsW + 4;
        addRenderableWidget(new FlatButton(cursor, y, eventsW, ROW_H, events, () -> {
            eventsOpen = !eventsOpen;
            this.scrolledAt = 0L;
            Cues.confirm();
            rebuild();
        }, eventsOpen ? FlatButton.Style.PRIMARY : FlatButton.Style.SECONDARY));

        String other = playback.otherName();
        if (!other.isEmpty()) {
            Component watch = Component.translatable("vivet.replay.watch_other", other);
            int watchW = this.font.width(watch) + 16;
            if (cursor - watchW - 4 > x + 6) {
                cursor -= watchW + 4;
                addRenderableWidget(new FlatButton(cursor, y, watchW, ROW_H, watch, () -> {
                    playback.switchPlayer();
                    rebuild();
                }, FlatButton.Style.SECONDARY));
            }
        }
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    protected void drawScaled(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        ReplayPlayback playback = ReplayPlayback.get();
        if (playback == null) {
            onClose();
            return;
        }

        Theme.roundedBox(graphics, this.left, this.top, this.panelW, HEIGHT, 6, Theme.PANEL);
        Theme.roundedBoxOutline(graphics, this.left, this.top, this.panelW, HEIGHT, 6, Theme.BORDER);

        int duration = Math.max(1, playback.raceCentisAt(playback.durationMs()));
        int now = this.dragging >= 0 ? Math.round(this.dragging * duration) : playback.raceCentis();

        String done = Time.formatPrecise(now);
        String all = Time.formatPrecise(duration);
        int textY = this.top + PADDING;
        graphics.text(this.font, Component.literal(done), this.left + PADDING, textY, Theme.TEXT);
        graphics.text(this.font, Component.literal(all),
                this.left + this.panelW - PADDING - this.font.width(all), textY, Theme.TEXT_FAINT);

        String who = playback.watchedName();
        graphics.centeredText(this.font, Component.literal(who), this.left + this.panelW / 2, textY,
                Theme.TEXT_MUTED);

        int[] bar = bar();
        Theme.roundedRect(graphics, bar[0], bar[1], bar[2], bar[3], Theme.PANEL_SUNKEN);
        Theme.roundedOutline(graphics, bar[0], bar[1], bar[2], bar[3], Theme.BORDER);
        float fraction = Math.max(0.0F, Math.min(1.0F, now / (float) duration));
        int filled = Math.round(fraction * (bar[2] - 2));
        if (filled > 0) Theme.roundedRect(graphics, bar[0] + 1, bar[1] + 1, filled, bar[3] - 2, Theme.ACCENT);

        List<Tip.Line> tip = null;
        for (ReplayTrace.Sample sample : playback.trace().samples()) {
            ReplayEvent event = sample.event();
            if (event == ReplayEvent.SAMPLE || event == ReplayEvent.START) continue;
            int at = bar[0] + 1 + Math.round(sample.centis() / (float) duration * (bar[2] - 2));
            int colour = switch (event) {
                case DIED -> Theme.DANGER;
                case FOUND -> Theme.SUCCESS;
                default -> Theme.WARNING;
            };
            Theme.fill(graphics, at, bar[1] - 2, at + 1, bar[1] + bar[3] + 2, colour);
            if (tip == null && Math.abs(mouseX - at) <= 2 && mouseY >= bar[1] - 4 && mouseY <= bar[1] + bar[3] + 4) {
                Component line = ReplayWords.describe(sample);
                if (line != null) {
                    tip = Tip.lines();
                    tip.add(new Tip.Line(Component.literal(Time.formatPrecise(sample.centis())), Theme.TEXT_FAINT));
                    tip.add(new Tip.Line(line, Theme.TEXT));
                }
            }
        }

        int handle = bar[0] + 1 + filled;
        Theme.fill(graphics, handle - 1, bar[1] - 3, handle + 2, bar[1] + bar[3] + 3, Theme.TEXT);

        int flyX = flyLabelX();
        graphics.text(this.font, Component.translatable("vivet.replay.fly"), flyX,
                this.top + HEIGHT - PADDING - ROW_H + (ROW_H - 8) / 2, Theme.TEXT_FAINT);

        if (eventsOpen) drawEvents(graphics, mouseX, mouseY, now);

        drawWidgets(graphics, mouseX, mouseY, partialTick);
        if (tip != null) Tip.draw(graphics, this.font, tip, mouseX, mouseY, this.width, this.height);
    }

    private int flyLabelX() {
        int x = this.left + PADDING + 58;
        for (int speed : SPEEDS) x += Chip.widthFor(Component.literal(speed + "x")) - 6 + 3;
        return x + 8;
    }

    private int[] bar() {
        return new int[] { this.left + PADDING, this.top + BAR_Y, this.panelW - PADDING * 2, 6 };
    }

    private int[] eventsBox() {
        int h = Math.min(EVENTS_MAX_H, Math.max(1, this.lines.size()) * LINE_H + 8);
        return new int[] { this.left, this.top - EVENTS_GAP - h, this.panelW, h };
    }

    private void drawEvents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int now) {
        int[] box = eventsBox();
        Theme.roundedBox(graphics, box[0], box[1], box[2], box[3], 6, Theme.PANEL);
        Theme.roundedBoxOutline(graphics, box[0], box[1], box[2], box[3], 6, Theme.BORDER);
        if (this.lines.isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("vivet.replay.no_events"),
                    box[0] + box[2] / 2, box[1] + (box[3] - 8) / 2, Theme.TEXT_FAINT);
            return;
        }

        int current = lineAt(now);
        int visible = Math.max(1, (box[3] - 8) / LINE_H);
        if (System.currentTimeMillis() - this.scrolledAt > 3000L && current >= 0) {
            this.linesScroll = Math.max(0, current - visible / 2);
        }
        this.linesScroll = Math.max(0, Math.min(this.linesScroll, Math.max(0, this.lines.size() - visible)));

        int timeW = this.font.width("00:00.00");
        for (ReplayWords.EventLine each : this.lines) {
            timeW = Math.max(timeW, this.font.width(Time.formatPrecise(each.centis())));
        }
        timeW += 6;
        int nameW = 0;
        if (this.names.size() > 1) {
            for (String name : this.names) nameW = Math.max(nameW, this.font.width(name));
            nameW += 6;
        }

        Clip.on(graphics, box[0] + 1, box[1] + 1, box[0] + box[2] - 1, box[1] + box[3] - 1);
        try {
            int y = box[1] + 4;
            for (int i = this.linesScroll; i < this.lines.size() && i < this.linesScroll + visible; i++) {
                ReplayWords.EventLine line = this.lines.get(i);
                boolean past = line.centis() <= now;
                boolean over = mouseX >= box[0] && mouseX < box[0] + box[2] && mouseY >= y - 1 && mouseY < y + LINE_H - 1;
                if (i == current || over) {
                    Theme.fill(graphics, box[0] + 2, y - 1, box[0] + box[2] - 2, y + LINE_H - 1,
                            Theme.withAlpha(0xFFFFFFFF, over ? 0x1C : 0x10));
                }
                int x = box[0] + PADDING;
                graphics.text(this.font, Component.literal(Time.formatPrecise(line.centis())), x, y,
                        past ? Theme.TEXT_MUTED : Theme.TEXT_FAINT);
                x += timeW;
                if (nameW > 0) {
                    int colour = ReplayWords.PLAYER_COLOURS[Math.min(line.who(), ReplayWords.PLAYER_COLOURS.length - 1)];
                    String name = line.who() < this.names.size() ? this.names.get(line.who()) : "";
                    graphics.text(this.font, Component.literal(name), x, y, past ? colour : Theme.fade(colour, 0.45F));
                    x += nameW;
                }
                String text = this.font.plainSubstrByWidth(line.text().getString(), Math.max(0, box[0] + box[2] - PADDING - x));
                graphics.text(this.font, Component.literal(text), x, y, past ? Theme.TEXT : Theme.TEXT_FAINT);
                y += LINE_H;
            }
        } finally {
            Clip.off(graphics);
        }
    }

    private int lineAt(int centis) {
        int found = -1;
        int low = 0;
        int high = this.lines.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (this.lines.get(mid).centis() <= centis) {
                found = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return found;
    }

    private boolean overEvents(double x, double y) {
        if (!eventsOpen) return false;
        int[] box = eventsBox();
        return x >= box[0] && x < box[0] + box[2] && y >= box[1] && y < box[1] + box[3];
    }

    private boolean clickEvent(double x, double y) {
        if (!overEvents(x, y)) return false;
        int[] box = eventsBox();
        int index = this.linesScroll + (int) ((y - (box[1] + 3)) / LINE_H);
        ReplayPlayback playback = ReplayPlayback.get();
        if (playback == null || index < 0 || index >= this.lines.size() || y < box[1] + 3) return true;
        playback.seekCentis(this.lines.get(index).centis());
        this.scrolledAt = System.currentTimeMillis();
        Cues.tick();
        rebuild();
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = event.x() / uiScale();
        double y = event.y() / uiScale();
        int[] bar = bar();
        if (x >= bar[0] - 4 && x <= bar[0] + bar[2] + 4 && y >= bar[1] - 6 && y <= bar[1] + bar[3] + 5) {
            this.dragging = fractionAt(x);
            return true;
        }
        if (clickEvent(x, y)) return true;
        if (super.mouseClicked(event, doubleClick)) return true;
        boolean onPanel = x >= this.left && x < this.left + this.panelW && y >= this.top && y < this.top + HEIGHT;
        if (!onPanel) onClose();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        double x = mouseX / uiScale();
        double y = mouseY / uiScale();
        if (overEvents(x, y)) {
            this.linesScroll = Math.max(0, this.linesScroll - (int) Math.signum(deltaY) * 3);
            this.scrolledAt = System.currentTimeMillis();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.dragging >= 0) {
            this.dragging = fractionAt(event.x() / uiScale());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        ReplayPlayback playback = ReplayPlayback.get();
        if (this.dragging >= 0 && playback != null) {
            int duration = Math.max(1, playback.raceCentisAt(playback.durationMs()));
            playback.seekCentis(Math.round(this.dragging * duration));
            this.dragging = -1.0F;
            this.scrolledAt = 0L;
            Cues.tick();
            rebuild();
            return true;
        }
        return super.mouseReleased(event);
    }

    private float fractionAt(double x) {
        int[] bar = bar();
        return (float) Math.max(0.0, Math.min(1.0, (x - bar[0] - 1) / Math.max(1, bar[2] - 2)));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        ReplayPlayback playback = ReplayPlayback.get();
        if (playback != null) {
            if (VivetSpeedrunClient.REPLAY_CONTROLS.matches(event)) {
                onClose();
                return true;
            }
            switch (event.key()) {
                case com.mojang.blaze3d.platform.InputConstants.KEY_SPACE -> {
                    playback.togglePause();
                    Cues.confirm();
                    rebuild();
                    return true;
                }
                case com.mojang.blaze3d.platform.InputConstants.KEY_LEFT -> {
                    playback.skip(-5);
                    Cues.tick();
                    return true;
                }
                case com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT -> {
                    playback.skip(5);
                    Cues.tick();
                    return true;
                }
                default -> {
                }
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    }
}
