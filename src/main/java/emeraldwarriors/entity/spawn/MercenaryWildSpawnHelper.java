package emeraldwarriors.entity.spawn;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.mercenary.MercenaryOrder;
import emeraldwarriors.mercenary.MercenaryRank;
import emeraldwarriors.mount.MercenaryMountHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.SpawnGroupData;

/**
 * Spawn natural de grupos salvajes: patrulla montada (líder alto rango + acompañantes a pie).
 */
public final class MercenaryWildSpawnHelper {

    /** Probabilidad de que el primer miembro de un grupo sea líder de patrulla montada. */
    private static final float MOUNTED_PATROL_LEADER_CHANCE = 0.22F;

    private MercenaryWildSpawnHelper() {
    }

    public static SpawnGroupData handleNaturalSpawn(
            EmeraldMercenaryEntity merc,
            ServerLevel level,
            SpawnGroupData spawnGroupData
    ) {
        MercenarySpawnGroupData data;
        if (spawnGroupData instanceof MercenarySpawnGroupData existing) {
            data = existing;
            data.membersSpawned++;
            if (data.mountedPatrol && data.leaderId != null) {
                applyMountedPatrolFollower(merc, data);
            } else {
                merc.setCurrentOrder(MercenaryOrder.NEUTRAL);
                merc.setPatrolCenter(merc.blockPosition());
            }
            return data;
        }

        data = new MercenarySpawnGroupData();
        data.membersSpawned = 1;
        data.mountedPatrol = merc.getRandom().nextFloat() < MOUNTED_PATROL_LEADER_CHANCE;
        if (data.mountedPatrol) {
            data.leaderId = merc.getUUID();
            data.patrolAnchor = merc.blockPosition();
            applyMountedPatrolLeader(merc, level, data.patrolAnchor);
        } else {
            merc.setCurrentOrder(MercenaryOrder.NEUTRAL);
            merc.setPatrolCenter(merc.blockPosition());
        }
        return data;
    }

    private static void applyMountedPatrolLeader(
            EmeraldMercenaryEntity merc,
            ServerLevel level,
            BlockPos anchor
    ) {
        if (merc.getRandom().nextFloat() < 0.35F) {
            merc.setRank(MercenaryRank.ANCIENT_GUARD);
        } else {
            merc.setRank(MercenaryRank.VETERAN);
        }
        merc.reapplySpawnGearForRank();
        merc.setCurrentOrder(MercenaryOrder.PATROL);
        merc.setPatrolCenter(anchor);
        MercenaryMountHelper.scheduleWildMountSetup(level, merc, anchor, 0.75F);
    }

    private static void applyMountedPatrolFollower(
            EmeraldMercenaryEntity merc,
            MercenarySpawnGroupData data
    ) {
        int roll = merc.getRandom().nextInt(100);
        if (roll < 50) {
            merc.setRank(MercenaryRank.RECRUIT);
        } else if (roll < 85) {
            merc.setRank(MercenaryRank.SOLDIER);
        } else {
            merc.setRank(MercenaryRank.SENTINEL);
        }
        merc.reapplySpawnGearForRank();
        merc.setCurrentOrder(MercenaryOrder.PATROL);
        merc.setPatrolCenter(data.patrolAnchor != null ? data.patrolAnchor : merc.blockPosition());
        merc.setWildPatrolLeaderUuid(data.leaderId);
    }
}
