package com.stevekung.pufferbee.entity.pufferfish;

import java.util.EnumSet;
import java.util.Optional;

import com.google.common.base.Predicate;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoublePlantBlock;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.passive.fish.PufferfishEntity;
import net.minecraft.state.properties.DoubleBlockHalf;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;

public class PollinateGoal extends PassiveGoal
{
    private final Predicate<BlockState> flowerPredicate = p_226499_0_ ->
    {
        if (p_226499_0_.isIn(BlockTags.TALL_FLOWERS)) {
            if (p_226499_0_.isIn(Blocks.SUNFLOWER)) {
                return p_226499_0_.get(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER;
            } else {
                return true;
            }
        } else {
            return p_226499_0_.isIn(BlockTags.SMALL_FLOWERS);
        }
    };
    private int pollinationTicks = 0;
    private int lastPollinationTick = 0;
    private boolean running;
    private Vector3d nextTarget;
    private int ticks = 0;
    private final PufferfishEntity entity;

    public PollinateGoal(PufferfishEntity entity)
    {
        super(entity);
        this.setMutexFlags(EnumSet.of(Goal.Flag.MOVE));
        this.entity = entity;
    }

    @Override
    public boolean canBeeStart() {

        if (((IBeeFish)this.entity).getRemainingCooldownBeforeLocatingNewFlower() > 0) {
            return false;
        } else if (((IBeeFish)this.entity).hasNectar()) {
            return false;
        } else if (this.entity.world.isRaining()) {
            return false;
        } else if (this.entity.getRNG().nextFloat() < 0.7F) {
            return false;
        } else {
            Optional<BlockPos> optional = this.getFlower();
            if (optional.isPresent()) {
                ((IBeeFish)this.entity).setSavedFlowerPos(optional.get());
                this.entity.getNavigator().tryMoveToXYZ(((IBeeFish)this.entity).getSavedFlowerPos().getX() + 0.5D, ((IBeeFish)this.entity).getSavedFlowerPos().getY() + 0.5D, ((IBeeFish)this.entity).getSavedFlowerPos().getZ() + 0.5D, 1.2F);
                return true;
            } else {
                return false;
            }
        }
    }

    @Override
    public boolean canBeeContinue() {
        if (!this.running) {
            return false;
        } else if (!((IBeeFish)this.entity).hasFlower()) {
            return false;
        } else if (this.entity.world.isRaining()) {
            return false;
        } else if (this.completedPollination()) {
            return this.entity.getRNG().nextFloat() < 0.2F;
        } else if (this.entity.ticksExisted % 20 == 0 && !this.isFlowers(((IBeeFish)this.entity).getSavedFlowerPos())) {
            ((IBeeFish)this.entity).setSavedFlowerPos(null);
            return false;
        } else {
            return true;
        }
    }

    private boolean completedPollination() {
        return this.pollinationTicks > 400;
    }

    public boolean isRunning() {
        return this.running;
    }

    public void cancel() {
        this.running = false;
    }

    /**
     * Execute a one shot task or start executing a continuous task
     */
    @Override
    public void startExecuting() {
        this.pollinationTicks = 0;
        this.ticks = 0;
        this.lastPollinationTick = 0;
        this.running = true;
        ((IBeeFish)this.entity).resetTicksWithoutNectar();
    }

    /**
     * Reset the task's internal state. Called when this task is interrupted by another one
     */
    @Override
    public void resetTask() {
        if (this.completedPollination()) {
            ((IBeeFish)this.entity).setHasNectar(true);
        }

        this.running = false;
        this.entity.getNavigator().clearPath();
        ((IBeeFish)this.entity).setRemainingCooldownBeforeLocatingNewFlower(200);
    }

    /**
     * Keep ticking a continuous task that has already been started
     */
    @Override
    public void tick() {
        ++this.ticks;
        if (this.ticks > 600) {
            ((IBeeFish)this.entity).setSavedFlowerPos(null);
        } else {
            Vector3d vector3d = Vector3d.copyCenteredHorizontally(((IBeeFish)this.entity).getSavedFlowerPos()).add(0.0D, 0.6F, 0.0D);
            if (vector3d.distanceTo(this.entity.getPositionVec()) > 1.0D) {
                this.nextTarget = vector3d;
                this.moveToNextTarget();
            } else {
                if (this.nextTarget == null) {
                    this.nextTarget = vector3d;
                }

                boolean flag = this.entity.getPositionVec().distanceTo(this.nextTarget) <= 0.1D;
                boolean flag1 = true;
                if (!flag && this.ticks > 600) {
                    ((IBeeFish)this.entity).setSavedFlowerPos(null);
                } else {
                    if (flag) {
                        boolean flag2 = this.entity.getRNG().nextInt(25) == 0;
                        if (flag2) {
                            this.nextTarget = new Vector3d(vector3d.getX() + this.getRandomOffset(), vector3d.getY(), vector3d.getZ() + this.getRandomOffset());
                            this.entity.getNavigator().clearPath();
                        } else {
                            flag1 = false;
                        }

                        this.entity.getLookController().setLookPosition(vector3d.getX(), vector3d.getY(), vector3d.getZ());
                    }

                    if (flag1) {
                        this.moveToNextTarget();
                    }

                    ++this.pollinationTicks;
                    if (this.entity.getRNG().nextFloat() < 0.05F && this.pollinationTicks > this.lastPollinationTick + 60) {
                        this.lastPollinationTick = this.pollinationTicks;
                        this.entity.playSound(SoundEvents.ENTITY_BEE_POLLINATE, 1.0F, 1.0F);
                    }

                }
            }
        }
    }

    private void moveToNextTarget() {
        this.entity.getMoveHelper().setMoveTo(this.nextTarget.getX(), this.nextTarget.getY(), this.nextTarget.getZ(), 0.35F);
    }

    private float getRandomOffset() {
        return (this.entity.getRNG().nextFloat() * 2.0F - 1.0F) * 0.33333334F;
    }

    private Optional<BlockPos> getFlower() {
        return this.findFlower(this.flowerPredicate, 5.0D);
    }

    private Optional<BlockPos> findFlower(Predicate<BlockState> p_226500_1_, double distance) {
        BlockPos blockpos = this.entity.getPosition();
        BlockPos.Mutable blockpos$mutable = new BlockPos.Mutable();

        for(int i = 0; i <= distance; i = i > 0 ? -i : 1 - i) {
            for(int j = 0; j < distance; ++j) {
                for(int k = 0; k <= j; k = k > 0 ? -k : 1 - k) {
                    for(int l = k < j && k > -j ? j : 0; l <= j; l = l > 0 ? -l : 1 - l) {
                        blockpos$mutable.setAndOffset(blockpos, k, i - 1, l);
                        if (blockpos.withinDistance(blockpos$mutable, distance) && p_226500_1_.test(this.entity.world.getBlockState(blockpos$mutable))) {
                            return Optional.of(blockpos$mutable);
                        }
                    }
                }
            }
        }

        return Optional.empty();
    }

    private boolean isFlowers(BlockPos pos) {
        return this.entity.world.isBlockPresent(pos) && this.entity.world.getBlockState(pos).getBlock().isIn(BlockTags.FLOWERS);
    }
}