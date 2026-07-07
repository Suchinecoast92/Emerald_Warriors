package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.mercenary.MercenaryOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;

/**
 * Shared vanilla-style tactical helpers for GUARD positioning and height advantage.
 */
public final class CombatTactics {
    private CombatTactics() {
    }

    public static boolean hasHeightAdvantage(EmeraldMercenaryEntity mob, LivingEntity target, double minBlocks) {
        return target != null && mob.getY() - target.getY() >= minBlocks;
    }

    public static boolean isGuardOrder(EmeraldMercenaryEntity mob) {
        return mob.getCurrentOrder() == MercenaryOrder.GUARD && mob.getGuardPos() != null;
    }

    /**
     * True when the merc should avoid descending: elevated guard post or 2+ blocks above target.
     */
    public static boolean shouldPreserveHeightInGuard(EmeraldMercenaryEntity mob, LivingEntity target) {
        if (!isGuardOrder(mob)) {
            return false;
        }
        BlockPos guard = mob.getGuardPos();
        double postRadius = mob.getRank().getGuardRadius() + 2.0;
        double postRadiusSqr = postRadius * postRadius;
        boolean nearElevatedPost = mob.getY() >= guard.getY() + 1.5
                && mob.distanceToSqr(guard.getX() + 0.5, mob.getY(), guard.getZ() + 0.5) <= postRadiusSqr;
        return nearElevatedPost || hasHeightAdvantage(mob, target, 2.0);
    }

    /**
     * Y level for ranged pathing: never path below current high ground while guarding.
     */
    public static double getRangedNavigationY(EmeraldMercenaryEntity mob, LivingEntity target) {
        if (!shouldPreserveHeightInGuard(mob, target)) {
            return target.getY();
        }
        BlockPos guard = mob.getGuardPos();
        double floorY = guard != null ? guard.getY() : mob.getY();
        return Math.max(mob.getY(), Math.max(floorY, target.getY()));
    }

    public static boolean canHoldGroundAndShoot(EmeraldMercenaryEntity mob, LivingEntity target,
                                                double distSqr, float attackRadiusSqr) {
        return shouldPreserveHeightInGuard(mob, target)
                && mob.getSensing().hasLineOfSight(target)
                && distSqr <= (double) attackRadiusSqr;
    }

    /**
     * When returning to guard post, stay on nearby high ground instead of descending.
     */
    public static double getGuardReturnY(EmeraldMercenaryEntity mob, BlockPos guard) {
        double guardX = guard.getX() + 0.5;
        double guardZ = guard.getZ() + 0.5;
        double horizontalDistSqr = mob.distanceToSqr(guardX, mob.getY(), guardZ);
        if (horizontalDistSqr <= 16.0 && mob.getY() > guard.getY() + 1.5) {
            return mob.getY();
        }
        return guard.getY();
    }

    public static void moveToTargetPreservingHeight(EmeraldMercenaryEntity mob, LivingEntity target, double speed) {
        double navSpeed = mob.resolveNavigationSpeed(speed);
        if (shouldPreserveHeightInGuard(mob, target)) {
            mob.getEffectiveNavigation().moveTo(target.getX(), getRangedNavigationY(mob, target), target.getZ(), navSpeed);
        } else {
            mob.getEffectiveNavigation().moveTo(target, navSpeed);
        }
    }
}
