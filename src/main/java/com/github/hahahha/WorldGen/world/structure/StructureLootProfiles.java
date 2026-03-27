package com.github.hahahha.WorldGen.world.structure;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import net.minecraft.ItemStack;
import net.minecraft.WeightedRandomChestContent;

public final class StructureLootProfiles {
    public static final String DEFAULT_PROFILE_ID = "default";
    private static final WeightedRandomChestContent[] EMPTY = new WeightedRandomChestContent[0];
    private static final StructureLootProfile DEFAULT_PROFILE = createDefaultProfile();

    private StructureLootProfiles() {
    }

    public static StructureLootProfile defaultProfile() {
        return DEFAULT_PROFILE;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static String normalizeId(String id) {
        if (id == null) {
            return null;
        }
        String trimmed = id.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static int getLevelForMarker(ItemStack markerStack, StructureLootProfile profile) {
        if (markerStack == null || markerStack.getItem() == null) {
            return 0;
        }
        StructureLootProfile safeProfile = profile == null ? DEFAULT_PROFILE : profile;
        Integer level = safeProfile.markerLevelOf(markerStack.itemID);
        return level == null ? 0 : level.intValue();
    }

    public static WeightedRandomChestContent[] getContentsForLevel(int level, StructureLootProfile profile) {
        StructureLootProfile safeProfile = profile == null ? DEFAULT_PROFILE : profile;
        StructureLootProfile.LootLevel levelData = safeProfile.levelData(level);
        return levelData == null ? EMPTY : levelData.contents();
    }

    public static int getRollCount(Random random, int level, StructureLootProfile profile) {
        StructureLootProfile safeProfile = profile == null ? DEFAULT_PROFILE : profile;
        StructureLootProfile.LootLevel levelData = safeProfile.levelData(level);
        if (levelData == null) {
            return 0;
        }

        int min = levelData.minRolls();
        int max = levelData.maxRolls();
        if (max < min) {
            max = min;
        }
        if (random == null || max == min) {
            return min;
        }
        return min + random.nextInt(max - min + 1);
    }

    public static float[] getArtifactChances(int level, StructureLootProfile profile) {
        StructureLootProfile safeProfile = profile == null ? DEFAULT_PROFILE : profile;
        StructureLootProfile.LootLevel levelData = safeProfile.levelData(level);
        return levelData == null ? null : levelData.artifactChances();
    }

    private static StructureLootProfile createDefaultProfile() {
        Builder builder = builder(DEFAULT_PROFILE_ID);

        builder.marker(280, 1); // stick
        builder.marker(318, 2); // flint
        builder.marker(263, 3); // coal
        builder.marker(265, 4); // iron ingot
        builder.marker(266, 5); // gold ingot
        builder.marker(264, 6); // diamond

        builder.level(1, 3, 5, WeightedTreasurePieces.getContentsForLevel(1), null);
        builder.level(2, 3, 5, WeightedTreasurePieces.getContentsForLevel(2), null);
        builder.level(3, 4, 6, WeightedTreasurePieces.getContentsForLevel(3), null);
        builder.level(4, 4, 7, WeightedTreasurePieces.getContentsForLevel(4), null);
        builder.level(5, 5, 8, WeightedTreasurePieces.getContentsForLevel(5), null);
        builder.level(6, 6, 9, WeightedTreasurePieces.getContentsForLevel(6), null);

        return builder.build();
    }

    public static final class Builder {
        private final String id;
        private final Map<Integer, Integer> markerLevels = new LinkedHashMap<Integer, Integer>();
        private final Map<Integer, StructureLootProfile.LootLevel> levels =
                new LinkedHashMap<Integer, StructureLootProfile.LootLevel>();

        private Builder(String id) {
            String normalized = normalizeId(id);
            if (normalized == null) {
                throw new IllegalArgumentException("loot profile id cannot be empty");
            }
            this.id = normalized;
        }

        public Builder marker(int itemId, int level) {
            if (itemId <= 0) {
                throw new IllegalArgumentException("marker itemId must be > 0");
            }
            if (level <= 0) {
                throw new IllegalArgumentException("marker level must be > 0");
            }
            this.markerLevels.put(Integer.valueOf(itemId), Integer.valueOf(level));
            return this;
        }

        public Builder level(
                int level,
                int minRolls,
                int maxRolls,
                WeightedRandomChestContent[] entries,
                float[] artifactChances) {
            if (level <= 0) {
                throw new IllegalArgumentException("level must be > 0");
            }
            WeightedRandomChestContent[] safeEntries = entries == null ? EMPTY : entries.clone();
            float[] safeArtifactChances = artifactChances == null ? null : Arrays.copyOf(artifactChances, artifactChances.length);
            this.levels.put(
                    Integer.valueOf(level),
                    new StructureLootProfile.LootLevel(minRolls, maxRolls, safeEntries, safeArtifactChances));
            return this;
        }

        public StructureLootProfile build() {
            return new StructureLootProfile(this.id, this.markerLevels, this.levels);
        }
    }
}
