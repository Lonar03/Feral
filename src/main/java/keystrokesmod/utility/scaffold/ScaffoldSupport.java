package keystrokesmod.utility.scaffold;

import keystrokesmod.helper.RotationHelper;
import keystrokesmod.mixin.impl.accessor.IAccessorPlayerControllerMP;
import keystrokesmod.platform.ClientPlatform;
import keystrokesmod.utility.IMinecraftInstance;
import net.minecraft.block.Block;
import net.minecraft.block.BlockWithBlockEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.LiquidBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.SlabBlock.Half;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.options.GameOptions;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ArmSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerUseC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import net.minecraft.world.HitResult.Type;

public final class ScaffoldSupport implements IMinecraftInstance {
    private static volatile long lastSetback;
    private static LocalClientPlayerEntity tickPlayer;

    private ScaffoldSupport() {}

    public static void recordSetback() {
        lastSetback = System.currentTimeMillis();
    }

    public static boolean sinceSetback(long duration) {
        return lastSetback != 0L && System.currentTimeMillis() - lastSetback < duration;
    }

    public static void resetTicks() {
        tickPlayer = null;
        ScaffoldSupport.PlayerUtils.groundTicks = 0L;
        ScaffoldSupport.PlayerUtils.airTicks = 0L;
    }

    public static void updateTicks() {
        if (mc.player == null) {
            resetTicks();
        } else {
            if (tickPlayer != mc.player) {
                resetTicks();
                tickPlayer = mc.player;
            }

            if (mc.player.onGround) {
                ScaffoldSupport.PlayerUtils.groundTicks++;
                ScaffoldSupport.PlayerUtils.airTicks = 0L;
            } else {
                ScaffoldSupport.PlayerUtils.groundTicks = 0L;
                ScaffoldSupport.PlayerUtils.airTicks++;
            }
        }
    }

    public static double randomBetween(double min, double max) {
        return min + Math.random() * (max - min);
    }

    public static final class BlockUtils {
        public static BlockPos below(double d) {
            return new BlockPos(
                    IMinecraftInstance.mc.player.x, IMinecraftInstance.mc.player.y - d, IMinecraftInstance.mc.player.z);
        }

        public static boolean isReplaceable(BlockPos pos) {
            Block block = getBlock(pos);
            return block == Blocks.SNOW_LAYER
                    ? block.canBeReplaced(IMinecraftInstance.mc.world, pos)
                    : block == Blocks.AIR
                            || block == Blocks.FIRE
                            || block instanceof LiquidBlock
                            || block.getMaterial().isReplaceable();
        }

        public static boolean isReplaceableSnow(BlockPos pos) {
            return getBlock(pos) == Blocks.SNOW_LAYER
                    && Blocks.SNOW_LAYER.canBeReplaced(IMinecraftInstance.mc.world, pos);
        }

        public static boolean isPlaceable(Block block) {
            return block == Blocks.GLASS
                    || block.isOpaque()
                            && block != Blocks.GRAVEL
                            && block != Blocks.SAND
                            && block != Blocks.SOUL_SAND
                            && block != Blocks.TNT
                            && block != Blocks.CRAFTING_TABLE
                            && block != Blocks.FURNACE
                            && block != Blocks.DISPENSER
                            && block != Blocks.DROPPER
                            && block != Blocks.NOTEBLOCK
                            && block != Blocks.COMMAND_BLOCK;
        }

        public static boolean isBlockItem(ItemStack itemStack) {
            return itemStack != null && itemStack.getItem() instanceof BlockItem;
        }

        public static boolean isContainer(Block block) {
            return block instanceof BlockWithBlockEntity || block == Blocks.CRAFTING_TABLE || block == Blocks.ANVIL;
        }

        public static boolean isInteractable(Block block) {
            if (isContainer(block) || block == Blocks.BED || block == Blocks.LEVER) {
                return true;
            } else if (block instanceof TrapdoorBlock
                    || block instanceof FenceBlock
                    || block instanceof FenceGateBlock) {
                return true;
            } else {
                return !(block instanceof DoorBlock) ? false : block != Blocks.IRON_DOOR;
            }
        }

        public static BlockPos placedPos(HitResult hit) {
            return hit.getPos().offset(hit.face);
        }

