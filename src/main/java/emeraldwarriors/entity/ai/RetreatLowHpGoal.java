package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.mercenary.MercenaryOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Cuando la vida del mercenario baja del umbral de su rango,
 * deja de atacar y se retira hacia su punto de anclaje
 * (dueño, guardPos o patrolCenter según la orden).
 */
public class RetreatLowHpGoal extends Goal {
    private final EmeraldMercenaryEntity mercenary;
    private final double speedModifier;

    public RetreatLowHpGoal(EmeraldMercenaryEntity mercenary, double speedModifier) {
        this.mercenary = mercenary;
        this.speedModifier = speedModifier;
        // MOVE para huir, TARGET para limpiar el objetivo
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (!this.mercenary.isAlive()) {
            return false;
        }
        float fraction = this.mercenary.getHealth() / this.mercenary.getMaxHealth();
        double threshold = this.mercenary.getRank().getRetreatHpFraction();
        return fraction < threshold;
    }

    @Override
    public boolean canContinueToUse() {
        if (!this.mercenary.isAlive()) {
            return false;
        }
        // Seguir huyendo mientras estemos bajo el umbral + 5% de margen
        float fraction = this.mercenary.getHealth() / this.mercenary.getMaxHealth();
        double threshold = this.mercenary.getRank().getRetreatHpFraction() + 0.05;
        return fraction < threshold;
    }

    @Override
    public void start() {
        // Limpiar objetivo para dejar de atacar
        this.mercenary.setTarget(null);
        moveToSafePoint();
    }

    @Override
    public void tick() {
        // Seguir moviéndose hacia el punto seguro
        if (this.mercenary.getNavigation().isDone()) {
            moveToSafePoint();
        }
    }

    @Override
    public void stop() {
        this.mercenary.getNavigation().stop();
    }

    private void moveToSafePoint() {
        MercenaryOrder order = this.mercenary.getCurrentOrder();

        switch (order) {
            case STAY -> {
                BlockPos guard = this.mercenary.getGuardPos();
                if (guard != null) {
                    this.mercenary.getNavigation().moveTo(
                            guard.getX() + 0.5, guard.getY(), guard.getZ() + 0.5, this.speedModifier);
                }
            }
            case PATROL -> {
                BlockPos center = this.mercenary.getPatrolCenter();
                if (center != null) {
                    this.mercenary.getNavigation().moveTo(
                            center.getX() + 0.5, center.getY(), center.getZ() + 0.5, this.speedModifier);
                }
            }
            default -> {
                // FOLLOW / NONE: huir hacia el dueño
                LivingEntity owner = this.mercenary.getOwner();
                if (owner != null) {
                    this.mercenary.getNavigation().moveTo(owner, this.speedModifier);
                }
            }
        }
    }
}
