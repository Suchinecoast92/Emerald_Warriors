package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.mercenary.MercenaryOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Shared helpers for GUARD return and vanilla-style ranged high-ground holds.
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
     * Vanilla-style hold: clear LOS, weapon in range (horizontal), and elevated.
     * Matches skeleton/pillager staying on cliffs/walls instead of descending.
     */
    public static boolean canHoldGroundAndShoot(EmeraldMercenaryEntity mob, LivingEntity target,
                                                float attackRadiusSqr) {
        if (target == null || !mob.getSensing().hasLineOfSight(target)) {
            return false;
        }
        if (!hasHeightAdvantage(mob, target, 1.0)) {
            return false;
        }
        double dx = target.getX() - mob.getX();
        double dz = target.getZ() - mob.getZ();
        return dx * dx + dz * dz <= (double) attackRadiusSqr;
    }

    /**
     * When returning to guard post, stay on nearby high ground instead of descending.
     */
    /**
     * Snap body/head pitch toward target after AI movement (fixes bow pose when strafe
     * or pathing left the merc facing the wrong way).
     */
    public static void snapAimAt(EmeraldMercenaryEntity mob, LivingEntity target) {
        if (target == null || !target.isAlive()) {
            return;
        }
        double dx = target.getX() - mob.getX();
        double dz = target.getZ() - mob.getZ();
        double dy = target.getEyeY() - mob.getEyeY();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(dy, horizontalDist) * Mth.RAD_TO_DEG);
        pitch = Mth.clamp(pitch, -mob.getMaxHeadXRot(), mob.getMaxHeadXRot());

        mob.setYRot(yaw);
        mob.yBodyRot = yaw;
        mob.yHeadRot = yaw;
        mob.setXRot(pitch);
    }

    public static double getGuardReturnY(EmeraldMercenaryEntity mob, BlockPos guard) {
        double guardX = guard.getX() + 0.5;
        double guardZ = guard.getZ() + 0.5;
        double horizontalDistSqr = mob.distanceToSqr(guardX, mob.getY(), guardZ);
        if (horizontalDistSqr <= 16.0 && mob.getY() > guard.getY() + 0.5) {
            return mob.getY();
        }
        return guard.getY();
    }
}