        public static Vec3d hitVec(BlockPos pos, Direction facing) {
            double lo = 0.1;
            double hi = 0.9;
            double ylo = 0.05;
            double yhi = 0.95;
            double top = 1.0;
            double x = pos.getX() + ScaffoldSupport.randomBetween(lo, hi);
            double y = pos.getY() + ScaffoldSupport.randomBetween(ylo, yhi);
            double z = pos.getZ() + ScaffoldSupport.randomBetween(lo, hi);
            BlockState state = getState(pos);
            Block block = state.getBlock();
            if (!block.isOpaque() && block instanceof SlabBlock) {
                if (state.get(SlabBlock.HALF) == Half.TOP) {
                    ylo = 0.505;
                } else if (facing == Direction.UP) {
                    top = 0.5;
                } else {
                    yhi = 0.495;
                }

                y = pos.getY() + ScaffoldSupport.randomBetween(ylo, yhi);
            }

            switch (facing) {
                case EAST:
                    x = pos.getX() + 1;
                    break;
                case WEST:
                    x = pos.getX();
                    break;
                case NORTH:
                    z = pos.getZ();
                    break;
                case SOUTH:
                    z = pos.getZ() + 1;
                    break;
                case UP:
                    y = pos.getY() + top;
                    break;
                case DOWN:
                    y = pos.getY();
            }

            return new Vec3d(x, y, z);
        }

        public static Block getBlock(BlockPos pos) {
            return IMinecraftInstance.mc.world.getBlockState(pos).getBlock();
        }

        public static BlockState getState(BlockPos pos) {
            return IMinecraftInstance.mc.world.getBlockState(pos);
        }

        public static boolean samePos(BlockPos a, BlockPos b) {
            return a.equals(b);
        }
    }

    public enum FakeRotation {
        None,
        Smooth,
        Forward,
        Spin;
    }

    public static final class ItemUtils {
        public static boolean isScaffoldBlock(ItemStack itemStack) {
            if (ScaffoldSupport.BlockUtils.isBlockItem(itemStack) && itemStack.size > 0) {
                Block block = ((BlockItem) itemStack.getItem()).getBlock();
                return ScaffoldSupport.BlockUtils.isPlaceable(block) && block != Blocks.PUMPKIN;
            } else {
                return false;
            }
        }
    }

    public enum MoveDirection {
        NONE(false, false, false),
        FORWARD(true, false, false),
        FORWARD_LEFT(true, true, false),
        FORWARD_RIGHT(true, true, false),
        BACK(false, false, true),
        BACK_LEFT(false, true, true),
        BACK_RIGHT(false, true, true),
        LEFT(false, false, false),
        RIGHT(false, false, false);

        public final boolean forward;
        public final boolean diagonal;
        public final boolean backward;

        MoveDirection(boolean forward, boolean diagonal, boolean backward) {
            this.forward = forward;
            this.diagonal = diagonal;
            this.backward = backward;
        }
    }

    public static final class MoveUtils {
        public static float moveYaw(float yaw, float forward, float strafe) {
            float f4 = 1.0F;
            if (forward < 0.0F) {
                yaw += 180.0F;
                f4 = -0.5F;
            } else if (forward > 0.0F) {
                f4 = 0.5F;
            }

            if (strafe > 0.0F) {
                yaw -= 90.0F * f4;
            } else if (strafe < 0.0F) {
                yaw += 90.0F * f4;
            }

            return yaw;
        }

        public static float moveYaw(float yaw) {
            return moveYaw(
                    yaw,
                    IMinecraftInstance.mc.player.input.movementForward,
                    IMinecraftInstance.mc.player.input.movementSideways);
        }

        public static void setSpeed(double speed, float yaw) {
            if (speed == 0.0) {
                IMinecraftInstance.mc.player.velocityZ = 0.0;
                IMinecraftInstance.mc.player.velocityX = 0.0;
            } else {
                float var3;
                IMinecraftInstance.mc.player.velocityX = -Math.sin(var3 = yaw * (float) (Math.PI / 180.0)) * speed;
                IMinecraftInstance.mc.player.velocityZ = Math.cos(var3) * speed;
            }
        }

