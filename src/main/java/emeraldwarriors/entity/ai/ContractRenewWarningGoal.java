package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * Short walk toward the owner when a renew-notice fires.
 * The action-bar warning is always sent from {@link EmeraldMercenaryEntity};
 * this goal only runs if the mercenary can path (not fighting, owner in range).
 */
public class ContractRenewWarningGoal extends Goal {

    private static final double APPROACH_RANGE = 48.0D;
    private static final double STAND_DISTANCE = 3.5D;

    private final EmeraldMercenaryEntity mercenary;
    private final double speed;

    public ContractRenewWarningGoal(EmeraldMercenaryEntity mercenary, double speed) {
        this.mercenary = mercenary;
        this.speed = speed;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.mercenary.level().isClientSide()) {
            return false;
        }
        if (this.mercenary.isContractAdmiring() || this.mercenary.isContractExpireNotifying()) {
            return false;
        }
        if (!this.mercenary.wantsContractRenewApproach()) {
            return false;
        }
        if (this.mercenary.getTarget() != null && this.mercenary.getTarget().isAlive()) {
            return false;
        }

        Player owner = this.mercenary.getContractOwnerPlayer();
        if (owner == null || !owner.isAlive() || owner.isSpectator()) {
            return false;
        }
        return this.mercenary.distanceToSqr(owner) <= APPROACH_RANGE * APPROACH_RANGE;
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void stop() {
        this.mercenary.getEffectiveNavigation().stop();
    }

    @Override
    public void tick() {
        Player owner = this.mercenary.getContractOwnerPlayer();
        if (owner == null) {
            return;
        }

        this.mercenary.getLookControl().setLookAt(owner, 30.0F, this.mercenary.getMaxHeadXRot());

        double standSqr = STAND_DISTANCE * STAND_DISTANCE;
        if (this.mercenary.distanceToSqr(owner) > standSqr) {
            this.mercenary.getEffectiveNavigation().moveTo(
                    owner, this.mercenary.resolveNavigationSpeed(this.speed));
        } else {
            this.mercenary.getEffectiveNavigation().stop();
        }
    }
}
