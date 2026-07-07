package emeraldwarriors.mount;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.mercenary.MercenaryOrder;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.tags.BiomeTags;

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
        AbstractHorse mount = spawnTamedMount(level, merc.blockPosition(), campCenter, random);
        if (mount == null) {
            return;
        }

        claimHorseForMercenary(level, merc, mount);
        merc.setCurrentOrder(MercenaryOrder.PATROL);
        merc.setPatrolCenter(campCenter);

        boolean startMounted = random.nextFloat() < 0.4F;
        if (startMounted) {
            MercenaryMounts.prepareForMount(mount);
            merc.startRiding(mount);
        } else {
            mount.setLeashedTo(merc, true);
        }
    }

    public static AbstractHorse spawnTamedMount(
            ServerLevel level,
            BlockPos near,
            BlockPos biomePos,
            RandomSource random
    ) {
        boolean aridBiome = isAridCampBiome(level, biomePos);
        EntityType<? extends AbstractHorse> type = pickCampMountType(random, aridBiome);
        AbstractHorse mount = type.create(level, EntitySpawnReason.STRUCTURE);
        if (mount == null) {
            return null;
        }

        double ox = (random.nextDouble() - 0.5D) * 3.0D;
        double oz = (random.nextDouble() - 0.5D) * 3.0D;
        mount.setPos(near.getX() + 0.5D + ox, near.getY(), near.getZ() + 0.5D + oz);
        mount.setYRot(random.nextFloat() * 360.0F);
        mount.finalizeSpawn(level, level.getCurrentDifficultyAt(near), EntitySpawnReason.STRUCTURE, null);
        mount.setTamed(true);
        mount.setAge(0);
        mount.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
        level.addFreshEntity(mount);
        return mount;
    }

    private static boolean isAridCampBiome(ServerLevel level, BlockPos pos) {
        var biome = level.getBiome(pos);
        if (biome.is(ConventionalBiomeTags.IS_BADLANDS) || biome.is(BiomeTags.IS_BADLANDS)) {
            return true;
        }
        String path = biome.unwrapKey()
                .map(ResourceKey::identifier)
                .map(id -> id.getPath())
                .orElse("");
        return path.contains("desert");
    }

    private static EntityType<? extends AbstractHorse> pickCampMountType(RandomSource random, boolean aridBiome) {
        float roll = random.nextFloat();
        if (aridBiome) {
            if (roll < 0.70F) {
                return EntityType.CAMEL;
            }
            if (roll < 0.85F) {
                return EntityType.HORSE;
            }
            if (roll < 0.95F) {
                return EntityType.DONKEY;
            }
            return EntityType.MULE;
        }
        if (roll < 0.50F) {
            return EntityType.HORSE;
        }
        if (roll < 0.70F) {
            return EntityType.DONKEY;
        }
        if (roll < 0.85F) {
            return EntityType.MULE;
        }
        return EntityType.CAMEL;
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
