package com.github.hahahha.WorldGenLib.item.treasure;

import com.github.hahahha.WorldGenLib.WorldGenLib;
import com.github.hahahha.WorldGenLib.util.StringNormalization;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.CreativeTabs;
import net.xiaoyu233.fml.reload.event.ItemRegistryEvent;
import net.xiaoyu233.fml.reload.utils.IdUtil;

public final class TreasureMapRegistry {
    public static final String DEFAULT_TREASURE_MAP_ID = WorldGenLib.MOD_ID + ":treasure_map";

    private static final Map<String, TreasureMapDefinition> DEFINITIONS =
            new LinkedHashMap<String, TreasureMapDefinition>(8);
    private static final Map<String, ItemTreasureMap> REGISTERED_ITEMS =
            new LinkedHashMap<String, ItemTreasureMap>(8);
    private static boolean frozen = false;

    static {
        registerInternal(TreasureMapDefinition.builder(DEFAULT_TREASURE_MAP_ID, "stronghold")
                .textureName("treasure_map")
                .unlocalizedName("treasure_map")
                .itemName("item.treasure_map.name", "Treasure Map")
                .targetDisplayName("Stronghold")
                .build());
    }

    private TreasureMapRegistry() {
    }

    public static synchronized void register(TreasureMapDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("definition cannot be null");
        }
        if (frozen) {
            throw new IllegalStateException(
                    "Treasure map registry is frozen. Register maps before item registration event.");
        }
        registerInternal(definition);
    }

    public static synchronized List<TreasureMapDefinition> listDefinitions() {
        return new ArrayList<TreasureMapDefinition>(DEFINITIONS.values());
    }

    public static synchronized ItemTreasureMap getItem(String id) {
        String normalized = normalizeLookupId(id);
        if (normalized == null) {
            return null;
        }
        return REGISTERED_ITEMS.get(normalized);
    }

    public static synchronized TreasureMapDefinition getDefinition(String id) {
        String normalized = normalizeLookupId(id);
        if (normalized == null) {
            return null;
        }
        return DEFINITIONS.get(normalized);
    }

    public static synchronized void registerItems(ItemRegistryEvent event) {
        if (event == null) {
            return;
        }

        frozen = true;
        for (TreasureMapDefinition definition : DEFINITIONS.values()) {
            if (REGISTERED_ITEMS.get(definition.id()) != null) {
                continue;
            }
            ItemTreasureMap item = new ItemTreasureMap(IdUtil.getNextItemID(), definition);
            event.register(
                    definition.namespace(),
                    definition.textureName(),
                    definition.unlocalizedName(),
                    item,
                    CreativeTabs.tabTools);
            REGISTERED_ITEMS.put(definition.id(), item);

            WorldGenLib.LOGGER.info(
                    "Registered treasure map id={} query={} radius={} texture={}/{}",
                    definition.id(),
                    definition.structureQuery(),
                    definition.searchRadius(),
                    definition.namespace(),
                    definition.textureName());
        }
    }

    private static void registerInternal(TreasureMapDefinition definition) {
        String id = definition.id();
        TreasureMapDefinition previous = DEFINITIONS.putIfAbsent(id, definition);
        if (previous != null) {
            throw new IllegalArgumentException("Treasure map id already registered: " + id);
        }
    }

    private static String normalizeLookupId(String id) {
        String normalized = StringNormalization.trimToNull(id);
        if (normalized == null) {
            return null;
        }
        return TreasureMapDefinition.normalizeId(normalized);
    }
}
