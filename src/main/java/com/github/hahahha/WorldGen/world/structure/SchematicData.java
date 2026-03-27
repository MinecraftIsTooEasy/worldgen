package com.github.hahahha.WorldGen.world.structure;

import java.util.Collections;
import java.util.List;
import net.minecraft.NBTTagCompound;

/**
 * 结构数据内存模型。
 * 把 schematic 中的 Blocks/Data/TileEntities 转为可直接放置的结构体。
 */
final class SchematicData {
    private final int width;
    private final int height;
    private final int length;
    private final int[] blockIds;
    private final byte[] metadata;
    private final List<NBTTagCompound> tileEntityTags;
    private final List<NBTTagCompound> entityTags;

    SchematicData(
            int width,
            int height,
            int length,
            int[] blockIds,
            byte[] metadata,
            List<NBTTagCompound> tileEntityTags,
            List<NBTTagCompound> entityTags) {
        this.width = width;
        this.height = height;
        this.length = length;
        this.blockIds = blockIds;
        this.metadata = metadata;
        this.tileEntityTags = Collections.unmodifiableList(tileEntityTags);
        this.entityTags = Collections.unmodifiableList(entityTags);
    }

    int getWidth() {
        return this.width;
    }

    int getHeight() {
        return this.height;
    }

    int getLength() {
        return this.length;
    }

    int getBlockId(int x, int y, int z) {
        return this.blockIds[index(x, y, z)];
    }

    int getMetadata(int x, int y, int z) {
        return this.metadata[index(x, y, z)] & 0xF;
    }

    List<NBTTagCompound> getTileEntityTags() {
        return this.tileEntityTags;
    }

    List<NBTTagCompound> getEntityTags() {
        return this.entityTags;
    }

    private int index(int x, int y, int z) {
        return x + (y * this.length + z) * this.width;
    }
}
