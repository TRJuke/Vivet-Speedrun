package com.vivet.speedrun.client.ui;

import com.vivet.speedrun.client.Cues;
import com.vivet.speedrun.p2p.Quests;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.text.NumberFormat;
import java.util.Locale;

public class QuestPlate extends AbstractWidget {
    public static final int ACCENT = 0xFF5FC9B8;

    private static final int GOLD = 0xFFE8C34A;

    private static final int PAD = 6;
    private static final int HEAD = 26;
    public static final int CARD = 40;
    private static final int GAP = 4;

    public static final int HEIGHT = PAD + HEAD + GAP + 3 * CARD + 2 * GAP + PAD;

    public static final int WIDTH = 256;

    public static final int FOLD = UiButtons.SIDE;
    private static final int FOLD_GAP = 6;

    public static final int OPEN_WIDTH = WIDTH + FOLD_GAP + FOLD;

    private static final int BUTTON_H = 16;

    private boolean open;

    private float shown;
    private long lastFrame;

    private static final float SLIDE_MS = 260.0F;

    private final int baseX;
    private final int baseY;

    private final Runnable onFold;

    private boolean room = true;

    private int rerollX;
    private int rerollY;
    private int rerollW;

    private volatile String refused;
    private volatile long refusedAt;

    private float hoverAmount;

    public QuestPlate(int x, int y, boolean open, boolean slide, Runnable onFold) {
        super(x, y, OPEN_WIDTH, HEIGHT, Component.translatable("vivet.quests.title"));
        this.baseX = x;
        this.baseY = y;
        this.open = open;
        this.shown = (open != slide) ? 1.0F : 0.0F;
        this.onFold = onFold;
        this.lastFrame = System.currentTimeMillis();
        fitBounds();
    }

    public QuestPlate noRoom() {
        this.room = false;
        return this;
    }

    public boolean foldedAway() {
        return !this.open && this.shown <= 0.0F;
    }

