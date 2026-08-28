package emeraldwarriors.mount;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.mixin.MobAiAccessor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * While a mercenary is riding, replace the horse's vanilla pathfinder/MoveControl with
 * direct steering. Restore the originals on dismount.
 */
public final class MercenaryMountSteering {

    private static final WeakHashMap<AbstractHorse, MoveControl> ORIGINAL_MOVE = new WeakHashMap<>();
    private static final WeakHashMap<AbstractHorse, PathNavigation> ORIGINAL_NAV = new WeakHashMap<>();
    /** Extra gap between hitboxes so horse/camel models do not clip. */
    private static final double SEPARATION_PADDING = 0.85D;
    private static final double SEPARATION_LOOKAHEAD = 3.5D;
    private static final double MAX_SEPARATION_NUDGE = 1.6D;

    private MercenaryMountSteering() {
    }

    public static void ensureMountedControls(AbstractHorse horse) {
        if (!(horse instanceof MobAiAccessor accessor)) {
            return;
        }
        if (horse.getMoveControl() instanceof MercenaryHorseMoveControl
                && horse.getNavigation() instanceof DirectMountNavigation) {
            return;
        }
        ORIGINAL_MOVE.putIfAbsent(horse, horse.getMoveControl());
        ORIGINAL_NAV.putIfAbsent(horse, horse.getNavigation());
        accessor.emeraldWarriors$setMoveControl(new MercenaryHorseMoveControl(horse));
        accessor.emeraldWarriors$setNavigation(new DirectMountNavigation(horse, horse.level()));
        horse.getNavigation().stop();
    }

    public static void restoreControls(AbstractHorse horse) {
        if (!(horse instanceof MobAiAccessor accessor)) {
            return;
        }
        MoveControl move = ORIGINAL_MOVE.remove(horse);
        PathNavigation nav = ORIGINAL_NAV.remove(horse);
        if (move != null) {
            accessor.emeraldWarriors$setMoveControl(move);
        }
        if (nav != null) {
            accessor.emeraldWarriors$setNavigation(nav);
        }
        horse.getNavigation().stop();
        horse.setZza(0.0F);
        horse.setSprinting(false);
    }

    public static boolean isSteering(AbstractHorse horse) {
        return horse.getMoveControl() instanceof MercenaryHorseMoveControl;
    }

    public static void tickRider(EmeraldMercenaryEntity merc, AbstractHorse horse) {
        if (merc.level().isClientSide()) {
            return;
        }
        ensureMountedControls(horse);
        MercenaryMounts.prepareForMount(horse);
        Vec3 nudge = separationNudge(horse);
        if (nudge.lengthSqr() <= 1.0E-4D) {
            return;
        }
        if (horse.getNavigation() instanceof DirectMountNavigation nav && !nav.isDone()) {
            return;
        }
        horse.getMoveControl().setWantedPosition(
                horse.getX() + nudge.x,
                horse.getY(),
                horse.getZ() + nudge.z,
                0.9D);
    }

    /**
     * Stable parking slot around the owner so several mounts do not converge on the same block.
     * Radius scales with the mount's width (camels sit a bit farther out than horses).
     */
    public static Vec3 getFollowSlot(EmeraldMercenaryEntity merc, LivingEntity owner) {
        double radius = followSlotRadius(merc);
        double angle = merc.getPersonalSpreadAngle();
        return new Vec3(
                owner.getX() + Math.cos(angle) * radius,
                owner.getY(),
                owner.getZ() + Math.sin(angle) * radius);
    }

    public static double followSlotRadius(EmeraldMercenaryEntity merc) {
        double width = 1.4D;
        if (merc.getVehicle() instanceof AbstractHorse horse) {
            width = horse.getBbWidth();
        }
        return Math.max(2.9D, width * 1.15D + 1.5D) + Math.floorMod(merc.getId(), 4) * 0.28D;
    }

    public static double distanceToFollowSlotSqr(EmeraldMercenaryEntity merc, LivingEntity owner) {
        Vec3 slot = getFollowSlot(merc, owner);
        return merc.distanceToSqr(slot.x, slot.y, slot.z);
    }

    /**
     * Horizontal push away from other mercenary-ridden mounts that are inside this mount's space.
     */
    public static Vec3 separationNudge(Mob mount) {
        if (!(mount instanceof AbstractHorse horse) || horse.level().isClientSide()) {
            return Vec3.ZERO;
        }
        if (!(horse.getFirstPassenger() instanceof EmeraldMercenaryEntity self)) {
            return Vec3.ZERO;
        }

        List<AbstractHorse> nearby = horse.level().getEntitiesOfClass(
                AbstractHorse.class,
                horse.getBoundingBox().inflate(SEPARATION_LOOKAHEAD),
                other -> other != horse
                        && other.isAlive()
                        && other.getFirstPassenger() instanceof EmeraldMercenaryEntity otherMerc
                        && isSameFollowGroup(self, otherMerc));
        if (nearby.isEmpty()) {
            return Vec3.ZERO;
        }

        double nx = 0.0D;
        double nz = 0.0D;
        for (AbstractHorse other : nearby) {
            double min = (horse.getBbWidth() + other.getBbWidth()) * 0.5D + SEPARATION_PADDING;
            double dx = horse.getX() - other.getX();
            double dz = horse.getZ() - other.getZ();
            double distSqr = dx * dx + dz * dz;
            if (distSqr >= min * min) {
                continue;
            }
            double dist = Math.sqrt(distSqr);
            double strength = dist < 1.0E-4D ? 1.0D : (min - dist) / min;
            if (dist < 1.0E-4D) {
                double angle = self.getPersonalSpreadAngle();
                nx += Math.cos(angle) * strength;
                nz += Math.sin(angle) * strength;
            } else {
                nx += (dx / dist) * strength;
                nz += (dz / dist) * strength;
            }
        }

        double mag = Math.sqrt(nx * nx + nz * nz);
        if (mag < 1.0E-4D) {
            return Vec3.ZERO;
        }
        if (mag > MAX_SEPARATION_NUDGE) {
            nx = nx / mag * MAX_SEPARATION_NUDGE;
            nz = nz / mag * MAX_SEPARATION_NUDGE;
        }
        return new Vec3(nx, 0.0D, nz);
    }

    private static boolean isSameFollowGroup(EmeraldMercenaryEntity a, EmeraldMercenaryEntity b) {
        UUID ownerA = a.getOwnerUuid();
        UUID ownerB = b.getOwnerUuid();
        if (ownerA == null || ownerB == null) {
            return true;
        }
        return ownerA.equals(ownerB);
    }
}
