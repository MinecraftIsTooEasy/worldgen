package com.github.hahahha.WorldGenLib.world.structure.config;

import com.github.hahahha.WorldGenLib.WorldGenLib;
import com.github.hahahha.WorldGenLib.util.LruCache;
import com.github.hahahha.WorldGenLib.util.StringNormalization;
import com.github.hahahha.WorldGenLib.world.structure.StructureEntityReplacementProfile;
import com.github.hahahha.WorldGenLib.world.structure.StructureEntityReplacementProfiles;
import com.github.hahahha.WorldGenLib.world.structure.StructureItemIdResolver;
import com.github.hahahha.WorldGenLib.world.structure.StructureLootProfile;
import com.github.hahahha.WorldGenLib.world.structure.StructureLootProfiles;
import com.github.hahahha.WorldGenLib.world.structure.api.StructureWorldGenLibApi;
import com.github.hahahha.WorldGenLib.world.structure.api.StructureWorldGenLibConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;
import moddedmite.rustedironcore.api.event.events.BiomeDecorationRegisterEvent;
import moddedmite.rustedironcore.api.world.Dimension;
import net.minecraft.BiomeGenBase;
import net.minecraft.ItemStack;
import net.minecraft.WeightedRandomChestContent;

/**
 * Loads structure WorldGenLib entries from a player-facing config file.
 *
 * <p>The config format is JSONC (JSON + comments). We parse it using a lenient Gson reader so players can keep
 * comments in the file.
 */
public final class StructureWorldGenLibPlayerConfigLoader {
    public static final String DEFAULT_FILE_NAME = "WorldGenLib-structures.jsonc";
    private static final File DEFAULT_CONFIG_FILE = new File("config", DEFAULT_FILE_NAME);
    public static final String DEFAULT_SCHEMATICS_DIR_NAME = "schematics";

    private static final String ROOT_ENABLED_KEY = "enabled";
    private static final String ROOT_SCHEMATICS_DIR_KEY = "schematicsDir";
    private static final String ROOT_LOOT_TABLE_ENABLED_KEY = "lootTableEnabled";
    private static final String ROOT_LOOT_ENABLED_ALIAS_KEY = "lootEnabled";
    private static final String ROOT_LOOT_KEY = "loot";
    private static final String ROOT_LOOT_ENABLED_CHILD_KEY = "enabled";
    private static final String ROOT_LOOT_PROFILES_CHILD_KEY = "profiles";
    private static final String ROOT_LOOT_PROFILES_KEY = "lootProfiles";
    private static final String ROOT_ENTITY_REPLACEMENT_KEY = "entityReplacement";
    private static final String ROOT_ENTITY_REPLACEMENT_ENABLED_KEY = "entityReplacementEnabled";
    private static final String ROOT_ENTITY_REPLACEMENT_PROFILES_KEY = "entityReplacementProfiles";
    private static final String ROOT_ENTITY_REPLACEMENT_ENABLED_CHILD_KEY = "enabled";
    private static final String ROOT_ENTITY_REPLACEMENT_PROFILES_CHILD_KEY = "profiles";
    private static final String STRUCTURES_KEY = "structures";
    private static final String ENTRY_ENABLED_KEY = "enabled";
    private static final String ENTRY_LOOT_KEY = "loot";
    private static final String ENTRY_ENTITY_REPLACEMENT_KEY = "entityReplacement";
    private static final String SCHEMATIC_PATH_KEY = "schematicPath";
    private static final String STRUCTURE_NAME_KEY = "name";
    private static final String SOURCE_MOD_KEY = "sourceMod";
    private static final String MOD_ID_ALIAS_KEY = "modId";
    private static final String SCHEMATIC_SUFFIX = ".schematic";
    private static final int SCHEMATIC_SUFFIX_LENGTH = SCHEMATIC_SUFFIX.length();
    private static final String UNKNOWN_STRUCTURE_NAME = "unknown";
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
    private static final String ENTITY_REPLACEMENT_ENABLED_KEY = "enabled";
    private static final String ENTITY_REPLACEMENT_PROFILE_KEY = "entityReplacementProfile";
    private static final String ENTITY_REPLACEMENT_PROFILE_ALIAS_KEY = "profile";
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
    private static final String ENTITY_PROFILE_ID_KEY = "id";
    private static final String ENTITY_PROFILE_LEVELS_KEY = "levels";
    private static final String ENTITY_LEVEL_VALUE_KEY = "level";
    private static final String ENTITY_LEVEL_TARGET_KEY = "target";
    private static final String ENTITY_LEVEL_TARGET_ALIAS_KEY = "entityId";
    private static final String ENTITY_LEVEL_EQUIPMENT_KEY = "equipment";
    private static final String ENTITY_LEVEL_DROPS_KEY = "drops";
    private static final String ENTITY_EQUIP_MAINHAND_KEY = "mainHand";
    private static final String ENTITY_EQUIP_MAINHAND_ALIAS_KEY = "mainhand";
    private static final String ENTITY_EQUIP_BOOTS_KEY = "boots";
    private static final String ENTITY_EQUIP_LEGGINGS_KEY = "leggings";
    private static final String ENTITY_EQUIP_CHESTPLATE_KEY = "chestplate";
    private static final String ENTITY_EQUIP_HELMET_KEY = "helmet";
    private static final String ENTITY_ITEM_KEY = "item";
    private static final String ENTITY_ITEM_ID_KEY = "itemId";
    private static final String ENTITY_ITEM_META_KEY = "meta";
    private static final String ENTITY_ITEM_COUNT_KEY = "count";
    private static final String ENTITY_DROP_MIN_KEY = "min";
    private static final String ENTITY_DROP_MAX_KEY = "max";
    private static final String ENTITY_DROP_CHANCE_KEY = "chance";

    private static final ConcurrentMap<String, Dimension> PARSED_DIMENSION_CACHE =
            new ConcurrentHashMap<String, Dimension>();
    private static final int PARSED_DIMENSION_CACHE_MAX_SIZE = 256;
    private static final Set<String> UNKNOWN_DIMENSION_VALUES =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final int UNKNOWN_DIMENSION_VALUES_MAX_SIZE = 256;
    private static final ConcurrentMap<String, StructureWorldGenLibConfig.DistanceScope> PARSED_DISTANCE_SCOPE_CACHE =
            new ConcurrentHashMap<String, StructureWorldGenLibConfig.DistanceScope>();
    private static final int PARSED_DISTANCE_SCOPE_CACHE_MAX_SIZE = 64;
    private static final Set<String> WARNED_DISTANCE_SCOPE_VALUES =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final int WARNED_DISTANCE_SCOPE_VALUES_MAX_SIZE = 64;
    private static final ThreadLocal<Map<String, Integer>> PARSE_ITEM_ID_CACHE =
            ThreadLocal.withInitial(() -> new HashMap<String, Integer>(256));
    private static final JsonParser JSON_PARSER = new JsonParser();
    private static final int ROOT_OBJECT_CACHE_MAX_SIZE = 16;
    private static final LruCache<String, CachedRootObject> ROOT_OBJECT_CACHE =
            new LruCache<String, CachedRootObject>(ROOT_OBJECT_CACHE_MAX_SIZE);
    private static final int ENTRY_WARN_DETAIL_LIMIT = 24;
    private static final int LOOT_PROFILE_WARN_DETAIL_LIMIT = 24;
    private static final int ENTITY_PROFILE_WARN_DETAIL_LIMIT = 24;

