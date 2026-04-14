package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.mercenary.MercenaryOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Cuando la orden es PATROL, el mercenario deambula aleatoriamente
 * dentro del radio de patrulla de su patrolCenter.
 */
public class PatrolAroundPointGoal extends Goal {
    private final EmeraldMercenaryEntity mercenary;
    private final double speedModifier;
    private int cooldown;

    public PatrolAroundPointGoal(EmeraldMercenaryEntity mercenary, double speedModifier) {
        this.mercenary = mercenary;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        MercenaryOrder order = this.mercenary.getCurrentOrder();
        if (order != MercenaryOrder.PATROL) {
            return false;
        }
        if (this.mercenary.getPatrolCenter() == null) {
            return false;
        }
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        return this.mercenary.getNavigation().isDone();
    }

    @Override
    public boolean canContinueToUse() {
        MercenaryOrder order = this.mercenary.getCurrentOrder();
        return order == MercenaryOrder.PATROL
                && !this.mercenary.getNavigation().isDone();
    }

    @Override
    public void start() {
        BlockPos center = this.mercenary.getPatrolCenter();
        if (center == null) {
            return;
        }

        int baseRadius = this.mercenary.getRank().getPatrolRadius();
        // Triple patrol radius during active raids
        int radius = this.mercenary.isRaidActive() ? baseRadius * 3 : baseRadius;
        var rng = this.mercenary.getRandom();

        // Elegir punto aleatorio dentro del radio de patrulla
        for (int attempts = 0; attempts < 10; attempts++) {
            int dx = rng.nextInt(radius * 2 + 1) - radius;
            int dz = rng.nextInt(radius * 2 + 1) - radius;
            BlockPos target = center.offset(dx, 0, dz);

            // Buscar suelo sólido en Y cercano
            BlockPos ground = target;
            for (int dy = 3; dy >= -3; dy--) {
                BlockPos check = target.offset(0, dy, 0);
                if (this.mercenary.level().getBlockState(check.below()).isSolid()
                        && !this.mercenary.level().getBlockState(check).isSolid()) {
                    ground = check;
                    break;
                }
            }

            if (this.mercenary.getNavigation().moveTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, this.speedModifier)) {
                return;
            }
        }

        // Si no encontró punto válido, esperar un poco
        this.cooldown = 40;
    }

    @Override
    public void stop() {
        this.cooldown = 60 + this.mercenary.getRandom().nextInt(40);
    }
}
