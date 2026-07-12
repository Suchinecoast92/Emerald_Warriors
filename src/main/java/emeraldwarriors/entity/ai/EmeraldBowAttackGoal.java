package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.mercenary.MercenaryOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;

import java.util.EnumSet;

/**
 * Bow combat modeled on vanilla {@code RangedBowAttackGoal} (skeleton):
 * look via LookControl, hold and shoot when in range with LOS,
 * especially from high ground instead of pathing downhill.
 */
public class EmeraldBowAttackGoal extends Goal {

    private final EmeraldMercenaryEntity mob;
    private final double speedModifier;
    private final int attackIntervalMin;
    private final float attackRadiusSqr;

    private int attackTime;
    private int seeTime;
    private int useTime;

    public EmeraldBowAttackGoal(EmeraldMercenaryEntity mob, double speedModifier, int attackIntervalMin, float attackRadius) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.attackIntervalMin = attackIntervalMin;
        this.attackRadiusSqr = attackRadius * attackRadius;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }

        if (CombatTargets.isEnderman(target)) {
            return false;
        }

        if (this.mob.isNeutralOrder() && !this.mob.isInDisciplineAggro()) {
            LivingEntity lastHurtBy = this.mob.getLastHurtByMob();
            boolean validNeutralTarget = (lastHurtBy != null
                    && lastHurtBy == target
                    && this.mob.tickCount - this.mob.getLastHurtByMobTimestamp() <= 200)
                    || this.mob.isBrotherhoodAssistTarget(target);
            if (!validNeutralTarget) {
                return false;
            }
        }
        return this.isHoldingBow();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }

        if (CombatTargets.isEnderman(target)) {
            return false;
        }

        if (this.mob.isNeutralOrder() && !this.mob.isInDisciplineAggro()) {
            LivingEntity lastHurtBy = this.mob.getLastHurtByMob();
            boolean validNeutralTarget = (lastHurtBy != null
                    && lastHurtBy == target
                    && this.mob.tickCount - this.mob.getLastHurtByMobTimestamp() <= 200)
                    || this.mob.isBrotherhoodAssistTarget(target);
            if (!validNeutralTarget) {
                return false;
            }
        }
        return this.isHoldingBow();
    }

    private boolean isHoldingBow() {
        return this.mob.getMainHandItem().getItem() instanceof BowItem;
    }

    @Override
    public void start() {
        super.start();
        this.mob.setAggressive(true);
    }

    @Override
    public void stop() {
        super.stop();
        this.mob.setAggressive(false);
        this.seeTime = 0;
        this.attackTime = 0;
        this.useTime = 0;
        this.mob.getMoveControl().strafe(0.0F, 0.0F);
        this.mob.stopUsingItem();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            this.mob.getMoveControl().strafe(0.0F, 0.0F);
            return;
        }

        if (this.isTooFarFromAnchor()) {
            this.mob.setTarget(null);
            this.mob.getEffectiveNavigation().stop();
            this.mob.getMoveControl().strafe(0.0F, 0.0F);
            if (this.mob.isUsingItem()) {
                this.mob.stopUsingItem();
            }
            return;
        }

        double distSqr = this.mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
        boolean canSee = this.mob.getSensing().hasLineOfSight(target);
        boolean inRange = distSqr <= (double) this.attackRadiusSqr;
        boolean holdHighGround = CombatTactics.canHoldGroundAndShoot(this.mob, target, this.attackRadiusSqr);
        boolean isUsingBow = this.mob.isUsingItem() && this.mob.getUseItem().getItem() instanceof BowItem;

        if (canSee) {
            ++this.seeTime;
        } else {
            --this.seeTime;
        }

        // Vanilla skeleton: lookControl aims head/body (and therefore bow arms) at the target.
        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (holdHighGround || (canSee && inRange)) {
            // Clear shot: stay put and face the target (no strafe — it rotates the body away).
            this.mob.getEffectiveNavigation().stop();
            this.mob.getMoveControl().strafe(0.0F, 0.0F);
        } else {
            this.mob.getEffectiveNavigation().moveTo(target, this.getChaseSpeed(target));
            this.mob.getMoveControl().strafe(0.0F, 0.0F);
        }

        if (this.mob.isUsingItem()) {
            if (!canSee && this.seeTime < -20) {
                this.mob.stopUsingItem();
                this.useTime = 0;
            } else {
                this.useTime++;
                if (this.useTime >= 20 && canSee && target.isAlive()
                        && (inRange || holdHighGround)) {
                    if (this.mob.isFriendlyInLineOfFire(target)) {
                        // Wait for a clear shot instead of strafing (strafe breaks bow pose).
                    } else {
                        this.mob.stopUsingItem();
                        this.mob.performRangedAttack(target, BowItem.getPowerForTime(this.useTime));
                        this.attackTime = this.attackIntervalMin;
                        this.useTime = 0;
                    }
                }
            }
        } else {
            if (this.attackTime > 0) {
                this.attackTime--;
            }

            if (this.attackTime <= 0 && canSee && (inRange || holdHighGround)) {
                CombatTactics.snapAimAt(this.mob, target);
                this.mob.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
                this.useTime = 0;
            }
        }
    }

    private double getChaseSpeed(LivingEntity target) {
        double speed = this.speedModifier;
        if (target instanceof Player || this.mob.isTacticalAttackTarget(target)) {
            speed = Math.max(speed, 1.1D);
        }
        return this.mob.resolveNavigationSpeed(speed);
    }

    private boolean isTooFarFromAnchor() {
        if (this.mob.shouldIgnoreChaseAnchor() || this.mob.isInDisciplineAggro()) {
            return false;
        }
        int maxChase = this.mob.getRank().getMaxChaseFromAnchor();
        double raidMultiplier = this.mob.isRaidActive() ? 3.0 : 1.0;
        LivingEntity target = this.mob.getTarget();
        double playerMultiplier = target instanceof Player ? 2.0 : 1.0;
        double effectiveMaxChase = maxChase * raidMultiplier * playerMultiplier;
        double maxChaseSqr = effectiveMaxChase * effectiveMaxChase;

        MercenaryOrder order = this.mob.getCurrentOrder();
        switch (order) {
            case GUARD -> {
                BlockPos guard = this.mob.getGuardPos();
                if (guard != null) {
                    double guardLimit = (this.mob.getRank().getGuardRadius() + 4.0) * raidMultiplier;
                    return this.mob.distanceToSqr(guard.getX() + 0.5, guard.getY(), guard.getZ() + 0.5) > guardLimit * guardLimit;
                }
                return false;
            }
            case PATROL, NEUTRAL -> {
                BlockPos center = this.mob.getPatrolCenter();
                if (center != null) {
                    double dist = this.mob.distanceToSqr(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
                    return dist > maxChaseSqr;
                }
            }
            default -> {
                LivingEntity owner = this.mob.getOwner();
                if (owner != null) {
                    return this.mob.distanceToSqr(owner) > maxChaseSqr;
                }
            }
        }
        return false;
    }
}
