package pl.k8bus.outflow.worldgen.feature.caldera;

import pl.k8bus.outflow.worldgen.feature.lake.LakeCenterData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;

import pl.k8bus.outflow.worldgen.math.SDFUtils;

public class CalderaBuilder {

    public static void buildTarn(LakeCenterData lake, WorldGenLevel world, BlockPos currentOrigin) {
        int currentChunkX = currentOrigin.getX() >> 4;
        int currentChunkZ = currentOrigin.getZ() >> 4;

        Registry<Biome> biomeRegistry = world.registryAccess().lookupOrThrow(Registries.BIOME);
        Holder<Biome> frozenOcean = biomeRegistry.getOrThrow(Biomes.FROZEN_OCEAN);
        Holder<Biome> ocean = biomeRegistry.getOrThrow(Biomes.OCEAN);
        Holder<Biome> lukewarmOcean = biomeRegistry.getOrThrow(Biomes.LUKEWARM_OCEAN);

        int blendDistance = 32; // Zwiększone z 24 na prośbę użytkownika (szersze łagodne zbocza)
        int totalRadius = lake.radius + blendDistance;

        // Expand bounds to account for domain warping deformations.
        int maxPhysicalRadius = totalRadius + 64;

        int cx = (currentChunkX << 4) + 8;
        int cz = (currentChunkZ << 4) + 8;
        if (Math.hypot(cx - lake.startX, cz - lake.startZ) > maxPhysicalRadius + 24) {
            return;
        }

        int lakeBottom = lake.waterLevel - lake.maxDepth;
        BlockPos.MutableBlockPos mutPos = new BlockPos.MutableBlockPos();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int globalX = (currentChunkX << 4) + x;
                int globalZ = (currentChunkZ << 4) + z;

                double dx = globalX - lake.startX;
                double dz = globalZ - lake.startZ;

                // Pseudo-random seed based on lake position
                double lakeSeed = lake.startX * 0.013 + lake.startZ * 0.017;

                // Domain warping for shoreline shape.
                double warpX = Math.sin(globalZ * 0.035 + lakeSeed) * 15.0
                        + Math.cos(globalX * 0.045 - lakeSeed) * 8.0;
                double warpZ = Math.cos(globalX * 0.035 + lakeSeed) * 15.0
                        + Math.sin(globalZ * 0.045 + lakeSeed) * 8.0;

                // Shoreline edge noise for small details.
                double edgeNoise = (Math.sin(globalX * 0.073 + lakeSeed) * 4.0)
                        + (Math.cos(globalZ * 0.091 - lakeSeed) * 4.0)
                        + (Math.sin((globalX - globalZ) * 0.137) * 2.5);

                // Zamiast zwykłej odległości euklidesowej używamy Signed Distance Field (SDF) + zniekształceń
                // SDF zwraca <= 0 gdy jesteśmy wewnątrz jeziora. Tutaj my potrzebujemy samej odległości, więc:
                // Ale ponieważ mamy różny "dynamicRadius" w zależności od kąta, policzymy najpierw odległość.
                double dist = SDFUtils.sdfCircle(dx + warpX, dz + warpZ, 0) + edgeNoise;
                
                if (dist > totalRadius)
                    continue;

                // Fast O(1) heightmap read.
                int rawTerrainY = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, globalX, globalZ);
                int actualSurfaceY = world.getHeight(Heightmap.Types.WORLD_SURFACE, globalX, globalZ);

                // Reverted back to reliable block scanning! Heightmaps can be unreliable mid-generation.
                int originalSolidY = actualSurfaceY;
                BlockState topSolidState = Blocks.GRASS_BLOCK.defaultBlockState();
                BlockState underSolidState = Blocks.DIRT.defaultBlockState();
                BlockState deepSolidState = Blocks.STONE.defaultBlockState();
                boolean hadSnow = false;

