package com.github.hahahha.WorldGen.world.structure.config;

import com.github.hahahha.WorldGen.WorldGen;
import com.github.hahahha.WorldGen.world.structure.api.StructureWorldgenApi;
import com.github.hahahha.WorldGen.world.structure.api.StructureWorldgenConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import moddedmite.rustedironcore.api.event.events.BiomeDecorationRegisterEvent;
import moddedmite.rustedironcore.api.world.Dimension;
import net.minecraft.BiomeGenBase;

/**
 * Loads structure worldgen entries from a player-facing config file.
 *
 * <p>The config format is JSONC (JSON + comments). We parse it using a lenient Gson reader so players can keep
 * comments in the file.
 */
public final class StructureWorldgenPlayerConfigLoader {
    public static final String DEFAULT_FILE_NAME = "worldgen-structures.jsonc";
    public static final String DEFAULT_SCHEMATICS_DIR_NAME = "schematics";

    private static final String ROOT_ENABLED_KEY = "enabled";
    private static final String ROOT_SCHEMATICS_DIR_KEY = "schematicsDir";
    private static final String STRUCTURES_KEY = "structures";
    private static final String ENTRY_ENABLED_KEY = "enabled";
    private static final String SCHEMATIC_PATH_KEY = "schematicPath";
    private static final String STRUCTURE_NAME_KEY = "name";
    private static final String DIMENSION_KEY = "dimension";
    private static final String WEIGHT_KEY = "weight";
    private static final String CHANCE_KEY = "chance";
    private static final String ATTEMPTS_KEY = "attempts";
    private static final String SURFACE_KEY = "surface";
    private static final String MIN_Y_KEY = "minY";
    private static final String MAX_Y_KEY = "maxY";
    private static final String Y_OFFSET_KEY = "yOffset";
    private static final String CENTER_ON_ANCHOR_KEY = "centerOnAnchor";
    private static final String MIN_DISTANCE_KEY = "minDistance";
    private static final String DISTANCE_SCOPE_KEY = "distanceScope";
    private static final String BIOME_MODE_KEY = "biomeMode";
    private static final String BIOME_IDS_KEY = "biomeIds";
    private static final String BIOME_NAMES_KEY = "biomeNames";

    private static final String DEFAULT_CONFIG_TEMPLATE = """
            // WorldGen player config (JSONC: comments are allowed)
            // WorldGen 玩家配置（JSONC，支持注释）
            //
            // Usage / 用法:
            // 1) Edit objects in "structures" or duplicate them.
            //    编辑或复制 "structures" 里的条目。
            //
            // 2) "schematicPath" supports / 支持:
            //    - Classpath resource / 资源路径: "/assets/<modid>/structures/<name>.schematic"
            //    - Absolute file path / 绝对路径: "F:/your/path/<name>.schematic"
            //    - Relative file path / 相对路径: resolved from "schematicsDir"
            //      (default config-sibling "schematics" / 默认是与 config 同级的 "schematics")
            //
            // 3) If a schematic file does not exist, that entry is skipped automatically.
            //    如果 schematic 文件不存在，该条目会自动跳过，不会加载。
            //
            // 4) chance = 40 means about 1/40 probability per generation check.
            //    chance = 40 表示每次生成检查约 1/40 概率。
            {
              "enabled": true,
              "schematicsDir": "schematics",
              "structures": [
                {
                  "enabled": true,
                  "name": "test",
                  "schematicPath": "test.schematic",
                  "dimension": "overworld",
                  "weight": 1,
                  "chance": 40,
                  "attempts": 1,
                  "surface": true,
                  "minY": 0,
                  "maxY": 255,
                  "yOffset": 0,
                  "centerOnAnchor": true,
                  "minDistance": 0,
                  "distanceScope": "all",
                  "biomeMode": "whitelist",
                  "biomeIds": [],
                  "biomeNames": []
                },
                {
                  "enabled": true,
                  "name": "test_nether",
                  "schematicPath": "test_nether.schematic",
                  "dimension": "the_nether",
                  "weight": 1,
                  "chance": 40,
                  "attempts": 1,
                  "surface": true,
                  "minY": 0,
                  "maxY": 255,
                  "yOffset": 0,
                  "centerOnAnchor": true,
                  "minDistance": 0,
                  "distanceScope": "all",
                  "biomeMode": "whitelist",
                  "biomeIds": [],
                  "biomeNames": []
                }
              ]
            }
            """;

