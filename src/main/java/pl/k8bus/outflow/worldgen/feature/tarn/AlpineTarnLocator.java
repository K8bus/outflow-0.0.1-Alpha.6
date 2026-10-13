package pl.k8bus.outflow.worldgen.feature.tarn;

import pl.k8bus.outflow.worldgen.feature.lake.LakeCenterData;
import pl.k8bus.outflow.worldgen.feature.lake.LakeCacheKey;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntBinaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import pl.k8bus.outflow.worldgen.math.NoiseCache;
import pl.k8bus.outflow.worldgen.math.HydrologySim;

public class AlpineTarnLocator {

    public static final AtomicInteger calculatingWaterworks = new AtomicInteger(0);
    public static volatile long lastCalculationTime = 0;
    public static volatile String currentWaterworksStatus = "Initializing...";

    public static List<LakeCenterData> getLakesForGrid(LakeCacheKey gridKey, int currentGx, int currentGz, int cGridSize,
            WorldGenLevel world, ChunkGenerator generator, net.minecraft.world.level.levelgen.RandomState randomState) {
        try {
            calculatingWaterworks.incrementAndGet();
            lastCalculationTime = System.currentTimeMillis();
            return NoiseCache.getOrCalculateLakeGrid(gridKey, () ->
                computeLakesDirect(gridKey, currentGx, currentGz, cGridSize, world, generator, randomState)
            );
        } catch (Exception e) {
            return Collections.emptyList();
        } finally {
            calculatingWaterworks.decrementAndGet();
        }
    }

