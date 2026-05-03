package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.mercenary.MercenaryOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

public class MercenarySleepGoal extends Goal {
    private static final int SEARCH_RADIUS = 16;
    private static final int SEARCH_Y = 2;
    private static final int SEARCH_COOLDOWN_TICKS = 60;
    private static final int FAIL_COOLDOWN_TICKS = 120;
    private static final int GIVE_UP_COOLDOWN_TICKS = 200;
    private static final int MAX_PATH_FAILS = 4;
    private static final int IGNORE_BOUND_BED_TICKS = 1200;

    private final EmeraldMercenaryEntity mercenary;
    private final double speedModifier;

    private BlockPos bedPos;
    private int cooldown;
    private int repathCooldown;
    private int pathFailCount;
    private int ignoreBoundBedTicks;

    public MercenarySleepGoal(EmeraldMercenaryEntity mercenary, double speedModifier) {
        this.mercenary = mercenary;
        this.speedModifier = speedModifier;
        EnumSet<Flag> flags = EnumSet.noneOf(Flag.class);
        flags.add(Flag.MOVE);
        flags.add(Flag.LOOK);
        this.setFlags(flags);
    }

    @Override
    public boolean canUse() {
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        if (this.ignoreBoundBedTicks > 0) {
            this.ignoreBoundBedTicks--;
        }
        if (!(this.mercenary.level() instanceof ServerLevel level)) {
            return false;
        }
        if (!this.mercenary.isAlive()) {
            return false;
        }
        if (this.mercenary.getCurrentOrder() != MercenaryOrder.NEUTRAL) {
            return false;
        }
        if (!isNight(level)) {
            return false;
        }
        if (!this.mercenary.isOutOfCombatForHeal()) {
            return false;
        }
        if (this.mercenary.isSleeping()) {
            return false;
        }

        BlockPos found = null;
        BlockPos bound = this.mercenary.getBoundBedPos();
        if (bound != null && this.ignoreBoundBedTicks <= 0) {
            if (isBedValidAndFree(level, bound)
                    && !this.isBedClaimedByOtherMercenary(level, bound)) {
                found = bound;
            } else {
                BlockState st = level.getBlockState(bound);
                if (!(st.getBlock() instanceof BedBlock)) {
                    this.mercenary.setBoundBedPos(null);
                }
            }
        }

        if (found == null) {
            found = this.findNearestFreeBed(level);
        }
        if (found == null) {
            this.cooldown = FAIL_COOLDOWN_TICKS;
            return false;
        }

        if (!this.canPathToBed(level, found)) {
            if (bound != null && bound.equals(found)) {
                this.ignoreBoundBedTicks = IGNORE_BOUND_BED_TICKS;
            }
            this.cooldown = GIVE_UP_COOLDOWN_TICKS;
            return false;
        }

        this.bedPos = found;
        this.mercenary.setReservedBedPos(found);
        this.pathFailCount = 0;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (!(this.mercenary.level() instanceof ServerLevel level)) {
            return false;
        }
        if (!this.mercenary.isAlive()) {
            return false;
        }
        if (this.mercenary.getCurrentOrder() != MercenaryOrder.NEUTRAL) {
            return false;
        }
        if (!isNight(level)) {
            return false;
        }
        if (!this.mercenary.isOutOfCombatForHeal()) {
            return false;
        }
        if (this.bedPos == null) {
            return false;
        }
        if (this.isSleepingInBed(this.bedPos)) {
            return isBedValid(level, this.bedPos)
                    && (this.isBoundBed(this.bedPos) || !isBedClaimedByVillager(level, this.bedPos));
        }

        return isBedValidAndFree(level, this.bedPos)
                && (this.isBoundBed(this.bedPos) || !isBedClaimedByVillager(level, this.bedPos))
                && !this.isBedClaimedByOtherMercenary(level, this.bedPos);
    }

    @Override
    public void start() {
        if (this.bedPos == null) {
            this.cooldown = FAIL_COOLDOWN_TICKS;
            return;
        }
        this.repathCooldown = 0;
        this.pathFailCount = 0;
        this.tryMoveToBed();
        this.cooldown = SEARCH_COOLDOWN_TICKS;
    }

