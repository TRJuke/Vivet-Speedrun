package com.vivet.speedrun.update;

import com.google.gson.Gson;
import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.config.ConfigManager;
import com.vivet.speedrun.p2p.MojangAuth;

import net.fabricmc.loader.api.Version;

public final class UpdateChecker {
    private static final class Feed {
        ReleaseManifest release;
    }

    private static final Gson GSON = new Gson();

    private static volatile ReleaseManifest available;

    private static volatile boolean started;

    private UpdateChecker() {
    }

    public static ReleaseManifest available() {
        if (Channel.hasUpdateSwitch() && !ConfigManager.get().autoUpdates) return null;
        return available;
    }

    public static void offer(ReleaseManifest release) {
        if (release == null) return;
        if (!release.verify(com.vivet.speedrun.config.ModConfig.RELEASE_PUBLIC_KEY)) return;
        if (!isNewerThanUs(release.version)) return;

        synchronized (UpdateChecker.class) {
            ReleaseManifest held = available;
            if (held != null && !isNewer(release.version, held.version)) return;
            available = release;
        }

        VivetSpeedrun.LOGGER.info("Update available: {}", release.version);
    }

    public static void checkOnce() {
        if (started) return;
        started = true;
        Channel.checkAtLaunch();
    }

    static void readFeed(java.util.function.Consumer<ReleaseManifest> then) {
        String url = MojangAuth.httpBase(ConfigManager.get().relayUrl) + "/version?mc="
                + java.net.URLEncoder.encode(net.minecraft.SharedConstants.getCurrentVersion().name(),
                        java.nio.charset.StandardCharsets.UTF_8);

        MojangAuth.get(url)
                .thenApply(body -> GSON.fromJson(body, Feed.class))
                .whenComplete((feed, error) -> {
                    if (error != null || feed == null || feed.release == null) return;

                    ReleaseManifest release = feed.release;
                    if (!release.verify(com.vivet.speedrun.config.ModConfig.RELEASE_PUBLIC_KEY)) return;
                    then.accept(release);
                });
    }

    static boolean isNewerThanUs(String candidate) {
        return isNewer(candidate, VivetSpeedrun.VERSION);
    }

    static boolean isNewer(String candidate, String than) {
        try {
            return Version.parse(candidate).compareTo(Version.parse(than)) > 0;
        } catch (Exception e) {
            VivetSpeedrun.LOGGER.debug("Unreadable version '{}'", candidate);
            return false;
        }
    }
}
