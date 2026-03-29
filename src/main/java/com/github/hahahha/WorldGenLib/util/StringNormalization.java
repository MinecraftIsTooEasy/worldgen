package com.github.hahahha.WorldGenLib.util;

import java.util.Locale;

public final class StringNormalization {
    private StringNormalization() {
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String trimLowerToNull(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    public static boolean isBlank(String value) {
        return trimToNull(value) == null;
    }

    public static String normalizePath(String path) {
        String normalized = trimToNull(path);
        return normalized == null ? null : normalized.replace('\\', '/');
    }

    public static String extractModIdFromAssetsPath(String path) {
        String normalizedPath = normalizePath(path);
        if (normalizedPath == null) {
            return null;
        }
        String lower = normalizedPath.toLowerCase(Locale.ROOT);
        int assetsIndex = lower.indexOf("/assets/");
        int markerLength = "/assets/".length();
        if (assetsIndex < 0) {
            assetsIndex = lower.indexOf("assets/");
            markerLength = "assets/".length();
            if (assetsIndex < 0) {
                return null;
            }
        }

        int modStart = assetsIndex + markerLength;
        if (modStart >= lower.length()) {
            return null;
        }
        int modEnd = lower.indexOf('/', modStart);
        if (modEnd <= modStart) {
            return null;
        }
        return trimLowerToNull(normalizedPath.substring(modStart, modEnd));
    }
}
