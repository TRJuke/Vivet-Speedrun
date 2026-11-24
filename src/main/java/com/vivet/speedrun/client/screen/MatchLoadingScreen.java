package com.vivet.speedrun.client.screen;

import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.client.LoadingTakeover;
import com.vivet.speedrun.client.ui.Clip;
import com.vivet.speedrun.client.ui.Cosmetics;
import com.vivet.speedrun.client.ui.NameLabel;
import com.vivet.speedrun.client.ui.Placements;
import com.vivet.speedrun.client.ui.PlayerHead;
import com.vivet.speedrun.client.ui.RankEmblem;
import com.vivet.speedrun.client.ui.Ranks;
import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.media.MediaClips;
import com.vivet.speedrun.media.MediaImages;
import com.vivet.speedrun.p2p.MojangAuth;
import com.vivet.speedrun.p2p.Wardrobe;
import com.vivet.speedrun.session.Session;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.joml.Matrix3x2fStack;

import java.util.List;

public class MatchLoadingScreen extends VivetScreen {
    private static final Identifier PLATE =
            Identifier.fromNamespaceAndPath(VivetSpeedrun.MOD_ID, "textures/gui/loading_default.png");

    private static final int PLATE_W = 216;
    private static final int PLATE_H = 120;

    private static final int PLATE_TINT = 0xFFA6A6A6;

    private static final int SHUTTER = Theme.mix(0xFF141822, 0xFF000000, 0.42F);

    private static final int CLIP_FILL = 0xFF050608;

    private static final float REF_H = 563.0F;
    private static final float REF_W_VERSUS = 1018.0F;
    private static final float REF_W_SOLO = 620.0F;

    private static final float CLIP_REF = 240.0F;
    private static final float PAD_REF = 10.0F;

    private static final float INNER_REF = 71.0F;

    private static final float STAGGER_REF = 44.0F;

    private static final float VS_SCALE_REF = 7.5F;

    private static final int MINI = 26;
    private static final int CELL = MINI + 8;
    private static final int CELL_GAP = 4;
    private static final int COLUMN_PAD = 5;
    private static final int HEAD = 24;
    private static final int COLUMN_W = CELL + COLUMN_PAD * 2;
    private static final int HEAD_SECTION = COLUMN_PAD * 2 + HEAD;
    private static final int HEADER = MINI + 14;

    private static final int BAND = 12;

    private static final float NAME_SCALE = 1.5F;
    private static final float NAME_MIN = 0.5F;

    private static final int FOOTER_ROOM = 34;

    private static final long GROUND_FADE_MS = 250L;

    private static final long GROUND_WAIT_MS = 5000L;

    private static final int GROUND_DARK = 0xFF0A0B0F;

    private static final int MAX_BADGES = 4;
    private static final int CARD_R = 5;

    private long groundSince;

    private Screen behind;

    public MatchLoadingScreen() {
        this(null);
    }

