package keystrokesmod.utility;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import keystrokesmod.mixin.impl.accessor.IAccessorEntity;
import keystrokesmod.mixin.impl.accessor.IAccessorEntityLivingBase;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.movement.NoSlow;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.CobwebBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.LiquidBlock;
import net.minecraft.block.SlimeBlock;
import net.minecraft.block.SoulSandBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.Input;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.ProtectionEnchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityFilter;
import net.minecraft.entity.living.attribute.AbstractEntityAttributeContainer;
import net.minecraft.entity.living.attribute.EntityAttribute;
import net.minecraft.entity.living.attribute.EntityAttributeContainer;
import net.minecraft.entity.living.attribute.EntityAttributeInstance;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.entity.living.player.HungerManager;
import net.minecraft.entity.living.player.PlayerAbilities;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.ChunkSource;
import net.minecraft.world.chunk.WorldChunk;
import org.apache.commons.lang3.tuple.Pair;

public class SimulatedPlayer {
    private static final Minecraft mc = Minecraft.getInstance();
    private final LocalClientPlayerEntity player;
    private final Map<BlockPos, BlockState> blockOverrides = new HashMap<>();
    private float itemInputMultiplier = 1.0F;
    public Box box;
    public final Input movementInput;
    private int jumpTicks;
    public double motionZ;
    public double motionY;
    public double motionX;
    private boolean inWater;
    public boolean onGround;
    private boolean isAirBorne;
    public float rotationYaw;
    public float rotationPitch;
    private double posX;
    private double posY;
    private double posZ;
    private final PlayerAbilities capabilities;
    private final Entity ridingEntity;
    private float jumpMovementFactor;
    private final World worldObj;
    public boolean isCollidedHorizontally;
    private boolean isCollidedVertically;
    private final WorldBorder worldBorder;
    private final ChunkSource chunkProvider;
    private boolean isOutsideBorder;
    private final Entity riddenByEntity;
    private AbstractEntityAttributeContainer attributeMap;
    private final boolean isSpectator;
    public float fallDistance;
    private final float stepHeight;
    private boolean isCollided;
    private int fire;
    private float distanceWalkedModified;
    private float distanceWalkedOnStepModified;
    private int nextStepDistance;
    public final float height;
    private final float width;
    private final int fireResistance;
    private boolean isInWeb;
    private boolean noClip;
    private boolean isSprinting;
    private boolean sprintRequested;
    private final HungerManager foodStats;
    private float moveForward = 0.0F;
    private float moveStrafing = 0.0F;
    private boolean isJumping = false;
    public boolean safeWalk = false;
    private static final float SPEED_IN_AIR = 0.02F;

    public SimulatedPlayer(
            LocalClientPlayerEntity player,
            Box box,
            Input movementInput,
            int jumpTicks,
            double motionZ,
            double motionY,
            double motionX,
            boolean inWater,
            boolean onGround,
            boolean isAirBorne,
            float rotationYaw,
            float rotationPitch,
            double posX,
            double posY,
            double posZ,
            PlayerAbilities capabilities,
            Entity ridingEntity,
            float jumpMovementFactor,
            World worldObj,
            boolean isCollidedHorizontally,
            boolean isCollidedVertically,
            WorldBorder worldBorder,
            ChunkSource chunkProvider,
            boolean isOutsideBorder,
            Entity riddenByEntity,
            AbstractEntityAttributeContainer attributeMap,
            boolean isSpectator,
            float fallDistance,
            float stepHeight,
            boolean isCollided,
            int fire,
            float distanceWalkedModified,
            float distanceWalkedOnStepModified,
            int nextStepDistance,
            float height,
            float width,
            int fireResistance,
            boolean isInWeb,
            boolean noClip,
            boolean isSprinting,
            HungerManager foodStats) {
        this.player = player;
        this.box = box;
        this.movementInput = movementInput;
        this.jumpTicks = jumpTicks;
        this.motionZ = motionZ;
        this.motionY = motionY;
        this.motionX = motionX;
        this.inWater = inWater;
        this.onGround = onGround;
        this.isAirBorne = isAirBorne;
        this.rotationYaw = rotationYaw;
        this.rotationPitch = rotationPitch;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.capabilities = capabilities;
        this.ridingEntity = ridingEntity;
        this.jumpMovementFactor = jumpMovementFactor;
        this.worldObj = worldObj;
        this.isCollidedHorizontally = isCollidedHorizontally;
        this.isCollidedVertically = isCollidedVertically;
        this.worldBorder = worldBorder;
        this.chunkProvider = chunkProvider;
        this.isOutsideBorder = isOutsideBorder;
        this.riddenByEntity = riddenByEntity;
        this.attributeMap = attributeMap;
        this.isSpectator = isSpectator;
        this.fallDistance = fallDistance;
        this.stepHeight = stepHeight;
        this.isCollided = isCollided;
        this.fire = fire;
        this.distanceWalkedModified = distanceWalkedModified;
        this.distanceWalkedOnStepModified = distanceWalkedOnStepModified;
        this.nextStepDistance = nextStepDistance;
        this.height = height;
        this.width = width;
        this.fireResistance = fireResistance;
        this.isInWeb = isInWeb;
        this.noClip = noClip;
        this.isSprinting = isSprinting;
        this.sprintRequested = isSprinting;
        this.foodStats = foodStats;
    }

    public Vec3d getPos() {
        return new Vec3d(this.posX, this.posY, this.posZ);
    }

