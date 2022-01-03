package com.stevekung.pufferbee.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.entity.MobEntity;
import net.minecraft.pathfinding.PathNodeType;

@Mixin(MobEntity.class)
public interface MixinMobEntity
{
    @Accessor("mapPathPriority") Map<PathNodeType, Float> getMapPathPriority();
}