    private static final String DEFAULT_CONFIG_TEMPLATE = """
            // WorldGenLib player config (JSONC: comments are allowed)
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
            // - Structure entity replacement switch: structure.entityReplacement.enabled
            //   (legacy alias: entityReplacementEnabled).
            // - Structure entity replacement profile: structure.entityReplacement.profile
            //   (legacy alias: entityReplacementProfile).
            // - Root entity replacement profiles: entityReplacement.profiles
            //   (legacy alias: entityReplacementProfiles).
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
            // - API example switch: examples.test10.enabled controls whether built-in
            //   sample structure "/assets/worldgenlib/structures/test10.schematic" is registered.
            {
              "enabled": true,
              "schematicsDir": "schematics",
              "examples": {
                "test10": {
                  "enabled": false
                }
              },
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
                  },
                  "entityReplacement": {
                    "enabled": false,
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
              },
              "entityReplacement": {
                "enabled": false,
                "profiles": [
                  {
                    "id": "default",
                    "levels": [
                      {
                        "level": 2,
                        "target": "Skeleton",
                        "equipment": {
                          "mainHand": { "item": "swordIron" },
                          "helmet": { "item": "helmetIron" }
                        },
                        "drops": [
                          { "item": "ingotIron", "min": 1, "max": 1, "chance": 1.0 }
                        ]
                      }
                    ]
                  }
                ]
              }
            }
            """;

    private StructureWorldGenLibPlayerConfigLoader() {
    }

    public static final class ConfiguredStructureOption {
        private final String structureName;
        private final String sourceMod;

        private ConfiguredStructureOption(String structureName, String sourceMod) {
            this.structureName = structureName;
            this.sourceMod = sourceMod;
        }

        public String structureName() {
            return this.structureName;
        }

        public String sourceMod() {
            return this.sourceMod;
        }
    }

    public static int registerFromDefaultConfig(BiomeDecorationRegisterEvent event) {
        return registerFromFile(event, DEFAULT_CONFIG_FILE);
    }

    public static List<String> listConfiguredStructureNames(int maxEntries) {
        return listConfiguredStructureNames(DEFAULT_CONFIG_FILE, maxEntries, null);
    }

    public static List<String> listConfiguredStructureNames(File configFile, int maxEntries) {
        return listConfiguredStructureNames(configFile, maxEntries, null);
    }

    public static List<String> listConfiguredStructureNames(int maxEntries, Integer dimensionId) {
        return listConfiguredStructureNames(DEFAULT_CONFIG_FILE, maxEntries, dimensionId);
    }

    public static List<String> listConfiguredStructureNames(File configFile, int maxEntries, Integer dimensionId) {
        List<ConfiguredStructureOption> options = listConfiguredStructures(configFile, maxEntries, dimensionId);
        if (options.isEmpty()) {
            return Collections.emptyList();
        }

        LinkedHashSet<String> names =
                new LinkedHashSet<String>(Math.max(1, Math.min(maxEntries, options.size())));
        for (ConfiguredStructureOption option : options) {
            if (option == null || option.structureName() == null) {
                continue;
            }
            names.add(option.structureName());
            if (names.size() >= maxEntries) {
                break;
            }
        }
        if (names.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<String>(names);
    }

    public static List<ConfiguredStructureOption> listConfiguredStructures(int maxEntries) {
        return listConfiguredStructures(DEFAULT_CONFIG_FILE, maxEntries, null);
    }

    public static List<ConfiguredStructureOption> listConfiguredStructures(File configFile, int maxEntries) {
        return listConfiguredStructures(configFile, maxEntries, null);
    }

    public static List<ConfiguredStructureOption> listConfiguredStructures(int maxEntries, Integer dimensionId) {
        return listConfiguredStructures(DEFAULT_CONFIG_FILE, maxEntries, dimensionId);
    }

    public static List<ConfiguredStructureOption> listConfiguredStructures(
            File configFile, int maxEntries, Integer dimensionId) {
        if (maxEntries < 1 || configFile == null) {
            return Collections.emptyList();
        }

        File absoluteConfigFile = configFile.getAbsoluteFile();
        ensureConfigFileExists(absoluteConfigFile);
        JsonObject root = readRootObject(absoluteConfigFile);
        if (root == null) {
            return Collections.emptyList();
        }

        JsonArray structures = getArray(root, STRUCTURES_KEY);
        if (structures == null || structures.size() == 0) {
            return Collections.emptyList();
        }

        int structureCount = structures.size();
        File schematicsBaseDir = resolveSchematicsBaseDir(
                getString(root, ROOT_SCHEMATICS_DIR_KEY, DEFAULT_SCHEMATICS_DIR_NAME),
                absoluteConfigFile);
        Map<PathResolveCacheKey, String> resolvedPathCache =
                new HashMap<PathResolveCacheKey, String>(Math.max(16, structureCount));
        LinkedHashSet<ConfiguredStructureIdentityKey> unique =
                new LinkedHashSet<ConfiguredStructureIdentityKey>(Math.max(16, Math.min(maxEntries, structureCount)));
        List<ConfiguredStructureOption> options =
                new ArrayList<ConfiguredStructureOption>(Math.min(maxEntries, structureCount));

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

            String normalizedPath = null;
            String name = getString(entry, STRUCTURE_NAME_KEY, null);
            String rawSchematicPath = getString(entry, SCHEMATIC_PATH_KEY, null);
            if (rawSchematicPath != null) {
                String resolvedPath =
                        resolveSchematicPathCached(rawSchematicPath, schematicsBaseDir, resolvedPathCache);
                normalizedPath = normalizePath(resolvedPath);
                if (name == null && normalizedPath != null) {
                    name = extractStructureName(normalizedPath);
                }
            }
            if (name == null) {
                continue;
            }

            String normalizedName = StringNormalization.trimToNull(name);
            if (normalizedName == null) {
                continue;
            }

            String sourceMod = resolveConfiguredStructureSource(entry, normalizedPath);
            ConfiguredStructureIdentityKey uniqueKey = new ConfiguredStructureIdentityKey(sourceMod, normalizedName);
            if (!unique.add(uniqueKey)) {
                continue;
            }

            options.add(new ConfiguredStructureOption(normalizedName, sourceMod));
            if (options.size() >= maxEntries) {
                break;
            }
        }

        if (options.isEmpty()) {
            return Collections.emptyList();
        }
        return options;
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
            WorldGenLib.LOGGER.info("Structure WorldGenLib is disabled by config: {}", absoluteConfigFile.getPath());
            return 0;
        }

