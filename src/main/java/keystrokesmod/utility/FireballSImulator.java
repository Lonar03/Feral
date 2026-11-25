package keystrokesmod.utility;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.LiquidBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import net.minecraft.world.World;

public final class FireballSImulator {
    private static final int DEFAULT_MAX_TICKS = 300;
    private static final int IGNORE_SHOOTER_TICKS = 25;
    private static final double ENTITY_HIT_EXPANSION = 0.3;
    private static final double SEARCH_EXPANSION = 1.0;
    private static final double COLLISION_EPSILON_SQ = 1.0E-7;
    private static final double WATER_FLOW_ACCELERATION = 0.014;
    private static final double WATER_CHECK_EXPAND_Y = -0.4F;
    private static final double WATER_CHECK_CONTRACT = 0.001;
    private static final float AIR_MOTION_FACTOR = 0.95F;
    private static final float WATER_MOTION_FACTOR = 0.8F;

    private FireballSImulator() {}

    public static FireballSImulator.Result simulate(FireballEntity fireball) {
        return simulate(fireball, null, 300, false);
    }

    public static FireballSImulator.Result simulate(FireballEntity fireball, Vec3d accelerationOverride) {
        return simulate(fireball, accelerationOverride, 300, false);
    }

    public static FireballSImulator.Result simulateCenterLine(FireballEntity fireball, Vec3d accelerationOverride) {
        return simulate(fireball, accelerationOverride, 300, true);
    }

    public static FireballSImulator.Result simulate(FireballEntity fireball, int maxTicks) {
        return simulate(fireball, null, maxTicks, false);
    }

    private static FireballSImulator.Result simulate(
            FireballEntity fireball, Vec3d accelerationOverride, int maxTicks, boolean traceCenterLine) {
        if (fireball == null) {
            throw new IllegalArgumentException("fireball cannot be null");
        }

        World world = fireball.world;
        if (world == null) {
            Vec3d pos = new Vec3d(fireball.x, fireball.y, fireball.z);
            return new FireballSImulator.Result(FireballSImulator.HitType.NONE, null, null, null, pos, 0);
        }

        double posX = fireball.x;
        double posY = fireball.y;
        double posZ = fireball.z;
        double motionX = fireball.velocityX;
        double motionY = fireball.velocityY;
        double motionZ = fireball.velocityZ;
        double accelerationX = accelerationOverride != null ? accelerationOverride.x : fireball.accelerationX;
        double accelerationY = accelerationOverride != null ? accelerationOverride.y : fireball.accelerationY;
        double accelerationZ = accelerationOverride != null ? accelerationOverride.z : fireball.accelerationZ;
        float width = fireball.width;
        float height = fireball.height;
        double halfWidth = traceCenterLine ? 0.0 : width * 0.5;
        double collisionHeight = traceCenterLine ? 0.0 : height;
        double collisionOffsetY = traceCenterLine ? height * 0.5 : 0.0;
        LivingEntity shooter = fireball.shooter;
        int ticksInAir = Math.max(0, fireball.ticks);
        int simulatedTicks = 0;
        Vec3d finalPosition = new Vec3d(posX, posY, posZ);

        for (int tick = 0; tick < Math.max(1, maxTicks); tick++) {
            simulatedTicks = tick + 1;
            double[] motion = motionRef(motionX, motionY, motionZ);
            FireballSImulator.WaterState waterState =
                    sampleWaterState(world, posX, posY, posZ, width, height, fireball);
            if (waterState.flowDirection.length() > 0.0) {
                motion[0] += waterState.flowDirection.x * 0.014;
                motion[1] += waterState.flowDirection.y * 0.014;
                motion[2] += waterState.flowDirection.z * 0.014;
            }

            motionX = motion[0];
            motionY = motion[1];
            motionZ = motion[2];
            Vec3d start = new Vec3d(posX, posY + collisionOffsetY, posZ);
            Vec3d end = new Vec3d(posX + motionX, posY + motionY + collisionOffsetY, posZ + motionZ);
            Box sweepBounds = getSweepBounds(posX, posY, posZ, motionX, motionY, motionZ, width, height);
            FireballSImulator.BlockCollisionResult blockCollision =
                    getBlockCollision(world, start, end, sweepBounds, halfWidth, collisionHeight);
            HitResult blockHit = blockCollision.collision;
            Vec3d searchEnd = blockCollision.impactPosition != null ? blockCollision.impactPosition : end;
            double bestDistanceSq = blockCollision.distanceSq;
            HitResult bestEntityHit = null;
            Vec3d bestEntityImpact = null;
            Box searchBox = sweepBounds.grown(1.0, 1.0, 1.0);

            for (Entity candidate : world.getEntities(fireball, searchBox)) {
                if (candidate.hasCollision() && (!candidate.is(shooter) || ticksInAir >= 25)) {
                    Box expandedBox = expandTargetForProjectile(candidate.getShape(), halfWidth, collisionHeight, 0.3);
                    HitResult entityHit = expandedBox.clip(start, searchEnd);
                    if (entityHit == null && expandedBox.contains(start)) {
                        entityHit = new HitResult(candidate, start);
                    }

                    if (entityHit != null) {
                        double distanceSq = start.squaredDistanceTo(entityHit.facePos);
                        if (distanceSq + 1.0E-7 < bestDistanceSq) {
                            bestDistanceSq = distanceSq;
                            bestEntityHit = new HitResult(candidate, entityHit.facePos);
                            bestEntityHit.face = entityHit.face;
                            bestEntityImpact = entityHit.facePos.add(0.0, -collisionOffsetY, 0.0);
                        }
                    }
                }
            }

            if (bestEntityHit != null) {
                return new FireballSImulator.Result(
                        FireballSImulator.HitType.ENTITY,
                        bestEntityHit,
                        bestEntityHit.facePos,
                        bestEntityImpact,
                        bestEntityImpact,
                        simulatedTicks);
            }

            if (blockHit != null) {
                Vec3d impactPosition = blockCollision.impactPosition.add(0.0, -collisionOffsetY, 0.0);
                return new FireballSImulator.Result(
                        FireballSImulator.HitType.BLOCK,
                        blockHit,
                        blockHit.facePos,
                        impactPosition,
                        impactPosition,
                        simulatedTicks);
            }

            posX += motionX;
            posY += motionY;
            posZ += motionZ;
            finalPosition = new Vec3d(posX, posY, posZ);
            motionX += accelerationX;
            motionY += accelerationY;
            motionZ += accelerationZ;
            float motionFactor = waterState.inWater ? 0.8F : 0.95F;
            motionX *= motionFactor;
            motionY *= motionFactor;
            motionZ *= motionFactor;
            ticksInAir++;
            if (posY < -64.0) {
                break;
            }
        }

        return new FireballSImulator.Result(
                FireballSImulator.HitType.NONE, null, null, finalPosition, finalPosition, simulatedTicks);
    }

