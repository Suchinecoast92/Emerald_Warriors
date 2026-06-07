package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * Target goal que ataca mobs hostiles y jugadores (excepto los en whitelist).
 * Solo usado en modos GUARD y PATROL.
 */
public class EmeraldNearestAttackableTargetGoal extends TargetGoal {
    private final EmeraldMercenaryEntity mercenary;
    private LivingEntity target;
    private int cooldown;

    public EmeraldNearestAttackableTargetGoal(EmeraldMercenaryEntity mercenary) {
        super(mercenary, false);
        this.mercenary = mercenary;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        // Solo activo en GUARD o PATROL
        if (this.mercenary.getCurrentOrder() != emeraldwarriors.mercenary.MercenaryOrder.GUARD
                && this.mercenary.getCurrentOrder() != emeraldwarriors.mercenary.MercenaryOrder.PATROL) {
            return false;
        }

        if (cooldown > 0) {
            cooldown--;
            return false;
        }

        // Radio de búsqueda: PATROL busca más lejos que GUARD
        double searchRadius = 16.0;
        if (this.mercenary.getCurrentOrder() == emeraldwarriors.mercenary.MercenaryOrder.PATROL) {
            searchRadius = 32.0; // PATROL busca activamente en área amplia
        }

        var nearby = this.mob.level().getEntitiesOfClass(
            LivingEntity.class,
            this.mob.getBoundingBox().inflate(searchRadius),
            e -> isValidTarget(e) && isWithinZone(e)
        );

        if (nearby.isEmpty()) {
            return false;
        }

        // Elegir el más cercano
        double closestDist = Double.MAX_VALUE;
        LivingEntity closest = null;
        for (LivingEntity entity : nearby) {
            double dist = this.mob.distanceToSqr(entity);
            if (dist < closestDist) {
                closestDist = dist;
                closest = entity;
            }
        }

        this.target = closest;
        return closest != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.target == null || !this.target.isAlive()) {
            return false;
        }
        // Si setTarget() rechazó el target silenciosamente, el mob no tiene target: parar para rescanear
        if (this.mob.getTarget() != this.target) {
            return false;
        }
        return isValidTarget(this.target);
    }

    private boolean isValidTarget(LivingEntity entity) {
        // No atacarse a sí mismo
        if (entity == this.mercenary) {
            return false;
        }

        // No atacar al owner
        if (entity instanceof Player p && p.getUUID().equals(this.mercenary.getOwnerUuid())) {
            return false;
        }

        // Mobs hostiles siempre atacables
        if (entity instanceof Monster) {
            return true;
        }

        // Jugadores: verificar condiciones
        if (entity instanceof Player player) {
            // No atacar espectadores o creativos
            if (player.isSpectator() || player.isCreative()) {
                return false;
            }

            return this.mercenary.canInitiatePvpAgainst(player);
        }

        return false;
    }

    /** Verifica que el target esté dentro de la zona de operación (guardPos/patrolCenter).
     *  Debe ser consistente con los filtros de setTarget() en EmeraldMercenaryEntity. */
    private boolean isWithinZone(LivingEntity entity) {
        emeraldwarriors.mercenary.MercenaryOrder order = this.mercenary.getCurrentOrder();
        double raidBonus = this.mercenary.isRaidActive() ? 12.0 : 0.0;

        if (order == emeraldwarriors.mercenary.MercenaryOrder.GUARD) {
            net.minecraft.core.BlockPos gp = this.mercenary.getGuardPos();
            if (gp != null) {
                double limit = this.mercenary.getRank().getGuardRadius() + 4.0 + raidBonus;
                return entity.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(gp)) <= limit * limit;
            }
        } else if (order == emeraldwarriors.mercenary.MercenaryOrder.PATROL) {
            net.minecraft.core.BlockPos pc = this.mercenary.getPatrolCenter();
            if (pc != null) {
                double limit = this.mercenary.getRank().getPatrolRadius() + 4.0 + raidBonus;
                return entity.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pc)) <= limit * limit;
            }
        }
        return true;
    }

    @Override
    public void start() {
        this.mob.setTarget(this.target);
        super.start();
    }

    @Override
    public void stop() {
        this.target = null;
        // Cooldown más corto para PATROL para búsqueda más frecuente
        this.cooldown = (this.mercenary.getCurrentOrder() == emeraldwarriors.mercenary.MercenaryOrder.PATROL) ? 10 : 20;
        super.stop();
    }
}