        public static void setSpeed(double speed) {
            float yaw = IMinecraftInstance.mc.player.yaw;
            if (IMinecraftInstance.mc.player.input.movementForward < 0.0F) {
                yaw += 180.0F;
            }

            setSpeed(speed, yaw);
        }

        public static void strafe(double speed) {
            setSpeed(
                    speed,
                    moveYaw(
                            RotationHelper.get().fixMovement()
                                            && RotationHelper.get().getServerYaw() != null
                                    ? RotationHelper.get().getServerYaw()
                                    : IMinecraftInstance.mc.player.yaw));
        }

        public static double speed() {
            return Math.sqrt(IMinecraftInstance.mc.player.velocityX * IMinecraftInstance.mc.player.velocityX
                    + IMinecraftInstance.mc.player.velocityZ * IMinecraftInstance.mc.player.velocityZ);
        }
    }

    public static final class PlayerUtils {
        public static long groundTicks;
        public static long airTicks;

        public static boolean isAirBelow(double x, double y, double z) {
            BlockPos pos = new BlockPos(x, y - (y % 1.0 == 0.0 ? 1 : 0), z);
            return IMinecraftInstance.mc.world.isAir(pos);
        }

        public static boolean isAirBelow() {
            return isAirBelow(
                    IMinecraftInstance.mc.player.x, IMinecraftInstance.mc.player.y, IMinecraftInstance.mc.player.z);
        }

        public static boolean nearEdge(double margin) {
            int x = MathHelper.floor(IMinecraftInstance.mc.player.x);
            int y = MathHelper.floor(IMinecraftInstance.mc.player.y - 0.01);
            int z = MathHelper.floor(IMinecraftInstance.mc.player.z);
            BlockPos pos = new BlockPos(x, y, z);
            if (IMinecraftInstance.mc.world.isAir(pos)) {
                return true;
            }

            double fx = IMinecraftInstance.mc.player.x - x;
            double fz = IMinecraftInstance.mc.player.z - z;
            return fx < margin && IMinecraftInstance.mc.world.isAir(new BlockPos(x - 1, y, z))
                    || fx > 1.0 - margin && IMinecraftInstance.mc.world.isAir(new BlockPos(x + 1, y, z))
                    || fz < margin && IMinecraftInstance.mc.world.isAir(new BlockPos(x, y, z - 1))
                    || fz > 1.0 - margin && IMinecraftInstance.mc.world.isAir(new BlockPos(x, y, z + 1));
        }

        public static boolean inLiquid() {
            return IMinecraftInstance.mc.player.isInWater() || IMinecraftInstance.mc.player.isInLava();
        }

        public static Vec3d eyes(double x, double y, double z) {
            return new Vec3d(x, y + IMinecraftInstance.mc.player.getEyeHeight(), z);
        }

        public static int idOffset() {
            int id = Math.abs(IMinecraftInstance.mc.player.getNetworkId());
            return id > 999 ? id + 213 : id * 99;
        }

        public static int decimals(double d) {
            return (int) Math.round(d % 1.0 * 10000.0);
        }

        public static int posYDecimals() {
            return decimals(IMinecraftInstance.mc.player.y);
        }

        public static int motionYDecimals() {
            return (int) Math.round(IMinecraftInstance.mc.player.velocityY % 10.0 * 10000.0);
        }

        public static float forwardInput() {
            float f = 0.0F;
            if (IMinecraftInstance.mc.options.forwardKey.isPressed()) {
                f++;
            }

            if (IMinecraftInstance.mc.options.backKey.isPressed()) {
                f--;
            }

            return f;
        }

        public static float strafeInput() {
            float f = 0.0F;
            if (IMinecraftInstance.mc.options.leftKey.isPressed()) {
                f++;
            }

            if (IMinecraftInstance.mc.options.rightKey.isPressed()) {
                f--;
            }

            return f;
        }

        public static boolean isMoving() {
            return IMinecraftInstance.mc.player.input.movementForward != 0.0F
                    || IMinecraftInstance.mc.player.input.movementSideways != 0.0F;
        }

        public static boolean jumpKeyDown() {
            return GameOptions.isPressed(IMinecraftInstance.mc.options.jumpKey);
        }

