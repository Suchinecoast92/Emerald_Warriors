package emeraldwarriors.mount;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * Mercenary ground pathfinding. When riding, vanilla {@link Mob#getNavigation()} delegates to
 * the mount (our {@link DirectMountNavigation}). This class lives on the mercenary's own
 * {@code navigation} field: it pathfinds safely and advances nodes as the horse moves, while
 * {@link MercenaryMountedPathSync} steers the mount toward each node.
 */
public class MercenaryRiderPathNavigation extends GroundPathNavigation {

    /** Horse hitbox is wide; 2.5 blocks XZ avoids stuck nodes at snow ledges. */
    private static final double NODE_REACHED_XZ = 6.25D;
    private static final double NODE_REACHED_Y = 1.75D;
    private static final int MAX_ADVANCE_PER_TICK = 6;

    public MercenaryRiderPathNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    private boolean isMountedRider() {
        return this.mob instanceof EmeraldMercenaryEntity merc
                && merc.isPassenger()
                && merc.getVehicle() instanceof AbstractHorse;
    }

    /**
     * Advance path nodes using the mount's position (not the passenger's), including when the
     * horse overshoots a node and would otherwise spin trying to return.
     */
    public void advanceMountedPath(AbstractHorse horse) {
        Path path = this.path;
        if (path == null || path.isDone()) {
            return;
        }

        Vec3 pos = horse.position();
        int safety = 0;
        while (!path.isDone() && safety++ < MAX_ADVANCE_PER_TICK) {
            Vec3 next = path.getNextEntityPos(this.mob);
            if (this.reachedNode(pos, next) || this.passedNode(pos, path)) {
                path.advance();
            } else {
                break;
            }
        }
    }

    private boolean reachedNode(Vec3 pos, Vec3 node) {
        double dx = node.x - pos.x;
        double dy = node.y - pos.y;
        double dz = node.z - pos.z;
        return dx * dx + dz * dz <= NODE_REACHED_XZ && Math.abs(dy) <= NODE_REACHED_Y;
    }

    private boolean passedNode(Vec3 pos, Path path) {
        int idx = path.getNextNodeIndex();
        if (idx <= 0) {
            return false;
        }

        Vec3 prev = path.getEntityPosAtNode(this.mob, idx - 1);
        Vec3 node = path.getEntityPosAtNode(this.mob, idx);

        double segX = node.x - prev.x;
        double segZ = node.z - prev.z;
        double segLenSq = segX * segX + segZ * segZ;
        if (segLenSq < 0.01D) {
            return false;
        }

        double toEntityX = pos.x - prev.x;
        double toEntityZ = pos.z - prev.z;
        double dot = segX * toEntityX + segZ * toEntityZ;

        if (dot > segLenSq * 0.72D) {
            return true;
        }

        double distToNodeSq = (node.x - pos.x) * (node.x - pos.x) + (node.z - pos.z) * (node.z - pos.z);
        if (distToNodeSq > 16.0D) {
            return false;
        }

        if (idx + 1 < path.getNodeCount()) {
            Vec3 after = path.getEntityPosAtNode(this.mob, idx + 1);
            double distAfterSq = (after.x - pos.x) * (after.x - pos.x) + (after.z - pos.z) * (after.z - pos.z);
            if (distAfterSq < distToNodeSq * 0.9D) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void tick() {
        if (!this.isMountedRider()) {
            super.tick();
            return;
        }

        if (this.hasDelayedRecomputation) {
            this.recomputePath();
        }
        if (this.isDone()) {
            return;
        }

        AbstractHorse horse = (AbstractHorse) this.mob.getVehicle();
        this.advanceMountedPath(horse);
    }
}
