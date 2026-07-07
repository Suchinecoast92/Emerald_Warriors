package emeraldwarriors.mount;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.mercenary.MercenaryOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Spawns and configures horses for wild mercenary groups (camps, patrols).
 */
public final class MercenaryMountHelper {

    public static final double BIND_RANGE = 8.0D;
    public static final double MOUNT_RANGE = 16.0D;
    public static final double MOUNT_APPROACH_RANGE = 24.0D;

    private MercenaryMountHelper() {
    }

    public static void setupWildCampMount(
            ServerLevel level,
            EmeraldMercenaryEntity merc,
            BlockPos campCenter,
            RandomSource random
    ) {
        AbstractHorse horse = spawnTamedHorse(level, merc.blockPosition(), random);
        if (horse == null) {
            return;
        }

        claimHorseForMercenary(level, merc, horse);
        merc.setCurrentOrder(MercenaryOrder.PATROL);
        merc.setPatrolCenter(campCenter);

        boolean startMounted = random.nextFloat() < 0.4F;
        if (startMounted) {
            merc.startRiding(horse);
        } else {
            horse.setLeashedTo(merc, true);
        }
    }

    public static AbstractHorse spawnTamedHorse(ServerLevel level, BlockPos near, RandomSource random) {
        Horse horse = EntityType.HORSE.create(level, EntitySpawnReason.STRUCTURE);
        if (horse == null) {
            return null;
        }

        double ox = (random.nextDouble() - 0.5D) * 3.0D;
        double oz = (random.nextDouble() - 0.5D) * 3.0D;
        horse.setPos(near.getX() + 0.5D + ox, near.getY(), near.getZ() + 0.5D + oz);
        horse.setYRot(random.nextFloat() * 360.0F);
        horse.finalizeSpawn(level, level.getCurrentDifficultyAt(near), EntitySpawnReason.STRUCTURE, null);
        horse.setTamed(true);
        horse.setAge(0);
        horse.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
        level.addFreshEntity(horse);
        return horse;
    }

    public static Component mountDisplayName(AbstractHorse horse) {
        return horse.getName();
    }

    public static void claimHorseForMercenary(
            ServerLevel level,
            EmeraldMercenaryEntity merc,
            AbstractHorse horse
    ) {
        for (EmeraldMercenaryEntity other : level.getEntitiesOfClass(
                EmeraldMercenaryEntity.class,
                horse.getBoundingBox().inflate(2.0D),
                e -> e != merc && horse.getUUID().equals(e.getBoundHorseUuid())
        )) {
            other.clearHorseBinding();
        }
        merc.bindToHorse(horse);
    }

    public static AbstractHorse findNearestHorse(EmeraldMercenaryEntity merc, double range) {
        List<AbstractHorse> horses = merc.level().getEntitiesOfClass(
                AbstractHorse.class,
                merc.getBoundingBox().inflate(range),
                AbstractHorse::isAlive
        );
        AbstractHorse best = null;
        double bestDist = range * range;
        for (AbstractHorse horse : horses) {
            double dist = merc.distanceToSqr(horse);
            if (dist < bestDist) {
                bestDist = dist;
                best = horse;
            }
        }
        return best;
    }
}