    @Override
    public void tick() {
        if (!(this.mercenary.level() instanceof ServerLevel level)) {
            return;
        }
        if (this.bedPos == null) {
            return;
        }

        if (!isNight(level)) {
            return;
        }

        if (this.mercenary.isSleeping()) {
            if (!isBedValid(level, this.bedPos)) {
                this.mercenary.stopSleeping();
            }
            return;
        }

        double distSqr = this.mercenary.distanceToSqr(
                this.bedPos.getX() + 0.5,
                this.bedPos.getY(),
                this.bedPos.getZ() + 0.5
        );

        if (distSqr <= 2.25) {
            this.mercenary.getNavigation().stop();
            if (isBedValidAndFree(level, this.bedPos)
                    && (this.isBoundBed(this.bedPos) || !isBedClaimedByVillager(level, this.bedPos))
                    && !this.isBedClaimedByOtherMercenary(level, this.bedPos)) {
                BlockPos previous = this.mercenary.getBoundBedPos();
                if (previous == null || !previous.equals(this.bedPos)) {
                    this.mercenary.setBoundBedPos(this.bedPos);
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                            this.mercenary.getX(), this.mercenary.getY() + 1.0, this.mercenary.getZ(),
                            8, 0.4, 0.5, 0.4, 0.0);
                }
                this.mercenary.setReservedBedPos(null);
                this.mercenary.startSleeping(this.bedPos);
            }
            return;
        }

