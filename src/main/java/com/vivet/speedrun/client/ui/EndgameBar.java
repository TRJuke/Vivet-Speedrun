package com.vivet.speedrun.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class EndgameBar {
    public static final int HEIGHT = 32;

    private static final int EMBLEM = 30;

    private static final float CAP_SCALE = 1.5F;

    private static final int TRACK_LEAST = 48;

    private static final int TRACK = 12;
    private static final int TRACK_RADIUS = 6;

    private static final int CAP_GAP = 8;

    private static final long SHAKE_MS = 90L;

    private static final float SHAKE_FROM = 0.20F;
    private static final float SHAKE_TO = 0.70F;

    public record Cap(String rankId, boolean placed, Integer level, String text) {
        public static Cap rank(String rankId, boolean placed) {
            return new Cap(rankId, placed, null, null);
        }

        public static Cap level(int level) {
            return new Cap(null, false, level, null);
        }

        public static Cap text(String text) {
            return new Cap(null, false, null, text == null ? "" : text);
        }

        public static Cap milestone(int place) {
            String title = Ranks.titleAtPlace(place);
            return title == null ? text("Top " + place) : rank(title, true);
        }
    }

    private final Cap leftBefore;
    private final Cap rightBefore;
    private final Cap leftAfter;
    private final Cap rightAfter;

    private final float from;
    private final float to;

    private final int crossing;

    private final int restColor;

    private final boolean moves;

    public EndgameBar(Cap leftBefore, Cap rightBefore, Cap leftAfter, Cap rightAfter,
                      float from, float to, int crossing, int restColor, boolean moves) {
        this.leftBefore = leftBefore;
        this.rightBefore = rightBefore;
        this.leftAfter = leftAfter == null ? leftBefore : leftAfter;
        this.rightAfter = rightAfter == null ? rightBefore : rightAfter;
        this.from = clamp(from);
        this.to = clamp(to);
        this.crossing = crossing;
        this.restColor = restColor;
        this.moves = moves;
    }

    public static EndgameBar of(Cap left, Cap right, float from, float to, int restColor) {
        return new EndgameBar(left, right, left, right, from, to, 0, restColor, true);
    }

    public static EndgameBar still(Cap left, Cap right, float at, int restColor) {
        return new EndgameBar(left, right, left, right, at, at, 0, restColor, false);
    }

    public int crossing() {
        return this.crossing;
    }

    public boolean rises() {
        return this.crossing > 0 || (this.crossing == 0 && this.to > this.from);
    }

    private float fillAt(float progress) {
        if (!this.moves) return this.from;
        if (this.crossing == 0) return this.from + (this.to - this.from) * progress;

        float edge = this.crossing > 0 ? 1.0F : 0.0F;
        float start = this.crossing > 0 ? 0.0F : 1.0F;

        if (progress < 0.5F) return this.from + (edge - this.from) * (progress / 0.5F);
        return start + (this.to - start) * ((progress - 0.5F) / 0.5F);
    }

    private boolean showsAfter(float progress) {
        if (!this.moves || this.crossing == 0) return true;
        return progress >= 0.5F;
    }

    private boolean moving(float progress) {
        return this.moves && progress < 1.0F && this.from != this.to;
    }

    public void draw(GuiGraphicsExtractor graphics, Font font, int x, int y, int width,
                     float progress) {
        float fill = fillAt(progress);
        boolean moving = moving(progress);
        int jitter = shake(moving, progress);

        boolean after = showsAfter(progress);
        Cap left = after ? this.leftAfter : this.leftBefore;
        Cap right = after ? this.rightAfter : this.rightBefore;

        int colour = left.level() != null ? Levels.colorOf(left.level())
                : moving ? (rises() ? Theme.SUCCESS : Theme.DANGER) : this.restColor;

        float capScale = capScale(font, left, right, width);
        int leftW = capWidth(font, left, capScale);
        int rightW = capWidth(font, right, capScale);

        cap(graphics, font, left, x, y, leftW, Theme.TEXT, capScale);
        cap(graphics, font, right, x + width - rightW, y, rightW, Theme.TEXT, capScale);

        int trackX = x + leftW + CAP_GAP;
        int trackW = Math.max(24, width - leftW - rightW - CAP_GAP * 2);
        track(graphics, trackX, y + (HEIGHT - TRACK) / 2 + jitter, trackW, fill, colour, moving);
    }

    private static int capWidth(Font font, Cap cap, float scale) {
        if (cap.rankId() != null) return EMBLEM;
        return BigText.widthAt(font, Component.literal(labelOf(cap)), scale);
    }

    private static float capScale(Font font, Cap left, Cap right, int width) {
        int taken = capWidth(font, left, CAP_SCALE) + capWidth(font, right, CAP_SCALE) + CAP_GAP * 2;
        return width - taken >= TRACK_LEAST ? CAP_SCALE : 1.0F;
    }

    private static String labelOf(Cap cap) {
        if (cap.level() != null) return "Lvl " + cap.level();
        return cap.text() == null ? "" : cap.text();
    }

    private void cap(GuiGraphicsExtractor graphics, Font font, Cap cap, int x, int y, int width,
                     int colour, float scale) {
        if (cap.rankId() != null) {
            RankEmblem.drawAt(graphics, x + width / 2, y + HEIGHT / 2, cap.rankId(), cap.placed(),
                    EMBLEM);
            return;
        }

        Component line = Component.literal(labelOf(cap));
        int ink = cap.level() != null ? Levels.colorOf(cap.level()) : colour;
        BigText.drawAt(graphics, font, line, x, y + (HEIGHT - BigText.heightAt(scale)) / 2, ink,
                scale, 0);
    }

    private void track(GuiGraphicsExtractor graphics, int x, int y, int width, float fill,
                       int color, boolean moving) {
        Theme.roundedBox(graphics, x, y, width, TRACK, TRACK_RADIUS, 0x66000000);

        int span = width - 2;
        int filled = Math.round(Math.max(0.0F, Math.min(1.0F, fill)) * span);
        if (filled <= 1) return;

        Theme.roundedBox(graphics, x + 1, y + 1, filled, TRACK - 2, TRACK_RADIUS - 1, color);

        Theme.roundedBox(graphics, x + 1, y + 1, filled, Math.max(1, (TRACK - 2) / 3),
                TRACK_RADIUS - 1, Theme.withAlpha(Theme.mix(color, 0xFFFFFFFF, 0.30F), 120));

        if (moving && filled < span) {
            int lead = x + 1 + filled;
            Theme.fill(graphics, lead - 1, y + 1, lead, y + TRACK - 1,
                    Theme.withAlpha(0xFFFFFFFF, 230));
            for (int i = 1; i <= 6; i++) {
                Theme.fill(graphics, lead - 1 - i, y + 1, lead - i, y + TRACK - 1,
                        Theme.withAlpha(0xFFFFFFFF, Math.round(150 * (1.0F - i / 7.0F))));
            }
        }
    }

    private int shake(boolean moving, float progress) {
        if (!moving || progress < SHAKE_FROM || progress > SHAKE_TO) return 0;

        float span = Math.max(0.001F, SHAKE_TO - SHAKE_FROM);
        float inside = (progress - SHAKE_FROM) / span;
        int density = 1 + Math.round(2.0F * (1.0F - Math.abs(inside - 0.5F) * 2.0F));

        long step = System.currentTimeMillis() / SHAKE_MS;
        return (step % 7) < density ? ((step & 1L) == 0L ? -1 : 1) : 0;
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