    private StructureWorldgenPlayerConfigLoader() {
    }

    public static int registerFromDefaultConfig(BiomeDecorationRegisterEvent event) {
        return registerFromFile(event, new File("config", DEFAULT_FILE_NAME));
    }

    public static List<String> listConfiguredStructureNames(int maxEntries) {
        return listConfiguredStructureNames(new File("config", DEFAULT_FILE_NAME), maxEntries, null);
    }

    public static List<String> listConfiguredStructureNames(File configFile, int maxEntries) {
        return listConfiguredStructureNames(configFile, maxEntries, null);
    }

    public static List<String> listConfiguredStructureNames(int maxEntries, Integer dimensionId) {
        return listConfiguredStructureNames(new File("config", DEFAULT_FILE_NAME), maxEntries, dimensionId);
    }

    public static List<String> listConfiguredStructureNames(File configFile, int maxEntries, Integer dimensionId) {
        if (maxEntries < 1 || configFile == null) {
            return java.util.Collections.emptyList();
        }

        File absoluteConfigFile = configFile.getAbsoluteFile();
        ensureConfigFileExists(absoluteConfigFile);
        JsonObject root = readRootObject(absoluteConfigFile);
        if (root == null) {
            return java.util.Collections.emptyList();
        }

        JsonArray structures = getArray(root, STRUCTURES_KEY);
        if (structures == null || structures.size() == 0) {
            return java.util.Collections.emptyList();
        }

        File schematicsBaseDir = resolveSchematicsBaseDir(
                getString(root, ROOT_SCHEMATICS_DIR_KEY, DEFAULT_SCHEMATICS_DIR_NAME),
                absoluteConfigFile);
        LinkedHashSet<String> names = new LinkedHashSet<String>();

        for (int i = 0; i < structures.size(); ++i) {
            JsonElement entryElement = structures.get(i);
            if (!entryElement.isJsonObject()) {
                continue;
            }
            JsonObject entry = entryElement.getAsJsonObject();
            if (!getBoolean(entry, ENTRY_ENABLED_KEY, true)) {
                continue;
            }
            if (dimensionId != null) {
                String dimensionName = getString(entry, DIMENSION_KEY, "overworld");
                Dimension entryDimension = parseDimension(dimensionName);
                if (entryDimension == null || entryDimension.id() != dimensionId.intValue()) {
                    continue;
                }
            }

            String name = getString(entry, STRUCTURE_NAME_KEY, null);
            if (name == null) {
                String rawSchematicPath = getString(entry, SCHEMATIC_PATH_KEY, null);
                if (rawSchematicPath != null) {
                    String resolvedPath = resolveSchematicPath(rawSchematicPath.trim(), schematicsBaseDir);
                    String normalizedPath = normalizePath(resolvedPath);
                    if (normalizedPath != null) {
                        name = extractStructureName(normalizedPath);
                    }
                }
            }
            if (name == null) {
                continue;
            }

            String trimmed = name.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            names.add(trimmed);
            if (names.size() >= maxEntries) {
                break;
            }
        }

        if (names.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return new ArrayList<String>(names);
    }

    public static int registerFromFile(BiomeDecorationRegisterEvent event, File configFile) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(configFile, "configFile");

        File absoluteConfigFile = configFile.getAbsoluteFile();
        ensureConfigFileExists(absoluteConfigFile);

        JsonObject root = readRootObject(absoluteConfigFile);
        if (root == null) {
            return 0;
        }

        if (!getBoolean(root, ROOT_ENABLED_KEY, true)) {
            WorldGen.LOGGER.info("Structure worldgen is disabled by config: {}", absoluteConfigFile.getPath());
            return 0;
        }

        File schematicsBaseDir = resolveSchematicsBaseDir(
                getString(root, ROOT_SCHEMATICS_DIR_KEY, DEFAULT_SCHEMATICS_DIR_NAME),
                absoluteConfigFile);

        JsonArray structures = getArray(root, STRUCTURES_KEY);
        if (structures == null || structures.size() == 0) {
            WorldGen.LOGGER.info("No structure entries found in config: {}", absoluteConfigFile.getPath());
            return 0;
        }

        int registered = 0;
        for (int i = 0; i < structures.size(); ++i) {
            JsonElement entryElement = structures.get(i);
            if (!entryElement.isJsonObject()) {
                WorldGen.LOGGER.warn("Skip config entry[{}]: expected JSON object", i);
                continue;
            }

            JsonObject entry = entryElement.getAsJsonObject();
            if (!getBoolean(entry, ENTRY_ENABLED_KEY, true)) {
                continue;
            }

            String rawSchematicPath = getString(entry, SCHEMATIC_PATH_KEY, null);
            if (rawSchematicPath == null) {
                WorldGen.LOGGER.warn("Skip config entry[{}]: missing '{}'", i, SCHEMATIC_PATH_KEY);
                continue;
            }

            String schematicPath = resolveSchematicPath(rawSchematicPath.trim(), schematicsBaseDir);
            if (!isSchematicAvailable(schematicPath)) {
                WorldGen.LOGGER.warn(
                        "Skip config entry[{}]: schematic file/resource not found '{}'",
                        i,
                        schematicPath);
                continue;
            }
            String structureName = getString(entry, STRUCTURE_NAME_KEY, null);
            String dimensionName = getString(entry, DIMENSION_KEY, "overworld");
            Dimension dimension = parseDimension(dimensionName);
            if (dimension == null) {
                WorldGen.LOGGER.warn("Skip config entry[{}]: unknown dimension '{}'", i, dimensionName);
                continue;
            }

            int weight = getInt(entry, WEIGHT_KEY, 1);
            int chance = getInt(entry, CHANCE_KEY, 40);
            int attempts = getInt(entry, ATTEMPTS_KEY, 1);
            boolean surface = getBoolean(entry, SURFACE_KEY, true);
            int minY = getInt(entry, MIN_Y_KEY, StructureWorldgenConfig.MIN_WORLD_Y);
            int maxY = getInt(entry, MAX_Y_KEY, StructureWorldgenConfig.MAX_WORLD_Y);
            int yOffset = getInt(entry, Y_OFFSET_KEY, 0);
            boolean centerOnAnchor = getBoolean(entry, CENTER_ON_ANCHOR_KEY, true);
            int minDistance = getInt(entry, MIN_DISTANCE_KEY, 0);
            StructureWorldgenConfig.DistanceScope distanceScope = parseDistanceScope(
                    getString(entry, DISTANCE_SCOPE_KEY, "all"),
                    i);
            Predicate<BiomeGenBase> biomeFilter = parseBiomeFilter(entry, i);

            try {
                StructureWorldgenConfig.Builder builder = StructureWorldgenApi.builder(schematicPath)
                        .structureName(structureName)
                        .dimension(dimension)
                        .weight(weight)
                        .chance(chance)
                        .attempts(attempts)
                        .surface(surface)
                        .yRange(minY, maxY)
                        .yOffset(yOffset)
                        .centerOnAnchor(centerOnAnchor)
                        .minDistance(minDistance)
                        .distanceScope(distanceScope);
                if (biomeFilter != null) {
                    builder.biomeFilter(biomeFilter);
                }
                StructureWorldgenConfig config = builder.build();
                StructureWorldgenApi.register(event, config);
                registered++;
            } catch (IllegalArgumentException e) {
                WorldGen.LOGGER.warn("Skip config entry[{}]: {}", i, e.getMessage());
            }
        }

        WorldGen.LOGGER.info(
                "Loaded {} structure entries from player config: {}",
                registered,
                absoluteConfigFile.getPath());
        return registered;
    }

