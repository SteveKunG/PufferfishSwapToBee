package com.stevekung.pufferbee.entity.pufferfish;

import net.minecraft.util.math.BlockPos;

public interface IBeeFish
{
    BlockPos getFlowerPos();

    boolean hasFlower();

    void setFlowerPos(BlockPos pos);

    void setStayOutOfHiveCountdown(int p_226450_1_);

    float getBodyPitch(float p_226455_1_);

    void resetTicksWithoutNectar();

    boolean hasHive();

    BlockPos getHivePos();
    void setHivePos(BlockPos pos);

    boolean hasNectar();

    boolean hasStung();

    void onHoneyDelivered();

    PollinateGoal getPollinateGoal();

    void setHasNectar(boolean p_226447_1_);

    BlockPos getSavedFlowerPos();

    void setSavedFlowerPos(BlockPos pos);

    int getRemainingCooldownBeforeLocatingNewFlower();

    void setRemainingCooldownBeforeLocatingNewFlower(int pos);
    void setRemainingCooldownBeforeLocatingNewHive(int pos);

    boolean isWithinDistance(BlockPos pos, int i);

    boolean canEnterHive();

    void startMovingTo(BlockPos hivePos);

    boolean isTooFar(BlockPos hivePos);
}