package com.vivet.speedrun.p2p;

import com.vivet.speedrun.VivetSpeedrun;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Integrity {
    private static volatile Report cached = null;

    private Integrity() {
    }

    public static final class Report {
        public String version;
        public String jar;
        public List<Mod> mods;
        public List<String> flags;
    }

    public static final class Mod {
        public String id;
        public String version;
        public String sha;

        public String parent;

        Mod(String id, String version, String sha, String parent) {
            this.id = id;
            this.version = version;
            this.sha = sha;
            this.parent = parent;
        }
    }

    public static Report get() {
        Report held = cached;
        if (held != null) return held;

        synchronized (Integrity.class) {
            if (cached != null) return cached;
            cached = build();
            return cached;
        }
    }

    private static Report build() {
        Report report = new Report();
        report.version = VivetSpeedrun.VERSION;
        report.mods = new ArrayList<>();
        report.flags = new ArrayList<>();

        try {
            ownJar(report);
            otherMods(report);
            environment(report);
        } catch (Exception error) {
            VivetSpeedrun.LOGGER.debug("Could not read install info", error);
        }

        return report;
    }

    private static void ownJar(Report report) {
        Optional<ModContainer> container =
                FabricLoader.getInstance().getModContainer(VivetSpeedrun.MOD_ID);

        if (container.isEmpty()) {
            report.flags.add("no_jar");
            return;
        }

        Path found = jarOf(container.get());
        if (found == null) {
            report.flags.add("no_jar");
            return;
        }

        String hash = hashOf(found);
        if (hash == null) {
            report.flags.add("no_jar");
            return;
        }

        report.jar = hash;
    }

    private static Path jarOf(ModContainer container) {
        try {
            for (Path root : container.getRootPaths()) {
                java.net.URI uri = root.toUri();
                String text = uri.toString();

                int mark = text.indexOf("!/");
                if (text.startsWith("jar:") && mark > 0) {
                    return Path.of(java.net.URI.create(text.substring(4, mark)));
                }

                if (Files.isRegularFile(root)) return root;
            }
        } catch (Exception error) {
            VivetSpeedrun.LOGGER.debug("Could not find a mod jar", error);
        }
        return null;
    }

    private static String hashOf(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Files.readAllBytes(file));

            StringBuilder hex = new StringBuilder(64);
            for (byte value : hash) hex.append(String.format("%02x", value));
            return hex.toString();
        } catch (Exception error) {
            return null;
        }
    }

    private static void otherMods(Report report) {
        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            String id = container.getMetadata().getId();

            if (id.equals("java") || id.equals("minecraft") || id.equals("fabricloader")
                    || id.equals("fabric-api") || id.startsWith("fabric-")) {
                continue;
            }

            String parent = container.getContainingMod()
                    .map(mod -> mod.getMetadata().getId())
                    .orElse("");

            String hash = "";
            if (parent.isEmpty()) {
                Path file = jarOf(container);
                String found = file == null ? null : hashOf(file);
                if (found != null) hash = found;
            }

            report.mods.add(new Mod(id, container.getMetadata().getVersion().getFriendlyString(),
                                    hash, parent));
        }
    }

    private static void environment(Report report) {
        try {
            List<String> arguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
            for (String argument : arguments) {
                String lower = argument.toLowerCase();
                if (lower.startsWith("-javaagent")) {
                    report.flags.add("agent");
                }
                if (lower.startsWith("-agentlib:jdwp") || lower.startsWith("-xrunjdwp")) {
                    report.flags.add("debugger");
                }
            }
        } catch (Exception error) {
            VivetSpeedrun.LOGGER.debug("Could not read the command line", error);
        }

        foreignMixins(report);
    }

    private static void foreignMixins(Report report) {
        try {
            Class<?> mixins = Class.forName("org.spongepowered.asm.mixin.Mixins");
            Object configs = mixins.getMethod("getConfigs").invoke(null);
            if (!(configs instanceof Iterable<?> list)) return;

            for (Object entry : list) {
                Object config = entry.getClass().getMethod("getConfig").invoke(entry);
                Object targets = config.getClass().getMethod("getTargets").invoke(config);
                if (!(targets instanceof Iterable<?> classes)) continue;

                String owner = String.valueOf(
                        entry.getClass().getMethod("getName").invoke(entry));

                if (owner.contains(VivetSpeedrun.MOD_ID) || owner.contains("vivet")) continue;

                for (Object target : classes) {
                    if (String.valueOf(target).startsWith("com.vivet.speedrun")) {
                        report.flags.add("foreign_mixin");
                        return;
                    }
                }
            }
        } catch (Throwable error) {
            VivetSpeedrun.LOGGER.debug("Could not check mixins", error);
        }
    }

    public static String describe() {
        Report report = get();
        long roots = report.mods.stream().filter(mod -> mod.parent.isEmpty()).count();

        return String.format("%d mods (%d bundled), jar %s%s",
                roots, report.mods.size() - roots,
                report.jar == null ? "unreadable" : report.jar.substring(0, 8),
                report.flags.isEmpty() ? "" : ", flags " + String.join(",", report.flags));
    }
}