        public static void setJump(boolean jump) {
            IMinecraftInstance.mc.player.input.jumping = jump;
        }

        public static void setSneak(boolean sneak) {
            boolean was = IMinecraftInstance.mc.player.input.sneaking;
            IMinecraftInstance.mc.player.input.sneaking = sneak;
            if (!was && sneak) {
                setForward((float) (getForward() * 0.3));
                setStrafe((float) (getStrafe() * 0.3));
            }
        }

        public static float getForward() {
            return IMinecraftInstance.mc.player.input.movementForward;
        }

        public static float getStrafe() {
            return IMinecraftInstance.mc.player.input.movementSideways;
        }

        public static void setForward(float f) {
            IMinecraftInstance.mc.player.input.movementForward = f;
        }

        public static void setStrafe(float f) {
            IMinecraftInstance.mc.player.input.movementSideways = f;
        }

        public static ScaffoldSupport.MoveDirection direction(double forward, double strafe) {
            ScaffoldSupport.MoveDirection d = null;
            if (forward == 0.0) {
                if (strafe == 0.0) {
                    d = ScaffoldSupport.MoveDirection.NONE;
                } else if (strafe > 0.0) {
                    d = ScaffoldSupport.MoveDirection.LEFT;
                } else if (strafe < 0.0) {
                    d = ScaffoldSupport.MoveDirection.RIGHT;
                }
            } else if (forward > 0.0) {
                if (strafe == 0.0) {
                    d = ScaffoldSupport.MoveDirection.FORWARD;
                } else if (strafe > 0.0) {
                    d = ScaffoldSupport.MoveDirection.FORWARD_LEFT;
                } else if (strafe < 0.0) {
                    d = ScaffoldSupport.MoveDirection.FORWARD_RIGHT;
                }
            } else if (forward < 0.0) {
                if (strafe == 0.0) {
                    d = ScaffoldSupport.MoveDirection.BACK;
                } else if (strafe > 0.0) {
                    d = ScaffoldSupport.MoveDirection.BACK_LEFT;
                } else if (strafe < 0.0) {
                    d = ScaffoldSupport.MoveDirection.BACK_RIGHT;
                }
            }

            return d;
        }

        public static ScaffoldSupport.MoveDirection direction() {
            return direction(forwardInput(), strafeInput());
        }

        public static boolean diagonalYaw() {
            float f = Math.abs(IMinecraftInstance.mc.player.yaw % 90.0F);
            return f > 10.0F && f < 80.0F;
        }

        public static boolean isDiagonal() {
            return diagonalYaw() || direction().diagonal;
        }

        public static float movementAngle() {
            return IMinecraftInstance.mc.player.yaw + (float) ScaffoldSupport.RotationUtils.inputAngle();
        }

        public static void jump() {
            IMinecraftInstance.mc.player.jump();
        }

        public static void jump(double speed) {
            ScaffoldSupport.MoveUtils.strafe(speed);
            jump();
        }

        public static void multiplyMotion(double d) {
            IMinecraftInstance.mc.player.velocityX *= d;
            IMinecraftInstance.mc.player.velocityZ *= d;
        }

        public static int potionLevel(StatusEffect potion) {
            StatusEffectInstance effect = IMinecraftInstance.mc.player.getEffectInstance(potion);
            return effect == null ? 0 : effect.getAmplifier() + 1;
        }

        public static int speedLevel() {
            return potionLevel(StatusEffect.SPEED);
        }

        public static double perSpeedLevel(double d) {
            return speedLevel() * d;
        }

        public static void syncCurrentPlayItem() {
            ((IAccessorPlayerControllerMP) IMinecraftInstance.mc.interactionManager).callSyncCurrentPlayItem();
        }

        public static void swing(boolean packetOnly, boolean resetEquip) {
            if (packetOnly) {
                IMinecraftInstance.mc.player.networkHandler.sendPacket(new ArmSwingC2SPacket());
            } else {
                IMinecraftInstance.mc.player.swingArm();
                if (resetEquip) {
                    IMinecraftInstance.mc.getItemInHandRenderer().onBlockUsed();
                }
            }
        }

