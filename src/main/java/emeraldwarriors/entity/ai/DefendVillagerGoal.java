package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.mercenary.MercenaryOrder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Mercenaries defend villagers, wandering traders, and iron golems.
 *
 * Wild: react to any attacker including players (iron-golem style).
 * Contracted GUARD/PATROL: defend against hostile mobs (not players).
 * Contracted FOLLOW: inactive except during an active raid, so the army
 * still peels off to save villagers when an evoker/vex/vindicator flips to them.
 * NEUTRAL: inactive.
 *
 * During raids, a merc already fighting elsewhere may retarget the threat
 * to a nearby villager (unless locked by a spyglass tactical attack).
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
        this.scanCooldown = this.mercenary.isRaidActive() ? 10 : 20;

        if (!this.canDefendVillagersInCurrentState()) {
            return false;
        }
        if (this.mercenary.isTacticalAttackActive()) {
            return false;
        }

        LivingEntity current = this.mercenary.getTarget();
        boolean raid = this.mercenary.isRaidActive();
        if (current != null && current.isAlive() && !raid) {
            return false;
        }

        double radius = raid ? this.detectionRadius + 8.0 : this.detectionRadius;
        LivingEntity threat = this.findVillagerThreat(radius, current);
        if (threat == null) {
            return false;
        }

        this.villagerAttacker = threat;
        return true;
    }

    @Override
    public void start() {
        this.mercenary.setTargetFromVillagerDefense(this.villagerAttacker);
        super.start();
    }

    private boolean canDefendVillagersInCurrentState() {
        boolean isWild = this.mercenary.getOwnerUuid() == null;
        if (isWild) {
            return true;
        }

        MercenaryOrder order = this.mercenary.getCurrentOrder();
        if (order == MercenaryOrder.NEUTRAL) {
            return false;
        }
        if (order == MercenaryOrder.FOLLOW) {
            return this.mercenary.isRaidActive();
        }
        return order == MercenaryOrder.GUARD || order == MercenaryOrder.PATROL;
    }

    /**
     * Prefer: (1) someone who recently hurt a villager/golem,
     * (2) a hostile whose current AI target is a villager/golem (evoker before fangs land).
     */
    private LivingEntity findVillagerThreat(double radius, LivingEntity currentTarget) {
        boolean isWild = this.mercenary.getOwnerUuid() == null;
        List<LivingEntity> nearbyDefendables = this.mercenary.level().getEntitiesOfClass(
                LivingEntity.class,
                this.mercenary.getBoundingBox().inflate(radius),
                entity -> entity.isAlive()
                        && (entity instanceof AbstractVillager || entity instanceof IronGolem));

        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;

        for (LivingEntity defendable : nearbyDefendables) {
            LivingEntity attacker = defendable.getLastHurtByMob();
            if (attacker != null
                    && attacker.isAlive()
                    && attacker != this.mercenary
                    && defendable.tickCount - defendable.getLastHurtByMobTimestamp() <= 100
                    && this.isAcceptableAttacker(attacker, defendable, isWild)
                    && this.shouldConsiderThreat(currentTarget, attacker, defendable)) {
                double score = this.mercenary.distanceToSqr(defendable);
                if (attacker instanceof Raider) {
                    score -= 64.0;
                }
                if (score < bestScore) {
                    bestScore = score;
                    best = attacker;
                }
            }
        }

        List<Mob> hostiles = this.mercenary.level().getEntitiesOfClass(
                Mob.class,
                this.mercenary.getBoundingBox().inflate(radius),
                mob -> mob.isAlive()
                        && mob != this.mercenary
                        && mob.getTarget() != null
                        && (mob.getTarget() instanceof AbstractVillager || mob.getTarget() instanceof IronGolem));

        for (Mob hostile : hostiles) {
            LivingEntity defendable = hostile.getTarget();
            if (defendable == null || !this.isAcceptableAttacker(hostile, defendable, isWild)) {
                continue;
            }
            if (!this.shouldConsiderThreat(currentTarget, hostile, defendable)) {
                continue;
            }
            double score = this.mercenary.distanceToSqr(defendable);
            if (hostile instanceof Raider) {
                score -= 64.0;
            }
            // Prefer already-hurt cases slightly over "only targeting"
            score += 8.0;
            if (score < bestScore) {
                bestScore = score;
                best = hostile;
            }
        }

        return best;
    }

    private boolean isAcceptableAttacker(LivingEntity attacker, LivingEntity defendable, boolean isWild) {
        if (attacker instanceof Player player) {
            if (!isWild) {
                return false;
            }
            if (player.isCreative() || player.isSpectator()) {
                return false;
            }
            return this.mercenary.hasLineOfSight(defendable) && this.mercenary.hasLineOfSight(player);
        }
        if (attacker instanceof EmeraldMercenaryEntity other) {
            UUID owner = this.mercenary.getOwnerUuid();
            UUID otherOwner = other.getOwnerUuid();
            if (owner != null && owner.equals(otherOwner)) {
                return false;
            }
        }
        return true;
    }

    private boolean shouldConsiderThreat(LivingEntity currentTarget, LivingEntity attacker, LivingEntity defendable) {
        if (currentTarget == null || !currentTarget.isAlive()) {
            return true;
        }
        if (currentTarget == attacker) {
            return false;
        }
        // Raid only: peel off a nearby villager emergency even mid-fight.
        return this.mercenary.isRaidActive()
                && this.mercenary.distanceToSqr(defendable) <= (this.detectionRadius + 8.0) * (this.detectionRadius + 8.0);
    }
}
