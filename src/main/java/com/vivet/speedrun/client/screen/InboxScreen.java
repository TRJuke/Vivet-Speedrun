package com.vivet.speedrun.client.screen;

import com.vivet.speedrun.client.Cues;
import com.vivet.speedrun.client.ui.ActionIcon;
import com.vivet.speedrun.client.ui.Clip;
import com.vivet.speedrun.client.ui.Cosmetics;
import com.vivet.speedrun.client.ui.InboxIcons;
import com.vivet.speedrun.client.ui.PlayerHead;
import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.client.ui.Tip;
import com.vivet.speedrun.client.ui.UiButtons;
import com.vivet.speedrun.client.InviteHold;
import com.vivet.speedrun.client.hud.Notices;
import com.vivet.speedrun.p2p.Hub;
import com.vivet.speedrun.p2p.Inbox;
import com.vivet.speedrun.p2p.MojangAuth;
import com.vivet.speedrun.p2p.Social;
import com.vivet.speedrun.p2p.Wardrobe;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class InboxScreen extends VivetScreen {
    private static final int MAX_WIDTH = 360;
    private static final int MIN_WIDTH = 250;

    private static final int MARGIN_X = 6;
    private static final int MARGIN_Y = 8;

    private static final int RAIL_GAP = 7;
    private static final int PADDING = 8;
    private static final int GAP = 6;

    private static final int RAIL_STEP = 3;

    private static final int PANEL_R = 8;
    private static final int CARD_R = 5;

    private static final int FOOT_H = 18;

    private static final int SHUT_H = 34;
    private static final int FACE = 20;

    private static final int BAR_ROOM = 10;
    private static final int BAR_W = 4;
    private static final int BAR_GRAB = 7;

    private static final int CARD_PAD = 6;
    private static final int CARD_GAP = 5;

    private static final int WANTED_ROWS = 7;

    private static final long ASK_MS = 4_000L;

    private enum Page {
        NOTICES(InboxIcons.Kind.NOTICES, "vivet.inbox.tab.notices", "vivet.inbox.empty.notices"),
        SYSTEM(InboxIcons.Kind.SYSTEM, "vivet.inbox.tab.system", "vivet.inbox.empty.system"),
        NEWS(InboxIcons.Kind.NEWS, "vivet.inbox.tab.news", "vivet.inbox.empty.news");

        private final InboxIcons.Kind icon;
        private final String name;
        private final String empty;

        Page(InboxIcons.Kind icon, String name, String empty) {
            this.icon = icon;
            this.name = name;
            this.empty = empty;
        }
    }

    private static Page pageOf(Inbox.Line line) {
        return switch (InboxIcons.kindOf(line == null ? null : line.kind)) {
            case NOTICES -> Page.NOTICES;
            case SYSTEM -> Page.SYSTEM;
            case NEWS -> Page.NEWS;
        };
    }

    private final Screen parent;

    private Page page = Page.NOTICES;

    private int scroll;
    private boolean draggingBar;

    private long open;

    private long asking;

    private long builtFrom = -1L;

    private long builtFromHub = -1L;

    private final List<Inbox.Line> lines = new ArrayList<>();

    private int overCard = -1;
    private int overDrop = -1;
    private int overAction = -1;
    private int overRail = -1;

    private int overWelcome = -1;
    private boolean overBack;
    private boolean overRead;
    private boolean overClear;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;

    private int listLeft;
    private int listTop;
    private int listWidth;
    private int listHeight;
    private int barX;

    public InboxScreen(Screen parent) {
        super(Component.translatable("vivet.screen.inbox"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        Inbox.refresh();
        Social.refresh();

        com.vivet.speedrun.p2p.Achievements.refresh();

        int side = Math.max(InboxIcons.W, UiButtons.SIDE) + RAIL_GAP;
        this.panelW = Math.max(MIN_WIDTH,
                Math.min(MAX_WIDTH, this.width - MARGIN_X * 2 - side * 2));

        int furniture = PADDING * 2 + GAP + FOOT_H;
        int wanted = furniture + WANTED_ROWS * (SHUT_H + CARD_GAP) - CARD_GAP;

        int floor = Math.max(furniture + SHUT_H, railHeight() + PADDING * 2);

        this.panelH = Math.min(this.height - MARGIN_Y * 2, Math.max(floor, wanted));

        this.panelX = (this.width - this.panelW) / 2;
        this.panelY = Math.max(MARGIN_Y, (this.height - this.panelH) / 2);

        int room = PADDING + BAR_ROOM;
        this.listLeft = this.panelX + room;
        this.listWidth = this.panelW - room * 2;
        this.listTop = this.panelY + PADDING;
        this.listHeight = this.panelH - PADDING * 2 - GAP - FOOT_H;
        this.barX = this.panelX + this.panelW - PADDING - BAR_W;

        rebuildLines();
    }

    private static int railHeight() {
        return InboxIcons.H * 3 + RAIL_STEP * 2;
    }

    private int railX() {
        return this.panelX - RAIL_GAP - InboxIcons.W;
    }

    private int railY(int index) {
        return this.panelY + index * (InboxIcons.H + RAIL_STEP);
    }

    private int backX() {
        return this.panelX + this.panelW + RAIL_GAP;
    }

    private int footTop() {
        return this.panelY + this.panelH - PADDING - FOOT_H;
    }

    private int footWidth() {
        return (this.panelW - PADDING * 2 - GAP) / 2;
    }

    private int footX(boolean right) {
        return right ? this.panelX + this.panelW - PADDING - footWidth() : this.panelX + PADDING;
    }

    private void rebuildLines() {
        this.lines.clear();
        this.builtFrom = Inbox.stamp();
        this.builtFromHub = Hub.stamp();

        if (this.page == Page.NOTICES) {
            for (Hub.Invite invite : Hub.invites()) this.lines.add(lineFor(invite));
        }

        for (Inbox.Line line : Inbox.lines()) {
            if (line == null || pageOf(line) != this.page) continue;

            if (welcome(line) && Inbox.answered(line.id)) continue;
            this.lines.add(line);
        }

        this.scroll = Math.max(0, Math.min(maxScroll(), this.scroll));
    }

    private static Inbox.Line lineFor(Hub.Invite invite) {
        Inbox.Line line = new Inbox.Line();

        line.id = -(Math.abs((long) invite.uuid().hashCode()) + 1L);
        line.kind = "race_invite";
        line.actor_uuid = invite.uuid();
        line.actor_name = invite.name();
        line.created_at = invite.at();
        line.read_at = 0L;

        return line;
    }

    private static boolean local(Inbox.Line line) {
        return line != null && line.id < 0L;
    }

    private static Hub.Invite inviteFrom(String uuid) {
        if (uuid == null) return null;

        for (Hub.Invite invite : Hub.invites()) {
            if (uuid.equals(invite.uuid())) return invite;
        }
        return null;
    }

    private boolean unreadIn(Page which) {
        if (which == Page.NOTICES && !Hub.invites().isEmpty()) return true;

        for (Inbox.Line line : Inbox.lines()) {
            if (line != null && line.unread() && pageOf(line) == which) return true;
        }
        return false;
    }

    private void show(Page which) {
        if (this.page == which) return;

        this.page = which;
        this.open = 0L;
        this.scroll = 0;
        this.asking = 0L;

        Cues.confirm();
        rebuildLines();
    }

    @Override
    public void tick() {
        super.tick();

        Inbox.refresh();
        Social.refresh();

        if (Inbox.stamp() != this.builtFrom || Hub.stamp() != this.builtFromHub) rebuildLines();
    }

    private Component headingOf(Inbox.Line line) {
        String kind = line.kind == null ? "" : line.kind;
        String who = line.actor_name == null ? "" : line.actor_name;

        return switch (kind) {
            case "race_invite" -> Component.translatable("vivet.inbox.invite", who);
            case "friend_request" -> Component.translatable("vivet.inbox.request", who);
            case "friend_accepted" -> Component.translatable("vivet.inbox.accepted", who);
            case "report" -> Component.translatable("vivet.inbox.report");
            case "welcome" -> Component.translatable("vivet.inbox.welcome.title");

            case "cosmetic" -> Component.translatable("vivet.inbox.cosmetic", unlockedName(line));

            case "achievement" -> Component.translatable("vivet.inbox.achievement",
                    achievementName(line));

            default -> line.title == null || line.title.isBlank()
                    ? Component.translatable(kind.equals("message")
                            ? "vivet.inbox.message" : "vivet.inbox.news")
                    : Component.literal(line.title);
        };
    }

    private static Component achievementName(Inbox.Line line) {
        String ref = line.ref == null ? "" : line.ref;
        int colon = ref.lastIndexOf(':');
        String id = colon < 0 ? ref : ref.substring(0, colon);
        int node = 0;
        try {
            node = colon < 0 ? 0 : Integer.parseInt(ref.substring(colon + 1));
        } catch (NumberFormatException ignored) {
        }
        return com.vivet.speedrun.client.ui.AchievementText.nodeName(id, node,
                com.vivet.speedrun.client.ui.AchievementText.nodesOf(id, node + 1), true);
    }

    private String unlockedName(Inbox.Line line) {
        String ref = line.ref == null ? "" : line.ref;
        if (ref.isBlank()) return Component.translatable("vivet.inbox.something").getString();

        Wardrobe.Item item = Wardrobe.item(ref);
        if (item != null) return Cosmetics.nameOf(item).getString();

        return Wardrobe.setName(ref);
    }

    private String bodyOf(Inbox.Line line) {
        if (welcome(line)) return Component.translatable("vivet.inbox.welcome.body").getString();
        return line.body == null ? "" : line.body;
    }

    private static boolean welcome(Inbox.Line line) {
        return line != null && "welcome".equals(line.kind);
    }

    private static final int WELCOME_BTN_H = 18;

    private int[] welcomeButton(int cardY, int cardH, int which) {
        int inner = this.listWidth - CARD_PAD * 2;
        int width = (inner - GAP) / 2;
        return new int[] { this.listLeft + CARD_PAD + which * (width + GAP),
                cardY + cardH - CARD_PAD - WELCOME_BTN_H, width };
    }

    private int accentOf(Inbox.Line line) {
        String kind = line.kind == null ? "" : line.kind;

        return switch (kind) {
            case "friend_request", "friend_accepted", "race_invite" -> Theme.SUCCESS;
            case "report", "message" -> Theme.WARNING;
            case "cosmetic" -> Theme.ACCENT;
            case "coins", "achievement", "refund" -> 0xFFE8C34A;

            case "ban" -> Theme.DANGER;
            default -> Theme.ACCENT;
        };
    }

    private record Answer(UiButtons.Kind icon, String action, String tip) {
    }

    private List<Answer> answersFor(Inbox.Line line) {
        String kind = line.kind == null ? "" : line.kind;
        String who = line.actor_uuid == null ? "" : line.actor_uuid;
        if (who.isBlank()) return List.of();

        return switch (kind) {
            case "race_invite" -> InviteHold.free()
                    ? List.of(
                            new Answer(UiButtons.Kind.PLUS, "invite_accept", "vivet.inbox.tip.join"),
                            new Answer(UiButtons.Kind.MINUS, "invite_decline", "vivet.inbox.tip.decline"))
                    : List.of(
                            new Answer(UiButtons.Kind.MINUS, "invite_decline", "vivet.inbox.tip.decline"));

            case "friend_request" -> List.of(
                    new Answer(UiButtons.Kind.PLUS, "accept", "vivet.inbox.tip.accept"),
                    new Answer(UiButtons.Kind.MINUS, "reject", "vivet.inbox.tip.reject"));

            case "friend_accepted" -> List.of(
                    new Answer(UiButtons.Kind.MESSAGE, "message", "vivet.inbox.tip.message"));

            default -> List.of();
        };
    }

    private void answer(Inbox.Line line, Answer what) {
        String who = line.actor_uuid;

        switch (what.action()) {
            case "invite_accept" -> {
                Hub.Invite invite = inviteFrom(who);

                if (invite == null || !InviteHold.free()) {
                    Cues.deny();
                    return;
                }
                InviteHold.take(this.minecraft, invite);
            }
            case "invite_decline" -> {
                Hub.Invite invite = inviteFrom(who);
                if (invite != null) Hub.dropInvite(invite);

                Notices.dismiss(who);
                Cues.deny();
                rebuildLines();
            }
            case "accept" -> {
                Social.act("accept", who);
                Cues.confirm();
                Inbox.drop(line.id);
            }
            case "reject" -> {
                Social.act("reject", who);
                Cues.deny();
                Inbox.drop(line.id);
            }
            case "message" -> {
                Cues.confirm();
                this.minecraft.gui.setScreen(new ChatScreen(this, who));
            }
            default -> Cues.nothingHappened();
        }
    }

    private int heightOf(Inbox.Line line) {
        if (welcome(line)) {
            List<String> body = wrap(bodyOf(line), this.listWidth - CARD_PAD * 2);
            return SHUT_H + body.size() * (this.font.lineHeight + 1) + 6 + WELCOME_BTN_H + CARD_PAD;
        }
        if (line == null || line.id != this.open) return SHUT_H;

        List<String> body = wrap(bodyOf(line), this.listWidth - CARD_PAD * 2);
        if (body.isEmpty()) return SHUT_H;

        return SHUT_H + body.size() * (this.font.lineHeight + 1) + 4;
    }

    private int contentHeight() {
        int total = 0;
        for (Inbox.Line line : this.lines) total += heightOf(line) + CARD_GAP;
        return Math.max(0, total - CARD_GAP);
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - this.listHeight);
    }

    private int topOf(int index) {
        int y = this.listTop - this.scroll;
        for (int i = 0; i < index; i++) y += heightOf(this.lines.get(i)) + CARD_GAP;
        return y;
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

        drawList(graphics);
        drawBar(graphics, mouseX, mouseY);
        drawFoot(graphics);
        drawRail(graphics);

        UiButtons.button(graphics, UiButtons.Kind.BACK, backX(), this.panelY, this.overBack,
                true, false);

        drawWidgets(graphics, mouseX, mouseY, partialTick);
        drawTip(graphics, mouseX, mouseY);
    }

    private void drawRail(GuiGraphicsExtractor graphics) {
        for (Page which : Page.values()) {
            int index = which.ordinal();

            InboxIcons.button(graphics, which.icon, railX(), railY(index), this.overRail == index,
                    which == this.page, unreadIn(which));
        }
    }

    private void drawList(GuiGraphicsExtractor graphics) {
        if (MojangAuth.token() == null) {
            drawNothing(graphics, "vivet.inbox.signed_out");
            return;
        }

        if (this.lines.isEmpty()) {
            drawNothing(graphics, Inbox.known() ? this.page.empty : "vivet.inbox.loading");
            return;
        }

        Clip.on(graphics, this.listLeft, this.listTop,
                this.listLeft + this.listWidth, this.listTop + this.listHeight);
        try {
            for (int i = 0; i < this.lines.size(); i++) {
                int y = topOf(i);
                int height = heightOf(this.lines.get(i));

                if (y + height < this.listTop || y > this.listTop + this.listHeight) continue;
                drawCard(graphics, this.lines.get(i), i, y, height, i == this.overCard);
            }
        } finally {
            Clip.off(graphics);
        }
    }

    private void drawCard(GuiGraphicsExtractor graphics, Inbox.Line line, int index, int y,
                          int height, boolean over) {
        int accent = accentOf(line);
        boolean unread = line.unread();

        Theme.roundedBox(graphics, this.listLeft, y, this.listWidth, height, CARD_R,
                over ? Theme.PLATE_LIT : Theme.PLATE);
        Theme.roundedBoxOutline(graphics, this.listLeft, y, this.listWidth, height, CARD_R,
                Theme.withAlpha(unread ? accent : Theme.PLATE_EDGE, over ? 0xEE : 0x88));

        int faceX = this.listLeft + CARD_PAD + 2;
        int faceY = y + (SHUT_H - FACE) / 2;

        String who = line.actor_name == null ? "" : line.actor_name;

        if (!who.isBlank()) {
            PlayerHead.draw(graphics, who, line.actor_uuid, faceX, faceY, FACE);
        } else {
            Theme.roundedBox(graphics, faceX, faceY, FACE, FACE, 3, Theme.withAlpha(accent, 0x28));
            InboxIcons.icon(graphics, pageOf(line).icon, faceX + FACE / 2, y + SHUT_H / 2, accent);
        }

        List<Answer> answers = answersFor(line);
        int right = this.listLeft + this.listWidth - CARD_PAD - 14;

        if (over) {
            boolean lit = this.overDrop == index;

            ActionIcon.draw(graphics, ActionIcon.Kind.NO, right + 7, y + SHUT_H / 2, 8,
                    lit ? Theme.DANGER : Theme.TEXT_FAINT);
        }

        for (int i = 0; i < answers.size(); i++) {
            int x = actionX(answers.size(), i);
            UiButtons.button(graphics, answers.get(i).icon(), x, y + (SHUT_H - UiButtons.SIDE) / 2,
                    this.overCard == index && this.overAction == i, true, false);
        }

        int textX = faceX + FACE + 8;

        int room = (answers.isEmpty() ? right : actionX(answers.size(), 0)) - 6 - textX;

        drawCut(graphics, headingOf(line), textX, y + 7, room,
                unread ? Theme.TEXT : Theme.TEXT_MUTED);
        drawCut(graphics, Component.literal(whenOf(line)), textX,
                y + 7 + this.font.lineHeight + 2, room, Theme.TEXT_FAINT);

        if (welcome(line)) {
            int bodyY = y + SHUT_H - 2;
            for (String text : wrap(bodyOf(line), this.listWidth - CARD_PAD * 2)) {
                graphics.text(this.font, Component.literal(text), this.listLeft + CARD_PAD, bodyY,
                        Theme.TEXT, false);
                bodyY += this.font.lineHeight + 1;
            }
            int[] skip = welcomeButton(y, height, 0);
            int[] go = welcomeButton(y, height, 1);
            foot(graphics, skip[0], skip[1], skip[2], Component.translatable("vivet.inbox.welcome.skip"),
                    over && this.overWelcome == 0, true, Theme.TEXT_MUTED);
            foot(graphics, go[0], go[1], go[2], Component.translatable("vivet.inbox.welcome.go"),
                    over && this.overWelcome == 1, true, Theme.SUCCESS);
            return;
        }

        if (line.id != this.open) return;

        int lineY = y + SHUT_H - 2;
        for (String text : wrap(bodyOf(line), this.listWidth - CARD_PAD * 2)) {
            graphics.text(this.font, Component.literal(text), this.listLeft + CARD_PAD, lineY,
                    Theme.TEXT, false);
            lineY += this.font.lineHeight + 1;
        }
    }

    private int actionX(int count, int index) {
        int last = this.listLeft + this.listWidth - CARD_PAD - 14 - 4 - UiButtons.SIDE;
        return last - (count - 1 - index) * (UiButtons.SIDE + 3);
    }

    private void drawFoot(GuiGraphicsExtractor graphics) {
        Theme.divider(graphics, this.panelX + PADDING, footTop() - GAP / 2 - 1,
                this.panelW - PADDING * 2);

        int y = footTop();
        int width = footWidth();

        foot(graphics, footX(false), y, width, Component.translatable("vivet.inbox.mark_read"),
                this.overRead, anyUnread(), Theme.ACCENT);

        boolean asked = asking();
        foot(graphics, footX(true), y, width,
                Component.translatable(asked ? "vivet.inbox.clear_sure" : "vivet.inbox.clear"),
                this.overClear, !this.lines.isEmpty(), asked ? Theme.DANGER : Theme.TEXT_MUTED);
    }

    private void foot(GuiGraphicsExtractor graphics, int x, int y, int width, Component word,
                      boolean over, boolean live, int accent) {
        int colour = live ? accent : Theme.TEXT_FAINT;

        Theme.roundedBox(graphics, x, y, width, FOOT_H, 4,
                !live ? Theme.PLATE
                      : (over ? Theme.mix(Theme.PLATE_LIT, colour, 0.22F) : Theme.PLATE));
        Theme.roundedBoxOutline(graphics, x, y, width, FOOT_H, 4,
                Theme.withAlpha(colour, live ? (over ? 0xEE : 0x88) : 0x44));

        graphics.text(this.font, word, x + (width - this.font.width(word)) / 2,
                y + (FOOT_H - this.font.lineHeight) / 2 + 1,
                live ? (over ? Theme.TEXT : Theme.TEXT_MUTED) : Theme.TEXT_FAINT, true);
    }

    private boolean anyUnread() {
        for (Inbox.Line line : this.lines) {
            if (line.unread()) return true;
        }
        return false;
    }

    private boolean asking() {
        return this.asking > 0L && System.currentTimeMillis() - this.asking < ASK_MS;
    }

    private String whenOf(Inbox.Line line) {
        long ago = Math.max(0L, System.currentTimeMillis() - line.created_at);

        long minutes = ago / 60_000L;
        if (minutes < 1) return Component.translatable("vivet.inbox.just_now").getString();
        if (minutes < 60) return Component.translatable("vivet.inbox.minutes", minutes).getString();

        long hours = minutes / 60;
        if (hours < 24) return Component.translatable("vivet.inbox.hours", hours).getString();

        return Component.translatable("vivet.inbox.days", hours / 24).getString();
    }

    private void drawCut(GuiGraphicsExtractor graphics, Component line, int x, int y, int room,
                         int colour) {
        String text = line.getString();
        if (this.font.width(text) <= room) {
            graphics.text(this.font, line, x, y, colour, true);
            return;
        }

        while (text.length() > 1 && this.font.width(text + "...") > room) {
            text = text.substring(0, text.length() - 1);
        }
        graphics.text(this.font, Component.literal(text + "..."), x, y, colour, true);
    }

    private void drawNothing(GuiGraphicsExtractor graphics, String key) {
        List<String> lines = wrap(Component.translatable(key).getString(), this.listWidth - 16);

        int step = this.font.lineHeight + 2;
        int y = this.listTop + (this.listHeight - lines.size() * step) / 2;

        for (String line : lines) {
            graphics.text(this.font, Component.literal(line),
                    this.listLeft + (this.listWidth - this.font.width(line)) / 2, y,
                    Theme.TEXT_FAINT, false);
            y += step;
        }
    }

    private List<String> wrap(String text, int room) {
        List<String> lines = new ArrayList<>();
        if (room <= 0 || text == null || text.isBlank()) return lines;

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

    private void drawBar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (maxScroll() <= 0) return;

        boolean lit = this.draggingBar || overBar(mouseX, mouseY);

        Theme.roundedBox(graphics, this.barX, this.listTop, BAR_W, this.listHeight, BAR_W / 2,
                Theme.withAlpha(0xFFFFFFFF, 0x12));
        Theme.roundedBox(graphics, this.barX, this.listTop + thumbOffset(), BAR_W, thumbHeight(),
                BAR_W / 2, Theme.withAlpha(0xFFFFFFFF, lit ? 0xBB : 0x55));
    }

    private int thumbHeight() {
        return Math.max(18, this.listHeight * this.listHeight / Math.max(1, contentHeight()));
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
    }

    private void drawTip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        String key = null;

        if (this.overBack) {
            key = "vivet.inbox.tip.back";
        } else if (this.overRail >= 0) {
            key = Page.values()[this.overRail].name;
        } else if (this.overDrop >= 0) {
            key = "vivet.inbox.tip.drop";
        } else if (this.overCard >= 0 && this.overAction >= 0) {
            List<Answer> answers = answersFor(this.lines.get(this.overCard));
            if (this.overAction < answers.size()) key = answers.get(this.overAction).tip();
        }

        if (key == null) return;

        List<Tip.Line> lines = Tip.lines();
        Tip.wrap(lines, Component.translatable(key), Theme.TEXT_MUTED, this.font);
        Tip.draw(graphics, this.font, lines, mouseX, mouseY, this.width, this.height);
    }

    private void findHover(int mouseX, int mouseY) {
        this.overCard = -1;
        this.overDrop = -1;
        this.overAction = -1;
        this.overRail = -1;
        this.overWelcome = -1;
        this.overBack = false;
        this.overRead = false;
        this.overClear = false;

        this.overBack = in(mouseX, mouseY, backX(), this.panelY, UiButtons.SIDE, UiButtons.SIDE);

        for (int i = 0; i < Page.values().length; i++) {
            if (in(mouseX, mouseY, railX(), railY(i), InboxIcons.W, InboxIcons.H)) {
                this.overRail = i;
            }
        }

        int foot = footTop();
        this.overRead = in(mouseX, mouseY, footX(false), foot, footWidth(), FOOT_H);
        this.overClear = in(mouseX, mouseY, footX(true), foot, footWidth(), FOOT_H);

        if (mouseY < this.listTop || mouseY >= this.listTop + this.listHeight) return;

        for (int i = 0; i < this.lines.size(); i++) {
            int top = topOf(i);
            int height = heightOf(this.lines.get(i));

            if (!in(mouseX, mouseY, this.listLeft, top, this.listWidth, height)) continue;

            this.overCard = i;

            if (welcome(this.lines.get(i))) {
                for (int w = 0; w < 2; w++) {
                    int[] b = welcomeButton(top, height, w);
                    if (in(mouseX, mouseY, b[0], b[1], b[2], WELCOME_BTN_H)) this.overWelcome = w;
                }
            }

            List<Answer> answers = answersFor(this.lines.get(i));
            for (int a = 0; a < answers.size(); a++) {
                if (in(mouseX, mouseY, actionX(answers.size(), a),
                        top + (SHUT_H - UiButtons.SIDE) / 2, UiButtons.SIDE, UiButtons.SIDE)) {
                    this.overAction = a;
                }
            }

            if (this.overAction < 0
                    && in(mouseX, mouseY, this.listLeft + this.listWidth - CARD_PAD - 14,
                          top + SHUT_H / 2 - 7, 14, 14)) {
                this.overDrop = i;
            }
            return;
        }
    }

    private static boolean in(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private int scaled(double value) {
        return (int) Math.round(value / uiScale());
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

        if (this.overRail >= 0) {
            show(Page.values()[this.overRail]);
            return true;
        }

        if (this.overRead) {
            markPageRead();
            return true;
        }

        if (this.overClear) {
            clearPage();
            return true;
        }

        if (this.overCard >= 0 && this.overWelcome >= 0) {
            answerWelcome(this.lines.get(this.overCard), this.overWelcome == 1);
            return true;
        }

        if (this.overCard >= 0 && this.overAction >= 0) {
            Inbox.Line line = this.lines.get(this.overCard);
            List<Answer> answers = answersFor(line);

            if (this.overAction < answers.size()) {
                answer(line, answers.get(this.overAction));
                return true;
            }
        }

        if (this.overDrop >= 0) {
            Inbox.Line dropped = this.lines.get(this.overDrop);

            if (local(dropped)) {
                answer(dropped, new Answer(UiButtons.Kind.MINUS, "invite_decline",
                        "vivet.inbox.tip.decline"));
                return true;
            }

            if (welcome(dropped)) {
                answerWelcome(dropped, false);
                return true;
            }

            Cues.confirm();
            Inbox.drop(dropped.id);
            return true;
        }

        if (this.overCard >= 0) {
            Inbox.Line line = this.lines.get(this.overCard);
            Cues.confirm();

            if (this.open == line.id) {
                this.open = 0L;
            } else {
                this.open = line.id;
                if (line.unread() && !local(line)) Inbox.read(line.id);
            }
            return true;
        }

        if (maxScroll() > 0 && overBar(x, y)) {
            this.draggingBar = true;
            dragTo(y);
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    private void answerWelcome(Inbox.Line line, boolean go) {
        Inbox.answerWelcome(line.id);
        if (!go) {
            Cues.confirm();
            rebuildLines();
            return;
        }

        var config = com.vivet.speedrun.config.ConfigManager.get();
        config.rankedSoloBand = com.vivet.speedrun.game.Difficulty.EASY;
        config.rankedSoloSetSeed = false;
        config.tutorialPlayPulse = true;
        com.vivet.speedrun.config.ConfigManager.save();

        Cues.tutorialGo();
        this.minecraft.gui.setScreen(new RankedScreen(this.parent));
    }

    private void markPageRead() {
        List<Long> ids = new ArrayList<>();
        for (Inbox.Line line : this.lines) {
            if (line.unread() && !local(line)) ids.add(line.id);
        }

        if (ids.isEmpty()) {
            Cues.nothingHappened();
            return;
        }

        Cues.markedRead();
        Inbox.read(ids);
    }

    private void clearPage() {
        if (this.lines.isEmpty()) {
            Cues.nothingHappened();
            return;
        }

        if (!asking()) {
            this.asking = System.currentTimeMillis();
            Cues.deny();
            return;
        }

        List<Long> ids = new ArrayList<>();
        for (Inbox.Line line : this.lines) {
            if (!local(line)) ids.add(line.id);
        }

        this.asking = 0L;
        this.open = 0L;

        Cues.swept();
        Inbox.drop(ids);
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
            this.scroll = Math.max(0, Math.min(maxScroll(),
                    this.scroll - (int) Math.signum(deltaY) * 18));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
            if (asking()) {
                this.asking = 0L;
                return true;
            }
            if (this.open != 0L) {
                this.open = 0L;
                return true;
            }
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
