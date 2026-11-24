package com.vivet.speedrun.client.screen;

import com.vivet.speedrun.client.Cues;
import com.vivet.speedrun.client.ResultsPrompt;
import com.vivet.speedrun.client.ui.BigText;
import com.vivet.speedrun.client.ui.Clip;
import com.vivet.speedrun.client.ui.Cosmetics;
import com.vivet.speedrun.client.ui.EndgameBar;
import com.vivet.speedrun.client.ui.EndgameClip;
import com.vivet.speedrun.client.ui.FlatButton;
import com.vivet.speedrun.client.ui.Ranks;
import com.vivet.speedrun.client.ui.RewardRow;
import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.client.ui.VanillaIcon;
import com.vivet.speedrun.game.AccountXp;
import com.vivet.speedrun.game.Difficulty;
import com.vivet.speedrun.game.RunMode;
import com.vivet.speedrun.game.RunSummary;
import com.vivet.speedrun.media.MediaClips;
import com.vivet.speedrun.media.MediaSounds;
import com.vivet.speedrun.p2p.MojangAuth;
import com.vivet.speedrun.p2p.PostMatch;
import com.vivet.speedrun.session.Session;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class EndgameScreen extends VivetScreen {
    private static final long STEP_MS = 2_000L;

    private static final long BAR_MS = 1_500L;

    private static final int SETTLED_FRAMES = 10;
    private static final long SMOOTH_FRAME_MS = 100L;

    private static final long SETTLE_BEAT_MS = 500L;

    private static final long SETTLE_CAP_MS = 5_000L;

    private static final long WAIT_MS = 1_500L;

    private static final int MAX_CARD_W = 440;
    private static final int MIN_CARD_W = 260;

    private static final float CARD_RATIO = 2.05F;

    private static final float CARD_ASPECT = 0.66F;

    private static final int PADDING = 12;
    private static final int COLUMN_GAP = 12;

    private static final int ROW_GAP = 6;

    private static final int MAX_SHOWN_ROWS = 4;

    private static final int CLIP_PAD = 12;

    private static final int HEAD_SCALE = 4;

    private static final int HEAD_OUTLINE = 1;

    private static final int BUTTON_H = 26;
    private static final int BUTTON_RADIUS = 6;

    private static final float BUTTON_LABEL_SCALE = 1.5F;

    private final RunSummary summary;

    private final String reason;

    private final boolean won;
    private final boolean drew;
    private final boolean voided;

    private final long shownAt = System.currentTimeMillis();

    private long lastFrameAt;
    private int smoothFrames;
    private long settledAt;

    private final String winClip;
    private final String winSound;

    private boolean winSounded;

    private final RunMode mode;
    private final Difficulty band;

    private final MojangAuth.Profile ratingBefore;

    private final List<Reward> rewards = new ArrayList<>();

    private final List<Long> dues = new ArrayList<>();

    private boolean coinsBuilt;

    private long armedAt;

    private boolean armedBlind;

    private int sounded;

    private int crossed;

    private boolean leaving;

    private record Reward(RewardRow row, EndgameBar bar, Cue cue) {
    }

    private enum Cue {
        ELO_UP, ELO_DOWN, ELO_FLAT, XP, COINS
    }

    public EndgameScreen(RunSummary summary, String line) {
        super(headlineOf(summary.won(), summary.drew(), noResult()));
        this.summary = summary;
        this.reason = line == null ? "" : line;
        this.won = summary.won();
        this.drew = summary.drew();
        this.voided = noResult();
        this.mode = summary.mode;
        this.band = summary.band;
        this.ratingBefore = null;

        String champion = championOf(summary.winnerName);
        this.winClip = Cosmetics.winMedia(champion, "win_gif");
        this.winSound = Cosmetics.winMedia(champion, "win_audio");
        buildRewards();
    }

    public EndgameScreen(String reason, boolean won) {
        this(reason, won, false, null);
    }

    public EndgameScreen(String reason, boolean won, boolean drew, MojangAuth.Profile ratingBefore) {
        super(headlineOf(won, drew, noResult()));
        this.summary = null;
        this.reason = reason == null ? "" : reason;
        this.won = won;
        this.drew = drew;
        this.voided = noResult();
        this.mode = RunMode.ofLast();
        this.band = Difficulty.MEDIUM;
        this.ratingBefore = ratingBefore;

        String champion = championOf(Session.lastWinner());
        this.winClip = Cosmetics.winMedia(champion, "win_gif");
        this.winSound = Cosmetics.winMedia(champion, "win_audio");
        buildRewards();
    }

    private static String championOf(String winnerName) {
        if (noResult() || winnerName == null) return "";
        return winnerName;
    }

    public static boolean outroTakesTheMoment(RunSummary summary) {
        if (summary == null || !showsFor(summary.mode)) return false;

        return Cosmetics.winMedia(championOf(summary.winnerName), "win_audio") != null;
    }

    private static boolean noResult() {
        return Session.wasVoided() || Session.wasConceded();
    }

    public static boolean showsFor(RunMode mode) {
        return AccountXp.earnsXp(mode);
    }

    private static Component headlineOf(boolean won, boolean drew, boolean voided) {
        if (voided) return Component.translatable("vivet.endgame.no_contest");
        if (drew) return Component.translatable("vivet.endgame.draw");
        return Component.translatable(won ? "vivet.endgame.victory" : "vivet.endgame.defeat");
    }

    private int accent() {
        if (this.voided) return Theme.TEXT_MUTED;
        if (this.drew) return Theme.WARNING;
        return this.won ? Theme.SUCCESS : Theme.DANGER;
    }

    private static String matchTag() {
        return Component.translatable("vivet.endgame.tag.match").getString();
    }

    private void buildRewards() {
        this.rewards.clear();
        this.dues.clear();

        if (this.mode == RunMode.RANKED_MATCH) {
            Reward elo = eloReward();
            if (elo != null) add(elo, this.rewards.size() * STEP_MS);
        }

        this.xpDue = this.rewards.size() * STEP_MS;
        add(xpReward(), this.xpDue);

        this.coinsBuilt = PostMatch.coinsArrived();
        if (this.coinsBuilt) {
            long due = this.xpDue + COINS_AFTER_XP_MS;
            for (Reward coins : coinRewards()) {
                add(coins, due);
                due += COINS_APART_MS;
            }
            this.questShown = PostMatch.questCoins();
        }
    }

    private int questShown;

    private static final long COINS_AFTER_XP_MS = 1_000L;
    private static final long COINS_APART_MS = 500L;

    private long xpDue;

    private void add(Reward reward, long due) {
        this.rewards.add(reward);
        this.dues.add(due);
    }

    private static final int COIN_GOLD = 0xFFE8C34A;

    private List<Reward> coinRewards() {
        List<Reward> out = new ArrayList<>();
        for (PostMatch.Part part : PostMatch.coinParts()) {
            String amount = java.text.NumberFormat.getIntegerInstance(java.util.Locale.US)
                    .format(part.amount);
            String reason = part.reason == null ? "" : part.reason;
            String key = "vivet.endgame.tag." + reason;
            String tag = net.minecraft.locale.Language.getInstance().has(key)
                    ? Component.translatable(key).getString() : reason;

            out.add(new Reward(new RewardRow(VanillaIcon.Kind.COIN,
                    Component.translatable("vivet.endgame.coins", amount).getString(),
                    COIN_GOLD, tag), null, Cue.COINS));
        }
        return out;
    }

    private void appendLateCoins() {
        this.coinsBuilt = true;
        long next = Math.max(this.xpDue + COINS_AFTER_XP_MS, Math.max(0L, into()) + 200L);
        for (Reward coins : coinRewards()) {
            add(coins, next);
            next += COINS_APART_MS;
        }
        this.questShown = PostMatch.questCoins();
        rebuild();
    }

    private void appendLateQuest() {
        int more = PostMatch.questCoins() - this.questShown;
        this.questShown = PostMatch.questCoins();
        if (more <= 0) return;
        String amount = java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(more);
        add(new Reward(new RewardRow(VanillaIcon.Kind.COIN,
                Component.translatable("vivet.endgame.coins", amount).getString(),
                COIN_GOLD, Component.translatable("vivet.endgame.tag.quest").getString()), null, Cue.COINS),
            Math.max(this.xpDue + COINS_AFTER_XP_MS, Math.max(0L, into()) + 200L));
        rebuild();
    }

    private boolean waitingForRelay() {
        if (System.currentTimeMillis() - this.shownAt >= WAIT_MS) return false;

        if (this.mode == RunMode.RANKED_MATCH && !PostMatch.hasArrived()) return true;

        return AccountXp.earnsXp(this.mode)
                && (!PostMatch.xpArrived() || !PostMatch.coinsArrived());
    }

    private Reward eloReward() {
        MojangAuth.Profile before = this.summary == null ? this.ratingBefore
                : this.summary.ratingBefore;
        PostMatch.Standing standing = PostMatch.current();
        MojangAuth.Profile after = standing == null ? null : standing.profile();
        MojangAuth.Profile shown = after != null ? after : before;

        if (shown == null) return null;

        if (!shown.placed) {
            return new Reward(
                    new RewardRow(VanillaIcon.Kind.TOTEM,
                            Component.translatable("vivet.endgame.unranked").getString(),
                            Theme.DANGER, matchTag()),
                    placementBar(before, shown), Cue.ELO_FLAT);
        }

        int ratingAfter = shown.rating == null ? 0 : shown.rating;
        int ratingBefore = before != null && before.rating != null ? before.rating : ratingAfter;
        int delta = standing != null ? standing.change() : ratingAfter - ratingBefore;

        RewardRow row = new RewardRow(VanillaIcon.Kind.TOTEM, pointsLabel(delta),
                deltaColor(delta), matchTag());
        Cue cue = delta > 0 ? Cue.ELO_UP : delta < 0 ? Cue.ELO_DOWN : Cue.ELO_FLAT;

        return new Reward(row, eloBar(before, shown, ratingBefore, ratingAfter), cue);
    }

    private EndgameBar eloBar(MojangAuth.Profile before, MojangAuth.Profile shown,
                              int ratingBefore, int ratingAfter) {
        String rankAfter = shown.rank;
        String rankBefore = before != null && before.rank != null && before.placed
                ? before.rank : rankAfter;

        if ("grand_champion".equals(rankAfter)) {
            return championBar(before, shown, ratingBefore, ratingAfter);
        }

        int crossing = Integer.compare(ladderIndex(rankAfter), ladderIndex(rankBefore));

        return new EndgameBar(
                capOf(rankBefore), capOf(Ranks.nextRankId(rankBefore)),
                capOf(rankAfter), capOf(Ranks.nextRankId(rankAfter)),
                fractionIn(rankBefore, ratingBefore), fractionIn(rankAfter, ratingAfter),
                crossing, Ranks.colorOf(rankAfter), true);
    }

    private EndgameBar.Cap capOf(String rankId) {
        return EndgameBar.Cap.rank(rankId, true);
    }

    private float fractionIn(String rankId, int rating) {
        Integer low = Ranks.thresholdOf(rankId);
        Integer high = Ranks.nextThreshold(rankId);
        if (low == null || high == null || high <= low) return 1.0F;

        return (rating - low) / (float) (high - low);
    }

    private static int ladderIndex(String rankId) {
        return Math.round(Ranks.progressOf(rankId) * 1000.0F);
    }

    private EndgameBar placementBar(MojangAuth.Profile before, MojangAuth.Profile shown) {
        int total = Math.max(1, shown.placementMatches);
        int played = Math.min(total, Math.max(0, shown.placementGames));
        int wasPlayed = before == null ? Math.max(0, played - 1)
                : Math.min(total, Math.max(0, before.placementGames));

        int remaining = total - played;
        String note = remaining <= 0 ? "Placed" : remaining + " to go";

        return EndgameBar.of(
                EndgameBar.Cap.rank("unranked", false), EndgameBar.Cap.text(note),
                wasPlayed / (float) total, played / (float) total, Theme.ACCENT);
    }

    private EndgameBar championBar(MojangAuth.Profile before, MojangAuth.Profile shown,
                                   int ratingBefore, int ratingAfter) {
        Integer place = shown.position;
        Integer[] ladder = shown.ladder;

        if (place != null && place <= 1) {
            return EndgameBar.still(EndgameBar.Cap.milestone(1), EndgameBar.Cap.milestone(1),
                    1.0F, Ranks.colorOf(Ranks.SPEED_KING));
        }

        int chasing = place == null ? 100 : Ranks.chasedPlace(place);
        Integer held = Ranks.heldPlace(place);
        int[] bounds = Ranks.placeBounds(chasing, held, ladder);

        if (bounds == null) {
            return EndgameBar.still(capForPlace(held), EndgameBar.Cap.milestone(chasing), 0.0F,
                    Ranks.colorOf("grand_champion"));
        }

        int wasChasing = before != null && before.position != null
                ? Ranks.chasedPlace(before.position) : chasing;
        Integer wasHeld = Ranks.heldPlace(before == null ? null : before.position);

        int[] wasBounds = Ranks.placeBounds(wasChasing, wasHeld, ladder);
        if (wasBounds == null) wasBounds = bounds;

        return new EndgameBar(
                capForPlace(wasHeld), EndgameBar.Cap.milestone(wasChasing),
                capForPlace(held), EndgameBar.Cap.milestone(chasing),
                fractionBetween(wasBounds, ratingBefore), fractionBetween(bounds, ratingAfter),
                Integer.compare(wasChasing, chasing), Ranks.colorOf("grand_champion"), true);
    }

    private static EndgameBar.Cap capForPlace(Integer place) {
        return place == null ? EndgameBar.Cap.rank("grand_champion", true)
                : EndgameBar.Cap.milestone(place);
    }

    private static float fractionBetween(int[] bounds, int rating) {
        return (rating - bounds[0]) / (float) (bounds[1] - bounds[0]);
    }

    private String pointsLabel(int delta) {
        return (delta > 0 ? "+" : "") + delta + " Elo";
    }

    private static int deltaColor(int delta) {
        return delta > 0 ? Theme.SUCCESS : delta < 0 ? Theme.DANGER : Theme.TEXT_MUTED;
    }

    private Reward xpReward() {
        AccountXp.State state = AccountXp.current(this.mode, this.band);

        RewardRow row = new RewardRow(VanillaIcon.Kind.BOTTLE, "+" + state.gained() + " XP",
                state.gained() > 0 ? Theme.SUCCESS : Theme.TEXT_MUTED, matchTag());

        if (state.gained() <= 0) {
            return new Reward(row, EndgameBar.still(
                    EndgameBar.Cap.level(state.level()), EndgameBar.Cap.level(state.level() + 1),
                    state.progress(), Theme.SUCCESS), Cue.XP);
        }

        if (!state.leveledUp()) {
            return new Reward(row, EndgameBar.of(
                    EndgameBar.Cap.level(state.level()), EndgameBar.Cap.level(state.level() + 1),
                    state.progressBefore(), state.progress(), Theme.SUCCESS), Cue.XP);
        }

        return new Reward(row, new EndgameBar(
                EndgameBar.Cap.level(state.levelBefore()),
                EndgameBar.Cap.level(state.levelBefore() + 1),
                EndgameBar.Cap.level(state.level()), EndgameBar.Cap.level(state.level() + 1),
                state.progressBefore(), state.progress(), 1, Theme.SUCCESS, true), Cue.XP);
    }

    private long into() {
        return this.armedAt == 0L ? -1L : System.currentTimeMillis() - this.armedAt;
    }

    private long ageOf(int index) {
        long into = into();
        if (into < 0 || index >= this.dues.size()) return -1L;
        return into - this.dues.get(index);
    }

    private int landed() {
        long into = into();
        if (into < 0) return 0;

        int landed = 0;
        while (landed < this.dues.size() && this.dues.get(landed) <= into) landed++;
        return landed;
    }

    private int barIndex() {
        int index = 0;
        int landed = landed();
        for (int i = 0; i < landed; i++) {
            if (this.rewards.get(i).bar() != null) index = i;
        }
        return index;
    }

    private float barProgress() {
        if (this.rewards.isEmpty()) return 0.0F;

        long age = ageOf(barIndex());
        if (age < 0) return 0.0F;
        return Math.max(0.0F, Math.min(1.0F, age / (float) BAR_MS));
    }

    private record Layout(int cardW, int clipBox, int clipScale, int cardH, int totalW, int pad) {
        int totalH() {
            return this.cardH;
        }
    }

    private Layout layout() {
        int room = Math.max(120, this.width - 16);
        int headroom = Math.max(120, this.height - 16);

        for (int scale = 3; scale >= 1; scale--) {
            int clipBox = EndgameClip.widthAt(scale) + CLIP_PAD * 2;
            int cardW = Math.max(MIN_CARD_W,
                    Math.min(MAX_CARD_W, Math.round(clipBox * CARD_RATIO)));
            int totalW = cardW + COLUMN_GAP + clipBox;

            int pad = PADDING;
            int cardH = Math.max(cardHeight(pad), Math.round(cardW * CARD_ASPECT));

            int height = Math.max(cardH, clipBox + COLUMN_GAP + BUTTON_H);

            if (scale == 1 || (totalW <= room && height <= headroom)) {
                int least = clipBox + COLUMN_GAP + BUTTON_H;
                return new Layout(cardW, clipBox, scale, Math.min(height, Math.max(least, headroom)),
                        totalW, pad);
            }
        }

        return new Layout(MIN_CARD_W, EndgameClip.widthAt(1) + CLIP_PAD * 2, 1,
                Math.max(cardHeight(PADDING), Math.round(MIN_CARD_W * CARD_ASPECT)),
                MIN_CARD_W, PADDING);
    }

    private int cardHeight(int pad) {
        int rows = Math.min(this.rewards.size(), MAX_SHOWN_ROWS) * (RewardRow.HEIGHT + ROW_GAP);
        int reasonH = this.reason.isEmpty() ? 0 : 12;

        return pad + BigText.height(HEAD_SCALE) + reasonH + 16 + rows + 12 + EndgameBar.HEIGHT
                + pad;
    }

    private int[] buttonBounds(Layout l, int columnX, int top) {
        int wanted = BigText.widthAt(this.font,
                Component.translatable("vivet.button.continue_results"), BUTTON_LABEL_SCALE) + 24;
        int width = Math.min(l.clipBox(), Math.max(l.clipBox() / 2, wanted));

        return new int[] {
                columnX + (l.clipBox() - width) / 2, top + l.totalH() - BUTTON_H, width };
    }

    @Override
    protected void init() {
        super.init();

        MediaClips.prepare(this.winClip);
        MediaSounds.prepare(this.winSound);

        if (!this.winSounded) {
            this.winSounded = true;
            MediaSounds.play(this.winSound, 1.0F);
        }

        Layout l = layout();
        int left = (this.width - l.totalW()) / 2;
        int top = Math.max(4, (this.height - l.totalH()) / 2);
        int columnX = left + l.cardW() + COLUMN_GAP;

        int[] bounds = buttonBounds(l, columnX, top);
        addRenderableWidget(new FlatButton(
                bounds[0], bounds[1], bounds[2], BUTTON_H,
                Component.empty(), this::advance, FlatButton.Style.INVISIBLE));
    }

    private boolean standingWorthReplaying() {
        PostMatch.Standing standing = PostMatch.current();
        return standing != null && standing.moved();
    }

    private boolean xpWorthReplaying() {
        return PostMatch.xpArrived() && PostMatch.xpGained() > 0;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.armedAt == 0L && loadedIn() && !waitingForRelay()) {
            buildRewards();

            this.armedBlind = !PostMatch.hasArrived()
                    || (AccountXp.earnsXp(this.mode) && !PostMatch.xpArrived());
            this.armedAt = System.currentTimeMillis();
            rebuild();
        } else if (this.armedAt != 0L && !this.coinsBuilt && !this.leaving
                && PostMatch.coinsArrived()) {
            appendLateCoins();
        } else if (this.armedAt != 0L && this.coinsBuilt && !this.leaving
                && PostMatch.questCoins() > this.questShown) {
            appendLateQuest();
        } else if (this.armedBlind && !this.leaving
                && (standingWorthReplaying() || xpWorthReplaying())) {
            this.armedBlind = false;
            this.sounded = 0;
            this.crossed = 0;
            buildRewards();
            this.armedAt = System.currentTimeMillis();
            rebuild();
        }

        int landed = landed();
        while (this.sounded < landed) {
            sound(this.rewards.get(this.sounded).cue());
            this.sounded++;
        }

        while (this.crossed < landed) {
            long age = ageOf(this.crossed);
            if (age < BAR_MS / 2) break;

            crossingSound(this.rewards.get(this.crossed));
            this.crossed++;
        }
    }

    private boolean loadedIn() {
        long now = System.currentTimeMillis();
        if (now - this.shownAt >= SETTLE_CAP_MS) return true;
        if (this.settledAt == 0L) return false;

        boolean clipReady = this.winClip == null || MediaClips.isReady(this.winClip);
        return clipReady && now - this.settledAt >= SETTLE_BEAT_MS;
    }

    private void noteFrame() {
        long now = System.currentTimeMillis();
        long gap = this.lastFrameAt == 0L ? Long.MAX_VALUE : now - this.lastFrameAt;
        this.lastFrameAt = now;

        if (this.settledAt != 0L) return;

        this.smoothFrames = gap <= SMOOTH_FRAME_MS ? this.smoothFrames + 1 : 0;
        if (this.smoothFrames >= SETTLED_FRAMES) this.settledAt = now;
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private void sound(Cue cue) {
        switch (cue) {
            case ELO_UP -> Cues.eloGranted(true);
            case ELO_DOWN -> Cues.eloGranted(false);
            case ELO_FLAT -> Cues.eloFlat();
            case XP -> Cues.xpGranted();
            case COINS -> Cues.coinsGranted();
        }
    }

    private void crossingSound(Reward reward) {
        if (reward.bar() == null || reward.bar().crossing() == 0) return;

        if (reward.cue() == Cue.XP) {
            Cues.levelUp();
            return;
        }
        if (reward.bar().crossing() > 0) {
            Cues.rankUp();
        } else {
            Cues.rankDown();
        }
    }

    private void advance() {
        if (this.leaving) return;
        this.leaving = true;

        if (this.summary != null) {
            RunSummary shown = this.summary;
            ResultsPrompt.show(() -> new VictoryScreen(shown));
            return;
        }

        com.vivet.speedrun.client.WorldActions.trace("pressed Continue on the end of match screen");
        com.vivet.speedrun.client.WorldActions.returnFromMatch(this.reason, this.won);
    }

    @Override
    protected void drawScaled(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                              float partialTick) {
        noteFrame();
        Theme.fill(graphics, 0, 0, this.width, this.height, Theme.SCRIM);

        Layout l = layout();
        int left = (this.width - l.totalW()) / 2;
        int top = Math.max(4, (this.height - l.totalH()) / 2);
        int columnX = left + l.cardW() + COLUMN_GAP;

        card(graphics, left, top, l);

        Theme.panel(graphics, columnX, top, l.clipBox(), l.clipBox());

        EndgameClip.draw(graphics,
                columnX + (l.clipBox() - EndgameClip.widthAt(l.clipScale())) / 2,
                top + (l.clipBox() - EndgameClip.heightAt(l.clipScale())) / 2,
                l.clipScale(), System.currentTimeMillis() - this.shownAt,
                Theme.withAlpha(0xFFFFFFFF, 0xEE), this.winClip);

        int[] bounds = buttonBounds(l, columnX, top);
        button(graphics, bounds[0], bounds[1], bounds[2], mouseX, mouseY);

        drawWidgets(graphics, mouseX, mouseY, partialTick);
    }

    private void card(GuiGraphicsExtractor graphics, int x, int y, Layout l) {
        Theme.panel(graphics, x, y, l.cardW(), l.totalH());

        int inner = l.cardW() - l.pad() * 2;
        int textX = x + l.pad();
        int centre = x + l.cardW() / 2;
        int cursor = y + l.pad();

        Component headline = this.title.copy().withStyle(ChatFormatting.BOLD);
        int headScale = BigText.fit(this.font, headline, inner, HEAD_SCALE);

        BigText.centered(graphics, this.font, headline, centre, cursor, accent(), headScale,
                HEAD_OUTLINE);
        cursor += BigText.height(headScale);

        if (!this.reason.isEmpty()) {
            graphics.centeredText(this.font, Component.literal(this.reason), centre, cursor + 2,
                    Theme.TEXT_MUTED);
            cursor += 12;
        }

        Theme.divider(graphics, textX, cursor + 6, inner);
        cursor += 16;

        float tagScale = tagScale(inner);

        int barY = y + l.totalH() - l.pad() - EndgameBar.HEIGHT;

        int rowsTop = cursor;
        int rowsBottom = barY - 10;
        int landed = landed();
        int contentBottom = rowsTop + landed * (RewardRow.HEIGHT + ROW_GAP) - ROW_GAP;
        float scroll = rowScroll(Math.max(0, contentBottom - rowsBottom));

        Clip.on(graphics, textX - 2, rowsTop - 2, textX + inner + 2, rowsBottom + 2);
        try {
            int rowY = rowsTop - Math.round(scroll);
            for (int i = 0; i < landed; i++) {
                if (rowY + RewardRow.HEIGHT >= rowsTop - 2 && rowY <= rowsBottom + 2) {
                    this.rewards.get(i).row()
                            .draw(graphics, this.font, textX, rowY, inner, ageOf(i), tagScale);
                }
                rowY += RewardRow.HEIGHT + ROW_GAP;
            }
        } finally {
            Clip.off(graphics);
        }

        Theme.divider(graphics, textX, barY - 8, inner);

        if (!this.rewards.isEmpty()) {
            EndgameBar bar = this.rewards.get(barIndex()).bar();
            if (bar != null) bar.draw(graphics, this.font, textX, barY, inner, barProgress());
        }
    }

    private float scrollShown;
    private long scrollAt;

    private static final float SCROLL_EASE_MS = 90.0F;

    private float rowScroll(int target) {
        long now = System.currentTimeMillis();
        float dt = this.scrollAt == 0L ? 0.0F : Math.min(100.0F, now - this.scrollAt);
        this.scrollAt = now;

        float step = 1.0F - (float) Math.exp(-dt / SCROLL_EASE_MS);
        this.scrollShown += (target - this.scrollShown) * step;
        if (Math.abs(target - this.scrollShown) < 0.5F) this.scrollShown = target;
        return this.scrollShown;
    }

    private float tagScale(int inner) {
        float scale = Float.MAX_VALUE;
        for (Reward reward : this.rewards) {
            scale = Math.min(scale, reward.row().tagFit(this.font, inner));
        }
        return scale == Float.MAX_VALUE ? 0.0F : scale;
    }

    private void button(GuiGraphicsExtractor graphics, int x, int y, int width,
                        int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + BUTTON_H;

        Theme.roundedBox(graphics, x, y, width, BUTTON_H, BUTTON_RADIUS,
                Theme.withAlpha(Theme.ACCENT, hovered ? 0xFF : 0xCC));
        Theme.roundedBoxOutline(graphics, x, y, width, BUTTON_H, BUTTON_RADIUS,
                Theme.withAlpha(Theme.ACCENT, 0xFF));
        Theme.fill(graphics, x + 2, y + 1, x + width - 2, y + 2, Theme.withAlpha(0xFFFFFFFF, 0x18));

        Component label = Component.translatable("vivet.button.continue_results");
        float scale = BigText.widthAt(this.font, label, BUTTON_LABEL_SCALE) > width - 12
                ? 1.0F : BUTTON_LABEL_SCALE;

        BigText.centeredAt(graphics, this.font, label, x + width / 2,
                y + (BUTTON_H - BigText.heightAt(scale)) / 2, 0xFFFFFFFF, scale, 0);
    }

    @Override
    public void onClose() {
        advance();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
