package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.mercenary.MercenaryOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Mercenaries defend villagers, wandering traders, and iron golems.
 *
 * Wild: react to any attacker including players (iron-golem style).
 * Contracted GUARD/PATROL: defend against hostile mobs (not players).
 * Contracted FOLLOW: only during an active raid.
 * NEUTRAL: inactive.
 *
 * In raids they pick the enemy that is hurting a villager, already targeting one
 * (evoker before fangs land), or a raider about to reach one. Spyglass tactical
 * orders are never overridden.
 */
public class DefendVillagerGoal extends TargetGoal {
    private static final int HURT_MEMORY_TICKS = 100;
    private static final double RAID_RADIUS_BONUS = 16.0;
    private static final double IMMINENT_RADIUS = 12.0;
    private static final double IMMINENT_RADIUS_SQR = IMMINENT_RADIUS * IMMINENT_RADIUS;

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
        this.scanCooldown = this.mercenary.isRaidActive() ? 4 : 15;

        if (!this.canDefendVillagersInCurrentState()) {
            return false;
        }
        if (this.mercenary.isTacticalAttackActive() || this.mercenary.isTacticalHoldActive()) {
            return false;
        }

        LivingEntity current = this.mercenary.getTarget();
        LivingEntity threat = this.findVillagerThreat(this.effectiveRadius(), current);
        if (threat == null) {
            return false;
        }

