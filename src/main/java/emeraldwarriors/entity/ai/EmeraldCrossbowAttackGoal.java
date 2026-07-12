package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.mercenary.MercenaryOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;

/**
 * Crossbow combat modeled on vanilla pillager {@code RangedCrossbowAttackGoal}:
 * look via LookControl, hold high ground with clear LOS instead of descending.
 */
public class EmeraldCrossbowAttackGoal extends Goal {

    private enum CrossbowState { UNCHARGED, CHARGING, CHARGED, READY_TO_ATTACK }

    private final EmeraldMercenaryEntity mob;
    private final double speedModifier;
    private final float attackRadiusSqr;

    private CrossbowState crossbowState = CrossbowState.UNCHARGED;
    private int attackDelay = 0;
    private int seeTime     = 0;

    public EmeraldCrossbowAttackGoal(EmeraldMercenaryEntity mob, double speedModifier,
                                     int attackIntervalMin, float attackRadius) {
        this.mob = mob;
        this.speedModifier   = speedModifier;
        this.attackRadiusSqr = attackRadius * attackRadius;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) return false;

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
        return isHoldingCrossbow();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) return false;

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
        return isHoldingCrossbow();
    }

    private boolean isHoldingCrossbow() {
        return this.mob.getMainHandItem().getItem() instanceof CrossbowItem;
    }

    private InteractionHand getCrossbowHand() {
        if (this.mob.getMainHandItem().getItem() instanceof CrossbowItem) {
            return InteractionHand.MAIN_HAND;
        }
        if (this.mob.getOffhandItem().getItem() instanceof CrossbowItem) {
            return InteractionHand.OFF_HAND;
        }
        return InteractionHand.MAIN_HAND;
    }

    @Override
    public void start() {
        super.start();
        this.mob.setAggressive(true);
        this.crossbowState = CrossbowState.UNCHARGED;
        this.attackDelay = 0;
        this.seeTime     = 0;
    }

    @Override
    public void stop() {
        super.stop();
        this.resetCrossbowState();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            this.resetCrossbowState();
            return;
        }

        if (this.isTooFarFromAnchor()) {
            this.mob.setTarget(null);
            this.mob.getEffectiveNavigation().stop();
            this.mob.getMoveControl().strafe(0.0F, 0.0F);
            this.resetCrossbowState();
            return;
        }

        double distSqr = this.mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
        boolean canSee  = this.mob.getSensing().hasLineOfSight(target);
        boolean inRange = distSqr <= (double) this.attackRadiusSqr;
        boolean holdHighGround = CombatTactics.canHoldGroundAndShoot(this.mob, target, this.attackRadiusSqr);
        boolean canShoot = inRange || holdHighGround;

        if (canSee) ++this.seeTime; else --this.seeTime;

        // Vanilla pillager: LookControl aims head/body/crossbow at the target.
        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (!canShoot && this.crossbowState != CrossbowState.UNCHARGED) {
            this.abortCrossbowCharge();
        }

        if (holdHighGround || (canSee && inRange)) {
            this.mob.getEffectiveNavigation().stop();
            this.mob.getMoveControl().strafe(0.0F, 0.0F);
        } else {
            this.mob.getEffectiveNavigation().moveTo(target, this.getChaseSpeed(target));
            this.mob.getMoveControl().strafe(0.0F, 0.0F);
        }

        ItemStack crossbow = this.mob.getMainHandItem();

        switch (this.crossbowState) {
            case UNCHARGED -> {
                if (this.attackDelay <= 0 && canShoot && canSee) {
                    CombatTactics.snapAimAt(this.mob, target);
                    InteractionHand hand = this.getCrossbowHand();
                    this.mob.startUsingItem(hand);
                    this.mob.setChargingCrossbow(true);
                    this.crossbowState = CrossbowState.CHARGING;
                }
            }
            case CHARGING -> {
                if (!this.mob.isUsingItem()) {
                    this.mob.setChargingCrossbow(false);
                    this.crossbowState = CrossbowState.UNCHARGED;
                    break;
                }
                int ticksUsing    = this.mob.getTicksUsingItem();
                int chargeDuration = CrossbowItem.getChargeDuration(crossbow, this.mob);
                if (ticksUsing >= chargeDuration) {
                    this.mob.releaseUsingItem();
                    if (this.mob.isUsingItem()) this.mob.stopUsingItem();
                    this.mob.setChargingCrossbow(false);
                    if (CrossbowItem.isCharged(crossbow)) {
                        this.crossbowState = CrossbowState.CHARGED;
                        this.attackDelay = 20 + this.mob.getRandom().nextInt(20);
                    } else {
                        this.crossbowState = CrossbowState.UNCHARGED;
                    }
                }
            }
            case CHARGED -> {
                if (--this.attackDelay <= 0) {
                    this.crossbowState = CrossbowState.READY_TO_ATTACK;
                }
            }
            case READY_TO_ATTACK -> {
                if (this.attackDelay <= 0 && canShoot && canSee) {
                    InteractionHand hand = this.getCrossbowHand();
                    float inaccuracy = this.getInaccuracyByRank();
                    if (this.mob.isFriendlyInLineOfFire(target)) {
                        this.crossbowState = CrossbowState.CHARGED;
                        this.attackDelay = 5;
                        break;
                    }

                    int damageBefore = crossbow.isDamageableItem() ? crossbow.getDamageValue() : 0;
                    ((CrossbowItem) crossbow.getItem()).performShooting(
                            this.mob.level(), this.mob, hand, crossbow,
                            CrossbowItem.MOB_ARROW_POWER, inaccuracy, target);

                    if (this.mob.getOwnerUuid() == null && crossbow.isDamageableItem()) {
                        crossbow.setDamageValue(damageBefore);
                    }
                    this.mob.onCrossbowAttackPerformed();
                    this.crossbowState = CrossbowState.UNCHARGED;
                }
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

    private void abortCrossbowCharge() {
        if (this.mob.isUsingItem()) {
            this.mob.stopUsingItem();
        }
        this.mob.setChargingCrossbow(false);
        this.crossbowState = CrossbowState.UNCHARGED;
        this.attackDelay = 0;
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

    private void resetCrossbowState() {
        this.mob.setAggressive(false);
        this.seeTime    = 0;
        this.attackDelay = 0;
        this.crossbowState = CrossbowState.UNCHARGED;
        this.mob.getEffectiveNavigation().stop();
        this.mob.getMoveControl().strafe(0.0F, 0.0F);
        this.mob.stopUsingItem();
        this.mob.setChargingCrossbow(false);
    }

    private float getInaccuracyByRank() {
        if (this.mob.level() instanceof ServerLevel sl) {
            float base = 14 - sl.getDifficulty().getId() * 4;
            return switch (this.mob.getRank()) {
                case RECRUIT      -> base;
                case SOLDIER      -> base * 0.85f;
                case SENTINEL     -> base * 0.70f;
                case VETERAN      -> base * 0.55f;
                case ANCIENT_GUARD -> base * 0.40f;
            };
        }
        return 8.0f;
    }
}
