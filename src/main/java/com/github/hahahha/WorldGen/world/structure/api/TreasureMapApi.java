package com.github.hahahha.WorldGen.world.structure.api;

import com.github.hahahha.WorldGen.item.treasure.ItemTreasureMap;
import com.github.hahahha.WorldGen.item.treasure.TreasureMapDefinition;
import com.github.hahahha.WorldGen.item.treasure.TreasureMapRegistry;
import java.util.List;

public final class TreasureMapApi {
    private TreasureMapApi() {
    }

    public static TreasureMapDefinition.Builder builder(String mapId, String structureQuery) {
        return TreasureMapDefinition.builder(mapId, structureQuery);
    }

    public static void register(TreasureMapDefinition definition) {
        TreasureMapRegistry.register(definition);
    }

    public static void register(String mapId, String structureQuery) {
        register(builder(mapId, structureQuery).build());
    }

    public static ItemTreasureMap getItem(String mapId) {
        return TreasureMapRegistry.getItem(mapId);
    }

    public static List<TreasureMapDefinition> listDefinitions() {
        return TreasureMapRegistry.listDefinitions();
    }
}
