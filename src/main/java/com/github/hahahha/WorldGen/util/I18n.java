package com.github.hahahha.WorldGen.util;

import java.util.Locale;
import net.minecraft.Translator;

public final class I18n {
    private I18n() {
    }

    public static String tr(String key, String fallback) {
        if (key == null || key.trim().isEmpty()) {
            return fallback == null ? "" : fallback;
        }

        String translated = Translator.get(key);
        if (translated == null || translated.trim().isEmpty() || key.equals(translated)) {
            return fallback == null ? key : fallback;
        }
        return translated;
    }

    public static String trf(String key, String fallbackFormat, Object... args) {
        String pattern = tr(key, fallbackFormat);
        try {
            return String.format(Locale.ROOT, pattern, args);
        } catch (Exception ignored) {
            return pattern;
        }
    }
}

