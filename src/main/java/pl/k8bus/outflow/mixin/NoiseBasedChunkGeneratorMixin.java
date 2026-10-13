package pl.k8bus.outflow.mixin;

import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.k8bus.outflow.cubic.world.SurfaceSection;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorMixin extends ChunkGenerator {

    public NoiseBasedChunkGeneratorMixin(net.minecraft.world.level.biome.BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Inject(method = "buildSurface", at = @At("RETURN"))
    private void outflow$markSurfaceSections(WorldGenRegion region, StructureManager structureManager, RandomState randomState, ChunkAccess chunk, CallbackInfo ci) {
        LevelChunkSection[] sections = chunk.getSections();
        
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int height = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                
                int sectionIndex = chunk.getSectionIndex(height);
                if (sectionIndex >= 0 && sectionIndex < sections.length) {
                    LevelChunkSection section = sections[sectionIndex];
                    if (section != null) {
                        ((SurfaceSection) section).outflow$setSurface(true);
                    }
                }
                // Oznaczamy rowniez sekcje ponizej, aby glebokie niecki (np. 20 blokow) nie byly obcinane
                if (sectionIndex - 1 >= 0 && sectionIndex - 1 < sections.length) {
                    LevelChunkSection subSection = sections[sectionIndex - 1];
                    if (subSection != null) {
                        ((SurfaceSection) subSection).outflow$setSurface(true);
                    }
                }
            }
        }
    }
}
