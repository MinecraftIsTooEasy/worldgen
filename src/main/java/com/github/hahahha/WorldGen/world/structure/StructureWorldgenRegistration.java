package com.github.hahahha.WorldGen.world.structure;

import com.github.hahahha.WorldGen.world.structure.example.Test10ApiStructureRegistration;
import moddedmite.rustedironcore.api.event.events.BiomeDecorationRegisterEvent;

/**
 * Module-owned registration entrypoint.
 *
 * <p>Registers example structure(s) through API only.
 */
public final class StructureWorldgenRegistration {
    private StructureWorldgenRegistration() {
    }

    public static void register(BiomeDecorationRegisterEvent event) {
        Test10ApiStructureRegistration.register(event);
    }
}