    public static SimulatedPlayer fromClientPlayer(Input input) {
        LocalClientPlayerEntity player = mc.player;
        PlayerAbilities capabilities = createCapabilitiesCopy(player);
        HungerManager foodStats = createFoodStatsCopy(player);
        Input movementInput = new Input();
        movementInput.jumping = input.jumping;
        movementInput.movementForward = input.movementForward;
        movementInput.movementSideways = input.movementSideways;
        movementInput.sneaking = input.sneaking;
        return new SimulatedPlayer(
                player,
                player.getShape(),
                movementInput,
                ((IAccessorEntityLivingBase) player).getJumpTicks(),
                player.velocityZ,
                player.velocityY,
                player.velocityX,
                player.isInWater(),
                player.onGround,
                player.velocityDirty,
                player.yaw,
                player.pitch,
                player.x,
                player.y,
                player.z,
                capabilities,
                player.vehicle,
                player.speedInAir,
                player.world,
                player.collidingHorizontally,
                player.collidingVertically,
                player.world.getWorldBorder(),
                player.world.getChunkSource(),
                player.isOutsideWorldBorder(),
                player.rider,
                player.getAttributes(),
                player.isSpectator(),
                player.fallDistance,
                player.stepHeight,
                player.colliding,
                ((IAccessorEntity) player).getFire(),
                player.walkDistance,
                player.distanceMoved,
                ((IAccessorEntity) player).getNextStepDistance(),
                player.height,
                player.width,
                player.safeOnFireTime,
                ((IAccessorEntity) player).getIsInWeb(),
                player.noClip,
                player.isSprinting(),
                foodStats);
    }

    private static HungerManager createFoodStatsCopy(LocalClientPlayerEntity player) {
        NbtCompound foodStatsNBT = new NbtCompound();
        HungerManager foodStats = new HungerManager();
        player.getHungerManager().writeNbt(foodStatsNBT);
        foodStats.readNbt(foodStatsNBT);
        return foodStats;
    }

    private static PlayerAbilities createCapabilitiesCopy(LocalClientPlayerEntity player) {
        NbtCompound capabilitiesNBT = new NbtCompound();
        PlayerAbilities capabilities = new PlayerAbilities();
        player.abilities.writeNbt(capabilitiesNBT);
        capabilities.readNbt(capabilitiesNBT);
        capabilities.flying = false;
        capabilities.canFly = false;
        return capabilities;
    }

    private boolean onEntityUpdate() {
        this.handleWaterMovement();
        if (this.worldObj.isClient) {
            this.fire = 0;
        } else if (this.fire > 0) {
            this.fire--;
        }

        if (this.isInLava()) {
            this.setOnFireFromLava();
            this.fallDistance *= 0.5F;
        }

        return !(this.posY < -64.0);
    }

    public void tick() {
        if (this.onEntityUpdate() && !this.player.isRiding()) {
            this.playerUpdate(false);
            this.clientPlayerLivingUpdate();
            this.playerUpdate(true);
        }
    }

    private void clientPlayerLivingUpdate() {
        this.pushOutOfBlocks(
                this.posX - this.width * 0.35, this.getEntityBoundingBox().minY + 0.5, this.posZ + this.width * 0.35);
        this.pushOutOfBlocks(
                this.posX - this.width * 0.35, this.getEntityBoundingBox().minY + 0.5, this.posZ - this.width * 0.35);
        this.pushOutOfBlocks(
                this.posX + this.width * 0.35, this.getEntityBoundingBox().minY + 0.5, this.posZ - this.width * 0.35);
        this.pushOutOfBlocks(
                this.posX + this.width * 0.35, this.getEntityBoundingBox().minY + 0.5, this.posZ + this.width * 0.35);
        boolean useItemBlocksSprint =
                ModuleManager.noSlow == null || !ModuleManager.noSlow.isEnabled() || NoSlow.slowed.getInput() == 80.0;
        boolean usingItem = NoSlow.isUsingItemForMovement(this.player);
        this.itemInputMultiplier = usingItem && this.ridingEntity == null ? NoSlow.getSlowed() : 1.0F;
        int foodLevel = this.foodStats.getFoodLevel();
        boolean flag3 = foodLevel > 6 || this.capabilities.canFly;
        boolean blind = this.isPotionActive(StatusEffect.BLINDNESS);
        if (this.sprintRequested
                && !this.movementInput.sneaking
                && !this.isCollidedHorizontally
                && this.movementInput.movementForward * this.itemInputMultiplier >= 0.8F
                && flag3
                && (!usingItem || !useItemBlocksSprint)
                && !blind) {
            this.setSprinting(true);
        }

        if (this.movementInput.sneaking) {
            this.setSprinting(false);
        }

        if (this.isSprinting()
                && (this.movementInput.movementForward * this.itemInputMultiplier < 0.8F
                        || this.movementInput.sneaking
                        || this.isCollidedHorizontally
                        || foodLevel <= 6 && !this.capabilities.canFly)) {
            this.setSprinting(false);
        }

        if (this.capabilities.canFly && mc.interactionManager.isSpectator() && !this.capabilities.flying) {
            this.capabilities.flying = true;
        }

        if (this.capabilities.flying) {
            if (this.movementInput.sneaking) {
                this.motionY = this.motionY - this.capabilities.getFlySpeed() * 3.0F;
            }

            if (this.movementInput.jumping) {
                this.motionY = this.motionY + this.capabilities.getFlySpeed() * 3.0F;
            }
        }

        this.livingEntityUpdate();
    }

    private void playerUpdate(boolean post) {
        if (!post) {
            this.noClip = this.isSpectator;
            if (this.isSpectator) {
                this.onGround = false;
            }
        } else {
            this.clampPositionFromEntityPlayer();
        }
    }

    private void livingEntityUpdate() {
        if (this.jumpTicks > 0) {
            this.jumpTicks--;
        }

        if (Math.abs(this.motionX) < 0.005) {
            this.motionX = 0.0;
        }

        if (Math.abs(this.motionY) < 0.005) {
            this.motionY = 0.0;
        }

        if (Math.abs(this.motionZ) < 0.005) {
            this.motionZ = 0.0;
        }

        if (this.isMovementBlocked()) {
            this.isJumping = false;
            this.moveStrafing = 0.0F;
            this.moveForward = 0.0F;
        } else if (this.isServerWorld()) {
            this.updateLivingEntityInput();
        }

        if (this.isJumping) {
            if (this.isInWater() || this.isInLava()) {
                this.updateAITick();
            } else if (this.onGround && this.jumpTicks == 0) {
                this.jump();
            }
        } else {
            this.jumpTicks = 0;
        }

        this.moveStrafing *= 0.98F;
        this.moveForward *= 0.98F;
        this.playerSideMoveEntityWithHeading(this.moveStrafing, this.moveForward);
        this.jumpMovementFactor = 0.02F;
        if (this.isSprinting()) {
            this.jumpMovementFactor = (float) (this.jumpMovementFactor + 0.005999999865889549);
        }

        if (this.onGround && this.capabilities.flying && !this.isSpectator) {
            this.capabilities.flying = false;
        }
    }

