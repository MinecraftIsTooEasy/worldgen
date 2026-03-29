package com.github.hahahha.WorldGenLib.world.structure;

import com.github.hahahha.WorldGenLib.WorldGenLib;
import com.github.hahahha.WorldGenLib.util.StringNormalization;
import com.github.hahahha.WorldGenLib.world.structure.api.StructureWorldGenLibConfig;
import com.github.hahahha.WorldGenLib.world.structure.data.StructureGenerationDataStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.function.Supplier;
import net.minecraft.Block;
import net.minecraft.Entity;
import net.minecraft.EntityList;
import net.minecraft.EntityLivingBase;
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
    private static final int MAX_BLOCK_CACHE_ID = 4096;
    private static final int BLOCK_SET_FLAGS = resolveBlockSetFlags();
    private static final boolean DEBUG_ENTITY_REPLACEMENT = Boolean.parseBoolean(
            System.getProperty(
                    "worldgenlib.debug.entityReplacement",
                    System.getProperty("schematica.debug.entityReplacement", "false")));
    private static final boolean VERBOSE_GENERATION_LOG = Boolean.parseBoolean(
            System.getProperty(
                    "worldgenlib.debug.generationLog",
                    System.getProperty("schematica.debug.generationLog", "false")));

    private final String schematicPath;
    private final String structureName;
    private final String structureNameForSpacing;
    private final boolean centerOnAnchor;
    private final int minPlacementY;
    private final int maxPlacementY;
    private final int yOffset;
    private final int minDistance;
    private final StructureWorldGenLibConfig.DistanceScope distanceScope;
    private final boolean lootTableEnabled;
    private final StructureLootProfile lootProfile;
    private final boolean entityReplacementEnabled;
    private final Supplier<StructureEntityReplacementProfile> entityReplacementProfileSupplier;

    private SchematicData schematicData;
    private boolean loadAttempted;
    private final Block[] blockCache = new Block[MAX_BLOCK_CACHE_ID];
    private final int[] blockResolveTokens = new int[MAX_BLOCK_CACHE_ID];
    private int blockResolveToken = 1;
    private List<TileEntityTemplate> tileEntityTemplates = Collections.emptyList();
    private List<NBTTagCompound> entityTemplates = Collections.emptyList();

    public SchematicStructureGenerator(String schematicPath) {
        this(StructureWorldGenLibConfig.builder(schematicPath).build());
    }

    public SchematicStructureGenerator(StructureWorldGenLibConfig config) {
        StructureWorldGenLibConfig safeConfig = Objects.requireNonNull(config, "config");
        this.schematicPath = safeConfig.schematicPath();
        this.structureName = safeConfig.structureName();
        this.structureNameForSpacing = resolveStructureNameForSpacing(this.structureName, this.schematicPath);
        this.centerOnAnchor = safeConfig.centerOnAnchor();
        this.minPlacementY = safeConfig.minY();
        this.maxPlacementY = safeConfig.maxY();
        this.yOffset = safeConfig.yOffset();
        this.minDistance = safeConfig.minDistance();
        this.distanceScope = safeConfig.distanceScope();
        this.lootTableEnabled = safeConfig.lootTableEnabled();
        this.lootProfile = safeConfig.lootProfile();
        this.entityReplacementEnabled = safeConfig.entityReplacementEnabled();
        this.entityReplacementProfileSupplier = safeConfig::entityReplacementProfile;
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
        int[] blockIds = this.schematicData.getBlockIds();
        byte[] metadataValues = this.schematicData.getMetadataValues();
        int resolveToken = nextBlockResolveToken();

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
            boolean sameNameOnly = this.distanceScope == StructureWorldGenLibConfig.DistanceScope.SAME_NAME;
            if (StructureGenerationDataStore.hasNearbyStructure(
                    world,
                    world.getDimensionId(),
                    minX,
                    maxX,
                    minZ,
                    maxZ,
                    this.minDistance,
                    this.structureNameForSpacing,
                    sameNameOnly)) {
                return false;
            }
        }

        int index = 0;
        int layerSize = width * length;
        for (int sy = 0; sy < height; ++sy) {
            int wy = originY + sy;
            if (wy < MIN_WORLD_Y || wy > MAX_WORLD_Y) {
                index += layerSize;
                continue;
            }
            for (int sz = 0; sz < length; ++sz) {
                int wz = originZ + sz;
                for (int sx = 0; sx < width; ++sx, ++index) {
                    int wx = originX + sx;
                    int blockId = blockIds[index];
                    if (blockId <= 0) {
                        int currentBlockId = world.getBlockId(wx, wy, wz);
                        if (currentBlockId != 0) {
                            world.setBlock(wx, wy, wz, 0, 0, BLOCK_SET_FLAGS);
                        }
                        continue;
                    }

                    Block block = resolveBlock(blockId, resolveToken);
                    if (block == null) {
                        int currentBlockId = world.getBlockId(wx, wy, wz);
                        if (currentBlockId != 0) {
                            world.setBlock(wx, wy, wz, 0, 0, BLOCK_SET_FLAGS);
                        }
                        continue;
                    }

                    int currentBlockId = world.getBlockId(wx, wy, wz);
                    int metadata = metadataValues[index] & 0xF;
                    if (currentBlockId == block.blockID && world.getBlockMetadata(wx, wy, wz) == metadata) {
                        continue;
                    }
                    world.setBlock(wx, wy, wz, block.blockID, metadata, BLOCK_SET_FLAGS);
                }
            }
        }

        int lootChestGenerated = 0;
        int lootChestFailed = 0;
        for (TileEntityTemplate template : this.tileEntityTemplates) {
            if (template == null) {
                continue;
            }
            int wx = originX + template.localX;
            int wy = originY + template.localY;
            int wz = originZ + template.localZ;
            if (wy < MIN_WORLD_Y || wy > MAX_WORLD_Y) {
                continue;
            }
            if (world.getBlockId(wx, wy, wz) != template.sourceBlockId) {
                continue;
            }

            int lootLevel = template.lootLevel;
            if (!applyTileEntityData(world, template.tag, wx, wy, wz)) {
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
            WorldGenLib.LOGGER.warn(
                    "Structure {} loot chest result: generated={}, failed={}, origin=[{},{},{}]",
                    this.schematicPath,
                    lootChestGenerated,
                    lootChestFailed,
                    originX,
                    originY,
                    originZ);
        } else if (lootChestGenerated > 0 && VERBOSE_GENERATION_LOG) {
            WorldGenLib.LOGGER.info(
                    "Structure {} generated {} marker loot chests at [{},{},{}]",
                    this.schematicPath,
                    lootChestGenerated,
                    originX,
                    originY,
                    originZ);
        }

        EntitySpawnResult entitySpawnResult = spawnEntities(world, originX, originY, originZ);
        if (entitySpawnResult.failed > 0) {
            WorldGenLib.LOGGER.warn(
                    "Structure {} entity spawn result: spawned={}, replaced={}, skipped={}, failed={}, origin=[{},{},{}]",
                    this.schematicPath,
                    entitySpawnResult.spawned,
                    entitySpawnResult.replaced,
                    entitySpawnResult.skipped,
                    entitySpawnResult.failed,
                    originX,
                    originY,
                    originZ);
        } else if (VERBOSE_GENERATION_LOG
                && (entitySpawnResult.spawned > 0
                || entitySpawnResult.replaced > 0
                || entitySpawnResult.skipped > 0)) {
            WorldGenLib.LOGGER.info(
                    "Structure {} entity result: spawned={}, replaced={}, skipped={} at [{},{},{}]",
                    this.schematicPath,
                    entitySpawnResult.spawned,
                    entitySpawnResult.replaced,
                    entitySpawnResult.skipped,
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
        if (world == null || this.entityTemplates.isEmpty()) {
            return result;
        }
        StructureEntityReplacementProfile replacementProfile = this.entityReplacementEnabled
                ? this.entityReplacementProfileSupplier.get()
                : null;

        for (NBTTagCompound sourceTag : this.entityTemplates) {
            if (sourceTag == null) {
                ++result.failed;
                continue;
            }

            EntityReplacementResult replacement = this.entityReplacementEnabled
                    ? applyEntityReplacement(sourceTag, this.schematicPath, replacementProfile)
                    : EntityReplacementResult.keepOriginal(sourceTag.getString("id"));
            if (replacement.skip) {
                ++result.skipped;
                continue;
            }
            if (!replacement.valid) {
                ++result.failed;
                continue;
            }
            if (replacement.replaced) {
                ++result.replaced;
            }

            NBTTagCompound worldTag = (NBTTagCompound) sourceTag.copy();
            if (replacement.replaced && replacement.replacementId != null) {
                worldTag.setString("id", replacement.replacementId);
            }
            offsetEntityPosition(worldTag, originX, originY, originZ);

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
                // Apply equipment/drops after spawn to avoid late spawn hooks overriding pre-spawn armor slots.
                postProcessReplacedEntity(copied, replacement.levelRule);
                ++result.spawned;
            } else {
                if (copied instanceof EntityLivingBase) {
                    StructureEntityReplacementRuntime.unregisterDrops((EntityLivingBase) copied);
                }
                ++result.failed;
            }
        }
        return result;
    }

    private static EntityReplacementResult applyEntityReplacement(
            NBTTagCompound tag,
            String structureKey,
            StructureEntityReplacementProfile profile) {
        if (tag == null) {
            return EntityReplacementResult.invalid();
        }

        StructureEntityReplacementRules.ReplacementDecision decision =
                StructureEntityReplacementRules.resolve(structureKey, tag, profile);
        String sourceId = decision.sourceId();
        String replacementId = decision.replacementId();
        if (replacementId == null) {
            return EntityReplacementResult.skip();
        }

        String normalizedReplacementId = StringNormalization.trimToNull(replacementId);
        if (normalizedReplacementId == null) {
            return EntityReplacementResult.invalid();
        }

        boolean replaced = sourceId == null || !normalizedReplacementId.equals(sourceId);

        StructureEntityReplacementProfile.LevelRule levelRule = decision.levelRule();
        if (DEBUG_ENTITY_REPLACEMENT && decision.detectedLevel() > 0) {
            WorldGenLib.LOGGER.info(
                    "Entity replacement debug: structure={}, rule={}, level={}, sourceId={}, targetId={}, replaced={}, hasEquipment={}, customDropCount={}",
                    structureKey,
                    decision.matchedRuleId(),
                    decision.detectedLevel(),
                    sourceId,
                    normalizedReplacementId,
                    replaced,
                    levelRule != null && !levelRule.equipment().isEmpty(),
                    levelRule == null ? 0 : levelRule.dropCount());
        }

        return EntityReplacementResult.valid(replaced, normalizedReplacementId, levelRule);
    }

    private static void postProcessReplacedEntity(
            Entity entity, StructureEntityReplacementProfile.LevelRule levelRule) {
        if (!(entity instanceof EntityLivingBase) || levelRule == null) {
            return;
        }

        EntityLivingBase living = (EntityLivingBase) entity;
        applyEquipment(living, levelRule.equipment());
        StructureEntityReplacementProfile.EntityDrop[] drops = levelRule.dropsInternal();
        if (drops.length > 0) {
            StructureEntityReplacementRuntime.registerDropsInternal(living, drops);
        }
    }

    private static void applyEquipment(
            EntityLivingBase entity, StructureEntityReplacementProfile.EntityEquipment equipment) {
        if (entity == null || equipment == null || equipment.isEmpty()) {
            return;
        }
        applyEquipmentSlot(entity, 0, equipment.mainHandInternal());
        applyEquipmentSlot(entity, 1, equipment.bootsInternal());
        applyEquipmentSlot(entity, 2, equipment.leggingsInternal());
        applyEquipmentSlot(entity, 3, equipment.chestplateInternal());
        applyEquipmentSlot(entity, 4, equipment.helmetInternal());
    }

    private static void applyEquipmentSlot(EntityLivingBase entity, int slot, ItemStack template) {
        if (!StructureItemStacks.isUsable(template)) {
            return;
        }
        ItemStack copied = StructureItemStacks.safeClone(template);
        if (StructureItemStacks.isUsable(copied)) {
            entity.setCurrentItemOrArmor(slot, copied);
        }
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
            WorldGenLib.LOGGER.warn("Structure file load failed: {}", this.schematicPath);
            return false;
        }
        this.tileEntityTemplates = buildTileEntityTemplates(this.schematicData, this.lootTableEnabled, this.lootProfile);
        this.entityTemplates = buildEntityTemplates(this.schematicData);

        WorldGenLib.LOGGER.info(
                "Structure file loaded: {} ({}x{}x{}), tileEntities={}, entities={}",
                this.schematicPath,
                this.schematicData.getWidth(),
                this.schematicData.getHeight(),
                this.schematicData.getLength(),
                this.schematicData.getTileEntityTags().size(),
                this.schematicData.getEntityTags().size());
        return true;
    }

    private static int resolveBlockSetFlags() {
        Integer configured = Integer.getInteger("worldgenlib.generation.setBlockFlags");
        if (configured == null) {
            configured = Integer.getInteger("schematica.generation.setBlockFlags");
        }
        if (configured == null) {
            return 2;
        }
        return Math.max(0, configured.intValue());
    }

    private int nextBlockResolveToken() {
        if (this.blockResolveToken == Integer.MAX_VALUE) {
            this.blockResolveToken = 1;
            for (int i = 0; i < this.blockResolveTokens.length; ++i) {
                this.blockResolveTokens[i] = 0;
            }
        }
        return this.blockResolveToken++;
    }

    private Block resolveBlock(int blockId, int resolveToken) {
        if (blockId <= 0) {
            return null;
        }
        if (blockId < this.blockCache.length && blockId < this.blockResolveTokens.length) {
            if (this.blockResolveTokens[blockId] != resolveToken) {
                this.blockCache[blockId] = Block.getBlock(blockId);
                this.blockResolveTokens[blockId] = resolveToken;
            }
            return this.blockCache[blockId];
        }
        return Block.getBlock(blockId);
    }

    private static int schematicIndex(int width, int length, int x, int y, int z) {
        return x + (y * length + z) * width;
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
            if (recreated instanceof IInventory) {
                ((IInventory) recreated).onInventoryChanged();
            }
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static List<TileEntityTemplate> buildTileEntityTemplates(
            SchematicData data,
            boolean lootTableEnabled,
            StructureLootProfile lootProfile) {
        if (data == null) {
            return Collections.emptyList();
        }
        List<NBTTagCompound> tags = data.getTileEntityTags();
        if (tags == null || tags.isEmpty()) {
            return Collections.emptyList();
        }

        int width = data.getWidth();
        int height = data.getHeight();
        int length = data.getLength();
        int[] blockIds = data.getBlockIds();
        List<TileEntityTemplate> out = new ArrayList<TileEntityTemplate>(tags.size());
        for (NBTTagCompound sourceTag : tags) {
            if (sourceTag == null) {
                continue;
            }
            int sx = sourceTag.getInteger("x");
            int sy = sourceTag.getInteger("y");
            int sz = sourceTag.getInteger("z");
            if (sx < 0 || sy < 0 || sz < 0 || sx >= width || sy >= height || sz >= length) {
                continue;
            }
            int sourceBlockId = blockIds[schematicIndex(width, length, sx, sy, sz)];
            if (sourceBlockId <= 0) {
                continue;
            }

            int lootLevel = 0;
            if (lootTableEnabled) {
                Block sourceBlock = Block.getBlock(sourceBlockId);
                lootLevel = detectLootChestLevel(sourceBlock, sourceTag, lootProfile);
            }
            out.add(new TileEntityTemplate(
                    sx,
                    sy,
                    sz,
                    sourceBlockId,
                    lootLevel,
                    (NBTTagCompound) sourceTag.copy()));
        }
        if (out.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(out);
    }

    private static List<NBTTagCompound> buildEntityTemplates(SchematicData data) {
        if (data == null) {
            return Collections.emptyList();
        }
        List<NBTTagCompound> tags = data.getEntityTags();
        if (tags == null || tags.isEmpty()) {
            return Collections.emptyList();
        }

        List<NBTTagCompound> out = new ArrayList<NBTTagCompound>(tags.size());
        for (NBTTagCompound sourceTag : tags) {
            if (sourceTag == null) {
                continue;
            }
            NBTTagCompound template = (NBTTagCompound) sourceTag.copy();
            clearEntityIdentity(template);
            out.add(template);
        }
        if (out.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(out);
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

        int markerItemId = -1;
        for (int i = 0; i < items.tagCount(); ++i) {
            NBTBase base = items.tagAt(i);
            if (!(base instanceof NBTTagCompound)) {
                continue;
            }
            int itemId = readItemIdFromItemTag((NBTTagCompound) base);
            if (itemId <= 0) {
                continue;
            }

            if (markerItemId > 0) {
                return 0;
            }
            markerItemId = itemId;
        }

        return StructureLootProfiles.getLevelForMarker(markerItemId, lootProfile);
    }

    private static int readItemIdFromItemTag(NBTTagCompound itemTag) {
        if (itemTag == null) {
            return -1;
        }

        int count = 0;
        if (itemTag.hasKey("Count")) {
            count = itemTag.getByte("Count");
        } else if (itemTag.hasKey("count")) {
            count = itemTag.getInteger("count");
        }
        if (count <= 0) {
            return -1;
        }

        int itemId = itemTag.hasKey("id") ? itemTag.getShort("id") : -1;
        if (itemId <= 0) {
            itemId = itemTag.getInteger("id");
        }
        if (itemId <= 0 && itemTag.hasKey("itemId")) {
            itemId = itemTag.getInteger("itemId");
        }
        if (itemId > 0) {
            return itemId;
        }

        if (itemTag.hasKey("id")) {
            Integer resolved = StructureItemIdResolver.resolveItemIdByName(itemTag.getString("id"));
            if (resolved != null && resolved.intValue() > 0) {
                return resolved.intValue();
            }
        }
        if (itemTag.hasKey("item")) {
            Integer resolved = StructureItemIdResolver.resolveItemIdByName(itemTag.getString("item"));
            if (resolved != null && resolved.intValue() > 0) {
                return resolved.intValue();
            }
        }
        return -1;
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
            ((NBTTagDouble) pos.tagAt(0)).data += offsetX;
            ((NBTTagDouble) pos.tagAt(1)).data += offsetY;
            ((NBTTagDouble) pos.tagAt(2)).data += offsetZ;
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

    private static String resolveStructureNameForSpacing(String structureName, String schematicPath) {
        String trimmedStructureName = StringNormalization.trimToNull(structureName);
        if (trimmedStructureName != null) {
            return trimmedStructureName;
        }
        String path = schematicPath;
        if (path == null || path.isEmpty()) {
            return "unknown";
        }
        String normalized = path.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String name = slash >= 0 && slash + 1 < normalized.length()
                ? normalized.substring(slash + 1)
                : normalized;
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".schematic")) {
            name = name.substring(0, name.length() - ".schematic".length());
        }
        return name.isEmpty() ? "unknown" : name;
    }

    private static final class TileEntityTemplate {
        private final int localX;
        private final int localY;
        private final int localZ;
        private final int sourceBlockId;
        private final int lootLevel;
        private final NBTTagCompound tag;

        private TileEntityTemplate(
                int localX,
                int localY,
                int localZ,
                int sourceBlockId,
                int lootLevel,
                NBTTagCompound tag) {
            this.localX = localX;
            this.localY = localY;
            this.localZ = localZ;
            this.sourceBlockId = sourceBlockId;
            this.lootLevel = lootLevel;
            this.tag = tag;
        }
    }

    private static final class EntitySpawnResult {
        private int spawned;
        private int failed;
        private int replaced;
        private int skipped;
    }

    private static final class EntityReplacementResult {
        private final boolean valid;
        private final boolean skip;
        private final boolean replaced;
        private final String replacementId;
        private final StructureEntityReplacementProfile.LevelRule levelRule;

        private EntityReplacementResult(
                boolean valid,
                boolean skip,
                boolean replaced,
                String replacementId,
                StructureEntityReplacementProfile.LevelRule levelRule) {
            this.valid = valid;
            this.skip = skip;
            this.replaced = replaced;
            this.replacementId = replacementId;
            this.levelRule = levelRule;
        }

        private static EntityReplacementResult valid(
                boolean replaced,
                String replacementId,
                StructureEntityReplacementProfile.LevelRule levelRule) {
            return new EntityReplacementResult(true, false, replaced, replacementId, levelRule);
        }

        private static EntityReplacementResult keepOriginal(String sourceId) {
            return new EntityReplacementResult(true, false, false, sourceId, null);
        }

        private static EntityReplacementResult invalid() {
            return new EntityReplacementResult(false, false, false, null, null);
        }

        private static EntityReplacementResult skip() {
            return new EntityReplacementResult(false, true, false, null, null);
        }
    }
}