    private static void ensureConfigFileExists(File configFile) {
        File parent = configFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            WorldGen.LOGGER.warn("Failed to create config directory: {}", parent.getPath());
        }
        ensureSchematicsDirectoryExists(configFile);

        if (configFile.isFile()) {
            return;
        }

        try {
            Files.writeString(configFile.toPath(), DEFAULT_CONFIG_TEMPLATE, StandardCharsets.UTF_8);
            WorldGen.LOGGER.info("Generated player config template: {}", configFile.getPath());
        } catch (IOException e) {
            WorldGen.LOGGER.error("Failed to create player config template: {}", configFile.getPath(), e);
        }
    }

    private static void ensureSchematicsDirectoryExists(File configFile) {
        File schematicsDir = resolveSchematicsBaseDir(DEFAULT_SCHEMATICS_DIR_NAME, configFile);
        if (schematicsDir.exists()) {
            return;
        }
        if (!schematicsDir.mkdirs()) {
            WorldGen.LOGGER.warn("Failed to create schematics directory: {}", schematicsDir.getPath());
        }
    }

    private static JsonObject readRootObject(File configFile) {
        if (!configFile.isFile()) {
            WorldGen.LOGGER.warn("Config file does not exist: {}", configFile.getPath());
            return null;
        }

        try (Reader reader = Files.newBufferedReader(configFile.toPath(), StandardCharsets.UTF_8)) {
            JsonReader jsonReader = new JsonReader(reader);
            jsonReader.setLenient(true);
            JsonElement rootElement = new JsonParser().parse(jsonReader);
            if (rootElement == null || rootElement.isJsonNull()) {
                WorldGen.LOGGER.warn("Config file is empty: {}", configFile.getPath());
                return null;
            }
            if (!rootElement.isJsonObject()) {
                WorldGen.LOGGER.warn("Config root must be a JSON object: {}", configFile.getPath());
                return null;
            }
            return rootElement.getAsJsonObject();
        } catch (Exception e) {
            WorldGen.LOGGER.error("Failed to parse player config: {}", configFile.getPath(), e);
            return null;
        }
    }

    private static JsonArray getArray(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static String getString(JsonObject object, String key, String defaultValue) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return defaultValue;
        }
        try {
            String value = element.getAsString();
            if (value == null) {
                return defaultValue;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? defaultValue : trimmed;
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private static int getInt(JsonObject object, String key, int defaultValue) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return defaultValue;
        }
        try {
            return element.getAsInt();
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private static boolean getBoolean(JsonObject object, String key, boolean defaultValue) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return defaultValue;
        }
        try {
            return element.getAsBoolean();
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private static Predicate<BiomeGenBase> parseBiomeFilter(JsonObject entry, int entryIndex) {
        Set<Integer> biomeIds = getIntSet(entry, BIOME_IDS_KEY);
        Set<String> biomeNames = getNormalizedStringSet(entry, BIOME_NAMES_KEY);
        if (biomeIds.isEmpty() && biomeNames.isEmpty()) {
            return null;
        }

        String modeValue = getString(entry, BIOME_MODE_KEY, "whitelist");
        String normalizedMode = modeValue == null ? "whitelist" : modeValue.trim().toLowerCase(Locale.ROOT);
        final boolean whitelist;
        if ("blacklist".equals(normalizedMode) || "exclude".equals(normalizedMode) || "deny".equals(normalizedMode)) {
            whitelist = false;
        } else if (
                "whitelist".equals(normalizedMode)
                        || "include".equals(normalizedMode)
                        || "allow".equals(normalizedMode)) {
            whitelist = true;
        } else {
            WorldGen.LOGGER.warn(
                    "Unknown biomeMode '{}' in config entry[{}], fallback to whitelist",
                    modeValue,
                    entryIndex);
            whitelist = true;
        }

        return biome -> {
            if (biome == null) {
                return !whitelist;
            }
            boolean matched = biomeIds.contains(biome.biomeID)
                    || biomeNames.contains(normalizeBiomeName(biome.biomeName));
            return whitelist ? matched : !matched;
        };
    }

    private static Set<Integer> getIntSet(JsonObject object, String key) {
        JsonArray array = getArray(object, key);
        if (array == null || array.size() == 0) {
            return java.util.Collections.emptySet();
        }

        Set<Integer> values = new HashSet<Integer>();
        for (int i = 0; i < array.size(); ++i) {
            JsonElement element = array.get(i);
            if (element == null || element.isJsonNull()) {
                continue;
            }
            try {
                values.add(element.getAsInt());
            } catch (Exception ignored) {
            }
        }
        return values;
    }

    private static Set<String> getNormalizedStringSet(JsonObject object, String key) {
        JsonArray array = getArray(object, key);
        if (array == null || array.size() == 0) {
            return java.util.Collections.emptySet();
        }

        Set<String> values = new HashSet<String>();
        for (int i = 0; i < array.size(); ++i) {
            JsonElement element = array.get(i);
            if (element == null || element.isJsonNull()) {
                continue;
            }
            try {
                String normalized = normalizeBiomeName(element.getAsString());
                if (normalized != null) {
                    values.add(normalized);
                }
            } catch (Exception ignored) {
            }
        }
        return values;
    }

    private static String normalizeBiomeName(String biomeName) {
        if (biomeName == null) {
            return null;
        }
        String trimmed = biomeName.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static Dimension parseDimension(String value) {
        if (value == null || value.isEmpty()) {
            return Dimension.OVERWORLD;
        }
        String normalized = value.trim();
        Dimension dimension = Dimension.fromString(normalized);
        if (dimension != null) {
            return dimension;
        }
        dimension = Dimension.fromString(normalized.toLowerCase(Locale.ROOT));
        if (dimension != null) {
            return dimension;
        }
        return Dimension.fromString(normalized.toUpperCase(Locale.ROOT));
    }

    private static StructureWorldgenConfig.DistanceScope parseDistanceScope(String value, int entryIndex) {
        String normalized = value == null ? "all" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || "all".equals(normalized) || "all_structures".equals(normalized)) {
            return StructureWorldgenConfig.DistanceScope.ALL;
        }
        if ("same_name".equals(normalized)
                || "same".equals(normalized)
                || "same_structure".equals(normalized)) {
            return StructureWorldgenConfig.DistanceScope.SAME_NAME;
        }

        WorldGen.LOGGER.warn(
                "Unknown distanceScope '{}' in config entry[{}], fallback to 'all'",
                value,
                entryIndex);
        return StructureWorldgenConfig.DistanceScope.ALL;
    }

    private static File resolveSchematicsBaseDir(String configuredDir, File configFile) {
        String normalizedDir = configuredDir == null ? "" : configuredDir.trim();
        if (normalizedDir.isEmpty()) {
            normalizedDir = DEFAULT_SCHEMATICS_DIR_NAME;
        }

        File baseDir = new File(normalizedDir);
        if (baseDir.isAbsolute()) {
            return baseDir;
        }

        File configDir = configFile.getParentFile();
        if (configDir != null && "config".equalsIgnoreCase(configDir.getName())) {
            // Default behavior: make schematics directory sibling to config directory.
            File runDir = configDir.getParentFile();
            if (runDir != null) {
                return new File(runDir, normalizedDir);
            }
        }
        if (configDir != null) {
            return new File(configDir, normalizedDir);
        }
        return baseDir;
    }

    private static String normalizePath(String path) {
        if (path == null) {
            return null;
        }
        String normalized = path.trim().replace('\\', '/');
        return normalized.isEmpty() ? null : normalized;
    }

    private static String extractStructureName(String normalizedPath) {
        if (normalizedPath == null || normalizedPath.isEmpty()) {
            return "unknown";
        }

        String name = normalizedPath;
        int slash = name.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < name.length()) {
            name = name.substring(slash + 1);
        }
        if (name.toLowerCase(Locale.ROOT).endsWith(".schematic")) {
            name = name.substring(0, name.length() - ".schematic".length());
        }
        return name.isEmpty() ? "unknown" : name;
    }

    private static String resolveSchematicPath(String path, File schematicsBaseDir) {
        if (path.startsWith("classpath:")) {
            String classpathPath = path.substring("classpath:".length()).trim();
            if (classpathPath.startsWith("/")) {
                return classpathPath;
            }
            return "/" + classpathPath;
        }
        if (path.startsWith("/")) {
            // Classpath paths are expected to start with '/'.
            return path;
        }

        File file = new File(path);
        if (file.isAbsolute()) {
            return file.getPath();
        }

        return schematicsBaseDir == null ? file.getPath() : new File(schematicsBaseDir, path).getPath();
    }

    private static boolean isSchematicAvailable(String resolvedPath) {
        String normalized = normalizePath(resolvedPath);
        if (normalized == null) {
            return false;
        }

        if (normalized.startsWith("/")) {
            return StructureWorldgenPlayerConfigLoader.class.getResource(normalized) != null;
        }
        return new File(normalized).isFile();
    }
}