        Map<String, Integer> parseItemIdCache = PARSE_ITEM_ID_CACHE.get();
        parseItemIdCache.clear();
        try {
            boolean rootLootTableEnabled = resolveRootLootTableEnabled(root);
            Map<String, StructureLootProfile> lootProfiles = parseLootProfiles(root);
            boolean rootEntityReplacementEnabled = resolveRootEntityReplacementEnabled(root);
            Map<String, StructureEntityReplacementProfile> entityReplacementProfiles = parseEntityReplacementProfiles(root);
            JsonArray structures = getArray(root, STRUCTURES_KEY);
            if (structures == null || structures.size() == 0) {
                WorldGenLib.LOGGER.info("No structure entries found in config: {}", absoluteConfigFile.getPath());
                return 0;
            }

            int structureCount = structures.size();
            File schematicsBaseDir = resolveSchematicsBaseDir(
                    getString(root, ROOT_SCHEMATICS_DIR_KEY, DEFAULT_SCHEMATICS_DIR_NAME),
                    absoluteConfigFile);
            Map<PathResolveCacheKey, String> resolvedPathCache =
                    new HashMap<PathResolveCacheKey, String>(Math.max(16, structureCount));
            Map<String, Boolean> schematicAvailabilityCache =
                    new HashMap<String, Boolean>(Math.max(16, structureCount));
            int registered = 0;
            WarnLimiter entryWarnLimiter = new WarnLimiter(ENTRY_WARN_DETAIL_LIMIT);
            for (int i = 0; i < structures.size(); ++i) {
                JsonElement entryElement = structures.get(i);
                if (!entryElement.isJsonObject()) {
                    entryWarnLimiter.warn("Skip config entry[{}]: expected JSON object", i);
                    continue;
                }

                JsonObject entry = entryElement.getAsJsonObject();
                if (!getBoolean(entry, ENTRY_ENABLED_KEY, true)) {
                    continue;
                }

                String rawSchematicPath = getString(entry, SCHEMATIC_PATH_KEY, null);
                if (rawSchematicPath == null) {
                    entryWarnLimiter.warn("Skip config entry[{}]: missing '{}'", i, SCHEMATIC_PATH_KEY);
                    continue;
                }

                String schematicPath = resolveSchematicPathCached(
                        rawSchematicPath,
                        schematicsBaseDir,
                        resolvedPathCache);
                if (!isSchematicAvailableCached(schematicPath, schematicAvailabilityCache)) {
                    entryWarnLimiter.warn(
                            "Skip config entry[{}]: schematic file/resource not found '{}'",
                            i,
                            schematicPath);
                    continue;
                }
                String structureName = getString(entry, STRUCTURE_NAME_KEY, null);
                String dimensionName = getString(entry, DIMENSION_KEY, "overworld");
                Dimension dimension = parseDimension(dimensionName);
                if (dimension == null) {
                    entryWarnLimiter.warn("Skip config entry[{}]: unknown dimension '{}'", i, dimensionName);
                    continue;
                }

                int weight = getInt(entry, WEIGHT_KEY, 1);
                int chance = getInt(entry, CHANCE_KEY, 40);
                int attempts = getInt(entry, ATTEMPTS_KEY, 1);
                boolean surface = getBoolean(entry, SURFACE_KEY, true);
                int minY = getInt(entry, MIN_Y_KEY, StructureWorldGenLibConfig.MIN_WORLD_Y);
                int maxY = getInt(entry, MAX_Y_KEY, StructureWorldGenLibConfig.MAX_WORLD_Y);
                int yOffset = getInt(entry, Y_OFFSET_KEY, 0);
                boolean centerOnAnchor = getBoolean(entry, CENTER_ON_ANCHOR_KEY, true);
                int minDistance = getInt(entry, MIN_DISTANCE_KEY, 0);
                boolean lootTableEnabled = resolveEntryLootTableEnabled(entry, rootLootTableEnabled);
                boolean entityReplacementEnabled =
                        resolveEntryEntityReplacementEnabled(entry, rootEntityReplacementEnabled);
                StructureWorldGenLibConfig.DistanceScope distanceScope = parseDistanceScope(
                        getString(entry, DISTANCE_SCOPE_KEY, "all"),
                        i,
                        entryWarnLimiter);
                String lootProfileId = resolveEntryLootProfileId(entry);
                StructureLootProfile lootProfile = lootProfiles.get(lootProfileId);
                if (lootProfile == null) {
                    entryWarnLimiter.warn(
                            "Unknown lootProfile '{}' in config entry[{}], fallback to '{}'",
                            lootProfileId,
                            i,
                            StructureLootProfiles.DEFAULT_PROFILE_ID);
                    lootProfile = StructureLootProfiles.defaultProfile();
                }
                String entityReplacementProfileId = resolveEntryEntityReplacementProfileId(entry);
                StructureEntityReplacementProfile entityReplacementProfile =
                        entityReplacementProfiles.get(entityReplacementProfileId);
                if (entityReplacementProfile == null) {
                    entryWarnLimiter.warn(
                            "Unknown entityReplacementProfile '{}' in config entry[{}], fallback to '{}'",
                            entityReplacementProfileId,
                            i,
                            StructureEntityReplacementProfiles.DEFAULT_PROFILE_ID);
                    entityReplacementProfile = StructureEntityReplacementProfiles.defaultProfile();
                }
                Predicate<BiomeGenBase> biomeFilter = parseBiomeFilter(entry, i, entryWarnLimiter);

                try {
                    StructureWorldGenLibConfig.Builder builder = StructureWorldGenLibApi.builder(schematicPath)
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
                            .lootProfile(lootProfile)
                            .entityReplacementEnabled(entityReplacementEnabled)
                            .entityReplacementProfile(entityReplacementProfile);
                    if (biomeFilter != null) {
                        builder.biomeFilter(biomeFilter);
                    }
                    StructureWorldGenLibConfig config = builder.build();
                    StructureWorldGenLibApi.register(event, config);
                    registered++;
                } catch (IllegalArgumentException e) {
                    entryWarnLimiter.warn("Skip config entry[{}]: {}", i, e.getMessage());
                }
            }
            entryWarnLimiter.logSummary("config entry warnings", absoluteConfigFile.getPath());

            WorldGenLib.LOGGER.info(
                    "Loaded {} structure entries from player config: {}",
                    registered,
                    absoluteConfigFile.getPath());
            return registered;
        } finally {
            parseItemIdCache.clear();
        }
    }

    private static void ensureConfigFileExists(File configFile) {
        File parent = configFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            WorldGenLib.LOGGER.warn("Failed to create config directory: {}", parent.getPath());
        }
        ensureSchematicsDirectoryExists(configFile);

        if (configFile.isFile()) {
            return;
        }

        try {
            Files.writeString(configFile.toPath(), DEFAULT_CONFIG_TEMPLATE, StandardCharsets.UTF_8);
            WorldGenLib.LOGGER.info("Generated player config template: {}", configFile.getPath());
        } catch (IOException e) {
            WorldGenLib.LOGGER.error("Failed to create player config template: {}", configFile.getPath(), e);
        }
    }

    private static void ensureSchematicsDirectoryExists(File configFile) {
        File schematicsDir = resolveSchematicsBaseDir(DEFAULT_SCHEMATICS_DIR_NAME, configFile);
        if (schematicsDir.exists()) {
            return;
        }
        if (!schematicsDir.mkdirs()) {
            WorldGenLib.LOGGER.warn("Failed to create schematics directory: {}", schematicsDir.getPath());
        }
    }

    private static JsonObject readRootObject(File configFile) {
        if (!configFile.isFile()) {
            WorldGenLib.LOGGER.warn("Config file does not exist: {}", configFile.getPath());
            invalidateRootObjectCache(configFile);
            return null;
        }

        File absoluteConfigFile = configFile.getAbsoluteFile();
        String cacheKey = absoluteConfigFile.getPath();
        long lastModified = absoluteConfigFile.lastModified();
        long length = absoluteConfigFile.length();
        CachedRootObject cached = ROOT_OBJECT_CACHE.get(cacheKey);
        if (cached != null && cached.matches(lastModified, length)) {
            return cached.root();
        }

        JsonObject parsed = parseRootObjectUncached(absoluteConfigFile);
        ROOT_OBJECT_CACHE.put(cacheKey, new CachedRootObject(lastModified, length, parsed));
        return parsed;
    }

    private static JsonObject parseRootObjectUncached(File configFile) {
        try (Reader reader = Files.newBufferedReader(configFile.toPath(), StandardCharsets.UTF_8)) {
            JsonReader jsonReader = new JsonReader(reader);
            jsonReader.setLenient(true);
            JsonElement rootElement;
            synchronized (JSON_PARSER) {
                rootElement = JSON_PARSER.parse(jsonReader);
            }
            if (rootElement == null || rootElement.isJsonNull()) {
                WorldGenLib.LOGGER.warn("Config file is empty: {}", configFile.getPath());
                return null;
            }
            if (!rootElement.isJsonObject()) {
                WorldGenLib.LOGGER.warn("Config root must be a JSON object: {}", configFile.getPath());
                return null;
            }
            return rootElement.getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            WorldGenLib.LOGGER.error("Failed to parse player config: {}", configFile.getPath(), e);
            return null;
        }
    }

    private static void invalidateRootObjectCache(File configFile) {
        if (configFile == null) {
            return;
        }
        ROOT_OBJECT_CACHE.remove(configFile.getAbsolutePath());
    }

    private static JsonArray getArray(JsonObject object, String key) {
        if (object == null || key == null || key.isEmpty()) {
            return null;
        }
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static JsonObject getObject(JsonObject object, String key) {
        if (object == null || key == null || key.isEmpty()) {
            return null;
        }
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static String getString(JsonObject object, String key, String defaultValue) {
        if (object == null || key == null || key.isEmpty()) {
            return defaultValue;
        }
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return defaultValue;
        }
        if (!element.isJsonPrimitive()) {
            return defaultValue;
        }
        String value = element.getAsString();
        if (value == null) {
            return defaultValue;
        }
        String trimmed = StringNormalization.trimToNull(value);
        return trimmed == null ? defaultValue : trimmed;
    }

    private static int getInt(JsonObject object, String key, int defaultValue) {
        if (object == null || key == null || key.isEmpty()) {
            return defaultValue;
        }
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return defaultValue;
        }
        Integer value = asInt(element);
        if (value == null) {
            return defaultValue;
        }
        return value.intValue();
    }

    private static float getFloat(JsonObject object, String key, float defaultValue) {
        if (object == null || key == null || key.isEmpty()) {
            return defaultValue;
        }
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return defaultValue;
        }
        Float value = asFloat(element);
        if (value == null) {
            return defaultValue;
        }
        return value.floatValue();
    }

    private static boolean getBoolean(JsonObject object, String key, boolean defaultValue) {
        if (object == null || key == null || key.isEmpty()) {
            return defaultValue;
        }
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return defaultValue;
        }
        Boolean value = asBoolean(element);
        if (value == null) {
            return defaultValue;
        }
        return value.booleanValue();
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
            Boolean value = asBoolean(primary);
            if (value != null) {
                return value.booleanValue();
            }
        }

        JsonElement alias = object.get(aliasKey);
        if (alias != null && !alias.isJsonNull()) {
            Boolean value = asBoolean(alias);
            if (value != null) {
                return value.booleanValue();
            }
        }

        return defaultValue;
    }

    private static Integer asInt(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isNumber()) {
            return primitive.getAsInt();
        }
        if (!primitive.isString()) {
            return null;
        }
        return parseIntSafe(primitive.getAsString());
    }

    private static Float asFloat(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isNumber()) {
            return primitive.getAsFloat();
        }
        if (!primitive.isString()) {
            return null;
        }
        return parseFloatSafe(primitive.getAsString());
    }

    private static Boolean asBoolean(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isNumber() || primitive.isString()) {
            return Boolean.parseBoolean(primitive.getAsString());
        }
        return null;
    }

    private static Integer parseIntSafe(String value) {
        String trimmed = StringNormalization.trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Float parseFloatSafe(String value) {
        String trimmed = StringNormalization.trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return Float.parseFloat(trimmed);
        } catch (NumberFormatException ignored) {
            return null;
        }
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

    private static boolean resolveEntityReplacementEnabledFromObject(
            JsonObject entityReplacementObject,
            boolean defaultValue) {
        if (entityReplacementObject == null) {
            return defaultValue;
        }
        return getBooleanWithAlias(
                entityReplacementObject,
                ENTITY_REPLACEMENT_ENABLED_KEY,
                ROOT_ENTITY_REPLACEMENT_ENABLED_KEY,
                getBoolean(entityReplacementObject, ROOT_ENTITY_REPLACEMENT_ENABLED_CHILD_KEY, defaultValue));
    }

    private static boolean resolveRootEntityReplacementEnabled(JsonObject root) {
        boolean legacyEnabled = getBoolean(root, ROOT_ENTITY_REPLACEMENT_ENABLED_KEY, false);
        JsonObject entityReplacementObject = getObject(root, ROOT_ENTITY_REPLACEMENT_KEY);
        if (entityReplacementObject == null) {
            return legacyEnabled;
        }
        return getBooleanWithAlias(
                entityReplacementObject,
                ROOT_ENTITY_REPLACEMENT_ENABLED_KEY,
                ENTITY_REPLACEMENT_ENABLED_KEY,
                getBoolean(entityReplacementObject, ROOT_ENTITY_REPLACEMENT_ENABLED_CHILD_KEY, legacyEnabled));
    }

    private static boolean resolveEntryEntityReplacementEnabled(JsonObject entry, boolean rootDefault) {
        boolean legacyEnabled = getBoolean(entry, ROOT_ENTITY_REPLACEMENT_ENABLED_KEY, rootDefault);
        JsonObject entryReplacement = getObject(entry, ENTRY_ENTITY_REPLACEMENT_KEY);
        return resolveEntityReplacementEnabledFromObject(entryReplacement, legacyEnabled);
    }

    private static String resolveEntryEntityReplacementProfileId(JsonObject entry) {
        String profileId = getString(entry, ENTITY_REPLACEMENT_PROFILE_KEY, StructureEntityReplacementProfiles.DEFAULT_PROFILE_ID);
        JsonObject entryReplacement = getObject(entry, ENTRY_ENTITY_REPLACEMENT_KEY);
        if (entryReplacement != null) {
            profileId = getString(
                    entryReplacement,
                    ENTITY_REPLACEMENT_PROFILE_KEY,
                    getString(entryReplacement, ENTITY_REPLACEMENT_PROFILE_ALIAS_KEY, profileId));
        }
        return normalizeEntityReplacementProfileId(profileId);
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

    private static JsonArray resolveRootEntityReplacementProfileArray(JsonObject root) {
        JsonObject replacementObject = getObject(root, ROOT_ENTITY_REPLACEMENT_KEY);
        if (replacementObject != null) {
            JsonArray profiles = getArray(replacementObject, ROOT_ENTITY_REPLACEMENT_PROFILES_CHILD_KEY);
            if (profiles != null) {
                return profiles;
            }
            profiles = getArray(replacementObject, ROOT_ENTITY_REPLACEMENT_PROFILES_KEY);
            if (profiles != null) {
                return profiles;
            }
        }
        return getArray(root, ROOT_ENTITY_REPLACEMENT_PROFILES_KEY);
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

        Integer numeric = asInt(element);
        if (numeric != null) {
            return StructureItemIdResolver.isValidItemId(numeric.intValue()) ? numeric : null;
        }

        if (!element.isJsonPrimitive()) {
            return null;
        }

        String raw = element.getAsString();
        String normalized = StringNormalization.trimToNull(raw);
        if (normalized == null) {
            return null;
        }

        Map<String, Integer> cache = PARSE_ITEM_ID_CACHE.get();

        String cacheKey = normalized.toLowerCase(Locale.ROOT);
        Integer cached = cache.get(cacheKey);
        if (cached != null) {
            return cached.intValue() > 0 ? cached : null;
        }

        Integer resolved = resolveItemIdByAlias(normalized);
        int cachedValue = resolved != null && resolved.intValue() > 0 ? resolved.intValue() : -1;
        Integer boxed = cachedValue;
        cache.put(cacheKey, boxed);
        return cachedValue > 0 ? boxed : null;
    }

    private static Integer resolveItemIdByAlias(String token) {
        return StructureItemIdResolver.resolveItemIdByName(token);
    }

    public static Integer resolveItemIdByName(String token) {
        return StructureItemIdResolver.resolveItemIdByName(token);
    }

    private static Map<String, StructureLootProfile> parseLootProfiles(JsonObject root) {
        JsonArray profileArray = resolveRootLootProfileArray(root);
        int profileCount = profileArray == null ? 0 : profileArray.size();
        Map<String, StructureLootProfile> profiles =
                new LinkedHashMap<String, StructureLootProfile>(Math.max(4, profileCount + 1));
        profiles.put(StructureLootProfiles.DEFAULT_PROFILE_ID, StructureLootProfiles.defaultProfile());
        if (profileCount == 0) {
            return profiles;
        }

        WarnLimiter warnLimiter = new WarnLimiter(LOOT_PROFILE_WARN_DETAIL_LIMIT);
        for (int profileIndex = 0; profileIndex < profileArray.size(); ++profileIndex) {
            JsonElement profileElement = profileArray.get(profileIndex);
            if (profileElement == null || !profileElement.isJsonObject()) {
                warnLimiter.warn("Skip lootProfiles[{}]: expected JSON object", profileIndex);
                continue;
            }

            JsonObject profileObject = profileElement.getAsJsonObject();
            String rawId = getString(profileObject, LOOT_PROFILE_ID_KEY, null);
            String profileId = StructureLootProfiles.normalizeId(rawId);
            if (profileId == null) {
                warnLimiter.warn("Skip lootProfiles[{}]: missing '{}'", profileIndex, LOOT_PROFILE_ID_KEY);
                continue;
            }

            try {
                StructureLootProfiles.Builder builder = StructureLootProfiles.builder(profileId);

                JsonArray markers = getArray(profileObject, LOOT_PROFILE_MARKERS_KEY);
                if (markers != null) {
                    for (int markerIndex = 0; markerIndex < markers.size(); ++markerIndex) {
                        JsonElement markerElement = markers.get(markerIndex);
                        if (markerElement == null || !markerElement.isJsonObject()) {
                            warnLimiter.warn(
                                    "Skip lootProfiles[{}].markers[{}]: expected JSON object",
                                    profileIndex,
                                    markerIndex);
                            continue;
                        }

                        JsonObject marker = markerElement.getAsJsonObject();
                        int itemId = resolveItemId(marker, LOOT_MARKER_ITEM_KEY, LOOT_MARKER_ITEM_ID_KEY, -1);
                        int level = getInt(marker, LOOT_MARKER_LEVEL_KEY, 0);
                        if (itemId <= 0 || level <= 0) {
                            warnLimiter.warn(
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
                            warnLimiter.warn(
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
                                levelIndex,
                                warnLimiter);
                        float[] artifactChances = parseArtifactChances(
                                getArray(levelObject, LOOT_LEVEL_ARTIFACT_CHANCES_KEY),
                                profileIndex,
                                levelIndex,
                                warnLimiter);

                        if (level <= 0) {
                            warnLimiter.warn(
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
            } catch (RuntimeException e) {
                warnLimiter.warn(
                        "Skip lootProfiles[{}] id='{}': {}",
                        profileIndex,
                        profileId,
                        e.getMessage());
            }
        }

        warnLimiter.logSummary("loot profile warnings", "loot.profiles");
        return profiles;
    }

    private static Map<String, StructureEntityReplacementProfile> parseEntityReplacementProfiles(JsonObject root) {
        JsonArray profileArray = resolveRootEntityReplacementProfileArray(root);
        int profileCount = profileArray == null ? 0 : profileArray.size();
        Map<String, StructureEntityReplacementProfile> profiles =
                new LinkedHashMap<String, StructureEntityReplacementProfile>(Math.max(4, profileCount + 1));
        profiles.put(
                StructureEntityReplacementProfiles.DEFAULT_PROFILE_ID,
                StructureEntityReplacementProfiles.defaultProfile());
        if (profileCount == 0) {
            return profiles;
        }

        WarnLimiter warnLimiter = new WarnLimiter(ENTITY_PROFILE_WARN_DETAIL_LIMIT);
        for (int profileIndex = 0; profileIndex < profileArray.size(); ++profileIndex) {
            JsonElement profileElement = profileArray.get(profileIndex);
            if (profileElement == null || !profileElement.isJsonObject()) {
                warnLimiter.warn("Skip entityReplacement.profiles[{}]: expected JSON object", profileIndex);
                continue;
            }

            JsonObject profileObject = profileElement.getAsJsonObject();
            String rawId = getString(profileObject, ENTITY_PROFILE_ID_KEY, null);
            String profileId = StructureEntityReplacementProfiles.normalizeId(rawId);
            if (profileId == null) {
                warnLimiter.warn(
                        "Skip entityReplacement.profiles[{}]: missing '{}'",
                        profileIndex,
                        ENTITY_PROFILE_ID_KEY);
                continue;
            }

            try {
                StructureEntityReplacementProfiles.Builder builder = StructureEntityReplacementProfiles.builder(profileId);
                JsonArray levels = getArray(profileObject, ENTITY_PROFILE_LEVELS_KEY);
                if (levels != null) {
                    for (int levelIndex = 0; levelIndex < levels.size(); ++levelIndex) {
                        JsonElement levelElement = levels.get(levelIndex);
                        if (levelElement == null || !levelElement.isJsonObject()) {
                            warnLimiter.warn(
                                    "Skip entityReplacement.profiles[{}].levels[{}]: expected JSON object",
                                    profileIndex,
                                    levelIndex);
                            continue;
                        }

                        JsonObject levelObject = levelElement.getAsJsonObject();
                        int level = getInt(levelObject, ENTITY_LEVEL_VALUE_KEY, 0);
                        String targetEntityId = getString(
                                levelObject,
                                ENTITY_LEVEL_TARGET_KEY,
                                getString(levelObject, ENTITY_LEVEL_TARGET_ALIAS_KEY, null));
                        if (level <= 0 || targetEntityId == null) {
                            warnLimiter.warn(
                                    "Skip entityReplacement.profiles[{}].levels[{}]: invalid level '{}' or missing target",
                                    profileIndex,
                                    levelIndex,
                                    level);
                            continue;
                        }

                        StructureEntityReplacementProfile.EntityEquipment equipment =
                                parseEntityEquipment(
                                        getObject(levelObject, ENTITY_LEVEL_EQUIPMENT_KEY),
                                        profileIndex,
                                        levelIndex,
                                        warnLimiter);
                        StructureEntityReplacementProfile.EntityDrop[] drops =
                                parseEntityDrops(
                                        getArray(levelObject, ENTITY_LEVEL_DROPS_KEY),
                                        profileIndex,
                                        levelIndex,
                                        warnLimiter);
                        builder.level(level, targetEntityId, equipment, drops);
                    }
                }
                StructureEntityReplacementProfile profile = builder.build();
                profiles.put(profile.id(), profile);
            } catch (RuntimeException e) {
                warnLimiter.warn(
                        "Skip entityReplacement.profiles[{}] id='{}': {}",
                        profileIndex,
                        profileId,
                        e.getMessage());
            }
        }

        warnLimiter.logSummary("entity replacement profile warnings", "entityReplacement.profiles");
        return profiles;
    }

    private static StructureEntityReplacementProfile.EntityEquipment parseEntityEquipment(
            JsonObject equipmentObject,
            int profileIndex,
            int levelIndex,
            WarnLimiter warnLimiter) {
        if (equipmentObject == null) {
            return null;
        }

        ItemStack mainHand = parseEquipmentStack(
                equipmentObject,
                ENTITY_EQUIP_MAINHAND_KEY,
                ENTITY_EQUIP_MAINHAND_ALIAS_KEY,
                profileIndex,
                levelIndex,
                warnLimiter);
        ItemStack boots = parseEquipmentStack(
                equipmentObject, ENTITY_EQUIP_BOOTS_KEY, null, profileIndex, levelIndex, warnLimiter);
        ItemStack leggings = parseEquipmentStack(
                equipmentObject, ENTITY_EQUIP_LEGGINGS_KEY, null, profileIndex, levelIndex, warnLimiter);
        ItemStack chestplate = parseEquipmentStack(
                equipmentObject, ENTITY_EQUIP_CHESTPLATE_KEY, null, profileIndex, levelIndex, warnLimiter);
        ItemStack helmet = parseEquipmentStack(
                equipmentObject, ENTITY_EQUIP_HELMET_KEY, null, profileIndex, levelIndex, warnLimiter);

        StructureEntityReplacementProfile.EntityEquipment equipment =
                StructureEntityReplacementProfile.EntityEquipment.of(mainHand, boots, leggings, chestplate, helmet);
        return equipment.isEmpty() ? null : equipment;
    }

    private static ItemStack parseEquipmentStack(
            JsonObject equipmentObject,
            String slotKey,
            String aliasKey,
            int profileIndex,
            int levelIndex,
            WarnLimiter warnLimiter) {
        if (equipmentObject == null) {
            return null;
        }

        JsonElement element = equipmentObject.get(slotKey);
        if ((element == null || element.isJsonNull()) && aliasKey != null) {
            element = equipmentObject.get(aliasKey);
        }
        if (element == null || element.isJsonNull()) {
            return null;
        }

        try {
            if (element.isJsonObject()) {
                JsonObject stackObject = element.getAsJsonObject();
                int itemId = resolveItemId(stackObject, ENTITY_ITEM_KEY, ENTITY_ITEM_ID_KEY, -1);
                if (itemId <= 0) {
                    return null;
                }
                int meta = Math.max(0, getInt(stackObject, ENTITY_ITEM_META_KEY, 0));
                int count = Math.max(1, getInt(stackObject, ENTITY_ITEM_COUNT_KEY, 1));
                return new ItemStack(itemId, count, meta);
            }

            Integer resolved = resolveItemIdElement(element);
            if (resolved != null && resolved.intValue() > 0) {
                return new ItemStack(resolved.intValue(), 1, 0);
            }
        } catch (RuntimeException e) {
            warnLimiter.warn(
                    "Skip entityReplacement.profiles[{}].levels[{}].equipment.{}: {}",
                    profileIndex,
                    levelIndex,
                    slotKey,
                    e.getMessage());
        }
        return null;
    }

    private static StructureEntityReplacementProfile.EntityDrop[] parseEntityDrops(
            JsonArray drops,
            int profileIndex,
            int levelIndex,
            WarnLimiter warnLimiter) {
        if (drops == null || drops.size() == 0) {
            return null;
        }

        List<StructureEntityReplacementProfile.EntityDrop> output =
                new ArrayList<StructureEntityReplacementProfile.EntityDrop>(drops.size());
        for (int dropIndex = 0; dropIndex < drops.size(); ++dropIndex) {
            JsonElement dropElement = drops.get(dropIndex);
            if (dropElement == null || !dropElement.isJsonObject()) {
                warnLimiter.warn(
                        "Skip entityReplacement.profiles[{}].levels[{}].drops[{}]: expected JSON object",
                        profileIndex,
                        levelIndex,
                        dropIndex);
                continue;
            }

            JsonObject dropObject = dropElement.getAsJsonObject();
            int itemId = resolveItemId(dropObject, ENTITY_ITEM_KEY, ENTITY_ITEM_ID_KEY, -1);
            if (itemId <= 0) {
                warnLimiter.warn(
                        "Skip entityReplacement.profiles[{}].levels[{}].drops[{}]: invalid item",
                        profileIndex,
                        levelIndex,
                        dropIndex);
                continue;
            }
            int meta = Math.max(0, getInt(dropObject, ENTITY_ITEM_META_KEY, 0));
            int min = Math.max(1, getInt(dropObject, ENTITY_DROP_MIN_KEY, 1));
            int max = Math.max(min, getInt(dropObject, ENTITY_DROP_MAX_KEY, min));
            float chance = getFloat(dropObject, ENTITY_DROP_CHANCE_KEY, 1.0F);
            output.add(new StructureEntityReplacementProfile.EntityDrop(itemId, meta, min, max, chance));
        }

        if (output.isEmpty()) {
            return null;
        }
        return output.toArray(new StructureEntityReplacementProfile.EntityDrop[output.size()]);
    }

    private static WeightedRandomChestContent[] parseLootEntries(
            JsonArray entries,
            int profileIndex,
            int levelIndex,
            WarnLimiter warnLimiter) {
        if (entries == null || entries.size() == 0) {
            return new WeightedRandomChestContent[0];
        }

        List<WeightedRandomChestContent> output = new ArrayList<WeightedRandomChestContent>(entries.size());
        for (int entryIndex = 0; entryIndex < entries.size(); ++entryIndex) {
            JsonElement entryElement = entries.get(entryIndex);
            if (entryElement == null || !entryElement.isJsonObject()) {
                warnLimiter.warn(
                        "Skip lootProfiles[{}].levels[{}].entries[{}]: expected JSON object",
                        profileIndex,
                        levelIndex,
                        entryIndex);
                continue;
            }

            JsonObject entryObject = entryElement.getAsJsonObject();
            int itemId = resolveItemId(entryObject, LOOT_ENTRY_ITEM_KEY, LOOT_ENTRY_ITEM_ID_KEY, -1);
            if (itemId <= 0) {
                warnLimiter.warn(
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

        return output.toArray(new WeightedRandomChestContent[output.size()]);
    }

    private static float[] parseArtifactChances(
            JsonArray values,
            int profileIndex,
            int levelIndex,
            WarnLimiter warnLimiter) {
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
            Float parsed = asFloat(element);
            if (parsed == null) {
                warnLimiter.warn(
                        "Skip lootProfiles[{}].levels[{}].artifactChances[{}]: invalid number",
                        profileIndex,
                        levelIndex,
                        i);
                continue;
            }
            float value = parsed.floatValue();
            if (value < 0.0F) {
                value = 0.0F;
            }
            buffer[count++] = value;
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

    private static String normalizeEntityReplacementProfileId(String value) {
        String normalized = StructureEntityReplacementProfiles.normalizeId(value);
        return normalized == null ? StructureEntityReplacementProfiles.DEFAULT_PROFILE_ID : normalized;
    }

    private static Predicate<BiomeGenBase> parseBiomeFilter(
            JsonObject entry,
            int entryIndex,
            WarnLimiter warnLimiter) {
        Set<Integer> biomeIds = getIntSet(entry, BIOME_IDS_KEY);
        Set<String> biomeNames = getNormalizedStringSet(entry, BIOME_NAMES_KEY);
        if (biomeIds.isEmpty() && biomeNames.isEmpty()) {
            return null;
        }

        String modeValue = getString(entry, BIOME_MODE_KEY, "whitelist");
        String normalizedMode = StringNormalization.trimLowerToNull(modeValue);
        normalizedMode = normalizedMode == null ? "whitelist" : normalizedMode;
        final boolean whitelist;
        if ("blacklist".equals(normalizedMode) || "exclude".equals(normalizedMode) || "deny".equals(normalizedMode)) {
            whitelist = false;
        } else if (
                "whitelist".equals(normalizedMode)
                        || "include".equals(normalizedMode)
                        || "allow".equals(normalizedMode)) {
            whitelist = true;
        } else {
            if (warnLimiter != null) {
                warnLimiter.warn(
                        "Unknown biomeMode '{}' in config entry[{}], fallback to whitelist",
                        modeValue,
                        entryIndex);
            } else {
                WorldGenLib.LOGGER.warn(
                        "Unknown biomeMode '{}' in config entry[{}], fallback to whitelist",
                        modeValue,
                        entryIndex);
            }
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
            return Collections.emptySet();
        }

        Set<Integer> values = new HashSet<Integer>(Math.max(4, array.size() * 2));
        for (int i = 0; i < array.size(); ++i) {
            JsonElement element = array.get(i);
            if (element == null || element.isJsonNull()) {
                continue;
            }
            Integer value = asInt(element);
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    private static Set<String> getNormalizedStringSet(JsonObject object, String key) {
        JsonArray array = getArray(object, key);
        if (array == null || array.size() == 0) {
            return Collections.emptySet();
        }

        Set<String> values = new HashSet<String>(Math.max(4, array.size() * 2));
        for (int i = 0; i < array.size(); ++i) {
            JsonElement element = array.get(i);
            if (element == null || element.isJsonNull()) {
                continue;
            }
            if (!element.isJsonPrimitive()) {
                continue;
            }
            String normalized = normalizeBiomeName(element.getAsString());
            if (normalized != null) {
                values.add(normalized);
            }
        }
        return values;
    }

    private static String normalizeBiomeName(String biomeName) {
        return StringNormalization.trimLowerToNull(biomeName);
    }

    private static Dimension parseDimension(String value) {
        String normalized = StringNormalization.trimToNull(value);
        if (normalized == null) {
            return Dimension.OVERWORLD;
        }

        String cacheKey = normalized.toLowerCase(Locale.ROOT);
        Dimension cached = PARSED_DIMENSION_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        if (UNKNOWN_DIMENSION_VALUES.contains(cacheKey)) {
            return null;
        }

        Dimension dimension = Dimension.fromString(normalized);
        if (dimension == null) {
            dimension = Dimension.fromString(cacheKey);
        }
        if (dimension == null) {
            dimension = Dimension.fromString(normalized.toUpperCase(Locale.ROOT));
        }

        if (dimension == null) {
            if (UNKNOWN_DIMENSION_VALUES.size() >= UNKNOWN_DIMENSION_VALUES_MAX_SIZE) {
                evictOneSetEntry(UNKNOWN_DIMENSION_VALUES);
            }
            UNKNOWN_DIMENSION_VALUES.add(cacheKey);
            return null;
        }
        UNKNOWN_DIMENSION_VALUES.remove(cacheKey);
        if (PARSED_DIMENSION_CACHE.size() >= PARSED_DIMENSION_CACHE_MAX_SIZE) {
            evictOneMapEntry(PARSED_DIMENSION_CACHE);
        }
        Dimension previous = PARSED_DIMENSION_CACHE.putIfAbsent(cacheKey, dimension);
        if (previous != null) {
            return previous;
        }
        return dimension;
    }

    private static StructureWorldGenLibConfig.DistanceScope parseDistanceScope(
            String value,
            int entryIndex,
            WarnLimiter warnLimiter) {
        String normalized = StringNormalization.trimLowerToNull(value);
        normalized = normalized == null ? "all" : normalized;

        StructureWorldGenLibConfig.DistanceScope cached = PARSED_DISTANCE_SCOPE_CACHE.get(normalized);
        if (cached != null) {
            return cached;
        }

        StructureWorldGenLibConfig.DistanceScope resolved;
        if ("all".equals(normalized) || "all_structures".equals(normalized)) {
            resolved = StructureWorldGenLibConfig.DistanceScope.ALL;
        } else if ("same_name".equals(normalized)
                || "same".equals(normalized)
                || "same_structure".equals(normalized)) {
            resolved = StructureWorldGenLibConfig.DistanceScope.SAME_NAME;
        } else {
            resolved = StructureWorldGenLibConfig.DistanceScope.ALL;
            if (WARNED_DISTANCE_SCOPE_VALUES.size() >= WARNED_DISTANCE_SCOPE_VALUES_MAX_SIZE) {
                evictOneSetEntry(WARNED_DISTANCE_SCOPE_VALUES);
            }
            if (WARNED_DISTANCE_SCOPE_VALUES.add(normalized)) {
                if (warnLimiter != null) {
                    warnLimiter.warn(
                            "Unknown distanceScope '{}' in config entry[{}], fallback to 'all'",
                            value,
                            entryIndex);
                } else {
                    WorldGenLib.LOGGER.warn(
                            "Unknown distanceScope '{}' in config entry[{}], fallback to 'all'",
                            value,
                            entryIndex);
                }
            }
        }

        if (PARSED_DISTANCE_SCOPE_CACHE.size() >= PARSED_DISTANCE_SCOPE_CACHE_MAX_SIZE) {
            evictOneMapEntry(PARSED_DISTANCE_SCOPE_CACHE);
        }
        StructureWorldGenLibConfig.DistanceScope previous =
                PARSED_DISTANCE_SCOPE_CACHE.putIfAbsent(normalized, resolved);
        return previous != null ? previous : resolved;
    }

    private static File resolveSchematicsBaseDir(String configuredDir, File configFile) {
        String normalizedDir = StringNormalization.trimToNull(configuredDir);
        normalizedDir = normalizedDir == null ? DEFAULT_SCHEMATICS_DIR_NAME : normalizedDir;

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
        return StringNormalization.normalizePath(path);
    }

    private static String resolveConfiguredStructureSource(JsonObject entry, String normalizedSchematicPath) {
        if (entry != null) {
            String explicitSource = getString(entry, SOURCE_MOD_KEY, null);
            if (explicitSource == null) {
                explicitSource = getString(entry, MOD_ID_ALIAS_KEY, null);
            }
            String normalizedExplicitSource = normalizeSourceMod(explicitSource);
            if (normalizedExplicitSource != null) {
                return normalizedExplicitSource;
            }
        }
        return StringNormalization.extractModIdFromAssetsPath(normalizedSchematicPath);
    }

    private static String normalizeSourceMod(String value) {
        return StringNormalization.trimLowerToNull(value);
    }

    private static String extractStructureName(String normalizedPath) {
        if (normalizedPath == null || normalizedPath.isEmpty()) {
            return UNKNOWN_STRUCTURE_NAME;
        }

        String name = normalizedPath;
        int slash = name.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < name.length()) {
            name = name.substring(slash + 1);
        }
        if (hasSchematicSuffixIgnoreCase(name)) {
            name = name.substring(0, name.length() - SCHEMATIC_SUFFIX_LENGTH);
        }
        return name.isEmpty() ? UNKNOWN_STRUCTURE_NAME : name;
    }

    private static boolean hasSchematicSuffixIgnoreCase(String value) {
        if (value == null) {
            return false;
        }
        int length = value.length();
        return length >= SCHEMATIC_SUFFIX_LENGTH
                && value.regionMatches(
                        true,
                        length - SCHEMATIC_SUFFIX_LENGTH,
                        SCHEMATIC_SUFFIX,
                        0,
                        SCHEMATIC_SUFFIX_LENGTH);
    }

    private static String resolveSchematicPath(String path, File schematicsBaseDir) {
        if (path.startsWith("classpath:")) {
            String classpathPath = StringNormalization.trimToNull(path.substring("classpath:".length()));
            if (classpathPath == null) {
                classpathPath = "";
            }
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

    private static String resolveSchematicPathCached(
            String path,
            File schematicsBaseDir,
            Map<PathResolveCacheKey, String> pathCache) {
        if (pathCache == null) {
            return resolveSchematicPath(path, schematicsBaseDir);
        }

        PathResolveCacheKey cacheKey = new PathResolveCacheKey(schematicsBaseDir, path);
        String cached = pathCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        String resolved = resolveSchematicPath(path, schematicsBaseDir);
        pathCache.put(cacheKey, resolved);
        return resolved;
    }

    private static boolean isSchematicAvailableCached(String resolvedPath, Map<String, Boolean> availabilityCache) {
        if (availabilityCache == null) {
            return isSchematicAvailable(resolvedPath);
        }

        String normalized = normalizePath(resolvedPath);
        if (normalized == null) {
            return false;
        }

        Boolean cached = availabilityCache.get(normalized);
        if (cached != null) {
            return cached.booleanValue();
        }

        boolean available = isSchematicAvailableNormalized(normalized);
        availabilityCache.put(normalized, available);
        return available;
    }

    private static boolean isSchematicAvailable(String resolvedPath) {
        String normalized = normalizePath(resolvedPath);
        if (normalized == null) {
            return false;
        }
        return isSchematicAvailableNormalized(normalized);
    }

    private static boolean isSchematicAvailableNormalized(String normalizedPath) {
        if (normalizedPath == null || normalizedPath.isEmpty()) {
            return false;
        }
        if (normalizedPath.startsWith("/")) {
            return StructureWorldGenLibPlayerConfigLoader.class.getResource(normalizedPath) != null;
        }
        return new File(normalizedPath).isFile();
    }

    private static <K, V> void evictOneMapEntry(Map<K, V> map) {
        if (map == null || map.isEmpty()) {
            return;
        }
        Iterator<K> iterator = map.keySet().iterator();
        if (!iterator.hasNext()) {
            return;
        }
        K key = iterator.next();
        map.remove(key);
    }

    private static <T> void evictOneSetEntry(Set<T> set) {
        if (set == null || set.isEmpty()) {
            return;
        }
        Iterator<T> iterator = set.iterator();
        if (!iterator.hasNext()) {
            return;
        }
        T value = iterator.next();
        set.remove(value);
    }

    private static final class ConfiguredStructureIdentityKey {
        private final String sourceMod;
        private final String normalizedName;

        private ConfiguredStructureIdentityKey(String sourceMod, String structureName) {
            this.sourceMod = sourceMod == null ? "" : sourceMod;
            String normalized = StringNormalization.trimLowerToNull(structureName);
            this.normalizedName = normalized == null ? "" : normalized;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ConfiguredStructureIdentityKey)) {
                return false;
            }
            ConfiguredStructureIdentityKey other = (ConfiguredStructureIdentityKey) obj;
            return this.sourceMod.equals(other.sourceMod) && this.normalizedName.equals(other.normalizedName);
        }

        @Override
        public int hashCode() {
            int result = this.sourceMod.hashCode();
            result = 31 * result + this.normalizedName.hashCode();
            return result;
        }
    }

    private static final class PathResolveCacheKey {
        private final String baseDirPath;
        private final String path;

        private PathResolveCacheKey(File schematicsBaseDir, String path) {
            this.baseDirPath = schematicsBaseDir == null ? "" : schematicsBaseDir.getPath();
            this.path = path == null ? "" : path;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof PathResolveCacheKey)) {
                return false;
            }
            PathResolveCacheKey other = (PathResolveCacheKey) obj;
            return this.baseDirPath.equals(other.baseDirPath) && this.path.equals(other.path);
        }

        @Override
        public int hashCode() {
            int result = this.baseDirPath.hashCode();
            result = 31 * result + this.path.hashCode();
            return result;
        }
    }

    private static final class CachedRootObject {
        private final long lastModified;
        private final long length;
        private final JsonObject root;

        private CachedRootObject(long lastModified, long length, JsonObject root) {
            this.lastModified = lastModified;
            this.length = length;
            this.root = root;
        }

        private boolean matches(long currentLastModified, long currentLength) {
            return this.lastModified == currentLastModified && this.length == currentLength;
        }

        private JsonObject root() {
            return this.root;
        }
    }

    private static final class WarnLimiter {
        private final int detailLimit;
        private int emitted;
        private int suppressed;

        private WarnLimiter(int detailLimit) {
            this.detailLimit = Math.max(1, detailLimit);
        }

        private void warn(String format, Object... args) {
            if (this.emitted < this.detailLimit) {
                this.emitted++;
                WorldGenLib.LOGGER.warn(format, args);
                return;
            }
            this.suppressed++;
        }

        private void logSummary(String scope, String sourcePath) {
            if (this.suppressed <= 0) {
                return;
            }
            WorldGenLib.LOGGER.warn(
                    "Suppressed {} additional {} for '{}' (first {} shown)",
                    this.suppressed,
                    scope == null ? "warnings" : scope,
                    sourcePath == null ? "unknown" : sourcePath,
                    this.detailLimit);
        }
    }
}