        this.villagerAttacker = threat;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (!this.canDefendVillagersInCurrentState()) {
            return false;
        }
        if (this.mercenary.isTacticalAttackActive() || this.mercenary.isTacticalHoldActive()) {
            return false;
        }
        LivingEntity current = this.mercenary.getTarget();
        if (current == null || !current.isAlive()) {
            return false;
        }
        if (this.mercenary.tickCount % 8 == 0) {
            LivingEntity better = this.findVillagerThreat(this.effectiveRadius(), current);
            if (better != null && better != current) {
                this.villagerAttacker = better;
                this.targetMob = better;
                this.mercenary.setTargetFromVillagerDefense(better);
                current = better;
            }
        }
        return this.mercenary.isVillagerDefenseTarget(current) || this.isStillAVillageThreat(current);
    }

    @Override
    public void start() {
        this.targetMob = this.villagerAttacker;
        this.mercenary.setTargetFromVillagerDefense(this.villagerAttacker);
        this.mercenary.alertBrotherhood(this.villagerAttacker);
        super.start();
    }

    @Override
    public void stop() {
        super.stop();
        this.villagerAttacker = null;
    }

    @Override
    protected double getFollowDistance() {
        return this.effectiveRadius();
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

    private double effectiveRadius() {
        return this.mercenary.isRaidActive() ? this.detectionRadius + RAID_RADIUS_BONUS : this.detectionRadius;
    }

    private AABB searchArea(double radius) {
        AABB area = this.mercenary.getBoundingBox().inflate(radius);
        BlockPos anchor = this.defenseAnchor();
        if (anchor == null) {
            return area;
        }
        AABB aroundAnchor = new AABB(anchor).inflate(radius);
        return new AABB(
                Math.min(area.minX, aroundAnchor.minX),
                Math.min(area.minY, aroundAnchor.minY),
                Math.min(area.minZ, aroundAnchor.minZ),
                Math.max(area.maxX, aroundAnchor.maxX),
                Math.max(area.maxY, aroundAnchor.maxY),
                Math.max(area.maxZ, aroundAnchor.maxZ));
    }

    private BlockPos defenseAnchor() {
        MercenaryOrder order = this.mercenary.getCurrentOrder();
        if (order == MercenaryOrder.GUARD) {
            return this.mercenary.getGuardPos();
        }
        if (order == MercenaryOrder.PATROL) {
            return this.mercenary.getPatrolCenter();
        }
        return null;
    }

    /**
     * Prefer: (1) someone who recently hurt a villager/golem,
     * (2) a hostile whose current AI target is a villager/golem (evoker before fangs land),
     * (3) during raids, a raider already in striking distance of a villager.
     */
    private LivingEntity findVillagerThreat(double radius, LivingEntity currentTarget) {
        boolean isWild = this.mercenary.getOwnerUuid() == null;
        boolean raid = this.mercenary.isRaidActive();
        AABB area = this.searchArea(radius);

        List<LivingEntity> nearbyDefendables = this.mercenary.level().getEntitiesOfClass(
                LivingEntity.class,
                area,
                entity -> entity.isAlive()
                        && (entity instanceof AbstractVillager || entity instanceof IronGolem));
        if (nearbyDefendables.isEmpty()) {
            return null;
        }

        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;

        for (LivingEntity defendable : nearbyDefendables) {
            LivingEntity attacker = defendable.getLastHurtByMob();
            if (attacker != null
                    && attacker.isAlive()
                    && attacker != this.mercenary
                    && defendable.tickCount - defendable.getLastHurtByMobTimestamp() <= HURT_MEMORY_TICKS
                    && this.isAcceptableAttacker(attacker, defendable, isWild)
                    && this.shouldConsiderThreat(currentTarget, attacker, defendable)) {
                double score = this.scoreThreat(attacker, defendable, 0.0D);
                if (score < bestScore) {
                    bestScore = score;
                    best = attacker;
                }
            }
        }

        List<Mob> hostiles = this.mercenary.level().getEntitiesOfClass(
                Mob.class,
                area,
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
            double score = this.scoreThreat(hostile, defendable, 8.0D);
            if (score < bestScore) {
                bestScore = score;
                best = hostile;
            }
        }

        if (raid) {
            List<Raider> raiders = this.mercenary.level().getEntitiesOfClass(
                    Raider.class,
                    area,
                    raider -> raider.isAlive());
            for (LivingEntity defendable : nearbyDefendables) {
                for (Raider raider : raiders) {
                    if (raider.distanceToSqr(defendable) > IMMINENT_RADIUS_SQR) {
                        continue;
                    }
                    if (!this.isAcceptableAttacker(raider, defendable, isWild)) {
                        continue;
                    }
                    if (!this.shouldConsiderThreat(currentTarget, raider, defendable)) {
                        continue;
                    }
                    double score = this.scoreThreat(raider, defendable, 16.0D);
                    if (score < bestScore) {
                        bestScore = score;
                        best = raider;
                    }
                }
            }
        }

        return best;
    }

    private double scoreThreat(LivingEntity attacker, LivingEntity defendable, double latenessPenalty) {
        double score = this.mercenary.distanceToSqr(defendable) + latenessPenalty;
        if (attacker.getType() == EntityType.EVOKER || attacker.getType() == EntityType.RAVAGER) {
            score -= 96.0;
        } else if (attacker instanceof Raider) {
            score -= 64.0;
        }
        return score;
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
        return this.mercenary.isRaidActive()
                || this.mercenary.distanceToSqr(defendable) <= this.detectionRadius * this.detectionRadius;
    }

    private boolean isStillAVillageThreat(LivingEntity attacker) {
        if (attacker instanceof Mob mob
                && mob.getTarget() != null
                && (mob.getTarget() instanceof AbstractVillager || mob.getTarget() instanceof IronGolem)) {
            return true;
        }
        AABB area = this.searchArea(this.effectiveRadius());
        List<LivingEntity> defendables = this.mercenary.level().getEntitiesOfClass(
                LivingEntity.class,
                area,
                entity -> entity.isAlive()
                        && (entity instanceof AbstractVillager || entity instanceof IronGolem));
        for (LivingEntity defendable : defendables) {
            if (defendable.getLastHurtByMob() == attacker
                    && defendable.tickCount - defendable.getLastHurtByMobTimestamp() <= HURT_MEMORY_TICKS) {
                return true;
            }
            if (this.mercenary.isRaidActive()
                    && attacker instanceof Raider
                    && attacker.distanceToSqr(defendable) <= IMMINENT_RADIUS_SQR) {
                return true;
            }
        }
        return false;
    }
}
