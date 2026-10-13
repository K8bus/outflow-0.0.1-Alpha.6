package pl.k8bus.outflow.worldgen.feature;

import com.mojang.serialization.Codec;

import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Feature wrapper for CliffFacePhase.
 * Runs as an independent feature. Order in the biome's feature list determines priority.
 */
public class CliffFaceFeature extends Feature<NoneFeatureConfiguration> {

    public CliffFaceFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel world = context.level();
        ChunkAccess chunk = world.getChunk(context.origin());
        CliffFacePhase.apply(chunk);
        return true;
    }
}
