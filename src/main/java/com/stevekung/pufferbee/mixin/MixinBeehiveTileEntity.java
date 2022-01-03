package com.stevekung.pufferbee.mixin;

import java.util.List;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import com.stevekung.pufferbee.entity.pufferfish.IBeeFish;

import net.minecraft.block.BeehiveBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.fish.PufferfishEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tags.BlockTags;
import net.minecraft.tileentity.BeehiveTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;

@Mixin(BeehiveTileEntity.class)
public abstract class MixinBeehiveTileEntity extends TileEntity
{
    private final BeehiveTileEntity that = (BeehiveTileEntity) (Object) this;

    @Shadow
    @Final
    private List<BeehiveTileEntity.Bee> bees;

    @Shadow
    private BlockPos flowerPos;

    @Shadow
    private List<Entity> tryReleaseBee(BlockState p_226965_1_, BeehiveTileEntity.State p_226965_2_)
    {
        return null;
    }

    @Shadow
    private boolean hasFlowerPos()
    {
        return false;
    }

    private MixinBeehiveTileEntity()
    {
        super(null);
    }

    @Overwrite
    public void angerBees(@Nullable PlayerEntity p_226963_1_, BlockState p_226963_2_, BeehiveTileEntity.State p_226963_3_) {
        List<Entity> list = this.tryReleaseBee(p_226963_2_, p_226963_3_);
        if (p_226963_1_ != null) {
            for(Entity entity : list) {
                if (entity instanceof PufferfishEntity) {
                    PufferfishEntity beeentity = (PufferfishEntity)entity;
                    if (p_226963_1_.getPositionVec().squareDistanceTo(entity.getPositionVec()) <= 16.0D) {
                        if (!this.that.isSmoked()) {
                            beeentity.setAttackTarget(p_226963_1_);
                        } else {
                            ((IBeeFish)beeentity).setStayOutOfHiveCountdown(400);
                        }
                    }
                }
            }
        }

    }

    @Overwrite
    public void tryEnterHive(Entity p_226962_1_, boolean p_226962_2_, int p_226962_3_) {
        if (this.bees.size() < 3) {
            p_226962_1_.stopRiding();
            p_226962_1_.removePassengers();
            CompoundNBT compoundnbt = new CompoundNBT();
            p_226962_1_.writeUnlessPassenger(compoundnbt);
            this.bees.add(new BeehiveTileEntity.Bee(compoundnbt, p_226962_3_, p_226962_2_ ? 2400 : 600));
            if (this.world != null) {
                if (p_226962_1_ instanceof PufferfishEntity) {
                    PufferfishEntity beeentity = (PufferfishEntity)p_226962_1_;
                    if (((IBeeFish)beeentity).hasFlower() && (!this.hasFlowerPos() || this.world.rand.nextBoolean())) {
                        this.flowerPos = ((IBeeFish)beeentity).getFlowerPos();
                    }
                }
                BlockPos blockpos = this.getPos();
                this.world.playSound((PlayerEntity)null, blockpos.getX(), blockpos.getY(), blockpos.getZ(), SoundEvents.BLOCK_BEEHIVE_ENTER, SoundCategory.BLOCKS, 1.0F, 1.0F);
            }

            p_226962_1_.remove();
        }
    }

    @Overwrite
    private boolean func_235651_a_(BlockState p_235651_1_, BeehiveTileEntity.Bee p_235651_2_, @Nullable List<Entity> p_235651_3_, BeehiveTileEntity.State p_235651_4_) {
        if ((this.world.isNightTime() || this.world.isRaining()) && p_235651_4_ != BeehiveTileEntity.State.EMERGENCY) {
            return false;
        } else {
            BlockPos blockpos = this.getPos();
            CompoundNBT compoundnbt = p_235651_2_.entityData;
            compoundnbt.remove("Passengers");
            compoundnbt.remove("Leash");
            compoundnbt.remove("UUID");
            Direction direction = p_235651_1_.get(BeehiveBlock.FACING);
            BlockPos blockpos1 = blockpos.offset(direction);
            boolean flag = !this.world.getBlockState(blockpos1).getCollisionShape(this.world, blockpos1).isEmpty();
            if (flag && p_235651_4_ != BeehiveTileEntity.State.EMERGENCY) {
                return false;
            } else {
                Entity entity = EntityType.loadEntityAndExecute(compoundnbt, this.world, p_226960_0_ -> p_226960_0_);
                if (entity != null) {
                    if (entity instanceof PufferfishEntity) {
                        PufferfishEntity beeentity = (PufferfishEntity)entity;
                        if (this.hasFlowerPos() && !((IBeeFish)beeentity).hasFlower() && this.world.rand.nextFloat() < 0.9F) {
                            ((IBeeFish)beeentity).setFlowerPos(this.flowerPos);
                        }

                        if (p_235651_4_ == BeehiveTileEntity.State.HONEY_DELIVERED) {
                            ((IBeeFish)beeentity).onHoneyDelivered();
                            if (p_235651_1_.getBlock().isIn(BlockTags.BEEHIVES)) {
                                int i = BeehiveTileEntity.getHoneyLevel(p_235651_1_);
                                if (i < 5) {
                                    int j = this.world.rand.nextInt(100) == 0 ? 2 : 1;
                                    if (i + j > 5) {
                                        --j;
                                    }

                                    this.world.setBlockState(this.getPos(), p_235651_1_.with(BeehiveBlock.HONEY_LEVEL, i + j));
                                }
                            }
                        }

                        this.func_235650_a_2(beeentity);
                        if (p_235651_3_ != null) {
                            p_235651_3_.add(beeentity);
                        }

                        float f = entity.getWidth();
                        double d3 = flag ? 0.0D : 0.55D + f / 2.0F;
                        double d0 = blockpos.getX() + 0.5D + d3 * direction.getXOffset();
                        double d1 = blockpos.getY() + 0.5D - entity.getHeight() / 2.0F;
                        double d2 = blockpos.getZ() + 0.5D + d3 * direction.getZOffset();
                        entity.setLocationAndAngles(d0, d1, d2, entity.rotationYaw, entity.rotationPitch);
                    }
                    this.world.playSound((PlayerEntity)null, blockpos, SoundEvents.BLOCK_BEEHIVE_EXIT, SoundCategory.BLOCKS, 1.0F, 1.0F);
                    return this.world.addEntity(entity);
                } else {
                    return false;
                }
            }
        }
    }

    private void func_235650_a_2(PufferfishEntity p_235650_2_) {
        ((IBeeFish)p_235650_2_).resetTicksWithoutNectar();
    }
}