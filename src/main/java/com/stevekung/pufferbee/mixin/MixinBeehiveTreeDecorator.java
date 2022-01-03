package com.stevekung.pufferbee.mixin;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.block.BeehiveBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.fish.PufferfishEntity;
import net.minecraft.tileentity.BeehiveTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MutableBoundingBox;
import net.minecraft.world.ISeedReader;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.treedecorator.BeehiveTreeDecorator;
import net.minecraft.world.gen.treedecorator.TreeDecorator;

@Mixin(BeehiveTreeDecorator.class)
public abstract class MixinBeehiveTreeDecorator extends TreeDecorator
{
    @Shadow
    @Final
    private float probability;

    @Override
    @Overwrite
    public void func_225576_a_(ISeedReader p_225576_1_, Random p_225576_2_, List<BlockPos> p_225576_3_, List<BlockPos> p_225576_4_, Set<BlockPos> p_225576_5_, MutableBoundingBox p_225576_6_) {
        if (!(p_225576_2_.nextFloat() >= this.probability)) {
            Direction direction = BeehiveBlock.getGenerationDirection(p_225576_2_);
            int i = !p_225576_4_.isEmpty() ? Math.max(p_225576_4_.get(0).getY() - 1, p_225576_3_.get(0).getY()) : Math.min(p_225576_3_.get(0).getY() + 1 + p_225576_2_.nextInt(3), p_225576_3_.get(p_225576_3_.size() - 1).getY());
            List<BlockPos> list = p_225576_3_.stream().filter(p_236864_1_ -> p_236864_1_.getY() == i).collect(Collectors.toList());
            if (!list.isEmpty()) {
                BlockPos blockpos = list.get(p_225576_2_.nextInt(list.size()));
                BlockPos blockpos1 = blockpos.offset(direction);
                if (Feature.isAirAt(p_225576_1_, blockpos1) && Feature.isAirAt(p_225576_1_, blockpos1.offset(Direction.SOUTH))) {
                    BlockState blockstate = Blocks.BEE_NEST.getDefaultState().with(BeehiveBlock.FACING, Direction.SOUTH);
                    this.func_227423_a_(p_225576_1_, blockpos1, blockstate, p_225576_5_, p_225576_6_);
                    TileEntity tileentity = p_225576_1_.getTileEntity(blockpos1);
                    if (tileentity instanceof BeehiveTileEntity) {
                        BeehiveTileEntity beehivetileentity = (BeehiveTileEntity)tileentity;
                        int j = 2 + p_225576_2_.nextInt(2);

                        for(int k = 0; k < j; ++k) {
                            PufferfishEntity beeentity = new PufferfishEntity(EntityType.PUFFERFISH, p_225576_1_.getWorld());
                            beehivetileentity.tryEnterHive(beeentity, false, p_225576_2_.nextInt(599));
                        }
                    }
                }
            }
        }
    }
}