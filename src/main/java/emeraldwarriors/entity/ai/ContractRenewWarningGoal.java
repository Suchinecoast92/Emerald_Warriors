package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;

import java.util.EnumSet;

public class ContractRenewWarningGoal extends Goal {

    private static final int WARN_THRESHOLD_TICKS = 3600;
    private static final int APPROACH_TIMEOUT_TICKS = 120;
    private static final int WINDOW_TICKS = 200;
    private static final int FAIL_RETRY_COOLDOWN_TICKS = 100;

    private final EmeraldMercenaryEntity mercenary;
    private final double speed;

    private int approachTicks;
    private int windowTicks;
    private int retryCooldown;

    public ContractRenewWarningGoal(EmeraldMercenaryEntity mercenary, double speed) {
        this.mercenary = mercenary;
        this.speed = speed;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.retryCooldown > 0) {
            this.retryCooldown--;
            return false;
        }

        if (this.mercenary.level().isClientSide()) {
            return false;
        }

        if (this.mercenary.isContractAdmiring()) {
            return false;
        }

        if (this.mercenary.hasSentContractRenewWarning()) {
            return false;
        }

        if (!this.mercenary.isOutOfCombatForHeal()) {
            return false;
        }

        if (this.mercenary.getTarget() != null && this.mercenary.getTarget().isAlive()) {
            return false;
        }

        int remaining = this.mercenary.getContractTicksRemaining();
        if (remaining <= 0 || remaining > WARN_THRESHOLD_TICKS) {
            return false;
        }

        Player owner = this.mercenary.getContractOwnerPlayer();
        if (owner == null || !owner.isAlive()) {
            return false;
        }

        double maxDist = 32.0D;
        if (this.mercenary.distanceToSqr(owner) > maxDist * maxDist) {
            return false;
        }

        if (this.mercenary.hasLineOfSight(owner)) {
            return true;
        }

        Path path = this.mercenary.getNavigation().createPath(owner, 0);
        return path != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.mercenary.level().isClientSide()) {
            return false;
        }

        if (this.mercenary.isContractAdmiring()) {
            return false;
        }

        if (!this.mercenary.isOutOfCombatForHeal()) {
            return false;
        }

        Player owner = this.mercenary.getContractOwnerPlayer();
        if (owner == null || !owner.isAlive()) {
            return false;
        }

        if (this.approachTicks > 0) {
            return true;
        }

        return this.windowTicks > 0;
    }

    @Override
    public void start() {
        this.approachTicks = APPROACH_TIMEOUT_TICKS;
        this.windowTicks = 0;
    }

    @Override
    public void stop() {
        this.approachTicks = 0;
        this.windowTicks = 0;
    }

    @Override
    public void tick() {
        Player owner = this.mercenary.getContractOwnerPlayer();
        if (owner == null || !owner.isAlive()) {
            this.retryCooldown = FAIL_RETRY_COOLDOWN_TICKS;
            this.stop();
            return;
        }

        // Cancel if attacked recently
        if (this.mercenary.getLastHurtByMob() != null && (this.mercenary.tickCount - this.mercenary.getLastHurtByMobTimestamp()) < 10) {
            this.retryCooldown = FAIL_RETRY_COOLDOWN_TICKS;
            this.stop();
            return;
        }

        // If contract was renewed, stop
        if (this.mercenary.getContractTicksRemaining() > WARN_THRESHOLD_TICKS) {
            this.stop();
            return;
        }

        this.mercenary.setTarget(null);
        this.mercenary.setAggressive(false);
        if (this.mercenary.isUsingItem()) {
            this.mercenary.stopUsingItem();
        }

        this.mercenary.getLookControl().setLookAt(owner, 30.0F, this.mercenary.getMaxHeadXRot());

        if (this.windowTicks > 0) {
            this.windowTicks--;

            this.mercenary.getNavigation().stop();

            if (this.mercenary.tickCount % 20 == 0) {
                this.mercenary.sendContractInfo(owner, "Contrato por concluir");
            }

            double cancelDist = 16.0D;
            if (this.mercenary.distanceToSqr(owner) > cancelDist * cancelDist) {
                this.stop();
            }
            return;
        }

        if (this.approachTicks > 0) {
            this.approachTicks--;

            double talkDist = 2.6D;
            if (this.mercenary.distanceToSqr(owner) > talkDist * talkDist) {
                boolean moved = this.mercenary.getNavigation().moveTo(owner, this.speed);
                if (!moved && this.approachTicks < (APPROACH_TIMEOUT_TICKS - 20)) {
                    this.retryCooldown = FAIL_RETRY_COOLDOWN_TICKS;
                    this.stop();
                }
                return;
            }

            this.mercenary.getNavigation().stop();

            if (this.mercenary.hasLineOfSight(owner)) {
                this.mercenary.sendMercenaryMessage(owner, this.mercenary.randomContractRenewWarnMessage());
                this.mercenary.markSentContractRenewWarning();
                this.windowTicks = WINDOW_TICKS;
                this.approachTicks = 0;
                return;
            }

            // Close but no line of sight: give it a moment, then abort and retry later
            if (this.approachTicks <= 0) {
                this.retryCooldown = FAIL_RETRY_COOLDOWN_TICKS;
                this.stop();
            }
        }
    }
}
