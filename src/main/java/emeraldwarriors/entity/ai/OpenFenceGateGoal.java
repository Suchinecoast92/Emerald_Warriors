package emeraldwarriors.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;

public class OpenFenceGateGoal extends Goal {

    private static final int SEARCH_RADIUS = 2;
    private static final int MAX_OPEN_TICKS = 80;

    private final PathfinderMob mob;

    private BlockPos gatePos;
    private int openTicks;

    public OpenFenceGateGoal(PathfinderMob mob) {
        this.mob = mob;
        EnumSet<Flag> flags = EnumSet.noneOf(Flag.class);
        flags.add(Flag.MOVE);
        this.setFlags(flags);
    }

    @Override
    public boolean canUse() {
        if (this.mob.level().isClientSide()) {
            return false;
        }
        if (!this.mob.isAlive()) {
            return false;
        }

        BlockPos found = this.findNearbyClosedGate();
        if (found == null) {
            return false;
        }

        this.gatePos = found;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.mob.level().isClientSide()) {
            return false;
        }
        if (!this.mob.isAlive()) {
            return false;
        }
        if (this.gatePos == null) {
            return false;
        }
        if (this.openTicks >= MAX_OPEN_TICKS) {
            return false;
        }

        BlockState state = this.mob.level().getBlockState(this.gatePos);
        if (!(state.getBlock() instanceof FenceGateBlock)) {
            return false;
        }
        if (!state.getValue(FenceGateBlock.OPEN)) {
            return false;
        }

        double dx = (this.gatePos.getX() + 0.5) - this.mob.getX();
        double dz = (this.gatePos.getZ() + 0.5) - this.mob.getZ();
        return (dx * dx + dz * dz) <= 16.0;
    }

    @Override
    public void start() {
        this.openTicks = 0;
        this.openGate(true);
    }

    @Override
    public void tick() {
        this.openTicks++;

        if (this.gatePos == null) {
            return;
        }

        double dx = (this.gatePos.getX() + 0.5) - this.mob.getX();
        double dz = (this.gatePos.getZ() + 0.5) - this.mob.getZ();
        double distSqr = dx * dx + dz * dz;

        if (this.openTicks > 20 && distSqr > 9.0) {
            this.openTicks = MAX_OPEN_TICKS;
        }
    }

    @Override
    public void stop() {
        this.openGate(false);
        this.gatePos = null;
        this.openTicks = 0;
    }

    private BlockPos findNearbyClosedGate() {
        BlockPos origin = this.mob.blockPosition();
        Level level = this.mob.level();

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    BlockState st = level.getBlockState(pos);
                    if (!(st.getBlock() instanceof FenceGateBlock)) {
                        continue;
                    }
                    if (!st.getValue(FenceGateBlock.OPEN)) {
                        return pos;
                    }
                }
            }
        }

        return null;
    }

    private void openGate(boolean open) {
        if (this.gatePos == null) {
            return;
        }
        Level level = this.mob.level();
        BlockState state = level.getBlockState(this.gatePos);
        if (!(state.getBlock() instanceof FenceGateBlock)) {
            return;
        }
        if (state.getValue(FenceGateBlock.OPEN) == open) {
            return;
        }
        level.setBlock(this.gatePos, state.setValue(FenceGateBlock.OPEN, open), 10);
    }
}
