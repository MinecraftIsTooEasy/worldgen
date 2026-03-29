package com.github.hahahha.WorldGenLib.world.structure;

import com.github.hahahha.WorldGenLib.WorldGenLib;
import com.github.hahahha.WorldGenLib.world.structure.example.Test10ApiStructureRegistration;
import com.github.hahahha.WorldGenLib.world.structure.example.Test10ExampleSwitch;
import moddedmite.rustedironcore.api.event.events.BiomeDecorationRegisterEvent;

/**
 * Module-owned registration entrypoint.
 *
 * <p>Registers example structure(s) through API only.
 */
public final class StructureWorldGenLibRegistration {
    private static volatile boolean test10DisabledLogged;

    private StructureWorldGenLibRegistration() {
    }

    public static void register(BiomeDecorationRegisterEvent event) {
        if (Test10ExampleSwitch.isEnabled()) {
            Test10ApiStructureRegistration.register(event);
        } else {
            if (test10DisabledLogged) {
                return;
            }
            test10DisabledLogged = true;
            WorldGenLib.LOGGER.info(
                    "Skip example structure test10: disabled by config {} ({})",
                    Test10ExampleSwitch.configFile().getPath(),
                    Test10ExampleSwitch.CONFIG_KEY_PATH);
        }
    }
}
