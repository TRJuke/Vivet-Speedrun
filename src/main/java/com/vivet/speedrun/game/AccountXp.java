package com.vivet.speedrun.game;

import com.vivet.speedrun.p2p.MojangAuth;
import com.vivet.speedrun.p2p.PostMatch;

public final class AccountXp {
    private AccountXp() {
    }

    public record State(int level, int intoLevel, int levelCost, int gained,
                        int levelBefore, int intoBefore, int costBefore) {
        public float progress() {
            if (this.levelCost <= 0) return 0.0F;
            return Math.min(1.0F, Math.max(0.0F, this.intoLevel / (float) this.levelCost));
        }

        public float progressBefore() {
            if (leveledUp()) {
                if (this.costBefore <= 0) return 0.0F;
                return Math.min(1.0F, Math.max(0.0F, this.intoBefore / (float) this.costBefore));
            }
            if (this.levelCost <= 0) return 0.0F;
            return Math.min(1.0F, Math.max(0.0F,
                    (this.intoLevel - this.gained) / (float) this.levelCost));
        }

        public boolean leveledUp() {
            return this.levelBefore > 0 && this.level > this.levelBefore;
        }
    }

    private static final State NOTHING = new State(1, 0, 0, 0, 0, 0, 0);

    public static State current(RunMode mode, Difficulty band) {
        MojangAuth.Profile profile = MojangAuth.knownProfile();
        if (profile == null) return NOTHING;

        int level = Math.max(1, profile.level);

        int gained = PostMatch.xpGained();

        if (!earnsXp(mode)) return new State(level, profile.xpInto, profile.xpNext, 0, 0, 0, 0);

        return new State(level, profile.xpInto, profile.xpNext, gained,
                PostMatch.levelBefore(), PostMatch.intoBefore(), PostMatch.costBefore());
    }

    public static boolean earnsXp(RunMode mode) {
        return mode != null && mode != RunMode.SOLO && mode != RunMode.PRIVATE;
    }
}