    private void clampPositionFromEntityPlayer() {
        double d3 = MathHelper.clamp(this.posX, -2.9999999E7, 2.9999999E7);
        double d4 = MathHelper.clamp(this.posZ, -2.9999999E7, 2.9999999E7);
        if (d3 != this.posX || d4 != this.posZ) {
            this.setPosition(d3, this.posY, d4);
        }
    }

    private void setPosition(double x, double y, double z) {
        this.posX = x;
        this.posY = y;
        this.posZ = z;
        float f = this.width / 2.0F;
        float f1 = this.height;
        this.setEntityBoundingBox(new Box(x - f, y, z - f, x + f, y + f1, z + f));
    }

    private void setSprinting(boolean state) {
        this.isSprinting = state;
    }

    public void setSprintRequested(boolean sprintRequested) {
        this.sprintRequested = sprintRequested;
    }

    public float getAirAcceleration() {
        return this.jumpMovementFactor;
    }

    public void resetSimulationState(double x, double y, double z, boolean onGround) {
        this.blockOverrides.clear();
        this.setPosition(x, y, z);
        this.motionX = 0.0;
        this.motionY = 0.0;
        this.motionZ = 0.0;
        this.onGround = onGround;
        this.isAirBorne = !onGround;
        this.isCollided = false;
        this.isCollidedHorizontally = false;
        this.isCollidedVertically = onGround;
        this.fallDistance = 0.0F;
        this.jumpTicks = 0;
        this.inWater = false;
        this.isInWeb = false;
        this.isSprinting = false;
        this.sprintRequested = false;
        this.movementInput.movementForward = 0.0F;
        this.movementInput.movementSideways = 0.0F;
        this.movementInput.jumping = false;
        this.movementInput.sneaking = false;
    }

    private boolean pushOutOfBlocks(double x, double y, double z) {
        if (this.noClip) {
            return false;
        }

        BlockPos blockPos = new BlockPos(x, y, z);
        double d0 = x - blockPos.getX();
        double d1 = z - blockPos.getZ();
        int entHeight = (int) Math.ceil(this.height);
        boolean inTranslucentBlock = !this.isHeadspaceFree(blockPos, entHeight);
        if (inTranslucentBlock) {
            int i = -1;
            double d2 = 9999.0;
            if (this.isHeadspaceFree(blockPos.west(), entHeight) && d0 < d2) {
                d2 = d0;
                i = 0;
            }

            if (this.isHeadspaceFree(blockPos.east(), entHeight) && 1.0 - d0 < d2) {
                d2 = 1.0 - d0;
                i = 1;
            }

            if (this.isHeadspaceFree(blockPos.north(), entHeight) && d1 < d2) {
                d2 = d1;
                i = 4;
            }

            if (this.isHeadspaceFree(blockPos.south(), entHeight) && 1.0 - d1 < d2) {
                i = 5;
            }

            float f = 0.1F;
            if (i == 0) {
                this.motionX = -f;
            }

            if (i == 1) {
                this.motionX = f;
            }

            if (i == 4) {
                this.motionZ = -f;
            }

            if (i == 5) {
                this.motionZ = f;
            }
        }

        return false;
    }

    private boolean isHeadspaceFree(BlockPos pos, int height) {
        for (int y = 0; y < height; y++) {
            if (!this.isOpenBlockSpace(pos.add(0, y, 0))) {
                return false;
            }
        }

        return true;
    }

    private boolean isOpenBlockSpace(BlockPos pos) {
        return !this.getBlockState(pos).getBlock().isSolid();
    }

    private void playerSideMoveEntityWithHeading(float moveStrafing, float moveForward) {
        if (this.capabilities.flying && this.ridingEntity == null) {
            double d3 = this.motionY;
            float f = this.jumpMovementFactor;
            this.jumpMovementFactor = this.capabilities.getFlySpeed() * (this.isSprinting() ? 2 : 1);
            this.livingEntitySideMoveEntityWithHeading(moveStrafing, moveForward);
            this.motionY = d3 * 0.6;
            this.jumpMovementFactor = f;
        } else {
            this.livingEntitySideMoveEntityWithHeading(moveStrafing, moveForward);
        }
    }

