package com.github.hahahha.WorldGenLib.util;

import java.util.Locale;
import net.minecraft.Translator;

public final class I18n {
    private static final int TRANSLATION_CACHE_MAX_SIZE = 4096;
    private static final LruCache<TranslationCacheKey, String> TRANSLATION_CACHE =
            new LruCache<TranslationCacheKey, String>(TRANSLATION_CACHE_MAX_SIZE);

    private I18n() {
    }

    public static String tr(String key, String fallback) {
        if (isBlank(key)) {
            return fallback == null ? "" : fallback;
        }
        TranslationCacheKey cacheKey = new TranslationCacheKey(key, fallback);
        String cached = TRANSLATION_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        String translated = Translator.get(key);
        String resolved;
        if (isBlank(translated) || key.equals(translated)) {
            resolved = fallback == null ? key : fallback;
        } else {
            resolved = translated;
        }
        String previous = TRANSLATION_CACHE.putIfAbsent(cacheKey, resolved);
        return previous != null ? previous : resolved;
    }

    public static void clearCache() {
        TRANSLATION_CACHE.clear();
    }

    public static String trf(String key, String fallbackFormat, Object... args) {
        String pattern = tr(key, fallbackFormat);
        if (args == null || args.length == 0 || pattern.indexOf('%') < 0) {
            return pattern;
        }
        try {
            return String.format(Locale.ROOT, pattern, args);
        } catch (RuntimeException ignored) {
            return pattern;
        }
    }

    private static boolean isBlank(String value) {
        return StringNormalization.isBlank(value);
    }

    private static final class TranslationCacheKey {
        private final String key;
        private final String fallback;

        private TranslationCacheKey(String key, String fallback) {
            this.key = key == null ? "" : key;
            this.fallback = fallback == null ? "" : fallback;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof TranslationCacheKey)) {
                return false;
            }
            TranslationCacheKey other = (TranslationCacheKey) obj;
            return this.key.equals(other.key) && this.fallback.equals(other.fallback);
        }

        @Override
        public int hashCode() {
            int result = this.key.hashCode();
            result = 31 * result + this.fallback.hashCode();
            return result;
        }
    }
}
