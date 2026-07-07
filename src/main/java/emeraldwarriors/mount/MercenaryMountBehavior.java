package emeraldwarriors.mount;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.mercenary.MercenaryOrder;
import emeraldwarriors.mercenary.MercenaryRole;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.Fluids;

/**
 * Autonomous mount decisions (v3.1): order/distance/weapon based movement,
 * soft owner influence, walk-to-mount anti-teleport, and leading the bound horse on foot.
 */
public final class MercenaryMountBehavior {

    /** Max distance to consider the bound horse available. */
    public static final double HORSE_AVAILABLE_RANGE = 16.0D;
    /** Beyond this the merc ignores the horse until it comes closer. */
    public static final double HORSE_ABANDON_RANGE = 24.0D;
    /** Must walk this close before boarding (anti-teleport). */
    public static final double MOUNT_BOARD_RANGE = 2.5D;
    public static final double MOUNT_BOARD_RANGE_SQR = MOUNT_BOARD_RANGE * MOUNT_BOARD_RANGE;

    private static final double HORSE_AVAILABLE_RANGE_SQR = HORSE_AVAILABLE_RANGE * HORSE_AVAILABLE_RANGE;
    private static final double HORSE_ABANDON_RANGE_SQR = HORSE_ABANDON_RANGE * HORSE_ABANDON_RANGE;

    private static final double FOLLOW_MOUNT_DIST = 8.0D;
    private static final double FOLLOW_MOUNT_DIST_SQR = FOLLOW_MOUNT_DIST * FOLLOW_MOUNT_DIST;
    private static final double FOLLOW_DISMOUNT_DIST = 3.0D;
    private static final double FOLLOW_DISMOUNT_DIST_SQR = FOLLOW_DISMOUNT_DIST * FOLLOW_DISMOUNT_DIST;

    private static final double GUARD_TRAVEL_DIST = 10.0D;
    private static final double GUARD_TRAVEL_DIST_SQR = GUARD_TRAVEL_DIST * GUARD_TRAVEL_DIST;
    private static final double GUARD_ARRIVE_DIST_SQR = 4.0D;

    private static final double PATROL_TRAVEL_DIST = 8.0D;
    private static final double PATROL_TRAVEL_DIST_SQR = PATROL_TRAVEL_DIST * PATROL_TRAVEL_DIST;

    private static final int LEAD_TOGGLE_COOLDOWN = 20;

    /** +20 % sobre el ritmo del goal fuera de combate. */
    private static final double MOUNTED_WALK_SPEED_BOOST = 1.2D;
    /** +17,5 % extra en combate sobre el ritmo de viaje (galope/sprint). */
    private static final double MOUNTED_COMBAT_EXTRA_BOOST = 1.175D;

    private MercenaryMountBehavior() {
    }

    public static void tickOwned(EmeraldMercenaryEntity merc) {
        if (merc.isLeashed()) {
            merc.removeLeash();
        }

        AbstractHorse horse = merc.findBoundHorse();
        if (merc.hasBoundHorse() && horse == null) {
            merc.clearHorseBinding();
            return;
        }
        if (horse == null) {
            return;
        }

        Player owner = merc.getContractOwnerPlayer();
        boolean ownerMounted = owner != null
                && owner.isPassenger()
                && owner.getVehicle() instanceof LivingEntity;
        boolean ownerOnFoot = owner != null && !ownerMounted;

        if (merc.isPassenger() && merc.getVehicle() instanceof AbstractHorse) {
            releaseHorseLead(merc, horse);
            tickMounted(merc, horse, owner, ownerMounted);
            return;
        }

        if (isInCombat(merc)) {
            releaseHorseLead(merc, horse);
            return;
        }

        if (shouldPreferMounted(merc, horse, owner, ownerMounted)) {
            releaseHorseLead(merc, horse);
            approachAndMount(merc, horse);
            return;
        }

        if (shouldLeadHorseOnFoot(merc, horse, owner, ownerOnFoot)) {
            applyHorseLead(merc, horse);
        } else {
            releaseHorseLead(merc, horse);
        }
    }