    private void livingEntitySideMoveEntityWithHeading(float strafing, float forwards) {
        if (this.isServerWorld()) {
            if (this.isInWater() && !this.capabilities.flying) {
                double d0 = this.posY;
                float f5 = 0.8F;
                float f6 = 0.02F;
                float f3 = EnchantmentHelper.getDepthStriderLevel(this.player);
                if (f3 > 3.0F) {
                    f3 = 3.0F;
                }

                if (!this.onGround) {
                    f3 *= 0.5F;
                }

                if (f3 > 0.0F) {
                    f5 += (0.54600006F - f5) * f3 / 3.0F;
                    f6 += (this.getAIMoveSpeed() - f6) * f3 / 3.0F;
                }

                this.moveFlying(strafing, forwards, f6);
                this.moveEntity(this.motionX, this.motionY, this.motionZ);
                this.motionX *= f5;
                this.motionY *= 0.8F;
                this.motionZ *= f5;
                this.motionY -= 0.02;
                if (this.isCollidedHorizontally
                        && this.isOffsetPositionInLiquid(
                                this.motionX, this.motionY + 0.6F - this.posY + d0, this.motionZ)) {
                    this.motionY = 0.3F;
                }
            } else if (this.isInLava() && !this.capabilities.flying) {
                double d0 = this.posY;
                this.moveFlying(strafing, forwards, 0.02F);
                this.moveEntity(this.motionX, this.motionY, this.motionZ);
                this.motionX *= 0.5;
                this.motionY *= 0.5;
                this.motionZ *= 0.5;
                this.motionY -= 0.02;
                if (this.isCollidedHorizontally
                        && this.isOffsetPositionInLiquid(
                                this.motionX, this.motionY + 0.6F - this.posY + d0, this.motionZ)) {
                    this.motionY = 0.3F;
                }
            } else {
                float f4 = 0.91F;
                if (this.onGround) {
                    f4 = this.getBlockState(new BlockPos(
                                            MathHelper.floor(this.posX),
                                            MathHelper.floor(this.getEntityBoundingBox().minY) - 1,
                                            MathHelper.floor(this.posZ)))
                                    .getBlock()
                                    .slipperiness
                            * 0.91F;
                }

                float f = 0.16277136F / (f4 * f4 * f4);
                float f5 = this.onGround ? this.getAIMoveSpeed() * f : this.jumpMovementFactor;
                this.moveFlying(strafing, forwards, f5);
                f4 = 0.91F;
                if (this.onGround) {
                    f4 = this.getBlockState(new BlockPos(
                                            MathHelper.floor(this.posX),
                                            MathHelper.floor(this.getEntityBoundingBox().minY) - 1,
                                            MathHelper.floor(this.posZ)))
                                    .getBlock()
                                    .slipperiness
                            * 0.91F;
                }

                if (this.isOnLadder()) {
                    float f6 = 0.15F;
                    this.motionX = MathHelper.clamp(this.motionX, -f6, f6);
                    this.motionZ = MathHelper.clamp(this.motionZ, -f6, f6);
                    this.fallDistance = 0.0F;
                    if (this.motionY < -0.15) {
                        this.motionY = -0.15;
                    }

                    boolean flag = this.isSneaking();
                    if (flag && this.motionY < 0.0) {
                        this.motionY = 0.0;
                    }
                }

                this.moveEntity(this.motionX, this.motionY, this.motionZ);
                if (this.isCollidedHorizontally && this.isOnLadder()) {
                    this.motionY = 0.2;
                }

                if (this.worldObj.isClient
                        && (!this.worldObj.isChunkLoaded(new BlockPos(this.posX, 0.0, this.posZ))
                                || !this.worldObj
                                        .getChunk(new BlockPos(this.posX, 0.0, this.posZ))
                                        .isLoaded())) {
                    this.motionY = this.posY > 0.0 ? -0.1 : 0.0;
                } else {
                    this.motionY -= 0.08;
                }

                this.motionY *= 0.98F;
                this.motionX *= f4;
                this.motionZ *= f4;
            }
        }
    }

