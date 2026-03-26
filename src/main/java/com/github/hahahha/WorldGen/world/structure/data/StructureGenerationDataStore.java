package com.github.hahahha.WorldGen.world.structure.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.MapStorage;
import net.minecraft.NBTBase;
import net.minecraft.NBTTagCompound;
import net.minecraft.NBTTagList;
import net.minecraft.World;
import net.minecraft.WorldSavedData;

public class StructureGenerationDataStore extends WorldSavedData {
    public static final String DATA_NAME = "worldgen_structure_generation";

    private static final String KEY_ENTRIES = "entries";

    private final List<StructureRecord> records = new ArrayList<StructureRecord>();
    private final Set<String> recordKeys = new HashSet<String>();

    public StructureGenerationDataStore() {
        this(DATA_NAME);
    }

    public StructureGenerationDataStore(String name) {
        super(name);
    }

    public static void recordStructure(World world, String structurePath, int x, int y, int z) {
        recordStructure(world, structurePath, null, x, y, z, 0, 0, 0);
    }

    public static void recordStructure(
            World world,
            String structurePath,
            String configuredStructureName,
            int x,
            int y,
            int z) {
        recordStructure(world, structurePath, configuredStructureName, x, y, z, 0, 0, 0);
    }

    public static void recordStructure(
            World world,
            String structurePath,
            String configuredStructureName,
            int x,
            int y,
            int z,
            int width,
            int height,
            int length) {
        if (world == null || world.isRemote) {
            return;
        }

        String normalizedPath = normalizePath(structurePath);
        if (normalizedPath == null) {
            return;
        }

        StructureGenerationDataStore store = getOrCreate(world);
        if (store == null) {
            return;
        }

        String normalizedStructureName = normalizeStructureName(configuredStructureName);
        if (normalizedStructureName == null) {
            normalizedStructureName = extractStructureName(normalizedPath);
        }

        StructureRecord record = new StructureRecord(
                world.getDimensionId(),
                normalizedPath,
                normalizedStructureName,
                x,
                y,
                z,
                Math.max(0, width),
                Math.max(0, height),
                Math.max(0, length));
        store.addRecord(record);
    }

    public static List<StructureSearchResult> findNearby(
            World world,
            int dimensionId,
            double centerX,
            double centerZ,
            String query,
            int radius,
            int maxResults) {
        if (world == null || radius < 1 || maxResults < 1) {
            return Collections.emptyList();
        }

        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery == null) {
            return Collections.emptyList();
        }

        StructureGenerationDataStore store = get(world);
        if (store == null || store.records.isEmpty()) {
            return Collections.emptyList();
        }

        double radiusSq = (double) radius * (double) radius;
        List<StructureSearchResult> exact = new ArrayList<StructureSearchResult>();
        List<StructureSearchResult> fuzzy = new ArrayList<StructureSearchResult>();

        for (StructureRecord record : store.records) {
            if (record.dimensionId() != dimensionId) {
                continue;
            }

            double dx = (double) record.x() - centerX;
            double dz = (double) record.z() - centerZ;
            double distanceSq = dx * dx + dz * dz;
            if (distanceSq > radiusSq) {
                continue;
            }

            if (record.matchesExact(normalizedQuery)) {
                exact.add(new StructureSearchResult(record, distanceSq));
            } else if (record.matchesFuzzy(normalizedQuery)) {
                fuzzy.add(new StructureSearchResult(record, distanceSq));
            }
        }

        if (exact.isEmpty() && fuzzy.isEmpty()) {
            return Collections.emptyList();
        }

        Comparator<StructureSearchResult> byDistance =
                Comparator.comparingDouble(StructureSearchResult::distanceSq);
        Collections.sort(exact, byDistance);
        Collections.sort(fuzzy, byDistance);

