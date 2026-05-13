package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.AbstractVillager;

import java.util.EnumSet;
import java.util.List;

/**
 * Target goal that makes mercenaries defend villagers, wandering traders,
 * and iron golems when they are attacked by hostile mobs.
 */
public class DefendVillagerGoal extends TargetGoal {
    private final EmeraldMercenaryEntity mercenary;
    private final double detectionRadius;
    private LivingEntity villagerAttacker;
    private int scanCooldown;

    public DefendVillagerGoal(EmeraldMercenaryEntity mercenary, double detectionRadius) {
        super(mercenary, false);
        this.mercenary = mercenary;
        this.detectionRadius = detectionRadius;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (this.scanCooldown > 0) {
            this.scanCooldown--;
            return false;
        }
        this.scanCooldown = 20;

        boolean isWild = this.mercenary.getOwnerUuid() == null;
        if (!isWild && this.mercenary.isNeutralOrder()) {
            return false;
        }

        // Don't override if already fighting something
        if (this.mercenary.getTarget() != null && this.mercenary.getTarget().isAlive()) {
            return false;
        }

        // Search for nearby villagers, wandering traders, and iron golems being attacked
        List<LivingEntity> nearbyDefendables = this.mercenary.level().getEntitiesOfClass(LivingEntity.class,
                this.mercenary.getBoundingBox().inflate(this.detectionRadius),
                entity -> entity.isAlive() && (entity instanceof AbstractVillager || entity instanceof IronGolem));

        for (LivingEntity defendable : nearbyDefendables) {
            LivingEntity attacker = defendable.getLastHurtByMob();
            if (attacker != null && attacker.isAlive() && attacker != this.mercenary) {
                if (isWild && attacker instanceof net.minecraft.world.entity.player.Player) {
                    continue;
                }
                if (!isWild && this.mercenary.getOwnerUuid().equals(attacker.getUUID())) {
                    continue;
                }
                // Check that the attack was recent (within last 5 seconds = 100 ticks)
                if (defendable.getLastHurtByMobTimestamp() > this.mercenary.tickCount - 100) {
                    this.villagerAttacker = attacker;
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public void start() {
        this.mob.setTarget(this.villagerAttacker);
        super.start();
    }
}
