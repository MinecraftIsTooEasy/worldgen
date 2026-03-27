package com.github.hahahha.WorldGen.world.structure.config;

import com.github.hahahha.WorldGen.WorldGen;
import com.github.hahahha.WorldGen.world.structure.StructureLootProfile;
import com.github.hahahha.WorldGen.world.structure.StructureLootProfiles;
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
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import moddedmite.rustedironcore.api.event.events.BiomeDecorationRegisterEvent;
import moddedmite.rustedironcore.api.world.Dimension;
import net.minecraft.BiomeGenBase;
import net.minecraft.Item;
import net.minecraft.WeightedRandomChestContent;
import net.xiaoyu233.fml.api.INamespaced;

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
    private static final String ROOT_LOOT_TABLE_ENABLED_KEY = "lootTableEnabled";
    private static final String ROOT_LOOT_ENABLED_ALIAS_KEY = "lootEnabled";
    private static final String ROOT_LOOT_KEY = "loot";
    private static final String ROOT_LOOT_ENABLED_CHILD_KEY = "enabled";
    private static final String ROOT_LOOT_PROFILES_CHILD_KEY = "profiles";
    private static final String ROOT_LOOT_PROFILES_KEY = "lootProfiles";
    private static final String STRUCTURES_KEY = "structures";
    private static final String ENTRY_ENABLED_KEY = "enabled";
    private static final String ENTRY_LOOT_KEY = "loot";
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
    private static final String LOOT_TABLE_ENABLED_KEY = "lootTableEnabled";
    private static final String LOOT_ENABLED_ALIAS_KEY = "lootEnabled";
    private static final String LOOT_ENABLED_KEY = "enabled";
    private static final String LOOT_PROFILE_KEY = "lootProfile";
    private static final String LOOT_PROFILE_ALIAS_KEY = "profile";
    private static final String BIOME_MODE_KEY = "biomeMode";
    private static final String BIOME_IDS_KEY = "biomeIds";
    private static final String BIOME_NAMES_KEY = "biomeNames";

    private static final String LOOT_PROFILE_ID_KEY = "id";
    private static final String LOOT_PROFILE_MARKERS_KEY = "markers";
    private static final String LOOT_PROFILE_LEVELS_KEY = "levels";
    private static final String LOOT_MARKER_ITEM_KEY = "item";
    private static final String LOOT_MARKER_ITEM_ID_KEY = "itemId";
    private static final String LOOT_MARKER_LEVEL_KEY = "level";
    private static final String LOOT_LEVEL_MIN_ROLLS_KEY = "minRolls";
    private static final String LOOT_LEVEL_MAX_ROLLS_KEY = "maxRolls";
    private static final String LOOT_LEVEL_ENTRIES_KEY = "entries";
    private static final String LOOT_LEVEL_ARTIFACT_CHANCES_KEY = "artifactChances";
    private static final String LOOT_ENTRY_ITEM_KEY = "item";
    private static final String LOOT_ENTRY_ITEM_ID_KEY = "itemId";
    private static final String LOOT_ENTRY_META_KEY = "meta";
    private static final String LOOT_ENTRY_MIN_KEY = "min";
    private static final String LOOT_ENTRY_MAX_KEY = "max";
    private static final String LOOT_ENTRY_WEIGHT_KEY = "weight";

    private static Map<String, Integer> cachedItemAliasToId;

    private static final String DEFAULT_CONFIG_TEMPLATE = """
            // WorldGen player config (JSONC: comments are allowed)
            //
            // Path rules:
            // - schematicPath supports:
            //   1) classpath path: "/assets/<modid>/structures/<name>.schematic"
            //   2) absolute path: "F:/your/path/<name>.schematic"
            //   3) relative path: resolved from "schematicsDir"
            //
            // Marker chest loot guide:
            // - A chest in schematic triggers loot replacement only when it contains exactly ONE marker item.
            // - Root loot switch: loot.enabled (legacy alias: lootTableEnabled / lootEnabled).
            // - Root loot profiles: loot.profiles (legacy alias: lootProfiles).
            // - Structure loot switch: structure.loot.enabled (legacy alias: lootTableEnabled / lootEnabled).
            // - Structure loot profile: structure.loot.profile (legacy alias: lootProfile).
            // - markers[] item:
            //   { "item": "minecraft:stick", "level": 1 }   // preferred
            //   { "itemId": 280, "level": 1 }               // backward compatible
            // - levels[] item:
            //   {
            //     "level": 1,
            //     "minRolls": 2,
            //     "maxRolls": 4,
            //     "entries": [
            //       { "item": "minecraft:coal", "meta": 0, "min": 1, "max": 3, "weight": 20 }
            //     ]
            //   }
            //
            // Note:
            // - chance = 40 means about 1/40 probability per generation check.
            // - Unknown lootProfile id falls back to "default".
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
                  "biomeNames": [],
                  "loot": {
                    "enabled": true,
                    "profile": "default"
                  }
                }
              ],
              "loot": {
                "enabled": true,
                "profiles": [
                  {
                    "id": "default",
                    "markers": [
                      { "item": "stick", "level": 1 },
                      { "item": "flint", "level": 2 },
                      { "item": "coal", "level": 3 },
                      { "item": "ingotIron", "level": 4 },
                      { "item": "ingotGold", "level": 5 },
                      { "item": "diamond", "level": 6 }
                    ],
                    "levels": [
                      {
                        "level": 1,
                        "minRolls": 3,
                        "maxRolls": 5,
                        "entries": [
                          { "item": "stick", "meta": 0, "min": 1, "max": 1, "weight": 25 }
                        ]
                      },
                      {
                        "level": 6,
                        "minRolls": 6,
                        "maxRolls": 9,
                        "entries": [
                          { "item": "diamond", "meta": 0, "min": 1, "max": 1, "weight": 16 }
                        ]
                      }
                    ]
                  }
                ]
              }
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

        boolean rootLootTableEnabled = resolveRootLootTableEnabled(root);
        Map<String, StructureLootProfile> lootProfiles = parseLootProfiles(root);
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
            boolean lootTableEnabled = resolveEntryLootTableEnabled(entry, rootLootTableEnabled);
            StructureWorldgenConfig.DistanceScope distanceScope = parseDistanceScope(
                    getString(entry, DISTANCE_SCOPE_KEY, "all"),
                    i);
            String lootProfileId = resolveEntryLootProfileId(entry);
            StructureLootProfile lootProfile = lootProfiles.get(lootProfileId);
            if (lootProfile == null) {
                WorldGen.LOGGER.warn(
                        "Unknown lootProfile '{}' in config entry[{}], fallback to '{}'",
                        lootProfileId,
                        i,
                        StructureLootProfiles.DEFAULT_PROFILE_ID);
                lootProfile = StructureLootProfiles.defaultProfile();
            }
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
                        .distanceScope(distanceScope)
                        .lootTableEnabled(lootTableEnabled)
                        .lootProfile(lootProfile);
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
        if (object == null) {
            return null;
        }
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static JsonObject getObject(JsonObject object, String key) {
        if (object == null) {
            return null;
        }
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return element.isJsonObject() ? element.getAsJsonObject() : null;
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

    private static boolean getBooleanWithAlias(
            JsonObject object,
            String primaryKey,
            String aliasKey,
            boolean defaultValue) {
        if (object == null) {
            return defaultValue;
        }
        JsonElement primary = object.get(primaryKey);
        if (primary != null && !primary.isJsonNull()) {
            try {
                return primary.getAsBoolean();
            } catch (Exception ignored) {
            }
        }

        JsonElement alias = object.get(aliasKey);
        if (alias != null && !alias.isJsonNull()) {
            try {
                return alias.getAsBoolean();
            } catch (Exception ignored) {
            }
        }

        return defaultValue;
    }

    private static boolean resolveLootEnabledFromObject(JsonObject lootObject, boolean defaultValue) {
        if (lootObject == null) {
            return defaultValue;
        }
        return getBooleanWithAlias(
                lootObject,
                LOOT_TABLE_ENABLED_KEY,
                LOOT_ENABLED_ALIAS_KEY,
                getBoolean(lootObject, LOOT_ENABLED_KEY, defaultValue));
    }

    private static boolean resolveRootLootTableEnabled(JsonObject root) {
        boolean legacyEnabled = getBooleanWithAlias(
                root,
                ROOT_LOOT_TABLE_ENABLED_KEY,
                ROOT_LOOT_ENABLED_ALIAS_KEY,
                true);
        JsonObject lootObject = getObject(root, ROOT_LOOT_KEY);
        if (lootObject == null) {
            return legacyEnabled;
        }
        return getBooleanWithAlias(
                lootObject,
                ROOT_LOOT_TABLE_ENABLED_KEY,
                ROOT_LOOT_ENABLED_ALIAS_KEY,
                getBoolean(lootObject, ROOT_LOOT_ENABLED_CHILD_KEY, legacyEnabled));
    }

    private static boolean resolveEntryLootTableEnabled(JsonObject entry, boolean rootDefault) {
        boolean legacyEnabled = getBooleanWithAlias(
                entry,
                LOOT_TABLE_ENABLED_KEY,
                LOOT_ENABLED_ALIAS_KEY,
                rootDefault);
        JsonObject entryLoot = getObject(entry, ENTRY_LOOT_KEY);
        return resolveLootEnabledFromObject(entryLoot, legacyEnabled);
    }

    private static String resolveEntryLootProfileId(JsonObject entry) {
        String profileId = getString(entry, LOOT_PROFILE_KEY, StructureLootProfiles.DEFAULT_PROFILE_ID);
        JsonObject entryLoot = getObject(entry, ENTRY_LOOT_KEY);
        if (entryLoot != null) {
            profileId = getString(
                    entryLoot,
                    LOOT_PROFILE_KEY,
                    getString(entryLoot, LOOT_PROFILE_ALIAS_KEY, profileId));
        }
        return normalizeLootProfileId(profileId);
    }

    private static JsonArray resolveRootLootProfileArray(JsonObject root) {
        JsonObject lootObject = getObject(root, ROOT_LOOT_KEY);
        if (lootObject != null) {
            JsonArray profiles = getArray(lootObject, ROOT_LOOT_PROFILES_CHILD_KEY);
            if (profiles != null) {
                return profiles;
            }
            profiles = getArray(lootObject, ROOT_LOOT_PROFILES_KEY);
            if (profiles != null) {
                return profiles;
            }
        }
        return getArray(root, ROOT_LOOT_PROFILES_KEY);
    }

    private static int resolveItemId(
            JsonObject object,
            String nameKey,
            String legacyIdKey,
            int defaultValue) {
        if (object == null) {
            return defaultValue;
        }

        JsonElement byName = object.get(nameKey);
        if (byName != null && !byName.isJsonNull()) {
            Integer resolved = resolveItemIdElement(byName);
            if (resolved != null && resolved.intValue() > 0) {
                return resolved.intValue();
            }
        }

        JsonElement byId = object.get(legacyIdKey);
        if (byId != null && !byId.isJsonNull()) {
            Integer resolved = resolveItemIdElement(byId);
            if (resolved != null && resolved.intValue() > 0) {
                return resolved.intValue();
            }
        }

        return defaultValue;
    }

    private static Integer resolveItemIdElement(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        try {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                int id = element.getAsInt();
                return Item.getItem(id) != null ? Integer.valueOf(id) : null;
            }
        } catch (Exception ignored) {
        }

        try {
            String raw = element.getAsString();
            if (raw == null) {
                return null;
            }
            String normalized = raw.trim();
            if (normalized.isEmpty()) {
                return null;
            }

            try {
                int numeric = Integer.parseInt(normalized);
                return Item.getItem(numeric) != null ? Integer.valueOf(numeric) : null;
            } catch (NumberFormatException ignored) {
            }

            return resolveItemIdByAlias(normalized);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Integer resolveItemIdByAlias(String token) {
        String normalized = normalizeAlias(token);
        if (normalized == null) {
            return null;
        }
        return getItemAliasToId().get(normalized);
    }

    private static synchronized Map<String, Integer> getItemAliasToId() {
        if (cachedItemAliasToId != null) {
            return cachedItemAliasToId;
        }

        Map<String, Integer> aliases = new HashMap<String, Integer>();
        Item[] items = Item.itemsList;
        if (items != null) {
            for (Item item : items) {
                if (item == null) {
                    continue;
                }
                addItemAliases(aliases, item, item.itemID);
            }
        }

        for (Field field : Item.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !Item.class.isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                Object value = field.get(null);
                if (!(value instanceof Item)) {
                    continue;
                }
                Item item = (Item) value;
                if (item == null) {
                    continue;
                }
                addAlias(aliases, field.getName(), item.itemID);
                addAlias(aliases, "minecraft:" + field.getName(), item.itemID);
            } catch (Exception ignored) {
            }
        }

        cachedItemAliasToId = aliases;
        return cachedItemAliasToId;
    }

    private static void addItemAliases(Map<String, Integer> aliases, Item item, int itemId) {
        if (item == null || itemId <= 0) {
            return;
        }
        addAlias(aliases, String.valueOf(itemId), itemId);

        String unlocalized = null;
        try {
            unlocalized = item.getUnlocalizedName();
        } catch (Exception ignored) {
        }
        if (unlocalized != null) {
            addAlias(aliases, unlocalized, itemId);
            if (unlocalized.startsWith("item.")) {
                String stripped = unlocalized.substring("item.".length());
                addAlias(aliases, stripped, itemId);
                addAlias(aliases, "minecraft:" + stripped, itemId);
            }
        }

        String refName = null;
        try {
            refName = item.getNameForReferenceFile();
        } catch (Exception ignored) {
        }
        if (refName != null) {
            addAlias(aliases, refName, itemId);
            addAlias(aliases, "minecraft:" + refName, itemId);
        }

        if (item instanceof INamespaced) {
            try {
                INamespaced namespaced = (INamespaced) item;
                String namespace = namespaced.getNamespace();
                String normalizedNs = namespace == null ? null : namespace.trim().toLowerCase(Locale.ROOT);
                if (normalizedNs != null && !normalizedNs.isEmpty()) {
                    if (unlocalized != null && unlocalized.startsWith("item.")) {
                        addAlias(aliases, normalizedNs + ":" + unlocalized.substring("item.".length()), itemId);
                    }
                    if (refName != null) {
                        addAlias(aliases, normalizedNs + ":" + refName, itemId);
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void addAlias(Map<String, Integer> aliases, String alias, int itemId) {
        String normalized = normalizeAlias(alias);
        if (normalized == null || aliases.containsKey(normalized)) {
            return;
        }
        aliases.put(normalized, Integer.valueOf(itemId));
    }

    private static String normalizeAlias(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        int colon = normalized.indexOf(':');
        if (colon >= 0 && colon + 1 < normalized.length()) {
            String namespace = normalized.substring(0, colon);
            String path = normalized.substring(colon + 1);
            if (path.startsWith("item.")) {
                path = path.substring("item.".length());
            }
            return path.isEmpty() ? namespace : namespace + ":" + path;
        }
        if (normalized.startsWith("item.")) {
            String stripped = normalized.substring("item.".length());
            return stripped.isEmpty() ? normalized : stripped;
        }
        return normalized;
    }

    private static Map<String, StructureLootProfile> parseLootProfiles(JsonObject root) {
        Map<String, StructureLootProfile> profiles =
                new LinkedHashMap<String, StructureLootProfile>();
        profiles.put(StructureLootProfiles.DEFAULT_PROFILE_ID, StructureLootProfiles.defaultProfile());

        JsonArray profileArray = resolveRootLootProfileArray(root);
        if (profileArray == null || profileArray.size() == 0) {
            return profiles;
        }

        for (int profileIndex = 0; profileIndex < profileArray.size(); ++profileIndex) {
            JsonElement profileElement = profileArray.get(profileIndex);
            if (profileElement == null || !profileElement.isJsonObject()) {
                WorldGen.LOGGER.warn("Skip lootProfiles[{}]: expected JSON object", profileIndex);
                continue;
            }

            JsonObject profileObject = profileElement.getAsJsonObject();
            String rawId = getString(profileObject, LOOT_PROFILE_ID_KEY, null);
            String profileId = StructureLootProfiles.normalizeId(rawId);
            if (profileId == null) {
                WorldGen.LOGGER.warn("Skip lootProfiles[{}]: missing '{}'", profileIndex, LOOT_PROFILE_ID_KEY);
                continue;
            }

            try {
                StructureLootProfiles.Builder builder = StructureLootProfiles.builder(profileId);

                JsonArray markers = getArray(profileObject, LOOT_PROFILE_MARKERS_KEY);
                if (markers != null) {
                    for (int markerIndex = 0; markerIndex < markers.size(); ++markerIndex) {
                        JsonElement markerElement = markers.get(markerIndex);
                        if (markerElement == null || !markerElement.isJsonObject()) {
                            WorldGen.LOGGER.warn(
                                    "Skip lootProfiles[{}].markers[{}]: expected JSON object",
                                    profileIndex,
                                    markerIndex);
                            continue;
                        }

                        JsonObject marker = markerElement.getAsJsonObject();
                        int itemId = resolveItemId(marker, LOOT_MARKER_ITEM_KEY, LOOT_MARKER_ITEM_ID_KEY, -1);
                        int level = getInt(marker, LOOT_MARKER_LEVEL_KEY, 0);
                        if (itemId <= 0 || level <= 0) {
                            WorldGen.LOGGER.warn(
                                    "Skip lootProfiles[{}].markers[{}]: invalid item {} / itemId {} / level {}",
                                    profileIndex,
                                    markerIndex,
                                    getString(marker, LOOT_MARKER_ITEM_KEY, null),
                                    itemId,
                                    level);
                            continue;
                        }
                        builder.marker(itemId, level);
                    }
                }

                JsonArray levels = getArray(profileObject, LOOT_PROFILE_LEVELS_KEY);
                if (levels != null) {
                    for (int levelIndex = 0; levelIndex < levels.size(); ++levelIndex) {
                        JsonElement levelElement = levels.get(levelIndex);
                        if (levelElement == null || !levelElement.isJsonObject()) {
                            WorldGen.LOGGER.warn(
                                    "Skip lootProfiles[{}].levels[{}]: expected JSON object",
                                    profileIndex,
                                    levelIndex);
                            continue;
                        }

                        JsonObject levelObject = levelElement.getAsJsonObject();
                        int level = getInt(levelObject, LOOT_MARKER_LEVEL_KEY, 0);
                        int minRolls = Math.max(0, getInt(levelObject, LOOT_LEVEL_MIN_ROLLS_KEY, 0));
                        int maxRolls = Math.max(minRolls, getInt(levelObject, LOOT_LEVEL_MAX_ROLLS_KEY, minRolls));
                        WeightedRandomChestContent[] entries = parseLootEntries(
                                getArray(levelObject, LOOT_LEVEL_ENTRIES_KEY),
                                profileIndex,
                                levelIndex);
                        float[] artifactChances = parseArtifactChances(
                                getArray(levelObject, LOOT_LEVEL_ARTIFACT_CHANCES_KEY),
                                profileIndex,
                                levelIndex);

                        if (level <= 0) {
                            WorldGen.LOGGER.warn(
                                    "Skip lootProfiles[{}].levels[{}]: invalid level {}",
                                    profileIndex,
                                    levelIndex,
                                    level);
                            continue;
                        }

                        builder.level(level, minRolls, maxRolls, entries, artifactChances);
                    }
                }

                StructureLootProfile profile = builder.build();
                profiles.put(profile.id(), profile);
            } catch (Exception e) {
                WorldGen.LOGGER.warn(
                        "Skip lootProfiles[{}] id='{}': {}",
                        profileIndex,
                        profileId,
                        e.getMessage());
            }
        }

        return profiles;
    }

    private static WeightedRandomChestContent[] parseLootEntries(
            JsonArray entries,
            int profileIndex,
            int levelIndex) {
        if (entries == null || entries.size() == 0) {
            return new WeightedRandomChestContent[0];
        }

        List<WeightedRandomChestContent> output = new ArrayList<WeightedRandomChestContent>();
        for (int entryIndex = 0; entryIndex < entries.size(); ++entryIndex) {
            JsonElement entryElement = entries.get(entryIndex);
            if (entryElement == null || !entryElement.isJsonObject()) {
                WorldGen.LOGGER.warn(
                        "Skip lootProfiles[{}].levels[{}].entries[{}]: expected JSON object",
                        profileIndex,
                        levelIndex,
                        entryIndex);
                continue;
            }

            JsonObject entryObject = entryElement.getAsJsonObject();
            int itemId = resolveItemId(entryObject, LOOT_ENTRY_ITEM_KEY, LOOT_ENTRY_ITEM_ID_KEY, -1);
            if (itemId <= 0) {
                WorldGen.LOGGER.warn(
                        "Skip lootProfiles[{}].levels[{}].entries[{}]: invalid item {} / itemId {}",
                        profileIndex,
                        levelIndex,
                        entryIndex,
                        getString(entryObject, LOOT_ENTRY_ITEM_KEY, null),
                        itemId);
                continue;
            }

            int meta = Math.max(0, getInt(entryObject, LOOT_ENTRY_META_KEY, 0));
            int minCount = Math.max(1, getInt(entryObject, LOOT_ENTRY_MIN_KEY, 1));
            int maxCount = Math.max(minCount, getInt(entryObject, LOOT_ENTRY_MAX_KEY, minCount));
            int weight = Math.max(1, getInt(entryObject, LOOT_ENTRY_WEIGHT_KEY, 1));

            output.add(new WeightedRandomChestContent(itemId, meta, minCount, maxCount, weight));
        }

        return output.toArray(new WeightedRandomChestContent[0]);
    }

    private static float[] parseArtifactChances(JsonArray values, int profileIndex, int levelIndex) {
        if (values == null || values.size() == 0) {
            return null;
        }

        float[] buffer = new float[values.size()];
        int count = 0;
        for (int i = 0; i < values.size(); ++i) {
            JsonElement element = values.get(i);
            if (element == null || element.isJsonNull()) {
                continue;
            }
            try {
                float value = element.getAsFloat();
                if (value < 0.0F) {
                    value = 0.0F;
                }
                buffer[count++] = value;
            } catch (Exception ignored) {
                WorldGen.LOGGER.warn(
                        "Skip lootProfiles[{}].levels[{}].artifactChances[{}]: invalid number",
                        profileIndex,
                        levelIndex,
                        i);
            }
        }

        if (count <= 0) {
            return null;
        }
        return count == buffer.length ? buffer : Arrays.copyOf(buffer, count);
    }

    private static String normalizeLootProfileId(String value) {
        String normalized = StructureLootProfiles.normalizeId(value);
        return normalized == null ? StructureLootProfiles.DEFAULT_PROFILE_ID : normalized;
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
