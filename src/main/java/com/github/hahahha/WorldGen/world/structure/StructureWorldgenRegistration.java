package com.github.hahahha.WorldGen.world.structure;

import com.github.hahahha.WorldGen.world.structure.api.StructureWorldgenApi;
import com.github.hahahha.WorldGen.world.structure.api.StructureWorldgenConfig;
import moddedmite.rustedironcore.api.event.events.BiomeDecorationRegisterEvent;
import moddedmite.rustedironcore.api.world.Dimension;

/**
 * 模组内置结构注册入口。
 */
public final class StructureWorldgenRegistration {
    private static final boolean ENABLE_BUILTIN_EXAMPLE = true;
    private static final String EXAMPLE_PATH = "/assets/worldgen/structures/test1.schematic";

    private StructureWorldgenRegistration() {
    }

    public static void register(BiomeDecorationRegisterEvent event) {
        if (!ENABLE_BUILTIN_EXAMPLE) {
            return;
        }

        StructureWorldgenApi.register(
                event,
                StructureWorldgenConfig.builder(EXAMPLE_PATH)
                        .dimension(Dimension.OVERWORLD)
                        .weight(1)
                        .chance(10)
                        .attempts(2)
                        .surface(true)
                        .build());
    }
}
