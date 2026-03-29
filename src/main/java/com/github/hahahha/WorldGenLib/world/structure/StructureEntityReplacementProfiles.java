package com.github.hahahha.WorldGenLib.world.structure;

import com.github.hahahha.WorldGenLib.util.StringNormalization;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.ItemStack;

public final class StructureEntityReplacementProfiles {
    public static final String DEFAULT_PROFILE_ID = "default";
    private static final int IRON_SWORD_ID = 267;

    private static final StructureEntityReplacementProfile DEFAULT_PROFILE = builder(DEFAULT_PROFILE_ID).build();
    private static final Map<String, StructureEntityReplacementProfile> BUILTIN_PROFILE_BY_ALIAS = createBuiltinProfiles();

    private StructureEntityReplacementProfiles() {
    }

    public static StructureEntityReplacementProfile defaultProfile() {
        return DEFAULT_PROFILE;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static String normalizeId(String id) {
        return StringNormalization.trimLowerToNull(id);
    }

    public static StructureEntityReplacementProfile findBuiltInProfile(String structureKey) {
        String normalized = normalizeStructure(structureKey);
        if (normalized.isEmpty()) {
            return null;
        }
        return BUILTIN_PROFILE_BY_ALIAS.get(normalized);
    }

    static String normalizeStructure(String structureKey) {
        String normalized = StringNormalization.trimLowerToNull(structureKey);
        if (normalized == null) {
            return "";
        }

        normalized = normalized.replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        return normalized;
    }

    private static Map<String, StructureEntityReplacementProfile> createBuiltinProfiles() {
        LinkedHashMap<String, StructureEntityReplacementProfile> profiles =
                new LinkedHashMap<String, StructureEntityReplacementProfile>(8);
        StructureEntityReplacementProfile test10Profile = builder("builtin_test10")
                .level(1, "Zombie")
                .level(
                        2,
                        "Skeleton",
                        StructureEntityReplacementProfile.EntityEquipment.of(
                                new ItemStack(IRON_SWORD_ID, 1, 0),
                                null,
                                null,
                                null,
                                null),
                        null)
                .level(3, "Spider")
                .build();
        registerProfile(
                profiles,
                test10Profile,
                new String[]{
                        "/assets/worldgenlib/structures/test10.schematic",
                        "assets/worldgenlib/structures/test10.schematic",
                        "/assets/worldgenlib/structures/10.schematic",
                        "assets/worldgenlib/structures/10.schematic",
                        "test10.schematic",
                        "test10",
                        "10.schematic",
                        "10"
                });
        return profiles;
    }

    private static void registerProfile(
            Map<String, StructureEntityReplacementProfile> profiles,
            StructureEntityReplacementProfile profile,
            String[] aliases) {
        if (profiles == null || profile == null || aliases == null) {
            return;
        }

        for (String alias : aliases) {
            String normalized = normalizeStructure(alias);
            if (!normalized.isEmpty()) {
                profiles.put(normalized, profile);
            }
        }
    }

    public static final class Builder {
        private final String id;
        private final Map<Integer, StructureEntityReplacementProfile.LevelRule> levelRules =
                new LinkedHashMap<Integer, StructureEntityReplacementProfile.LevelRule>(4);

        private Builder(String id) {
            String normalized = normalizeId(id);
            if (normalized == null) {
                throw new IllegalArgumentException("entity replacement profile id cannot be empty");
            }
            this.id = normalized;
        }

        public Builder level(int level, String targetEntityId) {
            return level(level, targetEntityId, null, null);
        }

        public Builder level(
                int level,
                String targetEntityId,
                StructureEntityReplacementProfile.EntityEquipment equipment,
                StructureEntityReplacementProfile.EntityDrop[] drops) {
            if (level <= 0) {
                throw new IllegalArgumentException("replacement level must be > 0");
            }
            String normalizedTarget = normalizeEntityId(targetEntityId);
            if (normalizedTarget == null) {
                throw new IllegalArgumentException("targetEntityId must not be empty");
            }
            this.levelRules.put(
                    level,
                    new StructureEntityReplacementProfile.LevelRule(normalizedTarget, equipment, drops));
            return this;
        }

        public StructureEntityReplacementProfile build() {
            return new StructureEntityReplacementProfile(this.id, this.levelRules);
        }

        private static String normalizeEntityId(String entityId) {
            return StringNormalization.trimToNull(entityId);
        }
    }
}
