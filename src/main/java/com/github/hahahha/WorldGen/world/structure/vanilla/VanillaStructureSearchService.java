package com.github.hahahha.WorldGen.world.structure.vanilla;

import com.github.hahahha.WorldGen.util.I18n;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
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
            VanillaStructureType.of("fortress", "Fortress", false, true, false, "worldgen.structure.vanilla.fortress", "nether_fortress", "nether fortress", "fortress"),
            VanillaStructureType.of("stronghold", "Stronghold", true, false, false, "worldgen.structure.vanilla.stronghold", "end portal", "portal room"),
            VanillaStructureType.of("village", "Village", true, false, false, "worldgen.structure.vanilla.village", "village"),
            VanillaStructureType.of("mineshaft", "Mineshaft", true, false, false, "worldgen.structure.vanilla.mineshaft", "abandoned_mineshaft", "abandoned mineshaft", "mineshaft"),
            VanillaStructureType.of("temple", "Temple", true, false, false, "worldgen.structure.vanilla.temple", "scattered_feature", "scattered feature", "temple", "desert temple", "jungle temple", "swamp hut", "witch hut"),
            VanillaStructureType.of("desert_pyramid", "Temple", true, false, false, "worldgen.structure.vanilla.desert_pyramid", new String[]{"desert temple", "desert pyramid", "desert_pyramid"}, "TeDP"),
            VanillaStructureType.of("jungle_pyramid", "Temple", true, false, false, "worldgen.structure.vanilla.jungle_pyramid", new String[]{"jungle temple", "jungle pyramid", "jungle_pyramid"}, "TeJP"),
            VanillaStructureType.of("witch_hut", "Temple", true, false, false, "worldgen.structure.vanilla.witch_hut", new String[]{"swamp hut", "witch hut", "witch_hut"}, "TeSH"));

    private VanillaStructureSearchService() {}

    public static List<VanillaStructureType> listTypes() { return TYPES; }

    public static List<VanillaStructureType> listTypesForDimension(int dimensionId) {
        List<VanillaStructureType> out = new ArrayList<VanillaStructureType>();
        for (VanillaStructureType t : TYPES) if (t.supportsDimension(dimensionId)) out.add(t);
        return out;
    }

    public static VanillaStructureType matchType(String query) {
        if (query == null) return null;
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return null;
        for (VanillaStructureType t : TYPES) if (t.matches(q)) return t;
        return null;
    }

    public static List<VanillaStructureResult> findNearby(World world, VanillaStructureType type, double cx, double cz, int radius, int maxResults) {
        if (world == null || type == null || radius < 1 || maxResults < 1) return Collections.emptyList();
        double r2 = (double) radius * (double) radius;
        List<StructureCandidate> all = new ArrayList<StructureCandidate>();
        MapGenStructureData data = loadData(world, type.mapDataName());
        if (data != null) {
            NBTTagCompound features = data.func_143041_a();
            if (features != null) all.addAll(collectKnownCandidates(features, type, cx, cz, r2));
        }
        all.addAll(collectPredictiveCandidates(world, type, cx, cz, radius, r2));
        if (all.isEmpty()) return Collections.emptyList();
        List<StructureCandidate> merged = mergeCandidates(all, type);
        if (merged.isEmpty()) return Collections.emptyList();
        List<VanillaStructureResult> out = new ArrayList<VanillaStructureResult>();
        for (StructureCandidate c : merged) out.add(new VanillaStructureResult(type, c.centerX, c.centerY, c.centerZ, c.distanceSq));
        out.sort(Comparator.comparingDouble(VanillaStructureResult::distanceSq));
        return out.size() > maxResults ? new ArrayList<VanillaStructureResult>(out.subList(0, maxResults)) : out;
    }

    public static int countKnown(World world, VanillaStructureType type) {
        if (world == null || type == null) return 0;
        MapGenStructureData data = loadData(world, type.mapDataName());
        if (data == null) return 0;
        NBTTagCompound features = data.func_143041_a();
        if (features == null) return 0;
        int count = 0;
        for (Object o : features.getTags()) {
            if (!(o instanceof NBTTagCompound)) continue;
            NBTTagCompound tag = (NBTTagCompound) o;
            if (!type.matchesStructureTag(tag)) continue;
            if (tag.hasKey("ChunkX") && tag.hasKey("ChunkZ")) count++;
        }
        return count;
    }

    private static MapGenStructureData loadData(World world, String mapDataName) {
        if (world == null || world.mapStorage == null || mapDataName == null || mapDataName.isEmpty()) return null;
        return (MapGenStructureData) world.mapStorage.loadData(MapGenStructureData.class, mapDataName);
    }

    private static int[] resolveBounds(NBTTagCompound tag) {
        if (tag != null && tag.hasKey("BB")) {
            int[] bb = tag.getIntArray("BB");
            if (bb != null && bb.length >= 6) {
                return new int[]{Math.min(bb[0], bb[3]), Math.min(bb[1], bb[4]), Math.min(bb[2], bb[5]), Math.max(bb[0], bb[3]), Math.max(bb[1], bb[4]), Math.max(bb[2], bb[5])};
            }
        }
        int chunkX = tag.getInteger("ChunkX");
        int chunkZ = tag.getInteger("ChunkZ");
        int minX = chunkX << 4, minZ = chunkZ << 4;
        return new int[]{minX, 0, minZ, minX + 15, 255, minZ + 15};
    }

    private static int[] resolveCenter(NBTTagCompound tag) {
        int[] b = resolveBounds(tag);
        return new int[]{b[0] + (b[3] - b[0] + 1) / 2, b[1] + (b[4] - b[1] + 1) / 2, b[2] + (b[5] - b[2] + 1) / 2};
    }

    private static List<StructureCandidate> collectKnownCandidates(NBTTagCompound features, VanillaStructureType type, double ox, double oz, double maxD2) {
        List<StructureCandidate> out = new ArrayList<StructureCandidate>();
        Set<String> dedupe = new LinkedHashSet<String>();
        Collection tags = features.getTags();
        for (Object obj : tags) {
            if (!(obj instanceof NBTBase)) continue;
            NBTBase base = (NBTBase) obj;
            if (!(base instanceof NBTTagCompound)) continue;
            NBTTagCompound tag = (NBTTagCompound) base;
            if (!tag.hasKey("ChunkX") || !tag.hasKey("ChunkZ")) continue;
            if (!type.matchesStructureTag(tag)) continue;
            int[] b = resolveBounds(tag);
            int[] c = resolveCenter(tag);
            double d2 = dist2ToBounds(ox, oz, b[0], b[3], b[2], b[5]);
            if (d2 > maxD2) continue;
            String key = b[0] + "|" + b[2] + "|" + b[3] + "|" + b[5];
            if (!dedupe.add(key)) continue;
            out.add(new StructureCandidate(c[0], c[1], c[2], b[0], b[1], b[2], b[3], b[4], b[5], d2));
        }
        out.sort(Comparator.comparingDouble(StructureCandidate::distanceSq));
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
        List<StructureCandidate> out = new ArrayList<StructureCandidate>();
        for (int rx = minRegionX; rx <= maxRegionX; ++rx) {
            for (int rz = minRegionZ; rz <= maxRegionZ; ++rz) {
                Random rnd = new Random((long) (rx ^ (rz << 4)) ^ world.getSeed());
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
        out.sort(Comparator.comparingDouble(StructureCandidate::distanceSq));
        return out;
    }

    private static List<StructureCandidate> collectVillages(World world, double ox, double oz, int radius, double maxD2) {
        if (!canVillageSpawnByState(world)) return Collections.emptyList();
        int cx = floorToInt(ox) >> 4, cz = floorToInt(oz) >> 4, cr = ceilDiv(radius, CHUNK_SIZE);
        int minChunkX = cx - cr, maxChunkX = cx + cr, minChunkZ = cz - cr, maxChunkZ = cz + cr;
        int minRegionX = floorDiv(minChunkX, GRID_REGION), maxRegionX = floorDiv(maxChunkX, GRID_REGION);
        int minRegionZ = floorDiv(minChunkZ, GRID_REGION), maxRegionZ = floorDiv(maxChunkZ, GRID_REGION);
        List<StructureCandidate> out = new ArrayList<StructureCandidate>();
        for (int rx = minRegionX; rx <= maxRegionX; ++rx) {
            for (int rz = minRegionZ; rz <= maxRegionZ; ++rz) {
                int[] c = gridCandidate(rx, rz, GRID_REGION, GRID_OFFSET, VILLAGE_SALT, world.getSeed());
                int chunkX = c[0], chunkZ = c[1];
                if (!world.getWorldChunkManager().areBiomesViable(chunkX * 16 + 8, chunkZ * 16 + 8, 0, MapGenVillage.villageSpawnBiomes)) continue;
                int x = (chunkX << 4) + 8, z = (chunkZ << 4) + 8;
                double d2 = dist2ToPoint(ox, oz, x, z);
                if (d2 > maxD2) continue;
                out.add(pointCandidate(x, DEFAULT_Y, z, d2));
            }
        }
        out.sort(Comparator.comparingDouble(StructureCandidate::distanceSq));
        return out;
    }

    private static List<StructureCandidate> collectTemples(World world, VanillaStructureType type, double ox, double oz, int radius, double maxD2) {
        int cx = floorToInt(ox) >> 4, cz = floorToInt(oz) >> 4, cr = ceilDiv(radius, CHUNK_SIZE);
        int minChunkX = cx - cr, maxChunkX = cx + cr, minChunkZ = cz - cr, maxChunkZ = cz + cr;
        int minRegionX = floorDiv(minChunkX, GRID_REGION), maxRegionX = floorDiv(maxChunkX, GRID_REGION);
        int minRegionZ = floorDiv(minChunkZ, GRID_REGION), maxRegionZ = floorDiv(maxChunkZ, GRID_REGION);
        List<StructureCandidate> out = new ArrayList<StructureCandidate>();
        for (int rx = minRegionX; rx <= maxRegionX; ++rx) {
            for (int rz = minRegionZ; rz <= maxRegionZ; ++rz) {
                int[] c = gridCandidate(rx, rz, GRID_REGION, GRID_OFFSET, TEMPLE_SALT, world.getSeed());
                int x = (c[0] << 4) + 8, z = (c[1] << 4) + 8;
                BiomeGenBase biome = world.getWorldChunkManager().getBiomeGenAt(x, z);
                if (!matchesTempleBiome(type, biome)) continue;
                double d2 = dist2ToPoint(ox, oz, x, z);
                if (d2 > maxD2) continue;
                out.add(pointCandidate(x, DEFAULT_Y, z, d2));
            }
        }
        out.sort(Comparator.comparingDouble(StructureCandidate::distanceSq));
        return out;
    }

    private static List<StructureCandidate> collectStrongholds(World world, double ox, double oz, double maxD2) {
        List<StructureCandidate> out = new ArrayList<StructureCandidate>();
        for (int[] ch : strongholdChunks(world)) {
            int x = (ch[0] << 4) + 8, z = (ch[1] << 4) + 8;
            double d2 = dist2ToPoint(ox, oz, x, z);
            if (d2 > maxD2) continue;
            out.add(pointCandidate(x, DEFAULT_Y, z, d2));
        }
        out.sort(Comparator.comparingDouble(StructureCandidate::distanceSq));
        return out;
    }

    private static List<int[]> strongholdChunks(World world) {
        if (world == null) return Collections.emptyList();
        Random rnd = new Random();
        rnd.setSeed(world.getSeed());
        double angle = rnd.nextDouble() * Math.PI * 2.0;
        int count = 3, spread = 3, ring = 1;
        double distMul = 32.0 * 16.0;
        List<int[]> out = new ArrayList<int[]>(count);
        for (int i = 0; i < count; ++i) {
            double dist = (1.25 * ring + rnd.nextDouble()) * distMul * ring;
            int chunkX = (int) Math.round(Math.cos(angle) * dist);
            int chunkZ = (int) Math.round(Math.sin(angle) * dist);
            ArrayList<BiomeGenBase> biomes = new ArrayList<BiomeGenBase>(STRONGHOLD_BIOMES);
            ChunkPosition p = world.getWorldChunkManager().findBiomePosition((chunkX << 4) + 8, (chunkZ << 4) + 8, 112, biomes, rnd);
            if (p != null) {
                chunkX = p.x >> 4;
                chunkZ = p.z >> 4;
            }
            out.add(new int[]{chunkX, chunkZ});
            angle += Math.PI * 2.0 * ring / spread;
            if (i == spread) {
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

    private static int[] gridCandidate(int regionX, int regionZ, int regionSize, int randomRange, long salt, long seed) {
        Random rnd = new Random((long) regionX * 341873128712L + (long) regionZ * 132897987541L + seed + salt);
        int baseX = regionX * regionSize, baseZ = regionZ * regionSize;
        int range = Math.max(1, randomRange);
        return new int[]{baseX + rnd.nextInt(range), baseZ + rnd.nextInt(range)};
    }

    private static int floorToInt(double v) { int i = (int) v; return v < i ? i - 1 : i; }
    private static int floorDiv(int n, int d) { int q = n / d, r = n % d; if (r != 0 && ((n ^ d) < 0)) q--; return q; }
    private static int ceilDiv(int n, int d) { return n <= 0 ? 0 : 1 + (n - 1) / d; }

    private static StructureCandidate pointCandidate(int x, int y, int z, double d2) { return new StructureCandidate(x, y, z, x, y, z, x, y, z, d2); }

    private static List<StructureCandidate> mergeCandidates(List<StructureCandidate> candidates, VanillaStructureType type) {
        if (candidates.isEmpty()) return Collections.emptyList();
        int pad = mergePadding(type);
        List<StructureCandidate> merged = new ArrayList<StructureCandidate>();
        for (StructureCandidate c : candidates) {
            boolean mergedOne = false;
            for (StructureCandidate e : merged) {
                if (isSameStructure(e, c, pad)) { e.merge(c); mergedOne = true; break; }
            }
            if (!mergedOne) merged.add(c.copy());
        }
        merged.sort(Comparator.comparingDouble(StructureCandidate::distanceSq));
        return merged;
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

    private static final class StructureCandidate {
        private int centerX, centerY, centerZ;
        private int minX, minY, minZ, maxX, maxY, maxZ;
        private double distanceSq;
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
            Set<String> aliasSet = new LinkedHashSet<String>();
            aliasSet.add(id.toLowerCase(Locale.ROOT));
            aliasSet.add(mapDataName.toLowerCase(Locale.ROOT));
            for (String alias : aliases) {
                if (alias == null) continue;
                String v = alias.trim().toLowerCase(Locale.ROOT);
                if (!v.isEmpty()) aliasSet.add(v);
            }

            Set<String> componentSet = null;
            if (componentIds != null && componentIds.length > 0) {
                componentSet = new LinkedHashSet<String>();
                for (String cid : componentIds) {
                    if (cid == null) continue;
                    String v = cid.trim();
                    if (!v.isEmpty()) componentSet.add(v);
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
                    "worldgen.structure.vanilla.display_label",
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
            if (this.supportsOverworld) appendLabel(sb, I18n.tr("worldgen.dimension.overworld", "Overworld"));
            if (this.supportsNether) appendLabel(sb, I18n.tr("worldgen.dimension.nether", "Nether"));
            if (this.supportsTheEnd) appendLabel(sb, I18n.tr("worldgen.dimension.end", "The End"));
            return sb.length() == 0 ? I18n.tr("worldgen.dimension.none", "None") : sb.toString();
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

