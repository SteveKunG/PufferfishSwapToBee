package com.stevekung.pufferbee.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import net.minecraft.block.BeehiveBlock;
import net.minecraft.block.ContainerBlock;
import net.minecraft.entity.passive.fish.PufferfishEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

@Mixin(BeehiveBlock.class)
public abstract class MixinBeehiveBlock extends ContainerBlock
{
    private MixinBeehiveBlock()
    {
        super(null);
    }

    @Overwrite
    private void angerNearbyBees(World world, BlockPos pos)
    {
        List<PufferfishEntity> list = world.getEntitiesWithinAABB(PufferfishEntity.class, new AxisAlignedBB(pos).grow(8.0D, 6.0D, 8.0D));
        if (!list.isEmpty()) {
            List<PlayerEntity> list1 = world.getEntitiesWithinAABB(PlayerEntity.class, new AxisAlignedBB(pos).grow(8.0D, 6.0D, 8.0D));
            int i = list1.size();

            for(PufferfishEntity beeentity : list) {
                if (beeentity.getAttackTarget() == null) {
                    beeentity.setAttackTarget(list1.get(world.rand.nextInt(i)));
                }
            }
        }
    }
}