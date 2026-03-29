package com.github.hahahha.WorldGenLib.world.structure.api;

import com.github.hahahha.WorldGenLib.util.StringNormalization;
import com.github.hahahha.WorldGenLib.world.structure.StructureEntityReplacementProfile;
import com.github.hahahha.WorldGenLib.world.structure.StructureEntityReplacementProfiles;
import com.github.hahahha.WorldGenLib.world.structure.StructureLootProfile;
import com.github.hahahha.WorldGenLib.world.structure.StructureLootProfiles;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import moddedmite.rustedironcore.api.world.Dimension;
import net.minecraft.BiomeGenBase;

/**
 * Structure world-generation configuration.
 * Use the builder to set parameters and call {@code build()} for validation.
 */
public final class StructureWorldGenLibConfig {
    public static final int MIN_WORLD_Y = 0;
    public static final int MAX_WORLD_Y = 255;

    public enum DistanceScope {
        ALL,
        SAME_NAME
    }

    private final String schematicPath;
    private final String structureName;
    private final Dimension dimension;
    private final int weight;
    private final int chance;
    private final int attempts;
    private final boolean surface;
    private final int minY;
    private final int maxY;
    private final int yOffset;
    private final boolean centerOnAnchor;
    private final int minDistance;
    private final DistanceScope distanceScope;
    private final boolean lootTableEnabled;
    private final StructureLootProfile lootProfile;
    private final boolean entityReplacementEnabled;
    private final StructureEntityReplacementProfile entityReplacementProfileFallback;
    private final Supplier<StructureEntityReplacementProfile> entityReplacementProfileSupplier;
    private final String entityReplacementProfileIdForLog;
    private final Predicate<BiomeGenBase> biomeFilter;

    private StructureWorldGenLibConfig(Builder builder) {
        this.schematicPath = builder.schematicPath;
        this.structureName = builder.structureName;
        this.dimension = builder.dimension;
        this.weight = builder.weight;
        this.chance = builder.chance;
        this.attempts = builder.attempts;
        this.surface = builder.surface;
        this.minY = builder.minY;
        this.maxY = builder.maxY;
        this.yOffset = builder.yOffset;
        this.centerOnAnchor = builder.centerOnAnchor;
        this.minDistance = builder.minDistance;
        this.distanceScope = builder.distanceScope;
        this.lootTableEnabled = builder.lootTableEnabled;
        this.lootProfile = builder.lootProfile;
        this.entityReplacementEnabled = builder.entityReplacementEnabled;
        this.entityReplacementProfileFallback = builder.entityReplacementProfile;
        this.entityReplacementProfileSupplier =
                builder.entityReplacementProfileSupplier == null
                        ? () -> this.entityReplacementProfileFallback
                        : builder.entityReplacementProfileSupplier;
        this.entityReplacementProfileIdForLog = normalizeProfileId(builder.entityReplacementProfileIdForLog);
        this.biomeFilter = builder.biomeFilter;
    }

    public static Builder builder(String schematicPath) {
        return new Builder(schematicPath);
    }

    public String schematicPath() {
        return this.schematicPath;
    }

    public String structureName() {
        return this.structureName;
    }

    public Dimension dimension() {
        return this.dimension;
    }

    public int weight() {
        return this.weight;
    }

    public int chance() {
        return this.chance;
    }

    public int attempts() {
        return this.attempts;
    }

    public boolean surface() {
        return this.surface;
    }

    public int minY() {
        return this.minY;
    }

    public int maxY() {
        return this.maxY;
    }

    public int yOffset() {
        return this.yOffset;
    }

    public boolean centerOnAnchor() {
        return this.centerOnAnchor;
    }

    public int minDistance() {
        return this.minDistance;
    }

    public DistanceScope distanceScope() {
        return this.distanceScope;
    }

    public boolean lootTableEnabled() {
        return this.lootTableEnabled;
    }

    public StructureLootProfile lootProfile() {
        return this.lootProfile;
    }

    public boolean entityReplacementEnabled() {
        return this.entityReplacementEnabled;
    }

    public StructureEntityReplacementProfile entityReplacementProfile() {
        StructureEntityReplacementProfile resolved;
        try {
            resolved = this.entityReplacementProfileSupplier.get();
        } catch (RuntimeException ignored) {
            resolved = null;
        }
        return resolved == null ? this.entityReplacementProfileFallback : resolved;
    }

    public String entityReplacementProfileIdForLog() {
        if (this.entityReplacementProfileIdForLog != null) {
            return this.entityReplacementProfileIdForLog;
        }
        StructureEntityReplacementProfile profile = entityReplacementProfile();
        return profile == null ? "null" : profile.id();
    }

    public Predicate<BiomeGenBase> biomeFilter() {
        return this.biomeFilter;
    }

    public static final class Builder {
        // Supported formats:
        // 1) classpath resource path: /assets/<modid>/structures/xxx.schematic
        // 2) filesystem path: F:/path/to/xxx.schematic
        private String schematicPath;
        private String structureName;

