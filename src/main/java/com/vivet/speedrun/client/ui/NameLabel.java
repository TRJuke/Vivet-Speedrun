package com.vivet.speedrun.client.ui;

import com.vivet.speedrun.p2p.MojangAuth;
import com.vivet.speedrun.session.Session;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;

import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public enum NameLabel {
    BURNING_ALIVE("burning_alive", "AHHH",
            look(Colour.EMBER).speed(400).breathing()
                    .with(Ornament.SPARKS, Ornament.RAIN, Ornament.FLAMES, Ornament.MOTES),
            0xFFFFFFFF, 0xFFFF5900, 0xFFFF0000),

    HAPPY_BIRTHDAY_DANIEL("happy_birthday_daniel", "AURA MONSTER",
            look(Colour.EMBER).speed(400).bloom(120)
                    .with(Ornament.OUTLINE, Ornament.WAVE, Ornament.SPARKS,
                            Ornament.RAIN, Ornament.MOTES),
            0xFF00FF00, 0xFF009E00, 0xFF005200, 0xFF009E00, 0xFF00FF00),

    REDDITOR("redditor", "REDDITOR",
            look(Colour.SHINE).underline().breathing()
                    .with(Ornament.OUTLINE, Ornament.GLITCH),
            0xFFFF4400, 0xFFFFFFFF, 0xFFFF4400),

    WEEB("weeb", "WEEB",
            look(Colour.SHINE).neon(27).breathing()
                    .with(Ornament.OUTLINE, Ornament.SPARKS, Ornament.MOTES, Ornament.COMET),
            0xFFFF00F7, 0xFF2BFF00),

    FRIENDS("friends", "I<3FRIENDS",
            look(Colour.FLOW).speed(210).bloom(35).with(Ornament.WAVE),
            0xFFBB00FF, 0xFFDC7AFF, 0xFFBB00FF),

    OPSEC("opsec", "OPSEC GOD",
            look(Colour.EMBER).speed(210).scan(35).stepped()
                    .with(Ornament.OUTLINE, Ornament.GLITCH),
            0xFF2BFF00, 0xFF000000),

    ECONOMIST("economist", "ECONOMIST",
            look(Colour.STATIC),
            0xFFD053F4),

    SPEEDRUNNER("speedrunner", "SPEEDRUNNER",
            look(Colour.STATIC),
            0xFF7CC4FF),

    DISCORDIAN("discordian", "DISCORDIAN",
            look(Colour.FLOW).speed(210),
            0xFF5865F2, 0xFFA5B1FF, 0xFF5865F2),

    BOOSTER("booster", "BOOSTER",
            look(Colour.SHINE).breathing().with(Ornament.SPARKS, Ornament.MOTES),
            0xFFF47FFF, 0xFFFFD6FF, 0xFFF47FFF),

    LOREM_IPSUM("lorem_ipsum", "LOREM IPSUM",
            look(Colour.STATIC),
            0xFFFFFFFF),

    RANK("rank", "RANK",
            look(Colour.STATIC),
            0xFFFFFFFF);

    public enum Colour {
        STATIC,

        FLOW,

        CHROMA,

        PULSE,

        SHINE,

        EMBER,

        VERTICAL;

        boolean bandsDown() {
            return this == EMBER || this == VERTICAL;
        }
    }

    public enum Aura {
        NONE,

        HALO,

        NEON,

        BLOOM,

        UNDERLINE,

        SCAN
    }

    public enum Ornament {
        OUTLINE,

        WAVE,

        SPARKS,

        RAIN,

        FLAMES,

        MOTES,

        WINGS,

        GLITCH,

        COMET
    }

    static final class Look {
        final Colour colour;
        final EnumSet<Ornament> ornaments = EnumSet.noneOf(Ornament.class);
        Aura aura = Aura.NONE;
        int speed = 100;
        int spread = 60;
        int auraStrength = 46;
        boolean breath;
        boolean smooth = true;

        Look(Colour colour) {
            this.colour = colour;
        }

        Look speed(int percent) {
            this.speed = percent;
            return this;
        }

        Look neon(int strength) {
            return aura(Aura.NEON, strength);
        }

        Look bloom(int strength) {
            return aura(Aura.BLOOM, strength);
        }

        Look underline() {
            return aura(Aura.UNDERLINE, this.auraStrength);
        }

        Look scan(int strength) {
            return aura(Aura.SCAN, strength);
        }

        private Look aura(Aura kind, int strength) {
            this.aura = kind;
            this.auraStrength = strength;
            return this;
        }

        Look breathing() {
            this.breath = true;
            return this;
        }

        Look stepped() {
            this.smooth = false;
            return this;
        }

        Look with(Ornament... wanted) {
            Collections.addAll(this.ornaments, wanted);
            return this;
        }

        boolean has(Ornament ornament) {
            return this.ornaments.contains(ornament);
        }
    }

    private static Look look(Colour colour) {
        return new Look(colour);
    }

    private static final long SHIMMER_PERIOD_MS = 2400L;

    private static final long ORBIT_PERIOD_MS = 3600L;

    private static final long COMET_PERIOD_MS = 2600L;
    private static final int COMET_TAIL = 17;

    private static final long BREATH_PERIOD_MS = 2000L;

    private static final int SPARKS = 4;

    private static final int GLYPH_ROWS = 8;

    private static final int OUTLINE_COLOUR = 0xE0080A0F;

    private static final int[][] OUTLINE_KERNEL = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1},
    };

    private static final long GLITCH_STEP_MS = 85L;
    private static final int GLITCH_RUN = 8;

    private static final int GLITCH_RED = 0xFFFF3355;
    private static final int GLITCH_CYAN = 0xFF33FFEE;

    private static final int GLITCH_BAND_ROWS = 2;

    private static final int[] BEAT = {0, 1, 2, 2, 1, 0, -1, -1};

    private static final int[][] NEON_KERNEL = disc(2, 0x80);
    private static final int[][] BLOOM_KERNEL = disc(3, 0x4A);

    private static int[][] disc(int radius, int base) {
        java.util.List<int[]> steps = new java.util.ArrayList<>();
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d < 0.5 || d > radius + 0.2) continue;

                int alpha = (int) Math.round(base * Math.pow(1 - d / (radius + 0.9), 1.5));

                if (alpha < 6) continue;
                steps.add(new int[]{dx, dy, alpha});
            }
        }
        return steps.toArray(new int[0][]);
    }

    private static final String[] ONE_CHAR = new String[128];

    static {
        for (int i = 0; i < ONE_CHAR.length; i++) ONE_CHAR[i] = String.valueOf((char) i);
    }

    private static String charAt(String text, int index) {
        char ch = text.charAt(index);
        return ch < ONE_CHAR.length ? ONE_CHAR[ch] : String.valueOf(ch);
    }

    private static final Component[] BOLD_CHAR = new Component[128];

    static {
        for (int i = 0; i < BOLD_CHAR.length; i++) {
            BOLD_CHAR[i] = Component.literal(ONE_CHAR[i]).withStyle(ChatFormatting.BOLD);
        }
    }

    private static void glyph(GuiGraphicsExtractor graphics, Font font, String glyph, int x, int y,
                              int colour, boolean bold, boolean shadow) {
        if (!bold) {
            graphics.text(font, glyph, x, y, colour, shadow);
            return;
        }
        char ch = glyph.length() == 1 ? glyph.charAt(0) : 0xFFFF;
        Component text = ch < BOLD_CHAR.length ? BOLD_CHAR[ch]
                : Component.literal(glyph).withStyle(ChatFormatting.BOLD);
        graphics.text(font, text, x, y, colour, shadow);
    }

    private final String id;
    private final String tag;
    private final Look look;
    private final int[] ramp;

    NameLabel(String id, String tag, Look look, int... ramp) {
        this.id = id;
        this.tag = tag;
        this.look = look;
        this.ramp = ramp;
    }

    public String id() {
        return this.id;
    }

    public int nameColor() {
        return nameColor(null);
    }

    public int nameColor(String name) {
        return this == RANK ? RankTitle.colourFor(name) : this.ramp[0];
    }

    public static NameLabel byId(String id) {
        if (id == null || id.isBlank()) return null;

        for (NameLabel label : values()) {
            if (label.id.equalsIgnoreCase(id)) return label;
        }
        return null;
    }

    public static NameLabel of(String name) {
        if (name == null || name.isBlank()) return null;

        MojangAuth.Profile me = MojangAuth.knownProfile();
        if (me != null && name.equalsIgnoreCase(me.name)) {
            NameLabel mine = byId(me.label);
            if (mine != null) return mine;
        }

        return byId(Session.labelIdOf(name));
    }

    private int tint(float across, int row, int seed, long now) {
        float period = Math.max(120.0F, SHIMMER_PERIOD_MS * 100.0F / Math.max(10, this.look.speed));
        float at = (now % (long) period) / period;

        return switch (this.look.colour) {
            case FLOW -> sample(wrap(across + at));
            case CHROMA -> chroma(wrap(across * (this.look.spread / 100.0F) + at));
            case PULSE -> sample(triangle(at));
            case SHINE -> {
                float centre = -0.25F + 1.5F * at;
                int base = sample(clamp(across));
                float gap = Math.abs(across - centre);
                yield gap < 0.14F ? Theme.mix(base, 0xFFFFFFFF, 1 - gap / 0.14F) : base;
            }
            case EMBER -> {
                float up = 1.0F - row / (float) (GLYPH_ROWS - 1);
                yield sample(clamp(up * 0.85F + noise(seed, now / 70L) * 0.35F));
            }
            case VERTICAL -> sample(row / (float) (GLYPH_ROWS - 1));
            default -> sample(clamp(across));
        };
    }

    private int sample(float at) {
        if (this.ramp.length == 1) return this.ramp[0];

        float scaled = clamp(at) * (this.ramp.length - 1);
        int index = Math.min((int) scaled, this.ramp.length - 2);
        return Theme.mix(this.ramp[index], this.ramp[index + 1], scaled - index);
    }

    private static int chroma(float hue) {
        float x = wrap(hue) * 6.0F;
        int slice = (int) x;
        float f = x - slice;
        float p = 0.08F, q = 1.0F - 0.92F * f, t = 1.0F - 0.92F * (1.0F - f);

        return switch (slice % 6) {
            case 0 -> rgb(1.0F, t, p);
            case 1 -> rgb(q, 1.0F, p);
            case 2 -> rgb(p, 1.0F, t);
            case 3 -> rgb(p, q, 1.0F);
            case 4 -> rgb(t, p, 1.0F);
            default -> rgb(1.0F, p, q);
        };
    }

    private static int rgb(float r, float g, float b) {
        return 0xFF000000 | (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8)
                | Math.round(b * 255);
    }

    private static float wrap(float at) {
        return at - (float) Math.floor(at);
    }

    private static float clamp(float at) {
        return at < 0.0F ? 0.0F : at > 1.0F ? 1.0F : at;
    }

    private static float triangle(float at) {
        return at < 0.5F ? at * 2.0F : (1.0F - at) * 2.0F;
    }

    private static float pulse(long periodMs) {
        return triangle((System.currentTimeMillis() % periodMs) / (float) periodMs);
    }

    private static float noise(int a, long b) {
        int h = a * 374761393 + (int) b * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h = h ^ (h >>> 16);
        return (h >>> 8) / (float) (1 << 24);
    }

    public static int runWidthOf(Font font, String text) {
        return text == null ? 0 : runWidth(font, text, false);
    }

    private static int runWidth(Font font, String text, boolean bold) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            width += font.width(charAt(text, i)) + (bold ? 1 : 0);
        }
        return width;
    }

    private void pass(GuiGraphicsExtractor graphics, Font font, String text, int runX, int runY,
                      int runWidth, long now, boolean bold, int offsetX, int offsetY, int row,
                      int override, int alpha, boolean shadow) {
        int cursor = runX;

        for (int i = 0; i < text.length(); i++) {
            String glyph = charAt(text, i);
            int advance = font.width(glyph) + (bold ? 1 : 0);
            int centre = cursor + advance / 2;

            int colour = override != 0
                    ? override
                    : tint((centre - runX) / (float) Math.max(1, runWidth), row, centre, now);
            if (alpha < 0xFF) colour = Theme.withAlpha(colour, alpha);

            int lift = liftOf(i, now);

            int x = cursor + offsetX;
            int y = runY + offsetY + lift;

            glyph(graphics, font, glyph, x, y, colour, bold, shadow);
            cursor += advance;
        }
    }

    private int[] glitchShifts(long now) {
        if (!this.look.has(Ornament.GLITCH)) return null;

        long step = now / GLITCH_STEP_MS;
        if (noise((int) (step / GLITCH_RUN), 91) > 0.34F) return null;

        int[] shifts = new int[GLYPH_ROWS / GLITCH_BAND_ROWS];
        for (int band = 0; band < shifts.length; band++) {
            float h = noise(band, step);
            shifts[band] = h < 0.3F ? Math.round(h * 40) % 5 - 2 : 0;
        }
        return shifts;
    }

    private void sliced(GuiGraphicsExtractor graphics, Font font, String text, int runX, int runY,
                        int runWidth, long now, boolean bold, int offsetX, int offsetY,
                        int override, int alpha, boolean shadow, int[] shifts) {
        Matrix3x2fStack pose = graphics.pose();

        float scaleX = pose.m00();
        float scaleY = pose.m11();
        float shiftX = pose.m20();
        float shiftY = pose.m21();

        int left = Mth.floor(scaleX * (runX - 8) + shiftX);
        int right = Mth.ceil(scaleX * (runX + runWidth + 8) + shiftX);
        int top = Mth.floor(scaleY * (runY - 1) + shiftY);
        int bottom = Mth.ceil(scaleY * (runY + GLYPH_ROWS + 1) + shiftY);

        int tall = Math.max(1, bottom - top);
        int strips = Math.max(1, Math.min(shifts.length, tall));

        for (int strip = 0; strip < strips; strip++) {
            int stripTop = top + tall * strip / strips;
            int stripBottom = top + tall * (strip + 1) / strips;
            if (stripBottom <= stripTop) continue;

            scissorPixels(graphics, left, stripTop, right, stripBottom);
            pass(graphics, font, text, runX, runY, runWidth, now, bold,
                    offsetX + shifts[strip * shifts.length / strips], offsetY,
                    GLYPH_ROWS / 2, override, alpha, shadow);
            Clip.off(graphics);
        }
    }

    private void letters(GuiGraphicsExtractor graphics, Font font, String text, int runX, int runY,
                         int runWidth, long now, boolean bold, int offsetX, int offsetY,
                         int override, int alpha, boolean shadow, int[] shifts) {
        if (shifts == null) {
            pass(graphics, font, text, runX, runY, runWidth, now, bold, offsetX, offsetY,
                    GLYPH_ROWS / 2, override, alpha, shadow);
        } else {
            sliced(graphics, font, text, runX, runY, runWidth, now, bold, offsetX, offsetY,
                    override, alpha, shadow, shifts);
        }
    }

    private int liftOf(int i, long now) {
        return this.look.has(Ornament.WAVE)
                ? Math.round(2.2F * (float) Math.sin(now / 360.0 - i * 0.72))
                : 0;
    }

    private void banded(GuiGraphicsExtractor graphics, Font font, String text, int x, int y,
                        int width, long now, boolean bold) {
        Matrix3x2fStack pose = graphics.pose();

        float scaleX = pose.m00();
        float scaleY = pose.m11();
        float shiftX = pose.m20();
        float shiftY = pose.m21();

        int cursor = x;

        for (int i = 0; i < text.length(); i++) {
            String glyph = charAt(text, i);
            int advance = font.width(glyph) + (bold ? 1 : 0);
            int centre = cursor + advance / 2;
            int lift = liftOf(i, now);
            float along = (centre - x) / (float) Math.max(1, width);

            int left = Mth.floor(scaleX * (cursor - 1) + shiftX);
            int right = Mth.ceil(scaleX * (cursor + advance + 1) + shiftX);
            int top = Mth.floor(scaleY * (y + lift) + shiftY);
            int bottom = Mth.ceil(scaleY * (y + lift + GLYPH_ROWS) + shiftY);

            int tall = Math.max(1, bottom - top);
            int bands = Math.max(1, Math.min(GLYPH_ROWS, tall));

            for (int band = 0; band < bands; band++) {
                int stripTop = top + tall * band / bands;
                int stripBottom = top + tall * (band + 1) / bands;
                if (stripBottom <= stripTop) continue;

                float middle = (stripTop + stripBottom) / 2.0F - top;
                int row = Math.round(middle / tall * (GLYPH_ROWS - 1));
                int colour = tint(along, Math.max(0, Math.min(GLYPH_ROWS - 1, row)), centre, now);

                scissorPixels(graphics, left, stripTop, right, stripBottom);
                graphics.text(font, glyph, cursor, y + lift, colour, false);
                if (bold) graphics.text(font, glyph, cursor + 1, y + lift, colour, false);
                Clip.off(graphics);
            }
            cursor += advance;
        }
    }

    private static void scissorPixels(GuiGraphicsExtractor graphics, int x0, int y0,
                                      int x1, int y1) {
        Clip.device(graphics, x0, y0, x1, y1);
    }

    private void run(GuiGraphicsExtractor graphics, Font font, String text, int x, int y,
                     int width, long now, boolean bold) {
        if (this.look.has(Ornament.FLAMES)) flames(graphics, x, y, width, now);

        int[] shifts = glitchShifts(now);

        if (this.look.aura == Aura.NEON || this.look.aura == Aura.BLOOM) {
            int[][] kernel = this.look.aura == Aura.NEON ? NEON_KERNEL : BLOOM_KERNEL;
            float scale = this.look.auraStrength / 46.0F;

            for (int[] step : kernel) {
                int alpha = Math.min(0xFF, Math.round(step[2] * scale));
                if (alpha < 4) continue;
                letters(graphics, font, text, x, y, width, now, bold,
                        step[0], step[1], 0, alpha, false, shifts);
            }
        }
        if (this.look.has(Ornament.OUTLINE)) {
            for (int[] step : OUTLINE_KERNEL) {
                letters(graphics, font, text, x, y, width, now, bold,
                        step[0], step[1], OUTLINE_COLOUR, 0xFF, false, shifts);
            }
        }

        if (shifts != null) {
            letters(graphics, font, text, x, y, width, now, bold, -1, 0,
                    GLITCH_RED, 0x99, false, shifts);
            letters(graphics, font, text, x, y, width, now, bold, 1, 0,
                    GLITCH_CYAN, 0x99, false, shifts);
        }

        if (this.look.colour.bandsDown() && this.look.smooth) {
            banded(graphics, font, text, x, y, width, now, bold);
        } else {
            letters(graphics, font, text, x, y, width, now, bold, 0, 0, 0, 0xFF,
                    !this.look.has(Ornament.OUTLINE) && shifts == null, shifts);
        }

        if (this.look.has(Ornament.RAIN)) rain(graphics, x, y, width, now);
    }

    private void decorBehind(GuiGraphicsExtractor graphics, int x, int y, int width, long now) {
        if (this.look.has(Ornament.WINGS)) wings(graphics, x, y, width, now);

        float breath = this.look.breath ? 0.82F + 0.18F * pulse(BREATH_PERIOD_MS) : 1.0F;

        if (this.look.aura == Aura.HALO) {
            for (int ring = 4; ring >= 1; ring--) {
                int alpha = (int) (ring == 1
                        ? this.look.auraStrength * breath
                        : (this.look.auraStrength * breath) / (ring * 1.6F));
                if (alpha <= 2) continue;

                Theme.fill(graphics, x - ring, y - ring + 1, x + width + ring, y + GLYPH_ROWS + ring - 1,
                        Theme.withAlpha(nameColor(), alpha));
            }
        } else if (this.look.aura == Aura.UNDERLINE) {
            int line = y + GLYPH_ROWS + 2;
            for (int px = x; px < x + width; px++) {
                Theme.fill(graphics, px, line, px + 1, line + 1, Theme.withAlpha(
                        tint((px - x) / (float) Math.max(1, width), GLYPH_ROWS / 2, px, now), 0xCC));
            }

            int head = x + (int) (((now % 1100L) / 1100.0F) * width);
            for (int trail = 0; trail < 8; trail++) {
                Theme.fill(graphics, head - trail, line, head - trail + 1, line + 1,
                        Theme.withAlpha(0xFFFFFFFF, Math.max(0, 0xFF - trail * 32)));
            }
        }
    }

    private void decorOver(GuiGraphicsExtractor graphics, int x, int y, int width, long now) {
        if (this.look.aura == Aura.SCAN) scan(graphics, x, y, width, GLYPH_ROWS, now);
        if (this.look.has(Ornament.MOTES)) motes(graphics, x, y, width, now);
        if (this.look.has(Ornament.COMET)) comet(graphics, x, y, width, GLYPH_ROWS, now);
        if (this.look.has(Ornament.SPARKS)) sparks(graphics, x, y, width, now);
    }

    private void scan(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                      long now) {
        int drift = (int) ((now / 55L) % 3L);

        for (int line = y - 3 + drift; line < y + height + 3; line += 3) {
            Theme.fill(graphics, x - 5, line, x + width + 5, line + 1, 0x26FFFFFF);
        }
        Theme.fill(graphics, x - 5, y - 3, x + width + 5, y + height + 3, 0x0AFFFFFF);
    }

    private void comet(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                       long now) {
        float turn = (now % COMET_PERIOD_MS) / (float) COMET_PERIOD_MS;

        float centreX = x + width / 2.0F;
        float centreY = y + height / 2.0F;
        float radiusX = width / 2.0F + 17.0F;
        float radiusY = height / 2.0F + 13.0F;

        for (int trail = COMET_TAIL; trail >= 0; trail--) {
            float at = turn - trail * 0.013F;
            double angle = at * Math.PI * 2.0;

            int px = Math.round(centreX + (float) Math.cos(angle) * radiusX);
            int py = Math.round(centreY + (float) Math.sin(angle) * radiusY);

            boolean head = trail < 2;
            int size = head ? 2 : 1;
            int colour = head ? 0xFFFFFFFF : sample(wrap(at));

            Theme.fill(graphics, px, py, px + size, py + size, Theme.withAlpha(colour,
                    Math.round(255 * (1.0F - trail / (float) (COMET_TAIL + 1)))));
        }
    }

    private void sparks(GuiGraphicsExtractor graphics, int x, int y, int width, long now) {
        sparks(graphics, x, y, width, GLYPH_ROWS, now);
    }

    private void sparks(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                        long now) {
        float turn = (now % ORBIT_PERIOD_MS) / (float) ORBIT_PERIOD_MS;

        float centreX = x + width / 2.0F;
        float centreY = y + height / 2.0F;
        float radiusX = width / 2.0F + 5.0F;
        float radiusY = height / 2.0F + 4.0F;

        for (int i = 0; i < SPARKS; i++) {
            double angle = (turn + i / (float) SPARKS) * Math.PI * 2.0;

            int px = Math.round(centreX + (float) Math.cos(angle) * radiusX);
            int py = Math.round(centreY + (float) Math.sin(angle) * radiusY);

            float depth = 0.55F + 0.45F * (float) -Math.sin(angle);
            int colour = sample((float) ((angle / (Math.PI * 2.0)) % 1.0));

            Theme.fill(graphics, px, py, px + 1, py + 1, Theme.withAlpha(colour, (int) (215 * depth)));
        }
    }

    private void rain(GuiGraphicsExtractor graphics, int x, int y, int width, long now) {
        rain(graphics, x, y, width, GLYPH_ROWS, now);
    }

    private void rain(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                      long now) {
        int count = Math.max(5, width / 7);

        for (int i = 0; i < count; i++) {
            float life = 1200.0F + noise(i, 3L) * 1000.0F;
            float at = ((now + (long) (noise(i, 5L) * life)) % (long) life) / life;

            int px = x + (int) (noise(i, 11L) * width);
            int py = Math.round(y + height - 1 + at * 13.0F);

            Theme.fill(graphics, px, py, px + 1, py + 1,
                    Theme.withAlpha(sample(noise(i, 13L)), Math.round(230 * (1 - at))));
        }
    }

    private void flames(GuiGraphicsExtractor graphics, int x, int y, int width, long now) {
        long step = now / 60L;

        for (int px = x; px < x + width; px++) {
            int tall = 4 + Math.round(noise(px, step) * 9.0F);

            for (int t = 0; t < tall; t++) {
                int alpha = Math.round(230 * (1 - t / (float) tall));
                if (alpha < 8) continue;

                int jitter = Math.round((noise(px, step + t * 31L) - 0.5F) * 2.4F);
                Theme.fill(graphics, px + jitter, y - 1 - t, px + jitter + 1, y - t,
                        Theme.withAlpha(sample(t / (float) tall), alpha));
            }
        }
    }

    private void motes(GuiGraphicsExtractor graphics, int x, int y, int width, long now) {
        motes(graphics, x, y, width, GLYPH_ROWS, now);
    }

    private void motes(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                       long now) {
        for (int i = 0; i < 16; i++) {
            float life = 2200.0F + noise(i, 17L) * 1500.0F;
            float at = ((now + (long) (noise(i, 19L) * life)) % (long) life) / life;

            float drift = (noise(i, 29L) - 0.5F) * 44.0F;
            int px = Math.round(x + noise(i, 23L) * width + drift * at);
            int py = Math.round(y + height - at * 34.0F);

            int alpha = Math.round(235 * (float) Math.sin(at * Math.PI));
            Theme.fill(graphics, px, py, px + 1, py + 1, Theme.withAlpha(sample(noise(i, 31L)), alpha));
        }
    }

    private void wings(GuiGraphicsExtractor graphics, int x, int y, int width, long now) {
        int beat = BEAT[(int) Math.floorMod(now / 170L, BEAT.length)];
        int centreY = y + GLYPH_ROWS / 2;
        int length = 15;

        for (int side = -1; side <= 1; side += 2) {
            for (int t = 0; t < length; t++) {
                int height = Math.max(1, 3 - t / 5);
                int px = side < 0 ? x - 5 - t : x + width + 4 + t;
                int lift = Math.round(beat * (t / (float) (length - 1)));
                int py = centreY - t / 2 - lift;

                Theme.fill(graphics, px, py - height, px + 1, py + height + 1,
                        Theme.withAlpha(nameColor(), Math.max(0x40, 0xE0 - t * 10)));
            }
        }
    }

    public int boxLift(long now, int phase) {
        if (!this.look.has(Ornament.WAVE)) return 0;
        return Math.round((float) Math.sin(now / 300.0 + phase) * 2.0F);
    }

    public int boxTint(int x, int y, int width, int height, int px, int py, long now) {
        float across = (px - x) / (float) Math.max(1, width);
        int row = Math.round((py - y) / (float) Math.max(1, height - 1) * (GLYPH_ROWS - 1));
        return tint(across, Math.max(0, Math.min(GLYPH_ROWS - 1, row)), px, now);
    }

    public void boxBehind(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                          long now) {
        if (this.look.aura == Aura.NONE) return;

        float breath = this.look.breath ? 0.82F + 0.18F * pulse(BREATH_PERIOD_MS) : 1.0F;

        for (int ring = 5; ring >= 1; ring--) {
            int alpha = Math.round((this.look.auraStrength * breath) / (ring * 1.7F));
            if (alpha <= 2) continue;

            int spread = ring * 3;
            Theme.fill(graphics, x - spread, y - spread, x + width + spread, y + height + spread,
                    Theme.withAlpha(nameColor(), Math.min(0xFF, alpha)));
        }
    }

    public void boxOver(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                        long now) {
        int thickness = 2;

        for (int px = x; px < x + width; px += 2) {
            int top = boxTint(x, y, width, height, px, y, now);
            int bottom = boxTint(x, y, width, height, px, y + height - 1, now);
            Theme.fill(graphics, px, y, Math.min(x + width, px + 2), y + thickness, top);
            Theme.fill(graphics, px, y + height - thickness, Math.min(x + width, px + 2), y + height,
                    bottom);
        }
        for (int py = y; py < y + height; py += 2) {
            int left = boxTint(x, y, width, height, x, py, now);
            int right = boxTint(x, y, width, height, x + width - 1, py, now);
            Theme.fill(graphics, x, py, x + thickness, Math.min(y + height, py + 2), left);
            Theme.fill(graphics, x + width - thickness, py, x + width, Math.min(y + height, py + 2),
                    right);
        }

        if (this.look.has(Ornament.OUTLINE)) {
            Theme.roundedOutline(graphics, x - 1, y - 1, width + 2, height + 2,
                    Theme.withAlpha(OUTLINE_COLOUR, 0xDD));
        }

        if (this.look.has(Ornament.SPARKS)) sparks(graphics, x, y, width, height, now);
        if (this.look.has(Ornament.RAIN)) rain(graphics, x, y, width, height, now);
        if (this.look.has(Ornament.MOTES)) motes(graphics, x, y, width, height, now);
    }

    public static int drawCentred(GuiGraphicsExtractor graphics, Font font, String name,
                                  int centreX, int y, int fallbackColor) {
        return drawCentred(graphics, font, of(name), name, centreX, y, fallbackColor);
    }

    public static int drawCentred(GuiGraphicsExtractor graphics, Font font, NameLabel label,
                                  String text, int centreX, int y, int fallbackColor) {
        if (label == null) {
            graphics.centeredText(font, Component.literal(text), centreX, y, fallbackColor);
            return font.lineHeight;
        }

        int width = runWidth(font, text, false);
        drawLeft(graphics, font, label, text, centreX - width / 2, y, fallbackColor);
        return font.lineHeight;
    }

    public static int drawLeft(GuiGraphicsExtractor graphics, Font font, String name,
                               int x, int y, int fallbackColor) {
        return drawLeft(graphics, font, of(name), name, x, y, fallbackColor);
    }

    public static int drawLeft(GuiGraphicsExtractor graphics, Font font, NameLabel label,
                               String text, int x, int y, int fallbackColor) {
        if (label == null) {
            graphics.text(font, Component.literal(text), x, y, fallbackColor);
            return font.width(text);
        }

        long now = System.currentTimeMillis();
        int width = runWidth(font, text, false);

        label.decorBehind(graphics, x, y, width, now);
        label.run(graphics, font, text, x, y, width, now, false);
        label.decorOver(graphics, x, y, width, now);
        return width;
    }

    public int tagWidth(Font font) {
        return tagWidth(font, null);
    }

    public int tagWidth(Font font, String name) {
        String word = tagFor(name);
        if (word == null) return 0;

        boolean bold = this != RANK || RankTitle.unranked(name);

        return font.width("[") + runWidth(font, word, bold) + font.width("] ");
    }

    public static int tagWidthOf(Font font, NameLabel label, String name) {
        return label == null ? 0 : label.tagWidth(font, name);
    }

    private String tagFor(String name) {
        return this == RANK ? RankTitle.tagFor(name) : this.tag;
    }

    private static int drawRankTitle(GuiGraphicsExtractor graphics, Font font, String text,
                                     int x, int y, int fallbackColor) {
        String word = RankTitle.tagFor(text);
        if (word == null) {
            graphics.text(font, Component.literal(text), x, y, fallbackColor);
            return font.width(text);
        }

        int colour = RankTitle.colourFor(text);
        boolean empty = RankTitle.unranked(text);

        Component tag = empty
                ? Component.literal(word).withStyle(ChatFormatting.BOLD)
                : Component.literal(word);

        int open = font.width("[");
        int inside = font.width(tag);

        graphics.text(font, Component.literal("["), x, y, Theme.TEXT_FAINT);
        graphics.text(font, tag, x + open, y, colour);
        graphics.text(font, Component.literal("]"), x + open + inside, y, Theme.TEXT_FAINT);

        int used = open + inside + font.width("] ");

        graphics.text(font, Component.literal(text), x + used, y, empty ? fallbackColor : colour);
        return used + font.width(text);
    }

    private static final float PULSE_MAX = 1.25F;

    private static final float PULSE_EDGE = 0.8F;

    private static final long PULSE_GROW_MS = 1_500L;
    private static final long PULSE_HOLD_MS = 2_000L;
    private static final long PULSE_SHRINK_MS = 1_500L;
    private static final long PULSE_REST_MS = 1_000L;
    private static final long PULSE_CYCLE_MS =
            PULSE_GROW_MS + PULSE_HOLD_MS + PULSE_SHRINK_MS + PULSE_REST_MS;

    private static float swell(long now) {
        long t = now % PULSE_CYCLE_MS;
        float amount;
        if (t < PULSE_GROW_MS) {
            amount = t / (float) PULSE_GROW_MS;
        } else if (t < PULSE_GROW_MS + PULSE_HOLD_MS) {
            amount = 1.0F;
        } else if (t < PULSE_GROW_MS + PULSE_HOLD_MS + PULSE_SHRINK_MS) {
            amount = 1.0F - (t - PULSE_GROW_MS - PULSE_HOLD_MS) / (float) PULSE_SHRINK_MS;
        } else {
            amount = 0.0F;
        }
        return amount * amount * (3.0F - 2.0F * amount);
    }

    private void balloon(GuiGraphicsExtractor graphics, Font font, String tag, String name, int x,
                         int y, long now) {
        List<String> glyphs = new ArrayList<>();
        List<Boolean> bolds = new ArrayList<>();
        List<Boolean> faint = new ArrayList<>();
        glyphs.add("[");
        bolds.add(false);
        faint.add(true);
        for (int i = 0; i < tag.length(); i++) {
            glyphs.add(charAt(tag, i));
            bolds.add(true);
            faint.add(false);
        }
        glyphs.add("]");
        bolds.add(false);
        faint.add(true);
        if (name != null) {
            glyphs.add(" ");
            bolds.add(false);
            faint.add(true);
            for (int i = 0; i < name.length(); i++) {
                glyphs.add(charAt(name, i));
                bolds.add(false);
                faint.add(false);
            }
        }

        int count = glyphs.size();
        float[] widths = new float[count];
        float total = 0.0F;
        for (int i = 0; i < count; i++) {
            widths[i] = font.width(glyphs.get(i)) + (bolds.get(i) ? 1 : 0);
            total += widths[i];
        }

        float amount = swell(now);
        float[] scales = new float[count];
        float laid = 0.0F;
        float cursor = 0.0F;
        for (int i = 0; i < count; i++) {
            float across = total <= 0.0F ? 0.0F : ((cursor + widths[i] / 2.0F) / total) * 2.0F - 1.0F;
            float round = (float) Math.sqrt(Math.max(0.0F, 1.0F - across * across));
            float top = PULSE_EDGE + (PULSE_MAX - PULSE_EDGE) * round;
            scales[i] = 1.0F + (top - 1.0F) * amount;
            laid += widths[i] * scales[i];
            cursor += widths[i];
        }

        float middleY = y + font.lineHeight / 2.0F;
        float at = x + total / 2.0F - laid / 2.0F;
        float along = 0.0F;
        Matrix3x2fStack pose = graphics.pose();
        for (int i = 0; i < count; i++) {
            String glyph = glyphs.get(i);
            float scale = scales[i];
            int colour = faint.get(i) ? Theme.TEXT_FAINT
                    : tint((along + widths[i] / 2.0F) / Math.max(1.0F, total), GLYPH_ROWS / 2, i, now);
            along += widths[i];

            if (!glyph.isBlank()) {
                pose.pushMatrix();
                pose.translate(at, middleY - font.lineHeight * scale / 2.0F);
                pose.scale(scale, scale);
                glyph(graphics, font, glyph, 0, 0, colour, bolds.get(i), true);
                pose.popMatrix();
            }
            at += widths[i] * scale;
        }
    }

    private void drawBracketed(GuiGraphicsExtractor graphics, Font font, String word, int x, int y,
                               int width, long now) {
        if (this == ECONOMIST) {
            balloon(graphics, font, word, null, x, y, now);
            return;
        }
        int open = font.width("[");
        graphics.text(font, Component.literal("["), x, y, Theme.TEXT_FAINT);
        run(graphics, font, word, x + open, y, width, now, true);
        graphics.text(font, Component.literal("]"), x + open + width, y, Theme.TEXT_FAINT);
    }

    public int drawTag(GuiGraphicsExtractor graphics, Font font, int x, int y, String name) {
        String word = tagFor(name);
        if (word == null) return 0;

        long now = System.currentTimeMillis();
        int open = font.width("[");

        if (this == RANK) {
            Component tag = RankTitle.unranked(name)
                    ? Component.literal(word).withStyle(ChatFormatting.BOLD)
                    : Component.literal(word);
            int inside = font.width(tag);

            graphics.text(font, Component.literal("["), x, y, Theme.TEXT_FAINT);
            graphics.text(font, tag, x + open, y, RankTitle.colourFor(name));
            graphics.text(font, Component.literal("]"), x + open + inside, y, Theme.TEXT_FAINT);
            return open + inside + font.width("] ");
        }

        int width = runWidth(font, word, true);

        drawBracketed(graphics, font, word, x, y, width, now);

        return tagWidth(font, name);
    }

    public int drawTagStill(GuiGraphicsExtractor graphics, Font font, int x, int y, String name) {
        String word = tagFor(name);
        if (word == null) return 0;
        if (this == RANK) return drawTag(graphics, font, x, y, name);

        Component still = Component.empty()
                .append(Component.literal("[").withColor(Theme.TEXT_FAINT & 0xFFFFFF))
                .append(chatTag(name))
                .append(Component.literal("]").withColor(Theme.TEXT_FAINT & 0xFFFFFF));
        graphics.text(font, still, x, y, 0xFFFFFFFF, true);
        return tagWidth(font, name);
    }

    public static int drawTaggedLeft(GuiGraphicsExtractor graphics, Font font, NameLabel label,
                                     String text, int x, int y, int fallbackColor) {
        if (label == null) {
            graphics.text(font, Component.literal(text), x, y, fallbackColor);
            return font.width(text);
        }

        if (label == RANK) return drawRankTitle(graphics, font, text, x, y, fallbackColor);

        long now = System.currentTimeMillis();
        int open = font.width("[");
        int tagWidth = runWidth(font, label.tag, true);
        int used = label.tagWidth(font);
        int nameWidth = runWidth(font, text, false);
        int total = used + nameWidth;

        if (label == ECONOMIST) {
            label.balloon(graphics, font, label.tag, text, x, y, now);
            return total;
        }

        label.decorBehind(graphics, x, y, total, now);

        label.drawBracketed(graphics, font, label.tag, x, y, tagWidth, now);
        label.run(graphics, font, text, x + used, y, nameWidth, now, false);

        label.decorOver(graphics, x, y, total, now);
        return total;
    }

    public static int drawTaggedLeft(GuiGraphicsExtractor graphics, Font font, String name,
                                     int x, int y, int fallbackColor) {
        return drawTaggedLeft(graphics, font, of(name), name, x, y, fallbackColor);
    }

    public static int drawTaggedScrolling(GuiGraphicsExtractor graphics, Font font, NameLabel label,
                                          String text, int x, int y, int maxWidth,
                                          int fallbackColor) {
        if (maxWidth <= 0) return 0;

        int total = label == null
                ? font.width(text)
                : label.tagWidth(font, text) + runWidth(font, text, false);

        if (total <= maxWidth) {
            return drawTaggedLeft(graphics, font, label, text, x, y, fallbackColor);
        }

        int travel = total - maxWidth + Marquee.TAIL;

        Matrix3x2fStack pose = graphics.pose();
        scissorPixels(graphics,
                Mth.floor(pose.m00() * x + pose.m20()),
                Mth.floor(pose.m11() * (y - font.lineHeight * 2) + pose.m21()),
                Mth.ceil(pose.m00() * (x + maxWidth) + pose.m20()),
                Mth.ceil(pose.m11() * (y + font.lineHeight * 2) + pose.m21()));
        drawTaggedLeft(graphics, font, label, text, x - Marquee.offsetFor(travel), y,
                fallbackColor);
        Clip.off(graphics);

        return maxWidth;
    }

    public Component chatTag(String name) {
        String word = tagFor(name);
        if (word == null) return Component.empty();

        if (this == RANK) {
            return Component.literal(word).withStyle(ChatFormatting.BOLD)
                    .withColor(RankTitle.colourFor(name) & 0xFFFFFF);
        }
        return gradient(word).withStyle(ChatFormatting.BOLD);
    }

    public static Component decorate(String name, NameLabel label) {
        if (label == null) return Component.literal(name);

        if (label == RANK && RankTitle.tagFor(name) == null) return Component.literal(name);

        return Component.empty()
                .append(Component.literal("[").withStyle(ChatFormatting.DARK_GRAY))
                .append(label.chatTag(name))
                .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY))
                .append(label == RANK
                        ? Component.literal(name).withColor(label.nameColor(name) & 0xFFFFFF)
                        : label.gradient(name));
    }

    public Component animatedTag(String name) {
        return chatTag(name);
    }

    private MutableComponent gradient(String text) {
        MutableComponent built = Component.empty();
        int length = Math.max(1, text.length() - 1);

        for (int i = 0; i < text.length(); i++) {
            int colour = tint(i / (float) length, GLYPH_ROWS / 2, i, 0L);
            built.append(Component.literal(String.valueOf(text.charAt(i)))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(colour & 0x00FFFFFF))));
        }
        return built;
    }
}