        public static void place(HitResult hit, ItemStack itemStack) {
            float fx = (float) (hit.facePos.x - hit.getPos().getX());
            float fy = (float) (hit.facePos.y - hit.getPos().getY());
            float fz = (float) (hit.facePos.z - hit.getPos().getZ());
            IMinecraftInstance.mc.player.networkHandler.sendPacket(
                    new PlayerUseC2SPacket(hit.getPos(), hit.face.getId(), itemStack, fx, fy, fz));
            if (!isCreative()) {
                itemStack.useOn(
                        IMinecraftInstance.mc.player, IMinecraftInstance.mc.world, hit.getPos(), hit.face, fx, fy, fz);
                if (itemStack.size <= 0) {
                    ClientPlatform.onPlayerDestroyItem(IMinecraftInstance.mc.player, itemStack);
                }
            }
        }

        public static boolean isCreative() {
            return IMinecraftInstance.mc.player.abilities.creativeMode;
        }

        public static boolean isAdventure() {
            return !IMinecraftInstance.mc.player.abilities.canModifyWorld;
        }

        public static Direction facing() {
            return IMinecraftInstance.mc.player.getHorizontalFacing();
        }

        public static HitResult rayTraceFrom(double range, double x, double y, double z, float yaw, float pitch) {
            Vec3d from = eyes(x, y, z);
            Vec3d dir = ScaffoldSupport.RotationUtils.getVectorForRotation(yaw, pitch);
            Vec3d to = from.add(dir.x * range, dir.y * range, dir.z * range);
            return IMinecraftInstance.mc.world.rayTrace(from, to, false, false, false);
        }

        public static HitResult rayTraceBlocks(double range, float yaw, float pitch) {
            return rayTraceFrom(
                    range,
                    IMinecraftInstance.mc.player.x,
                    IMinecraftInstance.mc.player.y,
                    IMinecraftInstance.mc.player.z,
                    yaw,
                    pitch);
        }

        public static HitResult rayTraceBlock(BlockPos pos, Direction facing, double range, float yaw, float pitch) {
            HitResult hit = rayTraceBlocks(range, yaw, pitch);
            if (hit != null && hit.type == Type.BLOCK) {
                return ScaffoldSupport.BlockUtils.samePos(hit.getPos(), pos) && (facing == null || hit.face == facing)
                        ? hit
                        : null;
            } else {
                return null;
            }
        }
    }

    public static final class RotationUtils {
        public static float clampPitch(float pitch) {
            return MathHelper.clamp(pitch, -90.0F, 90.0F);
        }

        public static float[] fixSensitivity(float yaw, float pitch, float lastYaw, float lastPitch, boolean jitter) {
            float f5 = yaw - lastYaw;
            float f7 = pitch - lastPitch;
            float f8 = IMinecraftInstance.mc.options.mouseSensitivity * 0.6F + 0.2F;
            double d = f8 * f8 * f8 * 1.2;
            float f9 = (float) (Math.round(f5 / d) * d);
            float f10 = (float) (Math.round(f7 / d) * d);
            yaw = lastYaw + f9;
            pitch = lastPitch + f10;
            return new float[] {yaw, clampPitch(pitch)};
        }

        public static float randomOffset() {
            return (float) (Math.random() - 0.5);
        }

        public static double inputAngle() {
            double d = Math.toDegrees(
                    Math.atan2(ScaffoldSupport.PlayerUtils.strafeInput(), -ScaffoldSupport.PlayerUtils.forwardInput()));
            return (d + 360.0) % 360.0;
        }

        public static double inputAngleSigned() {
            double d = Math.toDegrees(
                    Math.atan2(-ScaffoldSupport.PlayerUtils.strafeInput(), ScaffoldSupport.PlayerUtils.forwardInput()));
            return d == 0.0 ? 0.0 : d;
        }

        public static Vec3d getVectorForRotation(float yaw, float pitch) {
            float var6;
            float f3 = MathHelper.cos((var6 = yaw * (float) (-Math.PI / 180.0)) - (float) Math.PI);
            float f4 = MathHelper.sin(var6 - (float) Math.PI);
            float var7;
            float f5 = -MathHelper.cos(var7 = pitch * (float) (-Math.PI / 180.0));
            float f6 = MathHelper.sin(var7);
            return new Vec3d(f4 * f5, f6, f3 * f5);
        }

