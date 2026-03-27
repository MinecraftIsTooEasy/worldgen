package com.github.hahahha.WorldGen.world.structure;

import java.util.Random;
import net.minecraft.Item;
import net.minecraft.ItemStack;
import net.minecraft.WeightedRandomChestContent;

/**
 * Default editable loot table for schematic marker chests.
 *
 * Marker mapping:
 * stick -> level 1
 * flint -> level 2
 * coal -> level 3
 * iron ingot -> level 4
 * gold ingot -> level 5
 * diamond -> level 6
 */
public final class WeightedTreasurePieces {
    private static final int MIN_LEVEL = 1;
    private static final int MAX_LEVEL = 6;
    private static final WeightedRandomChestContent[] EMPTY = new WeightedRandomChestContent[0];

    public static final WeightedRandomChestContent[][] LOOT_TABLES = new WeightedRandomChestContent[][]{
            EMPTY,
            new WeightedRandomChestContent[]{
                    entry(Item.stick, 1, 1, 25)
            },
            new WeightedRandomChestContent[]{
                    entry(Item.flint, 1, 1, 20)
            },
            new WeightedRandomChestContent[]{
                    entry(Item.coal, 1, 1, 24)
            },
            new WeightedRandomChestContent[]{
                    entry(Item.ingotIron, 1, 1, 22)
            },
            new WeightedRandomChestContent[]{
                    entry(Item.ingotGold, 1, 1, 22)
            },
            new WeightedRandomChestContent[]{
                    entry(Item.diamond, 1, 1, 16)
            }
    };

    public static final int[] MIN_ROLLS = new int[]{0, 3, 3, 4, 4, 5, 6};
    public static final int[] MAX_ROLLS = new int[]{0, 5, 5, 6, 7, 8, 9};

    public static final float[][] ARTIFACT_CHANCES = new float[7][];

    private WeightedTreasurePieces() {
    }

    public static int getLevelForMarker(ItemStack markerStack) {
        if (markerStack == null || markerStack.getItem() == null) {
            return 0;
        }

        int itemId = markerStack.itemID;
        if (itemId == Item.stick.itemID) {
            return 1;
        }
        if (itemId == Item.flint.itemID) {
            return 2;
        }
        if (itemId == Item.coal.itemID) {
            return 3;
        }
        if (itemId == Item.ingotIron.itemID) {
            return 4;
        }
        if (itemId == Item.ingotGold.itemID) {
            return 5;
        }
        if (itemId == Item.diamond.itemID) {
            return 6;
        }
        return 0;
    }

    public static WeightedRandomChestContent[] getContentsForLevel(int level) {
        if (!isValidLevel(level)) {
            return EMPTY;
        }
        return LOOT_TABLES[level];
    }

    public static int getRollCount(Random random, int level) {
        if (!isValidLevel(level)) {
            return 0;
        }
        int min = MIN_ROLLS[level];
        int max = MAX_ROLLS[level];
        if (max < min) {
            max = min;
        }
        if (random == null || max == min) {
            return min;
        }
        return min + random.nextInt(max - min + 1);
    }

    public static float[] getArtifactChances(int level) {
        if (!isValidLevel(level)) {
            return null;
        }
        return ARTIFACT_CHANCES[level];
    }

    private static boolean isValidLevel(int level) {
        return level >= MIN_LEVEL && level <= MAX_LEVEL;
    }

    private static WeightedRandomChestContent entry(Item item, int minQuantity, int maxQuantity, int weight) {
        return new WeightedRandomChestContent(item.itemID, 0, minQuantity, maxQuantity, weight);
    }
}
