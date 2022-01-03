package com.stevekung.pufferbee.mixin;

import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.ai.goal.PrioritizedGoal;

@Mixin(GoalSelector.class)
public interface MixinGoalSelector
{
    @Accessor("goals") Set<PrioritizedGoal> getGoals();
}