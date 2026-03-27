package com.github.hahahha.WorldGen.world.structure;

import com.github.hahahha.WorldGen.WorldGen;
import com.github.hahahha.WorldGen.world.structure.api.StructureWorldgenConfig;
import com.github.hahahha.WorldGen.world.structure.data.StructureGenerationDataStore;
import java.util.Objects;
import java.util.Random;
import net.minecraft.Block;
import net.minecraft.Entity;
import net.minecraft.EntityList;
import net.minecraft.EntityPlayer;
import net.minecraft.IInventory;
import net.minecraft.ItemStack;
import net.minecraft.NBTBase;
import net.minecraft.NBTTagCompound;
import net.minecraft.NBTTagDouble;
import net.minecraft.NBTTagList;
import net.minecraft.TileEntity;
import net.minecraft.WeightedRandomChestContent;
import net.minecraft.World;
import net.minecraft.WorldGenerator;

public class SchematicStructureGenerator extends WorldGenerator {
    private static final int MIN_WORLD_Y = 0;
    private static final int MAX_WORLD_Y = 255;

    private final String schematicPath;
    private final String structureName;
    private final boolean centerOnAnchor;
    private final int minPlacementY;
    private final int maxPlacementY;
    private final int yOffset;
    private final int minDistance;
    private final StructureWorldgenConfig.DistanceScope distanceScope;
    private final boolean lootTableEnabled;
    private final StructureLootProfile lootProfile;

    private SchematicData schematicData;
    private boolean loadAttempted;

    public SchematicStructureGenerator(String schematicPath) {
        this(StructureWorldgenConfig.builder(schematicPath).build());
    }

    public SchematicStructureGenerator(StructureWorldgenConfig config) {
        StructureWorldgenConfig safeConfig = Objects.requireNonNull(config, "config");
        this.schematicPath = safeConfig.schematicPath();
        this.structureName = safeConfig.structureName();
        this.centerOnAnchor = safeConfig.centerOnAnchor();
        this.minPlacementY = safeConfig.minY();
        this.maxPlacementY = safeConfig.maxY();
        this.yOffset = safeConfig.yOffset();
        this.minDistance = safeConfig.minDistance();
        this.distanceScope = safeConfig.distanceScope();
        this.lootTableEnabled = safeConfig.lootTableEnabled();
        this.lootProfile = safeConfig.lootProfile();
    }

    @Override
    public boolean generate(World world, Random random, int x, int y, int z) {
        if (world == null || world.isRemote) {
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

        int minX = originX;
        int maxX = originX + width - 1;
        int minZ = originZ;
        int maxZ = originZ + length - 1;
        if (this.minDistance > 0) {
            boolean sameNameOnly = this.distanceScope == StructureWorldgenConfig.DistanceScope.SAME_NAME;
            if (StructureGenerationDataStore.hasNearbyStructure(
                    world,
                    world.getDimensionId(),
                    minX,
                    maxX,
                    minZ,
                    maxZ,
                    this.minDistance,
                    resolveStructureNameForSpacing(),
                    sameNameOnly)) {
                return false;
            }
        }

        for (int sx = 0; sx < width; ++sx) {
            for (int sy = 0; sy < height; ++sy) {
                for (int sz = 0; sz < length; ++sz) {
                    int wx = originX + sx;
                    int wy = originY + sy;
                    int wz = originZ + sz;
                    world.setBlock(wx, wy, wz, 0, 0, 2);
                }
            }
        }

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

        int lootChestGenerated = 0;
        int lootChestFailed = 0;
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

            Block sourceBlock = Block.getBlock(sourceBlockId);
            int lootLevel = 0;
            if (this.lootTableEnabled) {
                lootLevel = detectLootChestLevel(sourceBlock, sourceTag, this.lootProfile);
            }
            if (!applyTileEntityData(world, sourceTag, wx, wy, wz)) {
                if (lootLevel > 0) {
                    ++lootChestFailed;
                }
                continue;
            }

            if (lootLevel > 0) {
                if (populateLootChest(world, random, wx, wy, wz, lootLevel, this.lootProfile)) {
                    ++lootChestGenerated;
                } else {
                    ++lootChestFailed;
                }
            }
        }

        if (lootChestFailed > 0) {
            WorldGen.LOGGER.warn(
                    "Structure {} loot chest result: generated={}, failed={}, origin=[{},{},{}]",
                    this.schematicPath,
                    lootChestGenerated,
                    lootChestFailed,
                    originX,
                    originY,
                    originZ);
        } else if (lootChestGenerated > 0) {
            WorldGen.LOGGER.info(
                    "Structure {} generated {} marker loot chests at [{},{},{}]",
                    this.schematicPath,
                    lootChestGenerated,
                    originX,
                    originY,
                    originZ);
        }

        EntitySpawnResult entitySpawnResult = spawnEntities(world, originX, originY, originZ);
        if (entitySpawnResult.failed > 0) {
            WorldGen.LOGGER.warn(
                    "Structure {} entity spawn result: spawned={}, failed={}, origin=[{},{},{}]",
                    this.schematicPath,
                    entitySpawnResult.spawned,
                    entitySpawnResult.failed,
                    originX,
                    originY,
                    originZ);
        } else if (entitySpawnResult.spawned > 0) {
            WorldGen.LOGGER.info(
                    "Structure {} spawned {} entities at [{},{},{}]",
                    this.schematicPath,
                    entitySpawnResult.spawned,
                    originX,
                    originY,
                    originZ);
        }

        StructureGenerationDataStore.recordStructure(
                world,
                this.schematicPath,
                this.structureName,
                originX,
                originY,
                originZ,
                width,
                height,
                length);
        return true;
    }

