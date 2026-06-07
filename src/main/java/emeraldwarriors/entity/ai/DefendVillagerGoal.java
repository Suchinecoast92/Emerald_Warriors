package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.mercenary.MercenaryOrder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.List;

/**
 * Mercenaries defend villagers, wandering traders, and iron golems.
 *
 * Wild mercenaries react to any attacker including players (like an iron golem).
 * Contracted mercenaries in GUARD/PATROL defend against hostile mobs only;
 * they ignore player attackers entirely (owner loyalty).
 * FOLLOW and NEUTRAL: this goal is inactive.
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

        if (!isWild) {
            MercenaryOrder order = this.mercenary.getCurrentOrder();
            if (order == MercenaryOrder.FOLLOW || order == MercenaryOrder.NEUTRAL) {
                return false;
            }
        }

        if (this.mercenary.getTarget() != null && this.mercenary.getTarget().isAlive()) {
            return false;
        }

        List<LivingEntity> nearbyDefendables = this.mercenary.level().getEntitiesOfClass(LivingEntity.class,
                this.mercenary.getBoundingBox().inflate(this.detectionRadius),
                entity -> entity.isAlive() && (entity instanceof AbstractVillager || entity instanceof IronGolem));

        for (LivingEntity defendable : nearbyDefendables) {
            LivingEntity attacker = defendable.getLastHurtByMob();
            if (attacker == null || !attacker.isAlive() || attacker == this.mercenary) {
                continue;
            }
            if (defendable.tickCount - defendable.getLastHurtByMobTimestamp() > 100) {
                continue;
            }

            if (attacker instanceof Player player) {
                if (!isWild) {
                    continue;
                }
                if (player.isCreative() || player.isSpectator()) {
                    continue;
                }
                if (!this.mercenary.hasLineOfSight(defendable) || !this.mercenary.hasLineOfSight(player)) {
                    continue;
                }
            }

            this.villagerAttacker = attacker;
            return true;
        }

        return false;
    }

    @Override
    public void start() {
        this.mercenary.setTargetFromVillagerDefense(this.villagerAttacker);
        super.start();
    }
}
