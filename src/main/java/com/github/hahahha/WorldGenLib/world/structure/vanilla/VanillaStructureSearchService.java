package com.github.hahahha.WorldGenLib.world.structure.vanilla;

import com.github.hahahha.WorldGenLib.util.I18n;
import com.github.hahahha.WorldGenLib.util.StringNormalization;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.BiomeGenBase;
import net.minecraft.ChunkPosition;
import net.minecraft.MapGenStructureData;
import net.minecraft.MapGenVillage;
import net.minecraft.Minecraft;
import net.minecraft.NBTBase;
import net.minecraft.NBTTagCompound;
import net.minecraft.NBTTagList;
import net.minecraft.World;
import net.minecraft.WorldInfo;

public final class VanillaStructureSearchService {
    private static final int DEFAULT_Y = 64;
    private static final int CHUNK_SIZE = 16;
    private static final int FORTRESS_REGION = 16;
    private static final int GRID_REGION = 40;
    private static final int GRID_OFFSET = 20;
    private static final long VILLAGE_SALT = 10387312L;
    private static final long TEMPLE_SALT = 14357617L;

    private static final List<BiomeGenBase> STRONGHOLD_BIOMES = Arrays.asList(
            BiomeGenBase.desert, BiomeGenBase.forest, BiomeGenBase.extremeHills, BiomeGenBase.swampland,
            BiomeGenBase.taiga, BiomeGenBase.icePlains, BiomeGenBase.iceMountains, BiomeGenBase.desertHills,
            BiomeGenBase.forestHills, BiomeGenBase.extremeHillsEdge, BiomeGenBase.jungle, BiomeGenBase.jungleHills);

    private static final List<BiomeGenBase> TEMPLE_BIOMES = Arrays.asList(
            BiomeGenBase.desert, BiomeGenBase.desertHills, BiomeGenBase.jungle, BiomeGenBase.jungleHills, BiomeGenBase.swampland);

    private static final List<VanillaStructureType> TYPES = Arrays.asList(
            VanillaStructureType.of("fortress", "Fortress", false, true, false, "WorldGenLib.structure.vanilla.fortress", "nether_fortress", "nether fortress", "fortress"),
            VanillaStructureType.of("stronghold", "Stronghold", true, false, false, "WorldGenLib.structure.vanilla.stronghold", "end portal", "portal room"),
            VanillaStructureType.of("village", "Village", true, false, false, "WorldGenLib.structure.vanilla.village", "village"),
            VanillaStructureType.of("mineshaft", "Mineshaft", true, false, false, "WorldGenLib.structure.vanilla.mineshaft", "abandoned_mineshaft", "abandoned mineshaft", "mineshaft"),
            VanillaStructureType.of("temple", "Temple", true, false, false, "WorldGenLib.structure.vanilla.temple", "scattered_feature", "scattered feature", "temple", "desert temple", "jungle temple", "swamp hut", "witch hut"),
            VanillaStructureType.of("desert_pyramid", "Temple", true, false, false, "WorldGenLib.structure.vanilla.desert_pyramid", new String[]{"desert temple", "desert pyramid", "desert_pyramid"}, "TeDP"),
            VanillaStructureType.of("jungle_pyramid", "Temple", true, false, false, "WorldGenLib.structure.vanilla.jungle_pyramid", new String[]{"jungle temple", "jungle pyramid", "jungle_pyramid"}, "TeJP"),
            VanillaStructureType.of("witch_hut", "Temple", true, false, false, "WorldGenLib.structure.vanilla.witch_hut", new String[]{"swamp hut", "witch hut", "witch_hut"}, "TeSH"));
    private static final Map<String, VanillaStructureType> TYPES_BY_ALIAS = buildTypeAliasIndex();
    private static final List<VanillaStructureType> TYPES_OVERWORLD = filterTypesByDimension(0);
    private static final List<VanillaStructureType> TYPES_NETHER = filterTypesByDimension(-1);
    private static final List<VanillaStructureType> TYPES_END = filterTypesByDimension(1);
    private static final Object DATA_CACHE_LOCK = new Object();
    private static final int DATA_CACHE_WORLD_MAX_SIZE = 64;
    private static final int DATA_CACHE_PER_WORLD_MAX_SIZE = 64;
    private static final Map<World, Map<String, MapGenStructureData>> DATA_CACHE =
            new WeakHashMap<World, Map<String, MapGenStructureData>>();
    private static final ThreadLocal<Random> RANDOM_CACHE = ThreadLocal.withInitial(Random::new);

    private VanillaStructureSearchService() {}