    public MatchLoadingScreen(Screen behind) {
        super(Component.translatable("vivet.screen.match_loading"));
        this.behind = behind;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                  float partialTick) {
        Screen menu = this.behind;
        if (menu != null && LoadingTakeover.coverNow() < LoadingTakeover.menuFadeMs()) {
            try {
                menu.extractBackground(graphics, -1, -1, partialTick);
                graphics.nextStratum();
                menu.extractRenderState(graphics, -1, -1, partialTick);
            } catch (RuntimeException failed) {
                this.behind = null;
            }
            return;
        }
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void drawScaled(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                              float partialTick) {
        long cover = LoadingTakeover.coverNow();
        long now = LoadingTakeover.now();
        int width = this.width;
        int height = this.height;

        long menuFade = LoadingTakeover.menuFadeMs();
        if (cover < menuFade) {
            float gone = this.behind == null ? 1.0F : easeIn(cover / (float) menuFade);
            veil(graphics, width, height, gone);
            return;
        }
        this.behind = null;

        long blackAt = LoadingTakeover.blackAt();
        if (now >= blackAt) {
            float lifted = clamp((now - blackAt) / (float) LoadingTakeover.FADE_OUT_MS);
            veil(graphics, width, height, 1.0F - easeOut(lifted));
            return;
        }

        ground(graphics, width, height, cover);

        switch (LoadingTakeover.kind()) {
            case RANKED, PUBLIC -> versus(graphics, width, height, now);
            case SOLO -> solo(graphics, width, height, now);
            case PRIVATE -> grid(graphics, width, height, now);
            case PRACTICE -> practice(graphics, width, height);
        }

        footer(graphics, width, height, cover);

        long rising = cover - menuFade;
        if (rising < LoadingTakeover.LOAD_FADE_MS) {
            veil(graphics, width, height,
                    1.0F - easeOut(rising / (float) LoadingTakeover.LOAD_FADE_MS));
        }

        long exitAt = LoadingTakeover.exitAt();
        if (exitAt >= 0 && now >= exitAt) {
            float gone = clamp((now - exitAt) / (float) LoadingTakeover.FADE_IN_MS);
            veil(graphics, width, height, easeIn(gone));
        }
    }

    private static void veil(GuiGraphicsExtractor graphics, int width, int height, float amount) {
        int alpha = Math.round(255 * clamp(amount));
        if (alpha > 0) {
            Theme.fill(graphics, 0, 0, width, height, Theme.withAlpha(0xFF000000, alpha));
        }
    }

    private void ground(GuiGraphicsExtractor graphics, int width, int height, long cover) {
        String worn = Cosmetics.mediaOf(Session.matchBackground());

        MediaImages.prepare(worn);

        boolean waiting = LoadingTakeover.kind() != LoadingTakeover.Kind.PRACTICE
                && cover < GROUND_WAIT_MS
                && (!Session.matchBackgroundKnown()
                        || (worn != null && !worn.isEmpty()
                                && !MediaImages.isReady(worn) && !MediaImages.failed(worn)));

        if (waiting) {
            Theme.fill(graphics, 0, 0, width, height, GROUND_DARK);
            vignette(graphics, width, height);
            return;
        }

        if (this.groundSince == 0L) this.groundSince = System.currentTimeMillis();

        if (!MediaImages.stretch(graphics, worn, 0, 0, width, height, PLATE_TINT)) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, PLATE, 0, 0, 0.0F, 0.0F,
                    width, height, PLATE_W, PLATE_H, PLATE_W, PLATE_H, PLATE_TINT);
        }

        vignette(graphics, width, height);

