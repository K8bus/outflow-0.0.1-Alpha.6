package pl.k8bus.outflow.worldgen.feature;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Breaks up a solid lake ice sheet into cracked, polygonal floes - Worley
 * (cellular) noise cells with water in the gaps between them, instead of
 * one continuous ice cap. The outer ring near shore is forced to stay
 * solid ice (no cracks), so only the open middle of the lake breaks up.
 */
public final class IceFloePhase {

    private static final int CELL_SIZE = 6;
    private static final double CRACK_WIDTH = 0.16;
    private static final int SHORE_MARGIN = 3;

    private IceFloePhase() {
    }

    public static void apply(WorldGenLevel world, int chunkX, int chunkZ, long worldSeed) {
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int worldX = minX + lx;
                int worldZ = minZ + lz;

                int topY = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, worldX, worldZ);
                pos.set(worldX, topY, worldZ);
                BlockState state = world.getBlockState(pos);

                if (!state.is(Blocks.ICE)) {
                    continue;
                }

                if (isNearShore(world, worldX, topY, worldZ)) {
                    continue;
                }

                if (isCrack(worldX, worldZ, worldSeed)) {
                    world.setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
                }
            }
        }
    }

    private static boolean isNearShore(WorldGenLevel world, int x, int y, int z) {
        BlockPos.MutableBlockPos npos = new BlockPos.MutableBlockPos();
        for (int dx = -SHORE_MARGIN; dx <= SHORE_MARGIN; dx++) {
            for (int dz = -SHORE_MARGIN; dz <= SHORE_MARGIN; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                npos.set(x + dx, y, z + dz);
                BlockState neighbor = world.getBlockState(npos);
                boolean isIceOrWater = neighbor.is(Blocks.ICE) || !neighbor.getFluidState().isEmpty();
                if (!isIceOrWater) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isCrack(int worldX, int worldZ, long seed) {
        double px = worldX / (double) CELL_SIZE;
        double pz = worldZ / (double) CELL_SIZE;

        int cellX = (int) Math.floor(px);
        int cellZ = (int) Math.floor(pz);

        double best = Double.MAX_VALUE;
        double secondBest = Double.MAX_VALUE;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int cx = cellX + dx;
                int cz = cellZ + dz;

                double[] jitter = cellPoint(cx, cz, seed);
                double distX = (cx + jitter[0]) - px;
                double distZ = (cz + jitter[1]) - pz;
                double dist = distX * distX + distZ * distZ;

                if (dist < best) {
                    secondBest = best;
                    best = dist;
                } else if (dist < secondBest) {
                    secondBest = dist;
                }
            }
        }

        double edgeDistance = Math.sqrt(secondBest) - Math.sqrt(best);
        return edgeDistance < CRACK_WIDTH;
    }

    private static double[] cellPoint(int cellX, int cellZ, long seed) {
        long h = seed;
        h = h * 6364136223846793005L + cellX * 1442695040888963407L;
        h = h * 6364136223846793005L + cellZ * 1442695040888963407L;
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);

        double jx = (h & 0xFFFF) / 65536.0;
        double jz = ((h >>> 16) & 0xFFFF) / 65536.0;
        return new double[] { jx, jz };
    }
}
