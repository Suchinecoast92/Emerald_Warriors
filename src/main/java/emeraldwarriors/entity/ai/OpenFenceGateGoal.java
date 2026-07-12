package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;

/**
 * Opens fence gates only when the mob is actively pathing through them.
 * Does not scan nearby gates while idle (e.g. guard posts ringed with fence gates).
 */
public class OpenFenceGateGoal extends Goal {

    private static final int PATH_LOOKAHEAD = 6;
    private static final int MAX_OPEN_TICKS = 80;

    private final PathfinderMob mob;

    private BlockPos gatePos;
    private int openTicks;

    public OpenFenceGateGoal(PathfinderMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        if (this.mob.level().isClientSide()) {
            return false;
        }
        if (!this.mob.isAlive()) {
            return false;
        }

        PathNavigation navigation = this.getNavigation();
        Path path = navigation.getPath();
        if (path == null || path.isDone() || !navigation.isInProgress()) {
            return false;
        }

        BlockPos found = this.findGateOnPath(path);
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

        PathNavigation navigation = this.getNavigation();
        if (!navigation.isInProgress()) {
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

    private PathNavigation getNavigation() {
        if (this.mob instanceof EmeraldMercenaryEntity mercenary) {
            return mercenary.getEffectiveNavigation();
        }
        return this.mob.getNavigation();
    }

    private BlockPos findGateOnPath(Path path) {
        Level level = this.mob.level();
        int start = path.getNextNodeIndex();
        int end = Math.min(start + PATH_LOOKAHEAD, path.getNodeCount());
        for (int i = start; i < end; i++) {
            BlockPos gate = this.findClosedGateNear(path.getNodePos(i), 1);
            if (gate != null) {
                return gate;
            }
        }
        return null;
    }

    private BlockPos findClosedGateNear(BlockPos origin, int radius) {
        Level level = this.mob.level();

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN)) {
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
