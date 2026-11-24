package com.vivet.speedrun.client.screen;

import com.vivet.speedrun.client.Cues;
import com.vivet.speedrun.client.ui.Clip;
import com.vivet.speedrun.client.ui.NameLabel;
import com.vivet.speedrun.client.ui.PlayerHead;
import com.vivet.speedrun.client.ui.RankTitle;
import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.client.ui.Tip;
import com.vivet.speedrun.client.ui.UiButtons;
import com.vivet.speedrun.p2p.Chat;
import com.vivet.speedrun.p2p.MojangAuth;
import com.vivet.speedrun.p2p.Profiles;
import com.vivet.speedrun.p2p.Social;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class ChatScreen extends VivetScreen {
    private static final int MARGIN_X = 6;
    private static final int MARGIN_Y = 8;

    private static final int MAX_PAIR = 412;
    private static final int MIN_PAIR = 260;

    private static final float LIST_SHARE = 0.34F;
    private static final int LIST_MIN = 96;

    private static final int GAP = 6;

    private static final int RAIL_GAP = 7;

    private static final int PADDING = 8;
    private static final int PANEL_R = 8;

    private static final int CORNER = 6;

    private static final int HEAD_H = UiButtons.SIDE + CORNER * 2;

    private static final int ROW_H = 26;
    private static final int FACE = 18;

    private static final int BOX_H = 20;

    private static final int SEND_GAP = 4;

    private static final int BUBBLE_R = 5;

    private static final int BUBBLE_PAD = 4;
    private static final int LINE_GAP = 4;

    private static final float BUBBLE_SHARE = 0.72F;

    private static final long TIME_GAP_MS = 10 * 60 * 1000L;

    private static final int WHEEL = 22;

    private static final long NOTE_MS = 6_000L;

    private enum Left {
        THREADS, PICK
    }

    private final Screen parent;

    private Left left = Left.THREADS;

    private String said = "";
    private long saidAt;

    private String open;

    private long builtFrom = -1L;

    private EditBox field;

    private int listScroll;
    private int chatScroll;

    private boolean atBottom = true;

    private int chatHeight;

    private int panelY;
    private int panelH;

    private int listX;
    private int listW;
    private int listY;
    private int listH;
    private int listRows;

    private int chatX;
    private int chatW;

    private boolean overBack;
    private boolean overPlus;
    private boolean overReport;
    private boolean overBlock;
    private boolean overName;
    private boolean overSend;
    private int overRow = -1;

    public ChatScreen(Screen parent) {
        this(parent, null);
    }

    public ChatScreen(Screen parent, String uuid) {
        super(Component.translatable("vivet.screen.chat"));
        this.parent = parent;
        this.open = Profiles.normalise(uuid);
    }

    @Override
    protected void init() {
        super.init();

        Chat.refresh();
        Social.refresh();

        int side = UiButtons.SIDE + RAIL_GAP;
        int pair = Math.max(MIN_PAIR, Math.min(MAX_PAIR, this.width - MARGIN_X * 2 - side * 2));

        this.listW = Math.max(LIST_MIN, Math.round(pair * LIST_SHARE));
        this.chatW = pair - GAP - this.listW;

        this.listX = (this.width - pair) / 2;
        this.chatX = this.listX + this.listW + GAP;

        this.panelH = Math.max(HEAD_H + GAP + ROW_H + PADDING * 2,
                Math.min(240, this.height - MARGIN_Y * 2));
        this.panelY = Math.max(MARGIN_Y, (this.height - this.panelH) / 2);

        this.listY = this.panelY + HEAD_H + GAP;
        this.listH = this.panelH - HEAD_H - GAP;

        this.listRows = Math.max(1, (this.listH - PADDING * 2) / ROW_H);

        buildField();
        clampList();
    }

    private void buildField() {
        if (this.open == null || Chat.banned()) {
            this.field = null;
            return;
        }

        this.field = new EditBox(this.font, this.chatX + PADDING + 6,
                Theme.textTop(boxY(), BOX_H), boxW() - 12, 10,
                Component.translatable("vivet.chat.say"));

        this.field.setBordered(false);
        this.field.setMaxLength(200);
        this.field.setTextColor(Theme.TEXT);
        this.field.setHint(Component.translatable("vivet.chat.say")
                .copy().withStyle(ChatFormatting.ITALIC));

        addRenderableWidget(this.field);
        setInitialFocus(this.field);
    }

    private int boxY() {
        return this.panelY + this.panelH - PADDING - BOX_H;
    }

    private int boxW() {
        return this.chatW - PADDING * 2 - sendRoom();
    }

    private int sendRoom() {
        return sendW() + SEND_GAP;
    }

    private int sendW() {
        return this.font.width(sendWord().getString()) + 16;
    }

    private Component sendWord() {
        return Component.translatable("vivet.chat.send");
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    public void tick() {
        super.tick();

        Chat.refresh();
        Social.refresh();

        if (this.open != null) Chat.open(this.open);

        if (Chat.stamp() != this.builtFrom) {
            this.builtFrom = Chat.stamp();

            if (this.open != null) {
                Chat.Thread thread = Chat.threadWith(this.open);
                if (thread != null && thread.unreadCount() > 0) Chat.read(this.open);
            }
        }
    }

    @Override
    protected void drawScaled(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                              float partialTick) {
        Theme.fill(graphics, 0, 0, this.width, this.height, Theme.SCRIM);

        findHover(mouseX, mouseY);

        drawHeader(graphics);
        drawList(graphics);
        drawConversation(graphics, mouseX, mouseY);

        UiButtons.button(graphics, UiButtons.Kind.BACK, this.chatX + this.chatW + RAIL_GAP,
                this.panelY, this.overBack, true, false);

        drawWidgets(graphics, mouseX, mouseY, partialTick);
        drawTip(graphics, mouseX, mouseY);
    }

    private void drawHeader(GuiGraphicsExtractor graphics) {
        Theme.roundedBox(graphics, this.listX, this.panelY, this.listW, HEAD_H, PANEL_R,
                Theme.PANEL);
        Theme.roundedBoxOutline(graphics, this.listX, this.panelY, this.listW, HEAD_H, PANEL_R,
                Theme.BORDER_STRONG);

        Component title = Component.translatable(
                this.left == Left.PICK ? "vivet.chat.new" : "vivet.chat.title");

        graphics.text(this.font, title.copy().withStyle(ChatFormatting.BOLD),
                this.listX + PADDING, this.panelY + (HEAD_H - this.font.lineHeight) / 2 + 1,
                Theme.TEXT, true);

        UiButtons.button(graphics,
                this.left == Left.PICK ? UiButtons.Kind.BACK : UiButtons.Kind.PLUS,
                plusX(), this.panelY + CORNER, this.overPlus, true, false);
    }

    private int plusX() {
        return this.listX + this.listW - CORNER - UiButtons.SIDE;
    }

    private void drawList(GuiGraphicsExtractor graphics) {
        Theme.roundedBox(graphics, this.listX, this.listY, this.listW, this.listH, PANEL_R,
                Theme.PANEL);
        Theme.roundedBoxOutline(graphics, this.listX, this.listY, this.listW, this.listH, PANEL_R,
                Theme.BORDER_STRONG);

        if (MojangAuth.token() == null) {
            drawNothing(graphics, "vivet.chat.signed_out", this.listX + PADDING,
                    this.listY + PADDING, this.listW - PADDING * 2, this.listH - PADDING * 2);
            return;
        }

        int rowX = this.listX + PADDING;
        int rowW = this.listW - PADDING * 2;
        int count = rowCount();

        for (int slot = 0; slot < this.listRows; slot++) {
            int y = this.listY + PADDING + slot * ROW_H;
            int index = this.listScroll + slot;

            Theme.divider(graphics, rowX, y + ROW_H - 1, rowW);

            if (index >= count) continue;

            drawRow(graphics, index, rowX, y, rowW, this.overRow == slot);
        }

        if (count == 0) {
            String key = Chat.banned() ? Chat.bannedText()
                    : (this.left == Left.PICK ? "vivet.chat.no_friends" : "vivet.chat.nobody");

            drawNothing(graphics, key, rowX, this.listY + PADDING, rowW, this.listH - PADDING * 2);
        }
    }

    private void drawRow(GuiGraphicsExtractor graphics, int index, int x, int y, int width,
                         boolean over) {
        String uuid;
        String name;
        String label;
        long when = 0L;
        int unread = 0;

        if (this.left == Left.PICK) {
            Social.Person person = friendAt(index);
            if (person == null) return;

            uuid = person.uuid;
            name = person.name == null ? "?" : person.name;
            label = person.label;
        } else {
            Chat.Thread thread = threadAt(index);
            if (thread == null) return;

            uuid = thread.uuid;
            name = thread.name == null ? "?" : thread.name;
            label = thread.label;
            when = thread.when();
            unread = thread.unreadCount();
        }

        boolean showing = this.left == Left.THREADS && uuid != null
                && uuid.equals(this.open);

        if (over || showing) {
            Theme.roundedBox(graphics, x, y + 1, width, ROW_H - 3, BUBBLE_R,
                    showing ? Theme.PLATE_LIT : Theme.HIGHLIGHT);
        }

        int faceX = x + 3;
        int faceY = y + (ROW_H - FACE) / 2;
        PlayerHead.draw(graphics, name, uuid, faceX, faceY, FACE);

        Component age = when <= 0L ? null : Component.literal(ago(when));
        int tail = age == null ? 0 : this.font.width(age) + 6;

        int textX = faceX + FACE + 6;
        int textY = y + (ROW_H - this.font.lineHeight) / 2 + 1;

        NameLabel treatment = label == null || label.isBlank()
                ? NameLabel.of(name) : NameLabel.byId(label);

        NameLabel.drawTaggedScrolling(graphics, this.font, treatment, name, textX, textY,
                Math.max(12, x + width - textX - tail), Theme.TEXT);

        if (age != null) {
            graphics.text(this.font, age, x + width - this.font.width(age), textY,
                    unread > 0 ? Theme.ACCENT : Theme.TEXT_FAINT, true);
        }
    }

    private void drawConversation(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Theme.roundedBox(graphics, this.chatX, this.panelY, this.chatW, this.panelH, PANEL_R,
                Theme.PANEL);
        Theme.roundedBoxOutline(graphics, this.chatX, this.panelY, this.chatW, this.panelH,
                PANEL_R, Theme.BORDER_STRONG);

        if (Chat.banned()) {
            drawNothing(graphics, Chat.bannedText(), this.chatX + PADDING, this.panelY + PADDING,
                    this.chatW - PADDING * 2, this.panelH - PADDING * 2);
            return;
        }

        if (this.open == null) {
            drawNothing(graphics, "vivet.chat.pick", this.chatX + PADDING, this.panelY + PADDING,
                    this.chatW - PADDING * 2, this.panelH - PADDING * 2);
            return;
        }

        drawTheirHeader(graphics);

        int top = this.panelY + HEAD_H;
        int bottom = boxY() - PADDING;

        Theme.divider(graphics, this.chatX + PADDING, top, this.chatW - PADDING * 2);
        Theme.divider(graphics, this.chatX + PADDING, bottom, this.chatW - PADDING * 2);

        drawMessages(graphics, top + LINE_GAP, bottom - LINE_GAP);
        drawBox(graphics);
    }

    private void drawTheirHeader(GuiGraphicsExtractor graphics) {
        String name = nameOf(this.open);

        int faceX = this.chatX + PADDING;
        int faceY = this.panelY + (HEAD_H - FACE) / 2;
        PlayerHead.draw(graphics, name, this.open, faceX, faceY, FACE);

        int btnY = this.panelY + CORNER;
        boolean blocked = blocked(this.open);

        UiButtons.button(graphics, UiButtons.blockSign(blocked), blockX(), btnY, this.overBlock,
                true, blocked);
        UiButtons.button(graphics, UiButtons.Kind.REPORT, reportX(), btnY, this.overReport, true,
                false);

        rememberRank(this.open);

        NameLabel treatment = NameLabel.byId(labelOf(this.open));

        NameLabel.drawTaggedScrolling(graphics, this.font, treatment, name, nameX(),
                this.panelY + (HEAD_H - this.font.lineHeight) / 2 + 1,
                nameRoom(), this.overName ? Theme.ACCENT : Theme.TEXT);
    }

    private int nameX() {
        return this.chatX + PADDING + FACE + 6;
    }

    private int nameRoom() {
        return Math.max(12, reportX() - nameX() - 6);
    }

    private boolean blocked(String uuid) {
        if (uuid == null) return false;

        for (Social.Person person : Social.blocked()) {
            if (person != null && uuid.equals(person.uuid)) return true;
        }
        return false;
    }

    private int blockX() {
        return this.chatX + this.chatW - CORNER - UiButtons.SIDE;
    }

    private int reportX() {
        return blockX() - UiButtons.SIDE - 4;
    }

    private void drawMessages(GuiGraphicsExtractor graphics, int top, int bottom) {
        List<Chat.Message> messages = Chat.messagesWith(this.open);
        int room = bottom - top;

        if (messages.isEmpty()) {
            drawNothing(graphics, "vivet.chat.say_something", this.chatX + PADDING, top,
                    this.chatW - PADDING * 2, room);
            return;
        }

        MojangAuth.Identity identity = MojangAuth.identity();
        String me = identity == null ? null : Profiles.normalise(identity.uuid);
        int wide = Math.round((this.chatW - PADDING * 2 - FACE - 12) * BUBBLE_SHARE);

        int total = 0;
        long previous = 0L;
        List<int[]> heights = new ArrayList<>(messages.size());

        for (Chat.Message message : messages) {
            boolean stamp = message.when() - previous > TIME_GAP_MS;
            List<String> lines = wrap(message.body, wide);

            int bubble = lines.size() * this.font.lineHeight + BUBBLE_PAD * 2;
            int head = stamp ? this.font.lineHeight + LINE_GAP : 0;

            heights.add(new int[] { head, bubble });
            total += head + bubble + LINE_GAP;

            previous = message.when();
        }

        this.chatHeight = Math.max(0, total - LINE_GAP);

        int over = Math.max(0, this.chatHeight - room);
        if (this.atBottom) this.chatScroll = over;
        this.chatScroll = Math.max(0, Math.min(over, this.chatScroll));

        Clip.on(graphics, this.chatX + 1, top, this.chatX + this.chatW - 1, bottom);
        try {
            int y = top - this.chatScroll;
            String was = null;

            for (int i = 0; i < messages.size(); i++) {
                Chat.Message message = messages.get(i);
                int[] size = heights.get(i);

                if (size[0] > 0) {
                    Component when = Component.literal(clock(message.when()));
                    graphics.text(this.font, when,
                            this.chatX + (this.chatW - this.font.width(when)) / 2, y,
                            Theme.TEXT_FAINT, false);
                    y += size[0];
                }

                boolean mine = me != null && me.equals(Profiles.normalise(message.from));

                boolean first = size[0] > 0 || was == null
                        || !was.equals(message.from);

                drawBubble(graphics, message, wide, mine, first, y, size[1]);

                y += size[1] + LINE_GAP;
                was = message.from;
            }
        } finally {
            Clip.off(graphics);
        }
    }

    private void drawBubble(GuiGraphicsExtractor graphics, Chat.Message message, int wide,
                            boolean mine, boolean first, int y, int height) {
        List<String> text = wrap(message.body, wide);

        int widest = 0;
        for (String line : text) widest = Math.max(widest, this.font.width(line));

        int bubbleW = Math.min(wide, widest) + BUBBLE_PAD * 2 + 2;

        int faceY = y;
        int bubbleX;
        int faceX;

        if (mine) {
            faceX = this.chatX + this.chatW - PADDING - FACE;
            bubbleX = faceX - 6 - bubbleW;
        } else {
            faceX = this.chatX + PADDING;
            bubbleX = faceX + FACE + 6;
        }

        if (first) {
            if (mine) {
                PlayerHead.drawLocal(graphics, faceX, faceY, FACE);
            } else {
                PlayerHead.draw(graphics, nameOf(message.from), message.from, faceX, faceY, FACE);
            }
        }

        Theme.roundedBox(graphics, bubbleX, y, bubbleW, height, BUBBLE_R,
                mine ? Theme.withAlpha(Theme.ACCENT, 0x33) : Theme.PANEL_SUNKEN);
        Theme.roundedBoxOutline(graphics, bubbleX, y, bubbleW, height, BUBBLE_R,
                mine ? Theme.withAlpha(Theme.ACCENT, 0x99) : Theme.PLATE_EDGE);

        int lineY = y + BUBBLE_PAD;
        for (String line : text) {
            graphics.text(this.font, Component.literal(line), bubbleX + BUBBLE_PAD + 1, lineY,
                    Theme.TEXT, false);
            lineY += this.font.lineHeight;
        }
    }

    private void drawBox(GuiGraphicsExtractor graphics) {
        int y = boxY();
        int width = boxW();

        Theme.roundedBox(graphics, this.chatX + PADDING, y, width, BOX_H, BUBBLE_R,
                Theme.PANEL_SUNKEN);
        Theme.roundedBoxOutline(graphics, this.chatX + PADDING, y, width, BOX_H, BUBBLE_R,
                this.field != null && this.field.isFocused()
                        ? Theme.PLATE_EDGE_LIT : Theme.PLATE_EDGE);

        int sendX = this.chatX + this.chatW - PADDING - sendW();
        Theme.roundedBox(graphics, sendX, y, sendW(), BOX_H, BUBBLE_R,
                this.overSend ? Theme.PLATE_LIT : Theme.PLATE);
        Theme.roundedBoxOutline(graphics, sendX, y, sendW(), BOX_H, BUBBLE_R,
                this.overSend ? Theme.PLATE_EDGE_LIT : Theme.PLATE_EDGE);
        Component word = sendWord();
        graphics.text(this.font, word,
                sendX + (sendW() - this.font.width(word)) / 2,
                y + (BOX_H - this.font.lineHeight) / 2 + 1,
                this.overSend ? Theme.TEXT : Theme.TEXT_MUTED, true);

        String refused = Chat.refusal();
        boolean fresh = System.currentTimeMillis() - this.saidAt < NOTE_MS;

        lockWhileRefused(!refused.isEmpty());
        boolean empty = this.field == null || this.field.getValue().isEmpty();
        if (this.field != null) {
            this.field.setHint(!refused.isEmpty() && empty ? Component.empty()
                    : Component.translatable("vivet.chat.say").copy().withStyle(ChatFormatting.ITALIC));
        }
        if (!refused.isEmpty()) {
            String shown = refused.endsWith(".") ? refused.substring(0, refused.length() - 1) : refused;
            if (empty) {
                graphics.text(this.font, Component.literal(shown), this.chatX + PADDING + 6,
                        Theme.textTop(y, BOX_H), Theme.DANGER, true);
            }
        } else if (fresh && !this.said.isEmpty()) {
            graphics.text(this.font, Component.translatable(this.said), this.chatX + PADDING,
                    y - this.font.lineHeight - 2, Theme.SUCCESS, true);
        }
    }

    private void drawNothing(GuiGraphicsExtractor graphics, String key, int x, int y, int width,
                             int height) {
        List<String> lines = wrap(Component.translatable(key).getString(), width - 8);

        int step = this.font.lineHeight + 2;
        int at = y + (height - lines.size() * step) / 2;

        for (String line : lines) {
            graphics.text(this.font, Component.literal(line),
                    x + (width - this.font.width(line)) / 2, at, Theme.TEXT_FAINT, false);
            at += step;
        }
    }

    private void drawTip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        String key = null;

        if (this.overBack) {
            key = "vivet.chat.tip.back";
        } else if (this.overPlus) {
            key = this.left == Left.PICK ? "vivet.chat.tip.back" : "vivet.chat.tip.new";
        } else if (this.overReport) {
            key = "vivet.chat.tip.report";
        } else if (this.overBlock) {
            key = blocked(this.open) ? "vivet.chat.tip.unblock" : "vivet.chat.tip.block";
        } else if (this.overName) {
            key = "vivet.chat.tip.profile";
        }

        if (key == null) return;

        List<Tip.Line> lines = Tip.lines();
        Tip.wrap(lines, Component.translatable(key), Theme.TEXT_MUTED, this.font);
        Tip.draw(graphics, this.font, lines, mouseX, mouseY, this.width, this.height);
    }

    private void findHover(int mouseX, int mouseY) {
        this.overBack = over(mouseX, mouseY, this.chatX + this.chatW + RAIL_GAP, this.panelY,
                UiButtons.SIDE, UiButtons.SIDE);

        this.overPlus = over(mouseX, mouseY, plusX(), this.panelY + CORNER,
                UiButtons.SIDE, UiButtons.SIDE);

        int btnY = this.panelY + CORNER;

        boolean talking = this.open != null && !Chat.banned();

        this.overBlock = talking
                && over(mouseX, mouseY, blockX(), btnY, UiButtons.SIDE, UiButtons.SIDE);
        this.overReport = talking
                && over(mouseX, mouseY, reportX(), btnY, UiButtons.SIDE, UiButtons.SIDE);

        this.overName = talking && over(mouseX, mouseY, this.chatX + PADDING,
                this.panelY, nameRoom() + FACE + 6, HEAD_H);

        this.overSend = talking && over(mouseX, mouseY,
                this.chatX + this.chatW - PADDING - sendW(), boxY(), sendW(), BOX_H);

        this.overRow = -1;
        int top = this.listY + PADDING;
        if (mouseX >= this.listX + PADDING && mouseX <= this.listX + this.listW - PADDING
                && mouseY >= top && mouseY < top + this.listRows * ROW_H) {
            int slot = (mouseY - top) / ROW_H;
            if (this.listScroll + slot < rowCount()) this.overRow = slot;
        }
    }

    private static boolean over(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int x = scaled(event.x());
        int y = scaled(event.y());

        findHover(x, y);

        if (this.overBack) {
            Cues.confirm();
            onClose();
            return true;
        }

        if (this.overPlus) {
            Cues.confirm();
            this.left = this.left == Left.PICK ? Left.THREADS : Left.PICK;
            this.listScroll = 0;
            return true;
        }

        if (this.overReport) {
            report();
            return true;
        }

        if (this.overBlock) {
            block();
            return true;
        }

        if (this.overName) {
            Cues.confirm();
            this.minecraft.gui.setScreen(new ProfileScreen(this, this.open));
            return true;
        }

        if (this.overSend) {
            send();
            return true;
        }

        if (this.overRow >= 0) {
            choose(this.listScroll + this.overRow);
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    private void choose(int index) {
        String uuid = this.left == Left.PICK
                ? (friendAt(index) == null ? null : friendAt(index).uuid)
                : (threadAt(index) == null ? null : threadAt(index).uuid);

        String id = Profiles.normalise(uuid);
        if (id == null) return;

        Cues.confirm();

        this.open = id;
        this.left = Left.THREADS;
        this.said = "";
        this.listScroll = 0;
        this.chatScroll = 0;
        this.atBottom = true;

        Chat.clearRefusal();
        Chat.open(id);
        Chat.read(id);

        rebuild();
    }

    private boolean refocus;

    private void lockWhileRefused(boolean refused) {
        if (this.field == null) return;
        this.field.setEditable(!refused);

        if (refused && !this.field.getValue().isEmpty()) this.field.setValue("");
        if (refused && this.field.isFocused()) {
            this.field.setFocused(false);
            setFocused(null);
            this.refocus = true;
        } else if (!refused && this.refocus) {
            this.refocus = false;
            setFocused(this.field);
            this.field.setFocused(true);
        }
    }

    private void send() {
        if (this.open == null || this.field == null) return;

        if (!Chat.refusal().isEmpty()) {
            Cues.nothingHappened();
            return;
        }

        String text = this.field.getValue().trim();
        if (text.isEmpty()) {
            Cues.nothingHappened();
            return;
        }

        Chat.clearRefusal();
        Chat.say(this.open, text);

        this.field.setValue("");
        this.atBottom = true;

        Cues.confirm();
    }

    private void report() {
        if (this.open == null) return;

        Chat.report(this.open, "");

        this.said = "vivet.chat.reported";
        this.saidAt = System.currentTimeMillis();

        Cues.confirm();
    }

    private void block() {
        if (this.open == null) return;

        Cues.confirm();

        if (blocked(this.open)) {
            Social.act("unblock", this.open);
            return;
        }

        Social.act("block", this.open);

        this.open = null;
        this.said = "";
        rebuild();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int x = scaled(mouseX);
        int y = scaled(mouseY);

        if (x >= this.listX && x <= this.listX + this.listW) {
            this.listScroll -= (int) Math.signum(deltaY);
            clampList();
            return true;
        }

        if (this.open != null && x >= this.chatX && x <= this.chatX + this.chatW) {
            this.chatScroll -= (int) Math.signum(deltaY) * WHEEL;

            int room = boxY() - PADDING - LINE_GAP - (this.panelY + HEAD_H + LINE_GAP);
            this.atBottom = this.chatScroll >= Math.max(0, this.chatHeight - room);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER) {
            send();
            return true;
        }

        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE && this.left == Left.PICK) {
            this.left = Left.THREADS;
            return true;
        }

        return super.keyPressed(event);
    }

    private int rowCount() {
        return this.left == Left.PICK ? Social.friends().size() : Chat.threads().size();
    }

    private Chat.Thread threadAt(int index) {
        List<Chat.Thread> threads = Chat.threads();
        return index < 0 || index >= threads.size() ? null : threads.get(index);
    }

    private Social.Person friendAt(int index) {
        List<Social.Person> friends = Social.friends();
        return index < 0 || index >= friends.size() ? null : friends.get(index);
    }

    private void clampList() {
        int max = Math.max(0, rowCount() - this.listRows);
        this.listScroll = Math.max(0, Math.min(max, this.listScroll));
    }

    private String nameOf(String uuid) {
        Chat.Thread thread = Chat.threadWith(uuid);
        if (thread != null && thread.name != null) return thread.name;

        String id = Profiles.normalise(uuid);
        for (Social.Person person : Social.friends()) {
            if (person != null && id != null && id.equals(Profiles.normalise(person.uuid))) {
                return person.name == null ? "?" : person.name;
            }
        }
        return "?";
    }

    private void rememberRank(String uuid) {
        Chat.Thread thread = Chat.threadWith(uuid);
        if (thread != null) {
            RankTitle.remember(thread.name, thread.uuid, thread.rank, thread.placed);
            return;
        }

        String id = Profiles.normalise(uuid);
        for (Social.Person person : Social.friends()) {
            if (person != null && id != null && id.equals(Profiles.normalise(person.uuid))) {
                RankTitle.remember(person.name, person.uuid, person.rank, person.placed);
                return;
            }
        }
    }

    private String labelOf(String uuid) {
        Chat.Thread thread = Chat.threadWith(uuid);
        if (thread != null && thread.label != null && !thread.label.isBlank()) return thread.label;

        String id = Profiles.normalise(uuid);
        for (Social.Person person : Social.friends()) {
            if (person != null && id != null && id.equals(Profiles.normalise(person.uuid))) {
                return person.label == null ? "" : person.label;
            }
        }
        return "";
    }

    private static String ago(long when) {
        long seconds = Math.max(0L, (System.currentTimeMillis() - when) / 1000L);

        if (seconds < 60) return "now";
        if (seconds < 3600) return (seconds / 60) + "m";
        return (seconds / 3600) + "h";
    }

    private static String clock(long when) {
        return java.time.format.DateTimeFormatter.ofPattern("h:mm a")
                .format(java.time.Instant.ofEpochMilli(when)
                        .atZone(java.time.ZoneId.systemDefault()));
    }

    private List<String> wrap(String text, int room) {
        List<String> lines = new ArrayList<>();
        if (text == null || room <= 0) return lines;

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

            while (this.font.width(line.toString()) > room && line.length() > 1) {
                int fits = 1;
                while (fits < line.length()
                        && this.font.width(line.substring(0, fits + 1)) <= room) {
                    fits++;
                }

                lines.add(line.substring(0, fits));
                String tail = line.substring(fits);
                line.setLength(0);
                line.append(tail);
            }
        }

        if (!line.isEmpty()) lines.add(line.toString());
        if (lines.isEmpty()) lines.add("");
        return lines;
    }

    private int scaled(double value) {
        return (int) Math.round(value / uiScale());
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
