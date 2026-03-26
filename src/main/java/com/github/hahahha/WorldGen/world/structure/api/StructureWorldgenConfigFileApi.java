package com.github.hahahha.WorldGen.world.structure.api;

import com.github.hahahha.WorldGen.world.structure.config.StructureWorldgenPlayerConfigLoader;
import java.io.File;
import java.util.Objects;
import moddedmite.rustedironcore.api.event.events.BiomeDecorationRegisterEvent;

/**
 * Optional config-file entrypoint for structure worldgen registration.
 *
 * <p>This class does not change existing API usage. Mod developers can continue to call {@link StructureWorldgenApi},
 * and can also reuse this file-based entrypoint when needed.
 */
public final class StructureWorldgenConfigFileApi {
    private StructureWorldgenConfigFileApi() {
    }

    /**
     * Loads and registers structure entries from {@code config/worldgen-structures.jsonc}.
     *
     * @return number of registered entries
     */
    public static int registerFromDefaultConfig(BiomeDecorationRegisterEvent event) {
        return StructureWorldgenPlayerConfigLoader.registerFromDefaultConfig(event);
    }

    /**
     * Loads and registers structure entries from a custom config file.
     *
     * @return number of registered entries
     */
    public static int registerFromFile(BiomeDecorationRegisterEvent event, File configFile) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(configFile, "configFile");
        return StructureWorldgenPlayerConfigLoader.registerFromFile(event, configFile);
    }
}
