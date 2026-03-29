package com.github.hahahha.WorldGenLib.world.structure;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.WeightedRandomChestContent;

public final class StructureLootProfile {
    private final String id;
    private final Map<Integer, Integer> markerLevels;
    private final Map<Integer, LootLevel> levels;

    StructureLootProfile(
            String id,
            Map<Integer, Integer> markerLevels,
            Map<Integer, LootLevel> levels) {
        this.id = id;
        this.markerLevels = Collections.unmodifiableMap(new LinkedHashMap<Integer, Integer>(markerLevels));
        this.levels = Collections.unmodifiableMap(new LinkedHashMap<Integer, LootLevel>(levels));
    }

    public String id() {
        return this.id;
    }

    Integer markerLevelOf(int itemId) {
        return this.markerLevels.get(itemId);
    }

    LootLevel levelData(int level) {
        return this.levels.get(level);
    }

    static final class LootLevel {
        private final int minRolls;
        private final int maxRolls;
        private final WeightedRandomChestContent[] contents;
        private final float[] artifactChances;

        LootLevel(
                int minRolls,
                int maxRolls,
                WeightedRandomChestContent[] contents,
                float[] artifactChances) {
            this.minRolls = Math.max(0, minRolls);
            this.maxRolls = Math.max(this.minRolls, maxRolls);
            this.contents = contents == null ? new WeightedRandomChestContent[0] : contents.clone();
            this.artifactChances = artifactChances == null ? null : artifactChances.clone();
        }

        int minRolls() {
            return this.minRolls;
        }

        int maxRolls() {
            return this.maxRolls;
        }

        WeightedRandomChestContent[] contents() {
            return this.contents;
        }

        float[] artifactChances() {
            return this.artifactChances;
        }
    }
}