    public void moveEntity(double xMotion, double yMotion, double zMotion) {
        double velocityX = xMotion;
        double velocityY = yMotion;
        double velocityZ = zMotion;
        if (this.noClip) {
            this.setEntityBoundingBox(this.getEntityBoundingBox().moved(velocityX, velocityY, velocityZ));
            this.resetPositionToBB();
        } else {
            double d0 = this.posX;
            double d1 = this.posY;
            double d2 = this.posZ;
            if (this.isInWeb) {
                this.isInWeb = false;
                velocityX *= 0.25;
                velocityY *= 0.05F;
                velocityZ *= 0.25;
                this.motionX = 0.0;
                this.motionY = 0.0;
                this.motionZ = 0.0;
            }

            double d3 = velocityX;
            double d4 = velocityY;
            double d5 = velocityZ;
            boolean flag = this.onGround && (this.isSneaking() || this.safeWalk);
            if (flag) {
                d3 = (Double) this.checkForCollision(this, velocityX, velocityZ).getLeft();
                d5 = (Double) this.checkForCollision(this, velocityX, velocityZ).getRight();
            }

            List<Box> list1 = this.getCollidingBoundingBoxes(
                    this.getEntityBoundingBox().expanded(velocityX, velocityY, velocityZ));
            Box axisalignedbb = this.getEntityBoundingBox();

            for (Box axisalignedbb1 : list1) {
                velocityY = axisalignedbb1.intersectY(this.getEntityBoundingBox(), velocityY);
            }

            this.setEntityBoundingBox(this.getEntityBoundingBox().moved(0.0, velocityY, 0.0));
            boolean flag1 = this.onGround || d4 != velocityY && d4 < 0.0;

            for (Box axisalignedbb2 : list1) {
                velocityX = axisalignedbb2.intersectX(this.getEntityBoundingBox(), velocityX);
            }

            this.setEntityBoundingBox(this.getEntityBoundingBox().moved(velocityX, 0.0, 0.0));

            for (Box axisalignedbb13 : list1) {
                velocityZ = axisalignedbb13.intersectZ(this.getEntityBoundingBox(), velocityZ);
            }

            this.setEntityBoundingBox(this.getEntityBoundingBox().moved(0.0, 0.0, velocityZ));
            if (this.stepHeight > 0.0F && flag1 && (d3 != velocityX || d5 != velocityZ)) {
                double d11 = velocityX;
                double d7 = velocityY;
                double d8 = velocityZ;
                Box axisalignedbb3 = this.getEntityBoundingBox();
                this.setEntityBoundingBox(axisalignedbb);
                velocityY = this.stepHeight;
                List<Box> list = this.getCollidingBoundingBoxes(
                        this.getEntityBoundingBox().expanded(d3, velocityY, d5));
                Box axisalignedbb4 = this.getEntityBoundingBox();
                Box axisalignedbb5 = axisalignedbb4.expanded(d3, 0.0, d5);
                double d9 = velocityY;

                for (Box axisalignedbb6 : list) {
                    d9 = axisalignedbb6.intersectY(axisalignedbb5, d9);
                }

                axisalignedbb4 = axisalignedbb4.moved(0.0, d9, 0.0);
                double d15 = d3;

                for (Box axisalignedbb7 : list) {
                    d15 = axisalignedbb7.intersectX(axisalignedbb4, d15);
                }

                axisalignedbb4 = axisalignedbb4.moved(d15, 0.0, 0.0);
                double d16 = d5;

                for (Box axisalignedbb8 : list) {
                    d16 = axisalignedbb8.intersectZ(axisalignedbb4, d16);
                }

                axisalignedbb4 = axisalignedbb4.moved(0.0, 0.0, d16);
                Box axisalignedbb14 = this.getEntityBoundingBox();
                double d17 = velocityY;

                for (Box axisalignedbb9 : list) {
                    d17 = axisalignedbb9.intersectY(axisalignedbb14, d17);
                }

                axisalignedbb14 = axisalignedbb14.moved(0.0, d17, 0.0);
                double d18 = d3;

                for (Box axisalignedbb10 : list) {
                    d18 = axisalignedbb10.intersectX(axisalignedbb14, d18);
                }

                axisalignedbb14 = axisalignedbb14.moved(d18, 0.0, 0.0);
                double d19 = d5;

                for (Box axisalignedbb11 : list) {
                    d19 = axisalignedbb11.intersectZ(axisalignedbb14, d19);
                }

                axisalignedbb14 = axisalignedbb14.moved(0.0, 0.0, d19);
                double d20 = d15 * d15 + d16 * d16;
                double d10 = d18 * d18 + d19 * d19;
                if (d20 > d10) {
                    velocityX = d15;
                    velocityZ = d16;
                    velocityY = -d9;
                    this.setEntityBoundingBox(axisalignedbb4);
                } else {
                    velocityX = d18;
                    velocityZ = d19;
                    velocityY = -d17;
                    this.setEntityBoundingBox(axisalignedbb14);
                }

                for (Box axisalignedbb12 : list) {
                    velocityY = axisalignedbb12.intersectY(this.getEntityBoundingBox(), velocityY);
                }

                this.setEntityBoundingBox(this.getEntityBoundingBox().moved(0.0, velocityY, 0.0));
                if (d11 * d11 + d8 * d8 >= velocityX * velocityX + velocityZ * velocityZ) {
                    velocityX = d11;
                    velocityY = d7;
                    velocityZ = d8;
                    this.setEntityBoundingBox(axisalignedbb3);
                }
            }

            this.resetPositionToBB();
            this.isCollidedHorizontally = d3 != velocityX || d5 != velocityZ;
            this.isCollidedVertically = d4 != velocityY;
            this.onGround = this.isCollidedVertically && d4 < 0.0;
            this.isCollided = this.isCollidedHorizontally || this.isCollidedVertically;
            int i = MathHelper.floor(this.posX);
            int j = MathHelper.floor(this.posY - 0.2F);
            int k = MathHelper.floor(this.posZ);
            BlockPos blockPos = new BlockPos(i, j, k);
            Block block1 = this.getBlockState(blockPos).getBlock();
            if (block1.getMaterial() == Material.AIR) {
                Block block = this.getBlockState(blockPos.down()).getBlock();
                if (block instanceof FenceBlock || block instanceof WallBlock || block instanceof FenceGateBlock) {
                    block1 = block;
                }
            }

            this.updateFallState(velocityY, this.onGround);
            if (d3 != velocityX) {
                this.motionX = 0.0;
            }

            if (d5 != velocityZ) {
                this.motionZ = 0.0;
            }

            if (d4 != velocityY) {
                this.onLanded(block1);
            }

            if (this.canTriggerWalking() && !flag && this.ridingEntity == null) {
                double d12 = this.posX - d0;
                double d13 = this.posY - d1;
                double d14 = this.posZ - d2;
                if (block1 != Blocks.LADDER) {
                    d13 = 0.0;
                }

                if (block1 != null && this.onGround) {
                    this.onEntityCollidedWithBlock(block1);
                }

                this.distanceWalkedModified =
                        (float) (this.distanceWalkedModified + MathHelper.sqrt(d12 * d12 + d14 * d14) * 0.6);
                this.distanceWalkedOnStepModified = (float)
                        (this.distanceWalkedOnStepModified + MathHelper.sqrt(d12 * d12 + d13 * d13 + d14 * d14) * 0.6);
                if (this.distanceWalkedOnStepModified > this.nextStepDistance && block1.getMaterial() != Material.AIR) {
                    this.nextStepDistance = (int) this.distanceWalkedOnStepModified + 1;
                }
            }

            try {
                this.doBlockCollisions();
            } catch (Throwable var52) {
                var52.printStackTrace();
            }

            boolean flag2 = this.isWet();
            if (this.worldObj.containsFireSource(this.getEntityBoundingBox().contract(0.001, 0.001, 0.001))) {
                if (!flag2) {
                    this.fire++;
                    if (this.fire == 0) {
                        this.setFire(8);
                    }
                }
            } else if (this.fire <= 0) {
                this.fire = -this.fireResistance;
            }

            if (flag2 && this.fire > 0) {
                this.fire = -this.fireResistance;
            }
        }
    }

    public Box getEntityBoundingBox() {
        return this.box;
    }

    public void setEntityBoundingBox(Box box) {
        this.box = box;
    }

    public void setOnFireFromLava() {
        this.setFire(15);
    }

    public void setFire(int seconds) {
        int i = seconds * 20;
        i = ProtectionEnchantment.modifyOnFireTimer(this.player, i);
        if (this.fire < i) {
            this.fire = i;
        }
    }

    public boolean isWet() {
        return this.inWater
                || this.isRainingAt(new BlockPos(this.posX, this.posY, this.posZ))
                || this.isRainingAt(new BlockPos(this.posX, this.posY + this.height, this.posZ));
    }

