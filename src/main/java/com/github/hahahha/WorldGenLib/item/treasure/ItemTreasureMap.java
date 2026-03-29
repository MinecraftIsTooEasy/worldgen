package com.github.hahahha.WorldGenLib.item.treasure;

import com.github.hahahha.WorldGenLib.util.I18n;
import com.github.hahahha.WorldGenLib.util.StringNormalization;
import com.github.hahahha.WorldGenLib.world.structure.data.StructureGenerationDataStore;
import com.github.hahahha.WorldGenLib.world.structure.vanilla.VanillaStructureSearchService;
import com.github.hahahha.WorldGenLib.world.structure.vanilla.VanillaStructureSearchService.VanillaStructureResult;
import com.github.hahahha.WorldGenLib.world.structure.vanilla.VanillaStructureSearchService.VanillaStructureType;
import java.util.List;
import net.minecraft.ChatMessageComponent;
import net.minecraft.EntityPlayer;
import net.minecraft.Item;
import net.minecraft.ItemStack;
import net.minecraft.Material;
import net.minecraft.NBTTagCompound;
import net.minecraft.Slot;
import net.minecraft.World;

public class ItemTreasureMap extends Item {
    private static final String KEY_BOUND = "wgm_treasure_bound";
    private static final String KEY_TARGET_DIMENSION = "wgm_treasure_dimension";
    private static final String KEY_TARGET_X = "wgm_treasure_x";
    private static final String KEY_TARGET_Y = "wgm_treasure_y";
    private static final String KEY_TARGET_Z = "wgm_treasure_z";
    private static final String KEY_TARGET_LABEL = "wgm_treasure_label";
    private static final String KEY_TARGET_QUERY = "wgm_treasure_query";

    private final TreasureMapDefinition definition;

    public ItemTreasureMap(int id, TreasureMapDefinition definition) {
        super(id, Material.iron, "compass");
        this.definition = definition;
        this.addMaterial(Material.redstone);
        this.setMaxStackSize(1);
        this.setUnlocalizedName(definition.unlocalizedName());
    }

    @Override
    public boolean onItemRightClick(EntityPlayer player, float partial_tick, boolean ctrl_is_down) {
        if (player == null || player.onClient()) {
            return true;
        }

        World world = player.worldObj;
        if (world == null) {
            send(player, t(
                    "WorldGenLib.item.treasure_map.world_unavailable",
                    "Current world is unavailable, cannot search structures."), true);
            return true;
        }

        ItemStack held = player.getHeldItemStack();
        if (held == null || held.getItem() != this) {
            return true;
        }

        NBTTagCompound tag = held.getTagCompound();
        if (isBound(tag)) {
            sendBoundTarget(player, tag);
            return true;
        }

        SearchResult result = findNearest(world, player);
        if (result == null) {
            return true;
        }

        NBTTagCompound boundTag = tag != null ? tag : new NBTTagCompound();
        boundTag.setBoolean(KEY_BOUND, true);
        boundTag.setInteger(KEY_TARGET_DIMENSION, result.dimensionId);
        boundTag.setInteger(KEY_TARGET_X, result.x);
        boundTag.setInteger(KEY_TARGET_Y, result.y);
        boundTag.setInteger(KEY_TARGET_Z, result.z);
        boundTag.setString(KEY_TARGET_LABEL, result.label);
        boundTag.setString(KEY_TARGET_QUERY, this.definition.structureQuery());
        held.setTagCompound(boundTag);

        send(
                player,
                tf(
                        "WorldGenLib.item.treasure_map.bind.success",
                        "Bound target %s at (%d, %d, %d), distance: %d.",
                        result.label,
                        result.x,
                        result.y,
                        result.z,
                        result.distance),
                true);
        send(
                player,
                t(
                        "WorldGenLib.item.treasure_map.bind.locked",
                        "This treasure map is now locked to this coordinate."),
                false);
        return true;
    }

    @Override
    public String getItemDisplayName(ItemStack itemStack) {
        return I18n.tr(this.definition.itemTranslationKey(), this.definition.itemFallbackName());
    }

