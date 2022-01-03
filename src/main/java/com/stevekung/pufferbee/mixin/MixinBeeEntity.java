package com.stevekung.pufferbee.mixin;

import java.util.List;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.stevekung.pufferbee.entity.TemptGoalAlt;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.controller.LookController;
import net.minecraft.entity.ai.controller.MovementController;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.play.server.SChangeGameStatePacket;
import net.minecraft.pathfinding.PathNavigator;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.pathfinding.SwimmerPathNavigator;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.IServerWorld;
import net.minecraft.world.IWorldReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

@Mixin(BeeEntity.class)
public abstract class MixinBeeEntity extends AnimalEntity
{
    private final BeeEntity that = (BeeEntity) (Object) this;

    private static final Predicate<LivingEntity> ENEMY_MATCHER = living ->
    {
        Ingredient ingre = Ingredient.fromTag(ItemTags.FLOWERS);

        if (living == null)
        {
            return false;
        }
        else if (ingre.test(living.getHeldItemMainhand()) || ingre.test(living.getHeldItemOffhand()))
        {
            return false;
        }
        else if (!(living instanceof PlayerEntity) || !living.isSpectator() && !((PlayerEntity)living).isCreative())
        {
            return living.getCreatureAttribute() != CreatureAttribute.WATER;
        }
        else
        {
            return false;
        }
    };

    @Shadow
    private int timeSinceSting;

    @Shadow
    private void setNearTarget(boolean p_226452_1_) {}

    private MixinBeeEntity()
    {
        super(null, null);
    }

    @Redirect(method = "<init>", at = @At(value = "FIELD", target = "net/minecraft/entity/passive/BeeEntity.moveController : Lnet/minecraft/entity/ai/controller/MovementController;", opcode = Opcodes.PUTFIELD))
    private void newController(BeeEntity entity, MovementController defControl)
    {
        this.moveController = new MoveHelperController((BeeEntity) (Object) this);
    }

    @Redirect(method = "<init>", at = @At(value = "FIELD", target = "net/minecraft/entity/passive/BeeEntity.lookController : Lnet/minecraft/entity/ai/controller/LookController;", opcode = Opcodes.PUTFIELD))
    private void newLookController(BeeEntity entity, LookController defControl)
    {
        this.lookController = new LookController((BeeEntity) (Object) this);
    }

    @Inject(method = "<init>(Lnet/minecraft/entity/EntityType;Lnet/minecraft/world/World;)V", at = @At("RETURN"))
    private void init(EntityType<? extends BeeEntity> type, World world, CallbackInfo info)
    {
        ((MixinMobEntity)this).getMapPathPriority().clear();
        this.setPathPriority(PathNodeType.WATER, 0.0F);
    }

    @Override
    public boolean canDespawn(double distanceToClosestPlayer)
    {
        return !this.hasCustomName();
    }

    @Override
    public ILivingEntityData onInitialSpawn(IServerWorld worldIn, DifficultyInstance difficultyIn, SpawnReason reason, @Nullable ILivingEntityData spawnDataIn, @Nullable CompoundNBT dataTag)
    {
        if (spawnDataIn == null)
        {
            spawnDataIn = new AgeableEntity.AgeableData(true);
        }

        AgeableEntity.AgeableData ageableentity$ageabledata = (AgeableEntity.AgeableData)spawnDataIn;

        if (ageableentity$ageabledata.canBabySpawn() && ageableentity$ageabledata.getIndexInGroup() > 0 && this.rand.nextFloat() <= ageableentity$ageabledata.getBabySpawnProbability())
        {
            this.setGrowingAge(-24000);
        }
        ageableentity$ageabledata.incrementIndexInGroup();
        return super.onInitialSpawn(worldIn, difficultyIn, reason, spawnDataIn, dataTag);
    }

    @Override
    public float getBlockPathWeight(BlockPos pos, IWorldReader world)
    {
        return 0.0F;
    }

    @Override
    protected void updateAITasks()
    {
        super.updateAITasks();
        boolean flag = this.that.hasStung();

        if (flag)
        {
            ++this.timeSinceSting;

            if (this.timeSinceSting % 5 == 0 && this.rand.nextInt(MathHelper.clamp(1200 - this.timeSinceSting, 1, 1200)) == 0)
            {
                this.attackEntityFrom(DamageSource.GENERIC, this.getHealth());
            }
        }
        if (!this.world.isRemote)
        {
            this.that.func_241359_a_((ServerWorld)this.world, false);
        }
    }

    @Override
    public CreatureAttribute getCreatureAttribute()
    {
        return CreatureAttribute.WATER;
    }

