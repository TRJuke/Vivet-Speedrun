package com.vivet.speedrun.client.screen;

import com.vivet.speedrun.client.Cues;
import com.vivet.speedrun.client.replay.GearPicture;
import com.vivet.speedrun.client.replay.ReplayPlayback;
import com.vivet.speedrun.client.replay.ReplayWords;
import com.vivet.speedrun.client.replay.WorldRecording;
import com.vivet.speedrun.client.ui.Chip;
import com.vivet.speedrun.client.ui.Clip;
import com.vivet.speedrun.client.ui.FlatButton;
import com.vivet.speedrun.client.ui.Plural;
import com.vivet.speedrun.client.ui.PlayerHead;
import com.vivet.speedrun.client.ui.RailRow;
import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.client.ui.Tip;
import com.vivet.speedrun.client.ui.UiButtons;
import com.vivet.speedrun.client.ui.VanillaIcon;
import com.vivet.speedrun.game.Difficulty;
import com.vivet.speedrun.game.ItemTiers;
import com.vivet.speedrun.game.Target;
import com.vivet.speedrun.game.Time;
import com.vivet.speedrun.p2p.MojangAuth;
import com.vivet.speedrun.p2p.ReplayApi;
import com.vivet.speedrun.replay.LoadedReplay;
import com.vivet.speedrun.replay.ReplayEvent;
import com.vivet.speedrun.replay.ReplayMatch;
import com.vivet.speedrun.replay.ReplayTrace;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ReplayViewScreen extends VivetScreen {
    private static final int MAX_WIDTH = 440;
    private static final int MIN_WIDTH = 260;

    private static final int MARGIN_X = 6;
    private static final int MARGIN_Y = 8;
    private static final int RAIL_GAP = 8;
    private static final int PADDING = 12;
    private static final int PANEL_R = 8;

    private static final int RAIL_W = 88;
    private static final int RAIL_MIN = 58;
    private static final int RAIL_H = 26;
    private static final int RAIL_STEP = 4;

    private static final int HEAD_H = 44;
    private static final int TILE = 30;

    private static final int BODY_MAX = 196;
    private static final int BODY_MIN = 150;

    private static final int TAIL_H = 78;
    private static final int SCRUB_DROP = 12;
    private static final int SCRUB_H = 6;
    private static final int ROW_DROP = 28;
    private static final int ROW_H = 18;
    private static final int FOOT_DROP = 56;

    private static final int PLAYER_ROW_H = 24;
    private static final int PLAYER_ROW_STEP = 29;

    private static final int ROW_FACE = 12;

    private static final float CLOCK_SCALE = 1.6F;

    private static final int[] ROUTE_COLOURS = ReplayWords.PLAYER_COLOURS;

    private static final int[] SPEEDS = { 1, 2, 4, 8, 16 };

    private static final long END_HOLD_MS = 700L;
    private static final int LINE_H = 11;

    private enum View {
        ROUTE("vivet.replay.tab_route", VanillaIcon.Kind.MAP, Theme.ACCENT),
        GEAR("vivet.replay.tab_gear", VanillaIcon.Kind.ARMOUR_STAND, Theme.WARNING),
        EVENTS("vivet.replay.tab_events", VanillaIcon.Kind.BOOK, Theme.SUCCESS);

        private final String key;
        private final VanillaIcon.Kind icon;
        private final int accent;

        View(String key, VanillaIcon.Kind icon, int accent) {
            this.key = key;
            this.icon = icon;
            this.accent = accent;
        }
    }

    private static View lastView = View.ROUTE;

    private final Screen parent;
    private final LoadedReplay replay;
    private final ReplayMatch match;

    private final List<Route> routes = new ArrayList<>();
    private final List<ReplayWords.EventLine> lines = new ArrayList<>();

    private View view = lastView;
    private int dimension;
    private int speedIndex;
    private boolean playing;
    private int gearIndex;

    private int pov;
    private int overRow = -1;

    private int playhead;
    private long lastFrameMs;
    private long finishedAt;

    private boolean scrubbing;
    private boolean overBack;
    private int linesScroll;
    private long scrolledAt;

    private int railW;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int bodyX;
    private int bodyY;
    private int bodyW;
    private int bodyH;

    private double projectedScale = 1.0;

    private FlatButton playButton;
    private FlatButton worldButton;
    private ItemStack hovered = ItemStack.EMPTY;
    private List<Tip.Line> markTip;

    private boolean fetching;
    private volatile float fetched;

    private static final class Route {
        final LoadedReplay.View view;
        final int colour;

        int[] xs = new int[0];
        int[] ys = new int[0];
        int[] centis = new int[0];
        ReplayEvent[] events = new ReplayEvent[0];

        Route(LoadedReplay.View view, int colour) {
            this.view = view;
            this.colour = colour;
        }

        ReplayTrace trace() {
            return this.view.trace();
        }

        String name() {
            return this.view.name();
        }
    }

    public ReplayViewScreen(Screen parent, LoadedReplay replay) {
        super(Component.translatable("vivet.screen.replay"));
        this.parent = parent;
        this.replay = replay;
        this.match = replay.match();

        for (int i = 0; i < replay.views().size(); i++) {
            this.routes.add(new Route(replay.views().get(i), ROUTE_COLOURS[Math.min(i, ROUTE_COLOURS.length - 1)]));
        }
        this.dimension = busiestDimension();
        buildLines();
        this.playing = true;
        this.lastFrameMs = System.currentTimeMillis();
        Cues.replayOpened();
    }

    private void buildLines() {
        List<ReplayTrace> traces = new ArrayList<>();
        for (Route route : this.routes) traces.add(route.trace());
        this.lines.addAll(ReplayWords.events(traces));
    }

    @Override
    protected void init() {
        super.init();

        int gutter = UiButtons.SIDE + RAIL_GAP;
        this.railW = Math.max(RAIL_MIN, Math.min(RAIL_W, this.width - MARGIN_X * 2 - gutter - RAIL_GAP - MIN_WIDTH));
        this.panelW = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, this.width - MARGIN_X * 2 - gutter - RAIL_GAP - this.railW));

        int furniture = PADDING * 2 + HEAD_H + tailHeight();
        this.bodyH = Math.max(BODY_MIN, Math.min(BODY_MAX, this.height - MARGIN_Y * 2 - furniture));
        this.panelH = furniture + this.bodyH;

        int cluster = this.railW + RAIL_GAP + this.panelW + gutter;
        this.panelX = Math.max(MARGIN_X, (this.width - cluster) / 2) + this.railW + RAIL_GAP;
        this.panelY = Math.max(MARGIN_Y, (this.height - this.panelH) / 2);

        this.bodyX = this.panelX + PADDING;
        this.bodyY = this.panelY + PADDING + HEAD_H;
        this.bodyW = this.panelW - PADDING * 2;

        buildRail();
        buildBodyChips();
        buildControls();
        project();
    }

    private int tailHeight() {
        int rows = Math.max(1, this.routes.size());
        return Math.max(TAIL_H, FOOT_DROP - 4 + (rows - 1) * PLAYER_ROW_STEP + PLAYER_ROW_H + 4);
    }

    private void buildRail() {
        View[] views = View.values();
        for (int i = 0; i < views.length; i++) {
            View value = views[i];
            addRenderableWidget(new RailRow(this.panelX - RAIL_GAP - this.railW,
                    this.panelY + i * (RAIL_H + RAIL_STEP), this.railW, RAIL_H,
                    Component.translatable(value.key), null, value.icon, value.accent,
                    () -> this.view == value,
                    () -> switchTo(value)));
        }
    }

    private void buildBodyChips() {
        int x = this.bodyX + 6;
        int y = this.bodyY + 6;
        if (this.view == View.ROUTE) {
            for (int dim = 0; dim < 3; dim++) {
                if (!visits(dim)) continue;
                final int pick = dim;
                Component label = ReplayWords.dimensionName(dim);
                int width = Chip.widthFor(label);
                addRenderableWidget(new Chip(x, y, width, 16, label, Theme.ACCENT,
                        () -> this.dimension == pick,
                        () -> {
                            if (this.dimension == pick) return;
                            this.dimension = pick;
                            Cues.confirm();
                            rebuild();
                        }));
                x += width + 4;
            }
        } else if (this.view == View.GEAR && this.routes.size() > 1) {
            for (int i = 0; i < this.routes.size(); i++) {
                final int pick = i;
                Route route = this.routes.get(i);
                Component label = Component.literal(route.name());
                int width = Chip.widthFor(label);
                addRenderableWidget(new Chip(x, y, width, 16, label, route.colour,
                        () -> this.gearIndex == pick,
                        () -> {
                            if (this.gearIndex == pick) return;
                            this.gearIndex = pick;
                            this.pov = pick;
                            Cues.watchSwitched();
                            rebuild();
                        }));
                x += width + 4;
            }
        }
    }

    private void buildControls() {
        int y = this.bodyY + this.bodyH + ROW_DROP;
        int x = this.panelX + PADDING;

        this.playButton = addRenderableWidget(new FlatButton(x, y, 52, ROW_H,
                Component.translatable(this.playing ? "vivet.replay.pause" : "vivet.replay.play"),
                this::togglePlay, FlatButton.Style.SECONDARY));
        x += 58;

        int cursor = this.panelX + this.panelW - PADDING;

        if (this.replay.hasWorld() && this.minecraft.level == null) {
            Component label = worldLabel();
            int width = this.font.width(Component.translatable("vivet.replays.downloading", 100)) + 16;
            width = Math.max(width, this.font.width(label) + 16);
            cursor -= width;
            this.worldButton = addRenderableWidget(new FlatButton(cursor, y, width, ROW_H, label,
                    this::watchInWorld, FlatButton.Style.PRIMARY));

            if (this.replay.worldFromAnotherVersion(
                    net.minecraft.SharedConstants.getCurrentVersion().protocolVersion())) {
                this.worldButton.active = false;
                this.worldButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.translatable("vivet.replay.other_version")));
            }
        }

        if (ReportScreen.canReport(this.match)) {
            ReplayMatch.Player other = this.match.opponentOf(myUuid());
            Component label = Component.translatable("vivet.report.button");
            int width = this.font.width(label) + 16;
            cursor -= width + 6;
            addRenderableWidget(new FlatButton(cursor, y, width, ROW_H, label,
                    () -> this.minecraft.gui.setScreen(new ReportScreen(this, this.match.match,
                            other == null ? "" : other.name)),
                    FlatButton.Style.GHOST));
        }

        for (int i = 0; i < SPEEDS.length; i++) {
            final int pick = i;
            Component label = Component.literal(SPEEDS[i] + "x");
            int width = Chip.widthFor(label) - 6;
            if (x + width > cursor - 6) break;
            addRenderableWidget(new Chip(x, y + 1, width, ROW_H - 2, label, Theme.ACCENT,
                    () -> this.speedIndex == pick,
                    () -> {
                        this.speedIndex = pick;
                        Cues.confirm();
                    }));
            x += width + 3;
        }
    }

    private Component worldLabel() {
        return this.fetching
                ? Component.translatable("vivet.replays.downloading", Math.round(this.fetched * 100))
                : Component.translatable("vivet.replay.watch_in_world");
    }

    private void switchTo(View next) {
        if (this.view == next) return;
        this.view = next;
        lastView = next;
        Cues.confirm();
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private void togglePlay() {
        this.playing = !this.playing;
        if (this.playing && this.playhead >= duration()) this.playhead = 0;
        this.lastFrameMs = System.currentTimeMillis();
        this.playButton.setMessage(Component.translatable(this.playing ? "vivet.replay.pause" : "vivet.replay.play"));
        Cues.confirm();
    }

    private void watchInWorld() {
        if (this.fetching || !this.replay.hasWorld()) {
            Cues.deny();
            return;
        }
        this.fetching = true;
        this.fetched = 0.0F;
        Cues.confirm();

        int watch = Math.min(this.pov, Math.max(0, this.routes.size() - 1));
        int startAt = this.playhead >= duration() ? 0 : this.playhead;
        ReplayApi.fetchWorlds(this.replay,
                (got, all) -> this.fetched = Math.min(1.0F, got / (float) Math.max(1L, all)),
                paths -> {
                    int protocol = net.minecraft.SharedConstants.getCurrentVersion().protocolVersion();
                    List<WorldRecording> worlds = new ArrayList<>();
                    boolean any = false;
                    for (java.nio.file.Path path : paths) {
                        WorldRecording world = path == null ? null : WorldRecording.open(path, protocol);
                        worlds.add(world);
                        any |= world != null;
                    }
                    boolean found = any;
                    this.minecraft.execute(() -> {
                        this.fetching = false;
                        boolean opened = found && this.minecraft.gui.screen() == this
                                && ReplayPlayback.open(this.minecraft, this.replay, worlds, watch, startAt, this);
                        if (!opened) {
                            for (WorldRecording world : worlds) if (world != null) world.close();
                            Cues.deny();
                            if (this.worldButton != null) this.worldButton.setMessage(worldLabel());
                        }
                    });
                });
    }

    private static String myUuid() {
        MojangAuth.Identity identity = MojangAuth.identity();
        return identity == null || identity.uuid == null ? "" : identity.uuid;
    }

    private void project() {
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (Route route : this.routes) {
            for (ReplayTrace.Sample sample : route.trace().samples()) {
                if (sample.dimension() != this.dimension) continue;
                minX = Math.min(minX, sample.blockX());
                maxX = Math.max(maxX, sample.blockX());
                minZ = Math.min(minZ, sample.blockZ());
                maxZ = Math.max(maxZ, sample.blockZ());
            }
        }
        if (minX == Double.MAX_VALUE) {
            for (Route route : this.routes) {
                route.xs = new int[0];
                route.ys = new int[0];
                route.centis = new int[0];
                route.events = new ReplayEvent[0];
            }
            return;
        }

        double spanX = Math.max(1.0, maxX - minX);
        double spanZ = Math.max(1.0, maxZ - minZ);
        double scale = Math.min((this.bodyW - 28) / spanX, (this.bodyH - 44) / spanZ);
        double midX = (minX + maxX) / 2.0;
        double midZ = (minZ + maxZ) / 2.0;
        int centreX = this.bodyX + this.bodyW / 2;
        int centreY = this.bodyY + 10 + this.bodyH / 2;
        this.projectedScale = scale;

        for (Route route : this.routes) {
            List<int[]> points = new ArrayList<>();
            List<ReplayEvent> events = new ArrayList<>();
            int lastX = Integer.MIN_VALUE;
            int lastY = Integer.MIN_VALUE;
            for (ReplayTrace.Sample sample : route.trace().samples()) {
                if (sample.dimension() != this.dimension) continue;
                int px = centreX + (int) Math.round((sample.blockX() - midX) * scale);
                int py = centreY + (int) Math.round((sample.blockZ() - midZ) * scale);
                boolean plain = sample.event() == ReplayEvent.SAMPLE;
                if (plain && px == lastX && py == lastY) continue;
                points.add(new int[] { px, py, sample.centis() });
                events.add(sample.event());
                lastX = px;
                lastY = py;
            }
            route.xs = new int[points.size()];
            route.ys = new int[points.size()];
            route.centis = new int[points.size()];
            route.events = events.toArray(new ReplayEvent[0]);
            for (int i = 0; i < points.size(); i++) {
                route.xs[i] = points.get(i)[0];
                route.ys[i] = points.get(i)[1];
                route.centis[i] = points.get(i)[2];
            }
        }
    }

    private boolean visits(int dim) {
        for (Route route : this.routes) {
            for (ReplayTrace.Sample sample : route.trace().samples()) {
                if (sample.dimension() == dim) return true;
            }
        }
        return false;
    }

    private int busiestDimension() {
        int[] counts = new int[3];
        for (Route route : this.routes) {
            for (ReplayTrace.Sample sample : route.trace().samples()) {
                counts[Math.min(2, Math.max(0, sample.dimension()))]++;
            }
        }
        int best = 0;
        for (int i = 1; i < counts.length; i++) {
            if (counts[i] > counts[best]) best = i;
        }
        return best;
    }

    private int duration() {
        return this.replay.duration();
    }

    @Override
    protected void drawScaled(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        advanceClock();
        if (this.fetching && this.worldButton != null) this.worldButton.setMessage(worldLabel());

        Theme.fill(graphics, 0, 0, this.width, this.height, Theme.SCRIM);
        Theme.roundedBox(graphics, this.panelX, this.panelY, this.panelW, this.panelH, PANEL_R, Theme.PANEL);
        Theme.roundedBoxOutline(graphics, this.panelX, this.panelY, this.panelW, this.panelH, PANEL_R, Theme.BORDER);

        drawHead(graphics);

        this.hovered = ItemStack.EMPTY;
        this.markTip = null;
        switch (this.view) {
            case ROUTE -> drawMap(graphics);
            case GEAR -> drawGear(graphics, mouseX, mouseY);
            case EVENTS -> drawLines(graphics, mouseX, mouseY);
        }

        drawScrubber(graphics, mouseX, mouseY);
        drawFooter(graphics, mouseX, mouseY);
        drawWidgets(graphics, mouseX, mouseY, partialTick);

        UiButtons.button(graphics, UiButtons.Kind.BACK, this.panelX + this.panelW + RAIL_GAP, this.panelY,
                this.overBack, true, false);

        if (!this.hovered.isEmpty()) {
            List<Tip.Line> tip = Tip.lines();
            tip.add(new Tip.Line(Component.literal(this.hovered.getHoverName().getString()), Theme.TEXT));
            Tip.draw(graphics, this.font, tip, mouseX, mouseY, this.width, this.height);
        } else if (this.markTip != null) {
            Tip.draw(graphics, this.font, this.markTip, mouseX, mouseY, this.width, this.height);
        } else if (this.overRow >= 0) {
            List<Tip.Line> tip = Tip.lines();
            tip.add(new Tip.Line(Component.translatable("vivet.replay.tip.pov"), Theme.TEXT));
            Tip.draw(graphics, this.font, tip, mouseX, mouseY, this.width, this.height);
        }
    }

    private void advanceClock() {
        long now = System.currentTimeMillis();
        long since = Math.max(0L, Math.min(1000L, now - this.lastFrameMs));
        this.lastFrameMs = now;
        if (!this.playing || this.scrubbing) return;

        this.playhead += (int) (since * SPEEDS[this.speedIndex] / 10L);
        if (this.playhead >= duration()) {
            this.playhead = duration();
            if (this.finishedAt == 0L) {
                this.finishedAt = now;
            } else if (now - this.finishedAt > END_HOLD_MS) {
                this.finishedAt = 0L;
                this.playing = false;
                Cues.replayEnded();
                if (this.playButton != null) this.playButton.setMessage(Component.translatable("vivet.replay.play"));
            }
        }
    }

    private void drawHead(GuiGraphicsExtractor graphics) {
        int x = this.panelX + PADDING;
        int y = this.panelY + PADDING;

        Target target = ItemTiers.byId(this.match.itemId);
        Theme.roundedRect(graphics, x, y, TILE, TILE, Theme.PANEL_SUNKEN);
        Theme.roundedOutline(graphics, x, y, TILE, TILE, Theme.BORDER);
        ItemStack stack = target == null ? ItemStack.EMPTY : target.displayStack();
        if (!stack.isEmpty()) {
            var pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(x + 3, y + 3);
            pose.scale(1.5F, 1.5F);
            graphics.item(stack, 0, 0);
            pose.popMatrix();
        }

        int textX = x + TILE + 8;
        Component item = target == null ? Component.translatable("vivet.replays.unknown_item") : target.displayName();
        graphics.text(this.font, item.copy().withStyle(ChatFormatting.BOLD), textX, y + 2, Theme.TEXT);

        Difficulty band = Difficulty.byId(this.match.band);
        int badgeX = textX;
        if (band != null) {
            String name = band.displayName();
            int badge = this.font.width(name) + 14;
            Theme.badge(graphics, badgeX, y + 15, badge, 13, band.color());
            graphics.text(this.font, Component.literal(name), badgeX + 7, y + 18, band.color());
            badgeX += badge + 6;
        }
        graphics.text(this.font, Component.literal(this.match.mode().displayName()), badgeX, y + 18, Theme.TEXT_FAINT);

        String done = Time.formatPrecise(this.playhead);
        int right = this.panelX + this.panelW - PADDING;
        int clockW = Math.round(this.font.width(done) * CLOCK_SCALE);
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(right - clockW, y);
        pose.scale(CLOCK_SCALE, CLOCK_SCALE);
        graphics.text(this.font, Component.literal(done), 0, 0, Theme.TEXT);
        pose.popMatrix();
        String all = Time.formatPrecise(duration());
        graphics.text(this.font, Component.literal(all), right - this.font.width(all), y + 18, Theme.TEXT_FAINT);

        Theme.divider(graphics, this.panelX + PADDING, y + HEAD_H - 8, this.panelW - PADDING * 2);
    }

    private void drawBodyBox(GuiGraphicsExtractor graphics) {
        Theme.roundedBox(graphics, this.bodyX, this.bodyY, this.bodyW, this.bodyH, 5, Theme.PANEL_SUNKEN);
        Theme.roundedBoxOutline(graphics, this.bodyX, this.bodyY, this.bodyW, this.bodyH, 5, Theme.BORDER);
    }

    private void drawMap(GuiGraphicsExtractor graphics) {
        drawBodyBox(graphics);
        boolean anything = false;
        for (Route route : this.routes) {
            if (route.xs.length >= 2) anything = true;
        }
        if (!anything) {
            graphics.centeredText(this.font,
                    Component.translatable("vivet.replay.nothing_here", ReplayWords.dimensionName(this.dimension)),
                    this.bodyX + this.bodyW / 2, this.bodyY + this.bodyH / 2 - 4, Theme.TEXT_FAINT);
            return;
        }
        Clip.on(graphics, this.bodyX + 1, this.bodyY + 1, this.bodyX + this.bodyW - 1, this.bodyY + this.bodyH - 1);
        try {
            for (int i = this.routes.size() - 1; i >= 0; i--) {
                if (i != this.pov) drawRoute(graphics, this.routes.get(i));
            }
            if (this.pov < this.routes.size()) drawRoute(graphics, this.routes.get(this.pov));
        } finally {
            Clip.off(graphics);
        }
        drawScaleBar(graphics);
    }

    private void drawRoute(GuiGraphicsExtractor graphics, Route route) {
        if (route.xs.length < 2 || route.centis[0] > this.playhead) return;
        int shown = 0;
        for (int i = 0; i < route.centis.length && route.centis[i] <= this.playhead; i++) shown = i;

        for (int i = 1; i <= shown; i++) {
            float age = 0.3F + 0.7F * (i / (float) Math.max(1, route.xs.length - 1));
            line(graphics, route.xs[i - 1], route.ys[i - 1], route.xs[i], route.ys[i], Theme.fade(route.colour, age));
        }
        for (int i = 0; i <= shown; i++) {
            ReplayEvent event = route.events[i];
            if (event == ReplayEvent.SAMPLE || event == ReplayEvent.START) continue;
            int colour = switch (event) {
                case DIED -> Theme.DANGER;
                case FOUND -> Theme.SUCCESS;
                default -> route.colour;
            };
            int size = event == ReplayEvent.DIED || event == ReplayEvent.FOUND ? 3 : 2;
            Theme.fill(graphics, route.xs[i] - size, route.ys[i] - size, route.xs[i] + size, route.ys[i] + size, colour);
        }

        PlayerHead.draw(graphics, route.name(), route.view.uuid(), route.xs[shown] - 4, route.ys[shown] - 4, 8);
    }

    private void line(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int colour) {
        int dx = x1 - x0;
        int dy = y1 - y0;
        int steps = Math.min(512, Math.max(Math.abs(dx), Math.abs(dy)));
        if (steps == 0) {
            Theme.fill(graphics, x0, y0, x0 + 1, y0 + 1, colour);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int x = x0 + dx * i / steps;
            int y = y0 + dy * i / steps;
            Theme.fill(graphics, x, y, x + 1, y + 1, colour);
        }
    }

    private void drawScaleBar(GuiGraphicsExtractor graphics) {
        int blocks = niceRound(80.0 / Math.max(0.0001, this.projectedScale));
        int pixels = (int) Math.round(blocks * this.projectedScale);
        if (pixels < 8 || pixels > this.bodyW - 40) return;
        int x = this.bodyX + 10;
        int y = this.bodyY + this.bodyH - 12;
        Theme.fill(graphics, x, y, x + pixels, y + 1, Theme.TEXT_FAINT);
        graphics.text(this.font, Plural.of(blocks, "vivet.replay.blocks"), x, y - 10, Theme.TEXT_FAINT);
    }

    private static int niceRound(double value) {
        double power = Math.pow(10, Math.floor(Math.log10(Math.max(1.0, value))));
        double scaled = value / power;
        double step = scaled >= 5 ? 5 : scaled >= 2 ? 2 : 1;
        return (int) Math.max(1, step * power);
    }

    private void drawGear(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.routes.isEmpty()) return;
        Route route = this.routes.get(Math.min(this.gearIndex, this.routes.size() - 1));
        ReplayTrace.Sample now = route.trace().at(this.playhead);

        int x = this.bodyX + (this.bodyW - GearPicture.WIDTH) / 2;
        int y = this.bodyY + Math.max(0, (this.bodyH - GearPicture.HEIGHT) / 2) + (this.routes.size() > 1 ? 8 : 0);
        this.hovered = GearPicture.draw(graphics, this.font, x, y, now, route.name(), route.view.uuid(), mouseX, mouseY);
    }

    private void drawLines(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        drawBodyBox(graphics);
        if (this.lines.isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("vivet.replay.no_events"),
                    this.bodyX + this.bodyW / 2, this.bodyY + this.bodyH / 2 - 4, Theme.TEXT_FAINT);
            return;
        }

        int current = lineAt(this.playhead);
        int visible = Math.max(1, (this.bodyH - 8) / LINE_H);
        if (this.playing && System.currentTimeMillis() - this.scrolledAt > 3000L && current >= 0) {
            int wanted = Math.max(0, current - visible / 2);
            this.linesScroll = Math.min(wanted, Math.max(0, this.lines.size() - visible));
        }
        this.linesScroll = Math.max(0, Math.min(this.linesScroll, Math.max(0, this.lines.size() - visible)));

        int timeW = this.font.width("00:00.00");
        for (ReplayWords.EventLine each : this.lines) {
            timeW = Math.max(timeW, this.font.width(Time.formatPrecise(each.centis())));
        }
        timeW += 6;
        int nameW = 0;
        if (this.routes.size() > 1) {
            for (Route route : this.routes) nameW = Math.max(nameW, this.font.width(route.name()));
            nameW += 6;
        }

        Clip.on(graphics, this.bodyX + 1, this.bodyY + 1, this.bodyX + this.bodyW - 1, this.bodyY + this.bodyH - 1);
        try {
            int y = this.bodyY + 4;
            for (int i = this.linesScroll; i < this.lines.size() && i < this.linesScroll + visible; i++) {
                ReplayWords.EventLine line = this.lines.get(i);
                boolean past = line.centis() <= this.playhead;
                boolean over = mouseX >= this.bodyX && mouseX < this.bodyX + this.bodyW
                        && mouseY >= y - 1 && mouseY < y + LINE_H - 1;
                if (i == current || over) {
                    Theme.fill(graphics, this.bodyX + 2, y - 1, this.bodyX + this.bodyW - 2, y + LINE_H - 1,
                            Theme.withAlpha(0xFFFFFFFF, over ? 0x1C : 0x10));
                }
                int x = this.bodyX + 6;
                graphics.text(this.font, Component.literal(Time.formatPrecise(line.centis())), x, y,
                        past ? Theme.TEXT_MUTED : Theme.TEXT_FAINT);
                x += timeW;
                if (nameW > 0) {
                    Route route = this.routes.get(line.who());
                    graphics.text(this.font, Component.literal(route.name()), x, y,
                            past ? route.colour : Theme.fade(route.colour, 0.45F));
                    x += nameW;
                }
                String text = this.font.plainSubstrByWidth(line.text().getString(), Math.max(0, this.bodyX + this.bodyW - 6 - x));
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

    private boolean clickLine(int mouseX, int mouseY) {
        if (this.view != View.EVENTS || this.lines.isEmpty()) return false;
        if (mouseX < this.bodyX || mouseX >= this.bodyX + this.bodyW) return false;
        if (mouseY < this.bodyY + 3 || mouseY >= this.bodyY + this.bodyH) return false;
        int index = this.linesScroll + (mouseY - (this.bodyY + 3)) / LINE_H;
        if (index < 0 || index >= this.lines.size()) return true;
        this.playhead = Math.max(0, Math.min(duration(), this.lines.get(index).centis()));
        this.finishedAt = 0L;
        this.scrolledAt = System.currentTimeMillis();
        Cues.tick();
        return true;
    }

    private void drawScrubber(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int[] bar = scrubberBounds();
        Theme.roundedRect(graphics, bar[0], bar[1], bar[2], bar[3], Theme.PANEL_SUNKEN);
        Theme.roundedOutline(graphics, bar[0], bar[1], bar[2], bar[3], Theme.BORDER);

        float done = Math.max(0.0F, Math.min(1.0F, this.playhead / (float) duration()));
        int filled = (int) (done * (bar[2] - 2));
        if (filled > 0) Theme.roundedRect(graphics, bar[0] + 1, bar[1] + 1, filled, bar[3] - 2, Theme.ACCENT);

        for (Route route : this.routes) {
            for (ReplayTrace.Sample sample : route.trace().samples()) {
                ReplayEvent event = sample.event();
                if (event == ReplayEvent.SAMPLE || event == ReplayEvent.START) continue;
                int at = bar[0] + 1 + (int) ((sample.centis() / (float) duration()) * (bar[2] - 2));
                int colour = switch (event) {
                    case DIED -> Theme.DANGER;
                    case FOUND -> Theme.SUCCESS;
                    default -> route.colour;
                };
                Theme.fill(graphics, at, bar[1] - 2, at + 1, bar[1] + bar[3] + 2, colour);
                if (this.markTip == null && Math.abs(mouseX - at) <= 2
                        && mouseY >= bar[1] - 4 && mouseY <= bar[1] + bar[3] + 4) {
                    Component words = ReplayWords.describe(sample);
                    if (words != null) {
                        this.markTip = Tip.lines();
                        this.markTip.add(new Tip.Line(Component.literal(Time.formatPrecise(sample.centis())), Theme.TEXT_FAINT));
                        this.markTip.add(new Tip.Line(this.routes.size() > 1
                                ? Component.literal(route.name() + " ").append(words) : words, Theme.TEXT));
                    }
                }
            }
        }

        int handle = bar[0] + 1 + filled;
        Theme.fill(graphics, handle - 1, bar[1] - 3, handle + 2, bar[1] + bar[3] + 3, Theme.TEXT);
    }

    private int[] scrubberBounds() {
        return new int[] { this.panelX + PADDING, this.bodyY + this.bodyH + SCRUB_DROP, this.panelW - PADDING * 2, SCRUB_H };
    }

    private void drawFooter(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        boolean versus = this.routes.size() > 1;
        this.overRow = -1;

        for (int i = 0; i < this.routes.size(); i++) {
            Route route = this.routes.get(i);
            int[] row = rowBounds(i);
            int x = row[0];
            int y = row[1];

            if (versus) {
                boolean watched = i == this.pov;
                boolean over = !watched && in(mouseX, mouseY, row[0], row[1], row[2], row[3]);
                if (over) this.overRow = i;

                Theme.roundedBox(graphics, x, y, row[2], row[3], 4,
                        watched ? Theme.mix(Theme.PLATE_LIT, route.colour, 0.18F)
                                : over ? Theme.PLATE_LIT : Theme.PLATE);
                Theme.roundedBoxOutline(graphics, x, y, row[2], row[3], 4,
                        watched ? Theme.withAlpha(route.colour, 0xDD)
                                : over ? Theme.PLATE_EDGE_LIT : Theme.PLATE_EDGE);
                x += 5;
            }

            int text = y + (row[3] - this.font.lineHeight) / 2 + 1;
            int face = PlayerHead.framedSize(ROW_FACE);
            PlayerHead.drawFramed(graphics, route.name(), route.view.uuid(), x, y + (row[3] - face) / 2,
                    ROW_FACE, versus ? route.colour : Theme.BORDER);
            int nameX = x + face + 6;
            Component name = Component.literal(route.name());
            graphics.text(this.font, name, nameX, text, versus ? route.colour : Theme.TEXT);
            int after = nameX + this.font.width(name) + 6;
            String clock = Time.formatPrecise(route.trace().endCentis());
            graphics.text(this.font, Component.literal(clock), after, text, Theme.TEXT_FAINT);

            if (versus && !myUuid().isEmpty() && !myUuid().equals(route.view.uuid())) {
                Component opponent = Component.translatable("vivet.replay.opponent");
                graphics.text(this.font, opponent, row[0] + row[2] - 6 - this.font.width(opponent), text,
                        i == this.pov ? Theme.TEXT_MUTED : Theme.TEXT_FAINT);
            }
        }

        Long seed = this.replay.seed();
        if (seed != null && !this.match.setSeed) {
            int[] first = rowBounds(0);
            Component text = Component.translatable("vivet.replay.seed", String.valueOf(seed));
            int at = this.panelX + this.panelW - PADDING - this.font.width(text);
            int y = first[1] + (first[3] - this.font.lineHeight) / 2 + 1;
            if (at > first[0] + first[2] + 8) graphics.text(this.font, text, at, y, Theme.TEXT_FAINT);
        }
    }

    private int[] rowBounds(int index) {
        int x = this.panelX + PADDING;
        int y = this.bodyY + this.bodyH + FOOT_DROP - 4 + index * PLAYER_ROW_STEP;
        int width = 0;
        for (Route route : this.routes) {
            int wanted = 5 + PlayerHead.framedSize(ROW_FACE) + 6 + this.font.width(route.name()) + 6
                    + this.font.width(Time.formatPrecise(route.trace().endCentis())) + 12
                    + this.font.width(Component.translatable("vivet.replay.opponent")) + 8;
            width = Math.max(width, wanted);
        }
        width = Math.min(this.panelW - PADDING * 2, Math.max(180, width));
        return new int[] { x, y, width, PLAYER_ROW_H };
    }

    private void watchPov(int index) {
        if (index == this.pov || index < 0 || index >= this.routes.size()) return;
        this.pov = index;
        this.gearIndex = index;
        Cues.watchSwitched();
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
        this.overBack = in(scaled(mouseX), scaled(mouseY), this.panelX + this.panelW + RAIL_GAP, this.panelY,
                UiButtons.SIDE, UiButtons.SIDE);
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int x = scaled(event.x());
        int y = scaled(event.y());
        if (in(x, y, this.panelX + this.panelW + RAIL_GAP, this.panelY, UiButtons.SIDE, UiButtons.SIDE)) {
            onClose();
            return true;
        }
        if (grabScrubber(x, y)) return true;
        if (this.routes.size() > 1) {
            for (int i = 0; i < this.routes.size(); i++) {
                int[] row = rowBounds(i);
                if (i != this.pov && in(x, y, row[0], row[1], row[2], row[3])) {
                    watchPov(i);
                    return true;
                }
            }
        }
        if (super.mouseClicked(event, doubleClick)) return true;
        if (clickLine(x, y)) return true;
        return in(x, y, this.panelX, this.panelY, this.panelW, this.panelH);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.scrubbing) {
            seekTo(event.x() / uiScale());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.scrubbing = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (this.view == View.EVENTS) {
            this.linesScroll = Math.max(0, this.linesScroll - (int) Math.signum(deltaY) * 3);
            this.scrolledAt = System.currentTimeMillis();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    private boolean grabScrubber(double mouseX, double mouseY) {
        int[] bar = scrubberBounds();
        if (mouseX < bar[0] - 4 || mouseX > bar[0] + bar[2] + 4) return false;
        if (mouseY < bar[1] - 6 || mouseY > bar[1] + bar[3] + 6) return false;
        this.scrubbing = true;
        seekTo(mouseX);
        return true;
    }

    private void seekTo(double mouseX) {
        int[] bar = scrubberBounds();
        float where = (float) ((mouseX - bar[0] - 1) / Math.max(1, bar[2] - 2));
        this.playhead = Math.max(0, Math.min(duration(), Math.round(where * duration())));
        this.finishedAt = 0L;
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
