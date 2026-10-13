package pl.k8bus.outflow.worldgen.feature.caldera;

import pl.k8bus.outflow.worldgen.feature.lake.LakeCenterData;
import pl.k8bus.outflow.worldgen.feature.lake.LakeCacheKey;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import pl.k8bus.outflow.worldgen.math.NoiseCache;
import pl.k8bus.outflow.worldgen.math.HydrologySim;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import java.util.function.IntBinaryOperator;

public class CalderaLocator {

    public static final AtomicInteger calculatingWaterworks = new AtomicInteger(0);
    public static volatile long lastCalculationTime = 0;
    public static volatile String currentWaterworksStatus = "Initializing...";

    public static List<LakeCenterData> getLakesForGrid(LakeCacheKey gridKey, int currentGx, int currentGz, int cGridSize, WorldGenLevel world, ChunkGenerator generator, net.minecraft.world.level.levelgen.RandomState randomState) {
        try {
            calculatingWaterworks.incrementAndGet();
            lastCalculationTime = System.currentTimeMillis();
            return NoiseCache.getOrCalculateLakeGrid(gridKey, () -> {
                currentWaterworksStatus = "Scanning regional topography...";
                List<LakeCenterData> foundLakes = new ArrayList<>();
                long seed = world.getSeed() ^ ((long) currentGx * 341873128712L)
                        ^ ((long) currentGz * 132897987541L);
                RandomSource random = RandomSource.create(seed);

                // Local cache for gradient descents
                Long2IntMap localHeightCache = new Long2IntOpenHashMap();
                IntBinaryOperator getHeight = (hx, hz) -> {
                    long key = ((long) hx & 0xFFFFFFFFL) | (((long) hz & 0xFFFFFFFFL) << 32);
                    if (localHeightCache.containsKey(key)) {
                        return localHeightCache.get(key);
                    }
                    int h = generator.getBaseHeight(hx, hz, Heightmap.Types.WORLD_SURFACE_WG, world, randomState);
                    localHeightCache.put(key, h);
                    return h;
                };

                // Coarse grid search: Sample region every 32 blocks to find valleys
                int stride = 32;
                int coarseSteps = cGridSize / stride;

                int[][] coarseHeights = new int[coarseSteps][coarseSteps];
                for (int cx = 0; cx < coarseSteps; cx++) {
                    for (int cz = 0; cz < coarseSteps; cz++) {
                        int px = currentGx * cGridSize + cx * stride;
                        int pz = currentGz * cGridSize + cz * stride;
                        coarseHeights[cx][cz] = getHeight.applyAsInt(px, pz);
                    }
                }

                List<int[]> candidates = new ArrayList<>();
                for (int cx = 1; cx < coarseSteps - 1; cx++) {
                    for (int cz = 1; cz < coarseSteps - 1; cz++) {
                        int h = coarseHeights[cx][cz];
                        if (h > 90 && h <= 250) {
                            // Check if it's a local minimum (valley) OR a local maximum (peak)
                            boolean isMin = h <= coarseHeights[cx - 1][cz] &&
                                    h <= coarseHeights[cx + 1][cz] &&
                                    h <= coarseHeights[cx][cz - 1] &&
                                    h <= coarseHeights[cx][cz + 1];
                            boolean isMax = h >= coarseHeights[cx - 1][cz] &&
                                    h >= coarseHeights[cx + 1][cz] &&
                                    h >= coarseHeights[cx][cz - 1] &&
                                    h >= coarseHeights[cx][cz + 1];
                            if (isMin || isMax) {
                                int px = currentGx * cGridSize + cx * stride;
                                int pz = currentGz * cGridSize + cz * stride;
                                candidates.add(new int[] { px, pz, h, isMax ? 1 : 0 });
                            }
                        }
                    }
                }

                // Fallback random candidates if too few found
                if (candidates.size() < 4) {
                    for (int i = 0; i < 10; i++) {
                        int px = currentGx * cGridSize + random.nextInt(cGridSize);
                        int pz = currentGz * cGridSize + random.nextInt(cGridSize);
                        int h = getHeight.applyAsInt(px, pz);
                        if (h > 90 && h <= 250) {
                            candidates.add(new int[] { px, pz, h, 0 });
                        }
                    }
                }

                for (int[] cand : candidates) {
                    if (foundLakes.size() >= 4)
                        break;
                    
                    boolean isPeakCollapse = cand.length > 3 && cand[3] == 1;

                    int currentX = cand[0];
                    int currentZ = cand[1];
                    int cY = cand[2];

                    if (!isPeakCollapse) {
                        // HydrologySim symuluje spływ kropli wody i znajduje najniższy punkt (dno doliny/zlewiska)
                        BlockPos basinCenter = HydrologySim.simulateWaterDrop(cand[0], cand[1], getHeight, world, randomState, generator);
                        currentX = basinCenter.getX();
                        currentZ = basinCenter.getZ();
                        cY = basinCenter.getY();

                        if (cY <= 90 || cY > 240)
                            continue;

                        // HydrologySim waliduje, czy basen nie jest wąskim, stromym wąwozem
                        if (!HydrologySim.isValidBasin(basinCenter, generator, randomState, world)) {
                            continue;
                        }
                    } else {
                        // Peak collapse: sink the bottom of the crater deep into the peak
                        cY = cY - 30 - random.nextInt(40);
                    }

                    // Calderas are huge, but limit to 35-60 to avoid chunk issues and massive ugly spills
                    int r = 35 + random.nextInt(25);

                    int minRimY = 320;
                    
                    // Optymalizacja: Używamy już obliczonej siatki `coarseHeights`, zamiast robić setki nowych zapytań o szum!
                    int nCx = (currentX - currentGx * cGridSize) / stride;
                    int nCz = (currentZ - currentGz * cGridSize) / stride;
                    int coarseRad = Math.max(1, (int)(r * 1.5) / stride);
                    
                    int[][] coarseDirs = {{1,0}, {-1,0}, {0,1}, {0,-1}, {1,1}, {-1,1}, {1,-1}, {-1,-1}};
                    for (int[] dir : coarseDirs) {
                        int rayMax = cY;
                        for (int d = 1; d <= coarseRad; d++) {
                            int cx = nCx + dir[0] * d;
                            int cz = nCz + dir[1] * d;
                            if (cx >= 0 && cx < coarseSteps && cz >= 0 && cz < coarseSteps) {
                                int h = coarseHeights[cx][cz];
                                if (h > rayMax) rayMax = h;
                            } else {
                                // Fallback: jeśli wyjdziemy poza siatkę, używamy bezpiecznego getHeight
                                int px = currentX + dir[0] * d * stride;
                                int pz = currentZ + dir[1] * d * stride;
                                int h = getHeight.applyAsInt(px, pz);
                                if (h > rayMax) rayMax = h;
                            }
                        }
                        if (rayMax < minRimY) {
                            minRimY = rayMax;
                        }
                    }

                    if (minRimY >= cY + 2 || isPeakCollapse) {
                        currentWaterworksStatus = "Simulating fluid raycasts...";
                        
                        // Calderas can be completely dry, partially filled, or completely filled
                        int wLevel = cY - 1; // Default to dry
                        if (random.nextFloat() < 0.6f) { // 60% chance to have water
                            int maxFillHeight = 20 + random.nextInt(60);
                            int theoreticalRim = isPeakCollapse ? cY + 20 : minRimY - 1;
                            wLevel = Math.min(cY + maxFillHeight, theoreticalRim);
                        }

                        boolean validBasin = true;
                        
                        // Raycast radius profile to match lake shape to mountains.
                        double[] rawProfile = new double[32];
                        for (int i = 0; i < 32; i++) {
                            double rad = Math.toRadians(i * (360.0 / 32.0));
                            double dx = Math.cos(rad);
                            double dz = Math.sin(rad);
                            double wallDist = r;

                            for (int step = 4; step <= r; step += 4) {
                                int tx = currentX + (int) (dx * step);
                                int tz = currentZ + (int) (dz * step);
                                int hy = getHeight.applyAsInt(tx, tz);

                                // Stop raycast on shore (3 blocks above water) to limit cliff height.
                                if (!isPeakCollapse && hy >= Math.max(wLevel, cY) + 3) {
                                    wallDist = step;
                                    break;
                                }
                                
                                // For peak collapse, we want a massive crater, but we reject if we hit the outside air (mountain slope ends too early)
                                if (isPeakCollapse && hy < Math.max(wLevel, cY)) {
                                    validBasin = false;
                                    break;
                                }
                                
                                // Odrzucamy jezioro, jeśli nagle zapada się w przepaść (ravine bug)
                                // Ale jeśli to peak collapse, nie odrzucamy, dopóki nie wyjdziemy poza r
                                if (!isPeakCollapse && hy < cY - 5) {
                                    validBasin = false;
                                    break;
                                }
                            }
                            if (!validBasin) break;
                            rawProfile[i] = wallDist;
                        }
                        if (!validBasin) continue;

                        double[] radiusProfile = new double[32];
                        for (int i = 0; i < 32; i++) {
                            radiusProfile[i] = (rawProfile[(i + 31) % 32] + rawProfile[i] * 2.0
                                    + rawProfile[(i + 1) % 32]) / 4.0;
                        }

                        boolean riverFound = false;
                        currentWaterworksStatus = "Validating basin constraints...";

                        // Skanujemy precyzyjnie dookoła brzegu jeziora w poszukiwaniu rzek lub przepaści
                        for (int i = 0; i < 32; i++) {
                            double rad = Math.toRadians(i * (360.0 / 32.0));
                            double dx = Math.cos(rad);
                            double dz = Math.sin(rad);
                            double wallDist = rawProfile[i];

                            for (int extra = 4; extra <= 16; extra += 4) {
                                int tx = currentX + (int) (dx * (wallDist + extra));
                                int tz = currentZ + (int) (dz * (wallDist + extra));
                                int checkY = getHeight.applyAsInt(tx, tz);

                                // Check for rivers
                                if (checkY < wLevel + 2) {
                                    Holder<net.minecraft.world.level.biome.Biome> checkBiome = generator
                                            .getBiomeSource().getNoiseBiome(tx >> 2, checkY >> 2, tz >> 2, randomState.sampler());
                                    if (checkBiome.is(BiomeTags.IS_RIVER)) {
                                        riverFound = true;
                                    }
                                }

                                // Spadek o więcej niż 10 bloków zaraz za krawędzią jeziora oznacza klif/przepaść
                                if (!riverFound && checkY < Math.max(wLevel, cY) - 5) {
                                    validBasin = false;
                                    break;
                                }
                            }
                            if (!validBasin) break;
                        }

                        if (!validBasin) continue;

                        if (riverFound) {
                            wLevel = 64;
                            cY = Math.min(cY, 59);
                            validBasin = true;
                        }

                        if (validBasin) {
                            int mDepth = wLevel - cY + 4; // Decreased to raise bottom by 1 block

                            boolean overlaps = false;
                            for (LakeCenterData existing : foundLakes) {
                                if (Math.hypot(existing.startX - currentX,
                                        existing.startZ - currentZ) < existing.radius + r + 80) {
                                    overlaps = true;
                                    break;
                                }
                            }
                            if (!overlaps) {
                                foundLakes.add(
                                        new LakeCenterData(currentX, currentZ, cY, r, mDepth, wLevel, true,
                                                radiusProfile));
                            }
                        }
                    }
                }
                return foundLakes;
            });
        } catch (Exception e) {
            return null;
        } finally {
            calculatingWaterworks.decrementAndGet();
        }
    }
}