    public static void prefetchNeighbors(long worldSeed, int gridX, int gridZ, int cGridSize,
            WorldGenLevel world, ChunkGenerator generator, net.minecraft.world.level.levelgen.RandomState randomState) {
        int[][] deltas = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : deltas) {
            int nx = gridX + d[0];
            int nz = gridZ + d[1];
            LakeCacheKey key = new LakeCacheKey(worldSeed, nx, nz);
            NoiseCache.prefetchLakeGrid(key, () ->
                computeLakesDirect(key, nx, nz, cGridSize, world, generator, randomState)
            );
        }
    }

    private static List<LakeCenterData> computeLakesDirect(LakeCacheKey gridKey, int currentGx, int currentGz, int cGridSize,
            WorldGenLevel world, ChunkGenerator generator, net.minecraft.world.level.levelgen.RandomState randomState) {
        currentWaterworksStatus = "Scanning regional topography...";

        long seed = world.getSeed() ^ ((long) currentGx * 341873128712L) ^ ((long) currentGz * 132897987541L);
        RandomSource random = RandomSource.create(seed);

        IntBinaryOperator getHeight = (hx, hz) -> NoiseCache.getBaseHeightCached(hx, hz, generator, world, randomState);

        // KROK 1: Szybkie, zrównoleglone próbkowanie makrotopografii (stride = 32)
        // 512 / 32 = 16 kroków -> tylko 256 punktów zamiast 625!
        int stride = 32;
        int coarseSteps = cGridSize / stride;
        int[][] coarseHeights = new int[coarseSteps][coarseSteps];
        AtomicInteger maxH = new AtomicInteger(Integer.MIN_VALUE);
        AtomicInteger minH = new AtomicInteger(Integer.MAX_VALUE);

        IntStream.range(0, coarseSteps * coarseSteps).parallel().forEach(idx -> {
            int cx = idx / coarseSteps;
            int cz = idx % coarseSteps;
            int px = currentGx * cGridSize + cx * stride;
            int pz = currentGz * cGridSize + cz * stride;
            int h = getHeight.applyAsInt(px, pz);
            coarseHeights[cx][cz] = h;
            maxH.accumulateAndGet(h, Math::max);
            minH.accumulateAndGet(h, Math::min);
        });

        // KROK 2: Błyskawiczny Early Exit dla całkowicie płaskiego terenu
        if (maxH.get() < 64 || (maxH.get() - minH.get() < 12)) {
            return Collections.emptyList();
        }

        // KROK 3: Wykrywanie lokalnych zagłębień terenu (dolin i kotłów)
        List<int[]> candidates = new ArrayList<>();
        for (int cx = 1; cx < coarseSteps - 1; cx++) {
            for (int cz = 1; cz < coarseSteps - 1; cz++) {
                int h = coarseHeights[cx][cz];
                if (h > 62 && h <= 240) {
                    int higherNeighbors = 0;
                    if (coarseHeights[cx - 1][cz] >= h) higherNeighbors++;
                    if (coarseHeights[cx + 1][cz] >= h) higherNeighbors++;
                    if (coarseHeights[cx][cz - 1] >= h) higherNeighbors++;
                    if (coarseHeights[cx][cz + 1] >= h) higherNeighbors++;

                    if (higherNeighbors >= 2) {
                        int px = currentGx * cGridSize + cx * stride;
                        int pz = currentGz * cGridSize + cz * stride;
                        candidates.add(new int[] { px, pz, h });
                    }
                }
            }
        }

        // Uzupełniające próbkowanie kotlin, jeśli siatka znalazła mało punktów
        if (candidates.size() < 12) {
            for (int i = 0; i < 16; i++) {
                int px = currentGx * cGridSize + random.nextInt(cGridSize);
                int pz = currentGz * cGridSize + random.nextInt(cGridSize);
                int h = getHeight.applyAsInt(px, pz);
                if (h > 63 && h <= 240) {
                    candidates.add(new int[] { px, pz, h });
                }
            }
        }

        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        // Ograniczamy do najgłębszych dolin
        candidates.sort((a, b) -> Integer.compare(a[2], b[2]));
        if (candidates.size() > 28) {
            candidates = new ArrayList<>(candidates.subList(0, 28));
        }

        // KROK 4: Równoległa analiza kandydatów na jeziora na wszystkich rdzeniach CPU
        final long baseSeed = seed;
        List<LakeCenterData> rawLakes = candidates.parallelStream()
            .map(cand -> evaluateCandidate(cand, baseSeed, getHeight, world, generator, randomState))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        if (rawLakes.isEmpty()) {
            return Collections.emptyList();
        }

        // KROK 5: Deterministyczne chainowanie i odrzucanie duplikatów
        List<LakeCenterData> filteredLakes = new ArrayList<>();
        for (LakeCenterData lake : rawLakes) {
            if (filteredLakes.size() >= 16) break;
            boolean skip = false;
            for (LakeCenterData existing : filteredLakes) {
                double dist = Math.hypot(existing.startX - lake.startX, existing.startZ - lake.startZ);
                int heightDiff = Math.abs(existing.waterLevel - lake.waterLevel);

                // Bardzo bliski duplikat tej samej pozycji
                if (dist < 25) {
                    skip = true;
                    break;
                }

                // Naturalny łańcuch jezior (paternoster/multi-lobe): jeśli leżą blisko w tej samej dolinie na równej wysokości
                if (dist < existing.radius + lake.radius + 30 && heightDiff <= 2) {
                    int sharedLevel = Math.min(existing.waterLevel, lake.waterLevel);
                    existing.waterLevel = sharedLevel;
                    lake.waterLevel = sharedLevel;
                    existing.connectLobe(lake);
                    lake.connectLobe(existing);
                    break;
                } else if (dist < existing.radius + lake.radius + 35) {
                    skip = true;
                    break;
                }
            }
            if (!skip && !filteredLakes.contains(lake)) {
                filteredLakes.add(lake);
            }
        }

        return filteredLakes;
    }

    private static LakeCenterData evaluateCandidate(int[] cand, long seed, IntBinaryOperator getHeight,
            WorldGenLevel world, ChunkGenerator generator, net.minecraft.world.level.levelgen.RandomState randomState) {
        
        BlockPos basinCenter = HydrologySim.simulateWaterDrop(cand[0], cand[1], getHeight, world, randomState, generator);
        int currentX = basinCenter.getX();
        int currentZ = basinCenter.getZ();
        int cY = basinCenter.getY();

        if (cY <= 63 || cY > 240) return null;
        if (!HydrologySim.isValidBasin(basinCenter, generator, randomState, world)) return null;

        RandomSource candRand = RandomSource.create(seed ^ ((long) currentX * 31L + (long) currentZ * 17L));
        int maxR = 36 + candRand.nextInt(28);

        // Ściany kotliny – wznoszące się zbocza na min. 3 z 8 stron
        int wallDirections = 0;
        int[][] fastDirs = {{1,0}, {-1,0}, {0,1}, {0,-1}, {1,1}, {-1,1}, {1,-1}, {-1,-1}};
        for (int[] dir : fastDirs) {
            int h1 = getHeight.applyAsInt(currentX + dir[0] * 24, currentZ + dir[1] * 24);
            int h2 = getHeight.applyAsInt(currentX + dir[0] * 40, currentZ + dir[1] * 40);
            if (h1 >= cY + 2 || h2 >= cY + 4) {
                wallDirections++;
            }
        }
        if (wallDirections < 3) return null;

        int wLevel = cY + 2 + candRand.nextInt(2);

        double[] rawProfile = new double[32];
        double sumRadius = 0;
        BlockPos bestSpillwayPos = null;
        int maxSpillwayDrop = 0;

        for (int i = 0; i < 32; i++) {
            double rad = Math.toRadians(i * (360.0 / 32.0));
            double dx = Math.cos(rad);
            double dz = Math.sin(rad);
            double wallDist = maxR;

            for (int step = 4; step <= maxR; step += 3) {
                int tx = currentX + (int) (dx * step);
                int tz = currentZ + (int) (dz * step);
                int hy = getHeight.applyAsInt(tx, tz);

                // 1. Zbocze góry wznoszące się powyżej poziomu wody
                if (hy >= wLevel + 1) {
                    wallDist = step;
                    break;
                }

                // 2. Teren opada w dół doliny poniżej dna basenu (próg wypływu / spillway)
                if (step >= 16 && hy < cY - 2) {
                    wallDist = Math.max(10, step - 2);
                    if (wLevel - hy > maxSpillwayDrop) {
                        maxSpillwayDrop = wLevel - hy;
                        bestSpillwayPos = new BlockPos(tx, wLevel, tz);
                    }
                    break;
                }
            }
            rawProfile[i] = wallDist;
            sumRadius += wallDist;
        }

        int r = (int) Math.round(sumRadius / 32.0);
        if (r < 10) return null;

        double[] radiusProfile = new double[32];
        for (int i = 0; i < 32; i++) {
            radiusProfile[i] = (rawProfile[(i + 31) % 32] + rawProfile[i] * 2.0 + rawProfile[(i + 1) % 32]) / 4.0;
        }

        // Sprawdzamy czy nie nachodzi na rzeki
        for (int i = 0; i < 32; i += 4) {
            double rad = Math.toRadians(i * (360.0 / 32.0));
            int tx = currentX + (int) (Math.cos(rad) * (rawProfile[i] + 8));
            int tz = currentZ + (int) (Math.sin(rad) * (rawProfile[i] + 8));
            int checkY = getHeight.applyAsInt(tx, tz);
            if (checkY < wLevel + 2) {
                Holder<net.minecraft.world.level.biome.Biome> checkBiome = generator
                        .getBiomeSource().getNoiseBiome(tx >> 2, checkY >> 2, tz >> 2, randomState.sampler());
                if (checkBiome.is(BiomeTags.IS_RIVER)) {
                    wLevel = 64;
                    cY = Math.min(cY, 59);
                    break;
                }
            }
        }

        int mDepth = wLevel - cY + 4;
        return new LakeCenterData(currentX, currentZ, cY, r, mDepth, wLevel, true, radiusProfile, bestSpillwayPos);
    }
}
