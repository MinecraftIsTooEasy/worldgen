package com.github.hahahha.WorldGenLib.world.structure;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;
import moddedmite.rustedironcore.api.event.listener.IEntityEventListener;
import net.minecraft.Damage;
import net.minecraft.DamageSource;
import net.minecraft.Entity;
import net.minecraft.EntityLivingBase;
import net.minecraft.ItemStack;

public final class StructureEntityReplacementRuntime implements IEntityEventListener {
    public static final StructureEntityReplacementRuntime INSTANCE = new StructureEntityReplacementRuntime();

    private static final Map<EntityLivingBase, StructureEntityReplacementProfile.EntityDrop[]> PENDING_DROPS =
            Collections.synchronizedMap(
                    new WeakHashMap<EntityLivingBase, StructureEntityReplacementProfile.EntityDrop[]>());
    private static final Method ENTITY_DROP_ITEM_METHOD = resolveEntityDropItemMethod();
    private static final Method DROP_ITEM_BY_ID_METHOD = resolveDropItemByIdMethod();
    private static final boolean HAS_DROP_METHOD = ENTITY_DROP_ITEM_METHOD != null || DROP_ITEM_BY_ID_METHOD != null;

    private StructureEntityReplacementRuntime() {
    }

    public static void registerDrops(EntityLivingBase entity, StructureEntityReplacementProfile.EntityDrop[] drops) {
        if (entity == null || drops == null || drops.length == 0) {
            return;
        }
        registerDropsInternal(entity, drops.clone());
    }

    static void registerDropsInternal(EntityLivingBase entity, StructureEntityReplacementProfile.EntityDrop[] drops) {
        if (entity == null || drops == null || drops.length == 0) {
            return;
        }
        PENDING_DROPS.put(entity, drops);
    }

    public static void unregisterDrops(EntityLivingBase entity) {
        if (entity == null) {
            return;
        }
        PENDING_DROPS.remove(entity);
    }

    @Override
    public void onLoot(EntityLivingBase entity, DamageSource source) {
    }

    @Override
    public void onSpawn(Entity entity) {
    }

    @Override
    public void onDeath(EntityLivingBase entity, DamageSource source) {
        if (entity == null) {
            return;
        }

        StructureEntityReplacementProfile.EntityDrop[] drops = PENDING_DROPS.remove(entity);
        if (drops == null || drops.length == 0 || !HAS_DROP_METHOD) {
            return;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (StructureEntityReplacementProfile.EntityDrop drop : drops) {
            if (drop == null) {
                continue;
            }
            float chance = drop.chance();
            if (chance <= 0.0F) {
                continue;
            }
            if (chance < 1.0F && random.nextFloat() > chance) {
                continue;
            }
            int count = drop.minCount();
            if (drop.maxCount() > count) {
                count = count + random.nextInt(drop.maxCount() - count + 1);
            }
            if (count <= 0) {
                continue;
            }
            tryDropItem(entity, drop.itemId(), drop.meta(), count);
        }
    }

    @Override
    public void onUpdate(EntityLivingBase entity) {
    }

    @Override
    public void onAttackEntityFrom(EntityLivingBase entity, Damage damage) {
    }

    @Override
    public void onFall(EntityLivingBase entity, float distance) {
    }

    @Override
    public void onJump(EntityLivingBase entity) {
    }

    private static void tryDropItem(EntityLivingBase entity, int itemId, int meta, int count) {
        if (entity == null || itemId <= 0 || count <= 0) {
            return;
        }

        if (ENTITY_DROP_ITEM_METHOD != null) {
            ItemStack stack = new ItemStack(itemId, count, Math.max(0, meta));
            if (StructureItemStacks.isUsable(stack) && invokeEntityDropItem(entity, stack)) {
                return;
            }
        }
        invokeDropItemById(entity, itemId, count);
    }

    private static boolean invokeEntityDropItem(EntityLivingBase entity, ItemStack stack) {
        if (ENTITY_DROP_ITEM_METHOD == null) {
            return false;
        }
        try {
            ENTITY_DROP_ITEM_METHOD.invoke(entity, stack, 0.0F);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static boolean invokeDropItemById(EntityLivingBase entity, int itemId, int count) {
        if (DROP_ITEM_BY_ID_METHOD == null) {
            return false;
        }
        try {
            DROP_ITEM_BY_ID_METHOD.invoke(entity, itemId, count);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static Method resolveEntityDropItemMethod() {
        try {
            return Entity.class.getMethod("entityDropItem", ItemStack.class, float.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method resolveDropItemByIdMethod() {
        try {
            return EntityLivingBase.class.getMethod("dropItem", int.class, int.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
