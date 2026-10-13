package pl.k8bus.outflow.registry;

import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

public interface CarvingContextAccessor {
    ChunkGenerator outflow$getGenerator();

    default RandomState outflow$getRandomState() {
        return ((net.minecraft.world.level.levelgen.carver.CarvingContext) (Object) this).randomState();
    }
}