        List<StructureSearchResult> merged = new ArrayList<StructureSearchResult>(maxResults);
        appendLimit(merged, exact, maxResults);
        appendLimit(merged, fuzzy, maxResults);
        return merged;
    }

    public static int countAllRecords(World world) {
        StructureGenerationDataStore store = get(world);
        return store == null ? 0 : store.records.size();
    }

    public static int countRecordsInDimension(World world, int dimensionId) {
        StructureGenerationDataStore store = get(world);
        if (store == null || store.records.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (StructureRecord record : store.records) {
            if (record.dimensionId() == dimensionId) {
                count++;
            }
        }
        return count;
    }

    public static List<String> listStructureNames(World world, int dimensionId, int maxEntries) {
        if (maxEntries < 1) {
            return Collections.emptyList();
        }

        StructureGenerationDataStore store = get(world);
        if (store == null || store.records.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> names = new LinkedHashSet<String>();
        for (StructureRecord record : store.records) {
            if (record.dimensionId() != dimensionId) {
                continue;
            }
            names.add(record.structureName());
            if (names.size() >= maxEntries) {
                break;
            }
        }
        if (names.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<String>(names);
    }

    public static boolean hasNearbyStructure(
            World world,
            int dimensionId,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            int minDistance,
            String structureName,
            boolean sameNameOnly) {
        if (world == null || minDistance < 1) {
            return false;
        }

        StructureGenerationDataStore store = get(world);
        if (store == null || store.records.isEmpty()) {
            return false;
        }

        int queryMinX = Math.min(minX, maxX);
        int queryMaxX = Math.max(minX, maxX);
        int queryMinZ = Math.min(minZ, maxZ);
        int queryMaxZ = Math.max(minZ, maxZ);
        long minDistanceSq = (long) minDistance * (long) minDistance;

        String normalizedName = null;
        if (sameNameOnly && structureName != null) {
            normalizedName = structureName.trim().toLowerCase(Locale.ROOT);
            if (normalizedName.isEmpty()) {
                normalizedName = null;
            }
        }

        for (StructureRecord record : store.records) {
            if (record.dimensionId() != dimensionId) {
                continue;
            }
            if (sameNameOnly && normalizedName != null) {
                String recordName = record.structureName();
                if (recordName == null || !normalizedName.equals(recordName.trim().toLowerCase(Locale.ROOT))) {
                    continue;
                }
            }

            int recordMinX = record.x();
            int recordMaxX = record.width() > 0 ? recordMinX + record.width() - 1 : recordMinX;
            int recordMinZ = record.z();
            int recordMaxZ = record.length() > 0 ? recordMinZ + record.length() - 1 : recordMinZ;

            long dx;
            if (queryMaxX < recordMinX) {
                dx = (long) recordMinX - queryMaxX;
            } else if (recordMaxX < queryMinX) {
                dx = (long) queryMinX - recordMaxX;
            } else {
                dx = 0L;
            }

            long dz;
            if (queryMaxZ < recordMinZ) {
                dz = (long) recordMinZ - queryMaxZ;
            } else if (recordMaxZ < queryMinZ) {
                dz = (long) queryMinZ - recordMaxZ;
            } else {
                dz = 0L;
            }

            long distanceSq = dx * dx + dz * dz;
            if (distanceSq < minDistanceSq) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        this.records.clear();
        this.recordKeys.clear();

        if (nbt == null || !nbt.hasKey(KEY_ENTRIES)) {
            return;
        }

        NBTTagList list = nbt.getTagList(KEY_ENTRIES);
        if (list == null) {
            return;
        }

        for (int i = 0; i < list.tagCount(); ++i) {
            NBTBase base = list.tagAt(i);
            if (!(base instanceof NBTTagCompound)) {
                continue;
            }
            StructureRecord record = StructureRecord.fromTag((NBTTagCompound) base);
            if (record == null) {
                continue;
            }
            addRecordInternal(record, false);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (StructureRecord record : this.records) {
            list.appendTag(record.toTag());
        }
        nbt.setTag(KEY_ENTRIES, list);
    }

    private static void appendLimit(
            List<StructureSearchResult> destination,
            List<StructureSearchResult> source,
            int limit) {
        for (StructureSearchResult result : source) {
            if (destination.size() >= limit) {
                return;
            }
            destination.add(result);
        }
    }

    private void addRecord(StructureRecord record) {
        addRecordInternal(record, true);
    }

    private void addRecordInternal(StructureRecord record, boolean dirty) {
        if (record == null) {
            return;
        }

        String key = record.uniqueKey();
        if (this.recordKeys.contains(key)) {
            return;
        }

        this.records.add(record);
        this.recordKeys.add(key);

        if (dirty) {
            this.markDirty();
        }
    }

    private static StructureGenerationDataStore get(World world) {
        if (world == null) {
            return null;
        }

        MapStorage mapStorage = world.mapStorage;
        if (mapStorage == null) {
            return null;
        }

        WorldSavedData savedData = mapStorage.loadData(StructureGenerationDataStore.class, DATA_NAME);
        if (savedData instanceof StructureGenerationDataStore) {
            return (StructureGenerationDataStore) savedData;
        }
        return null;
    }

    private static StructureGenerationDataStore getOrCreate(World world) {
        StructureGenerationDataStore existing = get(world);
        if (existing != null) {
            return existing;
        }

        if (world == null || world.mapStorage == null) {
            return null;
        }

        StructureGenerationDataStore created = new StructureGenerationDataStore(DATA_NAME);
        world.mapStorage.setData(DATA_NAME, created);
        created.markDirty();
        return created;
    }

    private static String normalizePath(String path) {
        if (path == null) {
            return null;
        }

        String normalized = path.trim().replace('\\', '/');
        return normalized.isEmpty() ? null : normalized;
    }

    private static String normalizeQuery(String query) {
        String normalized = normalizePath(query);
        if (normalized == null) {
            return null;
        }

        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".schematic")) {
            lower = lower.substring(0, lower.length() - ".schematic".length());
        }
        return lower;
    }

    private static String extractStructureName(String normalizedPath) {
        if (normalizedPath == null || normalizedPath.isEmpty()) {
            return "unknown";
        }

        String name = normalizedPath;
        int slash = name.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < name.length()) {
            name = name.substring(slash + 1);
        }
        if (name.toLowerCase(Locale.ROOT).endsWith(".schematic")) {
            name = name.substring(0, name.length() - ".schematic".length());
        }

        return name.isEmpty() ? "unknown" : name;
    }

    private static String normalizeStructureName(String configuredStructureName) {
        if (configuredStructureName == null) {
            return null;
        }
        String trimmed = configuredStructureName.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static final class StructureSearchResult {
        private final StructureRecord record;
        private final double distanceSq;

        private StructureSearchResult(StructureRecord record, double distanceSq) {
            this.record = record;
            this.distanceSq = distanceSq;
        }

        public StructureRecord record() {
            return this.record;
        }

        public double distanceSq() {
            return this.distanceSq;
        }
    }

    public static final class StructureRecord {
        private static final String KEY_DIMENSION_ID = "dimension";
        private static final String KEY_STRUCTURE_PATH = "path";
        private static final String KEY_STRUCTURE_NAME = "name";
        private static final String KEY_X = "x";
        private static final String KEY_Y = "y";
        private static final String KEY_Z = "z";
        private static final String KEY_WIDTH = "width";
        private static final String KEY_HEIGHT = "height";
        private static final String KEY_LENGTH = "length";

        private final int dimensionId;
        private final String structurePath;
        private final String structureName;
        private final int x;
        private final int y;
        private final int z;
        private final int width;
        private final int height;
        private final int length;

        private StructureRecord(
                int dimensionId,
                String structurePath,
                String structureName,
                int x,
                int y,
                int z,
                int width,
                int height,
                int length) {
            this.dimensionId = dimensionId;
            this.structurePath = structurePath;
            this.structureName = structureName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.width = Math.max(0, width);
            this.height = Math.max(0, height);
            this.length = Math.max(0, length);
        }

        public int dimensionId() {
            return this.dimensionId;
        }

        public String structurePath() {
            return this.structurePath;
        }

        public String structureName() {
            return this.structureName;
        }

        public int x() {
            return this.x;
        }

        public int y() {
            return this.y;
        }

        public int z() {
            return this.z;
        }

        public int width() {
            return this.width;
        }

        public int height() {
            return this.height;
        }

        public int length() {
            return this.length;
        }

        private boolean matchesExact(String normalizedQuery) {
            String pathLower = this.structurePath.toLowerCase(Locale.ROOT);
            String nameLower = this.structureName.toLowerCase(Locale.ROOT);
            return normalizedQuery.equals(pathLower)
                    || normalizedQuery.equals(nameLower)
                    || normalizedQuery.equals(pathLower + ".schematic")
                    || normalizedQuery.equals(nameLower + ".schematic");
        }

        private boolean matchesFuzzy(String normalizedQuery) {
            String pathLower = this.structurePath.toLowerCase(Locale.ROOT);
            String nameLower = this.structureName.toLowerCase(Locale.ROOT);
            return pathLower.contains(normalizedQuery) || nameLower.contains(normalizedQuery);
        }

        private String uniqueKey() {
            return this.dimensionId
                    + "|"
                    + this.structurePath.toLowerCase(Locale.ROOT)
                    + "|"
                    + this.x
                    + "|"
                    + this.y
                    + "|"
                    + this.z;
        }

        private NBTTagCompound toTag() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger(KEY_DIMENSION_ID, this.dimensionId);
            tag.setString(KEY_STRUCTURE_PATH, this.structurePath);
            tag.setString(KEY_STRUCTURE_NAME, this.structureName);
            tag.setInteger(KEY_X, this.x);
            tag.setInteger(KEY_Y, this.y);
            tag.setInteger(KEY_Z, this.z);
            tag.setInteger(KEY_WIDTH, this.width);
            tag.setInteger(KEY_HEIGHT, this.height);
            tag.setInteger(KEY_LENGTH, this.length);
            return tag;
        }

        private static StructureRecord fromTag(NBTTagCompound tag) {
            if (tag == null) {
                return null;
            }

            int dimensionId = tag.getInteger(KEY_DIMENSION_ID);
            String structurePath = normalizePath(tag.getString(KEY_STRUCTURE_PATH));
            if (structurePath == null) {
                return null;
            }

            String structureName = tag.getString(KEY_STRUCTURE_NAME);
            if (structureName == null || structureName.trim().isEmpty()) {
                structureName = extractStructureName(structurePath);
            }

            int x = tag.getInteger(KEY_X);
            int y = tag.getInteger(KEY_Y);
            int z = tag.getInteger(KEY_Z);
            int width = tag.hasKey(KEY_WIDTH) ? tag.getInteger(KEY_WIDTH) : 0;
            int height = tag.hasKey(KEY_HEIGHT) ? tag.getInteger(KEY_HEIGHT) : 0;
            int length = tag.hasKey(KEY_LENGTH) ? tag.getInteger(KEY_LENGTH) : 0;
            return new StructureRecord(dimensionId, structurePath, structureName, x, y, z, width, height, length);
        }
    }
}
