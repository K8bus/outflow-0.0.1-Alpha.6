package pl.k8bus.outflow.worldgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class GlacierLakeFeature extends Feature<NoneFeatureConfiguration> {

    public GlacierLakeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel world = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();

        int surfaceY = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, origin.getX(), origin.getZ());
        BlockPos center = new BlockPos(origin.getX(), surfaceY, origin.getZ());

        // Skala w stylu laguny (bardziej podłużna)
        boolean lengthAxisX = random.nextBoolean();
        int radiusA = 20 + random.nextInt(15);
        int radiusB = 10 + random.nextInt(10);
        int radiusX = lengthAxisX ? radiusA : radiusB;
        int radiusZ = lengthAxisX ? radiusB : radiusA;

        // Szukamy brzegu z największym spadem, skąd może schodzić lodowiec
        int y1 = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, origin.getX() + radiusX, origin.getZ());
        int y2 = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, origin.getX() - radiusX, origin.getZ());
        int y3 = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, origin.getX(), origin.getZ() + radiusZ);
        int y4 = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, origin.getX(), origin.getZ() - radiusZ);

        int minH = Math.min(Math.min(y1, y2), Math.min(y3, y4));
        int maxH = Math.max(Math.max(y1, y2), Math.max(y3, y4));

        // Zbyt duży spadek = odrzucamy, musi być "dolina" albo "wcięcie" do laguny
        if (maxH - minH > 18 || Math.abs(surfaceY - minH) > 10) {
            return false;
        }

        // Kierunek lodowca (strona o najwyższej wysokości)
        double glacierDirX = 0;
        double glacierDirZ = 0;
        if (maxH == y1) glacierDirX = 1;
        else if (maxH == y2) glacierDirX = -1;
        else if (maxH == y3) glacierDirZ = 1;
        else glacierDirZ = -1;

        int depth = 8 + random.nextInt(5);
        boolean placedLake = false;

        // 1. Kopiemy misę jeziora/laguny
        for (int x = -radiusX; x <= radiusX; x++) {
            for (int z = -radiusZ; z <= radiusZ; z++) {
                double distanceSq = (double)(x * x) / (radiusX * radiusX) + (double)(z * z) / (radiusZ * radiusZ);
                if (distanceSq <= 1.0) {
                    int localDepth = (int) (depth * (1.0 - Math.sqrt(distanceSq)));
                    if (localDepth < 1) localDepth = 1;

                    for (int y = -localDepth; y <= 2; y++) {
                        BlockPos pos = center.offset(x, y - 1, z);
                        BlockState currentState = world.getBlockState(pos);

                        if (y <= 0) {
                            if (currentState.isSolid() || currentState.isAir()) {
                                world.setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
                                placedLake = true;
                            }
                        } else if (y > 0) {
                            if (currentState.isSolid()) {
                                world.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                            }
                        }
                    }
                }
            }
        }

        if (!placedLake) return false;

        // 2. Generowanie języka lodowca i kry lodowej
        int glacierReach = (int)(Math.max(radiusX, radiusZ) * 0.7);

        for (int x = -radiusX; x <= radiusX; x++) {
            for (int z = -radiusZ; z <= radiusZ; z++) {
                double distanceSq = (double)(x * x) / (radiusX * radiusX) + (double)(z * z) / (radiusZ * radiusZ);
                if (distanceSq <= 1.0) {
                    
                    // a) Sprawdzamy, czy ten punkt należy do "języka" schodzącego lodowca
                    // Obliczamy rzut punktu na kierunek lodowca
                    double proj = (x * glacierDirX) / radiusX + (z * glacierDirZ) / radiusZ;
                    
                    // Jeśli punkt leży po stronie lodowca (proj > 0.2)
                    if (proj > 0.1) {
                        double glacierThickness = proj * 6.0 + random.nextDouble() * 2.0;
                        int iceTopY = (int) glacierThickness;
                        
                        // Zasięg języka zanurzającego się w wodzie
                        for (int y = -2; y <= iceTopY; y++) {
                            BlockPos pos = center.offset(x, y - 1, z);
                            if (y == iceTopY) {
                                world.setBlock(pos, Blocks.SNOW_BLOCK.defaultBlockState(), 2);
                            } else if (y < 0) {
                                world.setBlock(pos, Blocks.BLUE_ICE.defaultBlockState(), 2);
                            } else {
                                world.setBlock(pos, Blocks.PACKED_ICE.defaultBlockState(), 2);
                            }
                        }
                    } 
                    // b) Reszta jeziora to otwarta woda z unoszącą się krą (Ice Floes)
                    else {
                        // Szum / losowość do generowania połamanej kry
                        if (random.nextFloat() < 0.15f) {
                            // Grupa kilku bloków lodu
                            int size = 1 + random.nextInt(3);
                            for(int ix = -size; ix <= size; ix++) {
                                for(int iz = -size; iz <= size; iz++) {
                                    if (ix*ix + iz*iz <= size*size && random.nextBoolean()) {
                                        BlockPos pos = center.offset(x + ix, -1, z + iz);
                                        // Sprawdzamy czy w tym miejscu jest woda, by jej nie nadpisywać na brzegach
                                        if (world.getBlockState(pos).is(Blocks.WATER)) {
                                            if (random.nextFloat() < 0.2f) {
                                                world.setBlock(pos, Blocks.SNOW_BLOCK.defaultBlockState(), 2);
                                            } else {
                                                world.setBlock(pos, Blocks.ICE.defaultBlockState(), 2);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return true;
    }
}