                while (originalSolidY > 0) {
                    mutPos.set(globalX, originalSolidY, globalZ);
                    BlockState state = world.getBlockState(mutPos);
                    if (state.is(Blocks.SNOW)) {
                        hadSnow = true;
                    }
                    if (state.isSolidRender() && !state.is(Blocks.ICE)) {
                        if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(BlockTags.PLANKS)) {
                            originalSolidY--;
                            continue;
                        }
                        topSolidState = state;
                        underSolidState = world.getBlockState(mutPos.below());
                        deepSolidState = world.getBlockState(mutPos.below(2));
                        break;
                    }
                    originalSolidY--;
                }

                // Fallback for chunk borders if WORLD_SURFACE_WG is not generated.
                if (rawTerrainY < -50) {
                    rawTerrainY = originalSolidY;
                }

                // Smoothly interpolate blend distance based on terrain height relative to water level
                double heightDiff = rawTerrainY - lake.waterLevel;
                double blendFactor = SDFUtils.smoothstep(-5.0, 5.0, heightDiff);
                double localBlendDistance = 10.0 + 18.0 * blendFactor; // 10 blocks for steep dam, 28 for gentle beach
                boolean isDam = rawTerrainY < lake.waterLevel;

                double angle = (Math.toDegrees(Math.atan2(dz + warpZ, dx + warpX)) + 360) % 360;
                double idx = (angle / 360.0) * 32.0;
                int idx1 = (int) Math.floor(idx) % 32;
                int idx2 = (idx1 + 1) % 32;
                double blend = idx - Math.floor(idx);
                double dynamicRadius = lake.radiusProfile[idx1] * (1.0 - blend) + lake.radiusProfile[idx2] * blend;
                mutPos.set(globalX, lake.waterLevel, globalZ);
                boolean isBadlands = world.getBiome(mutPos)
                        .is(BiomeTags.IS_BADLANDS);

                if (dist <= dynamicRadius) {
                    double progress = dist / dynamicRadius;
                    // Zamieniono wolne Math.cos() na szybki, wygładzony wielomian
                    double curve = SDFUtils.smoothstep(0.0, 1.0, progress);
                    int theoreticalBowlY = lakeBottom + (int) (lake.maxDepth * curve);

                    if (dynamicRadius - dist < 6.0) {
                        double shoreBlend = (dynamicRadius - dist) / 6.0;
                        double smoothBlend = SDFUtils.smoothstep(0.0, 1.0, shoreBlend);
                        theoreticalBowlY = (int) Math.round(lake.waterLevel * (1.0 - smoothBlend) + theoreticalBowlY * smoothBlend);
                    }

                    int targetSurfaceY = theoreticalBowlY;

                    int clearFrom = Math.max(rawTerrainY, actualSurfaceY) + 15;

                    for (int y = clearFrom; y > lake.waterLevel; y--) {
                        mutPos.set(globalX, y, globalZ);
                        BlockState state = world.getBlockState(mutPos);
                        if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES))
                            continue;
                        if (state.is(Blocks.SNOW)) {
                            BlockState below = world.getBlockState(mutPos.below());
                            if (below.is(BlockTags.LOGS) || below.is(BlockTags.LEAVES))
                                continue;
                        }
                        world.setBlock(mutPos.immutable(), Blocks.AIR.defaultBlockState(), 2);
                    }

                    int columnDepth = lake.waterLevel - targetSurfaceY;
                    boolean isDeep = columnDepth > 12;

                    mutPos.set(globalX, lake.waterLevel, globalZ);
                    Holder<Biome> originalBiome = world.getBiome(mutPos);
                    float temp = originalBiome.value().getBaseTemperature();

                    Holder<Biome> targetBiome;
                    if (temp < 0.2f) {
                        targetBiome = frozenOcean;
                    } else if (temp >= 0.85f) {
                        targetBiome = lukewarmOcean;
                    } else {
                        targetBiome = ocean;
                    }

                    for (int y = targetSurfaceY; y <= lake.waterLevel; y++) {
                        mutPos.set(globalX, y, globalZ);
                        if (y == lake.waterLevel && temp < 0.15f) {
                            if (progress > 0.85) {
                                world.setBlock(mutPos.immutable(), Blocks.ICE.defaultBlockState(), 2);
                            } else if (progress > 0.5) {
                                double nx = globalX * 0.13;
                                double nz = globalZ * 0.17;
                                double iceNoise = Math.sin(nx) + Math.cos(nz) + Math.sin(nx * 0.4 + nz * 0.3);

                                double localProg = (progress - 0.5) / 0.35;
                                double threshold = 1.5 - localProg * 3.0;

                                if (iceNoise > threshold) {
                                    world.setBlock(mutPos.immutable(), Blocks.ICE.defaultBlockState(), 2);
                                } else {
                                    world.setBlock(mutPos.immutable(), Blocks.WATER.defaultBlockState(), 2);
                                }
                            } else {
                                world.setBlock(mutPos.immutable(), Blocks.WATER.defaultBlockState(), 2);
                            }
                        } else {
                            world.setBlock(mutPos.immutable(), Blocks.WATER.defaultBlockState(), 2);
                        }

                        // Łatanie wycieków wodnych po bokach i malowanie ścian
                        for (int dir = 0; dir < 4; dir++) {
                            int dxPos = dir == 0 ? 1 : dir == 1 ? -1 : 0;
                            int dzPos = dir == 2 ? 1 : dir == 3 ? -1 : 0;
                            int nx = globalX + dxPos;
                            int nz = globalZ + dzPos;
                            
                            if ((nx >> 4) != currentChunkX || (nz >> 4) != currentChunkZ) {
                                continue;
                            }
                            mutPos.set(nx, y, nz);
                            BlockState ns = world.getBlockState(mutPos);
                            if (!ns.isSolidRender() && ns.getFluidState().isEmpty() && !ns.is(Blocks.ICE)) {
                                BlockState patch = world.isEmptyBlock(mutPos.above()) ? topSolidState : underSolidState;
                                world.setBlock(mutPos.immutable(), patch, 2);
                            }
                        }

                        // Ecosystem injection - replace water biome
                        mutPos.set(globalX, y, globalZ);
                        net.minecraft.world.level.chunk.ChunkAccess chunk = world.getChunk(mutPos);
                        int sectionIndex = chunk.getSectionIndex(y);
                        if (sectionIndex >= 0 && sectionIndex < chunk.getSections().length) {
                            net.minecraft.world.level.chunk.LevelChunkSection section = chunk
                                    .getSection(sectionIndex);
                            if (section
                                    .getBiomes() instanceof net.minecraft.world.level.chunk.PalettedContainer<Holder<Biome>> biomes) {
                                biomes.set((globalX & 15) >> 2, (y & 15) >> 2, (globalZ & 15) >> 2, targetBiome);
                            }
                        }
                    }

                    mutPos.set(globalX, targetSurfaceY - 1, globalZ);
                    int depth = lake.waterLevel - targetSurfaceY;
                    BlockState bedState = getLakePaintState(depth, globalX, globalZ, targetSurfaceY);
                    if (!isBadlands || !world.getBlockState(mutPos).isSolidRender()) {
                        world.setBlock(mutPos.immutable(), bedState, 2);
                    }

                    // Ecological succession
                    if (temp >= 0.2f) { // No underwater flora in coldest biomes
                        if (temp >= 0.4f && columnDepth <= 2 && world.getRandom().nextFloat() < 0.05f) {
                            mutPos.set(globalX, lake.waterLevel + 1, globalZ);
                            world.setBlock(mutPos.immutable(),
                                    Blocks.LILY_PAD.defaultBlockState(), 2);
                        } else if (columnDepth >= 2 && columnDepth <= 6 && world.getRandom().nextFloat() < 0.25f) {
                            mutPos.set(globalX, targetSurfaceY, globalZ);
                            world.setBlock(mutPos.immutable(),
                                    Blocks.SEAGRASS.defaultBlockState(), 2);
                        }
                    }

                    // Seal caves under the lake bed - keep drilling down until we've hit solid
                    // ground for a few blocks in a row, instead of a hard 4-block cap that
                    // leaves deeper caves/ravines exposed as a black gap.
                    int consecutiveSolid = 0;
                    int maxSealDepth = 20;
                    for (int dy = 1; dy <= maxSealDepth; dy++) {
                        mutPos.set(globalX, targetSurfaceY - dy, globalZ);
                        BlockState s = world.getBlockState(mutPos);
                        // Do not patch if it's flooded with water! Water is a valid bottom for a connected aquifer.
                        boolean isWeak = !s.isSolidRender() && s.getFluidState().isEmpty()
                                || s.is(BlockTags.LOGS)
                                || s.is(BlockTags.LEAVES);

                        if (isWeak) {
                            if (!isBadlands) {
                                BlockState patch = world.getRandom().nextFloat() < 0.3f ? Blocks.STONE.defaultBlockState() : Blocks.DIRT.defaultBlockState();
                                world.setBlock(mutPos.immutable(), patch, 2);
                            }
                            consecutiveSolid = 0;
                        } else {
                            consecutiveSolid++;
                            if (consecutiveSolid >= 3) {
                                break;
                            }
                        }
                    }
                } else {
                    if (dist > dynamicRadius + localBlendDistance)
                        continue;

                    double rimWidth = 2.0 * (1.0 - blendFactor);
                    double excess = dist - dynamicRadius;
                    
                    int targetSurfaceY;
                    if (excess <= rimWidth) {
                        targetSurfaceY = lake.waterLevel;
                    } else {
                        double falloffBlend = (excess - rimWidth) / (localBlendDistance - rimWidth);
                        double falloffFraction = SDFUtils.smoothstep(0.0, 1.0, falloffBlend);
                        targetSurfaceY = (int) Math.round(lake.waterLevel * (1.0 - falloffFraction) + rawTerrainY * falloffFraction);
                    }

                    int clearFrom = Math.max(rawTerrainY, actualSurfaceY) + 15;

                    for (int y = clearFrom; y > targetSurfaceY; y--) {
                        mutPos.set(globalX, y, globalZ);
                        BlockState state = world.getBlockState(mutPos);
                        if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES))
                            continue;
                        if (state.is(Blocks.SNOW)) {
                            BlockState below = world.getBlockState(mutPos.below());
                            if (below.is(BlockTags.LOGS) || below.is(BlockTags.LEAVES))
                                continue;
                        }
                        world.setBlock(mutPos.immutable(), Blocks.AIR.defaultBlockState(), 2);
                    }

                    boolean shouldPaint = (targetSurfaceY != originalSolidY);

                    mutPos.set(globalX, actualSurfaceY, globalZ);
                    BlockState actualSurfaceState = world.getBlockState(mutPos);
                    if (actualSurfaceState.is(Blocks.WATER) || actualSurfaceState.is(Blocks.ICE)) {
                        shouldPaint = false;
                    }

                    BlockState paint0 = Blocks.TUFF.defaultBlockState();
                    BlockState paint1 = Blocks.BASALT.defaultBlockState();
                    BlockState paint2 = Blocks.BASALT.defaultBlockState();
                    BlockState paintDeep = Blocks.DEEPSLATE.defaultBlockState();

                    if (shouldPaint) {
                        if (excess / localBlendDistance > 0.8 && actualSurfaceY > targetSurfaceY + 2) {
                            paint0 = topSolidState;
                            paint1 = underSolidState;
                            paint2 = deepSolidState;
                        }
                        
                        mutPos.set(globalX, targetSurfaceY, globalZ);
                        if (world.getFluidState(mutPos).isEmpty())
                            world.setBlock(mutPos.immutable(), paint0, 2);
                        mutPos.set(globalX, targetSurfaceY - 1, globalZ);
                        if (world.getFluidState(mutPos).isEmpty())
                            world.setBlock(mutPos.immutable(), paint1, 2);
                        mutPos.set(globalX, targetSurfaceY - 2, globalZ);
                        if (world.getFluidState(mutPos).isEmpty())
                            world.setBlock(mutPos.immutable(), paint2, 2);
                    }

                    boolean isCliff = (rawTerrainY >= lake.waterLevel) && (targetSurfaceY - lake.waterLevel > 3);
                    int shellDepth = Math.max(8, targetSurfaceY - originalSolidY + 5);

                    for (int dy = 3; dy <= shellDepth; dy++) {
                        int sealY = targetSurfaceY - dy;
                        if (sealY < originalSolidY - 5)
                            continue;
                        
                        mutPos.set(globalX, sealY, globalZ);
                        BlockState existing = world.getBlockState(mutPos);
                        if (!existing.isSolidRender() && existing.getFluidState().isEmpty()) {
                            world.setBlock(mutPos.immutable(), paintDeep, 2);
                        }
                    }

                    if (hadSnow && targetSurfaceY >= lake.waterLevel) {
                        mutPos.set(globalX, targetSurfaceY + 1, globalZ);
                        world.setBlock(mutPos.immutable(),
                                Blocks.SNOW.defaultBlockState(), 2);
                    }
                }

                // Post-processing: skanujemy od góry, ignorując liście, drewno i warstwy śniegu
                BlockPos topPos = null;
                for (int y = Math.max(rawTerrainY, actualSurfaceY) + 15; y >= lake.waterLevel; y--) {
                    mutPos.set(globalX, y, globalZ);
                    BlockState state = world.getBlockState(mutPos);
                    if (!state.isAir() && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS) && !state.is(Blocks.SNOW)) {
                        if (!state.is(Blocks.WATER) && !state.is(Blocks.ICE)) {
                            topPos = mutPos.immutable();
                        }
                        break;
                    }
                }
                if (topPos != null) {
                    BlockState state = world.getBlockState(topPos);
                    if (state.is(Blocks.DIRT)) {
                        float localTemp = world.getBiome(topPos).value().getBaseTemperature();
                        BlockState newState = (localTemp < 0.2f) ? Blocks.SNOW_BLOCK.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
                        world.setBlock(topPos, newState, 2);
                    }
                }
            }
        }
    }

    private static BlockState getLakePaintState(int depth, int x, int z, int targetSurfaceY) {
        double noise = Math.sin(x * 0.137 + z * 0.083)
                + Math.cos(x * 0.091 - z * 0.173)
                + Math.sin(x * 0.231 + z * 0.211)
                + Math.cos(x * 0.043 - z * 0.057);
        float roll = (float) ((noise + 3.0) / 6.0);
        if (roll < 0f) roll = 0f;
        if (roll > 1f) roll = 1f;

        if (depth <= 2) {
            return (roll < 0.6f) ? Blocks.BASALT.defaultBlockState() : Blocks.TUFF.defaultBlockState();
        } else if (depth <= 6) {
            if (roll < 0.4f) return Blocks.TUFF.defaultBlockState();
            else if (roll < 0.8f) return Blocks.BASALT.defaultBlockState();
            else return Blocks.SMOOTH_BASALT.defaultBlockState();
        } else if (depth <= 12) {
            return (roll < 0.3f) ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.BASALT.defaultBlockState();
        } else {
            return Blocks.OBSIDIAN.defaultBlockState();
        }
    }
}
