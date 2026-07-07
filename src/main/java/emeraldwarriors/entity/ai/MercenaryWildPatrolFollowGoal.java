package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.mercenary.MercenaryOrder;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Salvajes sin dueño: acompañantes a pie que siguen al líder de una patrulla montada.
 */
public class MercenaryWildPatrolFollowGoal extends Goal {

    private static final double FOLLOW_START_DIST_SQR = 10.0D * 10.0D;
    private static final double FOLLOW_STOP_DIST_SQR = 6.0D * 6.0D;

    private final EmeraldMercenaryEntity mercenary;
    private EmeraldMercenaryEntity leader;
    private int repathCooldown;

    public MercenaryWildPatrolFollowGoal(EmeraldMercenaryEntity mercenary) {
        this.mercenary = mercenary;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.mercenary.getOwnerUuid() != null) {
            return false;
        }
        if (this.mercenary.isPassenger()) {
            return false;
        }
        if (this.mercenary.getCurrentOrder() != MercenaryOrder.PATROL) {
            return false;
        }
        UUID leaderId = this.mercenary.getWildPatrolLeaderUuid();
        if (leaderId == null) {
            return false;
        }
        if (this.mercenary.getTarget() != null && this.mercenary.getTarget().isAlive()) {
            return false;
        }
        this.leader = this.findLeader(leaderId);
        if (this.leader == null || !this.leader.isAlive()) {
            return false;
        }
        return this.mercenary.distanceToSqr(this.leader) > FOLLOW_STOP_DIST_SQR;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.leader == null || !this.leader.isAlive()) {
            return false;
        }
        if (this.mercenary.getCurrentOrder() != MercenaryOrder.PATROL) {
            return false;
        }
        if (this.mercenary.getTarget() != null && this.mercenary.getTarget().isAlive()) {
            return false;
        }
        return this.mercenary.distanceToSqr(this.leader) > FOLLOW_STOP_DIST_SQR * 0.25D;
    }

    @Override
    public void start() {
        this.repathCooldown = 0;
        this.moveTowardLeader();
    }

    @Override
    public void tick() {
        if (this.leader == null) {
            return;
        }
        if (--this.repathCooldown <= 0) {
            this.repathCooldown = 15;
            if (this.mercenary.distanceToSqr(this.leader) > FOLLOW_START_DIST_SQR
                    || this.mercenary.getEffectiveNavigation().isDone()) {
                this.moveTowardLeader();
            }
        }
    }

    @Override
    public void stop() {
        this.leader = null;
        this.mercenary.getEffectiveNavigation().stop();
    }

    private void moveTowardLeader() {
        if (this.leader == null) {
            return;
        }
        double speed = this.mercenary.resolveNavigationSpeed(1.0D);
        this.mercenary.getEffectiveNavigation().moveTo(this.leader, speed);
    }

    private EmeraldMercenaryEntity findLeader(UUID leaderId) {
        return this.mercenary.level().getEntitiesOfClass(
                EmeraldMercenaryEntity.class,
                this.mercenary.getBoundingBox().inflate(48.0D),
                e -> e.isAlive() && leaderId.equals(e.getUUID())
        ).stream().findFirst().orElse(null);
    }
}
