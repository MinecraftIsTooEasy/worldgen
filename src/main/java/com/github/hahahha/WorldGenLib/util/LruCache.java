package com.github.hahahha.WorldGenLib.util;

import java.util.LinkedHashMap;
import java.util.Map;

public final class LruCache<K, V> {
    private final LinkedHashMap<K, V> map;

    public LruCache(final int maxSize) {
        final int safeMaxSize = Math.max(1, maxSize);
        this.map = new LinkedHashMap<K, V>(16, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > safeMaxSize;
            }
        };
    }

    public synchronized V get(K key) {
        return this.map.get(key);
    }

    public synchronized V putIfAbsent(K key, V value) {
        V existing = this.map.get(key);
        if (existing != null || this.map.containsKey(key)) {
            return existing;
        }
        this.map.put(key, value);
        return null;
    }

    public synchronized void put(K key, V value) {
        this.map.put(key, value);
    }

    public synchronized V remove(K key) {
        return this.map.remove(key);
    }

    public synchronized void clear() {
        this.map.clear();
    }
}
