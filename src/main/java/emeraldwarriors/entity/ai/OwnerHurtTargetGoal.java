package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;

import java.util.EnumSet;

/**
 * El mercenario ataca al último mob/jugador que el dueño golpeó.
 * Prioridad 1 en target selector (después de DefendOwner).
 */
public class OwnerHurtTargetGoal extends TargetGoal {
    private final EmeraldMercenaryEntity mercenary;
    private LivingEntity ownerTarget;
    private int timestamp;

    public OwnerHurtTargetGoal(EmeraldMercenaryEntity mercenary) {
        super(mercenary, false);
        this.mercenary = mercenary;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        LivingEntity owner = this.mercenary.getOwner();
        if (owner == null) {
            return false;
        }

        LivingEntity lastHurt = owner.getLastHurtMob();
        if (lastHurt == null || !lastHurt.isAlive()) {
            return false;
        }

        // No atacar al propio dueño
        if (lastHurt == owner) {
            return false;
        }

        // No atacar al propio mercenario
        if (lastHurt == this.mercenary) {
            return false;
        }

        int hurtTimestamp = owner.getLastHurtMobTimestamp();
        if (hurtTimestamp == this.timestamp) {
            return false;
        }

        this.ownerTarget = lastHurt;
        this.timestamp = hurtTimestamp;
        return true;
    }

    @Override
    public void start() {
        this.mob.setTarget(this.ownerTarget);
        super.start();
    }
}
