package keystrokesmod.script.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import keystrokesmod.platform.ClientPlatform;
import keystrokesmod.script.ScriptClassNames;
import keystrokesmod.utility.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.FishingBobberEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.BlockItem;

public class Entity {
    public net.minecraft.entity.Entity entity;
    public String type;
    public int entityId;
    public boolean isLiving;
    public boolean isPlayer;
    public boolean isUser;
    private static HashMap<Integer, Entity> cache = new HashMap<>();

    public Entity(net.minecraft.entity.Entity entity) {
        this.entity = entity;
        if (entity != null) {
            this.type = ScriptClassNames.simpleName(entity.getClass());
            this.entityId = entity.getNetworkId();
            this.isLiving = entity instanceof LivingEntity;
            this.isPlayer = entity instanceof PlayerEntity;
            if (this.isPlayer
                    && Minecraft.getInstance().player != null
                    && entity.getUuid().equals(Minecraft.getInstance().player.getUuid())) {
                this.isUser = true;
            }
        }
    }

    public static Entity convert(net.minecraft.entity.Entity entity) {
        if (entity == null) {
            return null;
        }

        int id = entity.getNetworkId() + System.identityHashCode(entity);
        Entity cachedEntity = cache.get(id);
        if (cachedEntity == null) {
            cachedEntity = new Entity(entity);
            cache.put(id, cachedEntity);
        }

        return cachedEntity;
    }

    public static void clearCache() {
        cache.clear();
    }

    public boolean allowEditing() {
        return !(this.entity instanceof PlayerEntity) ? false : ((PlayerEntity) this.entity).abilities.canModifyWorld;
    }

    public double distanceTo(Vec3 position) {
        return this.entity.distanceTo(position.x, position.y, position.z);
    }

    public double distanceToSq(Vec3 position) {
        return this.entity.squaredDistanceTo(position.x, position.y, position.z);
    }

    public double distanceToGround() {
        return Utils.distanceToGround(this.entity);
    }

    public boolean isHoldingBlock() {
        return this.isLiving
                && ((LivingEntity) this.entity).getDisplayItemInHand() != null
                && ((LivingEntity) this.entity).getDisplayItemInHand().getItem() instanceof BlockItem;
    }

    public boolean isHoldingWeapon() {
        return this.isLiving && Utils.holdingWeapon((LivingEntity) this.entity);
    }

    public float getAbsorption() {
        return !(this.entity instanceof LivingEntity) ? -1.0F : ((LivingEntity) this.entity).getAbsorption();
    }

    public Vec3 getBlockPosition() {
        return new Vec3(
                this.entity.getCommandSourceBlockPos().getX(),
                this.entity.getCommandSourceBlockPos().getY(),
                this.entity.getCommandSourceBlockPos().getZ());
    }

    public String getDisplayName() {
        return this.entity instanceof ItemEntity
                ? ((ItemEntity) this.entity).getItem().getHoverName()
                : this.entity.getDisplayName().getString();
    }

    public Entity getRidingEntity() {
        return convert(this.entity.vehicle);
    }

    public Entity getRiddenByEntity() {
        return convert(this.entity.rider);
    }

    public Vec3 getServerPosition() {
        return new Vec3(this.entity.lastKnownX, this.entity.lastKnownY, this.entity.lastKnownZ);
    }

    public int getExperienceLevel() {
        return !(this.entity instanceof PlayerEntity) ? 0 : ((PlayerEntity) this.entity).xpLevel;
    }

    public float getExperience() {
        return !(this.entity instanceof PlayerEntity) ? 0.0F : ((PlayerEntity) this.entity).xpProgress;
    }

    public float getFallDistance() {
        return this.entity.fallDistance;
    }

    public String getUUID() {
        return this.entity.getUuid().toString();
    }

    public String getCustomNameTag() {
        return this.entity.getCustomName();
    }

    public double getBPS() {
        if (!this.isLiving) {
            return 0.0;
        }

        double x = this.entity.x - this.entity.lastX;
        double z = this.entity.z - this.entity.lastZ;
        return Math.sqrt(x * x + z * z) * 20.0;
    }

    public String getFacing() {
        return this.entity.getHorizontalFacing().name();
    }

    public float getHealth() {
        return !(this.entity instanceof LivingEntity) ? -1.0F : ((LivingEntity) this.entity).getHealth();
    }

