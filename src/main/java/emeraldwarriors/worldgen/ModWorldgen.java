package emeraldwarriors.worldgen;

import emeraldwarriors.Emerald_Warriors;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public class ModWorldgen {

    public static final ResourceKey<Feature<?>> MERCENARY_CAMP_FEATURE_KEY = ResourceKey.create(
            Registries.FEATURE,
            Identifier.fromNamespaceAndPath(Emerald_Warriors.MOD_ID, "mercenary_camp")
    );

    public static final ResourceKey<PlacedFeature> MERCENARY_CAMP_PLACED_KEY = ResourceKey.create(
            Registries.PLACED_FEATURE,
            Identifier.fromNamespaceAndPath(Emerald_Warriors.MOD_ID, "mercenary_camp")
    );

    public static final MercenaryCampFeature MERCENARY_CAMP_FEATURE = new MercenaryCampFeature(NoneFeatureConfiguration.CODEC);

    public static void register() {
        Registry.register(BuiltInRegistries.FEATURE, MERCENARY_CAMP_FEATURE_KEY, MERCENARY_CAMP_FEATURE);

        BiomeModifications.addFeature(
                BiomeSelectors.foundInOverworld(),
                GenerationStep.Decoration.SURFACE_STRUCTURES,
                MERCENARY_CAMP_PLACED_KEY
        );
    }
}
