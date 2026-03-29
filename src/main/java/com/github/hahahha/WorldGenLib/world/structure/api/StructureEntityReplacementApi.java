package com.github.hahahha.WorldGenLib.world.structure.api;

import com.github.hahahha.WorldGenLib.util.StringNormalization;
import com.github.hahahha.WorldGenLib.world.structure.StructureEntityReplacementProfile;
import com.github.hahahha.WorldGenLib.world.structure.StructureEntityReplacementProfiles;
import com.github.hahahha.WorldGenLib.world.structure.StructureItemIdResolver;
import com.github.hahahha.WorldGenLib.world.structure.StructureItemStacks;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.Item;
import net.minecraft.ItemStack;

/**
 * Public API for custom structure entity replacement profiles.
 */
public final class StructureEntityReplacementApi {
    private StructureEntityReplacementApi() {
    }

    public static EntityReplacementProfileBuilder builder(String id) {
        return new EntityReplacementProfileBuilder(id);
    }

    public static StructureEntityReplacementProfile defaultProfile() {
        return StructureEntityReplacementProfiles.defaultProfile();
    }

    public static StructureEntityReplacementProfile.EntityDrop drop(
            Item item, int meta, int minCount, int maxCount, float chance) {
        if (item == null) {
            throw new IllegalArgumentException("item cannot be null");
        }
        return drop(item.itemID, meta, minCount, maxCount, chance);
    }

    public static StructureEntityReplacementProfile.EntityDrop drop(
            String itemName, int meta, int minCount, int maxCount, float chance) {
        return drop(resolveItemId(itemName), meta, minCount, maxCount, chance);
    }

    public static StructureEntityReplacementProfile.EntityDrop drop(
            int itemId, int meta, int minCount, int maxCount, float chance) {
        return new StructureEntityReplacementProfile.EntityDrop(itemId, meta, minCount, maxCount, chance);
    }

    public static ItemStack stack(Item item) {
        if (item == null) {
            throw new IllegalArgumentException("item cannot be null");
        }
        return new ItemStack(item);
    }

    public static ItemStack stack(String itemName) {
        return stack(resolveItemId(itemName), 1, 0);
    }

    public static ItemStack stack(Item item, int count, int meta) {
        if (item == null) {
            throw new IllegalArgumentException("item cannot be null");
        }
        return stack(item.itemID, count, meta);
    }

    public static ItemStack stack(String itemName, int count, int meta) {
        return stack(resolveItemId(itemName), count, meta);
    }

    public static ItemStack stack(int itemId, int count, int meta) {
        if (itemId <= 0) {
            throw new IllegalArgumentException("itemId must be > 0");
        }
        int safeCount = Math.max(1, count);
        int safeMeta = Math.max(0, meta);
        return new ItemStack(itemId, safeCount, safeMeta);
    }

    public static int resolveItemId(String itemName) {
        Integer resolved = StructureItemIdResolver.resolveItemIdByName(itemName);
        if (resolved != null) {
            int itemId = resolved.intValue();
            if (itemId > 0) {
                return itemId;
            }
        }
        throw new IllegalArgumentException("unknown item name: " + itemName);
    }

    public static final class EntityReplacementProfileBuilder {
        private final String id;
        private final Map<Integer, MutableLevelRule> rules = new LinkedHashMap<Integer, MutableLevelRule>(4);

        private EntityReplacementProfileBuilder(String id) {
            this.id = id;
        }

        public EntityReplacementProfileBuilder level(int level, String targetEntityId) {
            MutableLevelRule rule = getOrCreate(level);
            rule.targetEntityId = targetEntityId;
            return this;
        }

        public EntityReplacementProfileBuilder mainHand(int level, Item item) {
            return mainHand(level, stack(item));
        }

        public EntityReplacementProfileBuilder mainHand(int level, String itemName) {
            return mainHand(level, stack(itemName));
        }

        public EntityReplacementProfileBuilder mainHand(int level, ItemStack stack) {
            MutableLevelRule rule = getOrCreate(level);
            rule.mainHand = copy(stack);
            return this;
        }

        public EntityReplacementProfileBuilder boots(int level, Item item) {
            return boots(level, stack(item));
        }

        public EntityReplacementProfileBuilder boots(int level, String itemName) {
            return boots(level, stack(itemName));
        }

        public EntityReplacementProfileBuilder boots(int level, ItemStack stack) {
            MutableLevelRule rule = getOrCreate(level);
            rule.boots = copy(stack);
            return this;
        }

