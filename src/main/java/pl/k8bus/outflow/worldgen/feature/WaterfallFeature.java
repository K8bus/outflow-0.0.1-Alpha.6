package pl.k8bus.outflow.worldgen.feature;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

public class WaterfallFeature {

    public enum WaterfallType {
        SINGLE_COLUMN,
        DOUBLE_CASCADE,
        WIDE_CURTAIN
    }

    public static WaterfallType selectType(float dropHeight, float sourceWidth) {
        if (sourceWidth > 4.0f) return WaterfallType.WIDE_CURTAIN;
        if (dropHeight > 12.0f) return WaterfallType.DOUBLE_CASCADE;
        return WaterfallType.SINGLE_COLUMN;
    }

    /**
     * Traces and generates a natural mountain cascade / waterfall following the terrain contours downhill.
     */
    public static void placeWaterfall(WorldGenLevel world, BlockPos topPos, int targetY, WaterfallType type) {
        int currX = topPos.getX();
        int currZ = topPos.getZ();
        int lastY = topPos.getY();

        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState mossyCobble = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState gravel = Blocks.GRAVEL.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();

        int maxSteps = 40;
        int lastDirX = 0;
        int lastDirZ = 0;

        for (int step = 0; step < maxSteps; step++) {
            int surfaceY = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, currX, currZ);
            if (surfaceY <= targetY || surfaceY <= 63) {
                // Reached target pool / lower lake / sea level
                BlockPos endPos = new BlockPos(currX, surfaceY, currZ);
                BlockState targetState = world.getBlockState(endPos);
                if (!targetState.is(Blocks.WATER) && !targetState.is(Blocks.ICE) && world.getFluidState(endPos).isEmpty()) {
                    carvePlungePool(world, endPos, mossyCobble, cobble, gravel, water);
                }
                break;
            }

            int drop = lastY - surfaceY;

            if (drop >= 3) {
                // Steep cliff drop - vertical waterfall cascade along the cliff face
                for (int y = lastY; y >= surfaceY; y--) {
                    for (int w = -1; w <= 1; w++) {
                        int ox = (lastDirZ != 0) ? w : 0;
                        int oz = (lastDirX != 0) ? w : 0;
                        BlockPos wPos = new BlockPos(currX + ox, y, currZ + oz);
                        
                        // Clear front
                        if (lastDirX != 0 || lastDirZ != 0) {
                            safeSetBlock(world, wPos.offset(lastDirX, 0, lastDirZ), air);
                        }
                        safeSetBlock(world, wPos, water);

                        // Solid back wall
                        if (lastDirX != 0 || lastDirZ != 0) {
                            BlockPos backPos = wPos.offset(-lastDirX, 0, -lastDirZ);
                            if (!world.getBlockState(backPos).isSolidRender()) {
                                safeSetBlock(world, backPos, (world.getRandom().nextFloat() < 0.4f) ? mossyCobble : stone);
                            }
                        }
                    }
                }
            } else {
                // Gentle to moderate slope - rapids / rushing mountain stream channel
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx * dx + dz * dz <= 2) {
                            BlockPos p = new BlockPos(currX + dx, surfaceY - 1, currZ + dz);
                            safeSetBlock(world, p, water);
                            safeSetBlock(world, p.above(), air);
                            BlockState bed = (world.getRandom().nextFloat() < 0.35f) ? mossyCobble : (world.getRandom().nextFloat() < 0.5f ? cobble : gravel);
                            safeSetBlock(world, p.below(), bed);
                        }
                    }
                }
            }

            lastY = surfaceY;

            // Find steepest downhill neighbor
            int bestX = currX;
            int bestZ = currZ;
            int lowestY = surfaceY;

            for (int dx = -2; dx <= 2; dx += 2) {
                for (int dz = -2; dz <= 2; dz += 2) {
                    if (dx == 0 && dz == 0) continue;
                    int nx = currX + dx;
                    int nz = currZ + dz;
                    int ny = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, nx, nz);
                    if (ny < lowestY) {
                        lowestY = ny;
                        bestX = nx;
                        bestZ = nz;
                    }
                }
            }

            if (lowestY >= surfaceY) {
                // Reached a local depression / pool
                BlockPos endPos = new BlockPos(currX, surfaceY, currZ);
                BlockState targetState = world.getBlockState(endPos);
                if (!targetState.is(Blocks.WATER) && !targetState.is(Blocks.ICE) && world.getFluidState(endPos).isEmpty()) {
                    carvePlungePool(world, endPos, mossyCobble, cobble, gravel, water);
                }
                break;
            }

            lastDirX = Integer.signum(bestX - currX);
            lastDirZ = Integer.signum(bestZ - currZ);
            currX = bestX;
            currZ = bestZ;
        }
    }

    private static void carvePlungePool(WorldGenLevel world, BlockPos poolCenter, BlockState mossy, BlockState cobble, BlockState gravel, BlockState water) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx * dx + dz * dz <= 5) {
                    BlockPos p = poolCenter.offset(dx, -1, dz);
                    safeSetBlock(world, p, water);
                    BlockState bed = (world.getRandom().nextFloat() < 0.4f) ? mossy : (world.getRandom().nextFloat() < 0.5f ? cobble : gravel);
                    safeSetBlock(world, p.below(), bed);
                }
            }
        }
    }

    public static void safeSetBlock(WorldGenLevel world, BlockPos pos, BlockState state) {
        if (world.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
            world.setBlock(pos, state, 2);
        }
    }
}