    private static Box getBoundingBox(double posX, double posY, double posZ, float width, float height) {
        double halfWidth = width * 0.5;
        return new Box(posX - halfWidth, posY, posZ - halfWidth, posX + halfWidth, posY + height, posZ + halfWidth);
    }

    private static Box getSweepBounds(
            double posX,
            double posY,
            double posZ,
            double motionX,
            double motionY,
            double motionZ,
            float width,
            float height) {
        Box startBox = getBoundingBox(posX, posY, posZ, width, height);
        Box endBox = startBox.moved(motionX, motionY, motionZ);
        return new Box(
                Math.min(startBox.minX, endBox.minX),
                Math.min(startBox.minY, endBox.minY),
                Math.min(startBox.minZ, endBox.minZ),
                Math.max(startBox.maxX, endBox.maxX),
                Math.max(startBox.maxY, endBox.maxY),
                Math.max(startBox.maxZ, endBox.maxZ));
    }

    private static Box expandTargetForProjectile(
            Box targetBox, double halfWidth, double projectileHeight, double extraExpansion) {
        return new Box(
                targetBox.minX - halfWidth - extraExpansion,
                targetBox.minY - projectileHeight - extraExpansion,
                targetBox.minZ - halfWidth - extraExpansion,
                targetBox.maxX + halfWidth + extraExpansion,
                targetBox.maxY + extraExpansion,
                targetBox.maxZ + halfWidth + extraExpansion);
    }

    private static FireballSImulator.BlockCollisionResult getBlockCollision(
            World world, Vec3d start, Vec3d end, Box sweepBounds, double halfWidth, double projectileHeight) {
        int minX = MathHelper.floor(sweepBounds.minX);
        int maxX = MathHelper.floor(sweepBounds.maxX + 1.0);
        int minY = MathHelper.floor(sweepBounds.minY);
        int maxY = MathHelper.floor(sweepBounds.maxY + 1.0);
        int minZ = MathHelper.floor(sweepBounds.minZ);
        int maxZ = MathHelper.floor(sweepBounds.maxZ + 1.0);
        if (!world.isAreaLoaded(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), true)) {
            return new FireballSImulator.BlockCollisionResult(null, null, Double.MAX_VALUE);
        }

