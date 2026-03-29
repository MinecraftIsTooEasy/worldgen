/**
 * Pure API WorldGenLib registration example for test10.schematic.
 *
 * <p>This version keeps the example intentionally small and deterministic:
 * a single namespaced key per item.
 */
package com.github.hahahha.WorldGenLib.world.structure.example;

import com.github.hahahha.WorldGenLib.WorldGenLib;
import com.github.hahahha.WorldGenLib.world.structure.StructureEntityReplacementProfile;
import com.github.hahahha.WorldGenLib.world.structure.StructureLootProfile;
import com.github.hahahha.WorldGenLib.world.structure.api.StructureEntityReplacementApi;
import com.github.hahahha.WorldGenLib.world.structure.api.StructureLootApi;
import com.github.hahahha.WorldGenLib.world.structure.api.StructureWorldGenLibApi;
import com.github.hahahha.WorldGenLib.world.structure.api.StructureWorldGenLibConfig;
import moddedmite.rustedironcore.api.event.events.BiomeDecorationRegisterEvent;
import moddedmite.rustedironcore.api.world.Dimension;
import net.minecraft.ItemStack;
import net.minecraft.WeightedRandomChestContent;

public final class Test10ApiStructureRegistration {
    private static final String TEST10_SCHEMATIC_PATH = "/assets/worldgenlib/structures/test10.schematic";
    private static final String TEST10_MAIN_HAND_ITEM = "minecraft:swordIron";
    private static final String TEST10_HELMET_ITEM = "minecraft:helmetIron";
    private static final String TEST10_DROP_ITEM = "minecraft:ingotIron";

    private static final StructureLootProfile TEST10_LOOT_PROFILE = createLootProfile();
    // Build once on demand: avoid resolving item aliases too early during registration phase.
    private static volatile StructureEntityReplacementProfile cachedEntityReplacementProfile;

    private Test10ApiStructureRegistration() {
    }

    private static StructureLootProfile createLootProfile() {
        try {
            return StructureLootApi.builder("test10_loot")
                    .marker("minecraft:stick", 1)
                    .marker("minecraft:diamond", 6)
                    .level(
                            1,
                            3,
                            5,
                            new WeightedRandomChestContent[]{
                                    StructureLootApi.entry("minecraft:coal", 0, 1, 3, 20),
                                    StructureLootApi.entry("minecraft:stick", 0, 1, 2, 15)
                            })
                    .level(
                            6,
                            6,
                            9,
                            new WeightedRandomChestContent[]{
                                    StructureLootApi.entry("minecraft:diamond", 0, 1, 2, 10),
                                    StructureLootApi.entry("minecraft:ingotGold", 0, 2, 5, 18)
                            })
                    .build();
        } catch (RuntimeException e) {
            WorldGenLib.LOGGER.warn("Test10 example loot profile build failed, fallback to default profile", e);
            return StructureLootApi.defaultProfile();
        }
    }

    private static StructureEntityReplacementProfile createEntityReplacementProfile() {
        try {
            StructureEntityReplacementApi.EntityReplacementProfileBuilder builder =
                    StructureEntityReplacementApi.builder("test10_entity")
                            .level(1, "Zombie")
                            .level(2, "Skeleton")
                            .level(3, "Spider");

            ItemStack mainHand = tryResolveStack(TEST10_MAIN_HAND_ITEM, 1, 0);
            if (mainHand != null) {
                builder.mainHand(2, mainHand);
            } else {
                WorldGenLib.LOGGER.warn("Test10 example skipped optional item '{}'", TEST10_MAIN_HAND_ITEM);
            }

            ItemStack helmet = tryResolveStack(TEST10_HELMET_ITEM, 1, 0);
            if (helmet != null) {
                builder.helmet(2, helmet);
            } else {
                WorldGenLib.LOGGER.warn("Test10 example skipped optional item '{}'", TEST10_HELMET_ITEM);
            }

            StructureEntityReplacementProfile.EntityDrop drop = tryResolveDrop(TEST10_DROP_ITEM, 0, 1, 1, 1.0F);
            if (drop != null) {
                builder.drop(2, drop);
            } else {
                WorldGenLib.LOGGER.warn("Test10 example skipped optional drop '{}'", TEST10_DROP_ITEM);
            }

            return builder.build();
        } catch (RuntimeException e) {
            WorldGenLib.LOGGER.warn(
                    "Test10 example entity-replacement profile build failed, fallback to default profile",
                    e);
            return StructureEntityReplacementApi.defaultProfile();
        }
    }

    private static ItemStack tryResolveStack(String itemName, int count, int meta) {
        if (itemName == null || itemName.trim().isEmpty()) {
            return null;
        }
        try {
            return StructureEntityReplacementApi.stack(itemName, count, meta);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static StructureEntityReplacementProfile.EntityDrop tryResolveDrop(
            String itemName,
            int meta,
            int minCount,
            int maxCount,
            float chance) {
        if (itemName == null || itemName.trim().isEmpty()) {
            return null;
        }
        try {
            return StructureEntityReplacementApi.drop(itemName, meta, minCount, maxCount, chance);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static StructureEntityReplacementProfile getEntityReplacementProfile() {
        StructureEntityReplacementProfile cached = cachedEntityReplacementProfile;
        if (cached != null) {
            return cached;
        }
        synchronized (Test10ApiStructureRegistration.class) {
            cached = cachedEntityReplacementProfile;
            if (cached != null) {
                return cached;
            }
            StructureEntityReplacementProfile created = createEntityReplacementProfile();
            cachedEntityReplacementProfile = created;
            return created;
        }
    }

    public static void register(BiomeDecorationRegisterEvent event) {
        StructureWorldGenLibConfig config = StructureWorldGenLibApi.builder(TEST10_SCHEMATIC_PATH)
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
                .distanceScope(StructureWorldGenLibConfig.DistanceScope.ALL)
                .lootTableEnabled(true)
                .lootProfile(TEST10_LOOT_PROFILE)
                .entityReplacementEnabled(true)
                // Resolve replacement profile lazily at generation time.
                .entityReplacementProfileSupplier("test10_entity", Test10ApiStructureRegistration::getEntityReplacementProfile)
                .build();
        StructureWorldGenLibApi.register(event, config);
    }
}
