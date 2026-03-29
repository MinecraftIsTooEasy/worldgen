package com.github.hahahha.WorldGenLib.world.structure.api;

import com.github.hahahha.WorldGenLib.util.LruCache;
import com.github.hahahha.WorldGenLib.world.structure.config.StructureWorldGenLibPlayerConfigLoader;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import moddedmite.rustedironcore.api.event.events.BiomeDecorationRegisterEvent;

/**
 * Optional config-file entrypoint for structure WorldGenLib registration.
 *
 * <p>This class does not change existing API usage. Mod developers can continue to call {@link StructureWorldGenLibApi},
 * and can also reuse this file-based entrypoint when needed.
 */
public final class StructureWorldGenLibConfigFileApi {
    private static final int OPTION_CACHE_MAX_SIZE = 64;
    private static final File DEFAULT_CONFIG_FILE =
            new File("config", StructureWorldGenLibPlayerConfigLoader.DEFAULT_FILE_NAME);
    private static final LruCache<ListConfiguredStructuresCacheKey, List<ConfiguredStructureOption>> OPTION_CACHE =
            new LruCache<ListConfiguredStructuresCacheKey, List<ConfiguredStructureOption>>(OPTION_CACHE_MAX_SIZE);

    private StructureWorldGenLibConfigFileApi() {
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

    /**
     * Loads and registers structure entries from {@code config/WorldGenLib-structures.jsonc}.
     *
     * @return number of registered entries
     */
    public static int registerFromDefaultConfig(BiomeDecorationRegisterEvent event) {
        return StructureWorldGenLibPlayerConfigLoader.registerFromDefaultConfig(event);
    }

    /**
     * Loads and registers structure entries from a custom config file.
     *
     * @return number of registered entries
     */
    public static int registerFromFile(BiomeDecorationRegisterEvent event, File configFile) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(configFile, "configFile");
        return StructureWorldGenLibPlayerConfigLoader.registerFromFile(event, configFile);
    }

    /**
     * Lists configured structure names from {@code config/WorldGenLib-structures.jsonc}.
     */
    public static List<String> listConfiguredStructureNames(int maxEntries) {
        return StructureWorldGenLibPlayerConfigLoader.listConfiguredStructureNames(maxEntries);
    }

    /**
     * Lists configured structure names from a custom config file.
     */
    public static List<String> listConfiguredStructureNames(File configFile, int maxEntries) {
        Objects.requireNonNull(configFile, "configFile");
        return StructureWorldGenLibPlayerConfigLoader.listConfiguredStructureNames(configFile, maxEntries);
    }

    /**
     * Lists configured structure names from {@code config/WorldGenLib-structures.jsonc}, optionally filtered by dimension id.
     */
    public static List<String> listConfiguredStructureNames(int maxEntries, Integer dimensionId) {
        return StructureWorldGenLibPlayerConfigLoader.listConfiguredStructureNames(maxEntries, dimensionId);
    }

    /**
     * Lists configured structure names from a custom config file, optionally filtered by dimension id.
     */
    public static List<String> listConfiguredStructureNames(File configFile, int maxEntries, Integer dimensionId) {
        Objects.requireNonNull(configFile, "configFile");
        return StructureWorldGenLibPlayerConfigLoader.listConfiguredStructureNames(configFile, maxEntries, dimensionId);
    }

    /**
     * Lists configured structure options from {@code config/WorldGenLib-structures.jsonc}.
     */
    public static List<ConfiguredStructureOption> listConfiguredStructures(int maxEntries) {
        return listConfiguredStructures(DEFAULT_CONFIG_FILE, maxEntries, null);
    }

    /**
     * Lists configured structure options from a custom config file.
     */
    public static List<ConfiguredStructureOption> listConfiguredStructures(File configFile, int maxEntries) {
        Objects.requireNonNull(configFile, "configFile");
        return listConfiguredStructures(configFile, maxEntries, null);
    }

