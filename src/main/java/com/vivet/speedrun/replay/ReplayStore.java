package com.vivet.speedrun.replay;

import com.vivet.speedrun.VivetSpeedrun;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class ReplayStore {
    private static final Path DIR = Paths.get("config", "vivet_speedrun", "replays");

    private static final String WHOLE = ".vrp";
    private static final String TRACE = ".trace";

    private static final Pattern FILE = Pattern.compile("^[0-9a-f]{32}$");

    private static final long KEEP_MS = 31L * 24 * 60 * 60 * 1000;

    private static final Path OLD_HELD = Paths.get("config", "vivet_speedrun_suspended_replay.json");

    private ReplayStore() {
    }

    public static boolean isFile(String id) {
        return id != null && FILE.matcher(id).matches();
    }

    private static Path pathFor(String file, String kind) {
        if (!isFile(file)) return null;

        Path root = DIR.toAbsolutePath().normalize();
        Path path = root.resolve(file + kind).normalize();
        if (!path.startsWith(root)) {
            VivetSpeedrun.LOGGER.warn("Rejected a replay path outside its folder");
            return null;
        }
        return path;
    }

    public static byte[] cachedTrace(String file) {
        Path path = pathFor(file, TRACE);
        if (path == null || !Files.isRegularFile(path)) return null;
        try {
            if (Files.size(path) > ReplayTrace.MAX_FILE_BYTES) return null;
            return Files.readAllBytes(path);
        } catch (IOException e) {
            VivetSpeedrun.LOGGER.debug("Could not read cached replay", e);
            return null;
        }
    }

    public static void saveTrace(String file, byte[] bytes) {
        Path path = pathFor(file, TRACE);
        if (path == null || bytes == null || bytes.length == 0) return;
        try {
            Files.createDirectories(path.getParent());
            Path writing = path.resolveSibling(file + ".writing");
            Files.write(writing, bytes);
            move(writing, path);
        } catch (IOException e) {
            VivetSpeedrun.LOGGER.warn("Could not save replay", e);
        }
    }

    public static Path cachedWhole(String file, long bytes) {
        Path path = pathFor(file, WHOLE);
        try {
            if (path == null || !Files.isRegularFile(path)) return null;
            if (bytes > 0 && Files.size(path) != bytes) return null;
            return path;
        } catch (IOException e) {
            return null;
        }
    }

    public static Path downloading(String file) {
        Path path = pathFor(file, WHOLE);
        return path == null ? null : path.resolveSibling(file + ".writing");
    }

    public static Path keepWhole(String file, Path written) {
        Path path = pathFor(file, WHOLE);
        if (path == null || written == null) return null;
        try {
            move(written, path);
            return path;
        } catch (IOException e) {
            VivetSpeedrun.LOGGER.warn("Could not save replay", e);
            return null;
        }
    }

    public static void ensureFolder() throws IOException {
        Files.createDirectories(DIR);
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void keepOnly(Set<String> listed) {
        if (!Files.isDirectory(DIR)) return;
        long now = System.currentTimeMillis();

        try (Stream<Path> files = Files.list(DIR)) {
            for (Path path : (Iterable<Path>) files::iterator) {
                String name = path.getFileName().toString();
                int dot = name.lastIndexOf('.');
                String id = dot < 0 ? "" : name.substring(0, dot);
                String kind = dot < 0 ? "" : name.substring(dot);
                boolean ours = isFile(id) && (kind.equals(WHOLE) || kind.equals(TRACE));
                boolean old = now - Files.getLastModifiedTime(path).toMillis() > KEEP_MS;

                if (!ours || old || (listed != null && !listed.contains(id))) {
                    Files.deleteIfExists(path);
                }
            }
        } catch (IOException | RuntimeException e) {
            VivetSpeedrun.LOGGER.debug("Could not clean up replays folder", e);
        }
    }

    public static void sweepOld() {
        try {
            Files.deleteIfExists(OLD_HELD);
        } catch (IOException e) {
            VivetSpeedrun.LOGGER.debug("Could not delete old replay", e);
        }
        keepOnly(null);
    }
}
