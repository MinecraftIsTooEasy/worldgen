package com.github.hahahha.WorldGenLib.world.structure;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.ItemStack;

public final class StructureItemStacks {
    private static final String[] META_METHOD_NAMES =
            new String[] {"getItemSubtype", "getItemDamageForDisplay", "getItemDamage"};
    private static final String[] META_FIELD_NAMES =
            new String[] {"itemSubtype", "itemDamage", "itemDamageForDisplay", "damage"};
    private static final Method META_METHOD = resolveMetaMethod();
    private static final Field META_FIELD = META_METHOD == null ? resolveMetaField() : null;

    private StructureItemStacks() {
    }

    public static ItemStack safeClone(ItemStack stack) {
        if (stack == null) {
            return null;
        }

        int itemId = stack.itemID;
        if (itemId <= 0) {
            return null;
        }

        int count = Math.max(1, stack.stackSize);
        int meta = resolveMeta(stack);
        return new ItemStack(itemId, count, meta);
    }

    public static boolean isUsable(ItemStack stack) {
        return stack != null && stack.stackSize > 0 && stack.getItem() != null;
    }

    private static int resolveMeta(ItemStack stack) {
        if (META_METHOD != null) {
            Integer value = invokeIntMethod(stack, META_METHOD);
            if (value != null) {
                return Math.max(0, value.intValue());
            }
        }
        if (META_FIELD != null) {
            Integer value = readIntField(stack, META_FIELD);
            if (value != null) {
                return Math.max(0, value.intValue());
            }
        }
        return 0;
    }

    private static Integer invokeIntMethod(ItemStack stack, Method method) {
        try {
            Object value = method.invoke(stack);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return null;
    }

    private static Integer readIntField(ItemStack stack, Field field) {
        try {
            return field.getInt(stack);
        } catch (IllegalAccessException | RuntimeException ignored) {
        }
        return null;
    }

    private static Method resolveMetaMethod() {
        for (String methodName : META_METHOD_NAMES) {
            try {
                return ItemStack.class.getMethod(methodName);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static Field resolveMetaField() {
        for (String fieldName : META_FIELD_NAMES) {
            try {
                return ItemStack.class.getField(fieldName);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }
}
