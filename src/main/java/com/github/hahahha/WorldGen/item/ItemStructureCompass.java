package com.github.hahahha.WorldGen.item;

import com.github.hahahha.WorldGen.client.gui.GuiStructureCompassSearch;
import com.github.hahahha.WorldGen.util.I18n;
import net.minecraft.EntityPlayer;
import net.minecraft.Item;
import net.minecraft.ItemStack;
import net.minecraft.Material;
import net.minecraft.Minecraft;

public class ItemStructureCompass extends Item {
    public ItemStructureCompass(int id) {
        // Keep the same base material setup as vanilla compass:
        // new Item(89, Material.iron, "compass").addMaterial(Material.redstone)
        super(id, Material.iron, "compass");
        this.addMaterial(Material.redstone);
        this.setMaxStackSize(1);
        this.setUnlocalizedName("structure_compass");
    }

    @Override
    public boolean onItemRightClick(EntityPlayer player, float partial_tick, boolean ctrl_is_down) {
        if (player != null && player.onClient()) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null) {
                mc.displayGuiScreen(new GuiStructureCompassSearch());
            }
        }
        return true;
    }

    @Override
    public String getItemDisplayName(ItemStack itemStack) {
        return resolveDisplayName();
    }

    @Override
    public String getItemDisplayName() {
        return resolveDisplayName();
    }

    private static String resolveDisplayName() {
        return I18n.tr("item.structure_compass.name", "Structure Compass");
    }
}