    public boolean isSleeping() {
        return this.isPlayer ? ((PlayerEntity) this.entity).isSleeping() : false;
    }

    public float getEyeHeight() {
        return this.entity.getEyeHeight();
    }

    public float getHeight() {
        return this.entity.height;
    }

    public float getWidth() {
        return this.entity.width;
    }

    public boolean isBurning() {
        return this.entity.isOnFire();
    }

    public int getStackSize() {
        if (this.entity instanceof ItemEntity) {
            net.minecraft.item.ItemStack item = ((ItemEntity) this.entity).getItem();
            return item == null ? -1 : item.size;
        }

        if (!(this.entity instanceof LivingEntity)) {
            return -1;
        }

        net.minecraft.item.ItemStack stack = ((LivingEntity) this.entity).getDisplayItemInHand();
        return stack == null ? -1 : stack.size;
    }

    public ItemStack getHeldItem() {
        if (this.entity instanceof ItemEntity) {
            net.minecraft.item.ItemStack item = ((ItemEntity) this.entity).getItem();
            return item == null ? null : new ItemStack(item, (byte) 0);
        }

        if (!(this.entity instanceof LivingEntity)) {
            return null;
        }

        net.minecraft.item.ItemStack stack = ((LivingEntity) this.entity).getDisplayItemInHand();
        return stack == null ? null : new ItemStack(stack, (byte) 0);
    }

    public int getHurtTime() {
        return !(this.entity instanceof LivingEntity) ? -1 : ((LivingEntity) this.entity).damagedTimer;
    }

    public boolean isConsuming() {
        return Utils.isConsuming(this.entity);
    }

    public Vec3 getLastPosition() {
        return new Vec3(this.entity.prevX, this.entity.prevY, this.entity.prevZ);
    }

    public float getMaxHealth() {
        return !(this.entity instanceof LivingEntity) ? -1.0F : ((LivingEntity) this.entity).getMaxHealth();
    }

    public int getMaxHurtTime() {
        return !(this.entity instanceof LivingEntity) ? -1 : ((LivingEntity) this.entity).damagedTime;
    }

    public String getName() {
        return this.entity instanceof ItemEntity
                ? ClientPlatform.registryName(
                                ((ItemEntity) this.entity).getItem().getItem())
                        .substring(10)
                : this.entity.getName();
    }

    public NetworkPlayer getNetworkPlayer() {
        return NetworkPlayer.convert(
                Minecraft.getInstance().getNetworkHandler().getOnlinePlayer(this.entity.getUuid()));
    }

    public float getPitch() {
        return this.entity.pitch;
    }

    public Vec3 getPosition() {
        return this.entity == null ? null : new Vec3(this.entity.x, this.entity.y, this.entity.z);
    }

    public List<Object[]> getPotionEffects() {
        List<Object[]> potionEffects = new ArrayList<>();
        if (!(this.entity instanceof LivingEntity)) {
            return potionEffects;
        }

        for (StatusEffectInstance potionEffect : ((LivingEntity) this.entity).getStatusEffects()) {
            Object[] potionData = new Object[] {
                potionEffect.getId(), potionEffect.getName(), potionEffect.getAmplifier(), potionEffect.getDuration()
            };
            potionEffects.add(potionData);
        }

        return potionEffects;
    }

    public ItemStack getArmorInSlot(int slot) {
        return this.isPlayer && slot >= 0 && slot <= 3
                ? ItemStack.convert(((PlayerEntity) this.entity).inventory.armor[slot])
                : null;
    }

    public double getSpeed() {
        return Utils.getHorizontalSpeed(this.entity);
    }

    public int getSwingProgress() {
        return this.isLiving ? ((LivingEntity) this.entity).armSwingingTicks : -1;
    }

    public float getPrevSwingProgress() {
        return this.isLiving ? ((LivingEntity) this.entity).lastAttackAnimationProgress : -1.0F;
    }

    public int getTicksExisted() {
        return this.entity.ticks;
    }

    public float getYaw() {
        return this.entity.yaw;
    }

    public int getFireResistance() {
        return this.entity.safeOnFireTime;
    }

    public float getPrevYaw() {
        return this.entity.lastYaw;
    }

    public void setPrevYaw(float prevYaw) {
        this.entity.lastYaw = prevYaw;
    }