    private EntitySpawnResult spawnEntities(World world, int originX, int originY, int originZ) {
        EntitySpawnResult result = new EntitySpawnResult();
        if (world == null || this.schematicData == null) {
            return result;
        }

        for (NBTTagCompound sourceTag : this.schematicData.getEntityTags()) {
            if (sourceTag == null) {
                ++result.failed;
                continue;
            }

            NBTTagCompound worldTag = (NBTTagCompound) sourceTag.copy();
            offsetEntityPosition(worldTag, originX, originY, originZ);
            clearEntityIdentity(worldTag);

            Entity copied = EntityList.createEntityFromNBT(worldTag, world);
            if (copied == null || copied instanceof EntityPlayer) {
                ++result.failed;
                continue;
            }
            if (copied.posY < MIN_WORLD_Y || copied.posY > MAX_WORLD_Y) {
                ++result.failed;
                continue;
            }

            if (world.spawnEntityInWorld(copied)) {
                ++result.spawned;
            } else {
                ++result.failed;
            }
        }
        return result;
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
            WorldGen.LOGGER.warn("Structure file load failed: {}", this.schematicPath);
            return false;
        }

        WorldGen.LOGGER.info(
                "Structure file loaded: {} ({}x{}x{}), tileEntities={}, entities={}",
                this.schematicPath,
                this.schematicData.getWidth(),
                this.schematicData.getHeight(),
                this.schematicData.getLength(),
                this.schematicData.getTileEntityTags().size(),
                this.schematicData.getEntityTags().size());
        return true;
    }

    private static boolean applyTileEntityData(World world, NBTTagCompound sourceTag, int x, int y, int z) {
        if (world == null || sourceTag == null) {
            return false;
        }

        try {
            NBTTagCompound tag = (NBTTagCompound) sourceTag.copy();
            tag.setInteger("x", x);
            tag.setInteger("y", y);
            tag.setInteger("z", z);

            TileEntity existing = world.getBlockTileEntity(x, y, z);
            if (existing != null) {
                existing.readFromNBT(tag);
                existing.updateContainingBlockInfo();
                if (existing instanceof IInventory) {
                    ((IInventory) existing).onInventoryChanged();
                }
                return true;
            }

            TileEntity recreated = TileEntity.createAndLoadEntity(tag);
            if (recreated == null) {
                return false;
            }
            world.setBlockTileEntity(x, y, z, recreated);
            TileEntity placed = world.getBlockTileEntity(x, y, z);
            if (placed instanceof IInventory) {
                ((IInventory) placed).onInventoryChanged();
            }
            return placed != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static int detectLootChestLevel(
            Block sourceBlock,
            NBTTagCompound sourceTileEntityTag,
            StructureLootProfile lootProfile) {
        if (!isLootChestBlock(sourceBlock) || sourceTileEntityTag == null || !sourceTileEntityTag.hasKey("Items")) {
            return 0;
        }

        NBTTagList items = sourceTileEntityTag.getTagList("Items");
        if (items == null) {
            return 0;
        }

        ItemStack markerStack = null;
        for (int i = 0; i < items.tagCount(); ++i) {
            NBTBase base = items.tagAt(i);
            if (!(base instanceof NBTTagCompound)) {
                continue;
            }
            ItemStack stack = ItemStack.loadItemStackFromNBT((NBTTagCompound) base);
            if (stack == null || stack.getItem() == null || stack.stackSize <= 0) {
                continue;
            }

            if (markerStack != null) {
                return 0;
            }
            markerStack = stack;
        }

        return StructureLootProfiles.getLevelForMarker(markerStack, lootProfile);
    }

    private static boolean isLootChestBlock(Block block) {
        if (block == null) {
            return false;
        }

        int blockId = block.blockID;
        return blockId == Block.chest.blockID
                || blockId == Block.chestTrapped.blockID
                || blockId == Block.chestCopper.blockID
                || blockId == Block.chestSilver.blockID
                || blockId == Block.chestGold.blockID
                || blockId == Block.chestIron.blockID
                || blockId == Block.chestMithril.blockID
                || blockId == Block.chestAdamantium.blockID
                || blockId == Block.chestAncientMetal.blockID;
    }

    private static boolean populateLootChest(
            World world,
            Random random,
            int x,
            int y,
            int z,
            int level,
            StructureLootProfile lootProfile) {
        if (world == null || random == null || level <= 0) {
            return false;
        }

        TileEntity tileEntity = world.getBlockTileEntity(x, y, z);
        if (!(tileEntity instanceof IInventory)) {
            return false;
        }

        IInventory inventory = (IInventory) tileEntity;
        clearInventory(inventory);

        WeightedRandomChestContent[] contents = StructureLootProfiles.getContentsForLevel(level, lootProfile);
        int rollCount = StructureLootProfiles.getRollCount(random, level, lootProfile);
        if (contents.length > 0 && rollCount > 0) {
            WeightedRandomChestContent.generateChestContents(
                    world,
                    y,
                    random,
                    contents,
                    inventory,
                    rollCount,
                    StructureLootProfiles.getArtifactChances(level, lootProfile));
        }
        inventory.onInventoryChanged();
        return true;
    }

    private static void clearInventory(IInventory inventory) {
        for (int slot = 0; slot < inventory.getSizeInventory(); ++slot) {
            inventory.setInventorySlotContents(slot, null);
        }
    }

    private static void offsetEntityPosition(NBTTagCompound tag, int offsetX, int offsetY, int offsetZ) {
        NBTTagList pos = tag.getTagList("Pos");
        if (pos != null
                && pos.tagCount() >= 3
                && pos.tagAt(0) instanceof NBTTagDouble
                && pos.tagAt(1) instanceof NBTTagDouble
                && pos.tagAt(2) instanceof NBTTagDouble) {
            NBTTagList translated = new NBTTagList();
            translated.appendTag(new NBTTagDouble(null, ((NBTTagDouble) pos.tagAt(0)).data + offsetX));
            translated.appendTag(new NBTTagDouble(null, ((NBTTagDouble) pos.tagAt(1)).data + offsetY));
            translated.appendTag(new NBTTagDouble(null, ((NBTTagDouble) pos.tagAt(2)).data + offsetZ));
            tag.setTag("Pos", translated);
        }

        if (tag.hasKey("Riding")) {
            NBTTagCompound riding = tag.getCompoundTag("Riding");
            offsetEntityPosition(riding, offsetX, offsetY, offsetZ);
            tag.setTag("Riding", riding);
        }
    }

    private static void clearEntityIdentity(NBTTagCompound entityTag) {
        if (entityTag == null) {
            return;
        }

        entityTag.removeTag("UUIDMost");
        entityTag.removeTag("UUIDLeast");
        entityTag.removeTag("UUID");
        entityTag.removeTag("PersistentIDMSB");
        entityTag.removeTag("PersistentIDLSB");
        entityTag.removeTag("UniqueIDMost");
        entityTag.removeTag("UniqueIDLeast");
        entityTag.removeTag("EntityUUIDMost");
        entityTag.removeTag("EntityUUIDLeast");

        if (entityTag.hasKey("Riding")) {
            NBTTagCompound riding = entityTag.getCompoundTag("Riding");
            clearEntityIdentity(riding);
            entityTag.setTag("Riding", riding);
        }
    }

    private String resolveStructureNameForSpacing() {
        if (this.structureName != null) {
            String trimmed = this.structureName.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        String path = this.schematicPath;
        if (path == null || path.isEmpty()) {
            return "unknown";
        }
        String normalized = path.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String name = slash >= 0 && slash + 1 < normalized.length()
                ? normalized.substring(slash + 1)
                : normalized;
        String lower = name.toLowerCase();
        if (lower.endsWith(".schematic")) {
            name = name.substring(0, name.length() - ".schematic".length());
        }
        return name.isEmpty() ? "unknown" : name;
    }

    private static final class EntitySpawnResult {
        private int spawned;
        private int failed;
    }
}
