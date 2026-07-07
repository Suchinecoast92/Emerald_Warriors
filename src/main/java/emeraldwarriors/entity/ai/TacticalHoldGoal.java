package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Move to a spyglass-marked position and hold until the tactical command is cleared.
 */
public class TacticalHoldGoal extends Goal {

    private static final double ARRIVE_DISTANCE_SQR = 2.25D;

    private final EmeraldMercenaryEntity mercenary;
    private final double speedModifier;

    public TacticalHoldGoal(EmeraldMercenaryEntity mercenary, double speedModifier) {
        this.mercenary = mercenary;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!this.mercenary.isTacticalHoldActive()) {
            return false;
        }
        if (!this.mercenary.canObeyTacticalCommands()) {
            return false;
        }
        BlockPos hold = this.mercenary.getTacticalHoldPos();
        if (hold == null) {
            return false;
        }
        return this.distanceToHoldSqr(hold) > ARRIVE_DISTANCE_SQR;
    }

    @Override
    public boolean canContinueToUse() {
        if (!this.mercenary.isTacticalHoldActive() || !this.mercenary.canObeyTacticalCommands()) {
            return false;
        }
        BlockPos hold = this.mercenary.getTacticalHoldPos();
        if (hold == null) {
            return false;
        }
        return this.distanceToHoldSqr(hold) > ARRIVE_DISTANCE_SQR;
    }

    @Override
    public void start() {
        BlockPos hold = this.mercenary.getTacticalHoldPos();
        if (hold != null) {
            this.mercenary.getEffectiveNavigation().moveTo(
                    hold.getX() + 0.5, hold.getY(), hold.getZ() + 0.5,
                    this.resolveHoldSpeed());
        }
    }

    @Override
    public void tick() {
        BlockPos hold = this.mercenary.getTacticalHoldPos();
        if (hold == null) {
            return;
        }
        if (this.mercenary.getEffectiveNavigation().isDone()) {
            this.mercenary.getEffectiveNavigation().moveTo(
                    hold.getX() + 0.5, hold.getY(), hold.getZ() + 0.5,
                    this.resolveHoldSpeed());
        }
    }

    @Override
    public void stop() {
        this.mercenary.getEffectiveNavigation().stop();
    }

    /** Misma velocidad que follow: goal 1.0 + boosts montados (equino/camello). */
    private double resolveHoldSpeed() {
        return this.mercenary.resolveNavigationSpeed(this.speedModifier);
    }

    private double distanceToHoldSqr(BlockPos hold) {
        return this.mercenary.distanceToSqr(hold.getX() + 0.5, hold.getY(), hold.getZ() + 0.5);
    }
}
