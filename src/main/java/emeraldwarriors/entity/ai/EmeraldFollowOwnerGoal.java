package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.mercenary.MercenaryOrder;
import emeraldwarriors.mount.MercenaryMountSteering;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Simple follow-owner goal for Emerald mercenaries.
 * The mercenary follows its owner when too far away, similar to a tame wolf.
 *
 * <p>When mounted, {@link EmeraldMercenaryEntity#getEffectiveNavigation()} uses the mercenary's
 * own ground pathfinder (vanilla {@code Mob.getNavigation()} delegates to the mount).
 */
public class EmeraldFollowOwnerGoal extends Goal {
    private final EmeraldMercenaryEntity mercenary;
    private LivingEntity owner;
    private final double speedModifier;
    private int timeToRecalcPath;
    private final float startDistance;
    private final float stopDistance;

    public EmeraldFollowOwnerGoal(EmeraldMercenaryEntity mercenary, double speedModifier, float startDistance, float stopDistance) {
        this.mercenary = mercenary;
        this.speedModifier = speedModifier;
        this.startDistance = startDistance;
        this.stopDistance = stopDistance;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private PathNavigation navigation() {
        return this.mercenary.getEffectiveNavigation();
    }

    private boolean isMounted() {
        return this.mercenary.isPassenger()
                && this.mercenary.getVehicle() instanceof AbstractHorse;
    }

    @Override
    public boolean canUse() {
        MercenaryOrder order = this.mercenary.getCurrentOrder();
        if (order != MercenaryOrder.FOLLOW) {
            return false;
        }
        if (this.mercenary.isTacticalHoldActive() || this.mercenary.isTacticalAttackActive()) {
            return false;
        }
        if (this.mercenary.isSystemForcedNone()) {
            return false;
        }
        if (this.mercenary.getTarget() != null && this.mercenary.getTarget().isAlive()) {
            return false;
        }
        LivingEntity owner = this.mercenary.getOwner();
        if (owner == null) {
            return false;
        }
        if (owner.isSpectator()) {
            return false;
        }
        float start = this.isMounted() ? this.stopDistance : this.startDistance;
        double distSqr = this.followDistanceSqr(owner);
        if (distSqr < (double) (start * start)) {
            return false;
        }
        this.owner = owner;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        MercenaryOrder order = this.mercenary.getCurrentOrder();
        if (order != MercenaryOrder.FOLLOW) {
            return false;
        }
        if (this.mercenary.isTacticalHoldActive() || this.mercenary.isTacticalAttackActive()) {
            return false;
        }
        if (this.mercenary.isSystemForcedNone()) {
            return false;
        }
        if (this.mercenary.getTarget() != null && this.mercenary.getTarget().isAlive()) {
            return false;
        }
        if (this.owner == null || !this.owner.isAlive()) {
            return false;
        }
        if (this.followDistanceSqr(this.owner) <= (double) (this.stopDistance * this.stopDistance)) {
            return false;
        }
        // Mounted: keep steering even if the nav reports "done" for a moment at a ledge.
        if (!this.isMounted() && this.navigation().isDone()) {
            return false;
        }
        return true;
    }

    @Override
    public void start() {
        this.timeToRecalcPath = 0;
    }

    @Override
    public void stop() {
        this.owner = null;
        this.navigation().stop();
    }

    @Override
    public void tick() {
        if (this.owner == null) {
            return;
        }

        this.mercenary.getLookControl().setLookAt(this.owner, 10.0F, this.mercenary.getMaxHeadXRot());

        boolean mounted = this.isMounted();
        if (mounted || --this.timeToRecalcPath <= 0) {
            this.timeToRecalcPath = this.adjustedTickDelay(10);

            if (!this.mercenary.isLeashed()) {
                double speed = this.mercenary.resolveNavigationSpeed(this.speedModifier);
                if (this.owner.isSprinting() && !mounted) {
                    speed = Math.max(speed, this.mercenary.resolveNavigationSpeed(1.2D));
                }
                this.moveToOwner(speed);
            }
        }
    }

    private double followDistanceSqr(LivingEntity owner) {
        if (this.isMounted()) {
            return MercenaryMountSteering.distanceToFollowSlotSqr(this.mercenary, owner);
        }
        return this.mercenary.distanceToSqr(owner);
    }

    private void moveToOwner(double speed) {
        if (this.isMounted()) {
            Vec3 slot = MercenaryMountSteering.getFollowSlot(this.mercenary, this.owner);
            this.navigation().moveTo(slot.x, slot.y, slot.z, speed);
            return;
        }
        this.navigation().moveTo(this.owner, speed);
    }
}