    @Override
    @Overwrite
    protected void registerGoals()
    {
        this.goalSelector.addGoal(1, new PuffGoal((BeeEntity) (Object) this));
        this.goalSelector.addGoal(2, new BreedGoal(this, 3.0D));
        this.goalSelector.addGoal(3, new TemptGoalAlt(this, 3.25D, Ingredient.fromTag(ItemTags.FLOWERS), false));
        this.goalSelector.addGoal(4, new SwimGoal((BeeEntity) (Object) this));
        this.goalSelector.addGoal(5, new FollowParentGoal(this, 3.25D));
        this.goalSelector.addGoal(6, new MeleeAttackGoal(this, 7.2D, true));
        this.targetSelector.addGoal(1, new AngerGoal((BeeEntity) (Object) this).setCallsForHelp(new Class[0]));
        this.targetSelector.addGoal(2, new AttackPlayerGoal((BeeEntity) (Object) this));
        this.targetSelector.addGoal(3, new ResetAngerGoal<>((BeeEntity) (Object) this, true));
    }

    @Override
    public void livingTick()
    {
        super.livingTick();
        boolean anger = this.that.func_233678_J__() && !this.that.hasStung() && this.getAttackTarget() != null;

        if (!this.world.isRemote)
        {
            boolean flag = anger && this.getAttackTarget().getDistanceSq(this) < 4.0D;
            this.setNearTarget(flag);
        }
        if (this.isAlive() && !anger)
        {
            for (LivingEntity mobentity : this.world.getEntitiesWithinAABB(LivingEntity.class, this.getBoundingBox().grow(0.3D), ENEMY_MATCHER))
            {
                if (mobentity.isAlive())
                {
                    this.attack(mobentity);
                }
            }
        }
    }

    @Override
    public ActionResultType func_230254_b_(PlayerEntity p_230254_1_, Hand p_230254_2_) {
        ItemStack itemstack = p_230254_1_.getHeldItem(p_230254_2_);
        if (itemstack.getItem() == Items.WATER_BUCKET && this.isAlive()) {
            this.playSound(SoundEvents.ITEM_BUCKET_FILL_FISH, 1.0F, 1.0F);
            itemstack.shrink(1);
            ItemStack itemstack1 = new ItemStack(Items.PUFFERFISH_BUCKET);
            if (!this.world.isRemote) {
                CriteriaTriggers.FILLED_BUCKET.trigger((ServerPlayerEntity)p_230254_1_, itemstack1);
            }

            if (itemstack.isEmpty()) {
                p_230254_1_.setHeldItem(p_230254_2_, itemstack1);
            } else if (!p_230254_1_.inventory.addItemStackToInventory(itemstack1)) {
                p_230254_1_.dropItem(itemstack1, false);
            }

            this.remove();
            return ActionResultType.func_233537_a_(this.world.isRemote);
        } else {
            return super.func_230254_b_(p_230254_1_, p_230254_2_);
        }
    }

    @Override
    public boolean isNotColliding(IWorldReader worldIn)
    {
        return worldIn.checkNoEntityCollision((BeeEntity) (Object) this);
    }

    @Override
    public boolean canBreatheUnderwater()
    {
        return true;
    }

    @Override
    public void onCollideWithPlayer(PlayerEntity player)
    {
        Ingredient ingre = Ingredient.fromTag(ItemTags.FLOWERS);

        if (player instanceof ServerPlayerEntity && !(ingre.test(player.getHeldItemMainhand()) || ingre.test(player.getHeldItemOffhand())) && player.attackEntityFrom(DamageSource.causeMobDamage(this), 1))
        {
            if (!this.isSilent())
            {
                ((ServerPlayerEntity)player).connection.sendPacket(new SChangeGameStatePacket(SChangeGameStatePacket.field_241773_j_, 0.0F));
            }
            player.addPotionEffect(new EffectInstance(Effects.POISON, 60, 0));
        }
    }

    private void attack(LivingEntity entity)
    {
        if (entity.attackEntityFrom(DamageSource.causeMobDamage(this), 1))
        {
            entity.addPotionEffect(new EffectInstance(Effects.POISON, 60, 0));
            this.playSound(SoundEvents.ENTITY_PUFFER_FISH_STING, 1.0F, 1.0F);
        }
    }

    private void updateAir(int p_209207_1_)
    {
        if (this.isAlive() && !this.isInWaterOrBubbleColumn())
        {
            this.setAir(p_209207_1_ - 1);

            if (this.getAir() == -20)
            {
                this.setAir(0);
                this.attackEntityFrom(DamageSource.DROWN, 2.0F);
            }
        }
        else
        {
            this.setAir(300);
        }
    }

    @Override
    public void baseTick()
    {
        int i = this.getAir();
        super.baseTick();
        this.updateAir(i);
    }

    @Override
    public boolean isPushedByWater()
    {
        return false;
    }

    @Overwrite
    private boolean canEnterHive()
    {
        return false;
    }

    @Override
    protected PathNavigator createNavigator(World world)
    {
        return new SwimmerPathNavigator((BeeEntity) (Object) this, world);
    }