    public void setPrevPitch(float prevPitch) {
        this.entity.lastPitch = prevPitch;
    }

    public float getPrevPitch() {
        return this.entity.lastPitch;
    }

    public boolean isCreative() {
        return !(this.entity instanceof PlayerEntity) ? false : ((PlayerEntity) this.entity).abilities.creativeMode;
    }

    public boolean isCollided() {
        return !(this.entity instanceof PlayerEntity)
                ? Minecraft.getInstance()
                        .world
                        .containsNonAir(this.entity.getShape().grown(0.05, 0.0, 0.05))
                : this.entity.colliding;
    }

    public boolean isCollidedHorizontally() {
        return this.entity.collidingHorizontally;
    }

    public boolean isCollidedVertically() {
        return this.entity.collidingVertically;
    }

    public boolean isDead() {
        return this.entity.removed || this.isLiving && ((LivingEntity) this.entity).deathTicks > 0;
    }

    public int getHunger() {
        return this.isPlayer && ((PlayerEntity) this.entity).getHungerManager() != null
                ? ((PlayerEntity) this.entity).getHungerManager().getFoodLevel()
                : 0;
    }

    public float getSaturation() {
        return this.isPlayer && ((PlayerEntity) this.entity).getHungerManager() != null
                ? ((PlayerEntity) this.entity).getHungerManager().getSaturationLevel()
                : 0.0F;
    }

    public float getAir() {
        return this.entity.getBreath();
    }

    public boolean isInvisible() {
        return this.entity.isInvisible();
    }

    public boolean isInWater() {
        return this.entity.isInWater();
    }

    public boolean isInLava() {
        return this.entity.isInLava();
    }

    public Entity getFisher() {
        return this.entity instanceof FishingBobberEntity ? convert(((FishingBobberEntity) this.entity).thrower) : null;
    }

    public boolean isInLiquid() {
        return !this.entity.canMove(0.0, 0.0, 0.0);
    }

    public boolean isOnLadder() {
        return this.isLiving && ((LivingEntity) this.entity).isClimbing();
    }

    public boolean isOnEdge() {
        return Utils.onEdge(this.entity);
    }

    public boolean isSprinting() {
        return this.entity.isSprinting();
    }

    public boolean isSneaking() {
        return this.entity.isSneaking();
    }

    public boolean isUsingItem() {
        return !(this.entity instanceof PlayerEntity) ? false : ((PlayerEntity) this.entity).hasItemInUse();
    }

    public boolean onGround() {
        return this.entity.onGround;
    }

    public void setMotion(double x, double y, double z) {
        this.entity.velocityX = x;
        this.entity.velocityY = y;
        this.entity.velocityZ = z;
    }

    public Vec3 getMotion() {
        return new Vec3(this.entity.velocityX, this.entity.velocityY, this.entity.velocityZ);
    }

    public void setPitch(float pitch) {
        this.entity.pitch = pitch;
    }

    public void setYaw(float yaw) {
        this.entity.yaw = yaw;
    }

    public void moveTo(Vec3 position) {
        if (position != null) {
            this.moveTo(position.x, position.y, position.z);
        }
    }

    public void moveTo(double x, double y, double z) {
        this.entity.updatePositionAndAngles(x, y, z, this.entity.yaw, this.entity.pitch);
    }

    public void moveTo(Vec3 position, float yaw, float pitch) {
        if (position != null) {
            this.moveTo(position.x, position.y, position.z, yaw, pitch);
        }
    }

    public void moveTo(double x, double y, double z, float yaw, float pitch) {
        this.entity.updatePositionAndAngles(x, y, z, yaw, pitch);
    }

    public void moveTo(Vec3 position, float yaw, float pitch, int interpolationTicks) {
        if (position != null) {
            this.moveTo(position.x, position.y, position.z, yaw, pitch, interpolationTicks);
        }
    }

    public void moveTo(double x, double y, double z, float yaw, float pitch, int interpolationTicks) {
        if (interpolationTicks <= 0) {
            this.moveTo(x, y, z, yaw, pitch);
        } else {
            this.entity.lerpPositionAndAngles(x, y, z, yaw, pitch, interpolationTicks, false);
        }
    }

    public void setPosition(Vec3 position) {
        this.entity.setPosition(position.x, position.y, position.z);
    }

    public void setPosition(double x, double y, double z) {
        this.entity.setPosition(x, y, z);
    }
}