    private float eased() {
        float t = this.shown;
        return t < 0.5F ? 4.0F * t * t * t : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 3) / 2.0F;
    }

    private int offset() {
        return Math.round((1.0F - eased()) * (WIDTH + FOLD_GAP));
    }

    private int foldX() {
        return this.baseX + WIDTH + FOLD_GAP - offset();
    }

    private int foldY() {
        return this.baseY + (HEIGHT - FOLD) / 2;
    }

    private void fitBounds() {
        if (this.shown <= 0.0F) {
            setX(foldX());
            setY(foldY());
            setWidth(FOLD);
            setHeight(FOLD);
        } else {
            setX(this.baseX - offset());
            setY(this.baseY);
            setWidth(OPEN_WIDTH);
            setHeight(HEIGHT);
        }
    }

    private void step() {
        long now = System.currentTimeMillis();
        float by = Math.min(1.0F, (now - this.lastFrame) / SLIDE_MS);
        this.lastFrame = now;
        this.shown = this.open ? Math.min(1.0F, this.shown + by) : Math.max(0.0F, this.shown - by);
        fitBounds();
    }

    private boolean overFold(double mouseX, double mouseY) {
        return mouseX >= foldX() && mouseX < foldX() + FOLD && mouseY >= foldY() && mouseY < foldY() + FOLD;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                            float partialTick) {
        step();
        if (this.shown > 0.0F) drawContainer(graphics, mouseX, mouseY, partialTick);
        drawFold(graphics, overFold(mouseX, mouseY));
    }

    private void drawContainer(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int x = this.baseX - offset();
        int y = this.baseY;

        Theme.roundedBox(graphics, x, y, WIDTH, HEIGHT, 6, Theme.TITLE_PLATE);
        Theme.roundedBoxOutline(graphics, x, y, WIDTH, HEIGHT, 6, Theme.PLATE_EDGE);

        Quests.refresh();
        Quests.Book book = Quests.book();
        boolean known = Quests.current() && book != null && book.list != null;

        int inner = x + PAD;
        int innerW = WIDTH - PAD * 2;
        drawHead(graphics, font, inner, y + PAD, innerW, known, book, mouseX, mouseY, partialTick);

        int top = y + PAD + HEAD + GAP;
        for (int i = 0; i < 3; i++) {
            Quests.Quest quest = known && i < book.list.length ? book.list[i] : null;
            drawCard(graphics, font, quest, inner, top + i * (CARD + GAP), innerW);
        }
    }

    private void drawFold(GuiGraphicsExtractor graphics, boolean hovered) {
        int x = foldX();
        int y = foldY();
        float e = eased();
        if (e >= 1.0F) {
            UiButtons.button(graphics, UiButtons.Kind.BACK, x, y, hovered, true, false);
            return;
        }

        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x + FOLD / 2.0F, y + FOLD / 2.0F);
        pose.rotate((float) Math.PI * (1.0F - e));
        pose.translate(-FOLD / 2.0F, -FOLD / 2.0F);
        UiButtons.button(graphics, UiButtons.Kind.BACK, 0, 0, hovered, true, false);
        pose.popMatrix();
    }

    private void drawHead(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, boolean known,
                          Quests.Book book, int mouseX, int mouseY, float partialTick) {
        VanillaIcon.draw(graphics, VanillaIcon.Kind.QUEST, x + 9, y + HEAD / 2, 16);
        Component title = Component.translatable("vivet.quests.title").withStyle(ChatFormatting.BOLD);
        graphics.text(font, title, x + 21, y + 4, ACCENT, true);

        long until = Quests.untilReset();
        if (until >= 0) {
            graphics.text(font, Component.translatable("vivet.quests.resets", clock(until)), x + 21, y + 15,
                    Theme.TEXT_MUTED, true);
        }

        int left = known && book.rerolls != null ? book.rerolls : 0;
        boolean open = known && java.util.Arrays.stream(book.list)
                .anyMatch(q -> q != null && !Boolean.TRUE.equals(q.done));
        boolean usable = open && left > 0 && !Quests.rerolling();

        String word = this.refused != null && System.currentTimeMillis() - this.refusedAt < 3_000L
                ? this.refused
                : Component.translatable("vivet.quests.reroll_left", left).getString();
        this.rerollW = font.width(word) + 14;
        this.rerollX = x + width - this.rerollW;
        this.rerollY = y + (HEAD - BUTTON_H) / 2;

        boolean over = usable && mouseX >= this.rerollX && mouseX < this.rerollX + this.rerollW
                && mouseY >= this.rerollY && mouseY < this.rerollY + BUTTON_H;
        float target = over ? 1.0F : 0.0F;
        this.hoverAmount += (target - this.hoverAmount) * Math.min(1.0F, partialTick * 0.5F);
        float lit = this.hoverAmount;

        int fill = usable ? Theme.mix(Theme.withAlpha(ACCENT, 0x66), Theme.withAlpha(ACCENT, 0xCC), lit)
                : Theme.PANEL_SUNKEN;
        int edge = usable ? Theme.mix(ACCENT, Theme.TEXT, lit) : Theme.PLATE_EDGE;
        Theme.roundedBox(graphics, this.rerollX, this.rerollY, this.rerollW, BUTTON_H, 4, fill);
        Theme.roundedBoxOutline(graphics, this.rerollX, this.rerollY, this.rerollW, BUTTON_H, 4, edge);
        graphics.centeredText(font, Component.literal(word), this.rerollX + this.rerollW / 2,
                this.rerollY + 4, usable ? Theme.TEXT : Theme.TEXT_FAINT);
    }

    public static void drawCard(GuiGraphicsExtractor graphics, Font font, Quests.Quest quest, int x, int y,
                                 int width) {
        if (quest == null) {
            Theme.roundedBox(graphics, x, y, width, CARD, 5, Theme.PLATE);
            Theme.roundedBoxOutline(graphics, x, y, width, CARD, 5, Theme.PLATE_EDGE);
            return;
        }

        int target = quest.target == null ? 1 : Math.max(1, quest.target);
        int progress = quest.progress == null ? 0 : Math.min(quest.progress, target);
        boolean done = Boolean.TRUE.equals(quest.done);
        String rarity = quest.rarity == null ? "common" : quest.rarity;
        int colour = QuestText.colour(rarity);

        int edge = colour;

        Theme.roundedBox(graphics, x, y, width, CARD, 5, Theme.PLATE_LIT);
        Theme.roundedBox(graphics, x, y, width, CARD, 5, Theme.withAlpha(edge, done ? 0x22 : 0x16));
        Theme.roundedBoxOutline(graphics, x, y, width, CARD, 5, Theme.withAlpha(edge, 0xCC));

        int inner = x + 7;
        int right = x + width - 7;

        String tier = Component.translatable("vivet.locker.tier." + rarity).getString().toUpperCase(Locale.ROOT);
        graphics.text(font, Component.literal(tier).withStyle(ChatFormatting.BOLD), inner, y + 5, colour, true);
        String state = done ? Component.translatable("vivet.quests.done").getString()
                : QuestText.progress(progress, target);
        graphics.text(font, Component.literal(state), right - font.width(state), y + 5,
                done ? Theme.SUCCESS : Theme.TEXT, true);

        String line = QuestText.line(quest.id, target, quest.text);
        int room = right - inner;
        if (font.width(line) > room) {
            while (line.length() > 1 && font.width(line + "...") > room) line = line.substring(0, line.length() - 1);
            line = line.trim() + "...";
        }
        graphics.text(font, Component.literal(line), inner, y + 15, done ? Theme.TEXT_MUTED : Theme.TEXT, true);

        String pay = NumberFormat.getIntegerInstance(Locale.US).format(quest.pay == null ? 0 : quest.pay);
        int coinX = right - 16;
        int payX = coinX - 2 - font.width(pay);
        int rowY = y + CARD - 17;
        VanillaIcon.draw(graphics, VanillaIcon.Kind.COIN, coinX + 8, rowY + 8, 16,
                done ? 0xFF999999 : 0xFFFFFFFF);
        graphics.text(font, Component.literal(pay), payX, rowY + 4, done ? Theme.TEXT_MUTED : GOLD, true);

        int barW = payX - 7 - inner;
        int barY = rowY + 6;
        Theme.roundedBox(graphics, inner, barY, barW, 4, 2, 0xF0101216);
        int filled = done ? barW : (int) Math.floor(barW * (progress / (double) target));
        if (filled >= 2) Theme.roundedBox(graphics, inner, barY, filled, 4, 2, edge);
    }

    public static String clockOf(long ms) {
        return clock(ms);
    }

    static String clock(long ms) {
        long seconds = ms / 1000L;
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m";
        return Math.max(0L, seconds) + "s";
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        if (overFold(mx, my)) {
            if (!this.open && !this.room) {
                Cues.nothingHappened();
                return;
            }
            if (this.open) Cues.questFold(); else Cues.questUnfold();
            this.open = !this.open;
            if (this.onFold != null) this.onFold.run();
            return;
        }
        if (!this.open || this.shown < 1.0F) return;
        if (mx < this.rerollX || mx >= this.rerollX + this.rerollW || my < this.rerollY
                || my >= this.rerollY + BUTTON_H) {
            return;
        }
        Quests.Book book = Quests.book();
        int left = book != null && book.rerolls != null ? book.rerolls : 0;
        boolean open = Quests.current() && book != null && book.list != null
                && java.util.Arrays.stream(book.list).anyMatch(q -> q != null && !Boolean.TRUE.equals(q.done));
        if (!open || left <= 0 || Quests.rerolling()) {
            Cues.deny();
            return;
        }
        Cues.questReroll();
        Quests.reroll(refusal -> {
            if (refusal != null) {
                this.refused = refusal.isBlank() ? Component.translatable("vivet.quests.failed").getString() : refusal;
                this.refusedAt = System.currentTimeMillis();
            }
        });
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