    @Override
    public void travel(Vector3d travelVector)
    {
        if (this.isServerWorld() && this.isInWater())
        {
            this.moveRelative(0.01F, travelVector);
            this.move(MoverType.SELF, this.getMotion());
            this.setMotion(this.getMotion().scale(0.9D));

            if (this.getAttackTarget() == null)
            {
                this.setMotion(this.getMotion().add(0.0D, -0.005D, 0.0D));
            }
        }
        else
        {
            super.travel(travelVector);
        }
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
            return super.attackEntityFrom(source, amount);
        }
    }

    class SwimGoal extends RandomSwimmingGoal
    {
        public SwimGoal(CreatureEntity creature)
        {
            super(creature, 5.0D, 10);
        }
    }

    static class MoveHelperController extends MovementController
    {
        private final MobEntity fish;

        MoveHelperController(MobEntity fish)
        {
            super(fish);
            this.fish = fish;
        }

        @Override
        public void tick()
        {
            if (this.fish.areEyesInFluid(FluidTags.WATER))
            {
                this.fish.setMotion(this.fish.getMotion().add(0.0D, 0.005D, 0.0D));
            }

            if (this.action == MovementController.Action.MOVE_TO && !this.fish.getNavigator().noPath())
            {
                float f = (float)(this.speed * this.fish.getAttributeValue(Attributes.MOVEMENT_SPEED));
                this.fish.setAIMoveSpeed(MathHelper.lerp(0.125F, this.fish.getAIMoveSpeed(), f));
                double d0 = this.posX - this.fish.getPosX();
                double d1 = this.posY - this.fish.getPosY();
                double d2 = this.posZ - this.fish.getPosZ();

                if (d1 != 0.0D)
                {
                    double d3 = MathHelper.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
                    this.fish.setMotion(this.fish.getMotion().add(0.0D, this.fish.getAIMoveSpeed() * (d1 / d3) * 0.1D, 0.0D));
                }
                if (d0 != 0.0D || d2 != 0.0D)
                {
                    float f1 = (float)(MathHelper.atan2(d2, d0) * (180F / (float)Math.PI)) - 90.0F;
                    this.fish.rotationYaw = this.limitAngle(this.fish.rotationYaw, f1, 90.0F);
                    this.fish.renderYawOffset = this.fish.rotationYaw;
                }
            }
            else
            {
                this.fish.setAIMoveSpeed(0.0F);
            }
        }
    }

    class AngerGoal extends HurtByTargetGoal
    {
        AngerGoal(BeeEntity beeIn)
        {
            super(beeIn);
        }

        @Override
        public boolean shouldContinueExecuting()
        {
            return MixinBeeEntity.this.that.func_233678_J__() && super.shouldContinueExecuting();
        }

        @Override
        protected void setAttackTarget(MobEntity mobIn, LivingEntity targetIn)
        {
            if (mobIn instanceof BeeEntity && this.goalOwner.canEntityBeSeen(targetIn))
            {
                mobIn.setAttackTarget(targetIn);
            }
        }
    }

    static class AttackPlayerGoal extends NearestAttackableTargetGoal<PlayerEntity>
    {
        AttackPlayerGoal(BeeEntity beeIn)
        {
            super(beeIn, PlayerEntity.class, 10, true, false, beeIn::func_233680_b_);
        }

        @Override
        public boolean shouldExecute()
        {
            return this.canSting() && super.shouldExecute();
        }

        @Override
        public boolean shouldContinueExecuting()
        {
            boolean flag = this.canSting();

            if (flag && this.goalOwner.getAttackTarget() != null)
            {
                return super.shouldContinueExecuting();
            }
            else
            {
                this.target = null;
                return false;
            }
        }

        @Override
        public void tick()
        {
            boolean flag = this.canSting();

            if (flag && this.goalOwner.getAttackTarget() != null)
            {
                this.goalOwner.getNavigator().tryMoveToXYZ(this.goalOwner.getAttackTarget().getPosX(), this.goalOwner.getAttackTarget().getPosY(), this.goalOwner.getAttackTarget().getPosZ(), 7.0D);
            }
        }

        private boolean canSting()
        {
            BeeEntity beeentity = (BeeEntity)this.goalOwner;
            return beeentity.func_233678_J__() && !beeentity.hasStung();
        }
    }

    static class PuffGoal extends Goal
    {
        private final BeeEntity fish;

        public PuffGoal(BeeEntity fish)
        {
            this.fish = fish;
        }

        @Override
        public boolean shouldExecute()
        {
            List<LivingEntity> list = this.fish.world.getEntitiesWithinAABB(LivingEntity.class, this.fish.getBoundingBox().grow(2.0D), ENEMY_MATCHER);
            return !list.isEmpty();
        }

        @Override
        public boolean shouldContinueExecuting()
        {
            List<LivingEntity> list = this.fish.world.getEntitiesWithinAABB(LivingEntity.class, this.fish.getBoundingBox().grow(2.0D), ENEMY_MATCHER);
            return !list.isEmpty();
        }
    }
}