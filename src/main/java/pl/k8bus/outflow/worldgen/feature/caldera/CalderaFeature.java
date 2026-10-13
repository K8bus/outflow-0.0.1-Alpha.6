package pl.k8bus.outflow.worldgen.feature.caldera;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.ArrayList;
import java.util.List;

import pl.k8bus.outflow.worldgen.feature.lake.LakeCacheKey;
import pl.k8bus.outflow.worldgen.feature.lake.LakeCenterData;
import pl.k8bus.outflow.worldgen.feature.caldera.CalderaLocator;
import pl.k8bus.outflow.worldgen.feature.caldera.CalderaBuilder;

public class CalderaFeature extends Feature<NoneFeatureConfiguration> {

    public CalderaFeature(Codec<NoneFeatureConfiguration> codec) {
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

        // PHASE 1: Collect lakes from necessary grids
        List<LakeCenterData> allLakes = new ArrayList<>();
        long worldSeed = world.getSeed();

        int searchRadius = 200;
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

                List<LakeCenterData> lakes = CalderaLocator.getLakesForGrid(gridKey, currentGx, currentGz, cGridSize, world, generator, randomState);

                if (lakes != null) {
                    allLakes.addAll(lakes);
                }
            }
        }

        // PHASE 2: Sort lakes deterministically.
        // Sorting instead of rejecting prevents chunk border issues.
        // Higher lakes will overwrite lower ones.
        List<LakeCenterData> filteredLakes = new ArrayList<>(allLakes);
        filteredLakes.sort((a, b) -> {
            if (a.waterLevel != b.waterLevel)
                return Integer.compare(a.waterLevel, b.waterLevel);
            if (a.startX != b.startX)
                return Integer.compare(a.startX, b.startX);
            return Integer.compare(a.startZ, b.startZ);
        });
        // PHASE 3: Placement of filtered lakes
        for (LakeCenterData lake : filteredLakes) {
            // Dodatkowe sprawdzenie, czy w ogóle przecinamy bounding box jeziora
            int maxR = lake.radius + 32;
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

            CalderaBuilder.buildTarn(lake, world, currentOrigin);
        }
        
        return !filteredLakes.isEmpty();
    }
}