        private Dimension dimension = Dimension.OVERWORLD;
        private int weight = 1;
        private int chance = 40;
        private int attempts = 1;
        private boolean surface = true;
        private int minY = MIN_WORLD_Y;
        private int maxY = MAX_WORLD_Y;
        private int yOffset = 0;
        private boolean centerOnAnchor = true;
        private int minDistance = 0;
        private DistanceScope distanceScope = DistanceScope.ALL;
        private boolean lootTableEnabled = true;
        private StructureLootProfile lootProfile = StructureLootProfiles.defaultProfile();
        private boolean entityReplacementEnabled = false;
        private StructureEntityReplacementProfile entityReplacementProfile =
                StructureEntityReplacementProfiles.defaultProfile();
        private Supplier<StructureEntityReplacementProfile> entityReplacementProfileSupplier;
        private String entityReplacementProfileIdForLog = this.entityReplacementProfile.id();
        private Predicate<BiomeGenBase> biomeFilter;

        private Builder(String schematicPath) {
            this.schematicPath = schematicPath;
        }

        public Builder schematicPath(String schematicPath) {
            this.schematicPath = schematicPath;
            return this;
        }

        public Builder structureName(String structureName) {
            this.structureName = structureName;
            return this;
        }

        public Builder dimension(Dimension dimension) {
            this.dimension = dimension;
            return this;
        }

        public Builder weight(int weight) {
            this.weight = weight;
            return this;
        }

        public Builder chance(int chance) {
            this.chance = chance;
            return this;
        }

        public Builder attempts(int attempts) {
            this.attempts = attempts;
            return this;
        }

        public Builder surface(boolean surface) {
            this.surface = surface;
            return this;
        }

        public Builder yRange(int minY, int maxY) {
            this.minY = minY;
            this.maxY = maxY;
            return this;
        }

        public Builder yOffset(int yOffset) {
            this.yOffset = yOffset;
            return this;
        }

        public Builder centerOnAnchor(boolean centerOnAnchor) {
            this.centerOnAnchor = centerOnAnchor;
            return this;
        }

        public Builder minDistance(int minDistance) {
            this.minDistance = minDistance;
            return this;
        }

        public Builder distanceScope(DistanceScope distanceScope) {
            this.distanceScope = distanceScope;
            return this;
        }

        public Builder lootTableEnabled(boolean lootTableEnabled) {
            this.lootTableEnabled = lootTableEnabled;
            return this;
        }

        public Builder lootProfile(StructureLootProfile lootProfile) {
            this.lootProfile = lootProfile;
            return this;
        }

        public Builder entityReplacementEnabled(boolean entityReplacementEnabled) {
            this.entityReplacementEnabled = entityReplacementEnabled;
            return this;
        }

        public Builder entityReplacementProfile(StructureEntityReplacementProfile entityReplacementProfile) {
            this.entityReplacementProfile = entityReplacementProfile;
            this.entityReplacementProfileSupplier = null;
            this.entityReplacementProfileIdForLog =
                    entityReplacementProfile == null ? null : normalizeProfileId(entityReplacementProfile.id());
            return this;
        }

        public Builder entityReplacementProfileSupplier(
                String profileIdForLog,
                Supplier<StructureEntityReplacementProfile> entityReplacementProfileSupplier) {
            this.entityReplacementProfileSupplier = Objects.requireNonNull(
                    entityReplacementProfileSupplier,
                    "entityReplacementProfileSupplier");
            this.entityReplacementProfileIdForLog = normalizeProfileId(profileIdForLog);
            return this;
        }

        public Builder biomeFilter(Predicate<BiomeGenBase> biomeFilter) {
            this.biomeFilter = biomeFilter;
            return this;
        }

        public StructureWorldGenLibConfig build() {
            this.schematicPath = normalizePath(this.schematicPath);
            this.structureName = normalizePath(this.structureName);
            if (this.schematicPath == null) {
                throw new IllegalArgumentException("schematicPath must not be empty");
            }
            if (this.dimension == null) {
                throw new IllegalArgumentException("dimension must not be null");
            }
            if (this.weight < 1) {
                throw new IllegalArgumentException("weight must be >= 1");
            }
            if (this.chance < 1) {
                throw new IllegalArgumentException("chance must be >= 1");
            }
            if (this.attempts < 1) {
                throw new IllegalArgumentException("attempts must be >= 1");
            }
            if (this.minY < MIN_WORLD_Y || this.maxY > MAX_WORLD_Y || this.minY > this.maxY) {
                throw new IllegalArgumentException("yRange must be inside 0..255 and minY <= maxY");
            }
            if (this.minDistance < 0) {
                throw new IllegalArgumentException("minDistance must be >= 0");
            }
            if (this.distanceScope == null) {
                throw new IllegalArgumentException("distanceScope must not be null");
            }
            if (this.lootProfile == null) {
                throw new IllegalArgumentException("lootProfile must not be null");
            }
            if (this.entityReplacementProfile == null) {
                throw new IllegalArgumentException("entityReplacementProfile must not be null");
            }
            return new StructureWorldGenLibConfig(this);
        }

        private static String normalizePath(String path) {
            return StringNormalization.trimToNull(path);
        }

        private static String normalizeProfileId(String profileId) {
            return StringNormalization.trimLowerToNull(profileId);
        }
    }

    private static String normalizeProfileId(String profileId) {
        return StringNormalization.trimLowerToNull(profileId);
    }
}
