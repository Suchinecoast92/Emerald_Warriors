package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.mercenary.MercenaryOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
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

    private int repositionCooldown;

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

        if (this.mob.isNeutralOrder()) {
            LivingEntity lastHurtBy = this.mob.getLastHurtByMob();
            if (lastHurtBy == null || lastHurtBy != target) {
                return false;
            }
            if (this.mob.tickCount - this.mob.getLastHurtByMobTimestamp() > 100) {
                return false;
            }
        }
        return isHoldingCrossbow();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) return false;

        if (this.mob.isNeutralOrder()) {
            LivingEntity lastHurtBy = this.mob.getLastHurtByMob();
            if (lastHurtBy == null || lastHurtBy != target) {
                return false;
            }
            if (this.mob.tickCount - this.mob.getLastHurtByMobTimestamp() > 100) {
                return false;
            }
        }
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
        this.strafeRight = (this.mob.getId() & 1) == 0;
        this.strafeTime = 0;
        this.repositionCooldown = 0;
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
            this.mob.getNavigation().stop();
            this.mob.getMoveControl().strafe(0.0F, 0.0F);
            this.resetCrossbowState();
            return;
        }

        if (this.repositionCooldown > 0) {
            this.repositionCooldown--;
        }

        double distSqr = this.mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
        boolean canSee  = this.mob.getSensing().hasLineOfSight(target);
        boolean repositioningThisTick = false;

        if (canSee) ++this.seeTime; else --this.seeTime;

        if (canSee) {
            repositioningThisTick = this.tryRepositionAroundTarget(target, distSqr);
        }

        // Movement: approach if out of range or can't see; strafe while aiming
        boolean inRange = distSqr <= (double) this.attackRadiusSqr;
        if (!canSee) {
            this.mob.getNavigation().moveTo(target, this.getChaseSpeed(target));
            this.strafeTime = 0;
            this.mob.getMoveControl().strafe(0.0F, 0.0F);
        } else if (repositioningThisTick) {
            this.strafeTime = 0;
            this.mob.getMoveControl().strafe(0.0F, 0.0F);
        } else if (!inRange) {
            this.mob.getNavigation().moveTo(target, this.getChaseSpeed(target));
            this.strafeTime = 0;
            this.mob.getMoveControl().strafe(0.0F, 0.0F);
        } else {
            this.mob.getNavigation().stop();
            if (this.crossbowState == CrossbowState.CHARGED
                    || this.crossbowState == CrossbowState.READY_TO_ATTACK) {
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

        // Asegurar que el cuerpo y la cabeza miren bien al objetivo mientras usa ballesta,
        // para evitar que el modelo quede de lado y las flechas salgan por los costados.
        this.faceTarget(target);

        ItemStack crossbow = this.mob.getMainHandItem();

        switch (this.crossbowState) {
            // ── Wait for cooldown, then start charging ──────────────────
            case UNCHARGED -> {
                if (canSee && inRange) {
                    if (repositioningThisTick) {
                        break;
                    }
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
                if (!repositioningThisTick && --this.attackDelay <= 0) {
                    this.crossbowState = CrossbowState.READY_TO_ATTACK;
                }
            }
            // ── Ready: fire when line of sight is clear ─────────────────
            case READY_TO_ATTACK -> {
                if (canSee && inRange) {
                    if (repositioningThisTick) {
                        break;
                    }
                    InteractionHand hand = ProjectileUtil.getWeaponHoldingHand(this.mob, Items.CROSSBOW);
                    float inaccuracy = this.getInaccuracyByRank();
                    if (this.mob.isFriendlyInLineOfFire(target)) {
                        this.strafeRight = !this.strafeRight;
                        this.mob.getMoveControl().strafe(0.0F, this.strafeRight ? 0.45F : -0.45F);
                        this.crossbowState = CrossbowState.CHARGED;
                        this.attackDelay = 5;
                        break;
                    }
                    ((CrossbowItem) crossbow.getItem()).performShooting(
                            this.mob.level(), this.mob, hand, crossbow,
                            CrossbowItem.MOB_ARROW_POWER, inaccuracy, target);
                    this.mob.onCrossbowAttackPerformed();
                    this.crossbowState = CrossbowState.UNCHARGED;
                }
            }
        }
    }

    private boolean tryRepositionAroundTarget(LivingEntity target, double distSqr) {
        if (target instanceof Player) {
            return false;
        }
        if (this.repositionCooldown > 0) {
            return false;
        }

        if (this.crossbowState == CrossbowState.CHARGING) {
            return false;
        }

        boolean hasHeightAdvantage = this.shouldMaintainHeightAdvantage(target)
                && (this.mob.getY() - target.getY()) >= 2.0D;

        double preferredRadius = 10.5D + (double) (this.mob.getId() % 5) * 0.5D;
        double minRadius = preferredRadius - 1.25D;
        double maxRadius = preferredRadius + 1.25D;

        double dist = Math.sqrt(distSqr);
        boolean badDistance = dist < minRadius || dist > maxRadius;

        if (hasHeightAdvantage && distSqr >= 81.0D) {
            badDistance = false;
        }

        double desiredDeg = (double) (this.mob.getId() * 31 % 360);
        double currentDeg = (double) (Mth.atan2(this.mob.getZ() - target.getZ(), this.mob.getX() - target.getX()) * Mth.RAD_TO_DEG);
        double deltaDeg = (double) Mth.wrapDegrees((float) (desiredDeg - currentDeg));
        boolean badAngle = Math.abs(deltaDeg) > 25.0D;

        if (!badDistance && !badAngle) {
            return false;
        }

        double angleRad = desiredDeg * (double) Mth.DEG_TO_RAD;
        double x = target.getX() + Math.cos(angleRad) * preferredRadius;
        double z = target.getZ() + Math.sin(angleRad) * preferredRadius;

        boolean moved = this.mob.getNavigation().moveTo(x, target.getY(), z, this.getChaseSpeed(target));
        if (moved) {
            this.repositionCooldown = 10 + this.mob.getRandom().nextInt(10);
        }
        return moved;
    }

    private double getChaseSpeed(LivingEntity target) {
        double speed = this.speedModifier;
        if (target instanceof Player) {
            speed = Math.max(speed, 1.1D);
        }
        return speed;
    }

    private boolean isTooFarFromAnchor() {
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
            case PATROL -> {
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

    /**
     * Alinea inmediatamente yaw/pitch del mercenario con el objetivo actual.
     */
    private void faceTarget(LivingEntity target) {
        double dx = target.getX() - this.mob.getX();
        double dz = target.getZ() - this.mob.getZ();
        double dy = target.getEyeY() - this.mob.getEyeY();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(dy, horizontalDist) * Mth.RAD_TO_DEG);

        this.mob.setYRot(yaw);
        this.mob.yBodyRot = yaw;
        this.mob.yHeadRot = yaw;
        this.mob.setXRot(pitch);
    }

    private void resetCrossbowState() {
        this.mob.setAggressive(false);
        this.seeTime    = 0;
        this.strafeTime = 0;
        this.attackDelay = 0;
        this.repositionCooldown = 0;
        this.crossbowState = CrossbowState.UNCHARGED;
        this.mob.getNavigation().stop();
        this.mob.getMoveControl().strafe(0.0F, 0.0F);
        this.mob.stopUsingItem();
        this.mob.setChargingCrossbow(false);
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
