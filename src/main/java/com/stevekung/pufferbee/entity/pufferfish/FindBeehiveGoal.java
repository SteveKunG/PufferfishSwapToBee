package com.stevekung.pufferbee.entity.pufferfish;

import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

import com.google.common.collect.Lists;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.passive.fish.PufferfishEntity;
import net.minecraft.pathfinding.Path;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.math.BlockPos;

public class FindBeehiveGoal extends PassiveGoal
{
    private int ticks;
    private List<BlockPos> possibleHives = Lists.newArrayList();
    @Nullable
    private Path path = null;
    private int field_234183_f_;

    public FindBeehiveGoal(PufferfishEntity angerable)
    {
        super(angerable);
        this.ticks = this.angerable.world.rand.nextInt(10);
        this.setMutexFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canBeeStart() {
        return ((IBeeFish)this.angerable).getHivePos() != null && !this.angerable.detachHome() && ((IBeeFish)this.angerable).canEnterHive() && !this.isCloseEnough(((IBeeFish)this.angerable).getHivePos()) && this.angerable.world.getBlockState(((IBeeFish)this.angerable).getHivePos()).isIn(BlockTags.BEEHIVES);
    }

    @Override
    public boolean canBeeContinue() {
        return this.canBeeStart();
    }

    /**
     * Execute a one shot task or start executing a continuous task
     */
    @Override
    public void startExecuting() {
        this.ticks = 0;
        this.field_234183_f_ = 0;
        super.startExecuting();
    }

    /**
     * Reset the task's internal state. Called when this task is interrupted by another one
     */
    @Override
    public void resetTask() {
        this.ticks = 0;
        this.field_234183_f_ = 0;
        this.angerable.getNavigator().clearPath();
        this.angerable.getNavigator().resetRangeMultiplier();
    }

    /**
     * Keep ticking a continuous task that has already been started
     */
    @Override
    public void tick() {
        if (((IBeeFish)this.angerable).getHivePos() != null) {
            ++this.ticks;
            if (this.ticks > 600) {
                this.makeChosenHivePossibleHive();
            } else if (!this.angerable.getNavigator().hasPath()) {
                if (!((IBeeFish)this.angerable).isWithinDistance(((IBeeFish)this.angerable).getHivePos(), 16)) {
                    if (((IBeeFish)this.angerable).isTooFar(((IBeeFish)this.angerable).getHivePos())) {
                        this.reset();
                    } else {
                        ((IBeeFish)this.angerable).startMovingTo(((IBeeFish)this.angerable).getHivePos());
                    }
                } else {
                    boolean flag = this.startMovingToFar(((IBeeFish)this.angerable).getHivePos());
                    if (!flag) {
                        this.makeChosenHivePossibleHive();
                    } else if (this.path != null && this.angerable.getNavigator().getPath().isSamePath(this.path)) {
                        ++this.field_234183_f_;
                        if (this.field_234183_f_ > 60) {
                            this.reset();
                            this.field_234183_f_ = 0;
                        }
                    } else {
                        this.path = this.angerable.getNavigator().getPath();
                    }

                }
            }
        }
    }

    private boolean startMovingToFar(BlockPos pos) {
        this.angerable.getNavigator().setRangeMultiplier(10.0F);
        this.angerable.getNavigator().tryMoveToXYZ(pos.getX(), pos.getY(), pos.getZ(), 1.0D);
        return this.angerable.getNavigator().getPath() != null && this.angerable.getNavigator().getPath().reachesTarget();
    }

    public boolean isPossibleHive(BlockPos pos) {
        return this.possibleHives.contains(pos);
    }

    private void addPossibleHives(BlockPos pos) {
        this.possibleHives.add(pos);

        while(this.possibleHives.size() > 3) {
            this.possibleHives.remove(0);
        }

    }

    public void clearPossibleHives() {
        this.possibleHives.clear();
    }

    private void makeChosenHivePossibleHive() {
        if (((IBeeFish)this.angerable).getHivePos() != null) {
            this.addPossibleHives(((IBeeFish)this.angerable).getHivePos());
        }

        this.reset();
    }

    private void reset() {
        ((IBeeFish)this.angerable).setHivePos(null);
        ((IBeeFish)this.angerable).setRemainingCooldownBeforeLocatingNewHive(200);
    }

    private boolean isCloseEnough(BlockPos pos) {
        if (((IBeeFish)this.angerable).isWithinDistance(pos, 2)) {
            return true;
        } else {
            Path path = this.angerable.getNavigator().getPath();
            return path != null && path.getTarget().equals(pos) && path.reachesTarget() && path.isFinished();
        }
    }
}