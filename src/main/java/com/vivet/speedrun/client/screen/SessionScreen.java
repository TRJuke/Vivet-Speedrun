package com.vivet.speedrun.client.screen;

import com.vivet.speedrun.client.Cues;
import com.vivet.speedrun.client.ui.Clip;
import com.vivet.speedrun.client.ui.FlatButton;
import com.vivet.speedrun.client.ui.NameLabel;
import com.vivet.speedrun.client.ui.PlayerHead;
import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.client.ui.Tip;
import com.vivet.speedrun.client.ui.UiButtons;
import com.vivet.speedrun.config.ConfigManager;
import com.vivet.speedrun.config.ModConfig;
import com.vivet.speedrun.p2p.Hub;
import com.vivet.speedrun.p2p.InviteCode;
import com.vivet.speedrun.p2p.Social;
import com.vivet.speedrun.session.Racer;
import com.vivet.speedrun.session.Session;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SessionScreen extends VivetScreen {
    private static final int MAX_WIDTH = 380;
    private static final int GAP = 8;

    private static final int CARD_PAD = 10;
    private static final int RULE_Y = 19;

    private static final int ROOM_H = 58;
    private static final int CODE_Y = 28;
    private static final int CODE_H = 20;
    private static final int COPY_W = 58;

    private static final int LIST_H = 134;
    private static final int LIST_TOP = 24;
    private static final int ROW_H = 24;
    private static final int HEAD = 16;

    private static final int FOOT_H = 20;
    private static final int STATUS_H = 22;

    private static final int ACCESS_W = 110;
    private static final int SETTINGS_W = 70;
    private static final int START_W = 110;

    private final Screen parent;

    private enum Pending {
        NONE,
        HOST,
        JOIN,
    }

    private Pending pending = Pending.NONE;
    private String pendingCode = "";

    private enum Over {
        NONE,
        RACERS,
        FRIENDS,
    }

    private FlatButton startButton;

    private volatile String status = "";
    private volatile int statusColor = Theme.TEXT_MUTED;
    private volatile boolean working;

    private boolean starting;

    private final List<Social.Person> askable = new ArrayList<>();

    private int racerScroll;
    private int friendScroll;

    private Over over = Over.NONE;
    private boolean overBack;
    private int hoveredKick = -1;
    private int hoveredInvite = -1;

    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int listsY;
    private int footY;

    public SessionScreen(Screen parent) {
        super(Component.translatable("vivet.screen.session"));
        this.parent = parent;
    }

    public static SessionScreen creating(Screen parent) {
        SessionScreen screen = new SessionScreen(parent);
        screen.pending = Pending.HOST;
        return screen;
    }

    public static SessionScreen joining(Screen parent, String code) {
        SessionScreen screen = new SessionScreen(parent);
        screen.pending = Pending.JOIN;
        screen.pendingCode = code == null ? "" : code;
        return screen;
    }

    @Override
    protected void init() {
        super.init();

        this.panelWidth = Math.min(MAX_WIDTH, this.width - 16 - UiButtons.SIDE - 10);
        this.panelHeight = ROOM_H + GAP + LIST_H + GAP
                + (hosting() ? FOOT_H + GAP : 0) + STATUS_H;

        this.left = (this.width - this.panelWidth) / 2;
        this.top = Math.max(0, (this.height - this.panelHeight) / 2);
        this.listsY = this.top + ROOM_H + GAP;
        this.footY = this.listsY + LIST_H + GAP;

        buildControls();

        Session.bindStatus(this::showStatus);

        Social.refresh();

        runPending();
    }

    private void runPending() {
        Pending todo = this.pending;
        this.pending = Pending.NONE;

        switch (todo) {
            case HOST -> {
                if (Session.role() != Session.Role.HOST) startHosting();
            }
            case JOIN -> {
                String code = this.pendingCode;
                this.pendingCode = "";
                joinRoom(code);
            }
            default -> {
            }
        }
    }

    private boolean hosting() {
        return Session.role() == Session.Role.HOST;
    }

    private String roomCode() {
        String code = Session.currentRoom();
        return code == null ? "" : code;
    }

    private int backX() {
        return this.left + this.panelWidth + 10;
    }

    private int listWidth() {
        return hosting() ? (this.panelWidth - GAP) / 2 : this.panelWidth;
    }

    private int friendsX() {
        return this.left + listWidth() + GAP;
    }

    private int rowsVisible() {
        return (LIST_H - LIST_TOP - 4) / ROW_H;
    }

    private int rowY(int index) {
        return this.listsY + LIST_TOP + index * ROW_H;
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private void showStatus(String message) {
        this.status = message == null ? "" : message;
        this.statusColor = Theme.TEXT_MUTED;
    }

    private void buildControls() {
        addRenderableWidget(new FlatButton(
                this.left + this.panelWidth - CARD_PAD - COPY_W, this.top + CODE_Y,
                COPY_W, CODE_H,
                Component.translatable("vivet.button.copy"),
                this::copyInvite,
                FlatButton.Style.SECONDARY))
                .active = !roomCode().isEmpty();

        if (!hosting()) return;

        buildAccessButton();

        addRenderableWidget(new FlatButton(
                this.left + ACCESS_W + 6, this.footY, SETTINGS_W, FOOT_H,
                Component.translatable("vivet.button.settings"),
                () -> {
                    Session.bindStatus(null);
                    this.minecraft.gui.setScreen(new SettingsScreen(this));
                },
                FlatButton.Style.SECONDARY));

        this.startButton = addRenderableWidget(new FlatButton(
                this.left + this.panelWidth - START_W, this.footY, START_W, FOOT_H,
                Component.translatable("vivet.button.start_race"),
                this::startRace,
                FlatButton.Style.PRIMARY));
        this.startButton.active = Session.canHostStart();
    }

    private void buildAccessButton() {
        ModConfig config = ConfigManager.get();
        boolean friendsOnly = config.privateFriendsOnly;

        addRenderableWidget(new FlatButton(
                this.left, this.footY, ACCESS_W, FOOT_H,
                Component.translatable(friendsOnly
                        ? "vivet.session.friends_only" : "vivet.session.public"),
                () -> {
                    config.privateFriendsOnly = !config.privateFriendsOnly;
                    ConfigManager.save();
                    Cues.tick();

                    if (config.privateFriendsOnly) Social.refresh();
                    rebuild();
                },
                friendsOnly ? FlatButton.Style.READY : FlatButton.Style.SECONDARY));
    }

    private void startHosting() {
        this.working = true;
        this.status = "";

        Session.startHosting(
                code -> this.minecraft.execute(() -> {
                    this.working = false;
                    this.status = "";
                    rebuild();
                }),
                error -> this.minecraft.execute(() -> {
                    this.working = false;
                    this.status = error;
                    this.statusColor = Theme.DANGER;
                    rebuild();
                }));
    }

    private void joinRoom(String typed) {
        String code = InviteCode.normalize(typed);
        if (code == null) {
            this.status = "Invalid code. Codes look like VIVET-XXXXX-XXXXX";
            this.statusColor = Theme.DANGER;
            return;
        }

        this.working = true;
        this.status = "Joining " + code + "...";
        this.statusColor = Theme.TEXT_MUTED;

        Session.join(code,
                () -> this.minecraft.execute(() -> {
                    this.working = false;
                    this.status = "";
                    rebuild();
                }),
                error -> this.minecraft.execute(() -> {
                    this.working = false;
                    this.status = error;
                    this.statusColor = Theme.DANGER;
                    rebuild();
                }));
    }

    private void copyInvite() {
        String code = roomCode();
        if (code.isEmpty()) return;

        this.minecraft.keyboardHandler.setClipboard(code);
        Cues.confirm();
        this.status = "Copied!";
        this.statusColor = Theme.SUCCESS;
    }

    private void startRace() {
        if (!Session.canHostStart()) return;

        this.starting = true;
        Cues.confirm();
        Session.proposeNewMatch();
        leave();
    }

    private List<Racer> racers() {
        return Session.racers();
    }

    private void refreshAskable() {
        this.askable.clear();
        if (!hosting() || Session.roomCode().isEmpty()) return;

        List<String> here = new ArrayList<>();
        for (Racer racer : racers()) here.add(racer.name.toLowerCase(Locale.ROOT));

        for (Social.Person person : Social.friends()) {
            if (person == null || person.uuid == null || person.name == null) continue;
            if (!Hub.isOnline(person.uuid)) continue;
            if (here.contains(person.name.toLowerCase(Locale.ROOT))) continue;

            this.askable.add(person);
        }
    }

    private void kick(String name) {
        if (!Session.kick(name)) return;

        Cues.discarded();
        this.status = name + " was kicked.";
        this.statusColor = Theme.TEXT_MUTED;
        this.racerScroll = 0;
        rebuild();
    }

    private void invite(Social.Person person) {
        String room = Session.roomCode();
        if (person == null || person.uuid == null || room.isEmpty()) return;

        Hub.invite(person.uuid, room);
        Cues.confirm();
    }

    private boolean justInvited(String uuid) {
        return Hub.hasInvited(uuid);
    }

    private boolean canKick() {
        Session.Phase phase = Session.phase();
        return hosting() && phase != Session.Phase.RACING && phase != Session.Phase.LOADING;
    }

    @Override
    protected void drawScaled(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                              float partialTick) {
        Theme.fill(graphics, 0, 0, this.width, this.height, Theme.SCRIM);

        refreshAskable();
        clampScroll();
        findHover(mouseX, mouseY);

        drawRoomCard(graphics);
        drawPlayers(graphics);
        if (hosting()) drawFriends(graphics);
        drawStatus(graphics);

        UiButtons.button(graphics, UiButtons.Kind.BACK, backX(), this.top, this.overBack,
                true, false);

        refreshStartButton();
        drawWidgets(graphics, mouseX, mouseY, partialTick);
        drawTip(graphics, mouseX, mouseY);
    }

    private void card(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                      Component title, Component note, int noteColor) {
        Theme.roundedBox(graphics, x, y, width, height, 4, Theme.PANEL);
        Theme.roundedBoxOutline(graphics, x, y, width, height, 4, Theme.BORDER);

        graphics.text(this.font, title.copy().withStyle(ChatFormatting.BOLD),
                x + CARD_PAD, y + 7, Theme.TEXT);

        if (note != null) {
            graphics.text(this.font, note,
                    x + width - CARD_PAD - this.font.width(note), y + 7, noteColor);
        }

        Theme.fill(graphics, x + CARD_PAD, y + RULE_Y, x + width - CARD_PAD, y + RULE_Y + 1,
                Theme.BORDER);
    }

    private void drawRoomCard(GuiGraphicsExtractor graphics) {
        card(graphics, this.left, this.top, this.panelWidth, ROOM_H,
                Component.translatable("vivet.screen.session"), null, Theme.TEXT_FAINT);

        int x = this.left + CARD_PAD;
        int width = this.panelWidth - CARD_PAD * 2 - COPY_W - 6;
        int y = this.top + CODE_Y;

        Theme.roundedBox(graphics, x, y, width, CODE_H, 4, Theme.PANEL_SUNKEN);
        Theme.roundedBoxOutline(graphics, x, y, width, CODE_H, 4, Theme.BORDER);

        String room = roomCode();
        boolean have = !room.isEmpty();
        Component code = Component.literal(have ? room
                : this.working ? "Opening lobby..." : "No lobby yet");

        graphics.centeredText(this.font, code, x + width / 2, y + 6,
                have ? Theme.ACCENT : Theme.TEXT_FAINT);
    }

    private void drawPlayers(GuiGraphicsExtractor graphics) {
        List<Racer> racers = racers();
        boolean full = racers.size() >= Session.MAX_RACERS;

        card(graphics, this.left, this.listsY, listWidth(), LIST_H,
                Component.translatable("vivet.session.players"),
                Component.literal(racers.size() + " / " + Session.MAX_RACERS),
                full ? Theme.WARNING : Theme.TEXT_FAINT);

        int x = this.left;
        int width = listWidth();

        Clip.on(graphics, x + 1, this.listsY + LIST_TOP - 2, x + width - 1,
                this.listsY + LIST_H - 2);

        for (int i = 0; i < rowsVisible(); i++) {
            int index = i + this.racerScroll;
            if (index >= racers.size()) break;

            Racer racer = racers.get(index);
            boolean you = Session.isLocalName(racer.name);
            int y = rowY(i);

            PlayerHead.draw(graphics, racer.name, Session.accountIdOf(racer.name),
                    x + CARD_PAD, y + (ROW_H - HEAD) / 2, HEAD);

            int nameX = x + CARD_PAD + HEAD + 6;
            int taken = NameLabel.drawLeft(graphics, this.font, racer.name, nameX, y + 8,
                    Theme.TEXT);

            if (you) {
                graphics.text(this.font, Component.translatable("vivet.session.you"),
                        nameX + taken + 5, y + 8, Theme.TEXT_FAINT);
            }

            if (you || !canKick()) continue;

            UiButtons.button(graphics, UiButtons.Kind.MINUS,
                    x + width - CARD_PAD - UiButtons.SIDE, y + (ROW_H - UiButtons.SIDE) / 2,
                    this.hoveredKick == index, true, false);
        }

        Clip.off(graphics);
    }

    private void drawFriends(GuiGraphicsExtractor graphics) {
        int x = friendsX();
        int width = listWidth();

        card(graphics, x, this.listsY, width, LIST_H,
                Component.translatable("vivet.session.friends"), null, Theme.TEXT_FAINT);

        if (this.askable.isEmpty()) {
            graphics.text(this.font, Component.translatable("vivet.session.nobody_online"),
                    x + CARD_PAD, this.listsY + LIST_TOP + 4, Theme.TEXT_FAINT);
            return;
        }

        Clip.on(graphics, x + 1, this.listsY + LIST_TOP - 2, x + width - 1,
                this.listsY + LIST_H - 2);

        for (int i = 0; i < rowsVisible(); i++) {
            int index = i + this.friendScroll;
            if (index >= this.askable.size()) break;

            Social.Person person = this.askable.get(index);
            int y = rowY(i);

            PlayerHead.draw(graphics, person.name, person.uuid,
                    x + CARD_PAD, y + (ROW_H - HEAD) / 2, HEAD);

            NameLabel.drawLeft(graphics, this.font, person.name,
                    x + CARD_PAD + HEAD + 6, y + 8, Theme.TEXT);

            if (justInvited(person.uuid)) {
                Component sent = Component.translatable("vivet.session.invited");
                graphics.text(this.font, sent, x + width - CARD_PAD - this.font.width(sent),
                        y + 8, Theme.SUCCESS);
                continue;
            }

            UiButtons.button(graphics, UiButtons.Kind.PLUS,
                    x + width - CARD_PAD - UiButtons.SIDE, y + (ROW_H - UiButtons.SIDE) / 2,
                    this.hoveredInvite == index, true, false);
        }

        Clip.off(graphics);
    }

    private void drawStatus(GuiGraphicsExtractor graphics) {
        if (this.status.isEmpty()) return;

        int y = this.top + this.panelHeight - STATUS_H + 6;
        graphics.textWithWordWrap(this.font, Component.literal(this.status),
                this.left + CARD_PAD, y, this.panelWidth - CARD_PAD * 2, this.statusColor);
    }

    private void drawTip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        String key = this.hoveredKick >= 0 ? "vivet.tip.remove"
                : this.hoveredInvite >= 0 ? "vivet.tip.invite"
                : null;
        if (key == null) return;

        List<Tip.Line> lines = Tip.lines();
        Tip.wrap(lines, Component.translatable(key), Theme.TEXT_MUTED, this.font);
        Tip.draw(graphics, this.font, lines, mouseX, mouseY, this.width, this.height);
    }

    private void refreshStartButton() {
        if (this.startButton == null) return;

        boolean can = Session.canHostStart();
        this.startButton.active = can;

        String why = can ? null : startBlockedReason();
        this.startButton.setTooltip(why == null ? null
                : Tooltip.create(Component.translatable(why)));
    }

    private static String startBlockedReason() {
        if (!Session.isMultiplayer()) return "vivet.tooltip.start_alone";

        return switch (Session.phase()) {
            case READY_CHECK -> "vivet.tooltip.start_ready_check";
            case LOADING -> "vivet.tooltip.start_loading";
            case RACING -> "vivet.tooltip.start_racing";
            default -> null;
        };
    }

    private void clampScroll() {
        this.racerScroll = Math.max(0, Math.min(this.racerScroll,
                Math.max(0, racers().size() - rowsVisible())));
        this.friendScroll = Math.max(0, Math.min(this.friendScroll,
                Math.max(0, this.askable.size() - rowsVisible())));
    }

    private void findHover(int mouseX, int mouseY) {
        this.overBack = in(mouseX, mouseY, backX(), this.top, UiButtons.SIDE, UiButtons.SIDE);
        this.hoveredKick = -1;
        this.hoveredInvite = -1;
        this.over = Over.NONE;

        if (in(mouseX, mouseY, this.left, this.listsY, listWidth(), LIST_H)) {
            this.over = Over.RACERS;
        } else if (hosting()
                && in(mouseX, mouseY, friendsX(), this.listsY, listWidth(), LIST_H)) {
            this.over = Over.FRIENDS;
        }

        int row = rowAt(mouseY);
        if (row < 0) return;

        if (this.over == Over.RACERS && canKick()) {
            int index = row + this.racerScroll;
            List<Racer> racers = racers();

            if (index < racers.size() && !Session.isLocalName(racers.get(index).name)
                    && onButton(mouseX, this.left)) {
                this.hoveredKick = index;
            }
            return;
        }

        if (this.over == Over.FRIENDS) {
            int index = row + this.friendScroll;

            if (index < this.askable.size() && !justInvited(this.askable.get(index).uuid)
                    && onButton(mouseX, friendsX())) {
                this.hoveredInvite = index;
            }
        }
    }

    private int rowAt(int mouseY) {
        for (int i = 0; i < rowsVisible(); i++) {
            if (mouseY >= rowY(i) && mouseY < rowY(i) + ROW_H) return i;
        }
        return -1;
    }

    private boolean onButton(int mouseX, int cardX) {
        int x = cardX + listWidth() - CARD_PAD - UiButtons.SIDE;
        return mouseX >= x && mouseX < x + UiButtons.SIDE;
    }

    private static boolean in(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.overBack) {
            onClose();
            return true;
        }

        if (this.hoveredKick >= 0) {
            List<Racer> racers = racers();
            if (this.hoveredKick < racers.size()) kick(racers.get(this.hoveredKick).name);
            return true;
        }

        if (this.hoveredInvite >= 0) {
            if (this.hoveredInvite < this.askable.size()) {
                invite(this.askable.get(this.hoveredInvite));
            }
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int step = deltaY > 0 ? -1 : 1;

        if (this.over == Over.RACERS) {
            this.racerScroll += step;
            clampScroll();
            return true;
        }
        if (this.over == Over.FRIENDS) {
            this.friendScroll += step;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public void onClose() {
        if (!this.starting && Session.isActive()) Session.quit();

        leave();
    }

    private void leave() {
        Session.bindStatus(null);
        ConfigManager.save();
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
