package com.github.hahahha.WorldGenLib.world.structure;

import com.github.hahahha.WorldGenLib.util.StringNormalization;
import net.minecraft.NBTTagCompound;

public final class StructureEntityReplacementRules {
    private static final int LEVEL_NONE = 0;
    private static final int LEVEL_1 = 1;
    private static final int LEVEL_2 = 2;
    private static final int LEVEL_3 = 3;

    private StructureEntityReplacementRules() {
    }

    public static ReplacementDecision resolve(
            String structureKey,
            NBTTagCompound sourceTag,
            StructureEntityReplacementProfile configuredProfile) {
        if (sourceTag == null) {
            return new ReplacementDecision(null, null, LEVEL_NONE, null, null);
        }

        String sourceId = sourceTag.getString("id");
        String normalizedSourceId = normalizeId(sourceId);
        if (normalizedSourceId.isEmpty()) {
            return new ReplacementDecision(sourceId, sourceId, LEVEL_NONE, null, null);
        }

        int level = detectLevel(sourceTag, normalizedSourceId);
        if (level == LEVEL_NONE) {
            return new ReplacementDecision(sourceId, sourceId, LEVEL_NONE, null, null);
        }

        StructureEntityReplacementProfile profile = configuredProfile != null
                ? configuredProfile
                : StructureEntityReplacementProfiles.findBuiltInProfile(structureKey);
        if (profile == null) {
            return new ReplacementDecision(sourceId, sourceId, level, null, null);
        }

        StructureEntityReplacementProfile.LevelRule levelRule = profile.levelRule(level);
        if (levelRule == null) {
            return new ReplacementDecision(sourceId, sourceId, level, profile.id(), null);
        }

        String replacement = levelRule.targetEntityId();
        if (isBlank(replacement)) {
            return new ReplacementDecision(sourceId, sourceId, level, profile.id(), levelRule);
        }

        return new ReplacementDecision(sourceId, replacement, level, profile.id(), levelRule);
    }

    private static int detectLevel(NBTTagCompound sourceTag, String normalizedSourceId) {
        int taggedLevel = readTaggedLevel(sourceTag);
        if (taggedLevel != LEVEL_NONE) {
            return taggedLevel;
        }

        int markerLevel = parseMarkerLevel(normalizedSourceId);
        if (markerLevel != LEVEL_NONE) {
            return markerLevel;
        }

        if ("schematica".equals(normalizedSourceId)
                || "mudman".equals(normalizedSourceId)
                || "mud_man".equals(normalizedSourceId)) {
            return levelByHealth(sourceTag);
        }

        return LEVEL_NONE;
    }

    private static int readTaggedLevel(NBTTagCompound sourceTag) {
        if (sourceTag == null) {
            return LEVEL_NONE;
        }

        if (sourceTag.hasKey("schematica_level")) {
            return clampLevel(sourceTag.getInteger("schematica_level"));
        }

        if (sourceTag.hasKey("level")) {
            return clampLevel(sourceTag.getInteger("level"));
        }

        return LEVEL_NONE;
    }

    private static int parseMarkerLevel(String normalized) {
        if ("1".equals(normalized)
                || "mudman1".equals(normalized)
                || "mud_man_1".equals(normalized)
                || "mud_man1".equals(normalized)) {
            return LEVEL_1;
        }
        if ("2".equals(normalized)
                || "mudman2".equals(normalized)
                || "mud_man_2".equals(normalized)
                || "mud_man2".equals(normalized)) {
            return LEVEL_2;
        }
        if ("3".equals(normalized)
                || "mudman3".equals(normalized)
                || "mud_man_3".equals(normalized)
                || "mud_man3".equals(normalized)) {
            return LEVEL_3;
        }
        return LEVEL_NONE;
    }

    private static int levelByHealth(NBTTagCompound sourceTag) {
        float health = sourceTag.getFloat("HealF");
        if (health <= 0.0F) {
            health = sourceTag.getFloat("Health");
        }
        if (health <= 0.0F) {
            return LEVEL_NONE;
        }
        if (health <= 24.0F) {
            return LEVEL_1;
        }
        if (health <= 48.0F) {
            return LEVEL_2;
        }
        return LEVEL_3;
    }

    private static int clampLevel(int level) {
        if (level < LEVEL_1 || level > LEVEL_3) {
            return LEVEL_NONE;
        }
        return level;
    }

    private static String normalizeId(String sourceId) {
        String normalized = StringNormalization.trimLowerToNull(sourceId);
        if (normalized == null) {
            return "";
        }
        int colon = normalized.indexOf(':');
        if (colon >= 0 && colon + 1 < normalized.length()) {
            normalized = normalized.substring(colon + 1);
        }
        return normalized;
    }

    private static boolean isBlank(String value) {
        return StringNormalization.isBlank(value);
    }

    public static final class ReplacementDecision {
        private final String sourceId;
        private final String replacementId;
        private final int detectedLevel;
        private final String matchedRuleId;
        private final StructureEntityReplacementProfile.LevelRule levelRule;

        private ReplacementDecision(
                String sourceId,
                String replacementId,
                int detectedLevel,
                String matchedRuleId,
                StructureEntityReplacementProfile.LevelRule levelRule) {
            this.sourceId = sourceId;
            this.replacementId = replacementId;
            this.detectedLevel = detectedLevel;
            this.matchedRuleId = matchedRuleId;
            this.levelRule = levelRule;
        }

        public String sourceId() {
            return this.sourceId;
        }

        public String replacementId() {
            return this.replacementId;
        }

        public int detectedLevel() {
            return this.detectedLevel;
        }

        public String matchedRuleId() {
            return this.matchedRuleId;
        }

        public StructureEntityReplacementProfile.LevelRule levelRule() {
            return this.levelRule;
        }
    }
}
