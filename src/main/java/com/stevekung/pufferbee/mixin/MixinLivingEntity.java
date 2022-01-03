package com.stevekung.pufferbee.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.attributes.AttributeModifier;

@Mixin(LivingEntity.class)
public interface MixinLivingEntity
{
    @Accessor("SLOW_FALLING") AttributeModifier getSlowFalling();
}