package com.github.hahahha.WorldGen.world.structure;

import com.github.hahahha.WorldGen.WorldGen;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.CompressedStreamTools;
import net.minecraft.NBTBase;
import net.minecraft.NBTTagCompound;
import net.minecraft.NBTTagList;

/**
 * schematic 文件加载器。
 * 优先按 classpath 路径加载，失败后回退到文件系统路径。
 */
final class SchematicLoader {
    private SchematicLoader() {
    }

    static SchematicData load(String schematicPath) {
        if (schematicPath == null) {
            return null;
        }
        String trimmed = schematicPath.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        String classpathCandidate = trimmed.replace('\\', '/');
        SchematicData classpathData = loadFromClasspath(classpathCandidate);
        if (classpathData != null) {
            return classpathData;
        }
        return loadFromFile(trimmed);
    }

    private static SchematicData loadFromClasspath(String classpathPath) {
        String stripped = classpathPath.startsWith("/") ? classpathPath.substring(1) : classpathPath;
        if (stripped.isEmpty()) {
            return null;
        }

        String absolutePath = "/" + stripped;
        InputStream stream = SchematicLoader.class.getResourceAsStream(absolutePath);
        if (stream == null) {
            stream = SchematicLoader.class.getClassLoader().getResourceAsStream(stripped);
        }
        if (stream == null) {
            return null;
        }

        try (InputStream input = stream) {
            return readFromInputStream(input, absolutePath);
        } catch (Exception e) {
            WorldGen.LOGGER.error("读取 classpath 结构文件失败: {}", absolutePath, e);
            return null;
        }
    }

    private static SchematicData loadFromFile(String filePath) {
        File file = new File(filePath);
        if (!file.isFile()) {
            return null;
        }

        try (InputStream input = new FileInputStream(file)) {
            return readFromInputStream(input, file.getAbsolutePath());
        } catch (Exception e) {
            WorldGen.LOGGER.error("读取结构文件失败: {}", file.getAbsolutePath(), e);
            return null;
        }
    }

    private static SchematicData readFromInputStream(InputStream input, String source) throws Exception {
        NBTTagCompound root = CompressedStreamTools.readCompressed(input);
        int width = root.getShort("Width");
        int height = root.getShort("Height");
        int length = root.getShort("Length");

        if (width <= 0 || height <= 0 || length <= 0) {
            WorldGen.LOGGER.warn("结构尺寸非法: {} ({}x{}x{})", source, width, height, length);
            return null;
        }

        int expectedSize = width * height * length;
        if (expectedSize <= 0) {
            WorldGen.LOGGER.warn("结构体积非法: {} ({})", source, expectedSize);
            return null;
        }

        byte[] blocks = root.getByteArray("Blocks");
        byte[] data = root.getByteArray("Data");
        if (blocks.length < expectedSize || data.length < expectedSize) {
            WorldGen.LOGGER.warn(
                    "结构数据长度不足: {} blocks={} data={} expected={}",
                    source,
                    blocks.length,
                    data.length,
                    expectedSize);
            return null;
        }

        byte[] add = expandAddBlocks(root, expectedSize);
        int[] blockIds = new int[expectedSize];
        byte[] metadata = new byte[expectedSize];
        for (int i = 0; i < expectedSize; ++i) {
            int blockId = blocks[i] & 0xFF;
            if (add != null) {
                blockId |= (add[i] & 0xFF) << 8;
            }
            blockIds[i] = blockId;
            metadata[i] = (byte) (data[i] & 0xF);
        }

        List<NBTTagCompound> tileEntityTags = new ArrayList<NBTTagCompound>();
        NBTTagList tileEntities = root.getTagList("TileEntities");
        for (int i = 0; i < tileEntities.tagCount(); ++i) {
            NBTBase base = tileEntities.tagAt(i);
            if (base instanceof NBTTagCompound) {
                tileEntityTags.add((NBTTagCompound) base);
            }
        }

        List<NBTTagCompound> entityTags = new ArrayList<NBTTagCompound>();
        NBTTagList entities = root.getTagList("Entities");
        for (int i = 0; i < entities.tagCount(); ++i) {
            NBTBase base = entities.tagAt(i);
            if (base instanceof NBTTagCompound) {
                entityTags.add((NBTTagCompound) base);
            }
        }

        String materials = root.getString("Materials");
        if (materials != null && !materials.isEmpty() && !"Alpha".equalsIgnoreCase(materials)) {
            WorldGen.LOGGER.warn("结构格式不是 Alpha，已按 Alpha 读取: {} materials={}", source, materials);
        }

        return new SchematicData(width, height, length, blockIds, metadata, tileEntityTags, entityTags);
    }

    private static byte[] expandAddBlocks(NBTTagCompound root, int expectedSize) {
        if (root.hasKey("AddBlocks")) {
            byte[] nibble = root.getByteArray("AddBlocks");
            byte[] expanded = new byte[expectedSize];
            for (int i = 0; i < nibble.length; ++i) {
                int hiIndex = i * 2;
                int loIndex = hiIndex + 1;
                if (hiIndex < expectedSize) {
                    expanded[hiIndex] = (byte) ((nibble[i] >> 4) & 0xF);
                }
                if (loIndex < expectedSize) {
                    expanded[loIndex] = (byte) (nibble[i] & 0xF);
                }
            }
            return expanded;
        }
        if (root.hasKey("Add")) {
            byte[] add = root.getByteArray("Add");
            if (add.length >= expectedSize) {
                return add;
            }
            byte[] expanded = new byte[expectedSize];
            System.arraycopy(add, 0, expanded, 0, add.length);
            return expanded;
        }
        return null;
    }
}
