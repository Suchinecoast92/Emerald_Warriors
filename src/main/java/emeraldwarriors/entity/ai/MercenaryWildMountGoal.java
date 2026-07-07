package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.mercenary.MercenaryOrder;
import emeraldwarriors.mount.MercenaryMountBehavior;
import emeraldwarriors.mount.MercenaryMountHelper;
import emeraldwarriors.mount.MercenaryMounts;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;

import java.util.EnumSet;

/**
 * Wild (uncontracted) mercenaries with a bound horse mount when the horse is saddled and nearby.
 */
public class MercenaryWildMountGoal extends Goal {

    private final EmeraldMercenaryEntity mercenary;

    public MercenaryWildMountGoal(EmeraldMercenaryEntity mercenary) {
        this.mercenary = mercenary;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.mercenary.getOwnerUuid() != null) {
            return false;
        }
        if (this.mercenary.isPassenger()) {
            return false;
        }
        if (this.mercenary.getBoundHorseUuid() == null) {
            return false;
        }
        if (this.mercenary.getTarget() != null && this.mercenary.getTarget().isAlive()) {
            return false;
        }
        MercenaryOrder order = this.mercenary.getCurrentOrder();
        if (order != MercenaryOrder.PATROL && order != MercenaryOrder.NEUTRAL) {
            return false;
        }
        AbstractHorse horse = this.mercenary.findBoundHorse();
        if (horse == null || !horse.isAlive() || !horse.isSaddled()) {
            return false;
        }
        if (!horse.getPassengers().isEmpty()) {
            return false;
        }
        return this.mercenary.distanceToSqr(horse) <= MercenaryMountHelper.MOUNT_RANGE * MercenaryMountHelper.MOUNT_RANGE;
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void start() {
        AbstractHorse horse = this.mercenary.findBoundHorse();
        if (horse == null) {
            return;
        }
        this.mercenary.getNavigation().moveTo(horse, 1.0D);
    }

    @Override
    public void tick() {
        AbstractHorse horse = this.mercenary.findBoundHorse();
        if (horse == null) {
            return;
        }
        if (this.mercenary.distanceToSqr(horse) <= MercenaryMountBehavior.MOUNT_BOARD_RANGE_SQR) {
            MercenaryMounts.prepareForMount(horse);
            this.mercenary.startRiding(horse);
            this.mercenary.getNavigation().stop();
        } else if (this.mercenary.getNavigation().isDone()) {
            this.mercenary.getNavigation().moveTo(horse, 1.0D);
        }
    }

    @Override
    public void stop() {
        this.mercenary.getNavigation().stop();
    }
}
