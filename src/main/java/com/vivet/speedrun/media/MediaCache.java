package com.vivet.speedrun.media;

import com.google.gson.Gson;
import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.config.ConfigManager;
import com.vivet.speedrun.config.ModConfig;
import com.vivet.speedrun.p2p.MojangAuth;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class MediaCache {
    private static final Path MEDIA_DIR = Paths.get("config", "vivet_speedrun", "media");

    private static final String MANIFEST_PATH = "/media/manifest.json";

    private static final java.time.Duration TIMEOUT = java.time.Duration.ofMinutes(2);

    private static final Gson GSON = new Gson();

    private static final Map<String, Path> ready = new HashMap<>();

    private static volatile boolean started;

    private MediaCache() {
    }

    public static Path path(String id) {
        synchronized (ready) {
            return ready.get(id);
        }
    }

    public static void fetchOnce() {
        if (started) return;
        started = true;

        Thread worker = new Thread(() -> {
            try {
                refresh();
            } catch (Exception e) {
                VivetSpeedrun.LOGGER.debug("Media unavailable", e);
            }
        }, "vivet-media");

        worker.setDaemon(true);
        worker.start();
    }

    private static void refresh() throws Exception {
        String url = MojangAuth.httpBase(ConfigManager.get().relayUrl) + MANIFEST_PATH;
        String body = MojangAuth.get(url).join();

        MediaManifest manifest = GSON.fromJson(body, MediaManifest.class);
        if (manifest == null || !manifest.verify(ModConfig.RELEASE_PUBLIC_KEY)) return;

        Files.createDirectories(MEDIA_DIR);

        Set<String> wanted = new HashSet<>();
        for (MediaManifest.Entry entry : manifest.entries) {
            Path file = MEDIA_DIR.resolve(fileNameFor(entry));
            wanted.add(file.getFileName().toString());

            try {
                if (!isAlready(file, entry)) fetch(entry, file);

                synchronized (ready) {
                    ready.put(entry.id, file);
                }
            } catch (Exception e) {
                VivetSpeedrun.LOGGER.debug("Could not download media {}",
                        entry.id, e);
            }
        }

        sweep(wanted);
    }

    private static boolean isAlready(Path file, MediaManifest.Entry entry) {
        try {
            return Files.isRegularFile(file) && Files.size(file) == entry.size;
        } catch (Exception e) {
            return false;
        }
    }

    private static void fetch(MediaManifest.Entry entry, Path target) throws Exception {
        Path partial = target.resolveSibling(target.getFileName() + ".part");
        Files.deleteIfExists(partial);

        HttpRequest request = HttpRequest.newBuilder(URI.create(entry.url))
                .timeout(TIMEOUT)
                .header("user-agent", "vivet-speedrun/" + VivetSpeedrun.VERSION)
                .GET()
                .build();

        HttpResponse<InputStream> response =
                MojangAuth.http().send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new IllegalStateException("media download returned " + response.statusCode());
        }

        try (InputStream in = response.body()) {
            Files.copy(in, partial, StandardCopyOption.REPLACE_EXISTING);
        }

        long actualSize = Files.size(partial);
        if (actualSize != entry.size) {
            Files.deleteIfExists(partial);
            throw new IllegalStateException(
                    "media " + entry.id + " is " + actualSize + " bytes, expected " + entry.size);
        }

        String actualHash = sha256(partial);
        if (!actualHash.equalsIgnoreCase(entry.sha256)) {
            Files.deleteIfExists(partial);
            throw new IllegalStateException("media " + entry.id + " failed its hash check");
        }

        Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
        VivetSpeedrun.LOGGER.info("Downloaded media {} ({} KiB)",
                entry.id, actualSize / 1024);
    }

    private static void sweep(Set<String> keep) {
        try (Stream<Path> files = Files.list(MEDIA_DIR)) {
            files.filter(Files::isRegularFile)
                    .filter(file -> !keep.contains(file.getFileName().toString()))
                    .forEach(file -> {
                        try {
                            Files.deleteIfExists(file);
                        } catch (Exception ignored) {
                        }
                    });
        } catch (Exception ignored) {
        }
    }

    private static String fileNameFor(MediaManifest.Entry entry) {
        String extension = switch (entry.kind == null ? "" : entry.kind) {
            case "image" -> ".png";
            case "sound" -> ".ogg";
            default -> ".bin";
        };
        return entry.id + "-" + entry.sha256.substring(0, 16).toLowerCase(Locale.ROOT) + extension;
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
