package com.vivet.speedrun.game;

import com.vivet.speedrun.VivetSpeedrun;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class LogImport {
    public record Source(String name, Path logs, Path records, int games, int bests) {
    }

    private LogImport() {
    }

    public static List<Source> find() {
        Path game = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
        Path ownConfig = game.resolve("config");

        Set<Path> candidates = new LinkedHashSet<>();

        Path parent = game.getParent();
        if (parent != null) {
            addChildren(candidates, parent);
            if (parent.getParent() != null) addChildren(candidates, parent.getParent());
        }

        String home = System.getProperty("user.home", "");
        String appData = System.getenv("APPDATA");
        List<Path> roots = new ArrayList<>();
        if (appData != null) {
            candidates.add(Paths.get(appData, ".minecraft"));
            roots.add(Paths.get(appData, "ModrinthApp", "profiles"));
            roots.add(Paths.get(appData, "PrismLauncher", "instances"));
        }
        if (!home.isEmpty()) {
            candidates.add(Paths.get(home, ".minecraft"));
            candidates.add(Paths.get(home, "Library", "Application Support", "minecraft"));
            roots.add(Paths.get(home, "curseforge", "minecraft", "Instances"));
            roots.add(Paths.get(home, "Library", "Application Support", "ModrinthApp", "profiles"));
            roots.add(Paths.get(home, "Library", "Application Support", "PrismLauncher", "instances"));
            roots.add(Paths.get(home, ".local", "share", "ModrinthApp", "profiles"));
            roots.add(Paths.get(home, ".local", "share", "PrismLauncher", "instances"));
        }
        for (Path root : roots) addChildren(candidates, root);

        List<Source> found = new ArrayList<>();
        List<Long> when = new ArrayList<>();
        Set<Path> seen = new java.util.HashSet<>();

        for (Path candidate : candidates) {
            for (Path dir : new Path[] { candidate, candidate.resolve(".minecraft"), candidate.resolve("minecraft") }) {
                Path config = dir.resolve("config").toAbsolutePath().normalize();
                if (config.equals(ownConfig) || !Files.isDirectory(config) || !seen.add(real(config))) continue;

                Path logs = config.resolve("vivet_speedrun").resolve("logs.json");
                Path records = config.resolve("vivet_speedrun_records.json");
                if (!Files.exists(logs) && !Files.exists(records)) continue;

                int games = MatchLogs.readFrom(logs).size();
                int bests = PersonalBests.bestsIn(records);
                if (games == 0 && bests == 0) continue;

                found.add(new Source(nameOf(dir), logs, records, games, bests));
                when.add(Math.max(modified(logs), modified(records)));
            }
        }

        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < found.size(); i++) order.add(i);
        order.sort(Comparator.comparingLong((Integer i) -> when.get(i)).reversed());

        List<Source> sorted = new ArrayList<>();
        for (int i : order) sorted.add(found.get(i));
        return sorted;
    }

    public static int[] bring(Source source) {
        int games = MatchLogs.adopt(MatchLogs.readFrom(source.logs()));
        int records = PersonalBests.adopt(source.records());
        VivetSpeedrun.LOGGER.info("Imported {} games and {} records from {}",
                games, records, source.name());
        return new int[] { games, records };
    }

    private static void addChildren(Set<Path> into, Path folder) {
        if (!Files.isDirectory(folder)) return;
        try (Stream<Path> listed = Files.list(folder)) {
            listed.filter(Files::isDirectory).forEach(into::add);
        } catch (IOException | RuntimeException e) {
            VivetSpeedrun.LOGGER.debug("Could not look in {}", folder, e);
        }
    }

    private static String nameOf(Path dir) {
        String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
        if (!name.equals(".minecraft") && !name.equals("minecraft")) return name;

        Path above = dir.getParent();
        String outer = above == null || above.getFileName() == null ? "" : above.getFileName().toString();
        boolean launcherHome = outer.equalsIgnoreCase("Roaming") || outer.equalsIgnoreCase("Application Support")
                || (above != null && above.equals(Paths.get(System.getProperty("user.home", ""))));
        return launcherHome ? "Minecraft Launcher" : outer;
    }

    private static Path real(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return path;
        }
    }

    private static long modified(Path path) {
        try {
            return Files.exists(path) ? Files.getLastModifiedTime(path).toMillis() : 0L;
        } catch (IOException e) {
            return 0L;
        }
    }
}
