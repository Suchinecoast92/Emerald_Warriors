package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.EnumSet;

/**
 * Vanilla-style crossbow attack goal for mercenaries.
 * Mirrors the vanilla RangedCrossbowAttackGoal logic but works with PathfinderMob
 * instead of requiring Monster.
 *
 * State machine (matches vanilla pillager):
 *   UNCHARGED      → startUsingItem() + setChargingCrossbow(true) → CHARGING
 *   CHARGING       → getTicksUsingItem() >= getChargeDuration() → releaseUsingItem() → CHARGED
 *   CHARGED        → aiming delay (20-40 ticks, vanilla) → READY_TO_ATTACK
 *   READY_TO_ATTACK → CrossbowItem.performShooting() → UNCHARGED
 *
 * Arrow consumption happens via releaseUsingItem() → CrossbowItem.releaseUsing()
 * → tryLoadProjectiles() → mob.getProjectile() (overridden to use mercenaryInventory).
 */
public class EmeraldCrossbowAttackGoal extends Goal {

    private enum CrossbowState { UNCHARGED, CHARGING, CHARGED, READY_TO_ATTACK }

    private final EmeraldMercenaryEntity mob;
    private final double speedModifier;
    private final float attackRadiusSqr;

    private CrossbowState crossbowState = CrossbowState.UNCHARGED;
    private int attackDelay = 0;
    private int seeTime     = 0;

    private boolean strafeRight;
    private int strafeTime = 0;

    public EmeraldCrossbowAttackGoal(EmeraldMercenaryEntity mob, double speedModifier,
                                     int attackIntervalMin, float attackRadius) {
        this.mob = mob;
        this.speedModifier   = speedModifier;
        this.attackRadiusSqr = attackRadius * attackRadius;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.mob.isNeutralOrder()) return false;
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        return isHoldingCrossbow();
    }

    @Override
    public boolean canContinueToUse() {
        if (this.mob.isNeutralOrder()) return false;
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        return isHoldingCrossbow();
    }

    private boolean isHoldingCrossbow() {
        return this.mob.getMainHandItem().getItem() instanceof CrossbowItem;
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
        this.mob.setAggressive(false);
        this.seeTime    = 0;
        this.strafeTime = 0;
        this.crossbowState = CrossbowState.UNCHARGED;
        this.mob.getMoveControl().strafe(0.0F, 0.0F);
        this.mob.stopUsingItem();
        this.mob.setChargingCrossbow(false);
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

        double distSqr = this.mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
        boolean canSee  = this.mob.getSensing().hasLineOfSight(target);

        if (canSee) ++this.seeTime; else --this.seeTime;

        // Movement: approach if out of range or can't see; strafe while aiming
        boolean inRange = distSqr <= (double) this.attackRadiusSqr;
        if (!inRange || !canSee) {
            this.mob.getNavigation().moveTo(target, this.speedModifier);
            this.strafeTime = 0;
            this.mob.getMoveControl().strafe(0.0F, 0.0F);
        } else {
            this.mob.getNavigation().stop();
            if (this.crossbowState == CrossbowState.CHARGED
                    || this.crossbowState == CrossbowState.READY_TO_ATTACK) {
                // SENTINEL+ Tactical Height Advantage — reduce strafe when elevated 2+ blocks
                if (this.shouldMaintainHeightAdvantage(target)) {
                    double heightDiff = this.mob.getY() - target.getY();
                    if (heightDiff >= 2.0) {
                        this.strafeTime = Math.max(0, this.strafeTime - 1);
                    }
                }
                this.strafeTime++;
                if (this.strafeTime >= 40) {
                    this.strafeTime = 0;
                    if (this.mob.getRandom().nextFloat() < 0.5F) this.strafeRight = !this.strafeRight;
                }
                this.mob.getMoveControl().strafe(0.0F, this.strafeRight ? 0.3F : -0.3F);
            } else {
                this.strafeTime = 0;
                this.mob.getMoveControl().strafe(0.0F, 0.0F);
            }
        }

        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        ItemStack crossbow = this.mob.getMainHandItem();

        switch (this.crossbowState) {
            // ── Wait for cooldown, then start charging ──────────────────
            case UNCHARGED -> {
                if (canSee && inRange) {
                    InteractionHand hand = ProjectileUtil.getWeaponHoldingHand(this.mob, Items.CROSSBOW);
                    this.mob.startUsingItem(hand);
                    this.mob.setChargingCrossbow(true);
                    this.crossbowState = CrossbowState.CHARGING;
                }
            }
            // ── Charging: wait until getTicksUsingItem >= chargeDuration ──
            case CHARGING -> {
                if (!this.mob.isUsingItem()) {
                    this.mob.setChargingCrossbow(false);
                    this.crossbowState = CrossbowState.UNCHARGED;
                    break;
                }
                int ticksUsing    = this.mob.getTicksUsingItem();
                int chargeDuration = CrossbowItem.getChargeDuration(crossbow, this.mob);
                if (ticksUsing >= chargeDuration) {
                    // Vanilla: releaseUsingItem() calls CrossbowItem.releaseUsing()
                    // which calls tryLoadProjectiles() → mob.getProjectile() → loads arrow
                    this.mob.releaseUsingItem();
                    // For useOnRelease items (crossbow), releaseUsingItem may skip clearing
                    // the use state — explicitly stop to ensure clean transition
                    if (this.mob.isUsingItem()) this.mob.stopUsingItem();
                    this.mob.setChargingCrossbow(false);
                    if (CrossbowItem.isCharged(crossbow)) {
                        this.crossbowState = CrossbowState.CHARGED;
                        // Vanilla aiming delay: 20 + random(0..19) ticks
                        this.attackDelay = 20 + this.mob.getRandom().nextInt(20);
                    } else {
                        // Failed to load (no arrows) — stay uncharged
                        this.crossbowState = CrossbowState.UNCHARGED;
                    }
                }
            }
            // ── Charged: aiming delay before firing (vanilla behavior) ──
            case CHARGED -> {
                if (--this.attackDelay <= 0) {
                    this.crossbowState = CrossbowState.READY_TO_ATTACK;
                }
            }
            // ── Ready: fire when line of sight is clear ─────────────────
            case READY_TO_ATTACK -> {
                if (canSee && inRange) {
                    InteractionHand hand = ProjectileUtil.getWeaponHoldingHand(this.mob, Items.CROSSBOW);
                    float inaccuracy = this.getInaccuracyByRank();
                    ((CrossbowItem) crossbow.getItem()).performShooting(
                            this.mob.level(), this.mob, hand, crossbow,
                            CrossbowItem.MOB_ARROW_POWER, inaccuracy, target);
                    this.mob.onCrossbowAttackPerformed();
                    this.crossbowState = CrossbowState.UNCHARGED;
                }
            }
        }
    }

    private boolean shouldMaintainHeightAdvantage(LivingEntity target) {
        return this.mob.getRank().ordinal() >= 2; // SENTINEL=2, VETERAN=3, ANCIENT_GUARD=4
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
