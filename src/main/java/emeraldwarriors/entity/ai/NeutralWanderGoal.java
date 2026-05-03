package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.mercenary.MercenaryOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class NeutralWanderGoal extends Goal {
    private final EmeraldMercenaryEntity mercenary;
    private final double speedModifier;
    private int cooldown;

    public NeutralWanderGoal(EmeraldMercenaryEntity mercenary, double speedModifier) {
        this.mercenary = mercenary;
        this.speedModifier = speedModifier;
        EnumSet<Goal.Flag> flags = EnumSet.noneOf(Goal.Flag.class);
        flags.add(Goal.Flag.MOVE);
        this.setFlags(flags);
    }

    @Override
    public boolean canUse() {
        MercenaryOrder order = this.mercenary.getCurrentOrder();
        if (order != MercenaryOrder.NEUTRAL) {
            return false;
        }
        if (this.getCenter() == null) {
            return false;
        }
        if (this.mercenary.getTarget() != null && this.mercenary.getTarget().isAlive()) {
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
        if (order != MercenaryOrder.NEUTRAL) {
            return false;
        }
        if (this.mercenary.getTarget() != null && this.mercenary.getTarget().isAlive()) {
            return false;
        }
        return !this.mercenary.getNavigation().isDone();
    }

    @Override
    public void start() {
        BlockPos center = this.getCenter();
        if (center == null) {
            return;
        }

        int radius = this.mercenary.getRank().getPatrolRadius();
        var rng = this.mercenary.getRandom();

        for (int attempts = 0; attempts < 10; attempts++) {
            int dx = rng.nextInt(radius * 2 + 1) - radius;
            int dz = rng.nextInt(radius * 2 + 1) - radius;
            BlockPos target = center.offset(dx, 0, dz);

            BlockPos ground = target;
            for (int dy = 3; dy >= -3; dy--) {
                BlockPos check = target.offset(0, dy, 0);
                if (this.mercenary.level().getBlockState(check.below()).isSolid()
                        && !this.mercenary.level().getBlockState(check).isSolid()) {
                    ground = check;
                    break;
                }
            }

            if (this.mercenary.getNavigation().moveTo(
                    ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, this.speedModifier)) {
                return;
            }
        }

        this.cooldown = 60;
    }

    @Override
    public void stop() {
        this.cooldown = 100 + this.mercenary.getRandom().nextInt(121);
     }

    private BlockPos getCenter() {
        BlockPos bed = this.mercenary.getBoundBedPos();
        return bed != null ? bed : this.mercenary.getPatrolCenter();
    }
}
