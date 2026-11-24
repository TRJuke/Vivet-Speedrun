package com.vivet.speedrun.update;

import com.google.gson.Gson;
import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.p2p.MojangAuth;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

public final class Channel {
    static final String MODRINTH_PROJECT = "vivet-speedrun";

    private static final String MODRINTH_CDN = "cdn.modrinth.com";

    private static final class ModrinthVersion {
        String version_number;
        String version_type;
        ModrinthFile[] files;
    }

    private static final class ModrinthFile {
        String url;
        boolean primary;
        Long size;
        Map<String, String> hashes;
    }

    private static final Gson GSON = new Gson();

    private Channel() {
    }

    static void checkAtLaunch() {
        String minecraft = net.minecraft.SharedConstants.getCurrentVersion().name();
        String url = "https://api.modrinth.com/v2/project/" + MODRINTH_PROJECT + "/version"
                + "?loaders=" + encode("[\"fabric\"]") + "&game_versions=" + encode("[\"" + minecraft + "\"]");

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))

                .header("user-agent", "vivet-speedrun/" + VivetSpeedrun.VERSION + " (vivetspeedrun.com)")
                .header("accept", "application/json")
                .GET()
                .build();

        MojangAuth.http().sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> response.statusCode() == 200
                        ? GSON.fromJson(response.body(), ModrinthVersion[].class) : null)
                .whenComplete((versions, error) -> {
                    if (error != null || versions == null) return;
                    ModrinthVersion newest = newestFor(versions);
                    if (newest == null) return;
                    ModrinthFile file = primaryOf(newest);
                    if (file == null) return;
                    UpdateChecker.readFeed(signed -> agree(newest, file, signed));
                });
    }

    public static void signedIn(String body) {
    }

    public static boolean hasUpdateSwitch() {
        return false;
    }

    private static ModrinthVersion newestFor(ModrinthVersion[] versions) {
        boolean onRelease = !VivetSpeedrun.VERSION.contains("-");
        ModrinthVersion best = null;
        for (ModrinthVersion version : versions) {
            if (version == null || version.version_number == null) continue;
            if (onRelease && !"release".equals(version.version_type)) continue;
            if (!UpdateChecker.isNewerThanUs(version.version_number)) continue;
            if (best == null || UpdateChecker.isNewer(version.version_number, best.version_number)) best = version;
        }
        return best;
    }

    private static ModrinthFile primaryOf(ModrinthVersion version) {
        if (version.files == null) return null;
        for (ModrinthFile file : version.files) {
            if (file != null && file.primary) return file;
        }
        return version.files.length == 1 ? version.files[0] : null;
    }

    private static void agree(ModrinthVersion version, ModrinthFile file, ReleaseManifest signed) {
        if (!version.version_number.equals(signed.version)) return;
        if (file.url == null || !file.url.equals(signed.url)) return;
        if (!MODRINTH_CDN.equals(URI.create(signed.url).getHost())) return;
        if (file.size == null || !file.size.equals(signed.size)) return;
        String sha512 = file.hashes == null ? null : file.hashes.get("sha512");
        if (sha512 == null || !sha512.matches("[0-9a-fA-F]{128}")) return;

        signed.sha512 = sha512;
        UpdateChecker.offer(signed);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
