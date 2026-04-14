package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
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
    }

    @Override
    public void stop() {
        super.stop();
        this.mob.setAggressive(false);
        this.seeTime = 0;
        this.attackTime = 0;
        this.useTime = 0;
        this.strafeTime = 0;
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

        double distSqr = this.mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
        boolean canSee = this.mob.getSensing().hasLineOfSight(target);

        // Contador de visibilidad similar al esqueleto: positivo si ve, negativo si no
        if (canSee) {
            ++this.seeTime;
        } else {
            --this.seeTime;
        }

        // Movimiento y posicionamiento
        if (distSqr > (double) this.attackRadiusSqr || !canSee) {
            // Fuera de rango o sin visión: acercarse al objetivo normalmente
            this.mob.getNavigation().moveTo(target, this.speedModifier);
            this.strafeTime = 0;
            // Cuando está corriendo para reposicionarse, no queremos strafe lateral
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

            // Solo queremos strafe mientras REALMENTE está cargando un arco
            boolean isUsingBow = this.mob.isUsingItem() && this.mob.getUseItem().getItem() instanceof BowItem;

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

        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (this.mob.isUsingItem()) {
            // Si pierde la visión durante un rato, cancelar la carga
            if (!canSee && this.seeTime < -20) {
                this.mob.stopUsingItem();
                this.useTime = 0;
            } else {
                this.useTime++;
                if (this.useTime >= 20 && canSee && target.isAlive()) {
                    this.mob.stopUsingItem();
                    this.mob.performRangedAttack(target, BowItem.getPowerForTime(this.useTime));
                    this.attackTime = this.attackIntervalMin;
                    this.useTime = 0;
                }
            }
        } else {
            if (this.attackTime > 0) {
                this.attackTime--;
            }

            if (this.attackTime <= 0 && canSee && distSqr <= (double) this.attackRadiusSqr) {
                this.mob.startUsingItem(ProjectileUtil.getWeaponHoldingHand(this.mob, Items.BOW));
                this.useTime = 0;
            }
        }
    }
    
    private boolean shouldMaintainHeightAdvantage(LivingEntity target) {
        // Only SENTINEL and above ranks use tactical height advantage
        var rank = this.mob.getRank();
        return rank.ordinal() >= 2; // SENTINEL=2, VETERAN=3, ANCIENT_GUARD=4
    }
}
