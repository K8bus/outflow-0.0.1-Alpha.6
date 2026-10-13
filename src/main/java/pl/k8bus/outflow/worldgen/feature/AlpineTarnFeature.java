package pl.k8bus.outflow.worldgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import pl.k8bus.outflow.worldgen.feature.lake.LakeCacheKey;
import pl.k8bus.outflow.worldgen.feature.lake.LakeCenterData;
import pl.k8bus.outflow.worldgen.feature.tarn.AlpineTarnLocator;
import pl.k8bus.outflow.worldgen.feature.tarn.AlpineTarnBuilder;

public class AlpineTarnFeature extends Feature<NoneFeatureConfiguration> {

    public AlpineTarnFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel world = context.level();
        ChunkGenerator generator = context.chunkGenerator();
        net.minecraft.world.level.levelgen.RandomState randomState = world.getLevel().getChunkSource().randomState();
        BlockPos currentOrigin = context.origin();

        int currentChunkX = currentOrigin.getX() >> 4;
        int currentChunkZ = currentOrigin.getZ() >> 4;

        int stableX = currentChunkX << 4;
        int stableZ = currentChunkZ << 4;

        // Tweakable: Size of the grid cell for lake placement
        int gridSize = 512; // 32 * 16 = 512 blocks
        int gridX = Math.floorDiv(stableX, gridSize);
        int gridZ = Math.floorDiv(stableZ, gridSize);
        long worldSeed = world.getSeed();

        // Asynchroniczne zlecenie pre-kalkulacji sąsiednich kratek w tle
        AlpineTarnLocator.prefetchNeighbors(worldSeed, gridX, gridZ, gridSize, world, generator, randomState);

        // PHASE 1: Collect lakes from necessary grids
        List<LakeCenterData> allLakes = new ArrayList<>();

        int searchRadius = 192;
        int minSearchX = gridX;
        int maxSearchX = gridX;
        int minSearchZ = gridZ;
        int maxSearchZ = gridZ;

        if (stableX - searchRadius < gridX * gridSize) minSearchX = gridX - 1;
        if (stableX + searchRadius >= (gridX + 1) * gridSize) maxSearchX = gridX + 1;

        if (stableZ - searchRadius < gridZ * gridSize) minSearchZ = gridZ - 1;
        if (stableZ + searchRadius >= (gridZ + 1) * gridSize) maxSearchZ = gridZ + 1;

        for (int gx = minSearchX; gx <= maxSearchX; gx++) {
            for (int gz = minSearchZ; gz <= maxSearchZ; gz++) {

                LakeCacheKey gridKey = new LakeCacheKey(worldSeed, gx, gz);

                final int currentGx = gx;
                final int currentGz = gz;
                final int cGridSize = gridSize;

                List<LakeCenterData> lakes = AlpineTarnLocator.getLakesForGrid(gridKey, currentGx, currentGz, cGridSize, world, generator, randomState);

                if (lakes != null) {
                    allLakes.addAll(lakes);
                }
            }
        }

        // PHASE 2: Deduplikacja jezior pomiędzy sąsiednimi kratkami regionalnymi
        Map<Long, LakeCenterData> uniqueLakes = new LinkedHashMap<>();
        for (LakeCenterData lake : allLakes) {
            long key = ((long) lake.startX << 32) | ((long) lake.startZ & 0xFFFFFFFFL);
            uniqueLakes.putIfAbsent(key, lake);
        }

        // PHASE 3: Placement of filtered lakes
        for (LakeCenterData lake : uniqueLakes.values()) {
            // Sprawdzenie czy w ogóle przecinamy bounding box jeziora
            int maxR = lake.radius + 96;
            if (lake.radiusProfile != null) {
                for (double pr : lake.radiusProfile) {
                    if (pr + 96 > maxR) {
                        maxR = (int) Math.ceil(pr + 96);
                    }
                }
            }
            int minX = lake.startX - maxR;
            int maxX = lake.startX + maxR;
            int minZ = lake.startZ - maxR;
            int maxZ = lake.startZ + maxR;

            int chunkMinX = currentChunkX << 4;
            int chunkMaxX = chunkMinX + 15;
            int chunkMinZ = currentChunkZ << 4;
            int chunkMaxZ = chunkMinZ + 15;

            if (chunkMaxX < minX || chunkMinX > maxX || chunkMaxZ < minZ || chunkMinZ > maxZ) {
                continue;
            }

            AlpineTarnBuilder.buildTarn(lake, world, currentOrigin);
        }
        
        return !uniqueLakes.isEmpty();
    }
}
