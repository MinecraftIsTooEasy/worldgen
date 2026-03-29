package com.github.hahahha.WorldGenLib.world.structure.api;

import com.github.hahahha.WorldGenLib.WorldGenLib;
import com.github.hahahha.WorldGenLib.util.StringNormalization;
import com.github.hahahha.WorldGenLib.world.structure.SchematicStructureGenerator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import moddedmite.rustedironcore.api.event.events.BiomeDecorationRegisterEvent;
import moddedmite.rustedironcore.api.event.handler.BiomeDecorationHandler;
import moddedmite.rustedironcore.api.world.Dimension;

public final class StructureWorldGenLibApi {
    private static final String SCHEMATIC_SUFFIX = ".schematic";
    private static final int SCHEMATIC_SUFFIX_LENGTH = SCHEMATIC_SUFFIX.length();
    private static final LinkedHashMap<RegisteredStructureKey, RegisteredStructureOption> REGISTERED_STRUCTURES =
            new LinkedHashMap<RegisteredStructureKey, RegisteredStructureOption>(64);

    private StructureWorldGenLibApi() {
    }

    public static StructureWorldGenLibConfig.Builder builder(String schematicPath) {
        return StructureWorldGenLibConfig.builder(schematicPath);
    }

    public static void register(BiomeDecorationRegisterEvent event, String schematicPath) {
        register(event, builder(schematicPath).build());
    }

    public static void register(BiomeDecorationRegisterEvent event, StructureWorldGenLibConfig config) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(config, "config");

        SchematicStructureGenerator generator = new SchematicStructureGenerator(config);
        int effectiveAttempts = computeEffectiveAttempts(config.attempts(), config.weight());
        BiomeDecorationHandler.SettingBuilder settingBuilder = event
                .register(config.dimension(), generator)
                .setAttempts(effectiveAttempts)
                .setChance(config.chance());

        if (config.surface()) {
            settingBuilder.setSurface();
        } else {
            settingBuilder.setHeightSupplier(
                    (context, x, z) -> randomY(context.rand(), config.minY(), config.maxY()));
        }

        if (config.biomeFilter() != null) {
            settingBuilder.requiresBiome(config.biomeFilter());
        }

