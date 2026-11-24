package com.vivet.speedrun.config;

import com.vivet.speedrun.game.Difficulty;
import com.vivet.speedrun.game.DimensionPool;
import com.vivet.speedrun.game.WorldRule;

import java.util.LinkedHashMap;
import java.util.Map;

public class ModConfig {
    public static final String DEFAULT_RELAY = "https://relay.vivetspeedrun.com";

    private static final String[] LEGACY_RELAYS = {
            "https://vivet-relay.vivetcorp.workers.dev",
            "https://relay.vivetcorp.store",
    };

    static String carriedForward(String relayUrl) {
        String bare = relayUrl.trim();
        while (bare.endsWith("/")) bare = bare.substring(0, bare.length() - 1);
        bare = bare.toLowerCase(java.util.Locale.ROOT);
        if (bare.startsWith("wss://")) bare = "https://" + bare.substring(6);
        for (String legacy : LEGACY_RELAYS) {
            if (bare.equals(legacy)) return DEFAULT_RELAY;
        }
        return relayUrl;
    }

    public static final String RELEASE_PUBLIC_KEY =
            "MCowBQYDK2VwAyEANpv0fsS1cczTthXTSwopXz91QDyfsSUrttu9d9u0xmE=";

    public enum AfterRun {
        ASK("Ask Me"),
        AUTO_RESET("Auto Reset"),
        NOTHING("Do Nothing");

        private final String title;

        AfterRun(String title) {
            this.title = title;
        }

        public String title() {
            return this.title;
        }
    }

    public Difficulty difficulty = Difficulty.EASY;

    public boolean playToLastFinisher = false;

    public boolean includeEasierItems = false;

    public DimensionPool dimensionPool = DimensionPool.ANY;

    public boolean allowRerolls = true;

    public AfterRun afterRun = AfterRun.ASK;

    public int customAudioVolume = 100;

    public float customAudioGain() {
        return Math.max(0, Math.min(100, this.customAudioVolume)) / 100.0F;
    }

    public Map<String, Long> practiceSeeds = new LinkedHashMap<>();

    public Map<String, Boolean> worldRules = new LinkedHashMap<>();

    public Map<String, Integer> ruleLevels = new LinkedHashMap<>();

    public transient Map<String, Boolean> sessionRules;

    public transient Map<String, Integer> sessionLevels;

    public Difficulty publicBand = Difficulty.EASY;

    public Difficulty rankedBand = Difficulty.EASY;

    public String privateDifficulty = "normal";

    public boolean privateFriendsOnly = false;

    public net.minecraft.world.Difficulty privateWorldDifficulty() {
        net.minecraft.world.Difficulty parsed =
                net.minecraft.world.Difficulty.byName(this.privateDifficulty);
        return parsed == null ? net.minecraft.world.Difficulty.NORMAL : parsed;
    }

    public void setPrivateWorldDifficulty(net.minecraft.world.Difficulty difficulty) {
        this.privateDifficulty = (difficulty == null
                ? net.minecraft.world.Difficulty.NORMAL : difficulty).getSerializedName();
    }

    public Difficulty rankedSoloBand = Difficulty.EASY;

    public boolean rankedSoloSetSeed = false;

    public boolean tutorialPlayPulse = false;

    public boolean pitySystem = true;

    public boolean discordStatus = true;

    public boolean autoUpdates = true;

    public boolean questsCollapsed = false;

    public boolean bountiesSeen = false;

    public int bountiesPick = -1;

    public String playerName = "";

    public String relayUrl = DEFAULT_RELAY;

    public boolean isRuleEnabled(WorldRule rule) {
        if (this.sessionRules != null) {
            Boolean shared = this.sessionRules.get(rule.key());
            if (shared != null) return shared;
        }
        return ownRule(rule);
    }

    public boolean ownRule(WorldRule rule) {
        return this.worldRules.getOrDefault(rule.key(), false);
    }

    public int levelOf(WorldRule rule) {
        if (this.sessionLevels != null) {
            Integer shared = this.sessionLevels.get(rule.key());
            if (shared != null) return clampLevel(rule, shared);
        }
        return ownLevel(rule);
    }

    public int ownLevel(WorldRule rule) {
        Integer stored = this.ruleLevels.get(rule.key());
        return stored == null ? 1 : clampLevel(rule, stored);
    }

    public void setLevel(WorldRule rule, int level) {
        this.ruleLevels.put(rule.key(), clampLevel(rule, level));
    }

    private static int clampLevel(WorldRule rule, int level) {
        return Math.max(1, Math.min(rule.maxLevel(), level));
    }

    public Map<String, Boolean> ruleSnapshot() {
        Map<String, Boolean> snapshot = new LinkedHashMap<>();
        for (WorldRule rule : WorldRule.values()) {
            snapshot.put(rule.key(), ownRule(rule));
        }
        return snapshot;
    }

    public Map<String, Integer> levelSnapshot() {
        Map<String, Integer> snapshot = new LinkedHashMap<>();
        for (WorldRule rule : WorldRule.values()) {
            if (rule.hasLevels()) snapshot.put(rule.key(), ownLevel(rule));
        }
        return snapshot;
    }

    public void setRuleEnabled(WorldRule rule, boolean enabled) {
        this.worldRules.put(rule.key(), enabled);
    }

    public void resetWorldRules() {
        this.worldRules.clear();
        this.ruleLevels.clear();
        for (WorldRule rule : WorldRule.values()) {
            this.worldRules.put(rule.key(), false);
        }
    }

    public void resetWorldRules(WorldRule.Category category) {
        for (WorldRule rule : WorldRule.values()) {
            if (rule.category() != category) continue;

            this.worldRules.put(rule.key(), false);
            this.ruleLevels.remove(rule.key());
        }
    }

    public void sanitize() {
        if (this.difficulty == null) this.difficulty = Difficulty.EASY;
        if (this.publicBand == null) this.publicBand = Difficulty.EASY;
        if (this.rankedBand == null) this.rankedBand = Difficulty.EASY;
        if (this.rankedSoloBand == null) this.rankedSoloBand = Difficulty.EASY;
        if (this.dimensionPool == null) this.dimensionPool = DimensionPool.ANY;
        if (this.afterRun == null) this.afterRun = AfterRun.ASK;
        if (this.practiceSeeds == null) this.practiceSeeds = new LinkedHashMap<>();
        if (this.worldRules == null) this.worldRules = new LinkedHashMap<>();
        if (this.ruleLevels == null) this.ruleLevels = new LinkedHashMap<>();
        if (this.playerName == null) this.playerName = "";

        setPrivateWorldDifficulty(privateWorldDifficulty());
        if (this.relayUrl == null || this.relayUrl.isBlank()) this.relayUrl = DEFAULT_RELAY;
        this.relayUrl = carriedForward(this.relayUrl.trim());

        this.ruleLevels.entrySet().removeIf(entry -> {
            WorldRule rule = WorldRule.byKey(entry.getKey());
            return rule == null || !rule.hasLevels() || entry.getValue() == null;
        });
        for (Map.Entry<String, Integer> entry : this.ruleLevels.entrySet()) {
            entry.setValue(clampLevel(WorldRule.byKey(entry.getKey()), entry.getValue()));
        }
    }
}
