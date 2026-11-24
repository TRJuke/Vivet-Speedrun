package com.vivet.speedrun.client;

import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.client.screen.MatchLoadingScreen;
import com.vivet.speedrun.client.ui.Cosmetics;
import com.vivet.speedrun.client.ui.NameLabel;
import com.vivet.speedrun.client.ui.Placements;
import com.vivet.speedrun.client.ui.RankTitle;
import com.vivet.speedrun.client.ui.Ranks;
import com.vivet.speedrun.media.Media;
import com.vivet.speedrun.media.MediaClips;
import com.vivet.speedrun.media.MediaImages;
import com.vivet.speedrun.media.MediaSounds;
import com.vivet.speedrun.p2p.MojangAuth;
import com.vivet.speedrun.p2p.RankedApi;
import com.vivet.speedrun.p2p.Wardrobe;
import com.vivet.speedrun.session.Racer;
import com.vivet.speedrun.session.Session;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public final class LoadingTakeover {
    public enum Kind {
        RANKED,

        PUBLIC,

        SOLO,

        PRIVATE,

        PRACTICE;

        public boolean isVersus() {
            return this == RANKED || this == PUBLIC;
        }
    }

    private static final long LEAD_MS = 420L;
    private static final long DROP_MS = 440L;

    public static final long VS_MS = 620L;

    public static final long VS_FALL_MS = 230L;

    public static final long MENU_FADE_MS = 400L;
    public static final long LOAD_FADE_MS = 400L;
    private static final long IRIS_MS = 170L;
    private static final long PLAIN_HOLD_MS = 1000L;
    private static final long GAP_MS = 260L;
    private static final long STAGGER_MS = 55L;
    private static final long PRACTICE_TAIL_MS = 320L;

    private static final long SETTLE_MS = 280L;

    private static final long CLIP_CAP_MS = 3000L;

    private static final float DROP_HIT = 0.42F;

    public static final long FADE_IN_MS = 800L;

    public static final long FADE_OUT_MS = 800L;

    private static final long WAIT_PULSE_MS = 1400L;

    private static final long HARD_LIMIT_MS = 180_000L;

    private static final long SUPPRESS_MS = 20_000L;

    public static final class Card {
        public final String name;
        public final String labelId;
        public final String accountId;
        public final int index;

        public final String clipId;
        public final String soundId;

        public final long dropAt;
        public final long clipAt;
        public final long clipMs;

        public MojangAuth.Profile profile;

        private MojangAuth.Profile foldedFrom;
        private List<Placements.Badge> folded = List.of();

        Card(String name, String labelId, String accountId, String clipId, String soundId,
             int index, long dropAt, long clipAt, long clipMs) {
            this.name = name;
            this.labelId = labelId;
            this.accountId = accountId;
            this.clipId = clipId;
            this.soundId = soundId;
            this.index = index;
            this.dropAt = dropAt;
            this.clipAt = clipAt;
            this.clipMs = clipMs;
        }

        public NameLabel label() {
            if (this.profile != null) {
                RankTitle.remember(this.name, this.accountId, rankId(), placed());
            }

            return NameLabel.byId(this.labelId);
        }

        public String rankId() {
            return this.profile == null ? null : Ranks.displayRank(this.profile);
        }

        public boolean placed() {
            return this.profile != null && this.profile.placed;
        }

        public int rating() {
            return this.profile == null || this.profile.rating == null ? 0 : this.profile.rating;
        }

        public Integer position() {
            return this.profile == null || !this.profile.placed ? null : this.profile.position;
        }

        public List<Placements.Badge> badges() {
            if (this.profile != this.foldedFrom) {
                this.foldedFrom = this.profile;
                this.folded = this.profile == null
                        ? List.of() : Placements.fold(this.profile.badges);
            }
            return this.folded;
        }
    }

    private record Beat(long at, Runnable what) {
    }

    private static volatile boolean armed;
    private static boolean releasing;

    private static long coveringSince;

    private static Kind kind = Kind.PRACTICE;
    private static long openedAt;
    private static List<Card> cards = List.of();
    private static long animEnd;

    private static long vsAt = -1L;

    private static boolean fromMenu;

    private static long goAt;

    private static long exitAt;

    private static Runnable onReleased;

    private static final List<Beat> beats = new ArrayList<>();
    private static int nextBeat;
    private static long nextPulse;
    private static boolean pulseOffbeat;
    private static boolean outFired;

    private static long suppressUntil;

    private static int labelsSeen = -1;

    private LoadingTakeover() {
    }

    public static boolean isArmed() {
        return armed;
    }

    public static boolean isHolding() {
        return armed;
    }

    public static long coverNow() {
        return coveringSince == 0L ? 0L : System.currentTimeMillis() - coveringSince;
    }

    public static boolean hasSequence() {
        return openedAt != 0L;
    }

    public static boolean hidesTheWorld() {
        if (!armed) return false;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return false;
        if (minecraft.level == null || minecraft.player == null) return true;

        return !hasSequence() || now() < blackAt();
    }

    public static boolean isReleasing() {
        return releasing;
    }

    public static Kind kind() {
        return kind;
    }

    public static List<Card> cards() {
        return cards;
    }

    public static long now() {
        return openedAt == 0L ? 0L : System.currentTimeMillis() - openedAt;
    }

    public static long versusAt() {
        return vsAt;
    }

    public static long menuFadeMs() {
        return fromMenu ? MENU_FADE_MS : 0L;
    }

    public static boolean goArrived() {
        return goAt > 0L;
    }

    public static long exitAt() {
        return exitAt > 0L ? exitAt : -1L;
    }

    public static long blackAt() {
        return exitAt <= 0L ? Long.MAX_VALUE : exitAt + FADE_IN_MS;
    }

    public static long playingAt() {
        return exitAt <= 0L ? Long.MAX_VALUE : exitAt + FADE_IN_MS + FADE_OUT_MS;
    }

    public static void suppressNext() {
        suppressUntil = System.currentTimeMillis() + SUPPRESS_MS;
    }

    public static boolean wanted() {
        boolean suppressed = suppressUntil > 0L && System.currentTimeMillis() < suppressUntil;
        suppressUntil = 0L;
        return !suppressed;
    }

    public static void open(Kind opening) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;
        open(opening, minecraft.gui.screen());
    }

    public static void open(Kind opening, net.minecraft.client.gui.screens.Screen from) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;

        kind = opening;
        coveringSince = System.currentTimeMillis();
        openedAt = 0L;
        goAt = 0L;
        exitAt = 0L;
        outFired = false;
        cards = List.of();
        vsAt = -1L;

        net.minecraft.client.gui.screens.Screen menu = from;
        if (menu instanceof MatchLoadingScreen) menu = null;
        fromMenu = menu != null;
        armed = true;

        minecraft.gui.setScreen(new MatchLoadingScreen(menu));
        Cues.introSwoop();
        VivetSpeedrun.LOGGER.info("Match intro shown: {}", opening);
    }

    public static void worldReady() {
        if (!armed || hasSequence()) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) return;

        if (HostedLoading.isActive() && !HostedLoading.inRaceWorld(minecraft)) return;

        openedAt = System.currentTimeMillis();
        rebuild();
        VivetSpeedrun.LOGGER.info("Match intro: players joining");
    }

    public static void setRoster(List<Racer> racers) {
        if (!armed) return;

        List<String> names = new ArrayList<>();
        for (Racer racer : racers) names.add(racer.name);
        setNames(names);
    }

    private static void setNames(List<String> names) {
        List<Card> built = new ArrayList<>();
        long at = LEAD_MS;
        long versus = -1L;
        List<Card> before = cards;
        labelsSeen = Session.labelsVersion();

        Wardrobe.refresh();

        MediaImages.prepare(Cosmetics.mediaOf(Session.matchBackground()));

        MediaClips.prepare(Cosmetics.myMedia("win_gif", null));
        MediaSounds.prepare(Cosmetics.myMedia("win_audio", null));

        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            String labelId = labelFor(name);
            String accountId = Session.accountIdOf(name);

            boolean mine = name.equals(Session.localName());

            if (mine && (accountId == null || accountId.isBlank())) {
                var self = net.minecraft.client.Minecraft.getInstance().getGameProfile();
                if (self != null && self.id() != null) accountId = self.id().toString();
            }

            com.vivet.speedrun.client.ui.PlayerHead.skinOf(name, accountId);
            String clipFallback = labelId == null ? null : Media.clipOf(labelId);
            String soundFallback = labelId == null ? null : Media.soundOf(labelId);
            String clipId = mine ? Cosmetics.myMedia("loading_gif", clipFallback)
                    : Cosmetics.wornMedia(name, "loading_gif", clipFallback);
            String soundId = mine ? Cosmetics.myMedia("loading_audio", soundFallback)
                    : Cosmetics.wornMedia(name, "loading_audio", soundFallback);

            MediaClips.prepare(clipId);
            MediaSounds.prepare(soundId);

            if (kind != Kind.PRIVATE) {
                MediaClips.prepare(Cosmetics.wornMedia(name, "win_gif", null));
                MediaSounds.prepare(Cosmetics.wornMedia(name, "win_audio", null));
            }

            long dropAt;
            long clipAt;
            long clipMs;

            if (kind == Kind.PRIVATE) {
                dropAt = LEAD_MS + i * STAGGER_MS;
                clipAt = -1L;
                clipMs = 0L;
            } else {
                if (kind.isVersus() && i == 1) {
                    versus = at;
                    at += VS_MS + GAP_MS;
                }

                dropAt = at;
                at += DROP_MS;
                clipAt = at;
                clipMs = clipLengthOf(clipId, soundId);
                at += IRIS_MS + clipMs;
                if (i < names.size() - 1) at += GAP_MS;
            }

            Card card = new Card(name, labelId, accountId, clipId, soundId, i,
                                 dropAt, clipAt, clipMs);

            for (Card old : before) {
                if (old.name.equals(name) && old.profile != null) card.profile = old.profile;
            }
            built.add(card);
        }

        cards = List.copyOf(built);
        vsAt = versus;
        animEnd = switch (kind) {
            case PRIVATE -> LEAD_MS + Math.max(0, cards.size() - 1) * STAGGER_MS + DROP_MS;
            case PRACTICE -> LEAD_MS + PRACTICE_TAIL_MS;
            default -> at;
        };

        loadProfiles();
        rebuildBeats();
    }

    private static void rebuild() {
        if (kind == Kind.PRACTICE) {
            cards = List.of();
            vsAt = -1L;
            animEnd = LEAD_MS + PRACTICE_TAIL_MS;
            rebuildBeats();
            return;
        }
        setNames(rosterNames());
    }

    private static List<String> rosterNames() {
        List<String> names = new ArrayList<>();
        for (Racer racer : Session.racers()) names.add(racer.name);
        if (names.isEmpty() && !Session.localName().isBlank()) names.add(Session.localName());
        return names;
    }

    private static List<String> currentNames() {
        List<String> names = new ArrayList<>();
        for (Card card : cards) names.add(card.name);
        return names;
    }

    private static String labelFor(String name) {
        NameLabel label = NameLabel.of(name);
        return label == null ? null : label.id();
    }

    private static long clipLengthOf(String clipId, String soundId) {
        if (clipId == null && soundId == null) return PLAIN_HOLD_MS;

        long picture = MediaClips.lengthMs(clipId);
        long sound = MediaSounds.lengthMs(soundId);

        long shortest = Long.MAX_VALUE;
        if (picture > 0L) shortest = picture;
        if (sound > 0L) shortest = Math.min(shortest, sound);
        if (shortest == Long.MAX_VALUE) return PLAIN_HOLD_MS;

        return Math.min(shortest, CLIP_CAP_MS);
    }

    private static void loadProfiles() {
        if (kind == Kind.PRIVATE || kind == Kind.PRACTICE) return;

        MojangAuth.Profile mine = MojangAuth.knownProfile();
        for (Card card : cards) {
            if (mine != null && card.name.equals(Session.localName())) {
                card.profile = mine;
                continue;
            }
            if (card.profile != null) continue;

            RankedApi.loadProfileOf(card.name,
                    profile -> Minecraft.getInstance().execute(() -> {
                        if (profile == null) return;
                        card.profile = profile;
                    }),
                    error -> VivetSpeedrun.LOGGER.debug("No ranked profile for match intro: {}", error));
        }
    }

    private static void rebuildBeats() {
        beats.clear();
        nextBeat = 0;

        for (Card card : cards) {
            beats.add(new Beat(card.dropAt + Math.round(DROP_MS * DROP_HIT),
                    () -> Cues.introCard(card.index)));

            if (card.clipAt >= 0L && card.soundId != null) {
                beats.add(new Beat(card.clipAt + IRIS_MS,
                        () -> MediaSounds.play(card.soundId, 1.0F)));
            }
        }
        if (vsAt >= 0L) {
            beats.add(new Beat(vsAt, Cues::introVersusFall));
            beats.add(new Beat(vsAt + VS_FALL_MS, Cues::introVersus));
        }
        beats.sort(java.util.Comparator.comparingLong(Beat::at));

        long elapsed = now();
        while (nextBeat < beats.size() && beats.get(nextBeat).at() <= elapsed) nextBeat++;

        nextPulse = animEnd + 600L;
        pulseOffbeat = false;
    }

    public static void whenReleased(Runnable action) {
        onReleased = action;
    }

    public static void go() {
        if (!armed || goAt > 0L) return;

        goAt = now();
        exitAt = Math.max(animEnd, goAt + SETTLE_MS);
        VivetSpeedrun.LOGGER.info("Match intro ends at {}ms", exitAt);
    }

    public static void tick() {
        if (!armed) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;

        if (minecraft.gui.screen() == null) {
            minecraft.gui.setScreen(new MatchLoadingScreen());
        } else if (!(minecraft.gui.screen() instanceof MatchLoadingScreen)) {
            VivetSpeedrun.LOGGER.info("Match intro closed by another screen");
            release();
            return;
        }

        if (!hasSequence()) {
            if (coverNow() > HARD_LIMIT_MS) {
                VivetSpeedrun.LOGGER.warn("Match intro closed: no world");
                release();
            }
            worldReady();
            return;
        }

        long elapsed = now();

        if (elapsed > HARD_LIMIT_MS) {
            VivetSpeedrun.LOGGER.warn("Match intro timed out");
            release();
            return;
        }

        if (kind != Kind.PRACTICE && goAt == 0L && !Session.isActive() && !Session.isHosted()) {
            VivetSpeedrun.LOGGER.info("Match intro closed: match ended");
            release();
            return;
        }

        if (kind != Kind.PRACTICE && goAt == 0L) {
            List<String> current = rosterNames();
            if (!current.equals(currentNames()) || Session.labelsVersion() != labelsSeen) {
                setNames(current);
            }
        }

        beats(elapsed);

        if (goAt == 0L && elapsed >= nextPulse && cards.size() > 1) {
            Cues.introWaiting(pulseOffbeat);
            pulseOffbeat = !pulseOffbeat;
            nextPulse = elapsed + WAIT_PULSE_MS;
        }

        if (exitAt > 0L) {
            if (!outFired && elapsed >= exitAt) {
                outFired = true;
                Cues.introOut();
            }
            if (elapsed >= playingAt()) release();
        }
    }

    private static void beats(long elapsed) {
        while (nextBeat < beats.size() && beats.get(nextBeat).at() <= elapsed) {
            beats.get(nextBeat++).what().run();
        }
    }

    public static void release() {
        if (!armed) return;

        armed = false;
        coveringSince = 0L;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.gui.screen() instanceof MatchLoadingScreen) {
            releasing = true;
            try {
                minecraft.gui.setScreen(null);
            } finally {
                releasing = false;
            }
        }

        cards = List.of();
        vsAt = -1L;
        fromMenu = false;
        beats.clear();
        openedAt = 0L;
        goAt = 0L;
        exitAt = 0L;
        outFired = false;

        Runnable begin = onReleased;
        onReleased = null;
        if (begin != null) begin.run();
    }

    public static void abort() {
        if (!armed) return;
        VivetSpeedrun.LOGGER.info("Match intro cancelled");
        release();
    }
}
