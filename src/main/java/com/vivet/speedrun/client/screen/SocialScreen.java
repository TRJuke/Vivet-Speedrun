package com.vivet.speedrun.client.screen;

import com.vivet.speedrun.client.Cues;
import com.vivet.speedrun.client.ui.Cosmetics;
import com.vivet.speedrun.client.ui.NameLabel;
import com.vivet.speedrun.client.ui.PlayerHead;
import com.vivet.speedrun.client.ui.RankTitle;
import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.client.ui.Tip;
import com.vivet.speedrun.client.ui.UiButtons;
import com.vivet.speedrun.media.MediaImages;
import com.vivet.speedrun.p2p.Hub;
import com.vivet.speedrun.p2p.MojangAuth;
import com.vivet.speedrun.p2p.Social;
import com.vivet.speedrun.session.Session;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SocialScreen extends VivetScreen {
    private static final int MAX_WIDTH = 396;
    private static final int MIN_WIDTH = 244;

    private static final int MARGIN_X = 6;
    private static final int MARGIN_Y = 8;

    private static final int RAIL_GAP = 7;

    private static final int RAIL_PITCH = UiButtons.SIDE + 5;

    private static final int PADDING = 12;
    private static final int PANEL_R = 8;

    private static final int ROW_H = 30;
    private static final int ROW_GAP = 4;
    private static final int ROW_R = 5;
    private static final int FACE = 24;

    private static final int BTN_GAP = 3;

    private static final int TITLE_H = 12;
    private static final int TITLE_GAP = 5;

    private static final int SECTION_GAP = 11;

    private static final int BAR_ROOM = 10;
    private static final int BAR_W = 4;
    private static final int BAR_GRAB = 7;

    private static final int LANE_ROWS = 5;

    private static final int MAX_ROWS = 20;

    private static final int BACKDROP_TINT = 0xFF7A7A7A;

    private static final int BACKDROP_WASH = 0x88000000;

    private static final int DOT_R = 3;

    private enum Page {
        SEARCH, FRIENDS, REQUESTS, BLOCKED, LIVE;

        private UiButtons.Kind icon() {
            return switch (this) {
                case SEARCH -> UiButtons.Kind.SEARCH;
                case FRIENDS -> UiButtons.Kind.PEOPLE;
                case REQUESTS -> UiButtons.Kind.PERSON_PLUS;
                case BLOCKED -> UiButtons.Kind.BLOCK;
                case LIVE -> UiButtons.Kind.WATCH;
            };
        }

        private String tip() {
            return "vivet.social.tip." + name().toLowerCase(Locale.ROOT);
        }
    }

    private static List<Page> pages() {
        return Hub.isStaff() ? List.of(Page.values())
                : List.of(Page.SEARCH, Page.FRIENDS, Page.REQUESTS, Page.BLOCKED);
    }

    private enum Lane {
        FRIENDS, FOLLOWING, INCOMING, OUTGOING, BLOCKED, RESULTS, LIVE;

        private String title() {
            return "vivet.social.section." + name().toLowerCase(Locale.ROOT);
        }

        private String empty() {
            return "vivet.social.no_" + name().toLowerCase(Locale.ROOT);
        }
    }

    private record Btn(UiButtons.Kind kind, String action, String tip, boolean live) {
    }

    private static final class Section {
        private final Lane lane;
        private final List<Social.Person> people = new ArrayList<>();

        private int titleY;
        private int listY;
        private int rows;

        private Section(Lane lane) {
            this.lane = lane;
        }

        private int height() {
            return this.rows * (ROW_H + ROW_GAP) - ROW_GAP;
        }

        private int maxScroll() {
            return Math.max(0, this.people.size() - this.rows);
        }
    }

    private static final String INVITE = "invite";
    private static final String JOIN = "join";

    private static final String MESSAGE = "message";

    private static final String WATCH = "watch";

    private final java.util.Map<String, com.vivet.speedrun.p2p.Watching.Match> liveMatchOf =
            new java.util.HashMap<>();

    private long builtLive = -1L;

    private final Screen parent;

    private Page page = Page.FRIENDS;

    private final int[] scrolls = new int[Lane.values().length];

    private final List<Section> sections = new ArrayList<>();

    private EditBox searchBox;

    private long builtFrom = -1L;
    private long builtSearch = -1L;

    private long builtNotices = -1L;

    private Page overRail;
    private boolean overBack;
    private Section overSection;
    private int overRow = -1;
    private int overBtn = -1;

    private Section dragging;
    private Section overBarSection;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;

    private int rowX;
    private int rowW;
    private int barX;

    private int fieldY;

    public SocialScreen(Screen parent) {
        super(Component.translatable("vivet.screen.social"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        Social.refresh();

        int side = UiButtons.SIDE + RAIL_GAP;
        this.panelW = Math.max(MIN_WIDTH,
                Math.min(MAX_WIDTH, this.width - MARGIN_X * 2 - side * 2));
        this.panelX = (this.width - this.panelW) / 2;

        this.rowX = this.panelX + PADDING;
        this.rowW = this.panelW - PADDING * 2 - BAR_ROOM;
        this.barX = this.panelX + this.panelW - PADDING - BAR_W;

        buildSections();

        int tallest = this.height - MARGIN_Y * 2;

        this.panelH = tallestPanel(tallest);

        if (!pages().contains(this.page)) this.page = Page.FRIENDS;

        int rail = RAIL_PITCH * (pages().size() - 1) + UiButtons.SIDE;
        if (tallest >= rail) this.panelH = Math.max(this.panelH, rail);

        this.panelY = Math.max(MARGIN_Y, (this.height - this.panelH) / 2);

        int lanes = this.sections.size();
        int field = this.page == Page.SEARCH ? ROW_H + TITLE_GAP : 0;

        int furniture = field + lanes * (TITLE_H + TITLE_GAP) + (lanes - 1) * SECTION_GAP;

        int perLane = Math.max(ROW_H, (this.panelH - PADDING * 2 - furniture) / lanes);
        int rows = Math.max(1, Math.min(MAX_ROWS, (perLane + ROW_GAP) / (ROW_H + ROW_GAP)));
        for (Section section : this.sections) section.rows = rows;

        int content = furniture + lanes * (rows * (ROW_H + ROW_GAP) - ROW_GAP);
        int y = this.panelY + Math.max(PADDING, (this.panelH - content) / 2);

        if (this.page == Page.SEARCH) {
            this.fieldY = y;
            y += ROW_H + TITLE_GAP;
        }

        for (Section section : this.sections) {
            section.titleY = y;
            section.listY = y + TITLE_H + TITLE_GAP;
            y = section.listY + section.height() + SECTION_GAP;

            clampScroll(section);
        }

        buildSearch();
    }

    private int tallestPanel(int tallest) {
        int furniture = 2 * (TITLE_H + TITLE_GAP) + SECTION_GAP;
        int perLane = Math.max(ROW_H, (tallest - PADDING * 2 - furniture) / 2);

        int rows = Math.max(1, Math.min(LANE_ROWS, (perLane + ROW_GAP) / (ROW_H + ROW_GAP)));

        return PADDING * 2 + furniture + 2 * (rows * (ROW_H + ROW_GAP) - ROW_GAP);
    }

    private void buildSections() {
        this.sections.clear();
        this.builtFrom = Social.stamp();
        this.builtSearch = Social.searchStamp();
        this.builtNotices = com.vivet.speedrun.client.hud.Notices.stamp();
        this.builtLive = com.vivet.speedrun.p2p.Watching.stamp();

        for (Lane lane : lanesOf(this.page)) {
            Section section = new Section(lane);
            section.people.addAll(peopleOf(lane));
            this.sections.add(section);
        }
    }

    private static List<Lane> lanesOf(Page page) {
        return switch (page) {
            case FRIENDS -> List.of(Lane.FRIENDS, Lane.FOLLOWING);
            case REQUESTS -> List.of(Lane.INCOMING, Lane.OUTGOING);
            case BLOCKED -> List.of(Lane.BLOCKED);
            case SEARCH -> List.of(Lane.RESULTS);
            case LIVE -> List.of(Lane.LIVE);
        };
    }

    private List<Social.Person> peopleOf(Lane lane) {
        return switch (lane) {
            case FRIENDS -> Social.friends();
            case FOLLOWING -> Social.following();
            case INCOMING -> Social.incoming();
            case OUTGOING -> Social.outgoing();
            case BLOCKED -> Social.blocked();
            case RESULTS -> Social.results();
            case LIVE -> livePeople();
        };
    }

    private List<Social.Person> livePeople() {
        this.liveMatchOf.clear();
        List<Social.Person> people = new ArrayList<>();
        for (com.vivet.speedrun.p2p.Watching.Match match
                : com.vivet.speedrun.p2p.Watching.liveMatches()) {
            if (match == null || match.racers == null) continue;
            for (Social.Person racer : match.racers) {
                if (racer == null || racer.uuid == null) continue;
                people.add(racer);
                this.liveMatchOf.put(racer.uuid, match);
            }
        }
        return people;
    }

    private void buildSearch() {
        if (this.page != Page.SEARCH) {
            this.searchBox = null;
            return;
        }

        this.searchBox = new EditBox(this.font, this.rowX + 10,
                Theme.textTop(this.fieldY, ROW_H), this.rowW - 20, 10,
                Component.translatable("vivet.social.search"));
        this.searchBox.setBordered(false);
        this.searchBox.setMaxLength(16);
        this.searchBox.setTextColor(Theme.TEXT);
        this.searchBox.setHint(Component.translatable("vivet.social.search")
                .copy().withStyle(ChatFormatting.ITALIC));
        this.searchBox.setValue(Social.resultsFor());

        this.searchBox.setResponder(Social::typed);

        addRenderableWidget(this.searchBox);
        setInitialFocus(this.searchBox);
    }

    private void show(Page wanted) {
        if (wanted == Page.LIVE) {
            com.vivet.speedrun.p2p.Watching.forgetLive();
            com.vivet.speedrun.p2p.Watching.loadLive();
            if (this.page == wanted) {
                rebuild();
                Cues.confirm();
                return;
            }
        }

        if (this.page == wanted) return;

        this.page = wanted;

        if (wanted != Page.SEARCH) Social.clearSearch();

        rebuild();
        Cues.confirm();
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private void clampScroll(Section section) {
        int max = section.maxScroll();
        this.scrolls[section.lane.ordinal()] =
                Math.max(0, Math.min(max, this.scrolls[section.lane.ordinal()]));
    }

    private int scrollOf(Section section) {
        return this.scrolls[section.lane.ordinal()];
    }

    @Override
    public void tick() {
        super.tick();

        Social.refresh();
        Social.pump();

        for (Section section : this.sections) {
            for (Social.Person person : section.people) {
                MediaImages.prepare(Cosmetics.mediaOf(person.background));
            }
        }

        if (Social.stamp() != this.builtFrom || Social.searchStamp() != this.builtSearch
                || com.vivet.speedrun.client.hud.Notices.stamp() != this.builtNotices
                || (this.page == Page.LIVE
                        && com.vivet.speedrun.p2p.Watching.stamp() != this.builtLive)) {
            boolean focused = this.searchBox != null && this.searchBox.isFocused();
            String typed = this.searchBox == null ? "" : this.searchBox.getValue();

            rebuild();

            if (this.searchBox != null) {
                this.searchBox.setValue(typed);
                if (focused) setInitialFocus(this.searchBox);
            }
        }
    }

    @Override
    protected void drawScaled(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                              float partialTick) {
        Theme.fill(graphics, 0, 0, this.width, this.height, Theme.SCRIM);

        findHover(mouseX, mouseY);

        Theme.roundedBox(graphics, this.panelX, this.panelY, this.panelW, this.panelH, PANEL_R,
                Theme.PANEL);
        Theme.roundedBoxOutline(graphics, this.panelX, this.panelY, this.panelW, this.panelH,
                PANEL_R, Theme.BORDER_STRONG);

        drawRail(graphics);

        if (this.page == Page.SEARCH) drawField(graphics);

        if (MojangAuth.token() == null) {
            drawNothing(graphics, "vivet.social.signed_out", this.panelY + PADDING,
                    this.panelH - PADDING * 2);
        } else {
            for (Section section : this.sections) drawSection(graphics, section);
        }

        drawWidgets(graphics, mouseX, mouseY, partialTick);
        drawTip(graphics, mouseX, mouseY);
    }

    private void drawRail(GuiGraphicsExtractor graphics) {
        int x = this.panelX - RAIL_GAP - UiButtons.SIDE;
        int y = this.panelY;

        for (Page rail : pages()) {
            UiButtons.button(graphics, rail.icon(), x, y, this.overRail == rail, true,
                    this.page == rail);
            y += RAIL_PITCH;
        }

        UiButtons.button(graphics, UiButtons.Kind.BACK, this.panelX + this.panelW + RAIL_GAP,
                this.panelY, this.overBack, true, false);
    }

    private void drawField(GuiGraphicsExtractor graphics) {
        Theme.roundedBox(graphics, this.rowX, this.fieldY, this.rowW, ROW_H, ROW_R,
                Theme.PANEL_SUNKEN);
        Theme.roundedBoxOutline(graphics, this.rowX, this.fieldY, this.rowW, ROW_H, ROW_R,
                Social.searching() ? Theme.PLATE_EDGE_LIT : Theme.PLATE_EDGE);
    }

    private void drawSection(GuiGraphicsExtractor graphics, Section section) {
        int scroll = scrollOf(section);

        int ruleY = section.titleY + TITLE_H - 1;
        int textY = headingY(section, ruleY);

        graphics.text(this.font, Component.translatable(section.lane.title())
                        .copy().withStyle(ChatFormatting.BOLD),
                this.rowX, textY, Theme.TEXT, true);

        Component note = noteOf(section);
        if (note != null) {
            graphics.text(this.font, note,
                    this.rowX + this.rowW - this.font.width(note), textY,
                    Theme.SUCCESS, true);
        }

        Theme.divider(graphics, this.rowX, ruleY, this.rowW + BAR_ROOM);

        for (int slot = 0; slot < section.rows; slot++) {
            int y = section.listY + slot * (ROW_H + ROW_GAP);
            int index = scroll + slot;

            if (index >= section.people.size()) {
                Theme.roundedBoxOutline(graphics, this.rowX, y, this.rowW, ROW_H, ROW_R,
                        Theme.withAlpha(Theme.PLATE_EDGE, 0x44));
                continue;
            }

            drawRow(graphics, section, section.people.get(index), slot, y,
                    this.overSection == section && this.overRow == slot);
        }

        if (section.people.isEmpty()) {
            drawNothing(graphics, emptyKey(section.lane), section.listY, section.height());
        }

        drawBar(graphics, section);
    }

    private int headingY(Section section, int ruleY) {
        int index = this.sections.indexOf(section);
        int top;
        if (index > 0) {
            Section before = this.sections.get(index - 1);
            top = before.listY + before.height();
        } else if (this.page == Page.SEARCH) {
            top = this.fieldY + ROW_H;
        } else {
            top = this.panelY;
        }
        return Theme.textTop(top, ruleY - top);
    }

    private Component noteOf(Section section) {
        if (section.lane != Lane.FRIENDS) return null;

        int online = 0;
        for (Social.Person person : section.people) {
            if (Hub.isOnline(person.uuid)) online++;
        }
        return Component.translatable("vivet.social.online", online);
    }

    private void drawRow(GuiGraphicsExtractor graphics, Section section, Social.Person person,
                         int slot, int y, boolean over) {
        Theme.roundedBox(graphics, this.rowX, y, this.rowW, ROW_H, ROW_R,
                over ? Theme.PLATE_LIT : Theme.PANEL_SUNKEN);

        drawBackdrop(graphics, person, y);

        Theme.roundedBoxOutline(graphics, this.rowX, y, this.rowW, ROW_H, ROW_R,
                over ? Theme.PLATE_EDGE_LIT : Theme.PLATE_EDGE);

        String name = person.name == null ? "?" : person.name;
        int faceX = this.rowX + 3;
        int faceY = y + (ROW_H - FACE) / 2;

        PlayerHead.draw(graphics, name, person.uuid, faceX, faceY, FACE);

        if (Hub.isOnline(person.uuid)) drawLive(graphics, faceX + DOT_R, faceY + DOT_R);

        List<Btn> buttons = buttonsFor(section.lane, person);
        int right = this.rowX + this.rowW - 5;
        int btnY = y + (ROW_H - UiButtons.SIDE) / 2;

        for (int i = buttons.size() - 1; i >= 0; i--) {
            Btn btn = buttons.get(i);
            right -= UiButtons.SIDE;

            boolean hot = over && this.overBtn == i;

            UiButtons.draw(graphics, btn.kind(), right, btnY,
                    UiButtons.toneOf(btn.kind(), hot, btn.live()));
            right -= BTN_GAP;
        }

        int textX = faceX + FACE + 7;
        int textY = y + (ROW_H - this.font.lineHeight) / 2 + 1;
        int room = right - textX - 4;

        drawFacts(graphics, section, person, name, textX, textY, room);
    }

    private void drawFacts(GuiGraphicsExtractor graphics, Section section, Social.Person person,
                           String name, int x, int y, int room) {
        List<Component> facts = new ArrayList<>();

        com.vivet.speedrun.p2p.Watching.Match live = section.lane == Lane.LIVE
                ? this.liveMatchOf.get(person.uuid) : null;
        if (live != null) {
            facts.add(Component.translatable("ranked".equals(live.kind) ? "vivet.watch.mode.ranked"
                    : "solo".equals(live.kind) ? "vivet.watch.mode.solo"
                    : "vivet.watch.mode.unranked"));
            com.vivet.speedrun.game.Difficulty band = live.band == null ? null
                    : com.vivet.speedrun.game.Difficulty.byId(live.band);
            if (band != null) facts.add(Component.literal(band.displayName()));
            long going = com.vivet.speedrun.p2p.Watching.runningFor(live);
            if (going > 0) {
                facts.add(Component.literal(com.vivet.speedrun.game.Time.format((int) (going / 1000L))));
            }
        } else if (person.level != null && person.level > 0) {
            facts.add(Component.translatable("vivet.social.level", person.level));
        }
        if (section.lane == Lane.FOLLOWING && person.place != null && person.place > 0) {
            facts.add(Component.translatable("vivet.social.place", person.place));
        }

        int tail = 0;
        for (Component fact : facts) tail += this.font.width(fact) + RULE_ROOM;

        RankTitle.remember(name, person.uuid, person.rank, person.placed);

        NameLabel label = person.label == null || person.label.isBlank()
                ? NameLabel.of(name) : NameLabel.byId(person.label);

        int taken = NameLabel.drawTaggedScrolling(graphics, this.font, label, name, x, y,
                Math.max(12, room - tail), Theme.TEXT);

        int at = x + taken;
        for (Component fact : facts) {
            rule(graphics, at + RULE_ROOM / 2, y);
            at += RULE_ROOM;
            graphics.text(this.font, fact, at, y, Theme.TEXT_MUTED, true);
            at += this.font.width(fact);
        }
    }

    private static final int RULE_ROOM = 11;

    private void rule(GuiGraphicsExtractor graphics, int x, int y) {
        Theme.fill(graphics, x, y - 1, x + 1, y + this.font.lineHeight - 1,
                Theme.withAlpha(0xFFFFFFFF, 0x33));
    }

    private void drawBackdrop(GuiGraphicsExtractor graphics, Social.Person person, int y) {
        String media = Cosmetics.mediaOf(person.background);
        if (media == null) return;

        if (!com.vivet.speedrun.client.ui.RoundedPicture.draw(graphics, this.rowX, y, this.rowW,
                ROW_H, ROW_R, () -> MediaImages.cover(graphics, media, this.rowX, y, this.rowW,
                        ROW_H, BACKDROP_TINT))) {
            return;
        }

        Theme.roundedBox(graphics, this.rowX, y, this.rowW, ROW_H, ROW_R, BACKDROP_WASH);
    }

    private void drawLive(GuiGraphicsExtractor graphics, int cx, int cy) {
        int ring = DOT_R + 2;
        Theme.roundedBox(graphics, cx - ring, cy - ring, ring * 2, ring * 2, ring,
                Theme.PANEL_SUNKEN);
        Theme.roundedBox(graphics, cx - DOT_R, cy - DOT_R, DOT_R * 2, DOT_R * 2, DOT_R,
                Theme.SUCCESS);
    }

    private void drawBar(GuiGraphicsExtractor graphics, Section section) {
        int top = section.listY;
        int height = section.height();

        Theme.roundedBox(graphics, this.barX, top, BAR_W, height, BAR_W / 2,
                Theme.withAlpha(0xFFFFFFFF, 0x12));

        boolean lit = this.dragging == section || this.overBarSection == section;
        int thumb = thumbHeight(section, height);

        Theme.roundedBox(graphics, this.barX, top + thumbOffset(section, height, thumb),
                BAR_W, thumb, BAR_W / 2,
                Theme.withAlpha(0xFFFFFFFF, lit ? 0xBB : 0x55));
    }

    private int thumbHeight(Section section, int height) {
        int total = Math.max(section.rows, section.people.size());
        return Math.max(16, height * section.rows / Math.max(1, total));
    }

    private int thumbOffset(Section section, int height, int thumb) {
        int max = section.maxScroll();
        return max <= 0 ? 0 : (height - thumb) * scrollOf(section) / max;
    }

    private void drawNothing(GuiGraphicsExtractor graphics, String key, int top, int height) {
        List<String> lines = wrap(Component.translatable(key).getString(), this.rowW - 16);

        int step = this.font.lineHeight + 2;
        int y = top + (height - lines.size() * step) / 2;

        for (String line : lines) {
            graphics.text(this.font, Component.literal(line),
                    this.rowX + (this.rowW - this.font.width(line)) / 2, y,
                    Theme.TEXT_FAINT, false);
            y += step;
        }
    }

    private String emptyKey(Lane lane) {
        if (lane == Lane.LIVE) {
            return com.vivet.speedrun.p2p.Watching.liveKnown() ? lane.empty() : "vivet.social.loading";
        }
        if (lane == Lane.RESULTS) {
            if (Social.searching()) return "vivet.social.searching";
            return Social.resultsFor().isEmpty()
                    ? "vivet.social.find_hint" : "vivet.social.no_players";
        }
        return Social.known() ? lane.empty() : "vivet.social.loading";
    }

    private List<String> wrap(String text, int room) {
        List<String> lines = new ArrayList<>();
        if (room <= 0) return lines;

        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (word.isEmpty()) continue;

            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && this.font.width(candidate) > room) {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(candidate);
            }
        }

        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    private List<Btn> buttonsFor(Lane lane, Social.Person person) {
        List<Btn> buttons = new ArrayList<>();
        String uuid = person == null ? null : person.uuid;

        switch (lane) {
            case FRIENDS -> {
                if (Hub.isWatchable(uuid)) {
                    buttons.add(new Btn(UiButtons.Kind.WATCH, WATCH, "vivet.social.act.watch",
                            canWatch()));
                }

                if (inviteFrom(uuid) != null) {
                    buttons.add(new Btn(UiButtons.Kind.PLUS, JOIN, "vivet.social.act.join",
                            !Session.isActive()));
                } else {
                    buttons.add(new Btn(UiButtons.Kind.PLUS, INVITE, "vivet.social.act.invite",
                            Hub.isOnline(uuid) && canInvite()));
                }

                buttons.add(new Btn(UiButtons.Kind.MESSAGE, MESSAGE, "vivet.social.act.message",
                        true));

                buttons.add(new Btn(UiButtons.Kind.REMOVE, "unadd", "vivet.social.act.unadd",
                        true));
                buttons.add(followToggle(uuid));
                buttons.add(blockToggle(uuid));
            }
            case FOLLOWING -> {
                buttons.add(new Btn(UiButtons.Kind.MINUS, "unfollow", "vivet.social.act.unfollow",
                        true));
                buttons.add(blockToggle(uuid));
            }
            case INCOMING -> {
                buttons.add(new Btn(UiButtons.Kind.ADD, "accept", "vivet.social.act.accept", true));
                buttons.add(new Btn(UiButtons.Kind.REMOVE, "reject", "vivet.social.act.reject",
                        true));
                buttons.add(blockToggle(uuid));
            }
            case OUTGOING -> {
                buttons.add(new Btn(UiButtons.Kind.REMOVE, "cancel", "vivet.social.act.cancel",
                        true));
                buttons.add(blockToggle(uuid));
            }
            case BLOCKED -> buttons.add(blockToggle(uuid));

            case LIVE -> buttons.add(new Btn(UiButtons.Kind.WATCH, WATCH, "vivet.social.act.watch",
                    canWatch()));
            case RESULTS -> {
                if (holds(Social.blocked(), uuid)) {
                    buttons.add(blockToggle(uuid));
                    return buttons;
                }

                if (holds(Social.friends(), uuid)) {
                    buttons.add(new Btn(UiButtons.Kind.REMOVE, "unadd", "vivet.social.act.unadd",
                            true));
                } else if (holds(Social.outgoing(), uuid)) {
                    buttons.add(new Btn(UiButtons.Kind.REMOVE, "cancel", "vivet.social.act.cancel",
                            true));
                } else if (holds(Social.incoming(), uuid)) {
                    buttons.add(new Btn(UiButtons.Kind.ADD, "accept", "vivet.social.act.accept",
                            true));
                } else {
                    buttons.add(new Btn(UiButtons.Kind.ADD, "add", "vivet.social.act.add", true));
                }

                buttons.add(followToggle(uuid));
                buttons.add(blockToggle(uuid));
            }
        }

        return buttons;
    }

    private Btn blockToggle(String uuid) {
        boolean blocked = holds(Social.blocked(), uuid);

        return new Btn(UiButtons.blockSign(blocked), blocked ? "unblock" : "block",
                blocked ? "vivet.social.act.unblock" : "vivet.social.act.block", true);
    }

    private static Btn followToggle(String uuid) {
        return holds(Social.following(), uuid)
                ? new Btn(UiButtons.Kind.MINUS, "unfollow", "vivet.social.act.unfollow", true)
                : new Btn(UiButtons.Kind.PLUS, "follow", "vivet.social.act.follow", true);
    }

    private boolean canWatch() {
        return this.minecraft != null && this.minecraft.level == null
                && !com.vivet.speedrun.client.Spectating.isActive();
    }

    private static Hub.Invite inviteFrom(String uuid) {
        if (uuid == null) return null;

        for (Hub.Invite invite : Hub.invites()) {
            if (uuid.equals(invite.uuid())) return invite;
        }
        return null;
    }

    private boolean canInvite() {
        if (Session.isRanked() || Session.isPublicMatch()) return false;
        if (!Session.isActive()) return true;

        return !Session.roomCode().isBlank();
    }

    private void invite(Social.Person person) {
        String uuid = person == null ? null : person.uuid;
        if (uuid == null) return;

        Cues.confirm();

        String open = Session.roomCode();
        if (!open.isBlank()) {
            Hub.invite(uuid, open);
            return;
        }

        Session.startHosting(
                code -> this.minecraft.execute(() -> {
                    Hub.invite(uuid, code);
                    this.minecraft.gui.setScreen(new SessionScreen(this.parent));
                }),
                reason -> this.minecraft.execute(Cues::deny));
    }

    private void joinRoom(Social.Person person) {
        Hub.Invite invite = inviteFrom(person == null ? null : person.uuid);
        if (invite == null) return;

        if (Session.isActive()) {
            Cues.nothingHappened();
            return;
        }

        Cues.confirm();
        Hub.dropInvite(invite);
        com.vivet.speedrun.client.hud.Notices.dismiss(invite.uuid());

        Session.join(invite.room(),
                () -> this.minecraft.execute(
                        () -> this.minecraft.gui.setScreen(new SessionScreen(this.parent))),
                reason -> this.minecraft.execute(Cues::deny));
    }

    private static boolean holds(List<Social.Person> list, String uuid) {
        if (uuid == null) return false;
        for (Social.Person person : list) {
            if (person != null && uuid.equals(person.uuid)) return true;
        }
        return false;
    }

    private void drawTip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        String key = null;

        if (this.overRail != null) {
            key = this.overRail.tip();
        } else if (this.overBack) {
            key = "vivet.social.tip.back";
        } else if (this.overSection != null && this.overRow >= 0) {
            Social.Person person = personAt(this.overSection, this.overRow);

            if (person != null && this.overBtn >= 0) {
                List<Btn> buttons = buttonsFor(this.overSection.lane, person);
                if (this.overBtn < buttons.size()) key = buttons.get(this.overBtn).tip();
            } else if (person != null) {
                key = "vivet.social.tip.card";
            }
        }

        if (key == null) return;

        List<Tip.Line> lines = Tip.lines();
        Tip.wrap(lines, Component.translatable(key), Theme.TEXT_MUTED, this.font);
        Tip.draw(graphics, this.font, lines, mouseX, mouseY, this.width, this.height);
    }

    private void findHover(int mouseX, int mouseY) {
        this.overRail = null;
        this.overBack = false;
        this.overSection = null;
        this.overRow = -1;
        this.overBtn = -1;
        this.overBarSection = barAt(mouseX, mouseY);

        int railX = this.panelX - RAIL_GAP - UiButtons.SIDE;
        int y = this.panelY;

        for (Page rail : pages()) {
            if (in(mouseX, mouseY, railX, y, UiButtons.SIDE, UiButtons.SIDE)) this.overRail = rail;
            y += RAIL_PITCH;
        }

        this.overBack = in(mouseX, mouseY, this.panelX + this.panelW + RAIL_GAP, this.panelY,
                UiButtons.SIDE, UiButtons.SIDE);

        if (this.overRail != null || this.overBack) return;

        for (Section section : this.sections) {
            for (int slot = 0; slot < section.rows; slot++) {
                int rowY = section.listY + slot * (ROW_H + ROW_GAP);
                if (!in(mouseX, mouseY, this.rowX, rowY, this.rowW, ROW_H)) continue;

                Social.Person person = personAt(section, slot);
                if (person == null) return;

                this.overSection = section;
                this.overRow = slot;
                this.overBtn = buttonAt(section, person, rowY, mouseX, mouseY);
                return;
            }
        }
    }

    private int buttonAt(Section section, Social.Person person, int rowY, int mouseX, int mouseY) {
        List<Btn> buttons = buttonsFor(section.lane, person);
        int right = this.rowX + this.rowW - 5;
        int btnY = rowY + (ROW_H - UiButtons.SIDE) / 2;

        for (int i = buttons.size() - 1; i >= 0; i--) {
            right -= UiButtons.SIDE;
            if (in(mouseX, mouseY, right, btnY, UiButtons.SIDE, UiButtons.SIDE)) return i;
            right -= BTN_GAP;
        }
        return -1;
    }

    private Social.Person personAt(Section section, int slot) {
        int index = scrollOf(section) + slot;
        return index >= 0 && index < section.people.size() ? section.people.get(index) : null;
    }

    private static boolean in(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private int scaled(double value) {
        return (int) Math.round(value / uiScale());
    }

    private Section barAt(int mouseX, int mouseY) {
        for (Section section : this.sections) {
            if (mouseX >= this.barX - BAR_GRAB && mouseX <= this.barX + BAR_W + BAR_GRAB
                    && mouseY >= section.listY && mouseY < section.listY + section.height()) {
                return section;
            }
        }
        return null;
    }

    private Section listAt(int mouseX, int mouseY) {
        for (Section section : this.sections) {
            if (mouseX >= this.panelX && mouseX < this.panelX + this.panelW
                    && mouseY >= section.listY && mouseY < section.listY + section.height()) {
                return section;
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int x = scaled(event.x());
        int y = scaled(event.y());

        findHover(x, y);

        if (this.overRail != null) {
            show(this.overRail);
            return true;
        }

        if (this.overBack) {
            Cues.confirm();
            onClose();
            return true;
        }

        if (this.overSection != null && this.overRow >= 0) {
            Social.Person person = personAt(this.overSection, this.overRow);
            if (person == null) return true;

            if (this.overBtn >= 0) {
                press(this.overSection, person, this.overBtn);
            } else {
                Cues.confirm();
                this.minecraft.gui.setScreen(new ProfileScreen(this, person.uuid));
            }
            return true;
        }

        Section bar = barAt(x, y);
        if (bar != null && bar.maxScroll() > 0) {
            this.dragging = bar;
            dragTo(bar, y);
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    private void press(Section section, Social.Person person, int index) {
        List<Btn> buttons = buttonsFor(section.lane, person);
        if (index >= buttons.size()) return;

        Btn btn = buttons.get(index);

        if (!btn.live()) {
            Cues.nothingHappened();
            return;
        }

        if (MESSAGE.equals(btn.action())) {
            Cues.confirm();
            this.minecraft.gui.setScreen(new ChatScreen(this, person.uuid));
        } else if (WATCH.equals(btn.action())) {
            com.vivet.speedrun.client.Spectating.start(this.minecraft, this, person.uuid, person.name);
        } else if (INVITE.equals(btn.action())) {
            invite(person);
        } else if (JOIN.equals(btn.action())) {
            joinRoom(person);
        } else {
            Cues.confirm();
            Social.act(btn.action(), person.uuid);
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.dragging = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.dragging != null) {
            dragTo(this.dragging, scaled(event.y()));
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    private void dragTo(Section section, int mouseY) {
        int height = section.height();
        int thumb = thumbHeight(section, height);
        int travel = Math.max(1, height - thumb);
        int max = section.maxScroll();

        int want = Math.max(0, Math.min(travel, mouseY - section.listY - thumb / 2));
        this.scrolls[section.lane.ordinal()] = Math.round(max * (float) want / travel);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        Section section = listAt(scaled(mouseX), scaled(mouseY));

        if (section != null && section.maxScroll() > 0) {
            int at = scrollOf(section) - (int) Math.signum(deltaY);
            this.scrolls[section.lane.ordinal()] =
                    Math.max(0, Math.min(section.maxScroll(), at));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE && this.page != Page.FRIENDS) {
            show(Page.FRIENDS);
            return true;
        }
        return super.keyPressed(event);
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
