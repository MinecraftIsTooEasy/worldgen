package com.github.hahahha.WorldGenLib.world.structure.data;

import com.github.hahahha.WorldGenLib.util.StringNormalization;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.MapStorage;
import net.minecraft.NBTBase;
import net.minecraft.NBTTagCompound;
import net.minecraft.NBTTagList;
import net.minecraft.World;
import net.minecraft.WorldSavedData;

public class StructureGenerationDataStore extends WorldSavedData {
    public static final String DATA_NAME = "WorldGenLib_structure_generation";
    private static final int SPATIAL_INDEX_CELL_SIZE = 512;
    private static final int FUZZY_CANDIDATE_LIMIT = 8192;
    private static final Object STORE_CACHE_LOCK = new Object();
    private static final Map<World, StructureGenerationDataStore> STORE_CACHE =
            new WeakHashMap<World, StructureGenerationDataStore>();

    private static final String KEY_ENTRIES = "entries";
    private static final String SCHEMATIC_SUFFIX = ".schematic";
    private static final int SCHEMATIC_SUFFIX_LENGTH = SCHEMATIC_SUFFIX.length();
    private static final String UNKNOWN_STRUCTURE_NAME = "unknown";
    private static final Comparator<StructureSearchResult> BY_DISTANCE_ASC =
            Comparator.comparingDouble(StructureSearchResult::distanceSq);
    private static final Comparator<StructureSearchResult> BY_DISTANCE_DESC =
            (left, right) -> Double.compare(right.distanceSq(), left.distanceSq());

    private final List<StructureRecord> records = new ArrayList<StructureRecord>(256);
    private final Set<StructureRecordIdentityKey> recordKeys = new HashSet<StructureRecordIdentityKey>(256);
    private final Map<Integer, List<StructureRecord>> recordsByDimension =
            new HashMap<Integer, List<StructureRecord>>(16);
    private final Map<Integer, Map<String, List<StructureRecord>>> exactMatchIndexByDimension =
            new HashMap<Integer, Map<String, List<StructureRecord>>>(16);
    private final Map<Integer, Map<String, List<StructureRecord>>> recordsByNameLowerByDimension =
            new HashMap<Integer, Map<String, List<StructureRecord>>>(16);
    private final Map<Integer, LinkedHashSet<String>> structureNamesByDimension =
            new HashMap<Integer, LinkedHashSet<String>>(16);
    private final Map<Integer, Map<Long, List<StructureRecord>>> spatialIndexByDimension =
            new HashMap<Integer, Map<Long, List<StructureRecord>>>(16);
    private final Map<Integer, Map<Integer, List<StructureRecord>>> fuzzyBigramIndexByDimension =
            new HashMap<Integer, Map<Integer, List<StructureRecord>>>(16);
    private int nearbyScanToken = 1;
    private int exactScanToken = 1;
    private int fuzzyScanToken = 1;

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
        if (store == null) {
            return Collections.emptyList();
        }

        List<StructureRecord> recordsInDimension = store.recordsInDimension(dimensionId);
        if (recordsInDimension.isEmpty()) {
            return Collections.emptyList();
        }

        double radiusSq = (double) radius * (double) radius;
        PriorityQueue<StructureSearchResult> exactTop =
                new PriorityQueue<StructureSearchResult>(Math.max(1, maxResults), BY_DISTANCE_DESC);
        PriorityQueue<StructureSearchResult> fuzzyTop =
                new PriorityQueue<StructureSearchResult>(Math.max(1, maxResults), BY_DISTANCE_DESC);

        List<StructureRecord> exactMatches = store.exactMatchesInDimension(dimensionId, normalizedQuery);
        StructureRecord exactSingle = null;
        int exactToken = 0;
        if (!exactMatches.isEmpty()) {
            if (exactMatches.size() == 1) {
                exactSingle = exactMatches.get(0);
                double dx = (double) exactSingle.x() - centerX;
                double dz = (double) exactSingle.z() - centerZ;
                double distanceSq = dx * dx + dz * dz;
                if (distanceSq <= radiusSq) {
                    offerTopK(exactTop, exactSingle, distanceSq, maxResults);
                }
            } else {
                exactToken = store.nextExactScanToken();
                for (StructureRecord record : exactMatches) {
                    record.exactScanToken = exactToken;
                    double dx = (double) record.x() - centerX;
                    double dz = (double) record.z() - centerZ;
                    double distanceSq = dx * dx + dz * dz;
                    if (distanceSq > radiusSq) {
                        continue;
                    }
                    offerTopK(exactTop, record, distanceSq, maxResults);
                }
            }
        }

