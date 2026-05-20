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

        // Buscar mob hostil o jugador cercano
        double searchRadius = 16.0;
        var nearby = this.mob.level().getEntitiesOfClass(
            LivingEntity.class,
            this.mob.getBoundingBox().inflate(searchRadius),
            this::isValidTarget
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

            return true;
        }

        return false;
    }

    @Override
    public void start() {
        this.mob.setTarget(this.target);
        super.start();
    }

    @Override
    public void stop() {
        this.target = null;
        this.cooldown = 20;
        super.stop();
    }
}
