package pl.k8bus.outflow.mixin;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import pl.k8bus.outflow.registry.CarvingContextAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CarvingContext.class)
public class CarvingContextMixin implements CarvingContextAccessor {
    
    @Unique
    private ChunkGenerator outflow$generator;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(NoiseBasedChunkGenerator generator, RegistryAccess registryAccess, LevelHeightAccessor levelHeightAccessor, NoiseChunk noiseChunk, RandomState randomState, SurfaceRules.RuleSource ruleSource, CallbackInfo ci) {
        this.outflow$generator = generator;
    }

    @Override
    public ChunkGenerator outflow$getGenerator() {
        return this.outflow$generator;
    }
}
