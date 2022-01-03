package com.stevekung.pufferbee.mixin;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.stevekung.pufferbee.entity.pufferfish.FindBeehiveGoal;
import com.stevekung.pufferbee.entity.pufferfish.IBeeFish;
import com.stevekung.pufferbee.entity.pufferfish.PassiveGoal;
import com.stevekung.pufferbee.entity.pufferfish.PollinateGoal;

import net.minecraft.block.*;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.entity.ai.attributes.AttributeModifierManager;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.attributes.ModifiableAttributeInstance;
import net.minecraft.entity.ai.controller.FlyingMovementController;
import net.minecraft.entity.ai.controller.LookController;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.passive.IFlyingAnimal;
import net.minecraft.entity.passive.fish.AbstractFishEntity;
import net.minecraft.entity.passive.fish.PufferfishEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.network.play.server.SChangeGameStatePacket;
import net.minecraft.particles.IParticleData;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.pathfinding.FlyingPathNavigator;
import net.minecraft.pathfinding.PathNavigator;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.state.IntegerProperty;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ITag;
import net.minecraft.tileentity.BeehiveTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.village.PointOfInterest;
import net.minecraft.village.PointOfInterestManager;
import net.minecraft.village.PointOfInterestType;
import net.minecraft.world.Difficulty;
import net.minecraft.world.IWorldReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@Mixin(PufferfishEntity.class)
public abstract class MixinPufferfishEntity extends AbstractFishEntity implements IAngerable, IFlyingAnimal, IBeeFish
{
    private final PufferfishEntity that = (PufferfishEntity) (Object) this;

    private static final Predicate<LivingEntity> ENEMY_MATCHER = p_210139_0_ ->
    {
        if (p_210139_0_ == null) {
            return false;
        }
        else if (p_210139_0_ instanceof PlayerEntity && ((PlayerEntity)p_210139_0_).inventory.armorInventory.get(3).getItem() == Items.TURTLE_HELMET)
        {
            return false;
        }
        else if (!(p_210139_0_ instanceof PlayerEntity) || !p_210139_0_.isSpectator() && !((PlayerEntity)p_210139_0_).isCreative()) {
            return !(p_210139_0_ instanceof PufferfishEntity);
        }
        else {
            return false;
        }
    };
    private static final DataParameter<Byte> DATA_FLAGS_ID = EntityDataManager.createKey(PufferfishEntity.class, DataSerializers.BYTE);
    private static final DataParameter<Integer> ANGER_TIME = EntityDataManager.createKey(PufferfishEntity.class, DataSerializers.VARINT);
    private static final RangedInteger field_234180_bw_ = TickRangeConverter.convertRange(20, 39);
    private UUID lastHurtBy;
    private float rollAmount;
    private float rollAmountO;
    private int timeSinceSting;
    private int ticksWithoutNectarSinceExitingHive;
    private int stayOutOfHiveCountdown;
    private int numCropsGrownSincePollination;
    private int remainingCooldownBeforeLocatingNewHive = 0;
    private int remainingCooldownBeforeLocatingNewFlower = 0;
    @Nullable
    private BlockPos savedFlowerPos = null;
    @Nullable
    private BlockPos hivePos = null;
    private PollinateGoal pollinateGoal;
    private FindBeehiveGoal findBeehiveGoal;
    private FindFlowerGoal findFlowerGoal;
    private AttributeModifierManager attributes;

    @Shadow
    private void attack(MobEntity p_205719_1_) {}

    private MixinPufferfishEntity()
    {
        super(null, null);
    }

    @Inject(method = "<init>(Lnet/minecraft/entity/EntityType;Lnet/minecraft/world/World;)V", at = @At("RETURN"))
    private void init(EntityType<? extends PufferfishEntity> type, World world, CallbackInfo info)
    {
        ((MixinMobEntity)this).getMapPathPriority().clear();
        this.lookController = new BeeLookController((PufferfishEntity) (Object) this);
        this.moveController = new FlyingMovementController(this, 20, true);
        this.setPathPriority(PathNodeType.DANGER_FIRE, -1.0F);
        this.setPathPriority(PathNodeType.WATER, -1.0F);
        this.setPathPriority(PathNodeType.WATER_BORDER, 16.0F);
        this.setPathPriority(PathNodeType.COCOA, -1.0F);
        this.setPathPriority(PathNodeType.FENCE, -1.0F);
    }

    @Inject(method = "registerData()V", at = @At("RETURN"))
    private void registerData(CallbackInfo info)
    {
        this.dataManager.register(DATA_FLAGS_ID, (byte)0);
        this.dataManager.register(ANGER_TIME, 0);
    }

    @Inject(method = "writeAdditional(Lnet/minecraft/nbt/CompoundNBT;)V", at = @At("RETURN"))
    private void writeAdditional(CompoundNBT compound, CallbackInfo info)
    {
        if (this.hasHive())
        {
            compound.put("HivePos", NBTUtil.writeBlockPos(this.getHivePos()));
        }
        if (this.hasFlower())
        {
            compound.put("FlowerPos", NBTUtil.writeBlockPos(this.getFlowerPos()));
        }

        compound.putBoolean("HasNectar", this.hasNectar());
        compound.putBoolean("HasStung", this.hasStung());
        compound.putInt("TicksSincePollination", this.ticksWithoutNectarSinceExitingHive);
        compound.putInt("CannotEnterHiveTicks", this.stayOutOfHiveCountdown);
        compound.putInt("CropsGrownSincePollination", this.numCropsGrownSincePollination);
        this.writeAngerNBT(compound);
    }

    @Inject(method = "readAdditional(Lnet/minecraft/nbt/CompoundNBT;)V", at = @At("RETURN"))
    private void readAdditional(CompoundNBT compound, CallbackInfo info)
    {
        this.hivePos = null;

        if (compound.contains("HivePos"))
        {
            this.hivePos = NBTUtil.readBlockPos(compound.getCompound("HivePos"));
        }

        this.savedFlowerPos = null;

        if (compound.contains("FlowerPos"))
        {
            this.savedFlowerPos = NBTUtil.readBlockPos(compound.getCompound("FlowerPos"));
        }

        this.setHasNectar(compound.getBoolean("HasNectar"));
        this.setHasStung(compound.getBoolean("HasStung"));
        this.ticksWithoutNectarSinceExitingHive = compound.getInt("TicksSincePollination");
        this.stayOutOfHiveCountdown = compound.getInt("CannotEnterHiveTicks");
        this.numCropsGrownSincePollination = compound.getInt("CropsGrownSincePollination");
        this.readAngerNBT((ServerWorld)this.world, compound);
    }