    /**
     * Lists configured structure options from {@code config/WorldGenLib-structures.jsonc}, optionally filtered by dimension id.
     */
    public static List<ConfiguredStructureOption> listConfiguredStructures(int maxEntries, Integer dimensionId) {
        return listConfiguredStructures(DEFAULT_CONFIG_FILE, maxEntries, dimensionId);
    }

    /**
     * Lists configured structure options from a custom config file, optionally filtered by dimension id.
     */
    public static List<ConfiguredStructureOption> listConfiguredStructures(
            File configFile, int maxEntries, Integer dimensionId) {
        Objects.requireNonNull(configFile, "configFile");
        return listConfiguredStructuresCached(configFile, maxEntries, dimensionId);
    }

    private static List<ConfiguredStructureOption> listConfiguredStructuresCached(
            File configFile, int maxEntries, Integer dimensionId) {
        if (maxEntries < 1) {
            return Collections.emptyList();
        }

        File absoluteConfigFile = configFile.getAbsoluteFile();
        boolean configExists = absoluteConfigFile.isFile();
        long lastModified = configExists ? absoluteConfigFile.lastModified() : -1L;
        long length = configExists ? absoluteConfigFile.length() : -1L;
        ListConfiguredStructuresCacheKey cacheKey = new ListConfiguredStructuresCacheKey(
                absoluteConfigFile.getPath(),
                lastModified,
                length,
                maxEntries,
                dimensionId);
        List<ConfiguredStructureOption> cached = OPTION_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<ConfiguredStructureOption> mapped = mapConfiguredStructureOptions(
                StructureWorldGenLibPlayerConfigLoader.listConfiguredStructures(
                        absoluteConfigFile,
                        maxEntries,
                        dimensionId));
        List<ConfiguredStructureOption> previous = OPTION_CACHE.putIfAbsent(cacheKey, mapped);
        return previous == null ? mapped : previous;
    }

    private static List<ConfiguredStructureOption> mapConfiguredStructureOptions(
            List<StructureWorldGenLibPlayerConfigLoader.ConfiguredStructureOption> options) {
        if (options == null || options.isEmpty()) {
            return Collections.emptyList();
        }

        List<ConfiguredStructureOption> mapped = new ArrayList<ConfiguredStructureOption>(options.size());
        for (StructureWorldGenLibPlayerConfigLoader.ConfiguredStructureOption option : options) {
            if (option == null) {
                continue;
            }
            mapped.add(new ConfiguredStructureOption(option.structureName(), option.sourceMod()));
        }
        if (mapped.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(mapped);
    }

    private static final class ListConfiguredStructuresCacheKey {
        private final String configPath;
        private final long lastModified;
        private final long length;
        private final int maxEntries;
        private final Integer dimensionId;

        private ListConfiguredStructuresCacheKey(
                String configPath,
                long lastModified,
                long length,
                int maxEntries,
                Integer dimensionId) {
            this.configPath = configPath == null ? "" : configPath;
            this.lastModified = lastModified;
            this.length = length;
            this.maxEntries = maxEntries;
            this.dimensionId = dimensionId;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ListConfiguredStructuresCacheKey)) {
                return false;
            }
            ListConfiguredStructuresCacheKey other = (ListConfiguredStructuresCacheKey) obj;
            return this.lastModified == other.lastModified
                    && this.length == other.length
                    && this.maxEntries == other.maxEntries
                    && this.configPath.equals(other.configPath)
                    && Objects.equals(this.dimensionId, other.dimensionId);
        }

        @Override
        public int hashCode() {
            int result = this.configPath.hashCode();
            result = 31 * result + (int) (this.lastModified ^ (this.lastModified >>> 32));
            result = 31 * result + (int) (this.length ^ (this.length >>> 32));
            result = 31 * result + this.maxEntries;
            result = 31 * result + (this.dimensionId == null ? 0 : this.dimensionId.hashCode());
            return result;
        }
    }
}
