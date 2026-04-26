package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;

import java.util.EnumSet;

public class EmeraldOwnerHurtTargetGoal extends TargetGoal {

    private final EmeraldMercenaryEntity mercenary;
    private LivingEntity ownerTarget;
    private int timestamp;

    public EmeraldOwnerHurtTargetGoal(EmeraldMercenaryEntity mercenary) {
        super(mercenary, false);
        this.mercenary = mercenary;
        EnumSet<Goal.Flag> flags = EnumSet.noneOf(Goal.Flag.class);
        flags.add(Goal.Flag.TARGET);
        this.setFlags(flags);
    }

    @Override
    public boolean canUse() {
        if (this.mercenary.isNeutralOrder()) {
            return false;
        }

        LivingEntity owner = this.mercenary.getOwner();
        if (owner == null) {
            return false;
        }

        LivingEntity target = owner.getLastHurtMob();
        if (target == null || !target.isAlive()) {
            return false;
        }

        if (target == this.mercenary) {
            return false;
        }

        if (target == owner) {
            return false;
        }

        int hurtTimestamp = owner.getLastHurtMobTimestamp();
        if (hurtTimestamp == this.timestamp) {
            return false;
        }

        this.ownerTarget = target;
        this.timestamp = hurtTimestamp;
        return true;
    }

    @Override
    public void start() {
        this.mob.setTarget(this.ownerTarget);
        super.start();
    }
}