    @Override
    public String getItemDisplayName() {
        return I18n.tr(this.definition.itemTranslationKey(), this.definition.itemFallbackName());
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void addInformation(ItemStack itemStack, EntityPlayer player, List info, boolean extended_info, Slot slot) {
        if (itemStack == null || info == null) {
            return;
        }

        NBTTagCompound tag = itemStack.getTagCompound();
        if (!isBound(tag)) {
            info.add(tf(
                    "WorldGenLib.item.treasure_map.tooltip.target",
                    "Target: %s",
                    this.definition.preferredTargetLabel()));
            info.add(t(
                    "WorldGenLib.item.treasure_map.tooltip.unbound",
                    "Right click to find nearest target and lock this map."));
            return;
        }

        int dimension = tag.getInteger(KEY_TARGET_DIMENSION);
        int x = tag.getInteger(KEY_TARGET_X);
        int y = tag.getInteger(KEY_TARGET_Y);
        int z = tag.getInteger(KEY_TARGET_Z);
        String label = resolveStoredLabel(tag);

        info.add(tf("WorldGenLib.item.treasure_map.tooltip.bound", "Bound: %s", label));
        info.add(tf("WorldGenLib.item.treasure_map.tooltip.position", "Pos: (%d, %d, %d)", x, y, z));
        info.add(tf(
                "WorldGenLib.item.treasure_map.tooltip.dimension",
                "Dimension: %s",
                getDimensionLabel(dimension)));
    }

    private SearchResult findNearest(World world, EntityPlayer player) {
        String query = this.definition.structureQuery();
        VanillaStructureType vanillaType = VanillaStructureSearchService.matchType(query);
        if (vanillaType != null) {
            if (!vanillaType.supportsDimension(world.getDimensionId())) {
                send(
                        player,
                        tf(
                                "WorldGenLib.item.treasure_map.search.unsupported_dimension",
                                "Target %s does not generate in current dimension (%s).",
                                vanillaType.displayLabel(),
                                getDimensionLabel(world.getDimensionId())),
                        true);
                return null;
            }

            List<VanillaStructureResult> results = VanillaStructureSearchService.findNearby(
                    world,
                    vanillaType,
                    player.posX,
                    player.posZ,
                    this.definition.searchRadius(),
                    1);
            if (results.isEmpty()) {
                sendNotFound(player, vanillaType.displayLabel());
                return null;
            }

            VanillaStructureResult result = results.get(0);
            return new SearchResult(
                    world.getDimensionId(),
                    result.x(),
                    result.y(),
                    result.z(),
                    (int) Math.round(Math.sqrt(result.distanceSq())),
                    vanillaType.displayLabel());
        }

        List<StructureGenerationDataStore.StructureSearchResult> results = StructureGenerationDataStore.findNearby(
                world,
                world.getDimensionId(),
                player.posX,
                player.posZ,
                query,
                this.definition.searchRadius(),
                1);
        if (results.isEmpty()) {
            sendNotFound(player, this.definition.preferredTargetLabel());
            return null;
        }

        StructureGenerationDataStore.StructureSearchResult result = results.get(0);
        StructureGenerationDataStore.StructureRecord record = result.record();
        return new SearchResult(
                world.getDimensionId(),
                record.x(),
                record.y(),
                record.z(),
                (int) Math.round(Math.sqrt(result.distanceSq())),
                record.structureName());
    }

    private void sendNotFound(EntityPlayer player, String targetLabel) {
        send(
                player,
                tf(
                        "WorldGenLib.item.treasure_map.search.not_found",
                        "No structure found within %d blocks: %s",
                        this.definition.searchRadius(),
                        targetLabel),
                true);
    }

    private void sendBoundTarget(EntityPlayer player, NBTTagCompound tag) {
        int dimension = tag.getInteger(KEY_TARGET_DIMENSION);
        int x = tag.getInteger(KEY_TARGET_X);
        int y = tag.getInteger(KEY_TARGET_Y);
        int z = tag.getInteger(KEY_TARGET_Z);
        String label = resolveStoredLabel(tag);

        send(
                player,
                tf(
                        "WorldGenLib.item.treasure_map.bind.current",
                        "Fixed target %s at (%d, %d, %d), dimension: %s",
                        label,
                        x,
                        y,
                        z,
                        getDimensionLabel(dimension)),
                true);
    }

    private static boolean isBound(NBTTagCompound tag) {
        return tag != null && tag.hasKey(KEY_BOUND) && tag.getBoolean(KEY_BOUND);
    }

    private String resolveStoredLabel(NBTTagCompound tag) {
        if (tag == null || !tag.hasKey(KEY_TARGET_LABEL)) {
            return this.definition.preferredTargetLabel();
        }
        String value = tag.getString(KEY_TARGET_LABEL);
        return isBlank(value) ? this.definition.preferredTargetLabel() : value;
    }

    private static String getDimensionLabel(int dimensionId) {
        if (dimensionId == -1) {
            return t("WorldGenLib.dimension.nether", "Nether");
        }
        if (dimensionId == 0) {
            return t("WorldGenLib.dimension.overworld", "Overworld");
        }
        if (dimensionId == 1) {
            return t("WorldGenLib.dimension.end", "The End");
        }
        return tf("WorldGenLib.dimension.unknown", "Unknown(%d)", dimensionId);
    }

    private static String t(String key, String fallback) {
        return I18n.tr(key, fallback);
    }

    private static String tf(String key, String fallbackFormat, Object... args) {
        return I18n.trf(key, fallbackFormat, args);
    }

    private static boolean isBlank(String value) {
        return StringNormalization.isBlank(value);
    }

    private static void send(EntityPlayer player, String text, boolean withPrefix) {
        if (player == null || text == null || text.isEmpty()) {
            return;
        }

        String message = withPrefix
                ? tf("WorldGenLib.item.treasure_map.prefix", "[Treasure Map] %s", text)
                : text;
        player.sendChatToPlayer(ChatMessageComponent.createFromText(message));
    }

    private static final class SearchResult {
        private final int dimensionId;
        private final int x;
        private final int y;
        private final int z;
        private final int distance;
        private final String label;

        private SearchResult(int dimensionId, int x, int y, int z, int distance, String label) {
            this.dimensionId = dimensionId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.distance = distance;
            this.label = label;
        }
    }
}