    @Inject(method = "registerGoals()V", at = @At("RETURN"))
    private void registerGoals(CallbackInfo info)
    {
        ((MixinGoalSelector)this.goalSelector).getGoals().clear();
        this.goalSelector.addGoal(0, new PuffGoal((PufferfishEntity) (Object) this));
        this.goalSelector.addGoal(1, new StingGoal(this, 1.4F, true));
        this.goalSelector.addGoal(2, new EnterBeehiveGoal((PufferfishEntity) (Object) this));
        this.pollinateGoal = new PollinateGoal((PufferfishEntity) (Object) this);
        this.goalSelector.addGoal(3, this.pollinateGoal);
        this.goalSelector.addGoal(4, new UpdateBeehiveGoal((PufferfishEntity) (Object) this));
        this.findBeehiveGoal = new FindBeehiveGoal((PufferfishEntity) (Object) this);
        this.goalSelector.addGoal(5, this.findBeehiveGoal);
        this.findFlowerGoal = new FindFlowerGoal((PufferfishEntity) (Object) this);
        this.goalSelector.addGoal(6, this.findFlowerGoal);
        this.goalSelector.addGoal(7, new FindPollinationTargetGoal((PufferfishEntity) (Object) this));
        this.goalSelector.addGoal(8, new WanderGoal());
        this.targetSelector.addGoal(1, new AngerGoal((PufferfishEntity) (Object) this).setCallsForHelp(new Class[0]));
        this.targetSelector.addGoal(2, new AttackPlayerGoal((PufferfishEntity) (Object) this));
        this.targetSelector.addGoal(3, new ResetAngerGoal<>(this, true));
    }

    @Inject(method = "tick()V", at = @At("RETURN"))
    private void tick(CallbackInfo info)
    {
        if (this.hasNectar() && this.getCropsGrownSincePollination() < 10 && this.rand.nextFloat() < 0.05F) {
            for(int i = 0; i < this.rand.nextInt(2) + 1; ++i) {
                this.addParticle(this.world, this.getPosX() - 0.3F, this.getPosX() + 0.3F, this.getPosZ() - 0.3F, this.getPosZ() + 0.3F, this.getPosYHeight(0.5D), ParticleTypes.FALLING_NECTAR);
            }
        }
        this.updateBodyPitch();
    }

    @Override
    @Overwrite
    public void livingTick()
    {
        if (this.jumpTicks > 0) {
            --this.jumpTicks;
        }

        if (this.canPassengerSteer()) {
            this.newPosRotationIncrements = 0;
            this.setPacketCoordinates(this.getPosX(), this.getPosY(), this.getPosZ());
        }

        if (this.newPosRotationIncrements > 0) {
            double d0 = this.getPosX() + (this.interpTargetX - this.getPosX()) / this.newPosRotationIncrements;
            double d2 = this.getPosY() + (this.interpTargetY - this.getPosY()) / this.newPosRotationIncrements;
            double d4 = this.getPosZ() + (this.interpTargetZ - this.getPosZ()) / this.newPosRotationIncrements;
            double d6 = MathHelper.wrapDegrees(this.interpTargetYaw - this.rotationYaw);
            this.rotationYaw = (float)(this.rotationYaw + d6 / this.newPosRotationIncrements);
            this.rotationPitch = (float)(this.rotationPitch + (this.interpTargetPitch - this.rotationPitch) / this.newPosRotationIncrements);
            --this.newPosRotationIncrements;
            this.setPosition(d0, d2, d4);
            this.setRotation(this.rotationYaw, this.rotationPitch);
        } else if (!this.isServerWorld()) {
            this.setMotion(this.getMotion().scale(0.98D));
        }

        if (this.interpTicksHead > 0) {
            this.rotationYawHead = (float)(this.rotationYawHead + MathHelper.wrapDegrees(this.interpTargetHeadYaw - this.rotationYawHead) / this.interpTicksHead);
            --this.interpTicksHead;
        }

        Vector3d vector3d = this.getMotion();
        double d1 = vector3d.x;
        double d3 = vector3d.y;
        double d5 = vector3d.z;
        if (Math.abs(vector3d.x) < 0.003D) {
            d1 = 0.0D;
        }

        if (Math.abs(vector3d.y) < 0.003D) {
            d3 = 0.0D;
        }

        if (Math.abs(vector3d.z) < 0.003D) {
            d5 = 0.0D;
        }

        this.setMotion(d1, d3, d5);
        this.world.getProfiler().startSection("ai");
        if (this.isMovementBlocked()) {
            this.isJumping = false;
            this.moveStrafing = 0.0F;
            this.moveForward = 0.0F;
        } else if (this.isServerWorld()) {
            this.world.getProfiler().startSection("newAi");
            this.updateEntityActionState();
            this.world.getProfiler().endSection();
        }

        this.world.getProfiler().endSection();
        this.world.getProfiler().startSection("jump");
        if (this.isJumping && this.func_241208_cS_()) {
            double d7;
            if (this.isInLava()) {
                d7 = this.func_233571_b_(FluidTags.LAVA);
            } else {
                d7 = this.func_233571_b_(FluidTags.WATER);
            }

            boolean flag = this.isInWater() && d7 > 0.0D;
            double d8 = this.func_233579_cu_();
            if (!flag || this.onGround && !(d7 > d8)) {
                if (!this.isInLava() || this.onGround && !(d7 > d8)) {
                    if ((this.onGround || flag && d7 <= d8) && this.jumpTicks == 0) {
                        this.jump();
                        this.jumpTicks = 10;
                    }
                } else {
                    this.handleFluidJump(FluidTags.LAVA);
                }
            } else {
                this.handleFluidJump(FluidTags.WATER);
            }
        } else {
            this.jumpTicks = 0;
        }

        this.world.getProfiler().endSection();
        this.world.getProfiler().startSection("travel");
        this.moveStrafing *= 0.98F;
        this.moveForward *= 0.98F;
        AxisAlignedBB axisalignedbb = this.getBoundingBox();
        this.travel(new Vector3d(this.moveStrafing, this.moveVertical, this.moveForward));
        this.world.getProfiler().endSection();
        this.world.getProfiler().startSection("push");
        if (this.spinAttackDuration > 0) {
            --this.spinAttackDuration;
            this.updateSpinAttack(axisalignedbb, this.getBoundingBox());
        }

        this.collideWithNearbyEntities();
        this.world.getProfiler().endSection();

        if (this.isAlive() && this.that.getPuffState() > 0) {
            for(MobEntity mobentity : this.world.getEntitiesWithinAABB(MobEntity.class, this.getBoundingBox().grow(0.3D), ENEMY_MATCHER)) {
                if (mobentity.isAlive()) {
                    this.attack(mobentity);
                }
            }
        }

        if (!this.world.isRemote) {
            if (this.stayOutOfHiveCountdown > 0) {
                --this.stayOutOfHiveCountdown;
            }

            if (this.remainingCooldownBeforeLocatingNewHive > 0) {
                --this.remainingCooldownBeforeLocatingNewHive;
            }

            if (this.remainingCooldownBeforeLocatingNewFlower > 0) {
                --this.remainingCooldownBeforeLocatingNewFlower;
            }

            boolean flag = this.func_233678_J__() && !this.hasStung() && this.getAttackTarget() != null && this.getAttackTarget().getDistanceSq(this) < 4.0D;
            this.setNearTarget(flag);
            if (this.ticksExisted % 20 == 0 && !this.isHiveValid()) {
                this.hivePos = null;
            }
        }
        else
        {
            if (this.func_233678_J__() && this.rand.nextInt(20) == 0)
            {
                this.spawnParticles(ParticleTypes.ANGRY_VILLAGER);
            }
        }
    }

