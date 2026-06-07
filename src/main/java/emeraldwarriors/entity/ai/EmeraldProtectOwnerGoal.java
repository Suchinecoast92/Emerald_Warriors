package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * Target goal that makes the mercenary prioritize enemies attacking its owner.
 * When the owner is being hurt by a mob, the mercenary will target that mob.
 */
public class EmeraldProtectOwnerGoal extends TargetGoal {
    private final EmeraldMercenaryEntity mercenary;
    private LivingEntity ownerAttacker;
    private int timestamp;

    public EmeraldProtectOwnerGoal(EmeraldMercenaryEntity mercenary) {
        super(mercenary, false);
        this.mercenary = mercenary;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
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

        LivingEntity attacker = owner.getLastHurtByMob();
        if (attacker == null || !attacker.isAlive()) {
            return false;
        }

        if (attacker instanceof Player player) {
            this.mercenary.onOwnerAttackedByPlayer(player, owner);
            if (!this.mercenary.isPlayerMarkedHostile(player)) {
                return false;
            }
        }

        // Don't target the owner itself
        if (attacker == this.mercenary) {
            return false;
        }

        // Don't target the mercenary's own owner
        if (attacker == owner) {
            return false;
        }

        int hurtTimestamp = owner.getLastHurtByMobTimestamp();
        if (hurtTimestamp <= this.mercenary.getOwnerLastHurtByMobTimestampBaseline()) {
            return false;
        }
        if (hurtTimestamp == this.timestamp) {
            return false;
        }

        this.ownerAttacker = attacker;
        this.timestamp = hurtTimestamp;
        return true;
    }

    @Override
    public void start() {
        this.mob.setTarget(this.ownerAttacker);
        this.mercenary.alertBrotherhood(this.ownerAttacker);
        super.start();
    }
}