    public static List<VanillaStructureType> listTypes() { return TYPES; }

    public static List<VanillaStructureType> listTypesForDimension(int dimensionId) {
        if (dimensionId == 0) return TYPES_OVERWORLD;
        if (dimensionId == -1) return TYPES_NETHER;
        if (dimensionId == 1) return TYPES_END;
        return Collections.emptyList();
    }

    public static VanillaStructureType matchType(String query) {
        String q = StringNormalization.trimLowerToNull(query);
        if (q == null) return null;
        return TYPES_BY_ALIAS.get(q);
    }

    public static List<VanillaStructureResult> findNearby(World world, VanillaStructureType type, double cx, double cz, int radius, int maxResults) {
        if (world == null || type == null || radius < 1 || maxResults < 1) return Collections.emptyList();
        double r2 = (double) radius * (double) radius;
        List<StructureCandidate> all = new ArrayList<StructureCandidate>(128);
        MapGenStructureData data = loadData(world, type.mapDataName());
        if (data != null) {
            NBTTagCompound features = data.func_143041_a();
            if (features != null) all.addAll(collectKnownCandidates(features, type, cx, cz, r2));
        }
        all.addAll(collectPredictiveCandidates(world, type, cx, cz, radius, r2));
        if (all.isEmpty()) return Collections.emptyList();
        List<StructureCandidate> merged = mergeCandidates(all, type);
        if (merged.isEmpty()) return Collections.emptyList();
        int resultCount = Math.min(maxResults, merged.size());
        List<VanillaStructureResult> out = new ArrayList<VanillaStructureResult>(resultCount);
        for (int i = 0; i < resultCount; ++i) {
            StructureCandidate c = merged.get(i);
            out.add(new VanillaStructureResult(type, c.centerX, c.centerY, c.centerZ, c.distanceSq));
        }
        return out;
    }

    public static int countKnown(World world, VanillaStructureType type) {
        if (world == null || type == null) {
            return 0;
        }
        Map<VanillaStructureType, Integer> counts =
                countKnownBatch(world, Collections.singletonList(type));
        Integer value = counts.get(type);
        return value == null ? 0 : value.intValue();
    }