    private static AttributeModifierMap.MutableAttribute func_234182_eX_() {
        return MobEntity.func_233666_p_().createMutableAttribute(Attributes.MAX_HEALTH, 10.0D).createMutableAttribute(Attributes.FLYING_SPEED, 0.6F).createMutableAttribute(Attributes.MOVEMENT_SPEED, 0.3F).createMutableAttribute(Attributes.ATTACK_DAMAGE, 2.0D).createMutableAttribute(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    public void onCollideWithPlayer(PlayerEntity entityIn)
    {
        int i = this.that.getPuffState();
        if (entityIn instanceof ServerPlayerEntity && i > 0 && entityIn.inventory.armorInventory.get(3).getItem() != Items.TURTLE_HELMET && entityIn.attackEntityFrom(DamageSource.causeMobDamage(this), 1 + i)) {
            if (!this.isSilent()) {
                ((ServerPlayerEntity)entityIn).connection.sendPacket(new SChangeGameStatePacket(SChangeGameStatePacket.field_241773_j_, 0.0F));
            }
            entityIn.addPotionEffect(new EffectInstance(Effects.POISON, 60 * i, 0));
        }
    }

    @OnlyIn(Dist.CLIENT)
    private void spawnParticles(IParticleData particleData)
    {
        double d0 = this.rand.nextGaussian() * 0.02D;
        double d1 = this.rand.nextGaussian() * 0.02D;
        double d2 = this.rand.nextGaussian() * 0.02D;
        this.world.addParticle(particleData, this.getPosXRandom(1.0D), this.getPosYRandom() + 1.0D, this.getPosZRandom(1.0D), d0, d1, d2);
    }

    @Override
    public AttributeModifierManager getAttributeManager()
    {
        if (this.attributes == null)
        {
            this.attributes = new AttributeModifierManager(func_234182_eX_().create());
        }
        return this.attributes;
    }

    @Override
    public boolean canDespawn(double distanceToClosestPlayer)
    {
        return false;
    }

    @Override
    protected void updateAir(int p_209207_1_)
    {
        this.setAir(300);
    }

    @Override
    protected ActionResultType func_230254_b_(PlayerEntity p_230254_1_, Hand p_230254_2_)
    {
        return ActionResultType.PASS;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void travel(Vector3d travelVector)
    {
        if (this.isServerWorld() || this.canPassengerSteer()) {
            double d0 = 0.08D;
            ModifiableAttributeInstance gravity = this.getAttribute(net.minecraftforge.common.ForgeMod.ENTITY_GRAVITY.get());
            boolean flag = this.getMotion().y <= 0.0D;
            if (flag && this.isPotionActive(Effects.SLOW_FALLING)) {
                if (!gravity.hasModifier(((MixinLivingEntity)this).getSlowFalling()))
                {
                    gravity.applyNonPersistentModifier(((MixinLivingEntity)this).getSlowFalling());
                }
                this.fallDistance = 0.0F;
            } else if (gravity.hasModifier(((MixinLivingEntity)this).getSlowFalling())) {
                gravity.removeModifier(((MixinLivingEntity)this).getSlowFalling());
            }
            d0 = gravity.getValue();

            FluidState fluidstate = this.world.getFluidState(this.getPosition());
            if (this.isInWater() && this.func_241208_cS_() && !this.func_230285_a_(fluidstate.getFluid())) {
                double d8 = this.getPosY();
                float f5 = this.isSprinting() ? 0.9F : this.getWaterSlowDown();
                float f6 = 0.02F;
                float f7 = EnchantmentHelper.getDepthStriderModifier(this);
                if (f7 > 3.0F) {
                    f7 = 3.0F;
                }

                if (!this.onGround) {
                    f7 *= 0.5F;
                }

                if (f7 > 0.0F) {
                    f5 += (0.54600006F - f5) * f7 / 3.0F;
                    f6 += (this.getAIMoveSpeed() - f6) * f7 / 3.0F;
                }

                if (this.isPotionActive(Effects.DOLPHINS_GRACE)) {
                    f5 = 0.96F;
                }

                f6 *= (float)this.getAttribute(net.minecraftforge.common.ForgeMod.SWIM_SPEED.get()).getValue();
                this.moveRelative(f6, travelVector);
                this.move(MoverType.SELF, this.getMotion());
                Vector3d vector3d6 = this.getMotion();
                if (this.collidedHorizontally && this.isOnLadder()) {
                    vector3d6 = new Vector3d(vector3d6.x, 0.2D, vector3d6.z);
                }

                this.setMotion(vector3d6.mul(f5, 0.8F, f5));
                Vector3d vector3d2 = this.func_233626_a_(d0, flag, this.getMotion());
                this.setMotion(vector3d2);
                if (this.collidedHorizontally && this.isOffsetPositionInLiquid(vector3d2.x, vector3d2.y + 0.6F - this.getPosY() + d8, vector3d2.z)) {
                    this.setMotion(vector3d2.x, 0.3F, vector3d2.z);
                }
            } else if (this.isInLava() && this.func_241208_cS_() && !this.func_230285_a_(fluidstate.getFluid())) {
                double d7 = this.getPosY();
                this.moveRelative(0.02F, travelVector);
                this.move(MoverType.SELF, this.getMotion());
                if (this.func_233571_b_(FluidTags.LAVA) <= this.func_233579_cu_()) {
                    this.setMotion(this.getMotion().mul(0.5D, 0.8F, 0.5D));
                    Vector3d vector3d3 = this.func_233626_a_(d0, flag, this.getMotion());
                    this.setMotion(vector3d3);
                } else {
                    this.setMotion(this.getMotion().scale(0.5D));
                }

                if (!this.hasNoGravity()) {
                    this.setMotion(this.getMotion().add(0.0D, -d0 / 4.0D, 0.0D));
                }

                Vector3d vector3d4 = this.getMotion();
                if (this.collidedHorizontally && this.isOffsetPositionInLiquid(vector3d4.x, vector3d4.y + 0.6F - this.getPosY() + d7, vector3d4.z)) {
                    this.setMotion(vector3d4.x, 0.3F, vector3d4.z);
                }
            } else if (this.isElytraFlying()) {
                Vector3d vector3d = this.getMotion();
                if (vector3d.y > -0.5D) {
                    this.fallDistance = 1.0F;
                }

                Vector3d vector3d1 = this.getLookVec();
                float f = this.rotationPitch * ((float)Math.PI / 180F);
                double d1 = Math.sqrt(vector3d1.x * vector3d1.x + vector3d1.z * vector3d1.z);
                double d3 = Math.sqrt(horizontalMag(vector3d));
                double d4 = vector3d1.length();
                float f1 = MathHelper.cos(f);
                f1 = (float)((double)f1 * (double)f1 * Math.min(1.0D, d4 / 0.4D));
                vector3d = this.getMotion().add(0.0D, d0 * (-1.0D + f1 * 0.75D), 0.0D);
                if (vector3d.y < 0.0D && d1 > 0.0D) {
                    double d5 = vector3d.y * -0.1D * f1;
                    vector3d = vector3d.add(vector3d1.x * d5 / d1, d5, vector3d1.z * d5 / d1);
                }

                if (f < 0.0F && d1 > 0.0D) {
                    double d9 = d3 * -MathHelper.sin(f) * 0.04D;
                    vector3d = vector3d.add(-vector3d1.x * d9 / d1, d9 * 3.2D, -vector3d1.z * d9 / d1);
                }

                if (d1 > 0.0D) {
                    vector3d = vector3d.add((vector3d1.x / d1 * d3 - vector3d.x) * 0.1D, 0.0D, (vector3d1.z / d1 * d3 - vector3d.z) * 0.1D);
                }

                this.setMotion(vector3d.mul(0.99F, 0.98F, 0.99F));
                this.move(MoverType.SELF, this.getMotion());
                if (this.collidedHorizontally && !this.world.isRemote) {
                    double d10 = Math.sqrt(horizontalMag(this.getMotion()));
                    double d6 = d3 - d10;
                    float f2 = (float)(d6 * 10.0D - 3.0D);
                    if (f2 > 0.0F) {
                        this.playSound(this.getFallSound((int)f2), 1.0F, 1.0F);
                        this.attackEntityFrom(DamageSource.FLY_INTO_WALL, f2);
                    }
                }

                if (this.onGround && !this.world.isRemote) {
                    this.setFlag(7, false);
                }
            } else {
                BlockPos blockpos = this.getPositionUnderneath();
                float f3 = this.world.getBlockState(this.getPositionUnderneath()).getSlipperiness(this.world, this.getPositionUnderneath(), this);
                float f4 = this.onGround ? f3 * 0.91F : 0.91F;
                Vector3d vector3d5 = this.func_233633_a_(travelVector, f3);
                double d2 = vector3d5.y;
                if (this.isPotionActive(Effects.LEVITATION)) {
                    d2 += (0.05D * (this.getActivePotionEffect(Effects.LEVITATION).getAmplifier() + 1) - vector3d5.y) * 0.2D;
                    this.fallDistance = 0.0F;
                } else if (this.world.isRemote && !this.world.isBlockLoaded(blockpos)) {
                    if (this.getPosY() > 0.0D) {
                        d2 = -0.1D;
                    } else {
                        d2 = 0.0D;
                    }
                } else if (!this.hasNoGravity()) {
                    d2 -= d0;
                }

                this.setMotion(vector3d5.x * f4, d2 * 0.98F, vector3d5.z * f4);
            }
        }

        this.func_233629_a_(this, this instanceof IFlyingAnimal);
    }

    @Override
    public float getBlockPathWeight(BlockPos pos, IWorldReader worldIn)
    {
        return worldIn.getBlockState(pos).getBlock().isAir(worldIn.getBlockState(pos), worldIn, pos) ? 10.0F : 0.0F;
    }

    @Override
    protected void updateAITasks() {
        boolean flag = this.hasStung();

        if (flag) {
            ++this.timeSinceSting;
            if (this.timeSinceSting % 5 == 0 && this.rand.nextInt(MathHelper.clamp(1200 - this.timeSinceSting, 1, 1200)) == 0) {
                this.attackEntityFrom(DamageSource.GENERIC, this.getHealth());
            }
        }

        if (!this.hasNectar()) {
            ++this.ticksWithoutNectarSinceExitingHive;
        }

        if (!this.world.isRemote) {
            this.func_241359_a_((ServerWorld)this.world, false);
        }
    }

    @Override
    public boolean attackEntityAsMob(Entity entityIn)
    {
        boolean flag = entityIn.attackEntityFrom(DamageSource.causeBeeStingDamage(this), (int)this.getAttributeValue(Attributes.ATTACK_DAMAGE));

        if (flag)
        {
            this.applyEnchantments(this, entityIn);

            if (entityIn instanceof LivingEntity)
            {
                ((LivingEntity)entityIn).setBeeStingCount(((LivingEntity)entityIn).getBeeStingCount() + 1);
                int i = 0;

                if (this.world.getDifficulty() == Difficulty.NORMAL)
                {
                    i = 10;
                }
                else if (this.world.getDifficulty() == Difficulty.HARD)
                {
                    i = 18;
                }

                if (i > 0)
                {
                    ((LivingEntity)entityIn).addPotionEffect(new EffectInstance(Effects.POISON, i * 20, 0));
                }
            }

            this.setHasStung(true);
            this.func_241356_K__();
            this.playSound(SoundEvents.ENTITY_BEE_STING, 1.0F, 1.0F);
        }

        return flag;
    }

    private void addParticle(World worldIn, double p_226397_2_, double p_226397_4_, double p_226397_6_, double p_226397_8_, double posY, IParticleData particleData) {
        worldIn.addParticle(particleData, MathHelper.lerp(worldIn.rand.nextDouble(), p_226397_2_, p_226397_4_), posY, MathHelper.lerp(worldIn.rand.nextDouble(), p_226397_6_, p_226397_8_), 0.0D, 0.0D, 0.0D);
    }

    @Override
    public void startMovingTo(BlockPos pos) {
        Vector3d vector3d = Vector3d.copyCenteredHorizontally(pos);
        int i = 0;
        BlockPos blockpos = this.getPosition();
        int j = (int)vector3d.y - blockpos.getY();
        if (j > 2) {
            i = 4;
        } else if (j < -2) {
            i = -4;
        }

        int k = 6;
        int l = 8;
        int i1 = blockpos.manhattanDistance(pos);
        if (i1 < 15) {
            k = i1 / 2;
            l = i1 / 2;
        }

        Vector3d vector3d1 = RandomPositionGenerator.func_226344_b_(this, k, l, i, vector3d, (float)Math.PI / 10F);
        if (vector3d1 != null) {
            this.navigator.setRangeMultiplier(0.5F);
            this.navigator.tryMoveToXYZ(vector3d1.x, vector3d1.y, vector3d1.z, 1.0D);
        }
    }

    @Override
    @Nullable
    public BlockPos getFlowerPos() {
        return this.savedFlowerPos;
    }

    @Override
    public boolean hasFlower() {
        return this.savedFlowerPos != null;
    }

    @Override
    public void setFlowerPos(BlockPos pos) {
        this.savedFlowerPos = pos;
    }

    @Override
    public BlockPos getSavedFlowerPos()
    {
        return this.savedFlowerPos;
    }

    @Override
    public void setSavedFlowerPos(BlockPos pos)
    {
        this.savedFlowerPos = pos;
    }

    private boolean failedPollinatingTooLong() {
        return this.ticksWithoutNectarSinceExitingHive > 3600;
    }

    @Override
    public boolean canEnterHive() {
        if (this.stayOutOfHiveCountdown <= 0 && !this.pollinateGoal.isRunning() && !this.hasStung() && this.getAttackTarget() == null) {
            boolean flag = this.failedPollinatingTooLong() || this.world.isRaining() || this.world.isNightTime() || this.hasNectar();
            return flag && !this.isHiveNearFire();
        } else {
            return false;
        }
    }

    @Override
    public void setStayOutOfHiveCountdown(int p_226450_1_) {
        this.stayOutOfHiveCountdown = p_226450_1_;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public float getBodyPitch(float p_226455_1_) {
        return MathHelper.lerp(p_226455_1_, this.rollAmountO, this.rollAmount);
    }

    private void updateBodyPitch() {
        this.rollAmountO = this.rollAmount;
        if (this.isNearTarget()) {
            this.rollAmount = Math.min(1.0F, this.rollAmount + 0.2F);
        } else {
            this.rollAmount = Math.max(0.0F, this.rollAmount - 0.24F);
        }

    }

    @Override
    public boolean canBeLeashedTo(PlayerEntity player)
    {
        return !this.getLeashed();
    }

    @Override
    public PollinateGoal getPollinateGoal()
    {
        return this.pollinateGoal;
    }

    @Override
    public void resetTicksWithoutNectar() {
        this.ticksWithoutNectarSinceExitingHive = 0;
    }

    private boolean isHiveNearFire() {
        if (this.hivePos == null) {
            return false;
        } else {
            TileEntity tileentity = this.world.getTileEntity(this.hivePos);
            return tileentity instanceof BeehiveTileEntity && ((BeehiveTileEntity)tileentity).isNearFire();
        }
    }

    @Override
    public int getAngerTime() {
        return this.dataManager.get(ANGER_TIME);
    }

    @Override
    public void setAngerTime(int time) {
        this.dataManager.set(ANGER_TIME, time);
    }

    @Override
    public UUID getAngerTarget() {
        return this.lastHurtBy;
    }

    @Override
    public void setAngerTarget(@Nullable UUID target) {
        this.lastHurtBy = target;
    }

    @Override
    public void func_230258_H__() {
        this.setAngerTime(field_234180_bw_.getRandomWithinRange(this.rand));
    }

    private boolean doesHiveHaveSpace(BlockPos pos) {
        TileEntity tileentity = this.world.getTileEntity(pos);
        if (tileentity instanceof BeehiveTileEntity) {
            return !((BeehiveTileEntity)tileentity).isFullOfBees();
        } else {
            return false;
        }
    }

    @Override
    public boolean hasHive() {
        return this.hivePos != null;
    }

    @Override
    @Nullable
    public BlockPos getHivePos() {
        return this.hivePos;
    }

    @Override
    @Nullable
    public void setHivePos(BlockPos pos)
    {
        this.hivePos = pos;
    }

    private int getCropsGrownSincePollination() {
        return this.numCropsGrownSincePollination;
    }

    private void resetCropCounter() {
        this.numCropsGrownSincePollination = 0;
    }

    private void addCropCounter() {
        ++this.numCropsGrownSincePollination;
    }

    @Override
    public boolean onLivingFall(float distance, float damageMultiplier) {
        return false;
    }

    @Override
    protected void updateFallState(double y, boolean onGroundIn, BlockState state, BlockPos pos) {
    }

    @Override
    protected boolean makeFlySound() {
        return true;
    }

    private boolean isHiveValid() {
        if (!this.hasHive()) {
            return false;
        } else {
            TileEntity tileentity = this.world.getTileEntity(this.hivePos);
            return tileentity instanceof BeehiveTileEntity;
        }
    }

    @Override
    public boolean hasNectar() {
        return this.getBeeFlag(8);
    }

    @Override
    public void setHasNectar(boolean p_226447_1_) {
        if (p_226447_1_) {
            this.resetTicksWithoutNectar();
        }

        this.setBeeFlag(8, p_226447_1_);
    }

    @Override
    public boolean hasStung() {
        return this.getBeeFlag(4);
    }

    private void setHasStung(boolean p_226449_1_) {
        this.setBeeFlag(4, p_226449_1_);
    }

    private boolean isNearTarget() {
        return this.getBeeFlag(2);
    }

    private void setNearTarget(boolean p_226452_1_) {
        this.setBeeFlag(2, p_226452_1_);
    }

    @Override
    public boolean isTooFar(BlockPos pos) {
        return !this.isWithinDistance(pos, 32);
    }

    @Override
    protected void handleFluidJump(ITag<Fluid> fluidTag) {
        this.setMotion(this.getMotion().add(0.0D, 0.01D, 0.0D));
    }

    @Override
    public boolean isWithinDistance(BlockPos pos, int distance) {
        return pos.withinDistance(this.getPosition(), distance);
    }

    @Override
    public void onHoneyDelivered() {
        this.setHasNectar(false);
        this.resetCropCounter();
    }

    @Override
    public int getRemainingCooldownBeforeLocatingNewFlower()
    {
        return this.remainingCooldownBeforeLocatingNewFlower;
    }

    @Override
    public void setRemainingCooldownBeforeLocatingNewFlower(int pos)
    {
        this.remainingCooldownBeforeLocatingNewFlower = pos;
    }

    @Override
    public void setRemainingCooldownBeforeLocatingNewHive(int pos)
    {
        this.remainingCooldownBeforeLocatingNewHive = pos;
    }

    private boolean isFlowers(BlockPos pos) {
        return this.world.isBlockPresent(pos) && this.world.getBlockState(pos).getBlock().isIn(BlockTags.FLOWERS);
    }

    private void setBeeFlag(int flagId, boolean p_226404_2_) {
        if (p_226404_2_) {
            this.dataManager.set(DATA_FLAGS_ID, (byte)(this.dataManager.get(DATA_FLAGS_ID) | flagId));
        } else {
            this.dataManager.set(DATA_FLAGS_ID, (byte)(this.dataManager.get(DATA_FLAGS_ID) & ~flagId));
        }

    }

    private boolean getBeeFlag(int flagId) {
        return (this.dataManager.get(DATA_FLAGS_ID) & flagId) != 0;
    }

    @Override
    protected PathNavigator createNavigator(World worldIn) {
        FlyingPathNavigator flyingpathnavigator = new FlyingPathNavigator(this, worldIn) {
            @Override
            public boolean canEntityStandOnPos(BlockPos pos) {
                return !this.world.getBlockState(pos.down()).getBlock().isAir(this.world.getBlockState(pos.down()), worldIn, pos.down());
            }

            @Override
            public void tick() {
                if (!MixinPufferfishEntity.this.pollinateGoal.isRunning()) {
                    super.tick();
                }
            }
        };
        flyingpathnavigator.setCanOpenDoors(false);
        flyingpathnavigator.setCanSwim(false);
        flyingpathnavigator.setCanEnterDoors(true);
        return flyingpathnavigator;
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount)
    {
        if (this.isInvulnerableTo(source))
        {
            return false;
        }
        else
        {
            if (!this.world.isRemote)
            {
                this.pollinateGoal.cancel();
            }
            return super.attackEntityFrom(source, amount);
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public Vector3d func_241205_ce_() {
        return new Vector3d(0.0D, 0.5F * this.getEyeHeight(), this.getWidth() * 0.2F);
    }

    @Override
    public CreatureAttribute getCreatureAttribute() {
        return CreatureAttribute.ARTHROPOD;
    }

    static class PuffGoal extends Goal {
        private final PufferfishEntity fish;

        public PuffGoal(PufferfishEntity fish) {
            this.fish = fish;
        }

        /**
         * Returns whether execution should begin. You can also read and cache any state necessary for execution in this
         * method as well.
         */
        @Override
        public boolean shouldExecute() {
            List<LivingEntity> list = this.fish.world.getEntitiesWithinAABB(LivingEntity.class, this.fish.getBoundingBox().grow(2.0D), ENEMY_MATCHER);
            return !list.isEmpty();
        }

        /**
         * Execute a one shot task or start executing a continuous task
         */
        @Override
        public void startExecuting() {
            this.fish.puffTimer = 1;
            this.fish.deflateTimer = 0;
        }

        /**
         * Reset the task's internal state. Called when this task is interrupted by another one
         */
        @Override
        public void resetTask() {
            this.fish.puffTimer = 0;
        }

        /**
         * Returns whether an in-progress EntityAIBase should continue executing
         */
        @Override
        public boolean shouldContinueExecuting() {
            List<LivingEntity> list = this.fish.world.getEntitiesWithinAABB(LivingEntity.class, this.fish.getBoundingBox().grow(2.0D), ENEMY_MATCHER);
            return !list.isEmpty();
        }
    }

    static class AngerGoal extends HurtByTargetGoal {
        private final PufferfishEntity entity;

        public AngerGoal(PufferfishEntity beeIn) {
            super(beeIn);
            this.entity = beeIn;
        }

        /**
         * Returns whether an in-progress EntityAIBase should continue executing
         */
        @Override
        public boolean shouldContinueExecuting() {
            return ((IAngerable)this.entity).func_233678_J__() && super.shouldContinueExecuting();
        }

        @Override
        protected void setAttackTarget(MobEntity mobIn, LivingEntity targetIn) {
            if (mobIn instanceof PufferfishEntity && this.goalOwner.canEntityBeSeen(targetIn)) {
                mobIn.setAttackTarget(targetIn);
            }

        }
    }

    static class AttackPlayerGoal extends NearestAttackableTargetGoal<PlayerEntity>
    {
        private final PufferfishEntity entity;

        public AttackPlayerGoal(PufferfishEntity beeIn)
        {
            super(beeIn, PlayerEntity.class, 10, true, false, ((IAngerable)beeIn)::func_233680_b_);
            this.entity = beeIn;
        }

        /**
         * Returns whether execution should begin. You can also read and cache any state necessary for execution in this
         * method as well.
         */
        @Override
        public boolean shouldExecute() {
            return this.canSting() && super.shouldExecute();
        }

        /**
         * Returns whether an in-progress EntityAIBase should continue executing
         */
        @Override
        public boolean shouldContinueExecuting() {
            boolean flag = this.canSting();
            if (flag && this.goalOwner.getAttackTarget() != null) {
                return super.shouldContinueExecuting();
            } else {
                this.target = null;
                return false;
            }
        }

        private boolean canSting() {
            return ((IAngerable)this.entity).func_233678_J__() && !((IBeeFish)this.entity).hasStung();
        }
    }

    static class BeeLookController extends LookController {

        private final PufferfishEntity entity;

        public BeeLookController(PufferfishEntity beeIn) {
            super(beeIn);
            this.entity = beeIn;
        }

        /**
         * Updates look
         */
        @Override
        public void tick() {
            if (!((IAngerable)this.entity).func_233678_J__()) {
                super.tick();
            }
        }

        @Override
        protected boolean shouldResetPitch() {
            return !((IBeeFish)this.entity).getPollinateGoal().isRunning();
        }
    }

    class EnterBeehiveGoal extends PassiveGoal {

        public EnterBeehiveGoal(PufferfishEntity angerable)
        {
            super(angerable);
        }

        @Override
        public boolean canBeeStart() {
            if (((IBeeFish)this.angerable).hasHive() && MixinPufferfishEntity.this.canEnterHive() && MixinPufferfishEntity.this.hivePos.withinDistance(MixinPufferfishEntity.this.getPositionVec(), 2.0D)) {
                TileEntity tileentity = MixinPufferfishEntity.this.world.getTileEntity(MixinPufferfishEntity.this.hivePos);
                if (tileentity instanceof BeehiveTileEntity) {
                    BeehiveTileEntity beehivetileentity = (BeehiveTileEntity)tileentity;
                    if (!beehivetileentity.isFullOfBees()) {
                        return true;
                    }

                    MixinPufferfishEntity.this.hivePos = null;
                }
            }

            return false;
        }

        @Override
        public boolean canBeeContinue() {
            return false;
        }

        /**
         * Execute a one shot task or start executing a continuous task
         */
        @Override
        public void startExecuting() {
            TileEntity tileentity = MixinPufferfishEntity.this.world.getTileEntity(MixinPufferfishEntity.this.hivePos);
            if (tileentity instanceof BeehiveTileEntity) {
                BeehiveTileEntity beehivetileentity = (BeehiveTileEntity)tileentity;
                beehivetileentity.tryEnterHive(MixinPufferfishEntity.this, MixinPufferfishEntity.this.hasNectar());
            }

        }
    }

    class FindFlowerGoal extends PassiveGoal {
        private int ticks = MixinPufferfishEntity.this.world.rand.nextInt(10);

        public FindFlowerGoal(PufferfishEntity angerable)
        {
            super(angerable);
            this.setMutexFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canBeeStart() {
            return MixinPufferfishEntity.this.savedFlowerPos != null && !MixinPufferfishEntity.this.detachHome() && this.shouldMoveToFlower() && MixinPufferfishEntity.this.isFlowers(MixinPufferfishEntity.this.savedFlowerPos) && !MixinPufferfishEntity.this.isWithinDistance(MixinPufferfishEntity.this.savedFlowerPos, 2);
        }

        @Override
        public boolean canBeeContinue() {
            return this.canBeeStart();
        }

        /**
         * Execute a one shot task or start executing a continuous task
         */
        @Override
        public void startExecuting() {
            this.ticks = 0;
            super.startExecuting();
        }

        /**
         * Reset the task's internal state. Called when this task is interrupted by another one
         */
        @Override
        public void resetTask() {
            this.ticks = 0;
            MixinPufferfishEntity.this.navigator.clearPath();
            MixinPufferfishEntity.this.navigator.resetRangeMultiplier();
        }

        /**
         * Keep ticking a continuous task that has already been started
         */
        @Override
        public void tick() {
            if (MixinPufferfishEntity.this.savedFlowerPos != null) {
                ++this.ticks;
                if (this.ticks > 600) {
                    MixinPufferfishEntity.this.savedFlowerPos = null;
                } else if (!MixinPufferfishEntity.this.navigator.hasPath()) {
                    if (MixinPufferfishEntity.this.isTooFar(MixinPufferfishEntity.this.savedFlowerPos)) {
                        MixinPufferfishEntity.this.savedFlowerPos = null;
                    } else {
                        MixinPufferfishEntity.this.startMovingTo(MixinPufferfishEntity.this.savedFlowerPos);
                    }
                }
            }
        }

        private boolean shouldMoveToFlower() {
            return MixinPufferfishEntity.this.ticksWithoutNectarSinceExitingHive > 2400;
        }
    }

    class FindPollinationTargetGoal extends PassiveGoal {

        public FindPollinationTargetGoal(PufferfishEntity angerable)
        {
            super(angerable);
        }

        @Override
        public boolean canBeeStart() {
            if (MixinPufferfishEntity.this.getCropsGrownSincePollination() >= 10) {
                return false;
            } else if (MixinPufferfishEntity.this.rand.nextFloat() < 0.3F) {
                return false;
            } else {
                return MixinPufferfishEntity.this.hasNectar() && MixinPufferfishEntity.this.isHiveValid();
            }
        }

        @Override
        public boolean canBeeContinue() {
            return this.canBeeStart();
        }

        /**
         * Keep ticking a continuous task that has already been started
         */
        @Override
        public void tick() {
            if (MixinPufferfishEntity.this.rand.nextInt(30) == 0) {
                for(int i = 1; i <= 2; ++i) {
                    BlockPos blockpos = MixinPufferfishEntity.this.getPosition().down(i);
                    BlockState blockstate = MixinPufferfishEntity.this.world.getBlockState(blockpos);
                    Block block = blockstate.getBlock();
                    boolean flag = false;
                    IntegerProperty integerproperty = null;
                    if (block.isIn(BlockTags.BEE_GROWABLES)) {
                        if (block instanceof CropsBlock) {
                            CropsBlock cropsblock = (CropsBlock)block;
                            if (!cropsblock.isMaxAge(blockstate)) {
                                flag = true;
                                integerproperty = cropsblock.getAgeProperty();
                            }
                        } else if (block instanceof StemBlock) {
                            int j = blockstate.get(StemBlock.AGE);
                            if (j < 7) {
                                flag = true;
                                integerproperty = StemBlock.AGE;
                            }
                        } else if (block == Blocks.SWEET_BERRY_BUSH) {
                            int k = blockstate.get(SweetBerryBushBlock.AGE);
                            if (k < 3) {
                                flag = true;
                                integerproperty = SweetBerryBushBlock.AGE;
                            }
                        }

                        if (flag) {
                            MixinPufferfishEntity.this.world.playEvent(2005, blockpos, 0);
                            MixinPufferfishEntity.this.world.setBlockState(blockpos, blockstate.with(integerproperty, blockstate.get(integerproperty) + 1));
                            MixinPufferfishEntity.this.addCropCounter();
                        }
                    }
                }

            }
        }
    }



    class StingGoal extends MeleeAttackGoal {
        public StingGoal(CreatureEntity creatureIn, double speedIn, boolean useLongMemory) {
            super(creatureIn, speedIn, useLongMemory);
        }

        /**
         * Returns whether execution should begin. You can also read and cache any state necessary for execution in this
         * method as well.
         */
        @Override
        public boolean shouldExecute() {
            return super.shouldExecute() && MixinPufferfishEntity.this.func_233678_J__() && !MixinPufferfishEntity.this.hasStung();
        }

        /**
         * Returns whether an in-progress EntityAIBase should continue executing
         */
        @Override
        public boolean shouldContinueExecuting() {
            return super.shouldContinueExecuting() && MixinPufferfishEntity.this.func_233678_J__() && !MixinPufferfishEntity.this.hasStung();
        }
    }

    class UpdateBeehiveGoal extends PassiveGoal {

        public UpdateBeehiveGoal(PufferfishEntity angerable)
        {
            super(angerable);
        }

        @Override
        public boolean canBeeStart() {
            return MixinPufferfishEntity.this.remainingCooldownBeforeLocatingNewHive == 0 && !MixinPufferfishEntity.this.hasHive() && MixinPufferfishEntity.this.canEnterHive();
        }

        @Override
        public boolean canBeeContinue() {
            return false;
        }

        /**
         * Execute a one shot task or start executing a continuous task
         */
        @Override
        public void startExecuting() {
            MixinPufferfishEntity.this.remainingCooldownBeforeLocatingNewHive = 200;
            List<BlockPos> list = this.getNearbyFreeHives();
            if (!list.isEmpty()) {
                for(BlockPos blockpos : list) {
                    if (!MixinPufferfishEntity.this.findBeehiveGoal.isPossibleHive(blockpos)) {
                        MixinPufferfishEntity.this.hivePos = blockpos;
                        return;
                    }
                }

                MixinPufferfishEntity.this.findBeehiveGoal.clearPossibleHives();
                MixinPufferfishEntity.this.hivePos = list.get(0);
            }
        }

        private List<BlockPos> getNearbyFreeHives() {
            BlockPos blockpos = MixinPufferfishEntity.this.getPosition();
            PointOfInterestManager pointofinterestmanager = ((ServerWorld)MixinPufferfishEntity.this.world).getPointOfInterestManager();
            Stream<PointOfInterest> stream = pointofinterestmanager.func_219146_b(p_226486_0_ ->
            {
                return p_226486_0_ == PointOfInterestType.BEEHIVE || p_226486_0_ == PointOfInterestType.BEE_NEST;
            }, blockpos, 20, PointOfInterestManager.Status.ANY);
            return stream.map(PointOfInterest::getPos).filter(p_226487_1_ ->
            {
                return MixinPufferfishEntity.this.doesHiveHaveSpace(p_226487_1_);
            }).sorted(Comparator.comparingDouble(p_226488_1_ ->
            {
                return p_226488_1_.distanceSq(blockpos);
            })).collect(Collectors.toList());
        }
    }

    class WanderGoal extends Goal {
        public WanderGoal() {
            this.setMutexFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        /**
         * Returns whether execution should begin. You can also read and cache any state necessary for execution in this
         * method as well.
         */
        @Override
        public boolean shouldExecute() {
            return MixinPufferfishEntity.this.navigator.noPath() && MixinPufferfishEntity.this.rand.nextInt(10) == 0;
        }

        /**
         * Returns whether an in-progress EntityAIBase should continue executing
         */
        @Override
        public boolean shouldContinueExecuting() {
            return MixinPufferfishEntity.this.navigator.hasPath();
        }

        /**
         * Execute a one shot task or start executing a continuous task
         */
        @Override
        public void startExecuting() {
            Vector3d vector3d = this.getRandomLocation();
            if (vector3d != null) {
                MixinPufferfishEntity.this.navigator.setPath(MixinPufferfishEntity.this.navigator.getPathToPos(new BlockPos(vector3d), 1), 1.0D);
            }

        }

        @Nullable
        private Vector3d getRandomLocation() {
            Vector3d vector3d;
            if (MixinPufferfishEntity.this.isHiveValid() && !MixinPufferfishEntity.this.isWithinDistance(MixinPufferfishEntity.this.hivePos, 22)) {
                Vector3d vector3d1 = Vector3d.copyCentered(MixinPufferfishEntity.this.hivePos);
                vector3d = vector3d1.subtract(MixinPufferfishEntity.this.getPositionVec()).normalize();
            } else {
                vector3d = MixinPufferfishEntity.this.getLook(0.0F);
            }

            Vector3d vector3d2 = RandomPositionGenerator.findAirTarget(MixinPufferfishEntity.this, 8, 7, vector3d, (float)Math.PI / 2F, 2, 1);
            return vector3d2 != null ? vector3d2 : RandomPositionGenerator.findGroundTarget(MixinPufferfishEntity.this, 8, 4, -2, vector3d, (float)Math.PI / 2F);
        }
    }
}