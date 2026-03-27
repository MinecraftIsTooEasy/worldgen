package com.github.hahahha.WorldGen.world.structure.api;

import com.github.hahahha.WorldGen.world.structure.StructureLootProfile;
import com.github.hahahha.WorldGen.world.structure.StructureLootProfiles;
import net.minecraft.Item;
import net.minecraft.WeightedRandomChestContent;

/**
 * Public loot-profile API for marker chest generation.
 *
 * <p>Typical usage:
 *
 * <pre>{@code
 * StructureLootProfile loot = StructureLootApi.builder("my_loot")
 *         .marker(Item.stick, 1)
 *         .level(
 *                 1,
 *                 2,
 *                 4,
 *                 new WeightedRandomChestContent[]{
 *                         StructureLootApi.entry(Item.coal, 0, 1, 3, 20)
 *                 })
 *         .build();
 * }</pre>
 */
public final class StructureLootApi {
    private StructureLootApi() {
    }

    public static LootProfileBuilder builder(String id) {
        return new LootProfileBuilder(id);
    }

    public static StructureLootProfile defaultProfile() {
        return StructureLootProfiles.defaultProfile();
    }

    public static WeightedRandomChestContent entry(
            Item item,
            int meta,
            int minCount,
            int maxCount,
            int weight) {
        if (item == null) {
            throw new IllegalArgumentException("item cannot be null");
        }
        return entry(item.itemID, meta, minCount, maxCount, weight);
    }

    public static WeightedRandomChestContent entry(
            int itemId,
            int meta,
            int minCount,
            int maxCount,
            int weight) {
        if (itemId <= 0) {
            throw new IllegalArgumentException("itemId must be > 0");
        }
        int safeMeta = Math.max(0, meta);
        int safeMin = Math.max(1, minCount);
        int safeMax = Math.max(safeMin, maxCount);
        int safeWeight = Math.max(1, weight);
        return new WeightedRandomChestContent(itemId, safeMeta, safeMin, safeMax, safeWeight);
    }

    public static final class LootProfileBuilder {
        private final StructureLootProfiles.Builder delegate;

        private LootProfileBuilder(String id) {
            this.delegate = StructureLootProfiles.builder(id);
        }

        public LootProfileBuilder marker(Item item, int level) {
            if (item == null) {
                throw new IllegalArgumentException("item cannot be null");
            }
            this.delegate.marker(item.itemID, level);
            return this;
        }

        public LootProfileBuilder marker(int itemId, int level) {
            this.delegate.marker(itemId, level);
            return this;
        }

        public LootProfileBuilder level(
                int level,
                int minRolls,
                int maxRolls,
                WeightedRandomChestContent[] entries) {
            this.delegate.level(level, minRolls, maxRolls, entries, null);
            return this;
        }

        public LootProfileBuilder level(
                int level,
                int minRolls,
                int maxRolls,
                WeightedRandomChestContent[] entries,
                float[] artifactChances) {
            this.delegate.level(level, minRolls, maxRolls, entries, artifactChances);
            return this;
        }

        public StructureLootProfile build() {
            return this.delegate.build();
        }
    }
}
