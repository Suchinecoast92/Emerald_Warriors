package emeraldwarriors.entity.spawn;

import emeraldwarriors.config.ModConfig;
import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.entity.ModEntities;
import emeraldwarriors.mercenary.MercenaryOrder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class VillageMercenarySpawner {

    private static final Map<UUID, ActivityState> ACTIVITY = new HashMap<>();
    private static final Map<UUID, VillageVisitState> VISITS = new HashMap<>();

    private VillageMercenarySpawner() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(VillageMercenarySpawner::onWorldTick);
    }

    private static void onWorldTick(ServerLevel level) {
        if (!level.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        var cfg = ModConfig.get();
        if (!cfg.toggles.villageSpawns) {
            return;
        }

        updateActivity(level);

        attemptInitialSpawnOnVillageEntry(level);

        int interval = Math.max(1, cfg.villageSpawn.checkIntervalTicks);
        float chance = Math.max(0.0F, Math.min(1.0F, cfg.villageSpawn.spawnChancePerCheck));

        long time = level.getGameTime();
        if (time % interval != 0L) {
            return;
        }

        if (level.getRandom().nextFloat() >= chance) {
            return;
        }

        attemptSpawnInVillage(level);
    }

    private static void attemptInitialSpawnOnVillageEntry(ServerLevel level) {
        var cfg = ModConfig.get().villageSpawn;
        float firstChance = Math.max(0.0F, Math.min(1.0F, cfg.firstMercSpawnChanceOnVillageEntry));
        if (firstChance <= 0.0F) {
            return;
        }

        var random = level.getRandom();
        long time = level.getGameTime();

        for (var p : level.players()) {
            if (p.isSpectator()) {
                continue;
            }

            BlockPos base = p.blockPosition();
            boolean inVillage = level.isVillage(base) || level.isCloseToVillage(base, 64);

            VillageVisitState state = VISITS.get(p.getUUID());
            if (state == null) {
                state = new VillageVisitState(false);
                VISITS.put(p.getUUID(), state);
            }

            if (!inVillage) {
                state.inVillage = false;
                continue;
            }

            if (state.inVillage) {
                continue;
            }
            state.inVillage = true;

            if (cfg.requireActivePlayer) {
                ActivityState activity = ACTIVITY.get(p.getUUID());
                if (activity != null) {
                    int window = Math.max(0, cfg.activePlayerWindowTicks);
                    if (window > 0 && (time - activity.lastMovedGameTime) > window) {
                        continue;
                    }
                }
            }

            Optional<BlockPos> bell = level.getPoiManager().findClosest(
                    holder -> holder.is(PoiTypes.MEETING),
                    base,
                    96,
                    PoiManager.Occupancy.ANY
            );
            if (bell.isEmpty()) {
                continue;
            }
            BlockPos bellPos = bell.get();
            if (!isNaturalVillageBell(level, bellPos)) {
                continue;
            }

            int nearbyAtBell = level.getEntitiesOfClass(
                    EmeraldMercenaryEntity.class,
                    new AABB(bellPos).inflate(48.0D, 16.0D, 48.0D)
            ).size();
            if (nearbyAtBell > 0) {
                continue;
            }

            if (random.nextFloat() >= firstChance) {
                continue;
            }

            trySpawnNearBell(level, bellPos);
        }
    }

    private static void attemptSpawnInVillage(ServerLevel level) {
        int cap = Math.max(0, ModConfig.get().villageSpawn.maxNearbyMercs);
        var cfg = ModConfig.get().villageSpawn;
        var random = level.getRandom();
        if (level.players().isEmpty()) {
            return;
        }

        List<BellCandidate> candidates = new ArrayList<>();
        for (var p : level.players()) {
            if (p.isSpectator()) {
                continue;
            }

            BlockPos base = p.blockPosition();
            if (!(level.isVillage(base) || level.isCloseToVillage(base, 64))) {
                continue;
            }

            if (cfg.requireActivePlayer) {
                ActivityState state = ACTIVITY.get(p.getUUID());
                if (state != null) {
                    int window = Math.max(0, cfg.activePlayerWindowTicks);
                    if (window > 0 && (level.getGameTime() - state.lastMovedGameTime) > window) {
                        continue;
                    }
                }
            }

            Optional<BlockPos> bell = level.getPoiManager().findClosest(
                    holder -> holder.is(PoiTypes.MEETING),
                    base,
                    96,
                    PoiManager.Occupancy.ANY
            );
            if (bell.isEmpty()) {
                continue;
            }
            BlockPos bellPos = bell.get();
            if (!isNaturalVillageBell(level, bellPos)) {
                continue;
            }

            candidates.add(new BellCandidate(p, bellPos));
        }

        if (candidates.isEmpty()) {
            return;
        }

        var candidate = candidates.get(random.nextInt(candidates.size()));
        BlockPos bellPos = candidate.bellPos;

        int nearbyAtBell = level.getEntitiesOfClass(
                EmeraldMercenaryEntity.class,
                new AABB(bellPos).inflate(48.0D, 16.0D, 48.0D)
        ).size();
        if (cap > 0 && nearbyAtBell >= cap) {
            return;
        }
        if (cap > 0 && nearbyAtBell >= 2) {
            float mult = Math.max(0.0F, Math.min(1.0F, cfg.thirdMercSpawnChanceMultiplier));
            if (random.nextFloat() >= mult) {
                return;
            }
        }

        trySpawnNearBell(level, bellPos);
    }

    private static void trySpawnNearBell(ServerLevel level, BlockPos bellPos) {
        var random = level.getRandom();

        for (int attempts = 0; attempts < 25; attempts++) {
            int dx = random.nextInt(33) - 16;
            int dz = random.nextInt(33) - 16;
            if ((dx * dx + dz * dz) < 16) {
                continue;
            }

            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bellPos.offset(dx, 0, dz));
            if (!(level.isVillage(surface) || level.isCloseToVillage(surface, 64))) {
                continue;
            }

            var biome = level.getBiome(surface);
            if (biome.is(ConventionalBiomeTags.IS_OCEAN)
                    || biome.is(ConventionalBiomeTags.IS_DEEP_OCEAN)
                    || biome.is(ConventionalBiomeTags.IS_SHALLOW_OCEAN)
                    || biome.is(ConventionalBiomeTags.IS_RIVER)
                    || biome.is(ConventionalBiomeTags.IS_BEACH)
                    || biome.is(ConventionalBiomeTags.IS_MUSHROOM)
                    || biome.is(ConventionalBiomeTags.IS_UNDERGROUND)
                    || biome.is(ConventionalBiomeTags.IS_CAVE)) {
                continue;
            }

            if (!SpawnPlacements.checkSpawnRules(ModEntities.EMERALD_MERCENARY, level, EntitySpawnReason.EVENT, surface, random)) {
                continue;
            }

            var merc = ModEntities.EMERALD_MERCENARY.spawn(level, surface, EntitySpawnReason.EVENT);
            if (merc != null) {
                merc.setCurrentOrder(MercenaryOrder.NEUTRAL);
            }
            return;
        }
    }

    private static boolean isNaturalVillageBell(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).is(Blocks.BELL)) {
            return false;
        }
        StructureStart start = level.structureManager().getStructureWithPieceAt(pos, StructureTags.VILLAGE);
        return start.isValid();
    }

    private static void updateActivity(ServerLevel level) {
        var cfg = ModConfig.get().villageSpawn;
        int minMove = Math.max(0, cfg.activePlayerMinMoveBlocks);
        int minMoveSq = minMove * minMove;
        long time = level.getGameTime();

        for (var p : level.players()) {
            UUID id = p.getUUID();
            BlockPos pos = p.blockPosition();
            ActivityState state = ACTIVITY.get(id);
            if (state == null) {
                ACTIVITY.put(id, new ActivityState(pos, time));
                continue;
            }

            if (pos.distSqr(state.lastPos) >= (double) minMoveSq) {
                state.lastPos = pos;
                state.lastMovedGameTime = time;
            }
        }
    }

    private record BellCandidate(net.minecraft.world.entity.player.Player player, BlockPos bellPos) {
    }

    private static final class ActivityState {
        private BlockPos lastPos;
        private long lastMovedGameTime;

        private ActivityState(BlockPos lastPos, long lastMovedGameTime) {
            this.lastPos = lastPos;
            this.lastMovedGameTime = lastMovedGameTime;
        }
    }

    private static final class VillageVisitState {
        private boolean inVillage;

        private VillageVisitState(boolean inVillage) {
            this.inVillage = inVillage;
        }
    }
}