    public void doBlockCollisions() {
        BlockPos blockpos = new BlockPos(
                this.getEntityBoundingBox().minX + 0.001,
                this.getEntityBoundingBox().minY + 0.001,
                this.getEntityBoundingBox().minZ + 0.001);
        BlockPos blockpos1 = new BlockPos(
                this.getEntityBoundingBox().maxX - 0.001,
                this.getEntityBoundingBox().maxY - 0.001,
                this.getEntityBoundingBox().maxZ - 0.001);
        if (this.isAreaLoaded(
                blockpos.getX(),
                blockpos.getY(),
                blockpos.getZ(),
                blockpos1.getX(),
                blockpos1.getY(),
                blockpos1.getZ(),
                true)) {
            for (int i = blockpos.getX(); i <= blockpos1.getX(); i++) {
                for (int j = blockpos.getY(); j <= blockpos1.getY(); j++) {
                    for (int k = blockpos.getZ(); k <= blockpos1.getZ(); k++) {
                        BlockPos pos = new BlockPos(i, j, k);
                        BlockState state = this.getBlockState(pos);

                        try {
                            Block block = state.getBlock();
                            if (block instanceof CobwebBlock) {
                                this.isInWeb = true;
                            } else if (block instanceof SoulSandBlock) {
                                this.motionX *= 0.4;
                                this.motionZ *= 0.4;
                            }
                        } catch (Throwable var11) {
                            var11.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    public void updateFallState(double motionY, boolean onGround) {
        if (!this.isInWater()) {
            this.handleWaterMovement();
        }

        if (onGround) {
            if (this.fallDistance > 0.0F) {
                this.fallDistance = 0.0F;
            }
        } else if (motionY < 0.0) {
            this.fallDistance = (float) (this.fallDistance - motionY);
        }
    }

    public boolean handleWaterMovement() {
        if (this.handleMaterialAcceleration(
                this.getEntityBoundingBox().grown(0.0, -0.4F, 0.0).contract(0.001, 0.001, 0.001), Material.WATER)) {
            this.fallDistance = 0.0F;
            this.inWater = true;
            this.fire = 0;
        } else {
            this.inWater = false;
        }

        return this.inWater;
    }

    public boolean handleMaterialAcceleration(Box boundingBox, Material material) {
        int i = MathHelper.floor(boundingBox.minX);
        int j = MathHelper.floor(boundingBox.maxX + 1.0);
        int k = MathHelper.floor(boundingBox.minY);
        int l = MathHelper.floor(boundingBox.maxY + 1.0);
        int i1 = MathHelper.floor(boundingBox.minZ);
        int j1 = MathHelper.floor(boundingBox.maxZ + 1.0);
        if (!this.isAreaLoaded(i, k, i1, j, l, j1, true)) {
            return false;
        }

        boolean flag = false;
        Vec3d vec3 = new Vec3d(0.0, 0.0, 0.0);
        Mutable blockPos = new Mutable();

        for (int k1 = i; k1 < j; k1++) {
            for (int l1 = k; l1 < l; l1++) {
                for (int i2 = i1; i2 < j1; i2++) {
                    blockPos.set(k1, l1, i2);
                    BlockState state = this.getBlockState(blockPos);
                    if (state != null) {
                        Block block = state.getBlock();
                        if (block != null && block.getMaterial() == material) {
                            double d0 = l1 + 1 - LiquidBlock.getHeightLoss((Integer) state.get(LiquidBlock.LEVEL));
                            if (l >= d0) {
                                flag = true;
                                vec3 = block.applyMaterialDrag(this.worldObj, blockPos, this.player, vec3);
                            }
                        }
                    }
                }
            }
        }

        if (vec3.length() > 0.0 && this.isPushedByWater()) {
            vec3 = vec3.normalize();
            double d1 = 0.014;
            this.motionX = this.motionX + vec3.x * d1;
            this.motionY = this.motionY + vec3.y * d1;
            this.motionZ = this.motionZ + vec3.z * d1;
        }

        return flag;
    }

    public boolean isAreaLoaded(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean idfk) {
        if (maxY >= 0 && minY < 256) {
            minX >>= 4;
            minZ >>= 4;
            maxX >>= 4;
            maxZ >>= 4;

            for (int i = minX; i <= maxX; i++) {
                for (int j = minZ; j <= maxZ; j++) {
                    if (!this.isChunkLoaded(i, j, idfk)) {
                        return false;
                    }
                }
            }

            return true;
        } else {
            return false;
        }
    }

    public void onEntityCollidedWithBlock(Block block) {
        if (block instanceof SlimeBlock && Math.abs(this.motionY) < 0.1 && !this.isSneaking()) {
            double motion = 0.4 + Math.abs(this.motionY) * 0.2;
            this.motionX *= motion;
            this.motionZ *= motion;
        }
    }

    public boolean canTriggerWalking() {
        return !this.capabilities.flying;
    }

    public boolean isOnLadder() {
        int i = MathHelper.floor(this.posX);
        int j = MathHelper.floor(this.getEntityBoundingBox().minY);
        int k = MathHelper.floor(this.posZ);
        Block block = this.getBlockState(new BlockPos(i, j, k)).getBlock();
        return (block == Blocks.LADDER || block == Blocks.VINE) && !this.isSpectator;
    }

    public void moveFlying(float strafe, float forward, float friction) {
        float newStrafe = strafe;
        float newForward = forward;
        float f = newStrafe * newStrafe + newForward * newForward;
        if (f >= 1.0E-4F) {
            f = MathHelper.sqrt(f);
            if (f < 1.0F) {
                f = 1.0F;
            }

            f = friction / f;
            newStrafe *= f;
            newForward *= f;
            float f1 = MathHelper.sin(this.rotationYaw * (float) Math.PI / 180.0F);
            float f2 = MathHelper.cos(this.rotationYaw * (float) Math.PI / 180.0F);
            this.motionX += newStrafe * f2 - newForward * f1;
            this.motionZ += newForward * f2 + newStrafe * f1;
        }
    }

    public void jump() {
        this.motionY = this.getJumpUpwardsMotion();
        if (this.isPotionActive(StatusEffect.JUMP_BOOST)) {
            this.motionY = this.motionY
                    + (this.getActivePotionEffect(StatusEffect.JUMP_BOOST).getAmplifier() + 1) * 0.1F;
        }

        if (this.isSprinting()) {
            float f = this.rotationYaw * (float) (Math.PI / 180.0);
            this.motionX = this.motionX - MathHelper.sin(f) * 0.2F;
            this.motionZ = this.motionZ + MathHelper.cos(f) * 0.2F;
        }

        this.isAirBorne = true;
    }

    public boolean isSprinting() {
        return this.isSprinting;
    }

    public boolean isPotionActive(StatusEffect potion) {
        return this.player.getEffectInstance(potion) != null;
    }

    public StatusEffectInstance getActivePotionEffect(StatusEffect potion) {
        return this.player.getEffectInstance(potion);
    }

    public float getJumpUpwardsMotion() {
        return 0.42F;
    }

    public boolean isInWater() {
        return this.inWater;
    }

    public void updateLivingEntityInput() {
        this.moveForward = this.movementInput.movementForward * this.itemInputMultiplier;
        this.moveStrafing = this.movementInput.movementSideways * this.itemInputMultiplier;
        this.isJumping = this.movementInput.jumping;
    }

    public boolean isServerWorld() {
        return true;
    }

    public boolean isMovementBlocked() {
        return this.player.getHealth() <= 0.0F || this.player.isSleeping();
    }

    public boolean isInLava() {
        return this.worldObj.containsMaterial(this.getEntityBoundingBox().grown(-0.1F, -0.4F, -0.1F), Material.LAVA);
    }

    public void updateAITick() {
        this.motionY += 0.04F;
    }

    public boolean isOffsetPositionInLiquid(double x, double y, double z) {
        Box box = this.getEntityBoundingBox().moved(x, y, z);
        return this.isLiquidPresentInAABB(box);
    }

    public boolean isLiquidPresentInAABB(Box box) {
        return this.getCollidingBoundingBoxes(box).isEmpty() && !this.worldObj.containsLiquid(box);
    }

    public List<Box> getCollidingBoundingBoxes(Box box) {
        List<Box> list = Lists.newArrayList();
        int i = MathHelper.floor(box.minX);
        int j = MathHelper.floor(box.maxX + 1.0);
        int k = MathHelper.floor(box.minY);
        int l = MathHelper.floor(box.maxY + 1.0);
        int i1 = MathHelper.floor(box.minZ);
        int j1 = MathHelper.floor(box.maxZ + 1.0);
        WorldBorder worldBorder = this.getWorldBorder();
        boolean flag = this.isOutsideBorder;
        boolean flag1 = this.isInsideBorder(worldBorder, flag);
        BlockState iblockstate = Blocks.STONE.defaultState();
        Mutable blockPos = new Mutable();

        for (int k1 = i; k1 < j; k1++) {
            for (int l1 = i1; l1 < j1; l1++) {
                if (this.isBlockLoaded(blockPos.set(k1, 64, l1))) {
                    for (int i2 = k - 1; i2 < l; i2++) {
                        blockPos.set(k1, i2, l1);
                        if (flag && flag1) {
                            this.isOutsideBorder = false;
                        } else if (!flag && !flag1) {
                            this.isOutsideBorder = true;
                        }

                        BlockState state = iblockstate;
                        if (worldBorder.contains(blockPos) || !flag1) {
                            state = this.getBlockState(blockPos);
                        }

                        if (state.getBlock() == Blocks.LADDER && this.blockOverrides.containsKey(blockPos)) {
                            Box ladderBox = this.getLadderCollisionBox(blockPos, state);
                            if (ladderBox.intersects(box)) {
                                list.add(ladderBox);
                            }
                        } else {
                            state.getBlock().addCollisions(this.worldObj, blockPos, state, box, list, this.player);
                        }
                    }
                }
            }
        }

        double d0 = 0.25;

        for (Entity entity : this.getEntitiesWithinAABBExcludingEntity(this.player, box.grown(d0, d0, d0))) {
            if (this.riddenByEntity != entity && this.ridingEntity != entity) {
                Box boundingBox = entity.getCollisionShape();
                if (boundingBox != null && boundingBox.intersects(box)) {
                    list.add(boundingBox);
                }

                boundingBox = this.getCollisionBox(this.player, entity);
                if (boundingBox != null && boundingBox.intersects(box)) {
                    list.add(boundingBox);
                }
            }
        }

        return list;
    }

    private Box getLadderCollisionBox(BlockPos pos, BlockState state) {
        Direction facing = (Direction) state.get(LadderBlock.FACING);
        double minX = facing == Direction.WEST ? 0.875 : 0.0;
        double maxX = facing == Direction.EAST ? 0.125 : 1.0;
        double minZ = facing == Direction.NORTH ? 0.875 : 0.0;
        double maxZ = facing == Direction.SOUTH ? 0.125 : 1.0;
        return new Box(
                pos.getX() + minX,
                pos.getY(),
                pos.getZ() + minZ,
                pos.getX() + maxX,
                pos.getY() + 1.0,
                pos.getZ() + maxZ);
    }

    public void setBlockState(BlockPos blockPos, BlockState state) {
        this.blockOverrides.put(blockPos, state);
    }

    public BlockState getBlockState(BlockPos blockPos) {
        BlockState state = this.blockOverrides.get(blockPos);
        return state == null ? this.worldObj.getBlockState(blockPos) : state;
    }

    private WorldChunk getChunkFromBlockCoords(BlockPos blockPos) {
        return this.getChunkFromChunkCoords(blockPos.getX() >> 4, blockPos.getZ() >> 4);
    }

    private WorldChunk getChunkFromChunkCoords(int x, int z) {
        return this.chunkProvider.getChunk(x, z);
    }

    private boolean isValid(BlockPos pos) {
        return pos.getX() >= -30000000
                && pos.getZ() >= -30000000
                && pos.getX() < 30000000
                && pos.getZ() < 30000000
                && pos.getY() >= 0
                && pos.getY() < 256;
    }

    private WorldBorder getWorldBorder() {
        return this.worldBorder;
    }

    private boolean isInsideBorder(WorldBorder border, boolean insideBorder) {
        double d0 = border.getMinX();
        double d1 = border.getMinZ();
        double d2 = border.getMaxX();
        double d3 = border.getMaxZ();
        if (insideBorder) {
            d0++;
            d1++;
            d2--;
            d3--;
        } else {
            d0--;
            d1--;
            d2++;
            d3++;
        }

        return this.posX > d0 && this.posX < d2 && this.posZ > d1 && this.posZ < d3;
    }

    private boolean isBlockLoaded(BlockPos pos) {
        return this.isBlockLoaded(pos, true);
    }

    private boolean isBlockLoaded(BlockPos pos, boolean check2) {
        return this.isValid(pos) && this.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4, check2);
    }

    private boolean isChunkLoaded(int x, int z, boolean flag) {
        return this.chunkProvider.hasChunk(x, z)
                && (flag || !this.chunkProvider.getChunk(x, z).isEmpty());
    }

    private List<Entity> getEntitiesWithinAABBExcludingEntity(Entity entity, Box box) {
        return this.getEntitiesInAABBexcluding(entity, box, EntityFilter.NOT_SPECTATOR);
    }

    private List<Entity> getEntitiesInAABBexcluding(Entity entity, Box bb, Predicate<Entity> predicate) {
        List<Entity> list = Lists.newArrayList();
        int i = MathHelper.floor((bb.minX - 2.0) / 16.0);
        int j = MathHelper.floor((bb.maxX + 2.0) / 16.0);
        int k = MathHelper.floor((bb.minZ - 2.0) / 16.0);
        int l = MathHelper.floor((bb.maxZ + 2.0) / 16.0);

        for (int i1 = i; i1 <= j; i1++) {
            for (int j1 = k; j1 <= l; j1++) {
                if (this.isChunkLoaded(i1, j1, true)) {
                    this.getChunkFromChunkCoords(i1, j1).getEntities(entity, bb, list, predicate);
                }
            }
        }

        return list;
    }

    private Box getCollisionBox(Entity player, Entity entity) {
        if (entity instanceof BoatEntity) {
            return entity.getShape();
        } else {
            return entity instanceof MinecartEntity ? player.getCollisionAgainstShape(entity) : null;
        }
    }

    private float getAIMoveSpeed() {
        return (float) this.getEntityAttribute(EntityAttributes.MOVEMENT_SPEED).get();
    }

    private EntityAttributeInstance getEntityAttribute(EntityAttribute iAttribute) {
        return this.getAttributeMap().get(iAttribute);
    }

    private AbstractEntityAttributeContainer getAttributeMap() {
        if (this.attributeMap == null) {
            this.attributeMap = new EntityAttributeContainer();
        }

        return this.attributeMap;
    }

    private void resetPositionToBB() {
        this.posX = (this.getEntityBoundingBox().minX + this.getEntityBoundingBox().maxX) / 2.0;
        this.posY = this.getEntityBoundingBox().minY;
        this.posZ = (this.getEntityBoundingBox().minZ + this.getEntityBoundingBox().maxZ) / 2.0;
    }

    private void onLanded(Block block) {
        if (block instanceof SlimeBlock) {
            if (this.isSneaking()) {
                this.motionY = 0.0;
            } else if (this.motionY < 0.0) {
                this.motionY = -this.motionY;
            }
        } else {
            this.motionY = 0.0;
        }
    }

    public boolean isSneaking() {
        return this.movementInput.sneaking && !this.player.isSleeping();
    }

    private boolean isRainingAt(BlockPos pos) {
        if (this.worldObj.getRain(1.0F) <= 0.2) {
            return false;
        } else if (!this.canSeeSky(pos)) {
            return false;
        } else if (this.worldObj.getPrecipitationHeight(pos).getY() > pos.getY()) {
            return false;
        } else {
            Biome base = this.worldObj.getBiome(pos);
            if (base.isSnowy()) {
                return false;
            } else {
                return this.worldObj.canSnowFall(pos, false) ? false : base.isRainy();
            }
        }
    }

    private boolean canSeeSky(BlockPos pos) {
        return this.getChunkFromBlockCoords(pos).hasSkyAccess(pos);
    }

    private boolean isPushedByWater() {
        return !this.capabilities.flying;
    }

    public Pair<Double, Double> checkForCollision(SimulatedPlayer simPlayer, double velocityX, double velocityZ) {
        double d3 = velocityX;
        double d5 = velocityZ;
        double d6 = 0.05;

        while (velocityX != 0.0
                && simPlayer
                        .getCollidingBoundingBoxes(simPlayer.box.moved(velocityX, -1.0, 0.0))
                        .isEmpty()) {
            if (velocityX < d6 && velocityX >= -d6) {
                velocityX = 0.0;
            } else if (velocityX > 0.0) {
                velocityX -= d6;
            } else {
                velocityX += d6;
            }

            d3 = velocityX;
        }

        for (;
                velocityZ != 0.0
                        && simPlayer
                                .getCollidingBoundingBoxes(simPlayer.box.moved(0.0, -1.0, velocityZ))
                                .isEmpty();
                d5 = velocityZ) {
            if (velocityZ < d6 && velocityZ >= -d6) {
                velocityZ = 0.0;
            } else if (velocityZ > 0.0) {
                velocityZ -= d6;
            } else {
                velocityZ += d6;
            }
        }

        for (;
                velocityX != 0.0
                        && velocityZ != 0.0
                        && simPlayer
                                .getCollidingBoundingBoxes(simPlayer.box.moved(velocityX, -1.0, velocityZ))
                                .isEmpty();
                d5 = velocityZ) {
            if (velocityX < d6 && velocityX >= -d6) {
                velocityX = 0.0;
            } else if (velocityX > 0.0) {
                velocityX -= d6;
            } else {
                velocityX += d6;
            }

            d3 = velocityX;
            if (velocityZ < d6 && velocityZ >= -d6) {
                velocityZ = 0.0;
            } else if (velocityZ > 0.0) {
                velocityZ -= d6;
            } else {
                velocityZ += d6;
            }
        }

        return Pair.of(d3, d5);
    }

    public float getEyeHeight() {
        return this.height * 0.85F;
    }
}
