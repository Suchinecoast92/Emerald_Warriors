package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.mercenary.MercenaryOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Proactive hostile targeting for GUARD and PATROL.
 * Requires line of sight like vanilla {@code NearestAttackableTargetGoal}
 * (mustSee = true): no acquiring or chasing targets through walls.
 */
public class EmeraldNearestAttackableTargetGoal extends TargetGoal {
    /** Vanilla TargetGoal default: keep target briefly after LOS is lost. */
    private static final int UNSEEN_MEMORY_TICKS = 60;

    private final EmeraldMercenaryEntity mercenary;
    private LivingEntity target;
    private int cooldown;
    private int unseenTicks;

    public EmeraldNearestAttackableTargetGoal(EmeraldMercenaryEntity mercenary) {
        super(mercenary, true);
        this.mercenary = mercenary;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (this.mercenary.getCurrentOrder() != MercenaryOrder.GUARD
                && this.mercenary.getCurrentOrder() != MercenaryOrder.PATROL) {
            return false;
        }

        if (cooldown > 0) {
            cooldown--;
            return false;
        }

        double searchRadius = 16.0;
        if (this.mercenary.getCurrentOrder() == MercenaryOrder.PATROL) {
            searchRadius = 32.0;
        }

        var nearby = this.mob.level().getEntitiesOfClass(
            LivingEntity.class,
            this.mob.getBoundingBox().inflate(searchRadius),
            e -> isValidTarget(e) && isWithinZone(e) && this.mercenary.hasLineOfSight(e)
        );

        if (nearby.isEmpty()) {
            return false;
        }

        double closestDist = Double.MAX_VALUE;
        LivingEntity closest = null;
        for (LivingEntity entity : nearby) {
            double dist = this.mob.distanceToSqr(entity);
            if (dist < closestDist) {
                closestDist = dist;
                closest = entity;
            }
        }

        this.target = closest;
        return closest != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.target == null || !this.target.isAlive()) {
            return false;
        }
        if (this.mob.getTarget() != this.target) {
            return false;
        }
        if (!isValidTarget(this.target) || !isWithinZone(this.target)) {
            return false;
        }

        // Vanilla-style: remember target briefly after LOS breaks, then drop it.
        if (this.mercenary.hasLineOfSight(this.target)) {
            this.unseenTicks = 0;
            return true;
        }
        this.unseenTicks++;
        return this.unseenTicks <= UNSEEN_MEMORY_TICKS;
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (entity == this.mercenary) {
            return false;
        }

        if (entity instanceof Player p && p.getUUID().equals(this.mercenary.getOwnerUuid())) {
            return false;
        }

        if (emeraldwarriors.mount.MercenaryMounts.isAlliedMercenaryMount(entity, this.mercenary)) {
            return false;
        }

        if (CombatTargets.isEnderman(entity)) {
            return false;
        }

        if (entity instanceof Monster) {
            return true;
        }

        if (entity instanceof Player player) {
            if (player.isSpectator() || player.isCreative()) {
                return false;
            }
            return this.mercenary.canInitiatePvpAgainst(player);
        }

        return false;
    }

    /** Consistent with setTarget() zone filters on EmeraldMercenaryEntity. */
    private boolean isWithinZone(LivingEntity entity) {
        MercenaryOrder order = this.mercenary.getCurrentOrder();
        double raidBonus = this.mercenary.isRaidActive() ? 12.0 : 0.0;

        if (order == MercenaryOrder.GUARD) {
            BlockPos gp = this.mercenary.getGuardPos();
            if (gp != null) {
                double limit = this.mercenary.getRank().getGuardRadius() + 4.0 + raidBonus;
                return entity.distanceToSqr(Vec3.atCenterOf(gp)) <= limit * limit;
            }
        } else if (order == MercenaryOrder.PATROL) {
            BlockPos pc = this.mercenary.getPatrolCenter();
            if (pc != null) {
                double limit = this.mercenary.getRank().getPatrolRadius() + 4.0 + raidBonus;
                return entity.distanceToSqr(Vec3.atCenterOf(pc)) <= limit * limit;
            }
        }
        return true;
    }

    @Override
    public void start() {
        this.unseenTicks = 0;
        this.targetMob = this.target;
        this.mob.setTarget(this.target);
        super.start();
    }

    @Override
    public void stop() {
        this.target = null;
        this.unseenTicks = 0;
        this.cooldown = (this.mercenary.getCurrentOrder() == MercenaryOrder.PATROL) ? 10 : 20;
        super.stop();
    }
}