        if (this.mercenary.getNavigation().isDone()) {
            if (this.repathCooldown > 0) {
                this.repathCooldown--;
            } else {
                this.tryMoveToBed();
                if (!this.mercenary.isSleeping() && this.bedPos != null && this.pathFailCount >= MAX_PATH_FAILS) {
                    boolean wasBound = this.isBoundBed(this.bedPos);
                    this.mercenary.getNavigation().stop();
                    this.mercenary.setReservedBedPos(null);
                    this.bedPos = null;
                    if (wasBound) {
                        this.ignoreBoundBedTicks = IGNORE_BOUND_BED_TICKS;
                    }
                    this.cooldown = GIVE_UP_COOLDOWN_TICKS;
                    this.repathCooldown = 0;
                    this.pathFailCount = 0;
                    return;
                }
                this.repathCooldown = 20;
            }
        }
    }

    @Override
    public void stop() {
        if (this.mercenary.isSleeping()) {
            this.mercenary.stopSleeping();
        }
        this.mercenary.getNavigation().stop();
        this.mercenary.setReservedBedPos(null);
        this.bedPos = null;
        this.cooldown = Math.max(this.cooldown, SEARCH_COOLDOWN_TICKS);
        this.repathCooldown = 0;
        this.pathFailCount = 0;
    }

    private boolean isBoundBed(BlockPos bedPos) {
        BlockPos bound = this.mercenary.getBoundBedPos();
        return bound != null && bound.equals(bedPos);
    }

    private void tryMoveToBed() {
        if (this.bedPos == null) {
            return;
        }

        boolean moved = this.mercenary.getNavigation().moveTo(
                this.bedPos.getX() + 0.5,
                this.bedPos.getY(),
                this.bedPos.getZ() + 0.5,
                1,
                this.speedModifier
        );
        if (moved) {
            this.pathFailCount = 0;
            return;
        }

        if (!(this.mercenary.level() instanceof ServerLevel level)) {
            return;
        }

        BlockPos approach = this.findApproachPos(level, this.bedPos);
        if (approach == null) {
            return;
        }

        Path path = this.mercenary.getNavigation().createPath(approach, 1);
        if (path != null) {
            this.mercenary.getNavigation().moveTo(path, this.speedModifier);
            this.pathFailCount = 0;
            return;
        }

        this.pathFailCount++;
    }

    private boolean canPathToBed(ServerLevel level, BlockPos bedPos) {
        Path direct = this.mercenary.getNavigation().createPath(bedPos, 1);
        if (direct != null) {
            return true;
        }
        BlockPos approach = this.findApproachPos(level, bedPos);
        if (approach == null) {
            return false;
        }
        return this.mercenary.getNavigation().createPath(approach, 1) != null;
    }

    private BlockPos findApproachPos(ServerLevel level, BlockPos headPos) {
        BlockState headState = level.getBlockState(headPos);
        if (!(headState.getBlock() instanceof BedBlock)) {
            return null;
        }

        BlockPos footPos = headPos.relative(headState.getValue(BedBlock.FACING).getOpposite());

        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int dy = 0; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos c1 = headPos.offset(dx, dy, dz);
                    BlockPos c2 = footPos.offset(dx, dy, dz);

                    best = this.pickBetterApproach(level, c1, best, bestDist);
                    if (best != null) {
                        bestDist = this.mercenary.distanceToSqr(best.getX() + 0.5, best.getY(), best.getZ() + 0.5);
                    }
                    best = this.pickBetterApproach(level, c2, best, bestDist);
                    if (best != null) {
                        bestDist = this.mercenary.distanceToSqr(best.getX() + 0.5, best.getY(), best.getZ() + 0.5);
                    }
                }
            }
        }

        return best;
    }

    private BlockPos pickBetterApproach(ServerLevel level, BlockPos candidate, BlockPos best, double bestDist) {
        if (!isStandable(level, candidate)) {
            return best;
        }
        Path path = this.mercenary.getNavigation().createPath(candidate, 1);
        if (path == null) {
            return best;
        }

        double dist = this.mercenary.distanceToSqr(candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5);
        if (dist < bestDist) {
            return candidate;
        }
        return best;
    }

    private static boolean isStandable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.getCollisionShape(level, pos).isEmpty()) {
            return false;
        }
        BlockState below = level.getBlockState(pos.below());
        return !below.getCollisionShape(level, pos.below()).isEmpty();
    }

    private BlockPos findNearestFreeBed(ServerLevel level) {
        BlockPos origin = this.mercenary.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int dy = -SEARCH_Y; dy <= SEARCH_Y; dy++) {
            for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (!(state.getBlock() instanceof BedBlock)) {
                        continue;
                    }

                    BlockPos headPos = resolveBedHeadPos(pos, state);
                    if (headPos == null) {
                        continue;
                    }

                    if (!isBedValidAndFree(level, headPos)) {
                        continue;
                    }

                    if (isBedClaimedByVillager(level, headPos)) {
                        continue;
                    }

                    if (this.isBedClaimedByOtherMercenary(level, headPos)) {
                        continue;
                    }

                    double dist = origin.distSqr(headPos);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = headPos;
                    }
                }
            }
        }

        return best;
    }

    private boolean isSleepingInBed(BlockPos bedPos) {
        if (!this.mercenary.isSleeping()) {
            return false;
        }
        try {
            Optional<BlockPos> opt = this.mercenary.getSleepingPos();
            return opt.isPresent() && opt.get().equals(bedPos);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static BlockPos resolveBedHeadPos(BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof BedBlock)) {
            return null;
        }
        BedPart part = state.getValue(BedBlock.PART);
        if (part == BedPart.HEAD) {
            return pos;
        }
        return pos.relative(state.getValue(BedBlock.FACING));
    }

    private static boolean isBedValid(ServerLevel level, BlockPos headPos) {
        BlockState headState = level.getBlockState(headPos);
        if (!(headState.getBlock() instanceof BedBlock)) {
            return false;
        }
        return headState.getValue(BedBlock.PART) == BedPart.HEAD;
    }

    private static boolean isBedValidAndFree(ServerLevel level, BlockPos headPos) {
        BlockState headState = level.getBlockState(headPos);
        if (!(headState.getBlock() instanceof BedBlock)) {
            return false;
        }
        if (headState.getValue(BedBlock.PART) != BedPart.HEAD) {
            return false;
        }
        return !headState.getValue(BedBlock.OCCUPIED);
    }

    private static boolean isBedClaimedByVillager(ServerLevel level, BlockPos bedPos) {
        AABB box = new AABB(bedPos).inflate(48.0);
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class, box, v -> v.isAlive());

        BlockPos alt = bedPos;
        BlockState st = level.getBlockState(bedPos);
        if (st.getBlock() instanceof BedBlock) {
            if (st.getValue(BedBlock.PART) == BedPart.HEAD) {
                alt = bedPos.relative(st.getValue(BedBlock.FACING).getOpposite());
            } else {
                alt = bedPos.relative(st.getValue(BedBlock.FACING));
            }
        }

        for (Villager villager : villagers) {
            Optional<GlobalPos> home = villager.getBrain().getMemory(MemoryModuleType.HOME);
            if (home.isPresent()
                    && home.get().dimension().equals(level.dimension())
                    && (home.get().pos().equals(bedPos) || home.get().pos().equals(alt))) {
                return true;
            }
        }
        return false;
    }

    private boolean isBedClaimedByOtherMercenary(ServerLevel level, BlockPos bedPos) {
        AABB box = new AABB(bedPos).inflate(64.0);
        List<EmeraldMercenaryEntity> mercs = level.getEntitiesOfClass(EmeraldMercenaryEntity.class, box, m -> m.isAlive());
        for (EmeraldMercenaryEntity other : mercs) {
            if (other == this.mercenary) {
                continue;
            }
            BlockPos otherBound = other.getBoundBedPos();
            if (otherBound != null && otherBound.equals(bedPos)) {
                return true;
            }
            BlockPos otherReserved = other.getReservedBedPos();
            if (otherReserved != null && otherReserved.equals(bedPos)) {
                return true;
            }
            if (other.isSleeping()) {
                try {
                    Optional<BlockPos> sleep = other.getSleepingPos();
                    if (sleep.isPresent() && sleep.get().equals(bedPos)) {
                        return true;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return false;
    }

    private static boolean isNight(Level level) {
        long time = level.getDayTime() % 24000L;
        return time >= 12542L && time <= 23460L;
    }
}
