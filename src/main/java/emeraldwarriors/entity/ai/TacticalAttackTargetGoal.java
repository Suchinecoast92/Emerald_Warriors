package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;

import java.util.EnumSet;

/**
 * Attack a living entity marked by the owner's spyglass command.
 */
public class TacticalAttackTargetGoal extends TargetGoal {

    private final EmeraldMercenaryEntity mercenary;
    private LivingEntity tacticalTarget;

    public TacticalAttackTargetGoal(EmeraldMercenaryEntity mercenary) {
        super(mercenary, false);
        this.mercenary = mercenary;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (!this.mercenary.isTacticalAttackActive() || !this.mercenary.canObeyTacticalCommands()) {
            return false;
        }
        LivingEntity target = this.mercenary.getTacticalAttackTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        if (target == this.mercenary.getOwner()) {
            return false;
        }
        this.tacticalTarget = target;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (!this.mercenary.isTacticalAttackActive() || !this.mercenary.canObeyTacticalCommands()) {
            return false;
        }
        LivingEntity target = this.mercenary.getTacticalAttackTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        this.tacticalTarget = target;
        return true;
    }

    @Override
    public void start() {
        this.mercenary.setTarget(this.tacticalTarget);
        this.mercenary.alertBrotherhood(this.tacticalTarget);
        super.start();
    }

    @Override
    public void stop() {
        super.stop();
    }
}
