package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.mercenary.MercenaryOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Items;

import java.util.EnumSet;

public class EmeraldBowAttackGoal extends Goal {

    private final EmeraldMercenaryEntity mob;
    private final double speedModifier;
    private final int attackIntervalMin;
    private final float attackRadiusSqr;

    private int attackTime;
    private int seeTime;
    private int useTime;

    // Suave movimiento lateral mientras dispara, para que no sea un blanco estático
    private boolean strafeRight;
    private int strafeTime;

    private int repositionCooldown;

    public EmeraldBowAttackGoal(EmeraldMercenaryEntity mob, double speedModifier, int attackIntervalMin, float attackRadius) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.attackIntervalMin = attackIntervalMin;
        this.attackRadiusSqr = attackRadius * attackRadius;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.mob.isNeutralOrder()) {
            return false;
        }
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        return this.isHoldingBow();
    }

    @Override
    public boolean canContinueToUse() {
        if (this.mob.isNeutralOrder()) {
            return false;
        }
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
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
        this.strafeRight = (this.mob.getId() & 1) == 0;
        this.strafeTime = 0;
        this.repositionCooldown = 0;
    }

    @Override
    public void stop() {
        super.stop();
        this.mob.setAggressive(false);
        this.seeTime = 0;
        this.attackTime = 0;
        this.useTime = 0;
        this.strafeTime = 0;
        this.repositionCooldown = 0;
        // Asegurarse de que no quede ningún movimiento lateral pendiente
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
            // Sin objetivo: cancelar cualquier strafe residual
            this.strafeTime = 0;
            this.mob.getMoveControl().strafe(0.0F, 0.0F);
            return;
        }

        if (this.isTooFarFromAnchor()) {
            this.mob.setTarget(null);
            this.mob.getNavigation().stop();
            this.mob.getMoveControl().strafe(0.0F, 0.0F);
            if (this.mob.isUsingItem()) {
                this.mob.stopUsingItem();
            }
            return;
        }

        if (this.repositionCooldown > 0) {
            this.repositionCooldown--;
        }

        double distSqr = this.mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
        boolean canSee = this.mob.getSensing().hasLineOfSight(target);
        boolean repositioningThisTick = false;

        boolean isUsingBow = this.mob.isUsingItem() && this.mob.getUseItem().getItem() instanceof BowItem;

        if (canSee && !isUsingBow) {
            repositioningThisTick = this.tryRepositionAroundTarget(target, distSqr);
        }

        // Contador de visibilidad similar al esqueleto: positivo si ve, negativo si no
        if (canSee) {
            ++this.seeTime;
        } else {
            --this.seeTime;
        }

        // Movimiento y posicionamiento
        if (!canSee) {
            // Fuera de rango o sin visión: acercarse al objetivo normalmente
            this.mob.getNavigation().moveTo(target, this.getChaseSpeed(target));
            this.strafeTime = 0;
            // Cuando está corriendo para reposicionarse, no queremos strafe lateral
            this.mob.getMoveControl().strafe(0.0F, 0.0F);
        } else if (repositioningThisTick) {
            this.strafeTime = 0;
            this.mob.getMoveControl().strafe(0.0F, 0.0F);
        } else if (distSqr > (double) this.attackRadiusSqr) {
            this.mob.getNavigation().moveTo(target, this.getChaseSpeed(target));
            this.strafeTime = 0;
            this.mob.getMoveControl().strafe(0.0F, 0.0F);
        } else {
            // Dentro de rango y con visión: detener el pathing y hacer un leve strafe tipo vanilla
            this.mob.getNavigation().stop();
            
            // SENTINEL+ Tactical Height Advantage - maintain high ground with ranged weapons
            if (this.shouldMaintainHeightAdvantage(target)) {
                double heightDifference = this.mob.getY() - target.getY();
                // If we have significant height advantage (2+ blocks), avoid unnecessary movement
                if (heightDifference >= 2.0) {
                    // Reduce strafe movement to maintain advantageous position
                    this.strafeTime = Math.max(0, this.strafeTime - 1);
                }
            }

            if (this.seeTime >= 10 && isUsingBow) { // esperar un poco y solo mientras carga el arco
                this.strafeTime++;

                if (this.strafeTime >= 40) {
                    this.strafeTime = 0;
                    // Cambiar de lado ocasionalmente
                    if (this.mob.getRandom().nextFloat() < 0.5F) {
                        this.strafeRight = !this.strafeRight;
                    }
                }

                float sideways = this.strafeRight ? 0.3F : -0.3F;
                // Solo movimiento lateral suave; sin avanzar/retroceder para evitar teleports raros
                this.mob.getMoveControl().strafe(0.0F, sideways);
            } else {
                this.strafeTime = 0;
                // Detener cualquier strafe residual cuando no hay amenaza inmediata
                this.mob.getMoveControl().strafe(0.0F, 0.0F);
            }
        }

        // Forzar que el cuerpo y la cabeza miren realmente hacia el objetivo,
        // para que el arco se apunte hacia delante (no de lado).
        this.faceTarget(target);

        if (this.mob.isUsingItem()) {
            // Si pierde la visión durante un rato, cancelar la carga
            if (!canSee && this.seeTime < -20) {
                this.mob.stopUsingItem();
                this.useTime = 0;
            } else {
                this.useTime++;
                if (this.useTime >= 20 && canSee && target.isAlive()) {
                    if (repositioningThisTick) {
                        return;
                    }
                    if (this.mob.isFriendlyInLineOfFire(target)) {
                        this.strafeRight = !this.strafeRight;
                        this.mob.getMoveControl().strafe(0.0F, this.strafeRight ? 0.45F : -0.45F);
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

            if (this.attackTime <= 0 && canSee && distSqr <= (double) this.attackRadiusSqr) {
                if (repositioningThisTick) {
                    return;
                }
                this.mob.startUsingItem(ProjectileUtil.getWeaponHoldingHand(this.mob, Items.BOW));
                this.useTime = 0;
            }
        }
    }

    /**
     * Alinea inmediatamente yaw/pitch del mercenario con el objetivo,
     * similar a como se hace en ShieldAgainstCreeperGoal.
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
    
    private boolean shouldMaintainHeightAdvantage(LivingEntity target) {
        // Only SENTINEL and above ranks use tactical height advantage
        var rank = this.mob.getRank();
        return rank.ordinal() >= 2; // SENTINEL=2, VETERAN=3, ANCIENT_GUARD=4
    }

    private boolean tryRepositionAroundTarget(LivingEntity target, double distSqr) {
        if (target instanceof Player) {
            return false;
        }
        if (this.repositionCooldown > 0) {
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
}
