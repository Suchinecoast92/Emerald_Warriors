package emeraldwarriors.mount;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * MoveControl for mercenary-ridden mounts. Vanilla {@link MoveControl} jumps whenever the
 * block at the horse's feet has a collision shape (snow layers, slabs) and the next path
 * node is a 1-block drop — that is the spinning-in-place at ledges.
 *
 * This controller walks toward XZ every tick (player-style) and only jumps for a real 1-block
 * step-up. Gravity handles walking off drops.
 */
public class MercenaryHorseMoveControl extends MoveControl {

    private static final float TURN_DEG_PER_TICK = 12.0F;
    private static final double ARRIVE_HORIZ_SQR = 0.04D;

    public MercenaryHorseMoveControl(Mob mob) {
        super(mob);
    }

    @Override
    public void tick() {
        if (this.operation == Operation.JUMPING) {
            float speed = (float) (this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED));
            this.mob.setSpeed(speed);
            if (this.mob.onGround() || this.mob.isInLiquid()) {
                this.operation = Operation.WAIT;
            }
            return;
        }
        if (this.operation != Operation.MOVE_TO) {
            this.mob.setZza(0.0F);
            return;
        }

        this.operation = Operation.WAIT;

        double dx = this.wantedX - this.mob.getX();
        double dz = this.wantedZ - this.mob.getZ();
        double dy = this.wantedY - this.mob.getY();
        double horizSqr = dx * dx + dz * dz;

        if (horizSqr < ARRIVE_HORIZ_SQR) {
            if (Math.abs(dy) < 0.6D) {
                this.mob.setZza(0.0F);
                return;
            }
            float speed = (float) (this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED));
            this.mob.setSpeed(speed);
            return;
        }

        if (horizSqr >= ARRIVE_HORIZ_SQR) {
            float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
            this.mob.setYRot(this.rotlerp(this.mob.getYRot(), yaw, TURN_DEG_PER_TICK));
        }
        this.mob.yBodyRot = this.mob.getYRot();
        this.mob.setYHeadRot(this.mob.getYRot());

        float speed = (float) (this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED));
        this.mob.setSpeed(speed);

        if (this.mob.onGround() && dy > 0.55D && horizSqr < 16.0D && this.shouldJumpUp()) {
            this.mob.getJumpControl().jump();
            this.operation = Operation.JUMPING;
        }
    }

    private boolean shouldJumpUp() {
        float yawRad = this.mob.getYRot() * ((float) Math.PI / 180.0F);
        double fx = this.mob.getX() - Mth.sin(yawRad) * 1.1D;
        double fz = this.mob.getZ() + Mth.cos(yawRad) * 1.1D;
        BlockPos front = BlockPos.containing(fx, this.mob.getY(), fz);
        BlockState state = this.mob.level().getBlockState(front);
        VoxelShape shape = state.getCollisionShape(this.mob.level(), front);
        if (shape.isEmpty()) {
            return false;
        }
        double top = front.getY() + shape.max(Direction.Axis.Y);
        double rise = top - this.mob.getY();
        return rise > 0.2D && rise <= 1.35D;
    }
}
