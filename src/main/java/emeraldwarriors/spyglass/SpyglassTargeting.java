package emeraldwarriors.spyglass;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Resolves what the player is aiming at through the spyglass (up to {@link SpyglassCommandHandler#COMMAND_RADIUS} blocks).
 * Entity hits are checked first so mobs in the crosshair are not overridden by the ground block.
 */
public final class SpyglassTargeting {

    private static final float PARTIAL_TICK = 1.0F;
    /** Extra padding on entity hitboxes so scoped targets are easier to mark. */
    private static final double ENTITY_AIM_PADDING = 1.0D;

    private SpyglassTargeting() {
    }

    public static LivingEntity findLivingTarget(Player player, double range) {
        EntityHitResult hit = findEntityHit(player, range);
        if (hit == null) {
            return null;
        }
        Entity entity = hit.getEntity();
        return entity instanceof LivingEntity living ? living : null;
    }

    public static BlockHitResult findBlockTarget(Player player, double range) {
        Vec3 from = player.getEyePosition(PARTIAL_TICK);
        Vec3 to = from.add(player.getViewVector(PARTIAL_TICK).scale(range));
        return player.level().clip(new ClipContext(
                from,
                to,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player));
    }

    public static EntityHitResult findEntityHit(Player player, double range) {
        Vec3 from = player.getEyePosition(PARTIAL_TICK);
        Vec3 look = player.getViewVector(PARTIAL_TICK);
        Vec3 to = from.add(look.scale(range));
        AABB search = player.getBoundingBox()
                .expandTowards(look.scale(range))
                .inflate(ENTITY_AIM_PADDING);
        return ProjectileUtil.getEntityHitResult(
                player,
                from,
                to,
                search,
                entity -> entity instanceof LivingEntity
                        && entity != player
                        && entity.isPickable()
                        && !entity.isSpectator(),
                range * range);
    }

    public static boolean isEntityCloserThanBlock(Player player, double range, EntityHitResult entityHit) {
        if (entityHit == null) {
            return false;
        }
        Vec3 from = player.getEyePosition(PARTIAL_TICK);
        double entityDist = from.distanceToSqr(entityHit.getLocation());
        BlockHitResult blockHit = findBlockTarget(player, range);
        if (blockHit.getType() == HitResult.Type.MISS) {
            return true;
        }
        return entityDist <= from.distanceToSqr(blockHit.getLocation()) + 0.25D;
    }
}