        if (exactTop.size() < maxResults) {
            List<StructureRecord> fuzzyCandidates =
                    store.fuzzyCandidatesInDimension(dimensionId, normalizedQuery);
            if (!fuzzyCandidates.isEmpty()) {
                for (StructureRecord record : fuzzyCandidates) {
                    if (record == exactSingle) {
                        continue;
                    }
                    if (exactToken != 0 && record.exactScanToken == exactToken) {
                        continue;
                    }

                    double dx = (double) record.x() - centerX;
                    double dz = (double) record.z() - centerZ;
                    double distanceSq = dx * dx + dz * dz;
                    if (distanceSq > radiusSq) {
                        continue;
                    }

                    if (record.matchesFuzzy(normalizedQuery)) {
                        offerTopK(fuzzyTop, record, distanceSq, maxResults);
                    }
                }
            }
        }

        if (exactTop.isEmpty() && fuzzyTop.isEmpty()) {
            return Collections.emptyList();
        }

        List<StructureSearchResult> exact = toSortedList(exactTop);
        List<StructureSearchResult> fuzzy = toSortedList(fuzzyTop);

        List<StructureSearchResult> merged =
                new ArrayList<StructureSearchResult>(Math.min(maxResults, exact.size() + fuzzy.size()));
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
        if (store == null) {
            return 0;
        }
        return store.recordsInDimension(dimensionId).size();
    }

    public static List<String> listStructureNames(World world, int dimensionId, int maxEntries) {
        if (maxEntries < 1) {
            return Collections.emptyList();
        }

        StructureGenerationDataStore store = get(world);
        if (store == null) {
            return Collections.emptyList();
        }

        Set<String> namesInDimension = store.structureNamesInDimension(dimensionId);
        if (namesInDimension.isEmpty()) {
            return Collections.emptyList();
        }

        int initialCapacity = Math.min(maxEntries, namesInDimension.size());
        List<String> names = new ArrayList<String>(Math.max(1, initialCapacity));
        for (String name : namesInDimension) {
            if (name == null || name.isEmpty()) {
                continue;
            }
            names.add(name);
            if (names.size() == maxEntries) {
                break;
            }
        }
        if (names.isEmpty()) {
            return Collections.emptyList();
        }
        return names;
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
        if (store == null) {
            return false;
        }

        List<StructureRecord> recordsInDimension = store.recordsInDimension(dimensionId);
        if (recordsInDimension.isEmpty()) {
            return false;
        }

        int queryMinX = Math.min(minX, maxX);
        int queryMaxX = Math.max(minX, maxX);
        int queryMinZ = Math.min(minZ, maxZ);
        int queryMaxZ = Math.max(minZ, maxZ);
        long minDistanceSq = (long) minDistance * (long) minDistance;

        if (sameNameOnly) {
            String normalizedName = normalizeToLower(structureName);
            if (normalizedName == null) {
                return false;
            }
            recordsInDimension = store.recordsByNameInDimension(dimensionId, normalizedName);
            if (recordsInDimension.isEmpty()) {
                return false;
            }
            for (StructureRecord record : recordsInDimension) {
                if (isWithinDistance(record, queryMinX, queryMaxX, queryMinZ, queryMaxZ, minDistanceSq)) {
                    return true;
                }
            }
            return false;
        }

        Map<Long, List<StructureRecord>> spatialIndex = store.spatialIndexInDimension(dimensionId);
        if (spatialIndex.isEmpty()) {
            for (StructureRecord record : recordsInDimension) {
                if (isWithinDistance(record, queryMinX, queryMaxX, queryMinZ, queryMaxZ, minDistanceSq)) {
                    return true;
                }
            }
            return false;
        }

        int expandedMinX = queryMinX - minDistance;
        int expandedMaxX = queryMaxX + minDistance;
        int expandedMinZ = queryMinZ - minDistance;
        int expandedMaxZ = queryMaxZ + minDistance;
        int minCellX = floorDiv(expandedMinX, SPATIAL_INDEX_CELL_SIZE);
        int maxCellX = floorDiv(expandedMaxX, SPATIAL_INDEX_CELL_SIZE);
        int minCellZ = floorDiv(expandedMinZ, SPATIAL_INDEX_CELL_SIZE);
        int maxCellZ = floorDiv(expandedMaxZ, SPATIAL_INDEX_CELL_SIZE);
        int scanToken = store.nextNearbyScanToken();

        for (int cellX = minCellX; cellX <= maxCellX; ++cellX) {
            for (int cellZ = minCellZ; cellZ <= maxCellZ; ++cellZ) {
                List<StructureRecord> bucket = spatialIndex.get(cellKey(cellX, cellZ));
                if (bucket == null || bucket.isEmpty()) {
                    continue;
                }
                for (StructureRecord record : bucket) {
                    if (record == null || record.nearbyScanToken == scanToken) {
                        continue;
                    }
                    record.nearbyScanToken = scanToken;
                    if (isWithinDistance(record, queryMinX, queryMaxX, queryMinZ, queryMaxZ, minDistanceSq)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        this.records.clear();
        this.recordKeys.clear();
        this.recordsByDimension.clear();
        this.exactMatchIndexByDimension.clear();
        this.recordsByNameLowerByDimension.clear();
        this.structureNamesByDimension.clear();
        this.spatialIndexByDimension.clear();
        this.fuzzyBigramIndexByDimension.clear();

        if (nbt == null || !nbt.hasKey(KEY_ENTRIES)) {
            return;
        }

        NBTTagList list = nbt.getTagList(KEY_ENTRIES);
        if (list == null) {
            return;
        }

        int tagCount = list.tagCount();
        for (int i = 0; i < tagCount; ++i) {
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

    private static void offerTopK(
            PriorityQueue<StructureSearchResult> heap,
            StructureRecord record,
            double distanceSq,
            int limit) {
        if (heap == null || record == null || limit < 1) {
            return;
        }

        if (heap.size() < limit) {
            heap.offer(new StructureSearchResult(record, distanceSq));
            return;
        }

        StructureSearchResult worst = heap.peek();
        if (worst != null && distanceSq < worst.distanceSq()) {
            heap.poll();
            heap.offer(new StructureSearchResult(record, distanceSq));
        }
    }

    private static List<StructureSearchResult> toSortedList(PriorityQueue<StructureSearchResult> heap) {
        if (heap == null || heap.isEmpty()) {
            return Collections.emptyList();
        }

        List<StructureSearchResult> sorted = new ArrayList<StructureSearchResult>(heap);
        if (sorted.size() > 1) {
            sorted.sort(BY_DISTANCE_ASC);
        }
        return sorted;
    }

    private void addRecord(StructureRecord record) {
        addRecordInternal(record, true);
    }

    private void addRecordInternal(StructureRecord record, boolean dirty) {
        if (record == null) {
            return;
        }

        if (!this.recordKeys.add(record.uniqueKey())) {
            return;
        }

        this.records.add(record);
        this.indexByDimension(record);

        if (dirty) {
            this.markDirty();
        }
    }

    private void indexByDimension(StructureRecord record) {
        if (record == null) {
            return;
        }

        int dimensionId = record.dimensionId();
        List<StructureRecord> list =
                this.recordsByDimension.computeIfAbsent(
                        dimensionId,
                        ignored -> new ArrayList<StructureRecord>(64));
        list.add(record);
        indexExactMatch(dimensionId, record);
        indexByName(dimensionId, record);
        indexStructureName(dimensionId, record.structureName());
        indexSpatial(dimensionId, record);
        indexFuzzyBigrams(dimensionId, record);
    }

    private void indexExactMatch(Integer dimensionId, StructureRecord record) {
        if (dimensionId == null || record == null) {
            return;
        }

        Map<String, List<StructureRecord>> index =
                this.exactMatchIndexByDimension.computeIfAbsent(
                        dimensionId,
                        ignored -> new HashMap<String, List<StructureRecord>>());

        addExactMatch(index, record.structurePathLower, record);
        addExactMatch(index, record.structureNameLower, record);
        addExactMatch(index, record.structurePathLowerWithSchematic, record);
        addExactMatch(index, record.structureNameLowerWithSchematic, record);
    }

    private static void addExactMatch(
            Map<String, List<StructureRecord>> index,
            String key,
            StructureRecord record) {
        if (index == null || key == null || key.isEmpty() || record == null) {
            return;
        }
        List<StructureRecord> list =
                index.computeIfAbsent(
                        key,
                        ignored -> new ArrayList<StructureRecord>(4));
        if (!list.isEmpty() && list.get(list.size() - 1) == record) {
            return;
        }
        list.add(record);
    }

    private void indexByName(Integer dimensionId, StructureRecord record) {
        if (dimensionId == null || record == null) {
            return;
        }
        String nameLower = record.structureNameLower();
        if (nameLower == null || nameLower.isEmpty()) {
            return;
        }

        Map<String, List<StructureRecord>> byName =
                this.recordsByNameLowerByDimension.computeIfAbsent(
                        dimensionId,
                        ignored -> new HashMap<String, List<StructureRecord>>());
        List<StructureRecord> list =
                byName.computeIfAbsent(
                        nameLower,
                        ignored -> new ArrayList<StructureRecord>(8));
        list.add(record);
    }

    private void indexStructureName(Integer dimensionId, String structureName) {
        if (dimensionId == null || structureName == null || structureName.isEmpty()) {
            return;
        }
        LinkedHashSet<String> names =
                this.structureNamesByDimension.computeIfAbsent(
                        dimensionId,
                        ignored -> new LinkedHashSet<String>(32));
        names.add(structureName);
    }

    private void indexSpatial(Integer dimensionId, StructureRecord record) {
        if (dimensionId == null || record == null) {
            return;
        }
        Map<Long, List<StructureRecord>> index =
                this.spatialIndexByDimension.computeIfAbsent(
                        dimensionId,
                        ignored -> new HashMap<Long, List<StructureRecord>>());

        int minCellX = floorDiv(record.boundsMinX, SPATIAL_INDEX_CELL_SIZE);
        int maxCellX = floorDiv(record.boundsMaxX, SPATIAL_INDEX_CELL_SIZE);
        int minCellZ = floorDiv(record.boundsMinZ, SPATIAL_INDEX_CELL_SIZE);
        int maxCellZ = floorDiv(record.boundsMaxZ, SPATIAL_INDEX_CELL_SIZE);
        for (int cellX = minCellX; cellX <= maxCellX; ++cellX) {
            for (int cellZ = minCellZ; cellZ <= maxCellZ; ++cellZ) {
                long key = cellKey(cellX, cellZ);
                List<StructureRecord> bucket =
                        index.computeIfAbsent(
                                key,
                                ignored -> new ArrayList<StructureRecord>(4));
                bucket.add(record);
            }
        }
    }

    private void indexFuzzyBigrams(Integer dimensionId, StructureRecord record) {
        if (dimensionId == null || record == null) {
            return;
        }
        Map<Integer, List<StructureRecord>> index =
                this.fuzzyBigramIndexByDimension.computeIfAbsent(
                        dimensionId,
                        ignored -> new HashMap<Integer, List<StructureRecord>>());

        HashSet<Integer> seen =
                new HashSet<Integer>(Math.max(4, countPossibleBigrams(record.structurePathLower)
                        + countPossibleBigrams(record.structureNameLower)));
        collectBigrams(record.structurePathLower, seen);
        collectBigrams(record.structureNameLower, seen);
        for (Integer bigramCode : seen) {
            if (bigramCode == null) {
                continue;
            }
            List<StructureRecord> bucket =
                    index.computeIfAbsent(
                            bigramCode,
                            ignored -> new ArrayList<StructureRecord>(8));
            bucket.add(record);
        }
    }

    private static void collectBigrams(String value, Set<Integer> output) {
        if (value == null || output == null) {
            return;
        }
        int length = value.length();
        if (length < 2) {
            return;
        }
        for (int i = 0; i < length - 1; ++i) {
            output.add(encodeBigram(value.charAt(i), value.charAt(i + 1)));
        }
    }

    private static int countPossibleBigrams(String value) {
        if (value == null || value.length() < 2) {
            return 0;
        }
        return value.length() - 1;
    }

    private static int encodeBigram(char first, char second) {
        return ((first & 0xFFFF) << 16) | (second & 0xFFFF);
    }

    private List<StructureRecord> recordsInDimension(int dimensionId) {
        List<StructureRecord> list = this.recordsByDimension.get(dimensionId);
        return list == null ? Collections.<StructureRecord>emptyList() : list;
    }

    private List<StructureRecord> recordsByNameInDimension(int dimensionId, String structureNameLower) {
        if (structureNameLower == null || structureNameLower.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, List<StructureRecord>> byName =
                this.recordsByNameLowerByDimension.get(dimensionId);
        if (byName == null) {
            return Collections.emptyList();
        }
        List<StructureRecord> list = byName.get(structureNameLower);
        return list == null ? Collections.<StructureRecord>emptyList() : list;
    }

    private List<StructureRecord> exactMatchesInDimension(int dimensionId, String normalizedQuery) {
        if (normalizedQuery == null || normalizedQuery.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, List<StructureRecord>> index = this.exactMatchIndexByDimension.get(dimensionId);
        if (index == null) {
            return Collections.emptyList();
        }
        List<StructureRecord> matches = index.get(normalizedQuery);
        return matches == null ? Collections.<StructureRecord>emptyList() : matches;
    }

    private Set<String> structureNamesInDimension(int dimensionId) {
        LinkedHashSet<String> names = this.structureNamesByDimension.get(dimensionId);
        return names == null ? Collections.<String>emptySet() : names;
    }

    private List<StructureRecord> fuzzyCandidatesInDimension(int dimensionId, String normalizedQuery) {
        if (normalizedQuery == null || normalizedQuery.isEmpty()) {
            return Collections.emptyList();
        }
        List<StructureRecord> allRecords = recordsInDimension(dimensionId);
        if (normalizedQuery.length() < 2) {
            return allRecords;
        }
        Map<Integer, List<StructureRecord>> index =
                this.fuzzyBigramIndexByDimension.get(dimensionId);
        if (index == null || index.isEmpty()) {
            return allRecords;
        }

        int[] queryBigrams = collectUniqueQueryBigrams(normalizedQuery);
        if (queryBigrams.length == 0) {
            return allRecords;
        }

        List<StructureRecord> smallestBucket = null;
        List<List<StructureRecord>> matchedBuckets =
                new ArrayList<List<StructureRecord>>(queryBigrams.length);
        for (int bigramCode : queryBigrams) {
            List<StructureRecord> bucket = index.get(bigramCode);
            if (bucket == null || bucket.isEmpty()) {
                return Collections.emptyList();
            }
            matchedBuckets.add(bucket);
            if (smallestBucket == null || bucket.size() < smallestBucket.size()) {
                smallestBucket = bucket;
            }
        }
        if (smallestBucket == null || smallestBucket.isEmpty()) {
            return Collections.emptyList();
        }

        int token = nextFuzzyScanToken();
        int required = queryBigrams.length;
        for (List<StructureRecord> bucket : matchedBuckets) {
            for (StructureRecord record : bucket) {
                if (record == null) {
                    continue;
                }
                if (record.fuzzyScanToken != token) {
                    record.fuzzyScanToken = token;
                    record.fuzzyScanMatches = 1;
                } else if (record.fuzzyScanMatches < required) {
                    record.fuzzyScanMatches++;
                }
            }
        }

        List<StructureRecord> filtered =
                new ArrayList<StructureRecord>(Math.min(smallestBucket.size(), FUZZY_CANDIDATE_LIMIT));
        for (StructureRecord record : smallestBucket) {
            if (record == null) {
                continue;
            }
            if (record.fuzzyScanToken != token || record.fuzzyScanMatches < required) {
                continue;
            }
            filtered.add(record);
            if (filtered.size() >= FUZZY_CANDIDATE_LIMIT) {
                break;
            }
        }
        if (filtered.isEmpty()) {
            return Collections.emptyList();
        }
        return filtered;
    }

    private static int[] collectUniqueQueryBigrams(String value) {
        if (value == null || value.length() < 2) {
            return new int[0];
        }

        int pairCount = value.length() - 1;
        int[] codes = new int[pairCount];
        for (int i = 0; i < pairCount; ++i) {
            codes[i] = encodeBigram(value.charAt(i), value.charAt(i + 1));
        }
        if (pairCount == 1) {
            return codes;
        }

        Arrays.sort(codes);
        int uniqueCount = 1;
        for (int i = 1; i < codes.length; ++i) {
            int code = codes[i];
            if (code != codes[uniqueCount - 1]) {
                codes[uniqueCount++] = code;
            }
        }
        if (uniqueCount == codes.length) {
            return codes;
        }
        int[] trimmed = new int[uniqueCount];
        System.arraycopy(codes, 0, trimmed, 0, uniqueCount);
        return trimmed;
    }

    private Map<Long, List<StructureRecord>> spatialIndexInDimension(int dimensionId) {
        Map<Long, List<StructureRecord>> index = this.spatialIndexByDimension.get(dimensionId);
        return index == null ? Collections.<Long, List<StructureRecord>>emptyMap() : index;
    }

    private int nextNearbyScanToken() {
        if (this.nearbyScanToken == Integer.MAX_VALUE) {
            this.nearbyScanToken = 1;
            for (StructureRecord record : this.records) {
                if (record != null) {
                    record.nearbyScanToken = 0;
                }
            }
        }
        return this.nearbyScanToken++;
    }

    private int nextFuzzyScanToken() {
        if (this.fuzzyScanToken == Integer.MAX_VALUE) {
            this.fuzzyScanToken = 1;
            for (StructureRecord record : this.records) {
                if (record != null) {
                    record.fuzzyScanToken = 0;
                    record.fuzzyScanMatches = 0;
                }
            }
        }
        return this.fuzzyScanToken++;
    }

    private int nextExactScanToken() {
        if (this.exactScanToken == Integer.MAX_VALUE) {
            this.exactScanToken = 1;
            for (StructureRecord record : this.records) {
                if (record != null) {
                    record.exactScanToken = 0;
                }
            }
        }
        return this.exactScanToken++;
    }

    private static boolean isWithinDistance(
            StructureRecord record,
            int queryMinX,
            int queryMaxX,
            int queryMinZ,
            int queryMaxZ,
            long minDistanceSq) {
        if (record == null) {
            return false;
        }

        long dx;
        if (queryMaxX < record.boundsMinX) {
            dx = (long) record.boundsMinX - queryMaxX;
        } else if (record.boundsMaxX < queryMinX) {
            dx = (long) queryMinX - record.boundsMaxX;
        } else {
            dx = 0L;
        }

        long dz;
        if (queryMaxZ < record.boundsMinZ) {
            dz = (long) record.boundsMinZ - queryMaxZ;
        } else if (record.boundsMaxZ < queryMinZ) {
            dz = (long) queryMinZ - record.boundsMaxZ;
        } else {
            dz = 0L;
        }

        long distanceSq = dx * dx + dz * dz;
        return distanceSq < minDistanceSq;
    }

    private static int floorDiv(int n, int d) {
        int q = n / d;
        int r = n % d;
        if (r != 0 && ((n ^ d) < 0)) {
            q--;
        }
        return q;
    }

    private static long cellKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static StructureGenerationDataStore get(World world) {
        if (world == null) {
            return null;
        }
        synchronized (STORE_CACHE_LOCK) {
            StructureGenerationDataStore cached = STORE_CACHE.get(world);
            if (cached != null) {
                return cached;
            }
        }

        MapStorage mapStorage = world.mapStorage;
        if (mapStorage == null) {
            synchronized (STORE_CACHE_LOCK) {
                STORE_CACHE.remove(world);
            }
            return null;
        }

        WorldSavedData savedData = mapStorage.loadData(StructureGenerationDataStore.class, DATA_NAME);
        if (savedData instanceof StructureGenerationDataStore) {
            StructureGenerationDataStore resolved = (StructureGenerationDataStore) savedData;
            synchronized (STORE_CACHE_LOCK) {
                STORE_CACHE.put(world, resolved);
            }
            return resolved;
        }
        synchronized (STORE_CACHE_LOCK) {
            STORE_CACHE.remove(world);
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
        synchronized (STORE_CACHE_LOCK) {
            STORE_CACHE.put(world, created);
        }
        created.markDirty();
        return created;
    }

    private static String normalizePath(String path) {
        return StringNormalization.normalizePath(path);
    }

    private static String normalizeQuery(String query) {
        String normalized = normalizePath(query);
        if (normalized == null) {
            return null;
        }

        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.endsWith(SCHEMATIC_SUFFIX)) {
            lower = lower.substring(0, lower.length() - SCHEMATIC_SUFFIX_LENGTH);
        }
        return lower;
    }

    private static String extractStructureName(String normalizedPath) {
        if (normalizedPath == null || normalizedPath.isEmpty()) {
            return UNKNOWN_STRUCTURE_NAME;
        }

        String name = normalizedPath;
        int slash = name.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < name.length()) {
            name = name.substring(slash + 1);
        }
        if (hasSchematicSuffixIgnoreCase(name)) {
            name = name.substring(0, name.length() - SCHEMATIC_SUFFIX_LENGTH);
        }

        return name.isEmpty() ? UNKNOWN_STRUCTURE_NAME : name;
    }

    private static boolean hasSchematicSuffixIgnoreCase(String value) {
        if (value == null) {
            return false;
        }
        int length = value.length();
        return length >= SCHEMATIC_SUFFIX_LENGTH
                && value.regionMatches(
                        true,
                        length - SCHEMATIC_SUFFIX_LENGTH,
                        SCHEMATIC_SUFFIX,
                        0,
                        SCHEMATIC_SUFFIX_LENGTH);
    }

    private static String normalizeStructureName(String configuredStructureName) {
        return StringNormalization.trimToNull(configuredStructureName);
    }

    private static String normalizeToLower(String value) {
        return StringNormalization.trimLowerToNull(value);
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
        private final String structurePathLower;
        private final String structurePathLowerWithSchematic;
        private final String structureNameLower;
        private final String structureNameLowerWithSchematic;
        private final StructureRecordIdentityKey uniqueKey;
        private final int x;
        private final int y;
        private final int z;
        private final int width;
        private final int height;
        private final int length;
        private final int boundsMinX;
        private final int boundsMaxX;
        private final int boundsMinZ;
        private final int boundsMaxZ;
        private int nearbyScanToken;
        private int exactScanToken;
        private int fuzzyScanToken;
        private int fuzzyScanMatches;

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
            this.structurePathLower = structurePath.toLowerCase(Locale.ROOT);
            this.structurePathLowerWithSchematic = this.structurePathLower + SCHEMATIC_SUFFIX;
            this.structureNameLower = structureName.toLowerCase(Locale.ROOT);
            this.structureNameLowerWithSchematic = this.structureNameLower + SCHEMATIC_SUFFIX;
            this.uniqueKey = new StructureRecordIdentityKey(
                    this.dimensionId,
                    this.structurePathLower,
                    x,
                    y,
                    z);
            this.x = x;
            this.y = y;
            this.z = z;
            this.width = Math.max(0, width);
            this.height = Math.max(0, height);
            this.length = Math.max(0, length);
            this.boundsMinX = this.x;
            this.boundsMaxX = this.width > 0 ? this.x + this.width - 1 : this.x;
            this.boundsMinZ = this.z;
            this.boundsMaxZ = this.length > 0 ? this.z + this.length - 1 : this.z;
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

        private String structureNameLower() {
            return this.structureNameLower;
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
            return normalizedQuery.equals(this.structurePathLower)
                    || normalizedQuery.equals(this.structureNameLower)
                    || normalizedQuery.equals(this.structurePathLowerWithSchematic)
                    || normalizedQuery.equals(this.structureNameLowerWithSchematic);
        }

        private boolean matchesFuzzy(String normalizedQuery) {
            return this.structurePathLower.contains(normalizedQuery)
                    || this.structureNameLower.contains(normalizedQuery);
        }

        private StructureRecordIdentityKey uniqueKey() {
            return this.uniqueKey;
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
            if (isBlank(structureName)) {
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

        private static boolean isBlank(String value) {
            return StringNormalization.isBlank(value);
        }
    }

    private static final class StructureRecordIdentityKey {
        private final int dimensionId;
        private final String structurePathLower;
        private final int x;
        private final int y;
        private final int z;

        private StructureRecordIdentityKey(
                int dimensionId,
                String structurePathLower,
                int x,
                int y,
                int z) {
            this.dimensionId = dimensionId;
            this.structurePathLower = structurePathLower == null ? "" : structurePathLower;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof StructureRecordIdentityKey)) {
                return false;
            }
            StructureRecordIdentityKey other = (StructureRecordIdentityKey) obj;
            return this.dimensionId == other.dimensionId
                    && this.x == other.x
                    && this.y == other.y
                    && this.z == other.z
                    && this.structurePathLower.equals(other.structurePathLower);
        }

        @Override
        public int hashCode() {
            int result = this.dimensionId;
            result = 31 * result + this.structurePathLower.hashCode();
            result = 31 * result + this.x;
            result = 31 * result + this.y;
            result = 31 * result + this.z;
            return result;
        }
    }
}
