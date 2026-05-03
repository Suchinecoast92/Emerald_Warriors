package emeraldwarriors.worldgen;

import com.mojang.serialization.Codec;
import emeraldwarriors.Emerald_Warriors;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MercenaryCampFeature extends Feature<NoneFeatureConfiguration> {

    public MercenaryCampFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource ctxRandom = context.random();

        BlockPos origin = context.origin();
        BlockPos center = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, origin);

        RandomSource random = new LegacyRandomSource(ctxRandom.nextLong() ^ center.asLong());

        if (center.getY() <= level.getSeaLevel() + 1) {
            return false;
        }

        if (!level.getFluidState(center).isEmpty()) {
            return false;
        }

        BlockState ground = level.getBlockState(center.below());
        if (!ground.isFaceSturdy(level, center.below(), Direction.UP)) {
            return false;
        }

        int minY = center.getY();
        int maxY = center.getY();
        for (int dx = -5; dx <= 5; dx += 5) {
            for (int dz = -5; dz <= 5; dz += 5) {
                BlockPos p = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, origin.offset(dx, 0, dz));
                minY = Math.min(minY, p.getY());
                maxY = Math.max(maxY, p.getY());
                if (maxY - minY > 1) {
                    return false;
                }
            }
        }

        Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(random);

        if (!clearArea(level, center)) {
            return false;
        }

        placeBase(level, center, random);
        placeCampObjects(level, center, facing, random);

        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            Identifier biomeId = level.getBiome(center).unwrapKey()
                    .map(ResourceKey::identifier)
                    .orElse(Identifier.fromNamespaceAndPath("minecraft", "unknown"));
            WoodPalette palette = getPaletteForBiome(level, center);
            Emerald_Warriors.LOGGER.info(
                    "Generated mercenary camp at {},{},{} biome={} slab={} fence={}",
                    center.getX(),
                    center.getY(),
                    center.getZ(),
                    biomeId,
                    palette.slab(),
                    palette.fence()
            );
        }

        return true;
    }

    private record WoodPalette(Block slab, Block fence) {
    }

    private static WoodPalette getPaletteForBiome(WorldGenLevel level, BlockPos pos) {
        var biome = level.getBiome(pos);
        String path = level.getBiome(pos).unwrapKey()
                .map(ResourceKey::identifier)
                .map(Identifier::getPath)
                .orElse("");

        if (path.contains("mangrove")) {
            return new WoodPalette(Blocks.MANGROVE_SLAB, Blocks.MANGROVE_FENCE);
        }
        if (path.contains("cherry")) {
            return new WoodPalette(Blocks.CHERRY_SLAB, Blocks.CHERRY_FENCE);
        }
        if (path.contains("dark_forest")) {
            return new WoodPalette(Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_FENCE);
        }
        if (path.contains("bamboo")) {
            return new WoodPalette(Blocks.BAMBOO_SLAB, Blocks.BAMBOO_FENCE);
        }

        if (biome.is(ConventionalBiomeTags.IS_JUNGLE) || biome.is(ConventionalBiomeTags.IS_JUNGLE_TREE) || path.contains("jungle")) {
            return new WoodPalette(Blocks.JUNGLE_SLAB, Blocks.JUNGLE_FENCE);
        }

        if (biome.is(ConventionalBiomeTags.IS_SAVANNA)
                || biome.is(ConventionalBiomeTags.IS_SAVANNA_TREE)
                || biome.is(ConventionalBiomeTags.IS_BADLANDS)
                || path.contains("savanna")
                || path.contains("badlands")) {
            return new WoodPalette(Blocks.ACACIA_SLAB, Blocks.ACACIA_FENCE);
        }

        if (biome.is(ConventionalBiomeTags.IS_BIRCH_FOREST) || path.contains("birch")) {
            return new WoodPalette(Blocks.BIRCH_SLAB, Blocks.BIRCH_FENCE);
        }

        if (biome.is(ConventionalBiomeTags.IS_TAIGA)
                || biome.is(ConventionalBiomeTags.IS_CONIFEROUS_TREE)
                || biome.is(ConventionalBiomeTags.IS_SNOWY)
                || biome.is(ConventionalBiomeTags.IS_ICY)
                || path.contains("taiga")
                || path.contains("grove")
                || path.contains("snow")
                || path.contains("ice")) {
            return new WoodPalette(Blocks.SPRUCE_SLAB, Blocks.SPRUCE_FENCE);
        }

        return new WoodPalette(Blocks.OAK_SLAB, Blocks.OAK_FENCE);
    }

    private static BlockState getLeavesForBiome(WorldGenLevel level, BlockPos pos) {
        var biome = level.getBiome(pos);
        String path = biome.unwrapKey()
                .map(ResourceKey::identifier)
                .map(Identifier::getPath)
                .orElse("");

        Block leavesBlock = Blocks.OAK_LEAVES;
        if (path.contains("mangrove")) {
            leavesBlock = Blocks.MANGROVE_LEAVES;
        } else if (path.contains("cherry")) {
            leavesBlock = Blocks.CHERRY_LEAVES;
        } else if (path.contains("dark_forest")) {
            leavesBlock = Blocks.DARK_OAK_LEAVES;
        } else if (biome.is(ConventionalBiomeTags.IS_JUNGLE) || biome.is(ConventionalBiomeTags.IS_JUNGLE_TREE) || path.contains("jungle")) {
            leavesBlock = Blocks.JUNGLE_LEAVES;
        } else if (biome.is(ConventionalBiomeTags.IS_BIRCH_FOREST) || path.contains("birch")) {
            leavesBlock = Blocks.BIRCH_LEAVES;
        } else if (biome.is(ConventionalBiomeTags.IS_TAIGA)
                || biome.is(ConventionalBiomeTags.IS_CONIFEROUS_TREE)
                || biome.is(ConventionalBiomeTags.IS_SNOWY)
                || biome.is(ConventionalBiomeTags.IS_ICY)
                || path.contains("taiga")
                || path.contains("grove")
                || path.contains("snow")
                || path.contains("ice")) {
            leavesBlock = Blocks.SPRUCE_LEAVES;
        } else if (biome.is(ConventionalBiomeTags.IS_SAVANNA)
                || biome.is(ConventionalBiomeTags.IS_SAVANNA_TREE)
                || biome.is(ConventionalBiomeTags.IS_BADLANDS)
                || path.contains("savanna")
                || path.contains("badlands")) {
            leavesBlock = Blocks.ACACIA_LEAVES;
        }

        BlockState st = leavesBlock.defaultBlockState();
        if (st.hasProperty(LeavesBlock.PERSISTENT)) {
            st = st.setValue(LeavesBlock.PERSISTENT, true);
        }
        return st;
    }

    private static boolean clearArea(WorldGenLevel level, BlockPos center) {
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                BlockPos top = center.offset(dx, 0, dz);
                if (!level.getFluidState(top).isEmpty()) {
                    return false;
                }

                if (!level.getBlockState(top).isAir()) {
                    level.setBlock(top, Blocks.AIR.defaultBlockState(), 2);
                }
                for (int dy = 1; dy <= 3; dy++) {
                    BlockPos p = top.above(dy);
                    if (level.getBlockState(p).is(Blocks.BEDROCK)) {
                        return false;
                    }
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        return true;
    }

    private static void placeBase(WorldGenLevel level, BlockPos center, RandomSource random) {
        int radius = 5;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int dist2 = dx * dx + dz * dz;
                if (dist2 > 26) {
                    continue;
                }

                float dist = (float) Math.sqrt(dist2);
                float chance = 0.75F - (dist * 0.12F);
                if (chance <= 0.02F) {
                    continue;
                }

                BlockPos p = center.offset(dx, -1, dz);
                BlockState st = level.getBlockState(p);
                if (st.is(Blocks.GRASS_BLOCK) || st.is(Blocks.DIRT) || st.is(Blocks.PODZOL)) {
                    if (random.nextFloat() < chance) {
                        level.setBlock(p, Blocks.COARSE_DIRT.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    private static boolean canPlaceAt(WorldGenLevel level, BlockPos pos) {
        if (!level.getFluidState(pos).isEmpty()) {
            return false;
        }
        if (!level.getBlockState(pos).isAir()) {
            return false;
        }
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    private static boolean isAdjacent2D(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) <= 1 && Math.abs(a.getZ() - b.getZ()) <= 1 && a.getY() == b.getY();
    }

    private void placeCampObjects(WorldGenLevel level, BlockPos center, Direction facing, RandomSource random) {
        WoodPalette palette = getPaletteForBiome(level, center);
        BlockState leaves = getLeavesForBiome(level, center);

        BlockState campfire = Blocks.CAMPFIRE.defaultBlockState();
        if (campfire.hasProperty(CampfireBlock.LIT)) {
            campfire = campfire.setValue(CampfireBlock.LIT, true);
        }
        if (campfire.hasProperty(CampfireBlock.FACING)) {
            campfire = campfire.setValue(CampfireBlock.FACING, facing);
        }
        level.setBlock(center, campfire, 2);

        Set<BlockPos> occupied = new HashSet<>();
        occupied.add(center);

        BlockState slab = palette.slab().defaultBlockState();
        List<BlockPos> seats = new ArrayList<>();
        int seatCount = 2 + random.nextInt(3);
        for (int attempts = 0; attempts < 30 && seats.size() < seatCount; attempts++) {
            Direction dir = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            int dist = 2 + random.nextInt(2);
            int side = random.nextInt(3) - 1;
            BlockPos pos = center.relative(dir, dist).relative(dir.getClockWise(), side);

            if (occupied.contains(pos)) {
                continue;
            }
            if (!canPlaceAt(level, pos)) {
                continue;
            }

            level.setBlock(pos, slab, 2);
            seats.add(pos);
            occupied.add(pos);
        }

        BlockState fence = palette.fence().defaultBlockState();
        int postCount = 1 + random.nextInt(3);
        for (int attempts = 0; attempts < 50 && postCount > 0; attempts++) {
            int dx = random.nextInt(11) - 5;
            int dz = random.nextInt(11) - 5;
            if (Math.abs(dx) <= 2 && Math.abs(dz) <= 2) {
                continue;
            }

            BlockPos pos = center.offset(dx, 0, dz);
            if (occupied.contains(pos)) {
                continue;
            }
            if (!canPlaceAt(level, pos)) {
                continue;
            }
            boolean nearSeat = false;
            for (BlockPos seat : seats) {
                if (isAdjacent2D(pos, seat)) {
                    nearSeat = true;
                    break;
                }
            }
            if (nearSeat) {
                continue;
            }

            level.setBlock(pos, fence, 2);
            occupied.add(pos);
            postCount--;
        }

        if (random.nextFloat() < 0.35F) {
            for (int attempts = 0; attempts < 25; attempts++) {
                int dx = random.nextInt(9) - 4;
                int dz = random.nextInt(9) - 4;
                if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                    continue;
                }
                BlockPos pos = center.offset(dx, 0, dz);
                if (occupied.contains(pos)) {
                    continue;
                }
                if (!canPlaceAt(level, pos)) {
                    continue;
                }
                level.setBlock(pos, Blocks.HAY_BLOCK.defaultBlockState(), 2);
                occupied.add(pos);
                break;
            }
        }

        BlockPos leavesPos = null;
        int leavesCount = 1 + (random.nextFloat() < 0.35F ? 1 : 0);
        for (int placed = 0; placed < leavesCount; placed++) {
            BlockPos chosen = null;
            for (int attempts = 0; attempts < 60; attempts++) {
                int dx = random.nextInt(11) - 5;
                int dz = random.nextInt(11) - 5;
                int dist = Math.abs(dx) + Math.abs(dz);
                if (dist < 5 || dist > 9) {
                    continue;
                }
                BlockPos pos = center.offset(dx, 0, dz);
                if (occupied.contains(pos)) {
                    continue;
                }
                if (!canPlaceAt(level, pos)) {
                    continue;
                }

                boolean adjacentToSeat = false;
                for (BlockPos seat : seats) {
                    if (isAdjacent2D(pos, seat)) {
                        adjacentToSeat = true;
                        break;
                    }
                }
                if (adjacentToSeat) {
                    continue;
                }
                if (leavesPos != null && isAdjacent2D(pos, leavesPos)) {
                    continue;
                }

                chosen = pos;
                break;
            }

            if (chosen == null) {
                continue;
            }

            level.setBlock(chosen, leaves, 2);
            occupied.add(chosen);
            leavesPos = chosen;
        }

        if (leavesPos == null) {
            for (int dist = 4; dist <= 6; dist++) {
                for (Direction dir : Direction.Plane.HORIZONTAL.shuffledCopy(random)) {
                    BlockPos pos = center.relative(dir, dist);
                    if (occupied.contains(pos)) {
                        continue;
                    }
                    if (!canPlaceAt(level, pos)) {
                        continue;
                    }

                    boolean adjacentToSeat = false;
                    for (BlockPos seat : seats) {
                        if (isAdjacent2D(pos, seat)) {
                            adjacentToSeat = true;
                            break;
                        }
                    }
                    if (adjacentToSeat) {
                        continue;
                    }

                    level.setBlock(pos, leaves, 2);
                    occupied.add(pos);
                    leavesPos = pos;
                    break;
                }
                if (leavesPos != null) {
                    break;
                }
            }
        }
    }
}
