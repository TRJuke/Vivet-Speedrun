package com.vivet.speedrun.update;

import com.google.gson.Gson;
import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.config.ConfigManager;
import com.vivet.speedrun.p2p.MojangAuth;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class Channel {
    static final String SITE = "vivetspeedrun.com";

    private static final Gson GSON = new Gson();

    private Channel() {
    }

    static void checkAtLaunch() {
        if (!ConfigManager.get().autoUpdates) return;

        String minecraft = net.minecraft.SharedConstants.getCurrentVersion().name();
        String url = "https://" + SITE + "/update/" + URLEncoder.encode(minecraft, StandardCharsets.UTF_8)
                + ".json";

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("user-agent", "vivet-speedrun/" + VivetSpeedrun.VERSION)
                .header("accept", "application/json")
                .GET()
                .build();

        MojangAuth.http().sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> response.statusCode() == 200
                        ? GSON.fromJson(response.body(), ReleaseManifest.class) : null)
                .whenComplete((release, error) -> {
                    if (error != null || release == null || release.url == null) return;
                    if (!onSite(release.url)) return;
                    UpdateChecker.offer(release);
                });
    }

    public static void signedIn(String body) {
    }

    public static boolean hasUpdateSwitch() {
        return true;
    }

    static boolean onSite(String url) {
        try {
            URI uri = URI.create(url);
            return "https".equals(uri.getScheme()) && SITE.equals(uri.getHost()) && uri.getPort() == -1;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
