package pl.k8bus.outflow.registry;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import pl.k8bus.outflow.Outflow;
import pl.k8bus.outflow.worldgen.feature.AlpineTarnFeature;
import pl.k8bus.outflow.worldgen.feature.CliffFaceFeature;
import pl.k8bus.outflow.worldgen.feature.IceFloeFeature;

public class OutflowWorldGen {

    public static final Feature<NoneFeatureConfiguration> ALPINE_TARN_FEATURE = new AlpineTarnFeature(NoneFeatureConfiguration.CODEC);
    public static final ResourceKey<ConfiguredFeature<?, ?>> ALPINE_TARN_CONFIGURED = ResourceKey.create(Registries.CONFIGURED_FEATURE, id("alpine_tarn"));
    public static final ResourceKey<PlacedFeature> ALPINE_TARN_PLACED = ResourceKey.create(Registries.PLACED_FEATURE, id("alpine_tarn"));

    public static final Feature<NoneFeatureConfiguration> CLIFF_FACE_FEATURE = new CliffFaceFeature(NoneFeatureConfiguration.CODEC);
    public static final ResourceKey<ConfiguredFeature<?, ?>> CLIFF_FACE_CONFIGURED = ResourceKey.create(Registries.CONFIGURED_FEATURE, id("cliff_face"));
    public static final ResourceKey<PlacedFeature> CLIFF_FACE_PLACED = ResourceKey.create(Registries.PLACED_FEATURE, id("cliff_face"));

    public static final Feature<NoneFeatureConfiguration> ICE_FLOE_FEATURE = new IceFloeFeature(NoneFeatureConfiguration.CODEC);
    public static final ResourceKey<ConfiguredFeature<?, ?>> ICE_FLOE_CONFIGURED = ResourceKey.create(Registries.CONFIGURED_FEATURE, id("ice_floe"));
    public static final ResourceKey<PlacedFeature> ICE_FLOE_PLACED = ResourceKey.create(Registries.PLACED_FEATURE, id("ice_floe"));

    public static void registerAll() {
        Outflow.LOGGER.info("[Outflow] Rejestrowanie elementow WorldGen...");
        registerFeatures();
        registerBiomeModifications();
    }

    private static void registerFeatures() {
        Registry.register(BuiltInRegistries.FEATURE, id("alpine_tarn"), ALPINE_TARN_FEATURE);
        Registry.register(BuiltInRegistries.FEATURE, id("cliff_face"), CLIFF_FACE_FEATURE);
        Registry.register(BuiltInRegistries.FEATURE, id("ice_floe"), ICE_FLOE_FEATURE);
    }

    private static void registerBiomeModifications() {
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.LAKES, ALPINE_TARN_PLACED);
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.LOCAL_MODIFICATIONS, CLIFF_FACE_PLACED);
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.LOCAL_MODIFICATIONS, ICE_FLOE_PLACED);

        BiomeModifications.create(id("remove_springs")).add(
            ModificationPhase.REPLACEMENTS,
            BiomeSelectors.tag(BiomeTags.IS_MOUNTAIN),
            context -> {
                ResourceKey<PlacedFeature> springWaterKey = ResourceKey.create(Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath("minecraft", "spring_water"));
                context.getGenerationSettings().removeFeature(GenerationStep.Decoration.FLUID_SPRINGS, springWaterKey);
            }
        );
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("outflow", path);
    }
}
