package com.github.hahahha.WorldGenLib.item;

import com.github.hahahha.WorldGenLib.WorldGenLib;
import com.github.hahahha.WorldGenLib.item.treasure.TreasureMapRegistry;
import net.minecraft.CreativeTabs;
import net.minecraft.Item;
import net.xiaoyu233.fml.reload.event.ItemRegistryEvent;
import net.xiaoyu233.fml.reload.utils.IdUtil;

public final class Items {
    private static int getNextItemID() {
        return IdUtil.getNextItemID();
    }

    public static final Item STRUCTURE_COMPASS = new ItemStructureCompass(getNextItemID());

    private Items() {
    }

    public static void registerItems(ItemRegistryEvent event) {
        // Args are (namespace, textureName, unlocalizedName, item, tab)
        event.register(WorldGenLib.MOD_ID, "compass", "structure_compass", STRUCTURE_COMPASS, CreativeTabs.tabTools);
        TreasureMapRegistry.registerItems(event);
    }
}
