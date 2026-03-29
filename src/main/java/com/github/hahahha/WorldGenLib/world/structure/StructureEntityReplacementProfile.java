package com.github.hahahha.WorldGenLib.world.structure;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.ItemStack;

public final class StructureEntityReplacementProfile {
    private final String id;
    private final Map<Integer, LevelRule> levelRules;

    StructureEntityReplacementProfile(String id, Map<Integer, LevelRule> levelRules) {
        this.id = id;
        this.levelRules = Collections.unmodifiableMap(new LinkedHashMap<Integer, LevelRule>(levelRules));
    }

    public String id() {
        return this.id;
    }

    LevelRule levelRule(int level) {
        return this.levelRules.get(level);
    }

    public static final class LevelRule {
        private static final EntityDrop[] EMPTY_DROPS = new EntityDrop[0];

        private final String targetEntityId;
        private final EntityEquipment equipment;
        private final EntityDrop[] drops;

        LevelRule(String targetEntityId, EntityEquipment equipment, EntityDrop[] drops) {
            this.targetEntityId = targetEntityId;
            this.equipment = equipment == null ? EntityEquipment.empty() : equipment;
            this.drops = drops == null || drops.length == 0 ? EMPTY_DROPS : drops.clone();
        }

        public String targetEntityId() {
            return this.targetEntityId;
        }

        public EntityEquipment equipment() {
            return this.equipment;
        }

        public EntityDrop[] drops() {
            return this.drops.length == 0 ? EMPTY_DROPS : this.drops.clone();
        }

        int dropCount() {
            return this.drops.length;
        }

        EntityDrop[] dropsInternal() {
            return this.drops;
        }
    }

    public static final class EntityEquipment {
        private static final EntityEquipment EMPTY = new EntityEquipment(null, null, null, null, null);

        private final ItemStack mainHand;
        private final ItemStack boots;
        private final ItemStack leggings;
        private final ItemStack chestplate;
        private final ItemStack helmet;

        private EntityEquipment(
                ItemStack mainHand,
                ItemStack boots,
                ItemStack leggings,
                ItemStack chestplate,
                ItemStack helmet) {
            this.mainHand = copy(mainHand);
            this.boots = copy(boots);
            this.leggings = copy(leggings);
            this.chestplate = copy(chestplate);
            this.helmet = copy(helmet);
        }

        public static EntityEquipment empty() {
            return EMPTY;
        }

        public static EntityEquipment of(
                ItemStack mainHand,
                ItemStack boots,
                ItemStack leggings,
                ItemStack chestplate,
                ItemStack helmet) {
            if (mainHand == null
                    && boots == null
                    && leggings == null
                    && chestplate == null
                    && helmet == null) {
                return EMPTY;
            }
            return new EntityEquipment(mainHand, boots, leggings, chestplate, helmet);
        }

        public ItemStack mainHand() {
            return copy(this.mainHand);
        }

        ItemStack mainHandInternal() {
            return this.mainHand;
        }

        public ItemStack boots() {
            return copy(this.boots);
        }

        ItemStack bootsInternal() {
            return this.boots;
        }

        public ItemStack leggings() {
            return copy(this.leggings);
        }

        ItemStack leggingsInternal() {
            return this.leggings;
        }

        public ItemStack chestplate() {
            return copy(this.chestplate);
        }

        ItemStack chestplateInternal() {
            return this.chestplate;
        }

        public ItemStack helmet() {
            return copy(this.helmet);
        }

        ItemStack helmetInternal() {
            return this.helmet;
        }

        public boolean isEmpty() {
            return this.mainHand == null
                    && this.boots == null
                    && this.leggings == null
                    && this.chestplate == null
                    && this.helmet == null;
        }

        static ItemStack copy(ItemStack stack) {
            return StructureItemStacks.safeClone(stack);
        }
    }

    public static final class EntityDrop {
        private final int itemId;
        private final int meta;
        private final int minCount;
        private final int maxCount;
        private final float chance;

        public EntityDrop(int itemId, int meta, int minCount, int maxCount, float chance) {
            if (itemId <= 0) {
                throw new IllegalArgumentException("drop itemId must be > 0");
            }
            this.itemId = itemId;
            this.meta = Math.max(0, meta);
            int safeMin = Math.max(1, minCount);
            this.minCount = safeMin;
            this.maxCount = Math.max(safeMin, maxCount);
            float safeChance = chance;
            if (safeChance < 0.0F) {
                safeChance = 0.0F;
            } else if (safeChance > 1.0F) {
                safeChance = 1.0F;
            }
            this.chance = safeChance;
        }

        public int itemId() {
            return this.itemId;
        }

        public int meta() {
            return this.meta;
        }

        public int minCount() {
            return this.minCount;
        }

        public int maxCount() {
            return this.maxCount;
        }

        public float chance() {
            return this.chance;
        }
    }
}
