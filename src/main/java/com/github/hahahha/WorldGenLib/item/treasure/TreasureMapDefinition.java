package com.github.hahahha.WorldGenLib.item.treasure;

import com.github.hahahha.WorldGenLib.WorldGenLib;
import com.github.hahahha.WorldGenLib.util.StringNormalization;
import java.util.Locale;
import java.util.Objects;

public final class TreasureMapDefinition {
    public static final int DEFAULT_SEARCH_RADIUS = 20000;

    private final String id;
    private final String namespace;
    private final String textureName;
    private final String unlocalizedName;
    private final String itemTranslationKey;
    private final String itemFallbackName;
    private final String structureQuery;
    private final String targetDisplayName;
    private final int searchRadius;

    private TreasureMapDefinition(
            String id,
            String namespace,
            String textureName,
            String unlocalizedName,
            String itemTranslationKey,
            String itemFallbackName,
            String structureQuery,
            String targetDisplayName,
            int searchRadius) {
        this.id = id;
        this.namespace = namespace;
        this.textureName = textureName;
        this.unlocalizedName = unlocalizedName;
        this.itemTranslationKey = itemTranslationKey;
        this.itemFallbackName = itemFallbackName;
        this.structureQuery = structureQuery;
        this.targetDisplayName = targetDisplayName;
        this.searchRadius = searchRadius;
    }

    public static Builder builder(String id, String structureQuery) {
        return new Builder(id, structureQuery);
    }

    public static String normalizeId(String id) {
        ParsedId parsed = parseId(id);
        return parsed.namespace + ":" + parsed.path;
    }

    public String id() {
        return this.id;
    }

    public String namespace() {
        return this.namespace;
    }

    public String textureName() {
        return this.textureName;
    }

    public String unlocalizedName() {
        return this.unlocalizedName;
    }

    public String itemTranslationKey() {
        return this.itemTranslationKey;
    }

    public String itemFallbackName() {
        return this.itemFallbackName;
    }

    public String structureQuery() {
        return this.structureQuery;
    }

    public String targetDisplayName() {
        return this.targetDisplayName;
    }

    public int searchRadius() {
        return this.searchRadius;
    }

    public String preferredTargetLabel() {
        return this.targetDisplayName != null && !this.targetDisplayName.isEmpty()
                ? this.targetDisplayName
                : this.structureQuery;
    }

    private static ParsedId parseId(String rawId) {
        String raw = requireNonBlank(rawId, "id").toLowerCase(Locale.ROOT);
        int split = raw.indexOf(':');
        if (split < 0) {
            String path = validateToken(raw, "path");
            return new ParsedId(WorldGenLib.MOD_ID, path);
        }

        String namespace = validateToken(raw.substring(0, split), "namespace");
        String path = validateToken(raw.substring(split + 1), "path");
        return new ParsedId(namespace, path);
    }

    private static String requireNonBlank(String value, String fieldName) {
        String trimmed = StringNormalization.trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }
        return trimmed;
    }

    private static String validateToken(String value, String fieldName) {
        String token = requireNonBlank(value, fieldName).toLowerCase(Locale.ROOT);
        for (int i = 0; i < token.length(); ++i) {
            char c = token.charAt(i);
            boolean valid = (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '_'
                    || c == '.'
                    || c == '-'
                    || c == '/';
            if (!valid) {
                throw new IllegalArgumentException(fieldName + " contains invalid character: " + c);
            }
        }
        return token;
    }

    private static String sanitizeAsUnlocalizedName(String path) {
        StringBuilder sb = new StringBuilder(path.length());
        for (int i = 0; i < path.length(); ++i) {
            char c = path.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        String value = sb.toString();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("unlocalizedName cannot be empty");
        }
        return value;
    }

    private static String defaultFallbackName(String path) {
        String leaf = path;
        int slash = leaf.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < leaf.length()) {
            leaf = leaf.substring(slash + 1);
        }

        String[] parts = leaf.split("[_.-]+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
        }
        if (sb.length() == 0) {
            sb.append("Treasure Map");
        }
        return sb.toString();
    }

    public static final class Builder {
        private final String normalizedId;
        private final String path;
        private String namespace;
        private String textureName;
        private String unlocalizedName;
        private String itemTranslationKey;
        private String itemFallbackName;
        private String structureQuery;
        private String targetDisplayName;
        private boolean itemNameCustomized;
        private int searchRadius = DEFAULT_SEARCH_RADIUS;

        private Builder(String id, String structureQuery) {
            ParsedId parsed = parseId(id);
            this.normalizedId = parsed.namespace + ":" + parsed.path;
            this.namespace = parsed.namespace;
            this.path = parsed.path;
            this.textureName = parsed.path;
            this.unlocalizedName = sanitizeAsUnlocalizedName(parsed.path);
            this.itemTranslationKey = "item." + this.unlocalizedName + ".name";
            this.itemFallbackName = defaultFallbackName(parsed.path);
            this.structureQuery = requireNonBlank(structureQuery, "structureQuery");
            this.targetDisplayName = null;
            this.itemNameCustomized = false;
        }

        public Builder namespace(String namespace) {
            this.namespace = validateToken(namespace, "namespace");
            return this;
        }

        public Builder textureName(String textureName) {
            this.textureName = requireNonBlank(textureName, "textureName");
            return this;
        }

        public Builder unlocalizedName(String unlocalizedName) {
            String value = requireNonBlank(unlocalizedName, "unlocalizedName").toLowerCase(Locale.ROOT);
            this.unlocalizedName = sanitizeAsUnlocalizedName(value);
            if (!this.itemNameCustomized) {
                this.itemTranslationKey = "item." + this.unlocalizedName + ".name";
            }
            return this;
        }

        public Builder itemName(String translationKey, String fallbackName) {
            this.itemTranslationKey = requireNonBlank(translationKey, "translationKey");
            this.itemFallbackName = requireNonBlank(fallbackName, "fallbackName");
            this.itemNameCustomized = true;
            return this;
        }

        public Builder structureQuery(String structureQuery) {
            this.structureQuery = requireNonBlank(structureQuery, "structureQuery");
            return this;
        }

        public Builder targetDisplayName(String targetDisplayName) {
            this.targetDisplayName = StringNormalization.trimToNull(targetDisplayName);
            return this;
        }

        public Builder searchRadius(int searchRadius) {
            if (searchRadius < 1) {
                throw new IllegalArgumentException("searchRadius must be >= 1");
            }
            this.searchRadius = searchRadius;
            return this;
        }

        public TreasureMapDefinition build() {
            Objects.requireNonNull(this.namespace, "namespace");
            Objects.requireNonNull(this.textureName, "textureName");
            Objects.requireNonNull(this.unlocalizedName, "unlocalizedName");
            Objects.requireNonNull(this.itemTranslationKey, "itemTranslationKey");
            Objects.requireNonNull(this.itemFallbackName, "itemFallbackName");
            Objects.requireNonNull(this.structureQuery, "structureQuery");
            return new TreasureMapDefinition(
                    this.normalizedId,
                    this.namespace,
                    this.textureName,
                    this.unlocalizedName,
                    this.itemTranslationKey,
                    this.itemFallbackName,
                    this.structureQuery,
                    this.targetDisplayName,
                    this.searchRadius);
        }
    }

    private static final class ParsedId {
        private final String namespace;
        private final String path;

        private ParsedId(String namespace, String path) {
            this.namespace = namespace;
            this.path = path;
        }
    }
}
