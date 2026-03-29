package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.mercenary.MercenaryOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Cuando la orden es STAY, el mercenario vuelve a su guardPos si se alejó
 * (por ejemplo tras perseguir un enemigo cercano).
 */
public class GuardPositionGoal extends Goal {
    private final EmeraldMercenaryEntity mercenary;
    private final double speedModifier;
    private final double maxDistSqr;

    public GuardPositionGoal(EmeraldMercenaryEntity mercenary, double speedModifier, double maxDist) {
        this.mercenary = mercenary;
        this.speedModifier = speedModifier;
        this.maxDistSqr = maxDist * maxDist;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.mercenary.getCurrentOrder() != MercenaryOrder.STAY) {
            return false;
        }
        // No intentar volver a la posición de guardia mientras está en combate
        if (this.mercenary.getTarget() != null && this.mercenary.getTarget().isAlive()) {
            return false;
        }
        BlockPos guard = this.mercenary.getGuardPos();
        if (guard == null) {
            return false;
        }
        double distSqr = this.mercenary.distanceToSqr(guard.getX() + 0.5, guard.getY(), guard.getZ() + 0.5);
        return distSqr > 4.0;  // Solo volver si se alejó más de 2 bloques
    }

    @Override
    public boolean canContinueToUse() {
        if (this.mercenary.getCurrentOrder() != MercenaryOrder.STAY) {
            return false;
        }
        // Si entra en combate, dejamos de forzar el regreso a guardPos
        if (this.mercenary.getTarget() != null && this.mercenary.getTarget().isAlive()) {
            return false;
        }
        BlockPos guard = this.mercenary.getGuardPos();
        if (guard == null) {
            return false;
        }
        double distSqr = this.mercenary.distanceToSqr(guard.getX() + 0.5, guard.getY(), guard.getZ() + 0.5);
        return distSqr > 1.5 && !this.mercenary.getNavigation().isDone();
    }

    @Override
    public void start() {
        BlockPos guard = this.mercenary.getGuardPos();
        if (guard != null) {
            this.mercenary.getNavigation().moveTo(guard.getX() + 0.5, guard.getY(), guard.getZ() + 0.5, this.speedModifier);
        }
    }

    @Override
    public void stop() {
        this.mercenary.getNavigation().stop();
    }
}
