/**
 * Pure API worldgen registration example for test10.schematic.
 */

/**package com.github.hahahha.WorldGen.world.structure.example;

import com.github.hahahha.WorldGen.world.structure.StructureLootProfile;
import com.github.hahahha.WorldGen.world.structure.api.StructureLootApi;
import com.github.hahahha.WorldGen.world.structure.api.StructureWorldgenApi;
import com.github.hahahha.WorldGen.world.structure.api.StructureWorldgenConfig;
import moddedmite.rustedironcore.api.event.events.BiomeDecorationRegisterEvent;
import moddedmite.rustedironcore.api.world.Dimension;
import net.minecraft.Item;
import net.minecraft.WeightedRandomChestContent;


public final class Test10ApiStructureRegistration {
    private static final String TEST10_SCHEMATIC_PATH = "/assets/worldgen/structures/test10.schematic";

    private static final StructureLootProfile TEST10_LOOT_PROFILE = StructureLootApi.builder("test10_loot")
            .marker(Item.stick, 1)
            .marker(Item.diamond, 6)
            .level(
                    1,
                    3,
                    5,
                    new WeightedRandomChestContent[]{
                            StructureLootApi.entry(Item.coal, 0, 1, 3, 20),
                            StructureLootApi.entry(Item.flint, 0, 1, 2, 15)
                    })
            .level(
                    6,
                    6,
                    9,
                    new WeightedRandomChestContent[]{
                            StructureLootApi.entry(Item.diamond, 0, 1, 2, 10),
                            StructureLootApi.entry(Item.ingotGold, 0, 2, 5, 18)
                    })
            .build();

    private Test10ApiStructureRegistration() {
    }

    public static void register(BiomeDecorationRegisterEvent event) {
        StructureWorldgenConfig config = StructureWorldgenApi.builder(TEST10_SCHEMATIC_PATH)
                .structureName("test10")
                .dimension(Dimension.OVERWORLD)
                .weight(1)
                .chance(40)
                .attempts(1)
                .surface(true)
                .yRange(0, 255)
                .yOffset(0)
                .centerOnAnchor(true)
                .minDistance(0)
                .distanceScope(StructureWorldgenConfig.DistanceScope.ALL)
                .lootTableEnabled(true)
                .lootProfile(TEST10_LOOT_PROFILE)
                .build();
        StructureWorldgenApi.register(event, config);
    }
}*/
