package com.vivet.speedrun.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.vivet.speedrun.VivetSpeedrun;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class RunHistory {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<RunRecord>>() {}.getType();
    private static final String FILE_NAME = "vivet_speedrun_history.json";

    private static List<RunRecord> records = new ArrayList<>();

    private static Path path;

    private RunHistory() {
    }

    public static void openWorld(Path worldDirectory) {
        path = worldDirectory == null ? null : worldDirectory.resolve(FILE_NAME);
        records = new ArrayList<>();
        load();
    }

    public static void closeWorld() {
        save();
        path = null;
        records = new ArrayList<>();
    }

    public static List<RunRecord> all() {
        return records;
    }

    public static void add(RunRecord record) {
        records.add(0, record);
        save();
    }

    public static void replaceNewest(RunRecord record) {
        if (records.isEmpty()) {
            records.add(record);
        } else {
            records.set(0, record);
        }
        save();
    }

    public static RunRecord newest() {
        return records.isEmpty() ? null : records.get(0);
    }

    public static void load() {
        if (path == null || !Files.exists(path)) {
            records = new ArrayList<>();
            return;
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            List<RunRecord> loaded = GSON.fromJson(reader, LIST_TYPE);
            records = loaded != null ? loaded : new ArrayList<>();
            records.forEach(RunRecord::sanitize);
        } catch (Exception e) {
            VivetSpeedrun.LOGGER.error("Could not read run history", e);
            records = new ArrayList<>();
        }
    }

    public static void save() {
        if (path == null) return;

        try {
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);

            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(records, writer);
            }
        } catch (Exception e) {
            VivetSpeedrun.LOGGER.error("Could not save run history", e);
        }
    }
}
