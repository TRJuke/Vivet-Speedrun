package com.vivet.speedrun.update;

import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.p2p.MojangAuth;

import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class UpdateInstaller {
    private static final String STAGING_DIR = ".vivet-update";

    private static final String HELPER_DIR = "helper";

    private static final List<String> LAUNCHERS =
            List.of("multimc", "prismlauncher", "polymc");

    private static volatile boolean busy;
    private static volatile boolean staged;

    private static volatile boolean armed;

    private UpdateInstaller() {
    }

    public static boolean isBusy() {
        return busy;
    }

    public static boolean isStaged() {
        return staged;
    }

    public static void install(ReleaseManifest release, Consumer<Long> onDone,
                               Consumer<String> onFailed) {
        if (busy || staged) return;
        busy = true;

        Thread worker = new Thread(() -> {
            try {
                stage(release);
                staged = true;
                onDone.accept(release.size);
            } catch (Exception e) {
                VivetSpeedrun.LOGGER.error("Update failed", e);
                onFailed.accept(reasonFor(e));
            } finally {
                busy = false;
            }
        }, "vivet-update");

        worker.setDaemon(true);
        worker.start();
    }

    private static void stage(ReleaseManifest release) throws Exception {
        Path running = runningJar();
        if (running == null) {
            throw new IllegalStateException(
                    "Can't update this copy of the mod.");
        }

        Path staging = running.getParent().resolve(STAGING_DIR);
        Files.createDirectories(staging);

        Path target = staging.resolve("vivet-speedrun-" + safeName(release.version) + ".jar");
        Path partial = staging.resolve("download.part");

        Files.deleteIfExists(partial);
        download(release.url, partial);

        long actualSize = Files.size(partial);
        if (actualSize != release.size) {
            Files.deleteIfExists(partial);
            throw new IllegalStateException("The download was incomplete. Try again.");
        }

        String actualHash = digest(partial, "SHA-256");
        boolean modrinthAgrees = release.sha512 == null
                || digest(partial, "SHA-512").equalsIgnoreCase(release.sha512);
        if (!actualHash.equalsIgnoreCase(release.sha256) || !modrinthAgrees) {
            Files.deleteIfExists(partial);
            throw new IllegalStateException(
                    "The download was corrupted. Try again.");
        }

        Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
        VivetSpeedrun.LOGGER.info("Update {} downloaded to {}",
                release.version, target);

        armSwap(running, target);
    }

    private static void armSwap(Path oldJar, Path newJar) {
        if (armed) return;

        Path helper;
        try {
            helper = extractHelper(newJar.getParent());
        } catch (Exception e) {
            VivetSpeedrun.LOGGER.error("Could not set up the updater", e);
            writeRescueNote(oldJar, newJar);
            return;
        }

        armed = true;
        Path destination = oldJar.getParent().resolve(newJar.getFileName());

        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> startHelper(helper, oldJar, newJar, destination), "vivet-update-swap"));
    }

    private static Path extractHelper(Path staging) throws Exception {
        String binaryName = Swapper.class.getName();
        String resource = binaryName.replace('.', '/') + ".class";

        Path helperDir = staging.resolve(HELPER_DIR);
        Path target = helperDir.resolve(resource);
        Files.createDirectories(target.getParent());

        try (InputStream in = Swapper.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Couldn't set up the update.");
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return helperDir;
    }

    private static void startHelper(Path helperDir, Path oldJar, Path newJar, Path destination) {
        try {
            List<String> command = new ArrayList<>(List.of(
                    javaBinary(),
                    "-cp", helperDir.toString(),
                    Swapper.class.getName(),
                    oldJar.toString(),
                    newJar.toString(),
                    destination.toString(),
                    String.valueOf(ProcessHandle.current().pid())));

            if (relaunchWanted) {
                List<String> relaunch = relaunchCommand();
                if (!relaunch.isEmpty()) {
                    command.add("--relaunch");
                    command.addAll(relaunch);
                }
            }

            new ProcessBuilder(command)
                    .directory(helperDir.toFile())

                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(
                            helperDir.getParent().resolve("update-log.txt").toFile()))
                    .start();
        } catch (Exception e) {
            writeRescueNote(oldJar, newJar);
        }
    }

    private static String javaBinary() {
        Path home = Path.of(System.getProperty("java.home"));
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

        if (windows) {
            Path windowless = home.resolve("bin").resolve("javaw.exe");
            if (Files.isRegularFile(windowless)) return windowless.toString();

            Path console = home.resolve("bin").resolve("java.exe");
            if (Files.isRegularFile(console)) return console.toString();
        } else {
            Path unix = home.resolve("bin").resolve("java");
            if (Files.isRegularFile(unix)) return unix.toString();
        }

        return windows ? "javaw" : "java";
    }

    private static volatile boolean relaunchWanted;

    public static boolean canRestart() {
        return !relaunchCommand().isEmpty();
    }

    public static void restartNow(Runnable quit) {
        relaunchWanted = true;
        quit.run();
    }

    private static List<String> relaunchCommand() {
        try {
            Optional<ProcessHandle> parent = ProcessHandle.current().parent();
            if (parent.isEmpty()) return List.of();

            Optional<String> executable = parent.get().info().command();
            if (executable.isEmpty()) return List.of();

            Path launcher = Path.of(executable.get());
            String name = launcher.getFileName().toString().toLowerCase(Locale.ROOT);
            if (LAUNCHERS.stream().noneMatch(name::startsWith)) return List.of();

            String instance = instanceName();
            if (instance == null) return List.of();

            return List.of(launcher.toString(), "--launch", instance);
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String instanceName() {
        Path gameDir = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();

        Path instanceDir = gameDir.getParent();
        if (instanceDir == null) return null;

        Path instances = instanceDir.getParent();
        if (instances == null
                || !"instances".equalsIgnoreCase(instances.getFileName().toString())) {
            return null;
        }
        return instanceDir.getFileName().toString();
    }

    public static void resumeStagedSwap() {
        try {
            Path running = runningJar();
            if (running == null) return;

            Path staging = running.getParent().resolve(STAGING_DIR);
            if (!Files.isDirectory(staging)) return;

            Path candidate = newestStagedJar(staging);
            if (candidate == null || !isNewerThanRunning(candidate)) {
                deleteTree(staging);
                return;
            }

            VivetSpeedrun.LOGGER.info("Update already downloaded, installs when you close the game: {}", candidate.getFileName());

            staged = true;
            armSwap(running, candidate);
        } catch (Exception e) {
            VivetSpeedrun.LOGGER.warn("Could not load the downloaded update", e);
        }
    }

    private static Path newestStagedJar(Path staging) throws Exception {
        try (Stream<Path> entries = Files.list(staging)) {
            return entries
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .max(Comparator.comparing(path -> path.getFileName().toString()))
                    .orElse(null);
        }
    }

    private static boolean isNewerThanRunning(Path stagedJar) {
        String name = stagedJar.getFileName().toString();
        String version = name.replaceFirst("^vivet-speedrun-", "").replaceFirst("\\.jar$", "");

        try {
            return net.fabricmc.loader.api.Version.parse(version)
                    .compareTo(net.fabricmc.loader.api.Version.parse(VivetSpeedrun.VERSION)) > 0;
        } catch (Exception unparseable) {
            return false;
        }
    }

    private static void deleteTree(Path root) {
        try (Stream<Path> tree = Files.walk(root)) {
            tree.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    private static void writeRescueNote(Path oldJar, Path newJar) {
        try {
            Files.writeString(newJar.getParent().resolve("FINISH-THIS-UPDATE.txt"),
                    """
                    The Vivet Speedrun update downloaded but couldn't install itself.

                    Your game still works, and it will try again next time you close the game. To
                    finish it now, close Minecraft and:

                      1. Delete:  %s
                      2. Move:    %s
                         into your mods folder.

                    DON'T keep both in the mods folder.
                    """.formatted(oldJar, newJar));
        } catch (Exception ignored) {
        }
    }

    private static void download(String url, Path into) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(java.time.Duration.ofMinutes(5))
                .header("user-agent", "vivet-speedrun/" + VivetSpeedrun.VERSION)
                .GET()
                .build();

        HttpResponse<InputStream> response = MojangAuth.http()
                .send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "Download failed (" + response.statusCode() + "). Try again.");
        }

        try (InputStream body = response.body()) {
            Files.copy(body, into, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String digest(Path file, String algorithm) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algorithm);

        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static Path runningJar() {
        return FabricLoader.getInstance().getModContainer(VivetSpeedrun.MOD_ID)
                .map(container -> container.getOrigin().getPaths())
                .filter(paths -> !paths.isEmpty())
                .map(List::getFirst)
                .filter(path -> path.getFileName().toString().endsWith(".jar"))
                .filter(Files::isRegularFile)
                .orElse(null);
    }

    private static String safeName(String version) {
        return version.replaceAll("[^A-Za-z0-9._+-]", "_");
    }

    private static String reasonFor(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank()
                ? "Couldn't download the update." : message;
    }
}