        rememberRegisteredStructure(config);
        WorldGenLib.LOGGER.info(
                "Registered structure WorldGenLib path={} name={} dim={} weight={} chance=1/{} attempts={} effectiveAttempts={} surface={} y={}..{} yOffset={} centerOnAnchor={} minDistance={} distanceScope={} lootTableEnabled={} lootProfile={} entityReplacementEnabled={} entityReplacementProfile={}",
                config.schematicPath(),
                config.structureName(),
                config.dimension(),
                config.weight(),
                config.chance(),
                config.attempts(),
                effectiveAttempts,
                config.surface(),
                config.minY(),
                config.maxY(),
                config.yOffset(),
                config.centerOnAnchor(),
                config.minDistance(),
                config.distanceScope(),
                config.lootTableEnabled(),
                config.lootProfile() == null ? "null" : config.lootProfile().id(),
                config.entityReplacementEnabled(),
                config.entityReplacementProfileIdForLog());
    }

    public static void register(
            BiomeDecorationRegisterEvent event,
            String schematicPath,
            Dimension dimension,
            int weight,
            int chance) {
        register(
                event,
                builder(schematicPath)
                        .dimension(dimension)
                        .weight(weight)
                        .chance(chance)
                        .build());
    }

    public static List<RegisteredStructureOption> listRegisteredStructures(int maxEntries) {
        return listRegisteredStructures(maxEntries, null);
    }

    public static List<RegisteredStructureOption> listRegisteredStructures(int maxEntries, Integer dimensionId) {
        if (maxEntries < 1) {
            return Collections.emptyList();
        }

        List<RegisteredStructureOption> options;
        synchronized (REGISTERED_STRUCTURES) {
            options = new ArrayList<RegisteredStructureOption>(Math.max(1, Math.min(maxEntries, REGISTERED_STRUCTURES.size())));
            for (RegisteredStructureOption option : REGISTERED_STRUCTURES.values()) {
                if (option == null || option.structureName() == null) {
                    continue;
                }
                if (dimensionId != null && option.dimensionId() != dimensionId.intValue()) {
                    continue;
                }
                options.add(option);
                if (options.size() >= maxEntries) {
                    break;
                }
            }
        }

        if (options.isEmpty()) {
            return Collections.emptyList();
        }
        return options;
    }

    public static List<String> listRegisteredStructureNames(int maxEntries) {
        return listRegisteredStructureNames(maxEntries, null);
    }

    public static List<String> listRegisteredStructureNames(int maxEntries, Integer dimensionId) {
        if (maxEntries < 1) {
            return Collections.emptyList();
        }

        List<RegisteredStructureOption> options = listRegisteredStructures(maxEntries, dimensionId);
        if (options.isEmpty()) {
            return Collections.emptyList();
        }

        LinkedHashSet<String> names =
                new LinkedHashSet<String>(Math.max(1, Math.min(maxEntries, options.size())));
        for (RegisteredStructureOption option : options) {
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

    private static void rememberRegisteredStructure(StructureWorldGenLibConfig config) {
        String structureName = resolveStructureName(config);
        if (structureName == null) {
            return;
        }

        String sourceMod = StringNormalization.extractModIdFromAssetsPath(config.schematicPath());
        int dimensionId = config.dimension() == null ? Integer.MIN_VALUE : config.dimension().id();
        RegisteredStructureKey uniqueKey = new RegisteredStructureKey(dimensionId, sourceMod, structureName);
        RegisteredStructureOption option = new RegisteredStructureOption(structureName, sourceMod, dimensionId);
        synchronized (REGISTERED_STRUCTURES) {
            REGISTERED_STRUCTURES.put(uniqueKey, option);
        }
    }

    private static String resolveStructureName(StructureWorldGenLibConfig config) {
        if (config == null) {
            return null;
        }
        String explicitName = normalizeValue(config.structureName());
        if (explicitName != null) {
            return explicitName;
        }

        String normalizedPath = normalizeValue(config.schematicPath());
        if (normalizedPath == null) {
            return null;
        }
        int slash = Math.max(normalizedPath.lastIndexOf('/'), normalizedPath.lastIndexOf('\\'));
        String fileName = slash >= 0 ? normalizedPath.substring(slash + 1) : normalizedPath;
        if (hasSchematicSuffixIgnoreCase(fileName)) {
            fileName = fileName.substring(0, fileName.length() - SCHEMATIC_SUFFIX_LENGTH);
        }
        return normalizeValue(fileName);
    }

    private static String normalizeValue(String value) {
        return StringNormalization.trimToNull(value);
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

    public static final class RegisteredStructureOption {
        private final String structureName;
        private final String sourceMod;
        private final int dimensionId;

        private RegisteredStructureOption(String structureName, String sourceMod, int dimensionId) {
            this.structureName = structureName;
            this.sourceMod = sourceMod;
            this.dimensionId = dimensionId;
        }

        public String structureName() {
            return this.structureName;
        }

        public String sourceMod() {
            return this.sourceMod;
        }

        public int dimensionId() {
            return this.dimensionId;
        }
    }

    private static final class RegisteredStructureKey {
        private final int dimensionId;
        private final String sourceMod;
        private final String normalizedName;

        private RegisteredStructureKey(int dimensionId, String sourceMod, String structureName) {
            this.dimensionId = dimensionId;
            this.sourceMod = sourceMod == null ? "" : sourceMod;
            String normalized = StringNormalization.trimLowerToNull(structureName);
            this.normalizedName = normalized == null ? "" : normalized;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof RegisteredStructureKey)) {
                return false;
            }
            RegisteredStructureKey other = (RegisteredStructureKey) obj;
            return this.dimensionId == other.dimensionId
                    && this.sourceMod.equals(other.sourceMod)
                    && this.normalizedName.equals(other.normalizedName);
        }

        @Override
        public int hashCode() {
            int result = this.dimensionId;
            result = 31 * result + this.sourceMod.hashCode();
            result = 31 * result + this.normalizedName.hashCode();
            return result;
        }
    }

    private static int randomY(Random random, int minY, int maxY) {
        if (minY >= maxY) {
            return minY;
        }
        return minY + random.nextInt(maxY - minY + 1);
    }

    private static int computeEffectiveAttempts(int attempts, int weight) {
        long product = (long) attempts * (long) weight;
        if (product <= 0L) {
            return 1;
        }
        if (product > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) product;
    }
}
