package emeraldwarriors.mount;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.mercenary.MercenaryOrder;
import emeraldwarriors.mercenary.MercenaryRole;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

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

    private static final double GUARD_TRAVEL_DIST = 10.0D;
    private static final double GUARD_TRAVEL_DIST_SQR = GUARD_TRAVEL_DIST * GUARD_TRAVEL_DIST;
    private static final double GUARD_ARRIVE_DIST_SQR = 4.0D;

    private static final double PATROL_TRAVEL_DIST = 8.0D;
    private static final double PATROL_TRAVEL_DIST_SQR = PATROL_TRAVEL_DIST * PATROL_TRAVEL_DIST;

    private static final int LEAD_TOGGLE_COOLDOWN = 20;

    /** Ritmo montado actual (owner a pie caminando, FOLLOW). */
    private static final double MOUNTED_WALK_SPEED_BOOST = 1.2D;
    /** Catalejo: trote sobre el paso (más vivo que caminar, sin galope de sprint). */
    private static final double MOUNTED_TROT_EXTRA = 1.25D;
    /** Owner en su montura, sin sprint: un poco más que el paso para no quedarse atrás. */
    private static final double MOUNTED_KEEP_UP_EXTRA = 1.15D;
    /** Sprint vanilla (~+30 %) cuando el owner corre a pie o galopa montado. */
    private static final double MOUNTED_GALLOP_EXTRA = 1.3D;
    /** Combate montado sobre el ritmo de viaje. */
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
            if (isHorseLedByMerc(horse, merc)) {
                horse.removeLeash();
            }
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
            tickHorseFollow(merc, horse);
        } else {
            releaseHorseFollow(merc, horse);
        }
    }

    /** Wild mercenaries without an owner: keep the bound horse near without a vanilla leash. */
    public static void tickWildBoundHorse(EmeraldMercenaryEntity merc, AbstractHorse horse) {
        if (merc.isPassenger() && merc.getVehicle() instanceof AbstractHorse mount) {
            applyMountedPace(merc, mount);
            releaseHorseFollow(merc, horse);
            return;
        }
        if (horse.isVehicle() || !horse.getPassengers().isEmpty()) {
            return;
        }
        if (merc.distanceToSqr(horse) > HORSE_ABANDON_RANGE_SQR) {
            releaseHorseFollow(merc, horse);
            return;
        }
        if (isInCombat(merc)) {
            releaseHorseFollow(merc, horse);
            return;
        }
        tickHorseFollow(merc, horse);
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
            if (MercenaryMountSteering.distanceToFollowSlotSqr(merc, owner) <= 2.25D) {
                merc.stopRiding();
                return;
            }
            Vec3 slot = MercenaryMountSteering.getFollowSlot(merc, owner);
            horse.getNavigation().moveTo(slot.x, slot.y, slot.z, resolveNavigationSpeed(merc, 1.0D));
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

        if (merc.isTacticalHoldActive() && merc.getTacticalHoldPos() != null) {
            BlockPos hold = merc.getTacticalHoldPos();
            if (merc.distanceToSqr(hold.getX() + 0.5, hold.getY(), hold.getZ() + 0.5) > GUARD_TRAVEL_DIST_SQR) {
                return merc.distanceToSqr(horse) <= HORSE_AVAILABLE_RANGE_SQR;
            }
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

        if (merc.isTacticalHoldActive() && merc.getTacticalHoldPos() != null) {
            return merc.distanceToSqr(horse) <= HORSE_AVAILABLE_RANGE_SQR;
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
        MercenaryMounts.prepareForMount(horse);
        merc.startRiding(horse);
    }

    /**
     * Pathfind the bound horse toward the mercenary instead of using a vanilla leash,
     * which breaks repeatedly when the merc walks to a distant tactical point.
     */
    private static void tickHorseFollow(EmeraldMercenaryEntity merc, AbstractHorse horse) {
        if (isHorseLedByMerc(horse, merc)) {
            horse.removeLeash();
        }
        if (merc.distanceToSqr(horse) > HORSE_ABANDON_RANGE_SQR) {
            horse.getNavigation().stop();
            return;
        }
        if (merc.distanceToSqr(horse) <= MOUNT_BOARD_RANGE_SQR) {
            horse.getNavigation().stop();
            return;
        }
        horse.getNavigation().moveTo(merc, 1.15D);
    }

    public static void releaseHorseLead(EmeraldMercenaryEntity merc, AbstractHorse horse) {
        releaseHorseFollow(merc, horse);
    }

    public static void releaseHorseFollow(EmeraldMercenaryEntity merc, AbstractHorse horse) {
        if (horse == null) {
            return;
        }
        if (isHorseLedByMerc(horse, merc)) {
            horse.removeLeash();
            merc.setMountLeadCooldown(LEAD_TOGGLE_COOLDOWN);
        }
        if (!horse.getNavigation().isDone()) {
            horse.getNavigation().stop();
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
        MountedGait gait = resolveMountedGait(merc);
        boolean moving = horse.getMoveControl().hasWanted()
                || !horse.getNavigation().isDone()
                || horse.getDeltaMovement().horizontalDistanceSqr() > 0.0025D;
        // Walk usa la animación de paso. Trote/galope/keep-up activan el sprint vanilla de la montura.
        horse.setSprinting(moving && gait != MountedGait.WALK);
    }

    /**
     * Viaje a pie del owner: goalSpeed × 1.2 × escala montura (ritmo actual).
     * Trote (catalejo), keep-up (owner montado) y galope (owner sprint) se aplican encima.
     */
    public static double resolveNavigationSpeed(EmeraldMercenaryEntity merc, double goalSpeed) {
        if (!(merc.getVehicle() instanceof AbstractHorse mount)) {
            return goalSpeed;
        }
        double walkSpeed = goalSpeed * MOUNTED_WALK_SPEED_BOOST * MercenaryMounts.getMountedNavigationScale(mount);
        return switch (resolveMountedGait(merc)) {
            case WALK -> walkSpeed;
            case TROT -> walkSpeed * MOUNTED_TROT_EXTRA;
            case KEEP_UP -> walkSpeed * MOUNTED_KEEP_UP_EXTRA;
            case GALLOP -> walkSpeed * MOUNTED_GALLOP_EXTRA;
            case COMBAT -> walkSpeed * MOUNTED_COMBAT_EXTRA_BOOST;
        };
    }

    private static MountedGait resolveMountedGait(EmeraldMercenaryEntity merc) {
        if (merc.isTacticalHoldActive() || merc.isTacticalAttackActive()) {
            return MountedGait.TROT;
        }
        if (isMountedCombat(merc)) {
            return MountedGait.COMBAT;
        }
        if (merc.getCurrentOrder() == MercenaryOrder.FOLLOW) {
            Player owner = merc.getContractOwnerPlayer();
            if (owner != null) {
                if (owner.isSprinting()) {
                    return MountedGait.GALLOP;
                }
                if (owner.getVehicle() instanceof AbstractHorse) {
                    return MountedGait.KEEP_UP;
                }
            }
        }
        return MountedGait.WALK;
    }

    private enum MountedGait {
        WALK,
        TROT,
        KEEP_UP,
        GALLOP,
        COMBAT
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
