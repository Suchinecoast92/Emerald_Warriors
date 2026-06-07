package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.mercenary.MercenaryOrder;
import emeraldwarriors.mercenary.MercenaryRole;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.pathfinder.Path;

import java.util.EnumSet;

/**
 * Custom melee attack goal that always triggers the swing animation.
 * Based on vanilla MeleeAttackGoal but calls swing() explicitly before doHurtTarget().
 * Only activates when the mercenary's current role is NOT ARCHER.
 */
public class EmeraldMeleeAttackGoal extends Goal {
    protected final EmeraldMercenaryEntity mob;
    private final double speedModifier;
    private final boolean followingTargetEvenIfNotSeen;
    private Path path;
    private double pathedTargetX;
    private double pathedTargetY;
    private double pathedTargetZ;
    private int ticksUntilNextPathRecalculation;
    private int ticksUntilNextAttack;
    private long lastCanUseCheck;
    private int flankAngleSeed;
    private boolean strafeRight;
    private int strafeTicks;
    private int shieldDropWindupTicks;

    public EmeraldMeleeAttackGoal(EmeraldMercenaryEntity mob, double speedModifier, boolean followingTargetEvenIfNotSeen) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.followingTargetEvenIfNotSeen = followingTargetEvenIfNotSeen;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        long gameTime = this.mob.level().getGameTime();
        if (gameTime - this.lastCanUseCheck < 4L) {
            return false;
        }
        this.lastCanUseCheck = gameTime;

        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
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

        this.path = this.mob.getNavigation().createPath(target, 0);
        if (this.path != null) {
            return true;
        }

        double attackReachSqr = this.getAttackReachSqr(target);
        return this.isWithinAttackRange(target, attackReachSqr);
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
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
        if (!this.followingTargetEvenIfNotSeen) {
            return !this.mob.getNavigation().isDone();
        }

        // Límite de persecución: no alejarse demasiado del ancla
        if (isTooFarFromAnchor()) {
            return false;
        }