    public static Map<VanillaStructureType, Integer> countKnownBatch(
            World world, List<VanillaStructureType> types) {
        if (world == null || types == null || types.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, List<VanillaStructureType>> grouped =
                new HashMap<String, List<VanillaStructureType>>(Math.max(4, types.size() * 2));
        for (VanillaStructureType type : types) {
            if (type == null) {
                continue;
            }
            String mapDataName = type.mapDataName();
            if (mapDataName == null || mapDataName.isEmpty()) {
                continue;
            }
            List<VanillaStructureType> bucket =
                    grouped.computeIfAbsent(
                            mapDataName,
                            ignored -> new ArrayList<VanillaStructureType>(4));
            bucket.add(type);
        }
        if (grouped.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<VanillaStructureType, Integer> output =
                new HashMap<VanillaStructureType, Integer>(types.size() * 2);
        for (Map.Entry<String, List<VanillaStructureType>> entry : grouped.entrySet()) {
            List<VanillaStructureType> groupTypes = entry.getValue();
            if (groupTypes == null || groupTypes.isEmpty()) {
                continue;
            }

            int size = groupTypes.size();
            int[] counts = new int[size];
            MapGenStructureData data = loadData(world, entry.getKey());
            NBTTagCompound features = data == null ? null : data.func_143041_a();
            if (features != null) {
                Collection<?> tags = features.getTags();
                if (tags != null && !tags.isEmpty()) {
                    if (size == 1) {
                        VanillaStructureType singleType = groupTypes.get(0);
                        for (Object o : tags) {
                            if (!(o instanceof NBTTagCompound)) {
                                continue;
                            }
                            NBTTagCompound tag = (NBTTagCompound) o;
                            if (!tag.hasKey("ChunkX") || !tag.hasKey("ChunkZ")) {
                                continue;
                            }
                            if (singleType.matchesStructureTag(tag)) {
                                counts[0]++;
                            }
                        }
                    } else {
                        for (Object o : tags) {
                            if (!(o instanceof NBTTagCompound)) {
                                continue;
                            }
                            NBTTagCompound tag = (NBTTagCompound) o;
                            if (!tag.hasKey("ChunkX") || !tag.hasKey("ChunkZ")) {
                                continue;
                            }
                            for (int i = 0; i < size; ++i) {
                                VanillaStructureType groupedType = groupTypes.get(i);
                                if (groupedType != null && groupedType.matchesStructureTag(tag)) {
                                    counts[i]++;
                                }
                            }
                        }
                    }
                }
            }
            for (int i = 0; i < size; ++i) {
                VanillaStructureType groupedType = groupTypes.get(i);
                if (groupedType != null) {
                    output.put(groupedType, counts[i]);
                }
            }
        }

        if (output.isEmpty()) {
            return Collections.emptyMap();
        }
        return output;
    }

    private static MapGenStructureData loadData(World world, String mapDataName) {
        if (world == null || world.mapStorage == null || mapDataName == null || mapDataName.isEmpty()) {
            return null;
        }
        synchronized (DATA_CACHE_LOCK) {
            Map<String, MapGenStructureData> byName = DATA_CACHE.get(world);
            if (byName != null) {
                MapGenStructureData cached = byName.get(mapDataName);
                if (cached != null) {
                    return cached;
                }
            }
        }

        MapGenStructureData loaded =
                (MapGenStructureData) world.mapStorage.loadData(MapGenStructureData.class, mapDataName);
        if (loaded != null) {
            synchronized (DATA_CACHE_LOCK) {
                if (DATA_CACHE.size() >= DATA_CACHE_WORLD_MAX_SIZE) {
                    evictOneMapEntry(DATA_CACHE);
                }
                Map<String, MapGenStructureData> byName =
                        DATA_CACHE.computeIfAbsent(
                                world,
                                ignored -> newPerWorldDataCache());
                byName.put(mapDataName, loaded);
            }
        }
        return loaded;
    }

    private static <K, V> void evictOneMapEntry(Map<K, V> map) {
        if (map == null || map.isEmpty()) {
            return;
        }
        Iterator<K> iterator = map.keySet().iterator();
        if (!iterator.hasNext()) {
            return;
        }
        K key = iterator.next();
        map.remove(key);
    }

    private static Map<String, MapGenStructureData> newPerWorldDataCache() {
        return new LinkedHashMap<String, MapGenStructureData>(16, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, MapGenStructureData> eldest) {
                return size() > DATA_CACHE_PER_WORLD_MAX_SIZE;
            }
        };
    }

    private static List<StructureCandidate> collectKnownCandidates(NBTTagCompound features, VanillaStructureType type, double ox, double oz, double maxD2) {
        Collection<?> tags = features.getTags();
        int tagCount = tags == null ? 0 : tags.size();
        if (tagCount < 1) {
            return Collections.emptyList();
        }
        List<StructureCandidate> out = new ArrayList<StructureCandidate>(Math.max(8, tagCount));
        Set<Long> dedupe = new HashSet<Long>(Math.max(16, tagCount * 2));
        for (Object obj : tags) {
            if (!(obj instanceof NBTTagCompound)) continue;
            NBTTagCompound tag = (NBTTagCompound) obj;
            if (!tag.hasKey("ChunkX") || !tag.hasKey("ChunkZ")) continue;
            if (!type.matchesStructureTag(tag)) continue;
            int minX;
            int minY;
            int minZ;
            int maxX;
            int maxY;
            int maxZ;
            if (tag.hasKey("BB")) {
                int[] bb = tag.getIntArray("BB");
                if (bb != null && bb.length >= 6) {
                    minX = Math.min(bb[0], bb[3]);
                    minY = Math.min(bb[1], bb[4]);
                    minZ = Math.min(bb[2], bb[5]);
                    maxX = Math.max(bb[0], bb[3]);
                    maxY = Math.max(bb[1], bb[4]);
                    maxZ = Math.max(bb[2], bb[5]);
                } else {
                    int chunkX = tag.getInteger("ChunkX");
                    int chunkZ = tag.getInteger("ChunkZ");
                    minX = chunkX << 4;
                    minY = 0;
                    minZ = chunkZ << 4;
                    maxX = minX + 15;
                    maxY = 255;
                    maxZ = minZ + 15;
                }
            } else {
                int chunkX = tag.getInteger("ChunkX");
                int chunkZ = tag.getInteger("ChunkZ");
                minX = chunkX << 4;
                minY = 0;
                minZ = chunkZ << 4;
                maxX = minX + 15;
                maxY = 255;
                maxZ = minZ + 15;
            }
            int centerX = minX + (maxX - minX + 1) / 2;
            int centerY = minY + (maxY - minY + 1) / 2;
            int centerZ = minZ + (maxZ - minZ + 1) / 2;
            double d2 = dist2ToBounds(ox, oz, minX, maxX, minZ, maxZ);
            if (d2 > maxD2) continue;
            long key = boundsDedupeKey(minX, minZ, maxX, maxZ);
            if (!dedupe.add(key)) continue;
            out.add(new StructureCandidate(centerX, centerY, centerZ, minX, minY, minZ, maxX, maxY, maxZ, d2));
        }
        return out;
    }

    private static List<StructureCandidate> collectPredictiveCandidates(World world, VanillaStructureType type, double ox, double oz, int radius, double maxD2) {
        String id = type.id();
        if ("fortress".equals(id)) return collectFortress(world, ox, oz, radius, maxD2);
        if ("stronghold".equals(id)) return collectStrongholds(world, ox, oz, maxD2);
        if ("village".equals(id)) return collectVillages(world, ox, oz, radius, maxD2);
        if ("temple".equals(id) || "desert_pyramid".equals(id) || "jungle_pyramid".equals(id) || "witch_hut".equals(id)) {
            return collectTemples(world, type, ox, oz, radius, maxD2);
        }
        return Collections.emptyList();
    }
    private static List<StructureCandidate> collectFortress(World world, double ox, double oz, int radius, double maxD2) {
        int cx = floorToInt(ox) >> 4, cz = floorToInt(oz) >> 4, cr = ceilDiv(radius, CHUNK_SIZE);
        int minChunkX = cx - cr, maxChunkX = cx + cr, minChunkZ = cz - cr, maxChunkZ = cz + cr;
        int minRegionX = floorDiv(minChunkX, FORTRESS_REGION), maxRegionX = floorDiv(maxChunkX, FORTRESS_REGION);
        int minRegionZ = floorDiv(minChunkZ, FORTRESS_REGION), maxRegionZ = floorDiv(maxChunkZ, FORTRESS_REGION);
        int estimated = Math.max(0, maxRegionX - minRegionX + 1) * Math.max(0, maxRegionZ - minRegionZ + 1);
        List<StructureCandidate> out = new ArrayList<StructureCandidate>(Math.max(8, estimated));
        long worldSeed = world.getSeed();
        Random rnd = getThreadRandom();
        for (int rx = minRegionX; rx <= maxRegionX; ++rx) {
            for (int rz = minRegionZ; rz <= maxRegionZ; ++rz) {
                rnd.setSeed((long) (rx ^ (rz << 4)) ^ worldSeed);
                rnd.nextInt();
                if (rnd.nextInt(3) != 0) continue;
                int chunkX = (rx << 4) + 4 + rnd.nextInt(8);
                int chunkZ = (rz << 4) + 4 + rnd.nextInt(8);
                int x = (chunkX << 4) + 8, z = (chunkZ << 4) + 8;
                double d2 = dist2ToPoint(ox, oz, x, z);
                if (d2 > maxD2) continue;
                out.add(pointCandidate(x, DEFAULT_Y, z, d2));
            }
        }
        return out;
    }

    private static List<StructureCandidate> collectVillages(World world, double ox, double oz, int radius, double maxD2) {
        if (!canVillageSpawnByState(world)) return Collections.emptyList();
        int cx = floorToInt(ox) >> 4, cz = floorToInt(oz) >> 4, cr = ceilDiv(radius, CHUNK_SIZE);
        int minChunkX = cx - cr, maxChunkX = cx + cr, minChunkZ = cz - cr, maxChunkZ = cz + cr;
        int minRegionX = floorDiv(minChunkX, GRID_REGION), maxRegionX = floorDiv(maxChunkX, GRID_REGION);
        int minRegionZ = floorDiv(minChunkZ, GRID_REGION), maxRegionZ = floorDiv(maxChunkZ, GRID_REGION);
        int estimated = Math.max(0, maxRegionX - minRegionX + 1) * Math.max(0, maxRegionZ - minRegionZ + 1);
        List<StructureCandidate> out = new ArrayList<StructureCandidate>(Math.max(8, estimated));
        long seed = world.getSeed();
        Random rnd = getThreadRandom();
        for (int rx = minRegionX; rx <= maxRegionX; ++rx) {
            for (int rz = minRegionZ; rz <= maxRegionZ; ++rz) {
                int[] c = gridCandidate(rx, rz, GRID_REGION, GRID_OFFSET, VILLAGE_SALT, seed, rnd);
                int chunkX = c[0], chunkZ = c[1];
                if (!world.getWorldChunkManager().areBiomesViable(chunkX * 16 + 8, chunkZ * 16 + 8, 0, MapGenVillage.villageSpawnBiomes)) continue;
                int x = (chunkX << 4) + 8, z = (chunkZ << 4) + 8;
                double d2 = dist2ToPoint(ox, oz, x, z);
                if (d2 > maxD2) continue;
                out.add(pointCandidate(x, DEFAULT_Y, z, d2));
            }
        }
        return out;
    }

    private static List<StructureCandidate> collectTemples(World world, VanillaStructureType type, double ox, double oz, int radius, double maxD2) {
        int cx = floorToInt(ox) >> 4, cz = floorToInt(oz) >> 4, cr = ceilDiv(radius, CHUNK_SIZE);
        int minChunkX = cx - cr, maxChunkX = cx + cr, minChunkZ = cz - cr, maxChunkZ = cz + cr;
        int minRegionX = floorDiv(minChunkX, GRID_REGION), maxRegionX = floorDiv(maxChunkX, GRID_REGION);
        int minRegionZ = floorDiv(minChunkZ, GRID_REGION), maxRegionZ = floorDiv(maxChunkZ, GRID_REGION);
        int estimated = Math.max(0, maxRegionX - minRegionX + 1) * Math.max(0, maxRegionZ - minRegionZ + 1);
        List<StructureCandidate> out = new ArrayList<StructureCandidate>(Math.max(8, estimated));
        long seed = world.getSeed();
        Random rnd = getThreadRandom();
        for (int rx = minRegionX; rx <= maxRegionX; ++rx) {
            for (int rz = minRegionZ; rz <= maxRegionZ; ++rz) {
                int[] c = gridCandidate(rx, rz, GRID_REGION, GRID_OFFSET, TEMPLE_SALT, seed, rnd);
                int x = (c[0] << 4) + 8, z = (c[1] << 4) + 8;
                BiomeGenBase biome = world.getWorldChunkManager().getBiomeGenAt(x, z);
                if (!matchesTempleBiome(type, biome)) continue;
                double d2 = dist2ToPoint(ox, oz, x, z);
                if (d2 > maxD2) continue;
                out.add(pointCandidate(x, DEFAULT_Y, z, d2));
            }
        }
        return out;
    }

    private static List<StructureCandidate> collectStrongholds(World world, double ox, double oz, double maxD2) {
        List<StructureCandidate> out = new ArrayList<StructureCandidate>(3);
        for (int[] ch : strongholdChunks(world)) {
            int x = (ch[0] << 4) + 8, z = (ch[1] << 4) + 8;
            double d2 = dist2ToPoint(ox, oz, x, z);
            if (d2 > maxD2) continue;
            out.add(pointCandidate(x, DEFAULT_Y, z, d2));
        }
        return out;
    }

    private static List<int[]> strongholdChunks(World world) {
        if (world == null) return Collections.emptyList();
        Random rnd = getThreadRandom();
        rnd.setSeed(world.getSeed());
        double angle = rnd.nextDouble() * Math.PI * 2.0;
        int count = 3, spread = 3, ring = 1;
        double distMul = 32.0 * 16.0;
        List<int[]> out = new ArrayList<int[]>(count);
        for (int i = 0; i < count; ++i) {
            double dist = (1.25 * ring + rnd.nextDouble()) * distMul * ring;
            int chunkX = (int) Math.round(Math.cos(angle) * dist);
            int chunkZ = (int) Math.round(Math.sin(angle) * dist);
            ChunkPosition p = world.getWorldChunkManager().findBiomePosition(
                    (chunkX << 4) + 8, (chunkZ << 4) + 8, 112, STRONGHOLD_BIOMES, rnd);
            if (p != null) {
                chunkX = p.x >> 4;
                chunkZ = p.z >> 4;
            }
            out.add(new int[]{chunkX, chunkZ});
            angle += Math.PI * 2.0 * ring / spread;
            if (i == spread - 1) {
                ring += 2 + rnd.nextInt(5);
                spread += 1 + rnd.nextInt(2);
            }
        }
        return out;
    }

    private static boolean canVillageSpawnByState(World world) {
        if (world == null) return false;
        if (Minecraft.isInTournamentMode()) return false;
        if (world.getDayOfWorld() < 60) return false;
        WorldInfo info = world.getWorldInfo();
        if (info == null) return false;
        return info.getVillageConditions() >= WorldInfo.getVillagePrerequisites();
    }

    private static boolean matchesTempleBiome(VanillaStructureType type, BiomeGenBase biome) {
        if (biome == null || !TEMPLE_BIOMES.contains(biome)) return false;
        String id = type.id();
        if ("temple".equals(id)) return true;
        if ("desert_pyramid".equals(id)) return biome == BiomeGenBase.desert || biome == BiomeGenBase.desertHills;
        if ("jungle_pyramid".equals(id)) return biome == BiomeGenBase.jungle || biome == BiomeGenBase.jungleHills;
        if ("witch_hut".equals(id)) return biome == BiomeGenBase.swampland;
        return false;
    }

    private static int[] gridCandidate(
            int regionX,
            int regionZ,
            int regionSize,
            int randomRange,
            long salt,
            long seed,
            Random rnd) {
        rnd.setSeed((long) regionX * 341873128712L + (long) regionZ * 132897987541L + seed + salt);
        int baseX = regionX * regionSize, baseZ = regionZ * regionSize;
        int range = Math.max(1, randomRange);
        return new int[]{baseX + rnd.nextInt(range), baseZ + rnd.nextInt(range)};
    }

    private static long boundsDedupeKey(int minX, int minZ, int maxX, int maxZ) {
        long h = 1469598103934665603L;
        h = (h ^ minX) * 1099511628211L;
        h = (h ^ minZ) * 1099511628211L;
        h = (h ^ maxX) * 1099511628211L;
        h = (h ^ maxZ) * 1099511628211L;
        return h;
    }

    private static Random getThreadRandom() {
        return RANDOM_CACHE.get();
    }

    private static int floorToInt(double v) { int i = (int) v; return v < i ? i - 1 : i; }
    private static int floorDiv(int n, int d) { int q = n / d, r = n % d; if (r != 0 && ((n ^ d) < 0)) q--; return q; }
    private static int ceilDiv(int n, int d) { return n <= 0 ? 0 : 1 + (n - 1) / d; }

    private static StructureCandidate pointCandidate(int x, int y, int z, double d2) { return new StructureCandidate(x, y, z, x, y, z, x, y, z, d2); }

    private static List<StructureCandidate> mergeCandidates(List<StructureCandidate> candidates, VanillaStructureType type) {
        if (candidates.isEmpty()) return Collections.emptyList();
        int pad = mergePadding(type);
        int cellSize = Math.max(1, pad);
        List<StructureCandidate> merged = new ArrayList<StructureCandidate>(candidates.size());
        Map<Long, Set<StructureCandidate>> index =
                new HashMap<Long, Set<StructureCandidate>>(Math.max(16, candidates.size() * 2));
        int visitToken = 1;
        for (StructureCandidate c : candidates) {
            if (visitToken == Integer.MAX_VALUE) {
                clearVisitTokens(merged);
                visitToken = 1;
            }
            StructureCandidate target = findMergeTarget(index, c, pad, cellSize, visitToken++);
            if (target == null) {
                target = c.copy();
                merged.add(target);
            } else {
                target.merge(c);
            }
            indexCandidate(index, target, pad, cellSize);
        }
        merged.sort(Comparator.comparingDouble(StructureCandidate::distanceSq));
        return merged;
    }

    private static StructureCandidate findMergeTarget(
            Map<Long, Set<StructureCandidate>> index,
            StructureCandidate candidate,
            int pad,
            int cellSize,
            int visitToken) {
        int minCellX = floorDiv(candidate.minX - pad, cellSize);
        int maxCellX = floorDiv(candidate.maxX + pad, cellSize);
        int minCellZ = floorDiv(candidate.minZ - pad, cellSize);
        int maxCellZ = floorDiv(candidate.maxZ + pad, cellSize);
        for (int cellX = minCellX; cellX <= maxCellX; ++cellX) {
            for (int cellZ = minCellZ; cellZ <= maxCellZ; ++cellZ) {
                Set<StructureCandidate> bucket = index.get(cellKey(cellX, cellZ));
                if (bucket == null || bucket.isEmpty()) {
                    continue;
                }
                for (StructureCandidate existing : bucket) {
                    if (existing == null || existing.visitToken == visitToken) {
                        continue;
                    }
                    existing.visitToken = visitToken;
                    if (isSameStructure(existing, candidate, pad)) {
                        return existing;
                    }
                }
            }
        }
        return null;
    }

    private static void indexCandidate(
            Map<Long, Set<StructureCandidate>> index,
            StructureCandidate candidate,
            int pad,
            int cellSize) {
        int minCellX = floorDiv(candidate.minX - pad, cellSize);
        int maxCellX = floorDiv(candidate.maxX + pad, cellSize);
        int minCellZ = floorDiv(candidate.minZ - pad, cellSize);
        int maxCellZ = floorDiv(candidate.maxZ + pad, cellSize);
        for (int cellX = minCellX; cellX <= maxCellX; ++cellX) {
            for (int cellZ = minCellZ; cellZ <= maxCellZ; ++cellZ) {
                Set<StructureCandidate> bucket =
                        index.computeIfAbsent(
                                cellKey(cellX, cellZ),
                                ignored -> new HashSet<StructureCandidate>(4));
                bucket.add(candidate);
            }
        }
    }

    private static void clearVisitTokens(List<StructureCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        for (StructureCandidate candidate : candidates) {
            if (candidate != null) {
                candidate.visitToken = 0;
            }
        }
    }

    private static long cellKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static boolean isSameStructure(StructureCandidate a, StructureCandidate b, int pad) {
        if (boxesNear(a, b, pad)) return true;
        long dx = (long) a.centerX - (long) b.centerX;
        long dz = (long) a.centerZ - (long) b.centerZ;
        return dx * dx + dz * dz <= (long) pad * (long) pad;
    }

    private static boolean boxesNear(StructureCandidate a, StructureCandidate b, int pad) {
        return ((long) a.minX - pad) <= b.maxX && ((long) a.maxX + pad) >= b.minX && ((long) a.minZ - pad) <= b.maxZ && ((long) a.maxZ + pad) >= b.minZ;
    }

    private static int mergePadding(VanillaStructureType type) {
        if (type == null) return 96;
        String id = type.id();
        if ("mineshaft".equals(id)) return 384;
        if ("fortress".equals(id)) return 224;
        if ("stronghold".equals(id)) return 160;
        if ("village".equals(id)) return 112;
        if ("temple".equals(id) || "desert_pyramid".equals(id) || "jungle_pyramid".equals(id) || "witch_hut".equals(id)) return 96;
        return 96;
    }

    private static double dist2ToBounds(double px, double pz, int minX, int maxX, int minZ, int maxZ) {
        double dx = 0.0D, dz = 0.0D;
        if (px < minX) dx = minX - px; else if (px > maxX) dx = px - maxX;
        if (pz < minZ) dz = minZ - pz; else if (pz > maxZ) dz = pz - maxZ;
        return dx * dx + dz * dz;
    }

    private static double dist2ToPoint(double ox, double oz, int x, int z) {
        double dx = x - ox, dz = z - oz;
        return dx * dx + dz * dz;
    }

    private static Map<String, VanillaStructureType> buildTypeAliasIndex() {
        Map<String, VanillaStructureType> index = new HashMap<String, VanillaStructureType>(TYPES.size() * 8);
        for (VanillaStructureType type : TYPES) {
            if (type == null || type.aliases == null || type.aliases.isEmpty()) {
                continue;
            }
            for (String alias : type.aliases) {
                if (alias == null || alias.isEmpty()) {
                    continue;
                }
                index.putIfAbsent(alias, type);
            }
        }
        return Collections.unmodifiableMap(index);
    }

    private static List<VanillaStructureType> filterTypesByDimension(int dimensionId) {
        List<VanillaStructureType> out = new ArrayList<VanillaStructureType>(TYPES.size());
        for (VanillaStructureType t : TYPES) {
            if (t != null && t.supportsDimension(dimensionId)) {
                out.add(t);
            }
        }
        return out.isEmpty() ? Collections.<VanillaStructureType>emptyList() : Collections.unmodifiableList(out);
    }

    private static final class StructureCandidate {
        private int centerX, centerY, centerZ;
        private int minX, minY, minZ, maxX, maxY, maxZ;
        private double distanceSq;
        private int visitToken;
        private StructureCandidate(int cx, int cy, int cz, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, double d2) {
            this.centerX = cx; this.centerY = cy; this.centerZ = cz;
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
            this.distanceSq = d2;
        }
        private StructureCandidate copy() { return new StructureCandidate(centerX, centerY, centerZ, minX, minY, minZ, maxX, maxY, maxZ, distanceSq); }
        private double distanceSq() { return distanceSq; }
        private void merge(StructureCandidate o) {
            if (o == null) return;
            minX = Math.min(minX, o.minX); minY = Math.min(minY, o.minY); minZ = Math.min(minZ, o.minZ);
            maxX = Math.max(maxX, o.maxX); maxY = Math.max(maxY, o.maxY); maxZ = Math.max(maxZ, o.maxZ);
            if (o.distanceSq < distanceSq) { centerX = o.centerX; centerY = o.centerY; centerZ = o.centerZ; distanceSq = o.distanceSq; }
        }
    }
    public static final class VanillaStructureType {
        private final String id, mapDataName, translationKey;
        private final boolean supportsOverworld, supportsNether, supportsTheEnd;
        private final Set<String> aliases;
        private final Set<String> componentIds;

        private VanillaStructureType(String id, String mapDataName, boolean ow, boolean n, boolean e, String translationKey, Set<String> aliases, Set<String> componentIds) {
            this.id = id;
            this.mapDataName = mapDataName;
            this.supportsOverworld = ow;
            this.supportsNether = n;
            this.supportsTheEnd = e;
            this.translationKey = translationKey;
            this.aliases = aliases;
            this.componentIds = componentIds;
        }

        public static VanillaStructureType of(String id, String mapDataName, boolean ow, boolean n, boolean e, String translationKey, String... aliases) {
            return of(id, mapDataName, ow, n, e, translationKey, aliases, new String[0]);
        }

        public static VanillaStructureType of(String id, String mapDataName, boolean ow, boolean n, boolean e, String translationKey, String[] aliases, String... componentIds) {
            int aliasCapacity = aliases == null ? 4 : Math.max(4, aliases.length + 2);
            Set<String> aliasSet = new LinkedHashSet<String>(aliasCapacity);
            aliasSet.add(id.toLowerCase(Locale.ROOT));
            aliasSet.add(mapDataName.toLowerCase(Locale.ROOT));
            if (aliases != null) {
                for (String alias : aliases) {
                    if (alias == null) continue;
                    String v = StringNormalization.trimLowerToNull(alias);
                    if (v != null) aliasSet.add(v);
                }
            }

            Set<String> componentSet = null;
            if (componentIds != null && componentIds.length > 0) {
                componentSet = new LinkedHashSet<String>(componentIds.length);
                for (String cid : componentIds) {
                    if (cid == null) continue;
                    String v = StringNormalization.trimToNull(cid);
                    if (v != null) componentSet.add(v);
                }
                if (componentSet.isEmpty()) componentSet = null;
            }

            return new VanillaStructureType(id, mapDataName, ow, n, e, translationKey, aliasSet, componentSet);
        }

        public String id() { return this.id; }
        public String mapDataName() { return this.mapDataName; }
        public String translation() {
            return I18n.tr(this.translationKey, this.id);
        }
        public String displayLabel() {
            return I18n.trf(
                    "WorldGenLib.structure.vanilla.display_label",
                    "%s (%s)",
                    this.id,
                    this.translation());
        }

        public boolean supportsDimension(int dimensionId) {
            if (dimensionId == -1) return this.supportsNether;
            if (dimensionId == 1) return this.supportsTheEnd;
            if (dimensionId == 0) return this.supportsOverworld;
            return false;
        }

        public String supportedDimensionLabel() {
            StringBuilder sb = new StringBuilder();
            if (this.supportsOverworld) appendLabel(sb, I18n.tr("WorldGenLib.dimension.overworld", "Overworld"));
            if (this.supportsNether) appendLabel(sb, I18n.tr("WorldGenLib.dimension.nether", "Nether"));
            if (this.supportsTheEnd) appendLabel(sb, I18n.tr("WorldGenLib.dimension.end", "The End"));
            return sb.length() == 0 ? I18n.tr("WorldGenLib.dimension.none", "None") : sb.toString();
        }

        private static void appendLabel(StringBuilder sb, String value) {
            if (sb.length() > 0) sb.append('/');
            sb.append(value);
        }

        private boolean matches(String query) { return this.aliases.contains(query); }

        private boolean matchesStructureTag(NBTTagCompound structureTag) {
            if (this.componentIds == null || this.componentIds.isEmpty()) return true;
            if (structureTag == null || !structureTag.hasKey("Children")) return false;
            NBTTagList children = structureTag.getTagList("Children");
            if (children == null) return false;
            for (int i = 0; i < children.tagCount(); ++i) {
                NBTBase base = children.tagAt(i);
                if (!(base instanceof NBTTagCompound)) continue;
                String id = ((NBTTagCompound) base).getString("id");
                if (id != null && this.componentIds.contains(id)) return true;
            }
            return false;
        }
    }

    public static final class VanillaStructureResult {
        private final VanillaStructureType structureType;
        private final int x, y, z;
        private final double distanceSq;

        private VanillaStructureResult(VanillaStructureType type, int x, int y, int z, double d2) {
            this.structureType = type;
            this.x = x;
            this.y = y;
            this.z = z;
            this.distanceSq = d2;
        }

        public VanillaStructureType structureType() { return this.structureType; }
        public int x() { return this.x; }
        public int y() { return this.y; }
        public int z() { return this.z; }
        public double distanceSq() { return this.distanceSq; }
    }
}