        public static float[] searchBlockRotation(
                BlockPos pos,
                Direction facing,
                float offset,
                ScaffoldSupport.RotationUtils.Precision precision,
                float airYaw,
                boolean playerYaw) {
            float bestYaw = 0.0F;
            float bestPitch = 0.0F;
            float base = playerYaw
                    ? IMinecraftInstance.mc.player.yaw
                    : (IMinecraftInstance.mc.player.onGround ? ScaffoldSupport.PlayerUtils.movementAngle() : airYaw);
            float from = base + offset;
            float to = base + 170.0F;
            float reach = IMinecraftInstance.mc.interactionManager.getReach();
            float best = 360.0F;
            float yaw = from;

            while (yaw <= to) {
                float y = (float) (yaw + (Math.random() - 0.5));

                for (float pitch = 90.0F; pitch > 30.0F; pitch -= precision.getOffsetPitch(pitch)) {
                    float p = MathHelper.clamp((float) (pitch + (Math.random() - 0.5)), -90.0F, 90.0F);
                    HitResult hit = ScaffoldSupport.WorldUtils.rayTraceBlocks(reach, y, p);
                    float diff;
                    BlockPos hitPos;
                    if (hit != null
                            && hit.type == Type.BLOCK
                            && (facing == null || hit.face == facing)
                            && ScaffoldSupport.BlockUtils.samePos(hitPos = hit.getPos(), pos)
                            && awayFromEdge((float) (hit.facePos.y - hitPos.getY()))
                            && awayFromEdge((float) (hit.facePos.x - hitPos.getX()))
                            && awayFromEdge((float) (hit.facePos.z - hitPos.getZ()))
                            && (diff = Math.abs(base - y)) < best) {
                        best = diff;
                        bestYaw = y;
                        bestPitch = p;
                    }
                }

                yaw += precision.getOffsetYaw();
            }

            if (best == 360.0F) {
                return null;
            } else {
                return playerYaw && best > 70.0F ? null : new float[] {bestYaw, bestPitch};
            }
        }

        public static boolean awayFromEdge(float f) {
            return f == 0.0F || f == 1.0F || f > 0.06 && f < 0.94;
        }

        public enum Precision {
            Low(40.0F),
            Medium(20.0F),
            High(5.0F),
            Very_high(1.5F);

            public final float offsetYaw;

            Precision(float offsetYaw) {
                this.offsetYaw = offsetYaw;
            }

            public float getOffsetYaw() {
                return this.offsetYaw;
            }

            public float getOffsetPitch(float pitch) {
                if (pitch > 75.0F) {
                    return 1.0F;
                } else {
                    return pitch > 60.0F ? 2.0F : 5.0F;
                }
            }
        }
    }

    public static final class WorldUtils {
        public static HitResult rayTraceBlocks(double range, float yaw, float pitch) {
            Vec3d eyes = new Vec3d(
                    IMinecraftInstance.mc.player.x,
                    IMinecraftInstance.mc.player.y + IMinecraftInstance.mc.player.getEyeHeight(),
                    IMinecraftInstance.mc.player.z);
            float var11;
            float f3 = MathHelper.cos((var11 = yaw * (float) (-Math.PI / 180.0)) - (float) Math.PI);
            float f4 = MathHelper.sin(var11 - (float) Math.PI);
            float var12;
            float f5 = -MathHelper.cos(var12 = pitch * (float) (-Math.PI / 180.0));
            float f6 = MathHelper.sin(var12);
            Vec3d dir = new Vec3d(f4 * f5, f6, f3 * f5);
            Vec3d end = eyes.add(dir.x * range, dir.y * range, dir.z * range);
            return IMinecraftInstance.mc.world.rayTrace(eyes, end, false, false, false);
        }

        public static double distanceSq(double x, double y, double z, Vec3d p) {
            double d4 = x - p.x;
            double d5 = y - p.y;
            double d6 = z - p.z;
            return d4 * d4 + d5 * d5 + d6 * d6;
        }

        public static double behind(double x, double z, double dirX, double dirZ, Vec3d p) {
            double d5 = p.x - x;
            double d6 = p.z - z;
            return -(d5 * dirX + d6 * dirZ);
        }
    }
}
