package com.github.hahahha.WorldGenLib.world.structure;

import com.github.hahahha.WorldGenLib.util.StringNormalization;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;
import net.minecraft.Item;
import net.xiaoyu233.fml.api.INamespaced;

public final class StructureItemIdResolver {
    private static final long MISS_REFRESH_COOLDOWN_NANOS = TimeUnit.SECONDS.toNanos(2L);
    private static final String ITEM_PREFIX = "item.";
    private static final int ITEM_PREFIX_LENGTH = ITEM_PREFIX.length();
    private static final String MINECRAFT_NAMESPACE = "minecraft";
    private static final String MINECRAFT_PREFIX = "minecraft:";
    private static final AtomicReference<Map<String, Integer>> CACHED_ALIAS_TO_ID =
            new AtomicReference<Map<String, Integer>>();
    private static final AtomicLong LAST_REFRESH_NANOS = new AtomicLong(0L);
    private static final Field[] ITEM_PUBLIC_FIELDS = Item.class.getFields();
    private static final Object REFRESH_LOCK = new Object();

    private StructureItemIdResolver() {
    }

    public static Integer resolveItemIdByName(String token) {
        if (token == null) {
            return null;
        }
        String normalized = normalizeAlias(token);
        if (normalized == null) {
            return null;
        }
        Map<String, Integer> aliases = getItemAliasToId();
        Integer resolved = resolveFromAliases(aliases, normalized);
        if (resolved != null) {
            return resolved;
        }

        long now = System.nanoTime();
        if (!shouldRefreshOnMiss(now)) {
            return null;
        }
        synchronized (REFRESH_LOCK) {
            aliases = getItemAliasToId();
            resolved = resolveFromAliases(aliases, normalized);
            if (resolved != null) {
                return resolved;
            }

            long lockedNow = System.nanoTime();
            if (!shouldRefreshOnMiss(lockedNow)) {
                return null;
            }

            // Item static fields can still be filling during early bootstrap.
            // Refresh on miss (throttled) so late-initialized aliases become visible.
            Map<String, Integer> refreshed = Collections.unmodifiableMap(buildItemAliasToId());
            CACHED_ALIAS_TO_ID.set(refreshed);
            LAST_REFRESH_NANOS.set(lockedNow);
            return resolveFromAliases(refreshed, normalized);
        }
    }

    public static boolean isValidItemId(int itemId) {
        return itemId > 0 && Item.getItem(itemId) != null;
    }

    private static Map<String, Integer> getItemAliasToId() {
        Map<String, Integer> cached = CACHED_ALIAS_TO_ID.get();
        if (cached != null) {
            return cached;
        }

        Map<String, Integer> built = Collections.unmodifiableMap(buildItemAliasToId());
        if (CACHED_ALIAS_TO_ID.compareAndSet(null, built)) {
            LAST_REFRESH_NANOS.compareAndSet(0L, System.nanoTime());
            return built;
        }
        Map<String, Integer> resolved = CACHED_ALIAS_TO_ID.get();
        return resolved != null ? resolved : built;
    }

    private static boolean shouldRefreshOnMiss(long nowNanos) {
        long last = LAST_REFRESH_NANOS.get();
        return last <= 0L || nowNanos - last >= MISS_REFRESH_COOLDOWN_NANOS;
    }

    private static Map<String, Integer> buildItemAliasToId() {
        Item[] items = Item.itemsList;
        int estimatedSize = ITEM_PUBLIC_FIELDS.length * 2 + 128;
        if (items != null) {
            estimatedSize += items.length * 4;
        }
        Map<String, Integer> aliases = new HashMap<String, Integer>(Math.max(256, estimatedSize));

        if (items != null) {
            for (Item item : items) {
                if (item == null) {
                    continue;
                }
                addItemAliases(aliases, item, item.itemID);
            }
        }

        for (Field field : ITEM_PUBLIC_FIELDS) {
            if (!Modifier.isStatic(field.getModifiers()) || !Item.class.isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                Object value = field.get(null);
                if (!(value instanceof Item)) {
                    continue;
                }
                Item item = (Item) value;
                if (item == null) {
                    continue;
                }
                Integer boxedItemId = item.itemID;
                addAlias(aliases, field.getName(), boxedItemId);
                addAlias(aliases, MINECRAFT_PREFIX + field.getName(), boxedItemId);
            } catch (IllegalAccessException ignored) {
            }
        }
        return aliases;
    }

