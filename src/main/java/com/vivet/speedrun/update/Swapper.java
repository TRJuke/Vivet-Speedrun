package com.vivet.speedrun.update;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public final class Swapper {
    private static final long PARENT_WAIT_MS = 60_000L;

    private static final long RETRY_MS = 60_000L;

    private static final long POLL_MS = 250L;

    private Swapper() {
    }

    private static final String RELAUNCH_MARKER = "--relaunch";

    public static void main(String[] args) {
        if (args.length < 4) {
            System.out.println("Expected 4 arguments, got " + args.length);
            return;
        }

        Path oldJar = Path.of(args[0]);
        Path stagedJar = Path.of(args[1]);
        Path destination = Path.of(args[2]);
        long parentPid = parseLong(args[3]);
        List<String> relaunch = relaunchCommand(args);

        System.out.println("Updating Vivet Speedrun:");
        System.out.println("  replacing " + oldJar);
        System.out.println("  with      " + stagedJar);
        System.out.println("  as        " + destination);

        boolean swapped = false;
        try {
            swap(oldJar, stagedJar, destination, parentPid);
            swapped = true;
        } catch (Throwable failure) {
            System.out.println("Update failed: " + failure);
            writeRescueNote(oldJar, stagedJar);
        }

        if (!relaunch.isEmpty()) startGame(relaunch, swapped);
    }

    private static List<String> relaunchCommand(String[] args) {
        List<String> command = new ArrayList<>();

        boolean collecting = false;
        for (String argument : args) {
            if (collecting) {
                command.add(argument);
            } else if (RELAUNCH_MARKER.equals(argument)) {
                collecting = true;
            }
        }
        return command;
    }

    private static void startGame(List<String> command, boolean swapped) {
        System.out.println((swapped ? "Update installed. " : "") + "Starting: " + command);

        try {
            new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (Exception e) {
            System.out.println("Could not restart the launcher: " + e);
        }
    }

    private static void swap(Path oldJar, Path stagedJar, Path destination, long parentPid)
            throws IOException {
        waitForParent(parentPid);

        if (!Files.isRegularFile(stagedJar) || Files.size(stagedJar) == 0L) {
            System.out.println("New version already installed.");
            cleanUp(stagedJar.getParent());
            return;
        }

        boolean mustDeleteOld = !oldJar.equals(destination);

        if (mustDeleteOld && Files.exists(oldJar) && !deleteWithRetries(oldJar)) {
            throw new IOException("The old version is still in use: " + oldJar);
        }

        if (!moveWithRetries(stagedJar, destination)) {
            throw new IOException("Couldn't install the new version: " + destination);
        }

        System.out.println("Done.");
        cleanUp(stagedJar.getParent());
    }

    private static void waitForParent(long pid) {
        if (pid <= 0L) return;

        long deadline = System.currentTimeMillis() + PARENT_WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            Optional<ProcessHandle> handle = ProcessHandle.of(pid);
            if (handle.isEmpty() || !handle.get().isAlive()) {
                System.out.println("Game closed.");

                sleep(400L);
                return;
            }
            sleep(POLL_MS);
        }
        System.out.println("Game still running, updating anyway.");
    }

    private static boolean deleteWithRetries(Path file) {
        long deadline = System.currentTimeMillis() + RETRY_MS;

        while (true) {
            try {
                Files.deleteIfExists(file);
                if (!Files.exists(file)) return true;
            } catch (IOException retryable) {
            }

            if (System.currentTimeMillis() >= deadline) return false;
            sleep(POLL_MS);
        }
    }

    private static boolean moveWithRetries(Path from, Path to) {
        long deadline = System.currentTimeMillis() + RETRY_MS;

        while (true) {
            try {
                Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
                return true;
            } catch (IOException retryable) {
                if (System.currentTimeMillis() >= deadline) return false;
                sleep(POLL_MS);
            }
        }
    }

    private static void cleanUp(Path staging) {
        if (staging == null || !Files.isDirectory(staging)) return;

        try (Stream<Path> tree = Files.walk(staging)) {
            tree.sorted(Comparator.reverseOrder()).forEach(Swapper::deleteQuietly);
        } catch (Exception ignored) {
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
        }
    }

    private static void writeRescueNote(Path oldJar, Path stagedJar) {
        try {
            Path note = stagedJar.getParent().resolve("FINISH-THIS-UPDATE.txt");
            Files.writeString(note, """
                    The Vivet Speedrun update downloaded but couldn't replace the old version.
                    Usually an antivirus has the file open.

                    Your game still works. To finish the update, close Minecraft and:

                      1. Delete:  %s
                      2. Move:    %s
                         into your mods folder.

                    DON'T keep both in the mods folder.
                    """.formatted(oldJar, stagedJar), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private static long parseLong(String text) {
        try {
            return Long.parseLong(text.trim());
        } catch (RuntimeException notANumber) {
            return 0L;
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