    private static void tickMounted(
            EmeraldMercenaryEntity merc,
            AbstractHorse horse,
            Player owner,
            boolean ownerMounted
    ) {
        applyMountedPace(merc, horse);

        if (merc.isSpearInMainHand()) {
            return;
        }

        LivingEntity target = merc.getTarget();
        if (target != null && target.isAlive()
                && merc.getCurrentRole() == MercenaryRole.GUARDIAN
                && merc.distanceToSqr(target) <= 16.0D) {
            merc.stopRiding();
            return;
        }

        if (merc.isTacticalHoldActive() && merc.getTacticalHoldPos() != null) {
            BlockPos hold = merc.getTacticalHoldPos();
            if (merc.distanceToSqr(hold.getX() + 0.5, hold.getY(), hold.getZ() + 0.5) <= 4.0D) {
                merc.stopRiding();
                return;
            }
        }

        MercenaryOrder order = merc.getCurrentOrder();
        if (order == MercenaryOrder.FOLLOW && owner != null) {
            if (merc.distanceToSqr(owner) <= FOLLOW_DISMOUNT_DIST_SQR) {
                merc.stopRiding();
            }
            return;
        }

        if (order == MercenaryOrder.GUARD) {
            BlockPos guard = merc.getGuardPos();
            if (guard != null
                    && merc.distanceToSqr(guard.getX() + 0.5, guard.getY(), guard.getZ() + 0.5) <= GUARD_ARRIVE_DIST_SQR) {
                merc.stopRiding();
            }
            return;
        }

        if (order == MercenaryOrder.NEUTRAL) {
            merc.stopRiding();
        }
    }

    private static boolean shouldPreferMounted(
            EmeraldMercenaryEntity merc,
            AbstractHorse horse,
            Player owner,
            boolean ownerMounted
    ) {
        if (!horse.isSaddled()) {
            return false;
        }
        if (horse.isLeashed() && !isHorseLedByMerc(horse, merc)) {
            return false;
        }
        if (merc.distanceToSqr(horse) > HORSE_ABANDON_RANGE_SQR) {
            return false;
        }
        if (isInWaterOrUnsafe(merc) || isInWaterOrUnsafe(horse)) {
            return false;
        }

        if (merc.isSpearInMainHand()) {
            return merc.distanceToSqr(horse) <= HORSE_AVAILABLE_RANGE_SQR;
        }

        if (ownerMounted) {
            return merc.distanceToSqr(horse) <= HORSE_AVAILABLE_RANGE_SQR;
        }

        MercenaryOrder order = merc.getCurrentOrder();
        return switch (order) {
            case FOLLOW -> owner != null && merc.distanceToSqr(owner) > FOLLOW_MOUNT_DIST_SQR;
            case GUARD -> {
                BlockPos guard = merc.getGuardPos();
                yield guard != null
                        && merc.distanceToSqr(guard.getX() + 0.5, guard.getY(), guard.getZ() + 0.5) > GUARD_TRAVEL_DIST_SQR;
            }
            case PATROL -> !merc.getNavigation().isDone()
                    || hasPatrolTravelAhead(merc);
            case NEUTRAL -> false;
        };
    }

    private static boolean hasPatrolTravelAhead(EmeraldMercenaryEntity merc) {
        BlockPos center = merc.getPatrolCenter();
        if (center == null) {
            return false;
        }
        return merc.distanceToSqr(center.getX() + 0.5, center.getY(), center.getZ() + 0.5) > PATROL_TRAVEL_DIST_SQR;
    }

