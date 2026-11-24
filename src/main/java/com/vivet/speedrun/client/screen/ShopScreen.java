package com.vivet.speedrun.client.screen;

import com.vivet.speedrun.client.Cues;
import com.vivet.speedrun.client.ui.Cosmetics;
import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.client.ui.Tip;
import com.vivet.speedrun.client.ui.UiButtons;
import com.vivet.speedrun.client.ui.VanillaIcon;
import com.vivet.speedrun.media.MediaClips;
import com.vivet.speedrun.media.MediaSounds;
import com.vivet.speedrun.p2p.MojangAuth;
import com.vivet.speedrun.p2p.Wardrobe;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ShopScreen extends VivetScreen {
    private static final int PADDING = 14;
    private static final int HEAD_H = 46;
    private static final int PANEL_R = 8;
    private static final int CELL_R = 5;
    private static final int BACK_GAP = 8;

    private static final int GAP = 8;
    private static final int STACK_GAP = 6;

    private static final int SQUARE = 184;
    private static final int WIDE = 222;
    private static final int FULL_ROW = SQUARE * 2 + WIDE + GAP * 2;

    private static final int BANNER_H = 13;
    private static final int BANNER_INSET = 4;
    private static final int BANNER_R = 4;
    private static final int BANNER_FILL = 0xB4080A11;

    private static final int PILL_H = 18;

    private static final int GOLD = 0xFFE8C34A;

    private static final long HOLD_MS = 1000L;

    private static final int BUY_W = 150;
    private static final int BUY_H = 20;

    private final Screen parent;

    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int square;
    private int wide;
    private int wideTop;
    private int wideBottom;
    private int rowTop;

    private final Wardrobe.Item[] placed = new Wardrobe.Item[4];
    private long placedFrom = -1L;

    private String playing;

    private String previewing;
    private long previewSince;

    private boolean holding;
    private long holdingSince;
    private int holdTicks;

    private String pending;

    private Component problem;

    private int buyX;
    private int buyY;

    private int windowX;
    private int windowY;
    private int windowW;
    private int windowH;

    public ShopScreen(Screen parent) {
        super(Component.translatable("vivet.screen.shop"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        Wardrobe.refresh();

        int side = UiButtons.SIDE + BACK_GAP;
        int room = Math.min(FULL_ROW, this.width - 16 - side * 2 - PADDING * 2);

        float scale = Math.min(1.0F, room / (float) FULL_ROW);
        this.square = Math.round(SQUARE * scale);
        this.wide = room - this.square * 2 - GAP * 2;

        this.wideTop = (this.wide - 6) * 120 / 216 + 6;
        this.wideBottom = this.square - this.wideTop - STACK_GAP;

        this.panelWidth = room + PADDING * 2;
        this.panelHeight = PADDING + HEAD_H + this.square + PADDING;
        this.left = (this.width - this.panelWidth) / 2;
        this.top = Math.max(4, (this.height - this.panelHeight) / 2);
        this.rowTop = this.top + PADDING + HEAD_H;

        place();
    }

    @Override
    public void tick() {
        super.tick();
        Wardrobe.refresh();
        if (this.placedFrom != Wardrobe.mine().stamp()) place();

        if (this.holding && this.previewing != null) {
            long held = System.currentTimeMillis() - this.holdingSince;
            int quarter = (int) Math.min(4, held * 4 / HOLD_MS);
            while (this.holdTicks < quarter && this.holdTicks < 3) {
                this.holdTicks++;
                Cues.buyHold(this.holdTicks);
            }
            if (held >= HOLD_MS) {
                this.holding = false;
                if (Wardrobe.buy(this.previewing)) this.pending = this.previewing;
            }
        }

        if (this.pending != null && Wardrobe.buying() == null) {
            String id = this.pending;
            this.pending = null;

            if (Wardrobe.owns(id)) {
                this.problem = null;
                Cues.bought();
            } else {
                String refusal = Wardrobe.refusalFor(id);
                this.problem = Component.translatable(refusal != null
                        && refusal.startsWith("Not enough") ? "vivet.shop.short" : "vivet.shop.failed");
                Cues.deny();
            }
        }
    }

    private void place() {
        java.util.Arrays.fill(this.placed, null);

        List<Wardrobe.Item> squares = new ArrayList<>();
        List<Wardrobe.Item> wides = new ArrayList<>();
        for (Wardrobe.Item item : Wardrobe.forSale()) {
            if ("background".equals(item.kind) || "label".equals(item.kind)) wides.add(item);
            else squares.add(item);
        }
        squares.sort(java.util.Comparator.comparingInt(item -> "audio".equals(item.kind) ? 1 : 0));
        wides.sort(java.util.Comparator.comparingInt(item -> "label".equals(item.kind) ? 1 : 0));

        if (!squares.isEmpty()) this.placed[0] = squares.get(0);
        if (!wides.isEmpty()) this.placed[1] = wides.get(0);
        if (wides.size() > 1) this.placed[2] = wides.get(1);
        if (squares.size() > 1) this.placed[3] = squares.get(1);

        this.placedFrom = Wardrobe.mine().stamp();
    }

    private int[] boxOf(int i) {
        int x = this.left + PADDING;
        int middle = x + this.square + GAP;
        return switch (i) {
            case 0 -> new int[] { x, this.rowTop, this.square, this.square };
            case 1 -> new int[] { middle, this.rowTop, this.wide, this.wideTop };
            case 2 -> new int[] { middle, this.rowTop + this.wideTop + STACK_GAP, this.wide,
                    this.wideBottom };
            default -> new int[] { middle + this.wide + GAP, this.rowTop, this.square,
                    this.square };
        };
    }

    @Override
    protected void drawScaled(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                              float partialTick) {
        Theme.fill(graphics, 0, 0, this.width, this.height, Theme.SCRIM);

        Theme.roundedBox(graphics, this.left, this.top, this.panelWidth, this.panelHeight, PANEL_R,
                Theme.PANEL);
        Theme.roundedBoxOutline(graphics, this.left, this.top, this.panelWidth, this.panelHeight,
                PANEL_R, Theme.BORDER);

        boolean open = this.previewing != null;
        int mx = open ? -1 : mouseX;
        int my = open ? -1 : mouseY;

        UiButtons.button(graphics, UiButtons.Kind.BACK, backX(), this.top, onBack(mx, my), true,
                false);

        drawHeader(graphics);

        if (!Wardrobe.known()) {
            Component waiting = Component.translatable(MojangAuth.token() == null
                    ? "vivet.locker.signed_out" : "vivet.locker.waiting");
            graphics.text(this.font, waiting, this.left + PADDING + 2, this.rowTop + 6,
                    Theme.TEXT_FAINT);
        } else {
            for (int i = 0; i < this.placed.length; i++) {
                if (this.placed[i] != null) drawCard(graphics, this.placed[i], boxOf(i), mx, my);
            }
        }

        drawWidgets(graphics, mouseX, mouseY, partialTick);

        if (open) drawPreview(graphics, mouseX, mouseY);
    }

    private void drawHeader(GuiGraphicsExtractor graphics) {
        int x = this.left + PADDING;
        int y = this.top + PADDING;
        int right = this.left + this.panelWidth - PADDING;

        graphics.text(this.font, this.title.copy().withStyle(ChatFormatting.BOLD), x, y + 5,
                Theme.TEXT);

        Integer coins = MojangAuth.coins();
        if (coins != null) {
            String amount = grouped(coins);
            int boxW = Math.max(60, this.font.width(amount) + 14);
            int boxX = right - boxW;
            Theme.roundedBox(graphics, boxX, y, boxW, 18, 4, Theme.PANEL_SUNKEN);
            Theme.roundedBoxOutline(graphics, boxX, y, boxW, 18, 4, Theme.BORDER);
            graphics.text(this.font, Component.literal(amount), boxX + 7, y + 5, GOLD);
            VanillaIcon.draw(graphics, VanillaIcon.Kind.COIN, boxX - 12, y + 9, 16);
        }

        Theme.divider(graphics, x, y + 24, this.panelWidth - PADDING * 2);

        Component hint = Component.translatable("vivet.shop.hint");
        graphics.text(this.font, hint, right - this.font.width(hint), y + 30, Theme.TEXT_FAINT);
    }

    private void drawCard(GuiGraphicsExtractor graphics, Wardrobe.Item item, int[] box, int mouseX,
                          int mouseY) {
        int x = box[0];
        int y = box[1];
        int w = box[2];
        int h = box[3];

        boolean over = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        boolean owned = Wardrobe.owns(item.id);
        int accent = Cosmetics.tierColour(Cosmetics.rarityOf(item));

        Theme.roundedBox(graphics, x, y, w, h, CELL_R,
                Theme.mix(Theme.PANEL_SUNKEN, accent, over ? 0.14F : 0.07F));

        Cosmetics.ready(item);
        drawArt(graphics, item, x, y, w, h, false);

        drawPill(graphics, item, x + w - BANNER_INSET, y + BANNER_INSET, owned);
        drawBanner(graphics, x, y, w, h, Cosmetics.nameOf(item), accent);

        Theme.roundedBoxOutline(graphics, x, y, w, h, CELL_R,
                over ? accent : Theme.withAlpha(accent, 0x88));
    }

    private void drawArt(GuiGraphicsExtractor graphics, Wardrobe.Item item, int x, int y, int w,
                         int h, boolean preview) {
        String kind = item.kind == null ? "" : item.kind;
        long into = into(item, preview);

        switch (kind) {
            case "gif" -> {
                int box = Math.min(w, h) - 14;
                Cosmetics.draw(graphics, this.font, item, x + (w - box) / 2, y + (h - box) / 2,
                        box, box, into, true, false);
            }
            case "background" -> Cosmetics.draw(graphics, this.font, item, x + 2, y + 2, w - 4,
                    h - 4, into, true, false);
            case "audio" -> {
                int box = Math.round(Math.min(w, h) * 0.6F);
                Cosmetics.draw(graphics, this.font, item, x + (w - box) / 2,
                        y + (h - box) / 2 - (preview ? 0 : 4), box, box, into, true, false);
            }
            case "label" -> {
                int top = preview ? y : y + PILL_H + BANNER_INSET;
                int room = preview ? h : h - PILL_H - BANNER_INSET - BANNER_H - BANNER_INSET;
                Cosmetics.draw(graphics, this.font, item, x + 4, top, w - 8, room, into, true,
                        !preview);
            }
            default -> Cosmetics.draw(graphics, this.font, item, x + 4, y + 4, w - 8, h - 8, into,
                    true, false);
        }
    }

    private long into(Wardrobe.Item item, boolean preview) {
        if (!"gif".equals(item.kind) || !preview) return 0L;

        long elapsed = System.currentTimeMillis() - this.previewSince;
        String media = Cosmetics.mediaOf(item.id);
        int length = media == null ? 0 : MediaClips.lengthMs(media);
        return length <= 0 ? elapsed : elapsed % length;
    }

    private void drawPill(GuiGraphicsExtractor graphics, Wardrobe.Item item, int right, int y,
                          boolean owned) {
        if (owned) {
            Component word = Component.translatable("vivet.shop.owned");
            int w = this.font.width(word) + 12;
            Theme.roundedBox(graphics, right - w, y, w, PILL_H, BANNER_R, BANNER_FILL);
            graphics.text(this.font, word, right - w + 6, y + 5, Theme.SUCCESS);
            return;
        }
        if (item.price == null) return;

        String price = grouped(item.price);
        int w = 16 + 2 + this.font.width(price) + 10;
        Theme.roundedBox(graphics, right - w, y, w, PILL_H, BANNER_R, BANNER_FILL);
        VanillaIcon.draw(graphics, VanillaIcon.Kind.COIN, right - w + 3 + 8, y + PILL_H / 2, 16);
        graphics.text(this.font, Component.literal(price), right - w + 3 + 16 + 2, y + 5, GOLD);
    }

    private void drawBanner(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                            Component label, int colour) {
        int bw = width - BANNER_INSET * 2;
        int bx = x + BANNER_INSET;
        int by = y + height - BANNER_H - BANNER_INSET;

        Theme.roundedBox(graphics, bx, by, bw, BANNER_H, BANNER_R, BANNER_FILL);

        com.vivet.speedrun.client.ui.Marquee.drawSpotifyCentred(graphics, this.font,
                Component.literal(label.getString()), bx + bw / 2, by + 3, bw - 6, colour);
    }

    private static int[] stageOf(String kind) {
        return switch (kind == null ? "" : kind) {
            case "gif" -> new int[] { 184, 184 };
            case "background" -> new int[] { 222, 126 };
            case "audio" -> new int[] { 160, 160 };
            case "label" -> new int[] { 222, 36 };
            default -> new int[] { 160, 100 };
        };
    }

    private void drawPreview(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Wardrobe.Item item = Wardrobe.item(this.previewing);
        if (item == null) {
            this.previewing = null;
            return;
        }

        Theme.fill(graphics, 0, 0, this.width, this.height, 0xC4060810);

        boolean owned = Wardrobe.owns(item.id);
        int accent = Cosmetics.tierColour(Cosmetics.rarityOf(item));

        List<Tip.Line> lines = Tip.lines();
        Component rarity = Component.literal(Cosmetics.tierName(Cosmetics.rarityOf(item)).getString()
                .toUpperCase(Locale.ROOT)).withStyle(ChatFormatting.BOLD);
        lines.add(new Tip.Line(Component.translatable("vivet.locker.tier_line", rarity,
                Wardrobe.setName(item.set)), accent));
        Component flavour = Cosmetics.tipOf(item, null);
        if (flavour != null) Tip.wrap(lines, flavour, Theme.TEXT_MUTED, this.font);

        int[] stage = stageOf(item.kind);
        int width = Math.max(260, stage[0] + 24);
        int height = 30 + stage[1] + 10 + lines.size() * (this.font.lineHeight + 2)
                + 10 + BUY_H + 8 + (this.problem != null ? 12 : 0) + 12;

        int x = (this.width - width) / 2;
        int y = Math.max(4, (this.height - height) / 2);
        this.windowX = x;
        this.windowY = y;
        this.windowW = width;
        this.windowH = height;

        Theme.roundedBox(graphics, x, y, width, height, PANEL_R, Theme.PANEL_RAISED);
        Theme.roundedBoxOutline(graphics, x, y, width, height, PANEL_R,
                Theme.withAlpha(accent, 0x99));

        Component name = Cosmetics.nameOf(item);
        graphics.text(this.font, name.copy().withStyle(ChatFormatting.BOLD),
                x + (width - this.font.width(name)) / 2, y + 12, accent);

        int stageX = x + (width - stage[0]) / 2;
        int stageY = y + 30;
        Theme.roundedBox(graphics, stageX, stageY, stage[0], stage[1], CELL_R, Theme.PANEL_SUNKEN);
        Cosmetics.ready(item);
        drawArt(graphics, item, stageX, stageY, stage[0], stage[1], true);

        int line = stageY + stage[1] + 10;
        for (Tip.Line entry : lines) {
            Component shown = entry.text();
            if (this.font.width(shown) > width - 24) {
                shown = Component.literal(this.font.plainSubstrByWidth(shown.getString(),
                        width - 24));
            }
            graphics.text(this.font, shown, x + (width - this.font.width(shown)) / 2, line,
                    entry.colour());
            line += this.font.lineHeight + 2;
        }

        this.buyX = x + (width - BUY_W) / 2;
        this.buyY = line + 10;
        drawBuy(graphics, item, owned, mouseX, mouseY);

        if (this.problem != null) {
            graphics.text(this.font, this.problem,
                    x + (width - this.font.width(this.problem)) / 2, this.buyY + BUY_H + 6,
                    Theme.DANGER);
        }
    }

    private void drawBuy(GuiGraphicsExtractor graphics, Wardrobe.Item item, boolean owned,
                         int mouseX, int mouseY) {
        int bx = this.buyX;
        int by = this.buyY;

        if (owned) {
            Theme.roundedBox(graphics, bx, by, BUY_W, BUY_H, 4, Theme.PANEL_SUNKEN);
            graphics.centeredText(this.font, Component.translatable("vivet.shop.owned"),
                    bx + BUY_W / 2, by + 6, Theme.SUCCESS);
            return;
        }

        boolean busy = item.id.equals(Wardrobe.buying());
        boolean afford = canAfford(item);
        boolean over = onBuy(mouseX, mouseY);

        int base = afford ? Theme.withAlpha(Theme.SUCCESS, over || this.holding ? 0x66 : 0x40)
                : Theme.PANEL_SUNKEN;
        Theme.roundedBox(graphics, bx, by, BUY_W, BUY_H, 4, base);

        if (this.holding || busy) {
            float held = busy ? 1.0F
                    : Math.min(1.0F, (System.currentTimeMillis() - this.holdingSince)
                            / (float) HOLD_MS);
            int reach = Math.round(BUY_W * held);
            if (reach > 0) Theme.roundedBox(graphics, bx, by, reach, BUY_H, 4, Theme.SUCCESS);
        }
        Theme.roundedBoxOutline(graphics, bx, by, BUY_W, BUY_H, 4,
                afford ? Theme.SUCCESS : Theme.BORDER);

        Component words = Component.translatable(busy ? "vivet.shop.buying" : "vivet.shop.hold");
        String price = item.price == null ? "" : grouped(item.price);
        int wordsW = this.font.width(words);
        int total = wordsW + 8 + 16 + 2 + this.font.width(price);
        int tx = bx + (BUY_W - total) / 2;

        graphics.text(this.font, words, tx, by + 6, afford ? 0xFFFFFFFF : Theme.TEXT_MUTED);
        VanillaIcon.draw(graphics, VanillaIcon.Kind.COIN, tx + wordsW + 8 + 8, by + BUY_H / 2, 16);
        graphics.text(this.font, Component.literal(price), tx + wordsW + 8 + 16 + 2, by + 6,
                afford ? GOLD : Theme.DANGER);
    }

    private boolean canAfford(Wardrobe.Item item) {
        Integer coins = MojangAuth.coins();
        return item.price != null && coins != null && coins >= item.price;
    }

    private int scaled(double value) {
        return (int) (value / uiScale());
    }

    private Wardrobe.Item cardAt(int mouseX, int mouseY) {
        if (!Wardrobe.known()) return null;
        for (int i = 0; i < this.placed.length; i++) {
            if (this.placed[i] == null) continue;
            int[] box = boxOf(i);
            if (mouseX >= box[0] && mouseX < box[0] + box[2]
                    && mouseY >= box[1] && mouseY < box[1] + box[3]) {
                return this.placed[i];
            }
        }
        return null;
    }

    private boolean onBuy(int mouseX, int mouseY) {
        return mouseX >= this.buyX && mouseX < this.buyX + BUY_W
                && mouseY >= this.buyY && mouseY < this.buyY + BUY_H;
    }

    private boolean inWindow(int mouseX, int mouseY) {
        return mouseX >= this.windowX && mouseX < this.windowX + this.windowW
                && mouseY >= this.windowY && mouseY < this.windowY + this.windowH;
    }

    private void play(Wardrobe.Item item) {
        String media = Cosmetics.mediaOf(item.id);
        if (media == null) return;
        MediaSounds.stopAll();
        MediaSounds.play(media, 1.0F);
        this.playing = item.id;
    }

    private void open(Wardrobe.Item item) {
        this.previewing = item.id;
        this.previewSince = System.currentTimeMillis();
        this.problem = null;
        this.holding = false;
        Cues.confirm();
        if ("audio".equals(item.kind)) play(item);
    }

    private void closePreview() {
        this.previewing = null;
        this.holding = false;
        this.problem = null;
        MediaSounds.stopAll();
        this.playing = null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int x = scaled(event.x());
        int y = scaled(event.y());

        if (this.previewing != null) {
            Wardrobe.Item item = Wardrobe.item(this.previewing);

            if (item != null && event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && onBuy(x, y) && !Wardrobe.owns(item.id)) {
                if (Wardrobe.buying() != null) return true;
                if (!canAfford(item)) {
                    this.problem = Component.translatable("vivet.shop.short");
                    Cues.deny();
                    return true;
                }
                this.problem = null;
                this.holding = true;
                this.holdingSince = System.currentTimeMillis();
                this.holdTicks = 0;
                return true;
            }

            if (!inWindow(x, y)) {
                closePreview();
                Cues.confirm();
            }
            return true;
        }

        if (onBack(x, y)) {
            Cues.confirm();
            onClose();
            return true;
        }

        Wardrobe.Item item = cardAt(x, y);
        if (item != null && event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
            open(item);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.holding) {
            this.holding = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE && this.previewing != null) {
            closePreview();
            return true;
        }
        return super.keyPressed(event);
    }

    private int backX() {
        return this.left + this.panelWidth + BACK_GAP;
    }

    private boolean onBack(int mouseX, int mouseY) {
        return mouseX >= backX() && mouseX < backX() + UiButtons.SIDE
                && mouseY >= this.top && mouseY < this.top + UiButtons.SIDE;
    }

    private static String grouped(int value) {
        return NumberFormat.getIntegerInstance(Locale.US).format(value);
    }

    @Override
    public void onClose() {
        MediaSounds.stopAll();
        this.minecraft.gui.setScreen(this.parent);
    }
}