        public EntityReplacementProfileBuilder leggings(int level, Item item) {
            return leggings(level, stack(item));
        }

        public EntityReplacementProfileBuilder leggings(int level, String itemName) {
            return leggings(level, stack(itemName));
        }

        public EntityReplacementProfileBuilder leggings(int level, ItemStack stack) {
            MutableLevelRule rule = getOrCreate(level);
            rule.leggings = copy(stack);
            return this;
        }

        public EntityReplacementProfileBuilder chestplate(int level, Item item) {
            return chestplate(level, stack(item));
        }

        public EntityReplacementProfileBuilder chestplate(int level, String itemName) {
            return chestplate(level, stack(itemName));
        }

        public EntityReplacementProfileBuilder chestplate(int level, ItemStack stack) {
            MutableLevelRule rule = getOrCreate(level);
            rule.chestplate = copy(stack);
            return this;
        }

        public EntityReplacementProfileBuilder helmet(int level, Item item) {
            return helmet(level, stack(item));
        }

        public EntityReplacementProfileBuilder helmet(int level, String itemName) {
            return helmet(level, stack(itemName));
        }

        public EntityReplacementProfileBuilder helmet(int level, ItemStack stack) {
            MutableLevelRule rule = getOrCreate(level);
            rule.helmet = copy(stack);
            return this;
        }

        public EntityReplacementProfileBuilder drop(int level, StructureEntityReplacementProfile.EntityDrop drop) {
            if (drop == null) {
                throw new IllegalArgumentException("drop cannot be null");
            }
            MutableLevelRule rule = getOrCreate(level);
            rule.drops.add(drop);
            return this;
        }

        public EntityReplacementProfileBuilder drop(
                int level,
                Item item,
                int meta,
                int minCount,
                int maxCount,
                float chance) {
            return drop(level, StructureEntityReplacementApi.drop(item, meta, minCount, maxCount, chance));
        }

        public EntityReplacementProfileBuilder drop(
                int level,
                String itemName,
                int meta,
                int minCount,
                int maxCount,
                float chance) {
            return drop(level, StructureEntityReplacementApi.drop(itemName, meta, minCount, maxCount, chance));
        }

        public EntityReplacementProfileBuilder drop(
                int level,
                int itemId,
                int meta,
                int minCount,
                int maxCount,
                float chance) {
            return drop(level, StructureEntityReplacementApi.drop(itemId, meta, minCount, maxCount, chance));
        }

        public StructureEntityReplacementProfile build() {
            StructureEntityReplacementProfiles.Builder builder = StructureEntityReplacementProfiles.builder(this.id);
            for (Map.Entry<Integer, MutableLevelRule> entry : this.rules.entrySet()) {
                int level = entry.getKey().intValue();
                MutableLevelRule rule = entry.getValue();
                String targetEntityId = normalizeTarget(rule.targetEntityId);
                if (targetEntityId == null) {
                    throw new IllegalArgumentException("targetEntityId must be provided for level " + level);
                }
                StructureEntityReplacementProfile.EntityEquipment equipment =
                        StructureEntityReplacementProfile.EntityEquipment.of(
                                rule.mainHand,
                                rule.boots,
                                rule.leggings,
                                rule.chestplate,
                                rule.helmet);
                StructureEntityReplacementProfile.EntityDrop[] drops =
                        rule.drops.isEmpty()
                                ? null
                                : rule.drops.toArray(new StructureEntityReplacementProfile.EntityDrop[rule.drops.size()]);
                builder.level(level, targetEntityId, equipment, drops);
            }
            return builder.build();
        }

        private static String normalizeTarget(String value) {
            return StringNormalization.trimToNull(value);
        }

        private MutableLevelRule getOrCreate(int level) {
            if (level <= 0) {
                throw new IllegalArgumentException("level must be > 0");
            }
            return this.rules.computeIfAbsent(level, ignored -> new MutableLevelRule());
        }

        private static ItemStack copy(ItemStack stack) {
            return StructureItemStacks.safeClone(stack);
        }

        private static final class MutableLevelRule {
            private String targetEntityId;
            private ItemStack mainHand;
            private ItemStack boots;
            private ItemStack leggings;
            private ItemStack chestplate;
            private ItemStack helmet;
            private final List<StructureEntityReplacementProfile.EntityDrop> drops =
                    new ArrayList<StructureEntityReplacementProfile.EntityDrop>(4);
        }
    }
}
