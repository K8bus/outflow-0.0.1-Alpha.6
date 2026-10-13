package pl.k8bus.outflow.mixin;

import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(targets = "net.minecraft.world.level.levelgen.SurfaceRules$Context$SteepMaterialCondition")
public class SteepSlopeMixin {

    private static final int STEEP_THRESHOLD = 4;

    @Overwrite
    protected boolean compute() {
        SurfaceRules.Context context = ((LazyXZConditionAccessor) (Object) this).outflow$getContext();

        int x = context.blockX & 15;
        int z = context.blockZ & 15;
        ChunkAccess chunk = context.chunk;

        int north = Math.max(z - 1, 0);
        int south = Math.min(z + 1, 15);
        int alongZ = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, north)
                - chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, south);
        if (Math.abs(alongZ) >= STEEP_THRESHOLD) {
            return true;
        }

        int west = Math.max(x - 1, 0);
        int east = Math.min(x + 1, 15);
        int alongX = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, west, z)
                - chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, east, z);
        return Math.abs(alongX) >= STEEP_THRESHOLD;
    }
}
