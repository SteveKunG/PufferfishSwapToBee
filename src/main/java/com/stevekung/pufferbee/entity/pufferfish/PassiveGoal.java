package com.stevekung.pufferbee.entity.pufferfish;

import net.minecraft.entity.IAngerable;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.passive.fish.PufferfishEntity;

public abstract class PassiveGoal extends Goal
{
    protected final PufferfishEntity angerable;

    public PassiveGoal(PufferfishEntity angerable)
    {
        this.angerable = angerable;
    }

    public abstract boolean canBeeStart();

    public abstract boolean canBeeContinue();

    /**
     * Returns whether execution should begin. You can also read and cache any state necessary for execution in this
     * method as well.
     */
    @Override
    public boolean shouldExecute() {
        return this.canBeeStart() && !((IAngerable)this.angerable).func_233678_J__();
    }

    /**
     * Returns whether an in-progress EntityAIBase should continue executing
     */
    @Override
    public boolean shouldContinueExecuting() {
        return this.canBeeContinue() && !((IAngerable)this.angerable).func_233678_J__();
    }
}