    private static void addItemAliases(Map<String, Integer> aliases, Item item, int itemId) {
        if (item == null || itemId <= 0) {
            return;
        }
        Integer boxedItemId = itemId;
        addAlias(aliases, String.valueOf(itemId), boxedItemId);

        String unlocalized = null;
        try {
            unlocalized = item.getUnlocalizedName();
        } catch (RuntimeException ignored) {
        }
        if (unlocalized != null) {
            addAlias(aliases, unlocalized, boxedItemId);
            addAlias(aliases, MINECRAFT_PREFIX + unlocalized, boxedItemId);
            if (unlocalized.startsWith(ITEM_PREFIX)) {
                String stripped = unlocalized.substring(ITEM_PREFIX_LENGTH);
                addAlias(aliases, stripped, boxedItemId);
                addAlias(aliases, MINECRAFT_PREFIX + stripped, boxedItemId);
            }
        }

        String refName = null;
        try {
            refName = item.getNameForReferenceFile();
        } catch (RuntimeException ignored) {
        }
        if (refName != null) {
            addAlias(aliases, refName, boxedItemId);
            addAlias(aliases, MINECRAFT_PREFIX + refName, boxedItemId);
        }

        if (item instanceof INamespaced) {
            try {
                INamespaced namespaced = (INamespaced) item;
                String namespace = namespaced.getNamespace();
                String normalizedNs = normalizeNamespace(namespace);
                if (normalizedNs != null) {
                    if (unlocalized != null && unlocalized.startsWith(ITEM_PREFIX)) {
                        addAlias(
                                aliases,
                                normalizedNs + ":" + unlocalized.substring(ITEM_PREFIX_LENGTH),
                                boxedItemId);
                    }
                    if (refName != null) {
                        addAlias(aliases, normalizedNs + ":" + refName, boxedItemId);
                    }
                }
            } catch (RuntimeException ignored) {
            }
        }
    }

    private static void addAlias(Map<String, Integer> aliases, String alias, Integer itemId) {
        if (itemId == null) {
            return;
        }
        String normalized = normalizeAlias(alias);
        if (normalized == null) {
            return;
        }
        aliases.putIfAbsent(normalized, itemId);
    }

    private static Integer resolveFromAliases(Map<String, Integer> aliases, String normalizedAlias) {
        if (aliases == null || normalizedAlias == null) {
            return null;
        }

        Integer direct = aliases.get(normalizedAlias);
        if (direct != null) {
            return direct;
        }

        int colon = normalizedAlias.indexOf(':');
        if (colon <= 0 || colon + 1 >= normalizedAlias.length()) {
            return null;
        }
        String namespace = normalizedAlias.substring(0, colon);
        if (!MINECRAFT_NAMESPACE.equals(namespace)) {
            return null;
        }
        String pathOnly = normalizedAlias.substring(colon + 1);
        return pathOnly.isEmpty() ? null : aliases.get(pathOnly);
    }

    private static String normalizeNamespace(String namespace) {
        return StringNormalization.trimLowerToNull(namespace);
    }

    private static String normalizeAlias(String value) {
        String normalized = StringNormalization.trimLowerToNull(value);
        if (normalized == null) {
            return null;
        }
        int colon = normalized.indexOf(':');
        if (colon >= 0 && colon + 1 < normalized.length()) {
            String namespace = normalized.substring(0, colon);
            String path = normalized.substring(colon + 1);
            if (path.startsWith(ITEM_PREFIX)) {
                path = path.substring(ITEM_PREFIX_LENGTH);
            }
            return path.isEmpty() ? namespace : namespace + ":" + path;
        }
        if (normalized.startsWith(ITEM_PREFIX)) {
            String stripped = normalized.substring(ITEM_PREFIX_LENGTH);
            return stripped.isEmpty() ? normalized : stripped;
        }
        return normalized;
    }
}
