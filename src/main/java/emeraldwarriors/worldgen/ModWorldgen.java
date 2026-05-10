package emeraldwarriors.worldgen;

import emeraldwarriors.Emerald_Warriors;
import emeraldwarriors.config.ModConfig;
import emeraldwarriors.entity.ModEntities;
import emeraldwarriors.mixin.SpawnPlacementsInvoker;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags;
import net.minecraft.core.Registry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

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

    private static final Predicate<BiomeSelectionContext> MERCENARY_CAMP_BIOMES = ctx ->
            BiomeSelectors.foundInOverworld().test(ctx)
                    && !ctx.hasTag(ConventionalBiomeTags.IS_OCEAN)
                    && !ctx.hasTag(ConventionalBiomeTags.IS_DEEP_OCEAN)
                    && !ctx.hasTag(ConventionalBiomeTags.IS_SHALLOW_OCEAN)
                    && !ctx.hasTag(ConventionalBiomeTags.IS_RIVER)
                    && !ctx.hasTag(ConventionalBiomeTags.IS_BEACH)
                    && !ctx.hasTag(ConventionalBiomeTags.IS_MUSHROOM)
                    && !ctx.hasTag(ConventionalBiomeTags.IS_UNDERGROUND)
                    && !ctx.hasTag(ConventionalBiomeTags.IS_CAVE);

    private static final Predicate<BiomeSelectionContext> MERCENARY_SPAWN_BIOMES = ctx ->
            MERCENARY_CAMP_BIOMES.test(ctx)
                    && (ctx.hasTag(ConventionalBiomeTags.IS_PLAINS)
                    || ctx.hasTag(ConventionalBiomeTags.IS_FOREST)
                    || ctx.hasTag(ConventionalBiomeTags.IS_TAIGA)
                    || ctx.hasTag(ConventionalBiomeTags.IS_HILL)
                    || ctx.hasTag(ConventionalBiomeTags.IS_MOUNTAIN)
                    || ctx.hasTag(ConventionalBiomeTags.IS_SAVANNA)
                    || ctx.hasTag(ConventionalBiomeTags.IS_JUNGLE));

    public static void register() {
        var cfg = ModConfig.get();
        Registry.register(BuiltInRegistries.FEATURE, MERCENARY_CAMP_FEATURE_KEY, MERCENARY_CAMP_FEATURE);

        if (cfg.toggles.camps) {
            BiomeModifications.addFeature(
                    MERCENARY_CAMP_BIOMES,
                    GenerationStep.Decoration.SURFACE_STRUCTURES,
                    MERCENARY_CAMP_PLACED_KEY
            );
        }

        boolean anySpawnSystemEnabled = cfg.toggles.camps || cfg.toggles.solitarySpawns || cfg.toggles.villageSpawns;
        if (anySpawnSystemEnabled) {
            SpawnPlacementsInvoker.ew$register(
                    ModEntities.EMERALD_MERCENARY,
                    SpawnPlacementTypes.ON_GROUND,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    (type, level, reason, pos, random) -> {
                        if (reason == EntitySpawnReason.SPAWNER) {
                            return true;
                        }
                        if (!level.getFluidState(pos).isEmpty()) {
                            return false;
                        }
                        if (!level.getBlockState(pos).isAir()) {
                            return false;
                        }
                        BlockPos below = pos.below();
                        if (reason == EntitySpawnReason.EVENT || reason == EntitySpawnReason.STRUCTURE) {
                            return !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
                        }
                        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                            return false;
                        }
                        return level.getMaxLocalRawBrightness(pos) > 7;
                    }
            );
        }

        if (cfg.toggles.solitarySpawns) {
            int weight = Math.max(0, cfg.solitarySpawn.weight);
            int minGroup = Math.max(1, cfg.solitarySpawn.minGroup);
            int maxGroup = Math.max(minGroup, cfg.solitarySpawn.maxGroup);
            if (weight > 0 && maxGroup > 0) {
                Set<Identifier> whitelist = parseBiomeIdSet(cfg.solitarySpawn.biomeWhitelist);
                Set<Identifier> blacklist = parseBiomeIdSet(cfg.solitarySpawn.biomeBlacklist);

                Predicate<BiomeSelectionContext> spawnBiomes = ctx -> {
                    Identifier biomeId = ctx.getBiomeKey().identifier();
                    if (!whitelist.isEmpty() && !whitelist.contains(biomeId)) {
                        return false;
                    }
                    if (!blacklist.isEmpty() && blacklist.contains(biomeId)) {
                        return false;
                    }

                    if (!whitelist.isEmpty()) {
                        return MERCENARY_CAMP_BIOMES.test(ctx);
                    }

                    return MERCENARY_SPAWN_BIOMES.test(ctx);
                };

                BiomeModifications.addSpawn(
                        spawnBiomes,
                        MobCategory.CREATURE,
                        ModEntities.EMERALD_MERCENARY,
                        weight,
                        minGroup,
                        maxGroup
                );
            }
        }
    }

    private static Set<Identifier> parseBiomeIdSet(Iterable<String> ids) {
        Set<Identifier> out = new HashSet<>();
        if (ids == null) {
            return out;
        }
        for (String s : ids) {
            if (s == null || s.isEmpty()) {
                continue;
            }
            Identifier id = Identifier.tryParse(s);
            if (id != null) {
                out.add(id);
            }
        }
        return out;
    }
}
