package emeraldwarriors.mount;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * Mount navigation that does not use WalkNodeEvaluator. Horse pathfinding cannot walk off
 * 1-block drops (wide hitbox + node Y checks), which looks like spinning at the ledge.
 * Goals still call {@code moveTo}; we steer the mount at the destination with
 * {@link MercenaryHorseMoveControl} and let vanilla physics step down.
 */
public class DirectMountNavigation extends GroundPathNavigation {

    private boolean hasTarget;
    private double targetX;
    private double targetY;
    private double targetZ;

    public DirectMountNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    public boolean moveTo(double x, double y, double z, double speed) {
        this.setDirectTarget(x, y, z, speed);
        return true;
    }

    @Override
    public boolean moveTo(Entity entity, double speed) {
        this.setDirectTarget(entity.getX(), entity.getY(), entity.getZ(), speed);
        return true;
    }

    @Override
    public boolean moveTo(Path path, double speed) {
        if (path == null || path.getTarget() == null) {
            return false;
        }
        this.setDirectTarget(
                path.getTarget().getX() + 0.5D,
                path.getTarget().getY(),
                path.getTarget().getZ() + 0.5D,
                speed);
        return true;
    }

    @Override
    public void tick() {
        if (!this.hasTarget) {
            return;
        }
        Vec3 dest = new Vec3(this.targetX, this.targetY, this.targetZ)
                .add(MercenaryMountSteering.separationNudge(this.mob));
        this.mob.getMoveControl().setWantedPosition(dest.x, dest.y, dest.z, this.speedModifier);
    }

    @Override
    public boolean isDone() {
        if (!this.hasTarget) {
            return true;
        }
        double dx = this.targetX - this.mob.getX();
        double dz = this.targetZ - this.mob.getZ();
        double dy = this.targetY - this.mob.getY();
        return dx * dx + dz * dz < 2.25D && Math.abs(dy) < 0.75D;
    }

    @Override
    public void stop() {
        super.stop();
        this.hasTarget = false;
        this.mob.getMoveControl().setWait();
        this.mob.setZza(0.0F);
    }

    private void setDirectTarget(double x, double y, double z, double speed) {
        this.hasTarget = true;
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
        this.speedModifier = speed;
        this.mob.getMoveControl().setWantedPosition(x, y, z, speed);
    }
}
