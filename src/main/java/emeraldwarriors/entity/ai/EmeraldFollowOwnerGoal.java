package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;

import java.util.EnumSet;

/**
 * Simple follow-owner goal for Emerald mercenaries.
 * The mercenary follows its owner when too far away, similar to a tame wolf.
 */
public class EmeraldFollowOwnerGoal extends Goal {
    private final EmeraldMercenaryEntity mercenary;
    private LivingEntity owner;
    private final double speedModifier;
    private final PathNavigation navigation;
    private int timeToRecalcPath;
    private final float startDistance;
    private final float stopDistance;
    private float oldWaterCost;

    public EmeraldFollowOwnerGoal(EmeraldMercenaryEntity mercenary, double speedModifier, float startDistance, float stopDistance) {
        this.mercenary = mercenary;
        this.speedModifier = speedModifier;
        this.navigation = mercenary.getNavigation();
        this.startDistance = startDistance;
        this.stopDistance = stopDistance;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity owner = this.mercenary.getOwner();
        if (owner == null) {
            return false;
        }
        if (owner.isSpectator()) {
            return false;
        }
        if (this.mercenary.distanceToSqr(owner) < (double)(this.startDistance * this.startDistance)) {
            return false;
        }
        this.owner = owner;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.navigation.isDone()) {
            return false;
        }
        return this.mercenary.distanceToSqr(this.owner) > (double)(this.stopDistance * this.stopDistance);
    }

    @Override
    public void start() {
        this.timeToRecalcPath = 0;
        this.oldWaterCost = 0.0F;
    }

    @Override
    public void stop() {
        this.owner = null;
        this.navigation.stop();
    }

    @Override
    public void tick() {
        if (this.owner == null) {
            return;
        }

        this.mercenary.getLookControl().setLookAt(this.owner, 10.0F, this.mercenary.getMaxHeadXRot());

        if (--this.timeToRecalcPath <= 0) {
            this.timeToRecalcPath = this.adjustedTickDelay(10);

            if (!this.mercenary.isLeashed() && !this.mercenary.isPassenger()) {
                this.navigation.moveTo(this.owner, this.speedModifier);
            }
        }
    }
}
