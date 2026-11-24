package com.vivet.speedrun.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.vivet.speedrun.VivetSpeedrun;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static final Path CONFIG_PATH = Paths.get("config", "vivet_speedrun.json");

    private static ModConfig config = new ModConfig();

    private ConfigManager() {
    }

    public static ModConfig get() {
        return config;
    }

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            config = new ModConfig();
            config.resetWorldRules();
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
            ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
            config = loaded != null ? loaded : new ModConfig();
        } catch (Exception e) {
            VivetSpeedrun.LOGGER.error("Could not read config, using defaults", e);
            config = new ModConfig();
        }

        config.sanitize();
    }

    public static void save() {
        try {
            config.sanitize();
            Path parent = CONFIG_PATH.getParent();
            if (parent != null) Files.createDirectories(parent);

            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            VivetSpeedrun.LOGGER.error("Could not save config", e);
        }
    }
}
