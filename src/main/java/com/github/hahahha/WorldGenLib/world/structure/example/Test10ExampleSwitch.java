package com.github.hahahha.WorldGenLib.world.structure.example;

import com.github.hahahha.WorldGenLib.WorldGenLib;
import com.github.hahahha.WorldGenLib.world.structure.config.StructureWorldGenLibPlayerConfigLoader;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Config switch reader for the test10 API example structure.
 */
public final class Test10ExampleSwitch {
    private static final String ROOT_EXAMPLES_KEY = "examples";
    private static final String EXAMPLES_TEST10_KEY = "test10";
    private static final String EXAMPLE_ENABLED_KEY = "enabled";
    private static final String LEGACY_TEST10_ENABLED_KEY = "test10Enabled";

    public static final String CONFIG_KEY_PATH = "examples.test10.enabled";

    private static final File DEFAULT_CONFIG_FILE =
            new File("config", StructureWorldGenLibPlayerConfigLoader.DEFAULT_FILE_NAME);
    private static final JsonParser JSON_PARSER = new JsonParser();

    private static volatile Boolean cachedEnabled;
    private static volatile long cachedConfigLastModified = Long.MIN_VALUE;

    private Test10ExampleSwitch() {
    }

    public static boolean isEnabled() {
        File configFile = configFile();
        long currentLastModified = lastModified(configFile);
        Boolean cached = cachedEnabled;
        if (cached != null && currentLastModified == cachedConfigLastModified) {
            return cached.booleanValue();
        }

        synchronized (Test10ExampleSwitch.class) {
            long latestLastModified = lastModified(configFile);
            Boolean latestCached = cachedEnabled;
            if (latestCached != null && latestLastModified == cachedConfigLastModified) {
                return latestCached.booleanValue();
            }

            boolean enabled = resolveEnabled(configFile);
            cachedEnabled = enabled;
            cachedConfigLastModified = latestLastModified;
            return enabled;
        }
    }

    public static File configFile() {
        return DEFAULT_CONFIG_FILE;
    }

    private static boolean resolveEnabled(File configFile) {
        if (configFile == null || !configFile.isFile()) {
            return false;
        }

        JsonObject root = readRootObject(configFile);
        if (root == null) {
            return false;
        }

        boolean legacyRootEnabled = getBoolean(root, LEGACY_TEST10_ENABLED_KEY, false);
        JsonObject examples = getObject(root, ROOT_EXAMPLES_KEY);
        if (examples == null) {
            return legacyRootEnabled;
        }

        boolean legacyExamplesEnabled = getBoolean(examples, LEGACY_TEST10_ENABLED_KEY, legacyRootEnabled);
        JsonObject test10 = getObject(examples, EXAMPLES_TEST10_KEY);
        if (test10 == null) {
            return legacyExamplesEnabled;
        }
        return getBoolean(test10, EXAMPLE_ENABLED_KEY, legacyExamplesEnabled);
    }

    private static JsonObject readRootObject(File configFile) {
        try (Reader reader = Files.newBufferedReader(configFile.toPath(), StandardCharsets.UTF_8)) {
            JsonReader jsonReader = new JsonReader(reader);
            jsonReader.setLenient(true);
            JsonElement rootElement;
            synchronized (JSON_PARSER) {
                rootElement = JSON_PARSER.parse(jsonReader);
            }
            if (rootElement == null || !rootElement.isJsonObject()) {
                return null;
            }
            return rootElement.getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            WorldGenLib.LOGGER.warn(
                    "Failed to read example switch from config {}, keep test10 disabled",
                    configFile.getPath(),
                    e);
            return null;
        }
    }

    private static JsonObject getObject(JsonObject object, String key) {
        if (object == null || key == null || key.isEmpty() || !object.has(key)) {
            return null;
        }
        JsonElement element = object.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static boolean getBoolean(JsonObject object, String key, boolean defaultValue) {
        if (object == null || key == null || key.isEmpty() || !object.has(key)) {
            return defaultValue;
        }
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return defaultValue;
        }
        try {
            return element.getAsBoolean();
        } catch (RuntimeException ignored) {
            try {
                String value = element.getAsString();
                if ("true".equalsIgnoreCase(value)) {
                    return true;
                }
                if ("false".equalsIgnoreCase(value)) {
                    return false;
                }
            } catch (RuntimeException ignoredToo) {
            }
            return defaultValue;
        }
    }

    private static long lastModified(File configFile) {
        return configFile != null && configFile.isFile() ? configFile.lastModified() : -1L;
    }
}
