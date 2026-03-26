package com.github.hahahha.WorldGen.world.structure;

import com.github.hahahha.WorldGen.WorldGen;
import com.github.hahahha.WorldGen.world.structure.api.StructureWorldgenConfig;
import java.util.Objects;
import java.util.Random;
import net.minecraft.Block;
import net.minecraft.NBTTagCompound;
import net.minecraft.TileEntity;
import net.minecraft.World;
import net.minecraft.WorldGenerator;

/**
 * 真正执行方块放置的生成器。
 */
public class SchematicStructureGenerator extends WorldGenerator {
    private static final int MIN_WORLD_Y = 0;
    private static final int MAX_WORLD_Y = 255;

    private final String schematicPath;
    private final boolean centerOnAnchor;
    private final int minPlacementY;
    private final int maxPlacementY;
    private final int yOffset;

    private SchematicData schematicData;
    private boolean loadAttempted;

    public SchematicStructureGenerator(String schematicPath) {
        this(StructureWorldgenConfig.builder(schematicPath).build());
    }

    public SchematicStructureGenerator(StructureWorldgenConfig config) {
        StructureWorldgenConfig safeConfig = Objects.requireNonNull(config, "config");
        this.schematicPath = safeConfig.schematicPath();
        this.centerOnAnchor = safeConfig.centerOnAnchor();
        this.minPlacementY = safeConfig.minY();
        this.maxPlacementY = safeConfig.maxY();
        this.yOffset = safeConfig.yOffset();
    }

    @Override
    public boolean generate(World world, Random random, int x, int y, int z) {
        if (world == null) {
            return false;
        }
        if (!ensureLoaded() || this.schematicData == null) {
            return false;
        }

        int width = this.schematicData.getWidth();
        int height = this.schematicData.getHeight();
        int length = this.schematicData.getLength();
        if (width <= 0 || height <= 0 || length <= 0) {
            return false;
        }

        // 计算结构放置原点。
        int originX = this.centerOnAnchor ? x - width / 2 : x;
        int originZ = this.centerOnAnchor ? z - length / 2 : z;
        int originY = y + this.yOffset;

        int safeMinY = Math.max(MIN_WORLD_Y, this.minPlacementY);
        int safeMaxY = Math.min(MAX_WORLD_Y, this.maxPlacementY);
        if (safeMinY > safeMaxY) {
            return false;
        }
        if (originY < safeMinY) {
            originY = safeMinY;
        }
        if (originY + height - 1 > safeMaxY) {
            return false;
        }

        // 先放置方块。
        for (int sx = 0; sx < width; ++sx) {
            for (int sy = 0; sy < height; ++sy) {
                for (int sz = 0; sz < length; ++sz) {
                    int blockId = this.schematicData.getBlockId(sx, sy, sz);
                    if (blockId <= 0) {
                        continue;
                    }

                    Block block = Block.getBlock(blockId);
                    if (block == null) {
                        continue;
                    }

                    int wx = originX + sx;
                    int wy = originY + sy;
                    int wz = originZ + sz;
                    if (wy < MIN_WORLD_Y || wy > MAX_WORLD_Y) {
                        continue;
                    }

                    int metadata = this.schematicData.getMetadata(sx, sy, sz);
                    world.setBlock(wx, wy, wz, block.blockID, metadata, 2);
                }
            }
        }

        // 再恢复 TileEntity（箱子、告示牌等）。
        for (NBTTagCompound sourceTag : this.schematicData.getTileEntityTags()) {
            if (sourceTag == null) {
                continue;
            }
            int sx = sourceTag.getInteger("x");
            int sy = sourceTag.getInteger("y");
            int sz = sourceTag.getInteger("z");
            if (sx < 0 || sy < 0 || sz < 0 || sx >= width || sy >= height || sz >= length) {
                continue;
            }

            int sourceBlockId = this.schematicData.getBlockId(sx, sy, sz);
            if (sourceBlockId <= 0) {
                continue;
            }

            int wx = originX + sx;
            int wy = originY + sy;
            int wz = originZ + sz;
            if (wy < MIN_WORLD_Y || wy > MAX_WORLD_Y) {
                continue;
            }
            if (world.getBlockId(wx, wy, wz) != sourceBlockId) {
                continue;
            }

            TileEntity copied = copyTileEntityForPlacement(sourceTag, wx, wy, wz);
            if (copied != null) {
                world.setBlockTileEntity(wx, wy, wz, copied);
            }
        }

        return true;
    }

    private boolean ensureLoaded() {
        if (this.schematicData != null) {
            return true;
        }
        if (this.loadAttempted) {
            return false;
        }
        this.loadAttempted = true;

        this.schematicData = SchematicLoader.load(this.schematicPath);
        if (this.schematicData == null) {
            WorldGen.LOGGER.warn("结构文件加载失败: {}", this.schematicPath);
            return false;
        }

        WorldGen.LOGGER.info(
                "结构文件已加载: {} ({}x{}x{})",
                this.schematicPath,
                this.schematicData.getWidth(),
                this.schematicData.getHeight(),
                this.schematicData.getLength());
        return true;
    }

    private static TileEntity copyTileEntityForPlacement(NBTTagCompound sourceTag, int x, int y, int z) {
        try {
            TileEntity source = TileEntity.createAndLoadEntity(sourceTag);
            if (source == null) {
                return null;
            }

            NBTTagCompound worldTag = new NBTTagCompound();
            source.writeToNBT(worldTag);
            worldTag.setInteger("x", x);
            worldTag.setInteger("y", y);
            worldTag.setInteger("z", z);
            return TileEntity.createAndLoadEntity(worldTag);
        } catch (Exception ignored) {
            return null;
        }
    }
}
