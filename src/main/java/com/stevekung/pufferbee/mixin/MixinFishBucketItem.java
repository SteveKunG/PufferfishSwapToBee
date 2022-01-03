package com.stevekung.pufferbee.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.fish.AbstractFishEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FishBucketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;

@Mixin(FishBucketItem.class)
public abstract class MixinFishBucketItem
{
    @Shadow
    @Final
    private EntityType<?> fishType;

    @Overwrite
    private void placeFish(ServerWorld worldIn, ItemStack p_205357_2_, BlockPos pos)
    {
        if (this.fishType == EntityType.PUFFERFISH || this.fishType == EntityType.BEE)
        {
            EntityType.BEE.spawn(worldIn, p_205357_2_, (PlayerEntity)null, pos, SpawnReason.NATURAL, true, false);
        }
        else
        {
            Entity entity = this.fishType.spawn(worldIn, p_205357_2_, (PlayerEntity)null, pos, SpawnReason.BUCKET, true, false);

            if (entity != null)
            {
                ((AbstractFishEntity)entity).setFromBucket(true);
            }
        }
    }
}