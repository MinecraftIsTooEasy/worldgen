package com.github.hahahha.WorldGen.world.structure;

import com.github.hahahha.WorldGen.world.structure.api.StructureWorldgenConfigFileApi;
import moddedmite.rustedironcore.api.event.events.BiomeDecorationRegisterEvent;

/**
 * Module-owned registration entrypoint.
 *
 * <p>By default, structures are only loaded from player config.
 * No hardcoded example structure is registered.
 */
public final class StructureWorldgenRegistration {
    private StructureWorldgenRegistration() {
    }

    public static void register(BiomeDecorationRegisterEvent event) {
        StructureWorldgenConfigFileApi.registerFromDefaultConfig(event);
    }
}