    private static boolean shouldLeadHorseOnFoot(
            EmeraldMercenaryEntity merc,
            AbstractHorse horse,
            Player owner,
            boolean ownerOnFoot
    ) {
        if (!ownerOnFoot || owner == null) {
            return false;
        }
        if (merc.distanceToSqr(horse) > HORSE_AVAILABLE_RANGE_SQR) {
            return false;
        }
        if (horse.isVehicle() || !horse.getPassengers().isEmpty()) {
            return false;
        }
        if (isInWaterOrUnsafe(merc)) {
            return false;
        }

        MercenaryOrder order = merc.getCurrentOrder();
        if (order != MercenaryOrder.FOLLOW
                && order != MercenaryOrder.GUARD
                && order != MercenaryOrder.PATROL) {
            return false;
        }

        if (order == MercenaryOrder.FOLLOW) {
            return !merc.isSystemForcedNone()
                    && merc.distanceToSqr(owner) <= FOLLOW_MOUNT_DIST_SQR * 4.0D;
        }

        if (order == MercenaryOrder.GUARD) {
            BlockPos guard = merc.getGuardPos();
            if (guard == null) {
                return false;
            }
            double distGuard = merc.distanceToSqr(guard.getX() + 0.5, guard.getY(), guard.getZ() + 0.5);
            return distGuard <= GUARD_ARRIVE_DIST_SQR * 4.0D;
        }

        return !merc.getNavigation().isDone();
    }

    private static void approachAndMount(EmeraldMercenaryEntity merc, AbstractHorse horse) {
        if (merc.distanceToSqr(horse) > HORSE_AVAILABLE_RANGE_SQR) {
            return;
        }
        if (!horse.getPassengers().isEmpty()) {
            return;
        }

        if (merc.distanceToSqr(horse) > MOUNT_BOARD_RANGE_SQR) {
            merc.getNavigation().moveTo(horse, 1.0D);
            return;
        }

        merc.getNavigation().stop();
        merc.startRiding(horse);
    }

    private static void applyHorseLead(EmeraldMercenaryEntity merc, AbstractHorse horse) {
        if (isHorseLedByMerc(horse, merc)) {
            return;
        }
        if (merc.getMountLeadCooldown() > 0) {
            return;
        }
        horse.setLeashedTo(merc, true);
        merc.setMountLeadCooldown(LEAD_TOGGLE_COOLDOWN);
    }

    public static void releaseHorseLead(EmeraldMercenaryEntity merc, AbstractHorse horse) {
        if (horse != null && isHorseLedByMerc(horse, merc)) {
            horse.removeLeash();
            merc.setMountLeadCooldown(LEAD_TOGGLE_COOLDOWN);
        }
    }

    public static boolean isHorseLedByMerc(AbstractHorse horse, EmeraldMercenaryEntity merc) {
        return horse.isLeashed() && horse.getLeashHolder() == merc;
    }

    public static boolean isMountedCombat(EmeraldMercenaryEntity merc) {
        if (!merc.isPassenger() || !(merc.getVehicle() instanceof AbstractHorse)) {
            return false;
        }
        LivingEntity target = merc.getTarget();
        return target != null && target.isAlive();
    }

    public static void applyMountedPace(EmeraldMercenaryEntity merc, AbstractHorse horse) {
        // Sin sprint forzado: el galope lo marca solo el multiplicador de pathfinding.
        horse.setSprinting(false);
    }

    /**
     * Viaje: goalSpeed × 1.2. Combate: viaje × 1.175 (galope sobre el ritmo de viaje).
     */
    public static double resolveNavigationSpeed(EmeraldMercenaryEntity merc, double goalSpeed) {
        if (!(merc.getVehicle() instanceof AbstractHorse)) {
            return goalSpeed;
        }
        double travelSpeed = goalSpeed * MOUNTED_WALK_SPEED_BOOST;
        if (isMountedCombat(merc)) {
            return travelSpeed * MOUNTED_COMBAT_EXTRA_BOOST;
        }
        return travelSpeed;
    }

    private static boolean isInCombat(EmeraldMercenaryEntity merc) {
        LivingEntity target = merc.getTarget();
        return target != null && target.isAlive();
    }

    private static boolean isInWaterOrUnsafe(LivingEntity entity) {
        return entity.isInWater() || entity.level().getFluidState(entity.blockPosition()).is(Fluids.WATER);
    }

    /** Called before dimensional teleport so the horse is not left behind oddly. */
    public static void prepareForTeleport(EmeraldMercenaryEntity merc) {
        AbstractHorse horse = merc.findBoundHorse();
        if (merc.isPassenger() && merc.getVehicle() instanceof AbstractHorse) {
            merc.stopRiding();
        }
        releaseHorseLead(merc, horse);
    }
}