        List<Box> collisionBoxes = new ArrayList<>();
        Mutable mutablePos = new Mutable();
        HitResult bestCollision = null;
        Vec3d bestImpactPosition = null;
        double bestDistanceSq = Double.MAX_VALUE;

        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    mutablePos.set(x, y, z);
                    BlockState blockState = world.getBlockState(mutablePos);
                    Block block = blockState.getBlock();
                    if (block.canRayTrace(blockState, false)) {
                        collisionBoxes.clear();
                        block.addCollisions(world, mutablePos, blockState, sweepBounds, collisionBoxes, null);

                        for (Box collisionBox : collisionBoxes) {
                            Box expandedBox = expandTargetForProjectile(collisionBox, halfWidth, projectileHeight, 0.0);
                            HitResult collision = expandedBox.clip(start, end);
                            if (collision == null && expandedBox.contains(start)) {
                                collision = new HitResult(start, Direction.UP, new BlockPos(mutablePos));
                            }

                            if (collision != null) {
                                double distanceSq = start.squaredDistanceTo(collision.facePos);
                                if (distanceSq + 1.0E-7 < bestDistanceSq) {
                                    bestDistanceSq = distanceSq;
                                    bestImpactPosition = collision.facePos;
                                    bestCollision =
                                            new HitResult(collision.facePos, collision.face, new BlockPos(mutablePos));
                                }
                            }
                        }
                    }
                }
            }
        }

        return new FireballSImulator.BlockCollisionResult(bestCollision, bestImpactPosition, bestDistanceSq);
    }

    private static FireballSImulator.WaterState sampleWaterState(
            World world, double posX, double posY, double posZ, float width, float height, Entity fireball) {
        Box waterCheckBox = getBoundingBox(posX, posY, posZ, width, height)
                .grown(0.0, -0.4F, 0.0)
                .contract(0.001, 0.001, 0.001);
        int minX = MathHelper.floor(waterCheckBox.minX);
        int maxX = MathHelper.floor(waterCheckBox.maxX + 1.0);
        int minY = MathHelper.floor(waterCheckBox.minY);
        int maxY = MathHelper.floor(waterCheckBox.maxY + 1.0);
        int minZ = MathHelper.floor(waterCheckBox.minZ);
        int maxZ = MathHelper.floor(waterCheckBox.maxZ + 1.0);
        if (!world.isAreaLoaded(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), true)) {
            return new FireballSImulator.WaterState(false, new Vec3d(0.0, 0.0, 0.0));
        }

        boolean inWater = false;
        Vec3d flow = new Vec3d(0.0, 0.0, 0.0);
        Mutable mutablePos = new Mutable();

        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    mutablePos.set(x, y, z);
                    BlockState blockState = world.getBlockState(mutablePos);
                    if (blockState.getBlock().getMaterial() == Material.WATER) {
                        double liquidSurfaceY =
                                y + 1 - LiquidBlock.getHeightLoss((Integer) blockState.get(LiquidBlock.LEVEL));
                        if (!(maxY < liquidSurfaceY)) {
                            inWater = true;
                            flow = blockState.getBlock().applyMaterialDrag(world, mutablePos, fireball, flow);
                        }
                    }
                }
            }
        }

        if (flow.length() > 0.0) {
            flow = flow.normalize();
        }

        return new FireballSImulator.WaterState(inWater, flow);
    }

    private static double[] motionRef(double motionX, double motionY, double motionZ) {
        return new double[] {motionX, motionY, motionZ};
    }

    private static final class BlockCollisionResult {
        private final HitResult collision;
        private final Vec3d impactPosition;
        private final double distanceSq;

        private BlockCollisionResult(HitResult collision, Vec3d impactPosition, double distanceSq) {
            this.collision = collision;
            this.impactPosition = impactPosition;
            this.distanceSq = distanceSq;
        }
    }

    public enum HitType {
        NONE,
        BLOCK,
        ENTITY;
    }

    public static final class Result {
        private final FireballSImulator.HitType hitType;
        private final HitResult collision;
        private final Vec3d hitPosition;
        private final Vec3d impactPosition;
        private final Vec3d finalPosition;
        private final int simulatedTicks;

        private Result(
                FireballSImulator.HitType hitType,
                HitResult collision,
                Vec3d hitPosition,
                Vec3d impactPosition,
                Vec3d finalPosition,
                int simulatedTicks) {
            this.hitType = hitType;
            this.collision = collision;
            this.hitPosition = hitPosition;
            this.impactPosition = impactPosition;
            this.finalPosition = finalPosition;
            this.simulatedTicks = simulatedTicks;
        }

        public FireballSImulator.HitType getHitType() {
            return this.hitType;
        }

        public boolean collided() {
            return this.hitType != FireballSImulator.HitType.NONE;
        }

        public boolean hitBlock() {
            return this.hitType == FireballSImulator.HitType.BLOCK;
        }

        public boolean hitEntity() {
            return this.hitType == FireballSImulator.HitType.ENTITY;
        }

        public HitResult getCollision() {
            return this.collision;
        }

        public Entity getTarget() {
            return this.collision != null ? this.collision.entity : null;
        }

        public Entity getHitEntity() {
            return this.getTarget();
        }

        public BlockPos getHitBlockPos() {
            return this.collision != null ? this.collision.getPos() : null;
        }

        public Vec3d getHitPosition() {
            return this.hitPosition;
        }

        public Vec3d getImpactPosition() {
            return this.impactPosition;
        }

        public Vec3d getFinalPosition() {
            return this.finalPosition;
        }

        public int getSimulatedTicks() {
            return this.simulatedTicks;
        }
    }

    private static final class WaterState {
        private final boolean inWater;
        private final Vec3d flowDirection;

        private WaterState(boolean inWater, Vec3d flowDirection) {
            this.inWater = inWater;
            this.flowDirection = flowDirection;
        }
    }
}
