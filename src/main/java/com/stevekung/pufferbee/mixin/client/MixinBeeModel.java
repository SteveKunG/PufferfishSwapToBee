package com.stevekung.pufferbee.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.client.renderer.entity.model.BeeModel;
import net.minecraft.entity.passive.BeeEntity;

@Mixin(BeeModel.class)
public abstract class MixinBeeModel
{
    @ModifyVariable(method = "setRotationAngles(Lnet/minecraft/entity/passive/BeeEntity;FFFFF)V", at = @At(value = "STORE", ordinal = 0))
    private boolean newFlag(boolean flag, BeeEntity entityIn)
    {
        return !entityIn.isInWaterOrBubbleColumn() && entityIn.getMotion().lengthSquared() <= 0.01D;
    }
}