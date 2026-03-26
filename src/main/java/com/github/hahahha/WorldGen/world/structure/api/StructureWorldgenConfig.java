package com.github.hahahha.WorldGen.world.structure.api;

import java.util.function.Predicate;
import moddedmite.rustedironcore.api.world.Dimension;
import net.minecraft.BiomeGenBase;

/**
 * 结构世界生成配置。
 * 使用 builder 方式设置参数，最后调用 build() 完成校验。
 */
public final class StructureWorldgenConfig {
    public static final int MIN_WORLD_Y = 0;
    public static final int MAX_WORLD_Y = 255;

    private final String schematicPath;
    private final Dimension dimension;
    private final int weight;
    private final int chance;
    private final int attempts;
    private final boolean surface;
    private final int minY;
    private final int maxY;
    private final int yOffset;
    private final boolean centerOnAnchor;
    private final Predicate<BiomeGenBase> biomeFilter;

    private StructureWorldgenConfig(Builder builder) {
        this.schematicPath = builder.schematicPath;
        this.dimension = builder.dimension;
        this.weight = builder.weight;
        this.chance = builder.chance;
        this.attempts = builder.attempts;
        this.surface = builder.surface;
        this.minY = builder.minY;
        this.maxY = builder.maxY;
        this.yOffset = builder.yOffset;
        this.centerOnAnchor = builder.centerOnAnchor;
        this.biomeFilter = builder.biomeFilter;
    }

    public static Builder builder(String schematicPath) {
        return new Builder(schematicPath);
    }

    public String schematicPath() {
        return this.schematicPath;
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

    public Predicate<BiomeGenBase> biomeFilter() {
        return this.biomeFilter;
    }

    public static final class Builder {
        // 支持两种格式：
        // 1) 资源路径：/assets/<modid>/structures/xxx.schematic
        // 2) 文件路径：F:/path/to/xxx.schematic
        private String schematicPath;

        private Dimension dimension = Dimension.OVERWORLD;
        private int weight = 1;
        private int chance = 40;
        private int attempts = 1;
        private boolean surface = true;
        private int minY = MIN_WORLD_Y;
        private int maxY = MAX_WORLD_Y;
        private int yOffset = 0;
        private boolean centerOnAnchor = true;
        private Predicate<BiomeGenBase> biomeFilter;

        private Builder(String schematicPath) {
            this.schematicPath = schematicPath;
        }

        public Builder schematicPath(String schematicPath) {
            this.schematicPath = schematicPath;
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

        public Builder biomeFilter(Predicate<BiomeGenBase> biomeFilter) {
            this.biomeFilter = biomeFilter;
            return this;
        }

        public StructureWorldgenConfig build() {
            this.schematicPath = normalizePath(this.schematicPath);
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
            return new StructureWorldgenConfig(this);
        }

        private static String normalizePath(String path) {
            if (path == null) {
                return null;
            }
            String trimmed = path.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
    }
}