        if (cover > LoadingTakeover.menuFadeMs() + LoadingTakeover.LOAD_FADE_MS) {
            float up = clamp((System.currentTimeMillis() - this.groundSince)
                    / (float) GROUND_FADE_MS);
            int dark = Math.round(255 * (1.0F - easeOut(up)));
            if (dark > 0) {
                Theme.fill(graphics, 0, 0, width, height, Theme.withAlpha(GROUND_DARK, dark));
            }
        }
    }

    private static void vignette(GuiGraphicsExtractor graphics, int width, int height) {
        for (int i = 0; i < 26; i++) {
            int fade = 26 - i;
            Theme.fill(graphics, 0, i * 2, width, i * 2 + 2,
                    Theme.withAlpha(0xFF000000, Math.round(fade * 1.6F)));
            Theme.fill(graphics, 0, height - i * 2 - 2, width, height - i * 2,
                    Theme.withAlpha(0xFF000000, Math.round(fade * 1.9F)));
            Theme.fill(graphics, i * 2, 0, i * 2 + 2, height,
                    Theme.withAlpha(0xFF000000, Math.round(fade * 1.1F)));
            Theme.fill(graphics, width - i * 2 - 2, 0, width - i * 2, height,
                    Theme.withAlpha(0xFF000000, Math.round(fade * 1.1F)));
        }
    }

    private void footer(GuiGraphicsExtractor graphics, int width, int height, long now) {
        if (now < 230L) return;

        String text;
        int colour;
        float progress;

        if (LoadingTakeover.goArrived()) {
            text = "GET READY";
            colour = Theme.SUCCESS;
            progress = 1.0F;
        } else if (!LoadingTakeover.hasSequence()) {
            text = "LOADING WORLD";
            colour = Theme.TEXT_MUTED;
            progress = clamp(now / 12000.0F) * 0.75F;
        } else {
            List<String> waiting = Session.stillLoading();
            progress = clamp(now / 8000.0F) * 0.9F;

            if (!waiting.isEmpty()) {
                String who = waiting.size() > 2
                        ? waiting.size() + " PLAYERS"
                        : String.join(", ", waiting).toUpperCase(java.util.Locale.ROOT);
                text = "WAITING FOR " + who;
                colour = Theme.WARNING;
            } else {
                text = "LOADING WORLD";
                colour = Theme.TEXT_MUTED;
            }
        }

        String label = text + ".".repeat(1 + (int) ((now / 380L) % 3));
        graphics.centeredText(this.font, Component.literal(label), width / 2, height - 24, colour);

        Theme.fill(graphics, 0, height - 4, width, height, Theme.withAlpha(0xFF000000, 0xAA));
        int filled = Math.round(width * clamp(progress));
        Theme.fill(graphics, 0, height - 4, filled, height, Theme.withAlpha(colour, 0xEE));

        if (progress < 1.0F) {
            int run = Math.round(((float) Math.sin(now / 260.0) * 0.5F + 0.5F) * 26.0F);
            Theme.fill(graphics, filled, height - 4, Math.min(width, filled + run), height,
                    Theme.withAlpha(colour, 0x55));
        }
    }

    private record Frame(float s, int pad, int clip, int cardW, int cardH, int gap) {
        static int columnH(int badges) {
            if (badges <= 0) return HEAD_SECTION;
            return HEAD_SECTION + COLUMN_PAD * 2 + badges * CELL + (badges - 1) * CELL_GAP;
        }

        int groupW() {
            return COLUMN_W + this.gap + this.cardW;
        }
    }

    private static Frame frame(int width, int height, float refW, float stagger) {
        int fixed = HEADER + BAND;
        float needs = PAD_REF * 2 + PAD_REF / 3 + CLIP_REF + stagger * 2;

        float s = Math.min(width / refW, height / REF_H);
        s = Math.min(s, (height - fixed - FOOTER_ROOM - 8) / needs);
        s = Math.max(0.2F, s);

        int pad = Math.max(3, Math.round(PAD_REF * s));
        int clip = Math.max(24, Math.round(CLIP_REF * s));
        int cardW = Math.max(clip + pad * 2, 120);
        clip = cardW - pad * 2;
        int cardH = fixed + pad + clip + pad + Math.round(pad / 3.0F);

        cardH = Math.max(cardH, Frame.columnH(MAX_BADGES));

        return new Frame(s, pad, clip, cardW, cardH, Math.max(3, pad));
    }

    private void versus(GuiGraphicsExtractor graphics, int width, int height, long now) {
        boolean ranked = LoadingTakeover.kind() == LoadingTakeover.Kind.RANKED;
        Frame f = frame(width, height, REF_W_VERSUS, STAGGER_REF);

        int centreX = width / 2;
        int centreY = (height - FOOTER_ROOM) / 2 + 6;
        int inner = Math.round(INNER_REF * f.s());
        int stagger = Math.round(STAGGER_REF * f.s());

        if (!LoadingTakeover.hasSequence()) return;

        long at = LoadingTakeover.versusAt();
        long sinceHit = at < 0L ? -1L : now - at - LoadingTakeover.VS_FALL_MS;

        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        try {
            if (sinceHit >= 0L && sinceHit < SHAKE_MS) {
                pose.translate(shake(sinceHit, 0), shake(sinceHit, 1));
            }

            for (LoadingTakeover.Card card : LoadingTakeover.cards()) {
                Integer lift = lift(card, now, height);
                if (lift == null) continue;

                boolean first = card.index == 0;
                int cardX = first ? centreX - inner - f.cardW() : centreX + inner;
                int restY = first
                        ? centreY - stagger - f.cardH() / 2
                        : centreY + stagger - f.cardH() / 2;

                int y = first ? restY - lift : restY + lift;

                racer(graphics, card, f, cardX, y, first, ranked, now);
            }

            if (at >= 0L && now >= at) versusMark(graphics, f, centreX, centreY, now - at);
        } finally {
            pose.popMatrix();
        }

        if (sinceHit >= 0L && sinceHit < FLASH_MS) {
            int white = Math.round(70 * (1.0F - sinceHit / (float) FLASH_MS));
            Theme.fill(graphics, 0, 0, width, height, Theme.withAlpha(0xFFFFFFFF, white));
        }
    }

    private static final long SHAKE_MS = 280L;
    private static final long FLASH_MS = 160L;

    private static final long DEBRIS_MS = 520L;

    private static int shake(long sinceHit, int axis) {
        long step = sinceHit / 30L;
        int hash = (int) ((step * 73856093L) ^ (axis * 19349663L));
        hash ^= hash >>> 13;
        hash *= 0x5bd1e995;
        hash ^= hash >>> 15;
        float direction = ((hash & 0xFF) / 255.0F) * 2.0F - 1.0F;
        float left = 1.0F - sinceHit / (float) SHAKE_MS;
        return Math.round(direction * 4.0F * left * left);
    }

    private static final int[][] DEBRIS = {
            {-168, 9}, {-142, 6}, {-121, 10}, {-97, 7}, {-74, 11}, {-52, 6}, {-31, 9}, {-9, 8},
            {14, 10}, {36, 6}, {58, 9}, {83, 7}, {106, 10}, {131, 6}, {152, 9}, {176, 7},
    };

    private void versusMark(GuiGraphicsExtractor graphics, Frame f, int centreX, int centreY,
                            long since) {
        String word = "VS";
        int base = Math.max(2, Math.round(VS_SCALE_REF * f.s()));
        int wordW = this.font.width(word);

        long fall = LoadingTakeover.VS_FALL_MS;
        float scale;
        int alpha;

        if (since < fall) {
            float p = since / (float) fall;
            scale = base * (3.4F - 2.4F * easeIn(p));
            alpha = Math.round(255 * clamp(p * 2.5F));
        } else {
            float q = clamp((since - fall) / 150.0F);
            scale = base * (1.0F - 0.10F * (float) Math.sin(Math.PI * q));
            alpha = 255;
        }

        long sinceHit = since - fall;
        if (sinceHit >= 0L && sinceHit < DEBRIS_MS) {
            impact(graphics, centreX, centreY, wordW * base / 2, 4 * base, sinceHit, f.s());
        }

        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(centreX, centreY);
            pose.scale(scale, scale);
            graphics.text(this.font, Component.literal(word), -wordW / 2, -4,
                    Theme.withAlpha(0xFFFFFFFF, alpha), false);
        } finally {
            pose.popMatrix();
        }
    }

    private static void impact(GuiGraphicsExtractor graphics, int centreX, int centreY,
                               int halfW, int halfH, long sinceHit, float s) {
        float p = sinceHit / (float) DEBRIS_MS;
        int fade = Math.round(255 * (1.0F - p));

        int grow = Math.round(easeOut(p) * 70.0F * Math.max(0.5F, s));
        int x0 = centreX - halfW - 6 - grow;
        int x1 = centreX + halfW + 6 + grow;
        int y0 = centreY - halfH - 6 - grow / 2;
        int y1 = centreY + halfH + 6 + grow / 2;
        int ring = Theme.withAlpha(0xFFFFFFFF, Math.round(fade * 0.8F));
        Theme.fill(graphics, x0, y0, x1, y0 + 2, ring);
        Theme.fill(graphics, x0, y1 - 2, x1, y1, ring);
        Theme.fill(graphics, x0, y0 + 2, x0 + 2, y1 - 2, ring);
        Theme.fill(graphics, x1 - 2, y0 + 2, x1, y1 - 2, ring);

        float ms = sinceHit;
        for (int i = 0; i < DEBRIS.length; i++) {
            double angle = Math.toRadians(DEBRIS[i][0]);
            float speed = DEBRIS[i][1] * 0.045F * Math.max(0.6F, s);
            float travel = speed * ms * (1.0F - 0.45F * p);
            float x = centreX + (float) Math.cos(angle) * (halfW + travel);
            float y = centreY + (float) Math.sin(angle) * (halfH + travel * 0.6F)
                    + 0.00018F * ms * ms;
            int size = i % 3 == 0 ? 4 : i % 3 == 1 ? 3 : 2;
            int px = Math.round(x);
            int py = Math.round(y);
            Theme.fill(graphics, px, py, px + size, py + size,
                    Theme.withAlpha(i % 2 == 0 ? 0xFFFFFFFF : 0xFFC9CFDA, fade));
        }
    }

    private void solo(GuiGraphicsExtractor graphics, int width, int height, long now) {
        if (!LoadingTakeover.hasSequence()) return;

        List<LoadingTakeover.Card> cards = LoadingTakeover.cards();
        if (cards.isEmpty()) return;

        LoadingTakeover.Card card = cards.get(0);
        Integer lift = lift(card, now, height);
        if (lift == null) return;

        Frame f = frame(width, height, REF_W_SOLO, 0.0F);
        int groupX = (width - f.groupW()) / 2;
        int cardX = groupX + COLUMN_W + f.gap();
        int y = (height - FOOTER_ROOM) / 2 + 6 - f.cardH() / 2 - lift;

        racer(graphics, card, f, cardX, y, true, true, now);
    }

    private void racer(GuiGraphicsExtractor graphics, LoadingTakeover.Card card, Frame f,
                       int cardX, int y, boolean columnLeft, boolean ranked, long now) {
        int columnX = columnLeft ? cardX - f.gap() - COLUMN_W : cardX + f.cardW() + f.gap();
        column(graphics, card, columnX, y);

        Theme.roundedBox(graphics, cardX, y, f.cardW(), f.cardH(), CARD_R, Theme.PLATE);
        Theme.roundedBoxOutline(graphics, cardX, y, f.cardW(), f.cardH(), CARD_R, Theme.PLATE_EDGE);

        header(graphics, card, f, cardX, y, ranked);
        divider(graphics, card, f, cardX, y + HEADER + BAND / 2, ranked);

        NameLabel label = card.label();
        int bob = label == null ? 0 : label.boxLift(now, card.index * 2);
        float open = card.clipAt < 0 ? 1.0F : clamp((now - card.clipAt) / 170.0F);
        long into = now - card.clipAt - 170L;

        clipBox(graphics, cardX + f.pad(), y + HEADER + BAND + f.pad() + bob, f.clip(), f.clip(),
                card, now, open, into < 0 ? -1 : Math.min(into, card.clipMs));
    }

    private void column(GuiGraphicsExtractor graphics, LoadingTakeover.Card card, int x, int y) {
        List<Placements.Badge> badges = card.badges();
        int shown = Math.min(MAX_BADGES, badges.size());
        int height = Frame.columnH(shown);

        Theme.roundedBox(graphics, x, y, COLUMN_W, height, CARD_R, Theme.PLATE);
        Theme.roundedBoxOutline(graphics, x, y, COLUMN_W, height, CARD_R, Theme.PLATE_EDGE);

        PlayerHead.draw(graphics, card.name, card.accountId,
                x + (COLUMN_W - HEAD) / 2, y + COLUMN_PAD, HEAD);

        if (shown == 0) return;

        int ruleY = y + HEAD_SECTION;
        Theme.fill(graphics, x + COLUMN_PAD, ruleY, x + COLUMN_W - COLUMN_PAD, ruleY + 1,
                Theme.PLATE_EDGE);

        int cellX = x + COLUMN_PAD;
        int cellY = ruleY + COLUMN_PAD;
        for (int i = 0; i < shown; i++) {
            Placements.draw(graphics, this.font, badges.get(i), cellX,
                    cellY + i * (CELL + CELL_GAP), CELL, CELL, false);
        }
    }

    private void header(GuiGraphicsExtractor graphics, LoadingTakeover.Card card, Frame f, int x,
                        int y, boolean ranked) {
        int inset = f.pad();
        int emblemX = x + f.cardW() - inset - MINI;
        int emblemY = y + (HEADER - MINI) / 2;

        emblem(graphics, card, emblemX, emblemY, ranked);

        int textX = x + inset;
        int room = Math.max(0, emblemX - 6 - textX);
        int line = this.font.lineHeight;

        NameLabel label = card.label();
        int natural = NameLabel.tagWidthOf(this.font, label, card.name)
                + NameLabel.runWidthOf(this.font, card.name);

        float wanted = NAME_SCALE;
        float fits = natural <= 0 ? wanted : room / (float) natural;
        float scale = Math.max(NAME_MIN, Math.min(wanted, fits));

        int textY = y + Math.round((HEADER - (line - 1) * scale) / 2.0F);

        Clip.on(graphics, textX, y + 1, textX + room, y + HEADER - 1);
        try {
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(textX, textY);
            pose.scale(scale, scale);
            try {
                NameLabel.drawTaggedLeft(graphics, this.font, label, card.name, 0, 0, Theme.TEXT);
            } finally {
                pose.popMatrix();
            }
        } finally {
            Clip.off(graphics);
        }
    }

    private void divider(GuiGraphicsExtractor graphics, LoadingTakeover.Card card, Frame f, int x,
                         int lineY, boolean ranked) {
        int left = x + f.pad();
        int right = x + f.cardW() - f.pad();

        if (!ranked || card.profile == null) {
            Theme.fill(graphics, left, lineY, right, lineY + 1, Theme.PLATE_EDGE);
            return;
        }

        String rank = card.rankId();
        Component points = card.placed()
                ? Component.literal(card.rating() + " Elo")
                : Component.translatable("vivet.profile.unranked");
        int pointsColour = card.placed() && rank != null ? Ranks.colorOf(rank) : Theme.TEXT_FAINT;
        int pointsW = this.font.width(points);
        int textY = lineY - (this.font.lineHeight - 1) / 2;

        Integer position = card.position();
        String place = position == null ? null : "#" + position;
        int placeW = place == null ? 0 : this.font.width(place);
        if (place != null && pointsW + 6 + 6 + placeW > right - left) place = null;

        Clip.on(graphics, left, textY - 1, right, textY + this.font.lineHeight + 1);
        try {
            graphics.text(this.font, points, left, textY, pointsColour, true);
        } finally {
            Clip.off(graphics);
        }

        int lineFrom = left + pointsW + 6;
        int lineTo = place == null ? right : right - placeW - 6;
        if (lineTo > lineFrom) {
            Theme.fill(graphics, lineFrom, lineY, lineTo, lineY + 1, Theme.PLATE_EDGE);
        }

        if (place != null) {
            graphics.text(this.font, Component.literal(place), right - placeW, textY,
                    Theme.TEXT_MUTED, true);
        }
    }

    private void emblem(GuiGraphicsExtractor graphics, LoadingTakeover.Card card, int x, int y,
                        boolean ranked) {
        MojangAuth.Profile profile = card.profile;
        if (profile == null) return;

        if (!ranked && profile.emblem != null && !profile.emblem.isEmpty()) {
            Wardrobe.Item worn = Wardrobe.item(profile.emblem);
            if (worn != null) {
                Cosmetics.ready(worn);
                Cosmetics.draw(graphics, this.font, worn, x, y, MINI, MINI,
                        System.currentTimeMillis(), true, false, Ranks.displayRank(profile));
                return;
            }
        }

        String rank = card.rankId();
        if (rank != null) {
            RankEmblem.drawCompact(graphics, x + MINI / 2, y + MINI / 2, rank, card.placed());
        }
    }

    private static Integer lift(LoadingTakeover.Card card, long now, int height) {
        if (now < card.dropAt) return null;
        float at = clamp((now - card.dropAt) / 440.0F);
        return Math.round((1.0F - easeBack(at)) * height);
    }

    private void clipBox(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                         LoadingTakeover.Card card, long now, float open, long into) {
        NameLabel label = card.label();

        if (label != null) label.boxBehind(graphics, x, y, width, height, now);
        Theme.roundedRect(graphics, x, y, width, height, CLIP_FILL);

        String clipId = card.clipId;
        boolean drew = clipId != null && into >= 0
                && MediaClips.draw(graphics, clipId, x + 3, y + 3, width - 6, height - 6,
                        into, 0xFFFFFFFF);
        if (!drew) mark(graphics, x, y, width, height, now);

        if (label != null) {
            if (open >= 1.0F) {
                label.boxOver(graphics, x, y, width, height, now);
            } else {
                Theme.roundedOutline(graphics, x, y, width, height,
                        Theme.withAlpha(label.nameColor(card.name), 0xCC));
            }
        } else {
            Theme.roundedOutline(graphics, x, y, width, height, Theme.PLATE_EDGE_LIT);
        }

        int shut = Math.round((height / 2.0F) * (1.0F - clamp(open)));
        if (shut > 0) {
            int lip = label == null ? Theme.TEXT_FAINT : label.nameColor(card.name);
            Theme.fill(graphics, x, y, x + width, y + shut, SHUTTER);
            Theme.fill(graphics, x, y + height - shut, x + width, y + height, SHUTTER);
            Theme.fill(graphics, x, y + shut - 1, x + width, y + shut + 1, Theme.withAlpha(lip, 0xCC));
            Theme.fill(graphics, x, y + height - shut - 1, x + width, y + height - shut + 1,
                    Theme.withAlpha(lip, 0xCC));
        }
    }

    private void mark(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                      long now) {
        float scale = Math.max(1.5F, width / 62.0F);
        int centreY = y + height / 2 - Math.round(4 * scale);

        float head = -0.3F + 1.6F * ((now % 2600L) / 2600.0F);
        int colour = Math.abs(head - 0.5F) < 0.35F
                ? Theme.mix(Theme.TEXT_FAINT, Theme.TEXT, 1.0F - Math.abs(head - 0.5F) / 0.35F)
                : Theme.TEXT_FAINT;

        big(graphics, "VIVET", x + width / 2, centreY, scale, colour);
    }

    private void big(GuiGraphicsExtractor graphics, String text, int centreX, int y, float scale,
                     int colour) {
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(centreX, y);
        pose.scale(scale, scale);
        graphics.text(this.font, Component.literal(text),
                -this.font.width(text) / 2, 0, colour);
        pose.popMatrix();
    }

    private void grid(GuiGraphicsExtractor graphics, int width, int height, long now) {
        if (!LoadingTakeover.hasSequence()) return;

        List<LoadingTakeover.Card> cards = LoadingTakeover.cards();
        int count = Math.max(1, cards.size());
        int columns = Math.min(4, Math.max(2, (int) Math.ceil(Math.sqrt(count))));
        int rows = (count + columns - 1) / columns;

        int gap = 12;
        int cardHeight = 44;
        int cardWidth = Math.min(186, (width - 44 - (columns - 1) * gap) / columns);
        int gridWidth = columns * cardWidth + (columns - 1) * gap;
        int gridHeight = rows * cardHeight + (rows - 1) * gap;

        int gridX = (width - gridWidth) / 2;
        int gridY = Math.max(16, (height - FOOTER_ROOM - gridHeight) / 2);

        for (LoadingTakeover.Card card : cards) {
            Integer lift = lift(card, now, height);
            if (lift == null) continue;

            int x = gridX + (card.index % columns) * (cardWidth + gap);
            int y = gridY + (card.index / columns) * (cardHeight + gap) - lift;
            boolean host = card.index == 0;

            Theme.roundedBox(graphics, x, y, cardWidth, cardHeight, CARD_R, Theme.PLATE);
            Theme.roundedBoxOutline(graphics, x, y, cardWidth, cardHeight, CARD_R,
                    host ? Theme.PLATE_EDGE_LIT : Theme.PLATE_EDGE);

            PlayerHead.draw(graphics, card.name, card.accountId, x + 10, y + 10, 24);

            NameLabel.drawLeft(graphics, this.font, card.label(), card.name,
                    x + 42, host ? y + 11 : y + 18, Theme.TEXT);
            if (host) {
                graphics.text(this.font, Component.literal("HOST"), x + 42, y + 24,
                        Theme.TEXT_MUTED);
            }
        }
    }

    private void practice(GuiGraphicsExtractor graphics, int width, int height) {
        int centreX = width / 2;
        float scale = Math.max(2.0F, height / 96.0F);
        int y = Math.round(height * 0.40F);

        big(graphics, "PRACTICE", centreX, y, scale, Theme.TEXT_MUTED);

        graphics.centeredText(this.font, Component.literal("HAVE FUN!"), centreX,
                y + Math.round(9 * scale) + 8, Theme.TEXT_MUTED);
    }

    private static float clamp(float at) {
        return at < 0.0F ? 0.0F : at > 1.0F ? 1.0F : at;
    }

    private static float easeOut(float at) {
        float left = 1.0F - clamp(at);
        return 1.0F - left * left * left;
    }

    private static float easeIn(float at) {
        float in = clamp(at);
        return in * in * in;
    }

    private static float easeBack(float at) {
        float in = clamp(at) - 1.0F;
        float amount = 1.5F;
        return 1.0F + (amount + 1.0F) * in * in * in + amount * in * in;
    }
}
