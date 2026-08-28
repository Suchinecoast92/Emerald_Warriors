package emeraldwarriors.mount;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.mixin.PathNavigationAccessor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * While riding, goals pathfind on the mercenary's own {@link PathNavigation} (safe ground
 * nodes). Each tick this steers the mount toward the current path node via
 * {@link DirectMountNavigation}; the mercenary does not move independently.
 */
public final class MercenaryMountedPathSync {

    private MercenaryMountedPathSync() {
    }

    public static void tick(EmeraldMercenaryEntity merc, AbstractHorse horse) {
        if (!(horse.getNavigation() instanceof DirectMountNavigation mountNav)) {
            return;
        }

        PathNavigation mercNav = merc.getEffectiveNavigation();
        if (mercNav.isDone()) {
            mountNav.stop();
            return;
        }

        if (mercNav instanceof MercenaryRiderPathNavigation riderNav) {
            riderNav.advanceMountedPath(horse);
        }

        Path path = mercNav.getPath();
        if (path == null || path.isDone()) {
            mountNav.stop();
            return;
        }

        Vec3 node = path.getNextEntityPos(merc);
        double resolvedSpeed = ((PathNavigationAccessor) mercNav).emeraldWarriors$getSpeedModifier();
        if (resolvedSpeed <= 0.0D) {
            resolvedSpeed = MercenaryMountBehavior.resolveNavigationSpeed(merc, 1.0D);
        }

        double horizDist = Math.sqrt(
                (node.x - horse.getX()) * (node.x - horse.getX())
                        + (node.z - horse.getZ()) * (node.z - horse.getZ()));
        if (horizDist < 3.0D) {
            resolvedSpeed *= Mth.lerp((float) (horizDist / 3.0D), 0.55F, 1.0F);
        }

        mountNav.steerToward(node.x, node.y, node.z, resolvedSpeed);
    }

    public static boolean hasActivePath(EmeraldMercenaryEntity merc) {
        return !merc.getEffectiveNavigation().isDone();
    }
}