        return !(target instanceof Player) || !target.isSpectator() && !((Player) target).isCreative();
    }

    private boolean isTooFarFromAnchor() {
        if (this.mob.isInDisciplineAggro()) {
            return false;
        }
        int maxChase = this.mob.getRank().getMaxChaseFromAnchor();
        // Triple chase distance during active raids
        double raidMultiplier = this.mob.isRaidActive() ? 3.0 : 1.0;

        LivingEntity target = this.mob.getTarget();
        double playerMultiplier = target instanceof Player ? 2.0 : 1.0;

        double effectiveMaxChase = maxChase * raidMultiplier;
        effectiveMaxChase *= playerMultiplier;
        double maxChaseSqr = effectiveMaxChase * effectiveMaxChase;

        MercenaryOrder order = this.mob.getCurrentOrder();
        switch (order) {
            case GUARD -> {
                // En GUARD: perseguir libremente dentro del radio de guardia; GuardPositionGoal devuelve al punto.
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
                // FOLLOW: ancla = dueño
                LivingEntity owner = this.mob.getOwner();
                if (owner != null) {
                    return this.mob.distanceToSqr(owner) > maxChaseSqr;
                }
            }
        }
        return false;
    }

    @Override
    public void start() {
        LivingEntity target = this.mob.getTarget();
        this.mob.getNavigation().moveTo(this.path, target != null ? this.getChaseSpeed(target) : this.speedModifier);
        this.mob.setAggressive(true);
        this.ticksUntilNextPathRecalculation = 0;
        this.ticksUntilNextAttack = 0;
        this.flankAngleSeed = this.mob.getRandom().nextInt(360);
        this.strafeRight = this.mob.getRandom().nextBoolean();
        this.strafeTicks = 0;
        this.shieldDropWindupTicks = 0;
    }

    @Override
    public void stop() {
        LivingEntity target = this.mob.getTarget();
        if (!EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target)) {
            this.mob.setTarget(null);
        }
        this.mob.setAggressive(false);
        this.mob.getNavigation().stop();
        this.shieldDropWindupTicks = 0;
        this.mob.getMoveControl().strafe(0.0F, 0.0F);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }

        boolean isPlayerTarget = target instanceof Player;
        double chaseSpeed = this.getChaseSpeed(target);

        if (this.isTooFarFromAnchor()) {
            this.mob.setTarget(null);
            this.mob.getNavigation().stop();
            return;
        }

        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double distanceSqr = this.mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
        this.ticksUntilNextPathRecalculation = Math.max(this.ticksUntilNextPathRecalculation - 1, 0);

        // Contra creepers: no acercarse más allá del alcance de ataque para evitar
        // que la explosión lance al mercenario por los aires.
        boolean isCreeper = target instanceof Creeper;
        double attackReachSqr = this.getAttackReachSqr(target);
        boolean closeEnoughToCreeper = isCreeper && this.horizontalDistanceSqr(target) <= attackReachSqr;

        if ((!isCreeper || !closeEnoughToCreeper)
                && (this.followingTargetEvenIfNotSeen || this.mob.getSensing().hasLineOfSight(target))
                && this.ticksUntilNextPathRecalculation <= 0
                && (this.pathedTargetX == 0.0D && this.pathedTargetY == 0.0D && this.pathedTargetZ == 0.0D
                || target.distanceToSqr(this.pathedTargetX, this.pathedTargetY, this.pathedTargetZ) >= (isPlayerTarget ? 0.25D : 1.0D)
                || this.mob.getRandom().nextFloat() < (isPlayerTarget ? 0.12F : 0.08F))) {
            this.pathedTargetX = target.getX();
            this.pathedTargetY = target.getY();
            this.pathedTargetZ = target.getZ();
            this.ticksUntilNextPathRecalculation = isPlayerTarget ? 1 + this.mob.getRandom().nextInt(2) : 4 + this.mob.getRandom().nextInt(7);

            if (distanceSqr > 1024.0D) {
                this.ticksUntilNextPathRecalculation += 10;
            } else if (distanceSqr > 256.0D) {
                this.ticksUntilNextPathRecalculation += 5;
            }

            boolean movedWithOffset = this.shouldMoveToTargetWithOffset(target, distanceSqr, attackReachSqr)
                    && this.moveToTargetWithOffset(target, distanceSqr, attackReachSqr, chaseSpeed);
            if (!movedWithOffset) {
                double approachSpeed = chaseSpeed * (0.9D + this.mob.getRandom().nextDouble() * 0.25D);
                if (CombatTactics.shouldPreserveHeightInGuard(this.mob, target)) {
                    this.mob.getNavigation().moveTo(target.getX(), CombatTactics.getRangedNavigationY(this.mob, target),
                            target.getZ(), approachSpeed);
                } else if (!this.mob.getNavigation().moveTo(target, approachSpeed)) {
                    this.ticksUntilNextPathRecalculation += isPlayerTarget ? 2 : 15;
                }
            }
            this.ticksUntilNextPathRecalculation = this.adjustedTickDelay(this.ticksUntilNextPathRecalculation);
        } else if (closeEnoughToCreeper) {
            // Ya estamos lo suficientemente cerca del creeper, dejar de acercarse
            this.mob.getNavigation().stop();
        }

        this.ticksUntilNextAttack = Math.max(this.ticksUntilNextAttack - 1, 0);

        if (!closeEnoughToCreeper && !this.isWithinAttackRange(target, attackReachSqr) && this.mob.getNavigation().isDone()) {
            CombatTactics.moveToTargetPreservingHeight(this.mob, target, chaseSpeed);
        }

        if (this.shieldDropWindupTicks > 0) {
            this.mob.getMoveControl().strafe(0.0F, 0.0F);
        } else if (this.isWithinAttackRange(target, attackReachSqr) && this.ticksUntilNextAttack > 0) {
            if (this.strafeTicks <= 0) {
                this.strafeRight = this.mob.getRandom().nextBoolean();
                this.strafeTicks = 8 + this.mob.getRandom().nextInt(12);
            }
            this.strafeTicks--;
            float sideways = this.strafeRight ? 0.35F : -0.35F;
            this.mob.getMoveControl().strafe(sideways, 0.0F);
        } else {
            this.strafeTicks = 0;
            this.mob.getMoveControl().strafe(0.0F, 0.0F);
        }

        this.checkAndPerformAttack(target, distanceSqr);
    }

    private double horizontalDistanceSqr(LivingEntity target) {
        double dx = target.getX() - this.mob.getX();
        double dz = target.getZ() - this.mob.getZ();
        return dx * dx + dz * dz;
    }

    private boolean isWithinAttackRange(LivingEntity target, double attackReachSqr) {
        double dy = Math.abs(target.getY() - this.mob.getY());
        if (dy > 2.0D) {
            return false;
        }
        return this.horizontalDistanceSqr(target) <= attackReachSqr + 0.25D;
    }

    private boolean shouldMoveToTargetWithOffset(LivingEntity target, double distanceSqr, double attackReachSqr) {
        if (distanceSqr > 400.0D) {
            return false;
        }
        if (distanceSqr <= attackReachSqr + 1.0D) {
            return false;
        }

        boolean alliesNearby = !this.mob.level().getEntitiesOfClass(
                EmeraldMercenaryEntity.class,
                this.mob.getBoundingBox().inflate(3.0D),
                m -> m != this.mob && m.isAlive() && m.getTarget() == target
        ).isEmpty();
        if (alliesNearby) {
            return true;
        }
        return distanceSqr < 144.0D && this.mob.getRandom().nextFloat() < 0.45F;
    }

    private boolean moveToTargetWithOffset(LivingEntity target, double distanceSqr, double attackReachSqr, double speed) {
        if (distanceSqr > 400.0D) {
            return false;
        }

        this.flankAngleSeed += 40 + this.mob.getRandom().nextInt(80);
        double radius = 1.0D + this.mob.getRandom().nextDouble() * 1.2D;
        double dy = target.getY() - this.mob.getY();
        double maxHorizontalSqr = Math.max(attackReachSqr - dy * dy, 0.0D);
        double maxRadius = Math.sqrt(maxHorizontalSqr) * 0.9D;
        if (maxRadius <= 0.0D) {
            return false;
        }
        radius = Math.min(radius, maxRadius);
        double angle = (double) this.flankAngleSeed * 0.017453292519943295D;
        double x = target.getX() + Math.cos(angle) * radius;
        double z = target.getZ() + Math.sin(angle) * radius;
        double y = CombatTactics.shouldPreserveHeightInGuard(this.mob, target)
                ? CombatTactics.getRangedNavigationY(this.mob, target)
                : target.getY();

        return this.mob.getNavigation().moveTo(x, y, z, speed);
    }

    private double getChaseSpeed(LivingEntity target) {
        double speed = this.speedModifier;
        if (target instanceof Player) {
            speed = Math.max(speed, 1.1D);
        }
        return speed;
    }

    protected void checkAndPerformAttack(LivingEntity target, double distanceSqr) {
        double attackReachSqr = this.getAttackReachSqr(target);

        if (this.shieldDropWindupTicks > 0) {
            this.shieldDropWindupTicks--;
            return;
        }

        if (this.mob.isUsingItem()) {
            if (!this.isWithinAttackRange(target, attackReachSqr) || this.ticksUntilNextAttack > 0) {
                return;
            }
            if (this.mob.getRandom().nextFloat() < 0.65F) {
                this.beginMeleeStrike();
            }
            return;
        }

        if (this.isWithinAttackRange(target, attackReachSqr) && this.ticksUntilNextAttack <= 0) {
            this.resetAttackCooldown();
            this.mob.swing(InteractionHand.MAIN_HAND);
            if (this.mob.level() instanceof ServerLevel serverLevel) {
                this.mob.doHurtTarget(serverLevel, target);
            }
            this.mob.suppressReactiveShieldForMelee(10);
        }
    }

    private void beginMeleeStrike() {
        this.mob.stopUsingItem();
        this.shieldDropWindupTicks = 3 + this.mob.getRandom().nextInt(3);
        this.mob.suppressReactiveShieldForMelee(this.shieldDropWindupTicks + 12);
    }

    protected void resetAttackCooldown() {
        this.ticksUntilNextAttack = this.adjustedTickDelay(this.getAttackCooldownTicks());
    }

    private int getAttackCooldownTicks() {
        ItemStack main = this.mob.getMainHandItem();
        if (!main.is(Items.MACE)) {
            return 20;
        }

        double attackSpeed = this.mob.getAttributeValue(Attributes.ATTACK_SPEED);
        if (attackSpeed <= 0.0D) {
            return 20;
        }

        int vanillaTicks = (int) Math.ceil(20.0D / attackSpeed);
        return Math.max(20, vanillaTicks);
    }

    protected double getAttackReachSqr(LivingEntity target) {
        double base = (double) (this.mob.getBbWidth() * 2.0F * this.mob.getBbWidth() * 2.0F + target.getBbWidth());

        // Contra creepers, permitimos un "alcance" mayor para que el mercenario
        // pueda golpear desde algo más lejos sin tener que pegarse a su hitbox.
        // Esto mantiene la física de knockback 100 % vanilla (LivingEntity.knockback),
        // cambiando solo la distancia a la que se coloca.
        double reach = base;
        if (target instanceof Creeper) {
            reach = base * 4.0D; // ~doble alcance lineal (sqrt(4) = 2)
        }

        return Math.max(reach, 5.5225D);
    }
}
