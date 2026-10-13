package pl.k8bus.outflow.worldgen.math;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

public final class HydrologySim {
    private static final int[][] DIRECTIONS_16 = {
            {16, 0}, {-16, 0}, {0, 16}, {0, -16},
            {16, 16}, {-16, 16}, {16, -16}, {-16, -16}
    };
    private static final int[][] DIRECTIONS_8 = {
            {8, 0}, {-8, 0}, {0, 8}, {0, -8},
            {8, 8}, {-8, 8}, {8, -8}, {-8, -8}
    };
    private static final int[][] DIRECTIONS_4 = {
            {4, 0}, {-4, 0}, {0, 4}, {0, -4}
    };
    private static final int[][] DIRECTIONS_BASIN = {
            {16, 0}, {-16, 0}, {0, 16}, {0, -16}, {12, 12}, {-12, 12}, {12, -12}, {-12, -12}
    };

    private HydrologySim() {}

    public static BlockPos simulateWaterDrop(int startX, int startZ, java.util.function.IntBinaryOperator getHeight, LevelHeightAccessor level, RandomState randomState, ChunkGenerator generator) {
        int currentX = startX;
        int currentZ = startZ;
        int currentY = getHeight.applyAsInt(currentX, currentZ);

        // Phase 1: Macro descent across valley slopes (stride 16) - fast 6 steps
        for (int step = 0; step < 6; step++) {
            int lowestX = currentX;
            int lowestZ = currentZ;
            int lowestY = currentY;

            for (int[] dir : DIRECTIONS_16) {
                int nx = currentX + dir[0];
                int nz = currentZ + dir[1];
                int ny = getHeight.applyAsInt(nx, nz);

                if (ny < lowestY) {
                    lowestY = ny;
                    lowestX = nx;
                    lowestZ = nz;
                }
            }
            if (lowestY >= currentY) break;
            currentX = lowestX;
            currentZ = lowestZ;
            currentY = lowestY;
        }

        // Phase 2: Medium descent into basin hollow (stride 8) - fast 5 steps
        for (int step = 0; step < 5; step++) {
            int lowestX = currentX;
            int lowestZ = currentZ;
            int lowestY = currentY;

            for (int[] dir : DIRECTIONS_8) {
                int nx = currentX + dir[0];
                int nz = currentZ + dir[1];
                int ny = getHeight.applyAsInt(nx, nz);

                if (ny < lowestY) {
                    lowestY = ny;
                    lowestX = nx;
                    lowestZ = nz;
                }
            }
            if (lowestY >= currentY) break;
            currentX = lowestX;
            currentZ = lowestZ;
            currentY = lowestY;
        }

        // Phase 3: Fine descent to deepest local point (stride 4) - fast 3 steps
        for (int step = 0; step < 3; step++) {
            int lowestX = currentX;
            int lowestZ = currentZ;
            int lowestY = currentY;

            for (int[] dir : DIRECTIONS_4) {
                int nx = currentX + dir[0];
                int nz = currentZ + dir[1];
                int ny = getHeight.applyAsInt(nx, nz);

                if (ny < lowestY) {
                    lowestY = ny;
                    lowestX = nx;
                    lowestZ = nz;
                }
            }
            if (lowestY >= currentY) break;
            currentX = lowestX;
            currentZ = lowestZ;
            currentY = lowestY;
        }

        return new BlockPos(currentX, currentY, currentZ);
    }

    public static boolean isValidBasin(BlockPos pos, ChunkGenerator generator, RandomState randomState, LevelHeightAccessor level) {
        if (pos.getY() <= 50 || pos.getY() >= 240) return false;
        int centerY = pos.getY();
        int highSides = 0;
        
        for (int[] dir : DIRECTIONS_BASIN) {
            int edgeY = NoiseCache.getBaseHeightCached(pos.getX() + dir[0], pos.getZ() + dir[1], generator, level, randomState);
            if (edgeY - centerY > 35) {
                highSides++;
            }
        }
        
        return highSides < 4;
    }
}