package pl.k8bus.outflow.worldgen.feature;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Overrides topsoil with stone or deepslate on steep terrain, regardless of biome.
 * Runs as a post-buildSurface phase.
 *
 * Uses a steepness threshold based on surface block height differences.
 */
public final class CliffFacePhase {

    private static final int STEEP_THRESHOLD = 4;
    private static final int STRIP_DEPTH = 8;

    private CliffFacePhase() {
    }

    public static void apply(ChunkAccess chunk) {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();

        // [OPTIMIZATION]: Pre-calculate real heights for the 16x16 chunk to avoid recalculating 4 times per block.
        int[][] heights = new int[16][16];
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                heights[lx][lz] = getRealHeight(chunk, lx, lz);
            }
        }

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                if (!isSteep(heights, lx, lz)) {
                    continue;
                }

                int h = heights[lx][lz];
                stripTopsoil(chunk, minX + lx, h, minZ + lz);
            }
        }
    }

    private static int getRealHeight(ChunkAccess chunk, int lx, int lz) {
        // WORLD_SURFACE_WG can be slightly off after Carvers modify the chunk.
        // We start near the estimated surface rather than scanning 384 blocks from top to bottom.
        int startY = Math.min(chunk.getMaxY(), chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, lx, lz) + 4);
        int minY = chunk.getMinY();
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos(lx, startY, lz);
        for (int y = startY; y >= minY; y--) {
            mpos.setY(y);
            BlockState state = chunk.getBlockState(mpos);
            if (state.isAir() || state.is(Blocks.WATER) || state.is(Blocks.ICE) || state.is(Blocks.SNOW)) {
                continue; // Skip air, water, ice, and thin snow layers
            }
            if (state.isSolidRender() || isSoftTopsoil(state)) {
                return y;
            }
        }
        return minY;
    }

    private static boolean isSteep(int[][] heights, int lx, int lz) {
        int north = Math.max(lz - 1, 0);
        int south = Math.min(lz + 1, 15);
        int alongZ = heights[lx][north] - heights[lx][south];
        int zDist = south - north;
        double slopeZ = zDist > 0 ? (double) Math.abs(alongZ) / zDist : 0;

        int west = Math.max(lx - 1, 0);
        int east = Math.min(lx + 1, 15);
        int alongX = heights[west][lz] - heights[east][lz];
        int xDist = east - west;
        double slopeX = xDist > 0 ? (double) Math.abs(alongX) / xDist : 0;

        // Use Euclidean gradient to properly catch diagonal slopes
        // STEEP_THRESHOLD of 4 across 2 blocks means a slope of 2.0 is required.
        double gradient = Math.sqrt(slopeX * slopeX + slopeZ * slopeZ);
        return gradient >= (STEEP_THRESHOLD / 2.0);
    }

    private static void stripTopsoil(ChunkAccess chunk, int worldX, int topY, int worldZ) {
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        for (int y = topY; y > topY - STRIP_DEPTH; y--) {
            mpos.set(worldX, y, worldZ);
            BlockState state = chunk.getBlockState(mpos);
            if (isSoftTopsoil(state)) {
                chunk.setBlockState(mpos, cliffBlock(y), 2);
            } else if (!state.isAir()) {
                break;
            }
        }
    }

    private static BlockState cliffBlock(int y) {
        return y < 0 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState();
    }

    private static boolean isSoftTopsoil(BlockState state) {
        return state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.PODZOL);
    }
}
