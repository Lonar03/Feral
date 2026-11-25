package keystrokesmod.utility;

import com.google.common.base.Predicates;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import keystrokesmod.event.PreMotionEvent;
import keystrokesmod.helper.RotationHelper;
import keystrokesmod.module.impl.client.Settings;
import keystrokesmod.platform.ClientPlatform;
import net.minecraft.block.Block;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityFilter;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import net.minecraft.world.HitResult.Type;

public class RotationUtils implements IMinecraftInstance {
    public static float renderPitch;
    public static float prevRenderPitch;
    public static float renderYaw;
    public static float prevRenderYaw;
    public static float[] serverRotations = new float[] {0.0F, 0.0F};
    public static Float[] fakeRotations;
    public static boolean setFakeRotations;
    private static final double BACKUP_FACE_INSET = 0.05;
    private static final int BACKUP_TARGET_TOTAL = 30;
    private static final float FAR_THRESHOLD = 180.0F;

    public static void setFakeRotations(float yaw, float pitch) {
        fakeRotations = new Float[] {yaw, pitch};
        setFakeRotations = true;
    }

    public static void setRenderYaw(float yaw) {
        mc.player.headYaw = yaw;
        if (Settings.rotateBody.isToggled() && Settings.fullBody.isToggled()) {
            mc.player.lastBodyYaw = prevRenderYaw;
            mc.player.bodyYaw = yaw;
        }
    }

    public static float[] getRotations(BlockPos blockPos, float n, float n2) {
        float[] array = getRotations(blockPos);
        return fixRotation(array[0], array[1], n, n2);
    }

    public static float[] getRotationsToBlock(BlockPos blockPos, Direction facing, float yaw, float pitch) {
        float[] array = getRotationsToBlock(blockPos, facing);
        return fixRotation(array[0], array[1], yaw, pitch);
    }

    public static float[] getRotations(BlockPos blockPos) {
        double x = blockPos.getX() + 0.45 - mc.player.x;
        double y = blockPos.getY() + 0.45 - (mc.player.y + mc.player.getEyeHeight());
        double z = blockPos.getZ() + 0.45 - mc.player.z;
        float angleToBlock = (float) (Math.atan2(z, x) * (180.0 / Math.PI)) - 90.0F;
        float deltaYaw = MathHelper.wrapDegrees(angleToBlock - mc.player.yaw);
        float yaw = mc.player.yaw + deltaYaw;
        double distance = MathHelper.sqrt(x * x + z * z);
        float angleToBlockPitch = (float) (-(Math.atan2(y, distance) * (180.0 / Math.PI)));
        float deltaPitch = MathHelper.wrapDegrees(angleToBlockPitch - mc.player.pitch);
        float pitch = mc.player.pitch + deltaPitch;
        pitch = clampPitch(pitch);
        return new float[] {yaw, pitch};
    }

    public static float[] getRotations(double posX, double posY, double posZ) {
        double x = posX + 1.0 - mc.player.x;
        double y = posY + 1.0 - (mc.player.y + mc.player.getEyeHeight());
        double z = posZ + 1.0 - mc.player.z;
        float angleToBlock = (float) (Math.atan2(z, x) * (180.0 / Math.PI)) - 90.0F;
        float deltaYaw = MathHelper.wrapDegrees(angleToBlock - mc.player.yaw);
        float yaw = mc.player.yaw + deltaYaw;
        double distance = MathHelper.sqrt(x * x + z * z);
        float angleToBlockPitch = (float) (-(Math.atan2(y, distance) * (180.0 / Math.PI)));
        float deltaPitch = MathHelper.wrapDegrees(angleToBlockPitch - mc.player.pitch);
        float pitch = mc.player.pitch + deltaPitch;
        pitch = clampPitch(pitch);
        return new float[] {yaw, pitch};
    }

    public static float[] getRotations(Vec3d vec3) {
        double x = vec3.x + 1.0 - mc.player.x;
        double y = vec3.y + 1.0 - (mc.player.y + mc.player.getEyeHeight());
        double z = vec3.z + 1.0 - mc.player.z;
        float angleToBlock = (float) (Math.atan2(z, x) * (180.0 / Math.PI)) - 90.0F;
        float deltaYaw = MathHelper.wrapDegrees(angleToBlock - mc.player.yaw);
        float yaw = mc.player.yaw + deltaYaw;
        double distance = MathHelper.sqrt(x * x + z * z);
        float angleToBlockPitch = (float) (-(Math.atan2(y, distance) * (180.0 / Math.PI)));
        float deltaPitch = MathHelper.wrapDegrees(angleToBlockPitch - mc.player.pitch);
        float pitch = mc.player.pitch + deltaPitch;
        pitch = clampPitch(pitch);
        return new float[] {yaw, pitch};
    }

    public static float[] getRotations(Entity entity, float yaw, float pitch) {
        float[] array = getRotations(entity);
        return array == null ? null : fixRotation(array[0], array[1], yaw, pitch);
    }

    public static float[] getRotationsToBlock(BlockPos pos, Direction facing) {
        double diffX = pos.getX() + 0.45 - mc.player.x;
        double diffY = pos.getY() + 0.45 - (mc.player.y + mc.player.getEyeHeight());
        double diffZ = pos.getZ() + 0.45 - mc.player.z;
        if (facing != null) {
            diffX += facing.getNormal().getX() * 0.5;
            diffY += facing.getNormal().getY() * 0.5;
            diffZ += facing.getNormal().getZ() * 0.5;
        }

        double dist = MathHelper.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) (Math.atan2(diffZ, diffX) * (float) (180.0 / Math.PI)) - 90.0F;
        float pitch = (float) (-(Math.atan2(diffY, dist) * (float) (180.0 / Math.PI)));
        return new float[] {
            mc.player.yaw + MathHelper.wrapDegrees(yaw - mc.player.yaw),
            clampPitch(mc.player.pitch + MathHelper.wrapDegrees(pitch - mc.player.pitch))
        };
    }

    public static double distanceFromYaw(Entity entity, boolean b) {
        return Math.abs(MathHelper.wrapDegrees(
                i(entity.x, entity.z) - (b && PreMotionEvent.setRenderYaw() ? renderYaw : mc.player.yaw)));
    }

    public static float i(double n, double n2) {
        return (float) (Math.atan2(n - mc.player.x, n2 - mc.player.z) * (float) (180.0 / Math.PI) * -1.0);
    }

    public static boolean isPossibleToHit(Entity target, double reach, float[] rotations) {
        Vec3d eyePosition = mc.player.getEyePosition(1.0F);
        float yaw = rotations[0];
        float pitch = rotations[1];
        float radianYaw = -yaw * (float) (Math.PI / 180.0) - (float) Math.PI;
        float radianPitch = -pitch * (float) (Math.PI / 180.0);
        float cosYaw = MathHelper.cos(radianYaw);
        float sinYaw = MathHelper.sin(radianYaw);
        float cosPitch = -MathHelper.cos(radianPitch);
        float sinPitch = MathHelper.sin(radianPitch);
        Vec3d lookVector = new Vec3d(sinYaw * cosPitch, sinPitch, cosYaw * cosPitch);
        double lookVecX = lookVector.x * reach;
        double lookVecY = lookVector.y * reach;
        double lookVecZ = lookVector.z * reach;
        Vec3d endPosition = eyePosition.add(lookVecX, lookVecY, lookVecZ);
        Entity renderViewEntity = mc.getCamera();
        Box expandedBox = renderViewEntity
                .getShape()
                .expanded(lookVecX, lookVecY, lookVecZ)
                .grown(1.0, 1.0, 1.0);

        for (Entity entity : mc.world.getEntities(renderViewEntity, expandedBox)) {
            if (entity == target && entity.hasCollision()) {
                float borderSize = entity.getPickRadius();
                Box entityBox = entity.getShape().grown(borderSize, borderSize, borderSize);
                HitResult intercept = entityBox.clip(eyePosition, endPosition);
                return intercept != null;
            }
        }

        return false;
    }

    public static boolean inRange(BlockPos blockPos, double n) {
        float[] array = getRotations(blockPos);
        Vec3d getPositionEyes = mc.player.getEyePosition(1.0F);
        float n2 = -array[0] * (float) (Math.PI / 180.0);
        float n3 = -array[1] * (float) (Math.PI / 180.0);
        float cos = MathHelper.cos(n2 - (float) Math.PI);
        float sin = MathHelper.sin(n2 - (float) Math.PI);
        float n4 = -MathHelper.cos(n3);
        Vec3d vec3 = new Vec3d(sin * n4, MathHelper.sin(n3), cos * n4);
        Block block = BlockUtils.getBlock(blockPos);
        BlockState blockState = BlockUtils.getBlockState(blockPos);
        if (block != null && blockState != null) {
            Box boundingBox = block.getCollisionShape(mc.world, blockPos, blockState);
            if (boundingBox != null) {
                Vec3d targetVec = getPositionEyes.add(vec3.x * n, vec3.y * n, vec3.z * n);
                HitResult intercept = boundingBox.clip(getPositionEyes, targetVec);
                if (intercept != null) {
                    return true;
                }
            }
        }

        return false;
    }

    public static float[] getRotations(Entity entity) {
        return getRotations(entity, RotationUtils.PLAYER_OFFSETS.NONE);
    }

    public static float[] getRotations(Entity entity, RotationUtils.PLAYER_OFFSETS playerOffset) {
        if (entity == null) {
            return null;
        }

        double deltaX = entity.x - mc.player.x;
        double deltaZ = entity.z - mc.player.z;
        double deltaY;
        if (entity instanceof LivingEntity) {
            LivingEntity entityLivingBase = (LivingEntity) entity;
            deltaY = entityLivingBase.y
                    + playerOffset.getHeightOffset(entityLivingBase) * 0.9
                    - (mc.player.y + mc.player.getEyeHeight());
        } else {
            deltaY = (entity.getShape().minY + entity.getShape().maxY) / 2.0 - (mc.player.y + mc.player.getEyeHeight());
        }

        return new float[] {
            mc.player.yaw
                    + MathHelper.wrapDegrees(
                            (float) (Math.atan2(deltaZ, deltaX) * (float) (180.0 / Math.PI)) - 90.0F - mc.player.yaw),
            clampPitch(mc.player.pitch
                    + MathHelper.wrapDegrees((float)
                                    (-(Math.atan2(deltaY, MathHelper.sqrt(deltaX * deltaX + deltaZ * deltaZ))
                                            * (float) (180.0 / Math.PI)))
                            - mc.player.pitch)
                    + 3.0F)
        };
    }

    public static float[] getRotationsToPoint(double x, double y, double z) {
        return getRotationsToPoint(x, y, z, mc.player.yaw, mc.player.pitch);
    }

    public static float[] getRotationsToPoint(double x, double y, double z, float baseYaw, float basePitch) {
        return getRotationsToPoint(x, y, z, baseYaw, basePitch, 3.0F);
    }

    public static float[] getRotationsToPoint(
            double x, double y, double z, float baseYaw, float basePitch, float pitchOffset) {
        double deltaX = x - mc.player.x;
        double deltaZ = z - mc.player.z;
        double deltaY = y - (mc.player.y + mc.player.getEyeHeight());
        double horizDistSq = deltaX * deltaX + deltaZ * deltaZ;
        float yaw;
        float targetPitch;
        if (horizDistSq < 1.0E-12) {
            yaw = baseYaw;
            targetPitch = (float) (-(Math.atan2(deltaY, 0.0) * (float) (180.0 / Math.PI)));
        } else {
            float targetYaw = (float) (Math.atan2(deltaZ, deltaX) * (float) (180.0 / Math.PI)) - 90.0F;
            yaw = baseYaw + MathHelper.wrapDegrees(targetYaw - baseYaw);
            double horizDist = MathHelper.sqrt(horizDistSq);
            targetPitch = (float) (-(Math.atan2(deltaY, horizDist) * (float) (180.0 / Math.PI)));
        }

        float pitch = basePitch + MathHelper.wrapDegrees(targetPitch - basePitch) + pitchOffset;
        return new float[] {yaw, clampPitch(pitch)};
    }

    public static float[] getRotations(
            Entity entity, double horizontalMultipoint, double verticalMultipoint, float baseYaw, float basePitch) {
        return getRotations(entity, horizontalMultipoint, verticalMultipoint, baseYaw, basePitch, 3.0F);
    }

    public static float[] getRotations(
            Entity entity,
            double horizontalMultipoint,
            double verticalMultipoint,
            float baseYaw,
            float basePitch,
            float pitchOffset) {
        Vec3d aimPoint = getAimPoint(entity, horizontalMultipoint, verticalMultipoint);
        return aimPoint == null
                ? null
                : getRotationsToPoint(aimPoint.x, aimPoint.y, aimPoint.z, baseYaw, basePitch, pitchOffset);
    }

    public static Vec3d getAimPoint(Entity entity, double horizontalMultipoint, double verticalMultipoint) {
        if (entity != null && mc.player != null) {
            float borderSize = entity.getPickRadius();
            Box bb = entity.getShape().grown(borderSize, borderSize, borderSize);
            double centerX = (bb.minX + bb.maxX) / 2.0;
            double centerY;
            if (entity instanceof LivingEntity) {
                centerY = entity.y + ((LivingEntity) entity).getEyeHeight();
            } else {
                centerY = (bb.minY + bb.maxY) / 2.0;
            }

            double centerZ = (bb.minZ + bb.maxZ) / 2.0;
            Vec3d eye = mc.player.getEyePosition(1.0F);
            if (bb.contains(eye)) {
                return new Vec3d(centerX, eye.y, centerZ);
            }

            Vec3d cl = closestPointOnAabb(bb, eye);
            double tH = Math.max(0.0, Math.min(1.0, horizontalMultipoint / 100.0));
            double tV = Math.max(0.0, Math.min(1.0, verticalMultipoint / 100.0));
            double targetX = centerX + (cl.x - centerX) * tH;
            double targetY = centerY + (cl.y - centerY) * tV;
            double targetZ = centerZ + (cl.z - centerZ) * tH;
            return new Vec3d(targetX, targetY, targetZ);
        } else {
            return null;
        }
    }

    public static Vec3d closestPointOnAabb(Box box, Vec3d point) {
        double x = Math.max(box.minX, Math.min(box.maxX, point.x));
        double y = Math.max(box.minY, Math.min(box.maxY, point.y));
        double z = Math.max(box.minZ, Math.min(box.maxZ, point.z));
        return new Vec3d(x, y, z);
    }

    public static List<Vec3d> buildBackupPoints(Entity entity, Vec3d eye) {
        if (entity != null && mc.player != null) {
            float borderSize = entity.getPickRadius();
            Box bb = entity.getShape().grown(borderSize, borderSize, borderSize);
            double sizeX = bb.maxX - bb.minX;
            double sizeY = bb.maxY - bb.minY;
            double sizeZ = bb.maxZ - bb.minZ;
            boolean xPos = eye.x > bb.maxX;
            boolean xNeg = eye.x < bb.minX;
            boolean yPos = eye.y > bb.maxY;
            boolean yNeg = eye.y < bb.minY;
            boolean zPos = eye.z > bb.maxZ;
            boolean zNeg = eye.z < bb.minZ;
            int visibleFaceCount = (!xPos && !xNeg ? 0 : 1) + (!yPos && !yNeg ? 0 : 1) + (!zPos && !zNeg ? 0 : 1);
            if (visibleFaceCount == 0) {
                return new ArrayList<>();
            }

            int pointsPerFace = 30 / visibleFaceCount;
            List<Vec3d> points = new ArrayList<>(36);
            if (xPos || xNeg) {
                double fixedX = xPos ? bb.maxX - 0.05 : bb.minX + 0.05;
                addFaceGrid(
                        points,
                        0,
                        fixedX,
                        bb.minY + 0.05,
                        bb.maxY - 0.05,
                        bb.minZ + 0.05,
                        bb.maxZ - 0.05,
                        pointsPerFace,
                        sizeY,
                        sizeZ);
            }

            if (yPos || yNeg) {
                double fixedY = yPos ? bb.maxY - 0.05 : bb.minY + 0.05;
                addFaceGrid(
                        points,
                        1,
                        fixedY,
                        bb.minX + 0.05,
                        bb.maxX - 0.05,
                        bb.minZ + 0.05,
                        bb.maxZ - 0.05,
                        pointsPerFace,
                        sizeX,
                        sizeZ);
            }

            if (zPos || zNeg) {
                double fixedZ = zPos ? bb.maxZ - 0.05 : bb.minZ + 0.05;
                addFaceGrid(
                        points,
                        2,
                        fixedZ,
                        bb.minX + 0.05,
                        bb.maxX - 0.05,
                        bb.minY + 0.05,
                        bb.maxY - 0.05,
                        pointsPerFace,
                        sizeX,
                        sizeY);
            }

            return points;
        } else {
            return new ArrayList<>();
        }
    }

    private static void addFaceGrid(
            List<Vec3d> out,
            int fixedAxis,
            double fixedVal,
            double uMin,
            double uMax,
            double vMin,
            double vMax,
            int targetPoints,
            double dimU,
            double dimV) {
        if (!(dimU < 1.0E-4) && !(dimV < 1.0E-4)) {
            double ratio = dimU / dimV;
            int gridU = Math.max(2, (int) Math.round(Math.sqrt(targetPoints * ratio)));
            int gridV = Math.max(2, (int) Math.round(Math.sqrt(targetPoints / ratio)));

            for (int i = 0; i < gridU; i++) {
                double u = uMin + (uMax - uMin) * i / (gridU - 1);

                for (int j = 0; j < gridV; j++) {
                    double v = vMin + (vMax - vMin) * j / (gridV - 1);
                    switch (fixedAxis) {
                        case 0:
                            out.add(new Vec3d(fixedVal, u, v));
                            break;
                        case 1:
                            out.add(new Vec3d(u, fixedVal, v));
                            break;
                        case 2:
                            out.add(new Vec3d(u, v, fixedVal));
                    }
                }
            }
        } else {
            double uMid = (uMin + uMax) / 2.0;
            double vMid = (vMin + vMax) / 2.0;
            switch (fixedAxis) {
                case 0:
                    out.add(new Vec3d(fixedVal, uMid, vMid));
                    break;
                case 1:
                    out.add(new Vec3d(uMid, fixedVal, vMid));
                    break;
                case 2:
                    out.add(new Vec3d(uMid, vMid, fixedVal));
            }
        }
    }

    public static double distanceSqFromEyeToClosestOnAABB(Entity entity) {
        if (entity != null && mc.player != null) {
            Vec3d eye = mc.player.getEyePosition(1.0F);
            float borderSize = entity.getPickRadius();
            Box bb = entity.getShape().grown(borderSize, borderSize, borderSize);
            Vec3d closest = closestPointOnAabb(bb, eye);
            double dx = eye.x - closest.x;
            double dy = eye.y - closest.y;
            double dz = eye.z - closest.z;
            return dx * dx + dy * dy + dz * dz;
        } else {
            return Double.MAX_VALUE;
        }
    }

    public static double distanceFromEyeToClosestOnAABB(Entity entity) {
        double dSq = distanceSqFromEyeToClosestOnAABB(entity);
        return dSq == Double.MAX_VALUE ? Double.MAX_VALUE : Math.sqrt(dSq);
    }

    public static double distanceFromEyeToClosestOnAABB(Entity entity, Vec3d position) {
        double dSq = distanceSqFromEyeToClosestOnAABB(entity, position);
        return dSq == Double.MAX_VALUE ? Double.MAX_VALUE : Math.sqrt(dSq);
    }

    public static double distanceSqFromEyeToClosestOnAABB(Entity entity, Vec3d position) {
        if (entity != null && position != null && mc.player != null) {
            Vec3d eye = mc.player.getEyePosition(1.0F);
            float borderSize = entity.getPickRadius();
            double offsetX = position.x - entity.x;
            double offsetY = position.y - entity.y;
            double offsetZ = position.z - entity.z;
            Box bb = entity.getShape().moved(offsetX, offsetY, offsetZ).grown(borderSize, borderSize, borderSize);
            Vec3d closest = closestPointOnAabb(bb, eye);
            double dx = eye.x - closest.x;
            double dy = eye.y - closest.y;
            double dz = eye.z - closest.z;
            return dx * dx + dy * dy + dz * dz;
        } else {
            return Double.MAX_VALUE;
        }
    }

    public static boolean canAimAtPoint(Vec3d eye, Vec3d point, Entity target, double range) {
        return canAimAtPoint(eye, point, target, range, false, true);
    }

    public static boolean canAimAtPoint(
            Vec3d eye,
            Vec3d point,
            Entity target,
            double range,
            boolean allowThroughBlocks,
            boolean allowThroughEntities) {
        return canAimAtPoint(eye, point, target, range, allowThroughBlocks, allowThroughEntities, false);
    }

    public static boolean canAimAtPoint(
            Vec3d eye,
            Vec3d point,
            Entity target,
            double range,
            boolean allowThroughBlocks,
            boolean allowThroughEntities,
            boolean ignoreOpenFenceGates) {
        if (target == null) {
            return false;
        }

        double dx = point.x - eye.x;
        double dy = point.y - eye.y;
        double dz = point.z - eye.z;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-6) {
            return false;
        }

        double scale = range / len;
        Vec3d end = new Vec3d(eye.x + dx * scale, eye.y + dy * scale, eye.z + dz * scale);
        float borderSize = target.getPickRadius();
        Box aabb = target.getShape().grown(borderSize, borderSize, borderSize);
        HitResult entityHit = aabb.clip(eye, end);
        if (entityHit == null) {
            return false;
        }

        double entityDistSq = eye.squaredDistanceTo(entityHit.facePos);
        if (!allowThroughBlocks) {
            HitResult blockHit = ignoreOpenFenceGates
                    ? rayTraceBlocksIgnoringOpenFenceGates(eye, end, false, false, false)
                    : mc.world.rayTrace(eye, end, false, false, false);
            if (blockHit != null && blockHit.type == Type.BLOCK) {
                double blockDistSq = eye.squaredDistanceTo(blockHit.facePos);
                if (blockDistSq < entityDistSq) {
                    return false;
                }
            }
        }

        return allowThroughEntities || !hasEntityBlockingPath(eye, end, target, entityDistSq);
    }

    public static HitResult rayTraceBlocksIgnoringOpenFenceGates(
            Vec3d start,
            Vec3d end,
            boolean stopOnLiquid,
            boolean ignoreBlockWithoutBoundingBox,
            boolean returnLastUncollidableBlock) {
        if (mc.world != null && start != null && end != null) {
            Vec3d traceStart = start;

            for (int ignoredGates = 0; ignoredGates < 200; ignoredGates++) {
                HitResult hit = mc.world.rayTrace(
                        traceStart, end, stopOnLiquid, ignoreBlockWithoutBoundingBox, returnLastUncollidableBlock);
                if (!isOpenFenceGateHit(hit)) {
                    return hit;
                }

                traceStart = movePastBlock(hit.facePos, end, hit.getPos());
                if (traceStart == null) {
                    return null;
                }
            }

            return null;
        } else {
            return null;
        }
    }

    private static boolean isOpenFenceGateHit(HitResult hit) {
        if (hit != null && hit.type == Type.BLOCK && hit.getPos() != null) {
            BlockState state = mc.world.getBlockState(hit.getPos());
            return state.getBlock() instanceof FenceGateBlock && (Boolean) state.get(FenceGateBlock.OPEN);
        } else {
            return false;
        }
    }

    private static Vec3d movePastBlock(Vec3d hitVec, Vec3d end, BlockPos blockPos) {
        if (hitVec != null && end != null && blockPos != null) {
            double dx = end.x - hitVec.x;
            double dy = end.y - hitVec.y;
            double dz = end.z - hitVec.z;
            double remainingDistance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (remainingDistance < 1.0E-7) {
                return null;
            }

            dx /= remainingDistance;
            dy /= remainingDistance;
            dz /= remainingDistance;
            double distanceToExit = Double.POSITIVE_INFINITY;
            if (dx > 1.0E-7) {
                distanceToExit = Math.min(distanceToExit, (blockPos.getX() + 1.0 - hitVec.x) / dx);
            } else if (dx < -1.0E-7) {
                distanceToExit = Math.min(distanceToExit, (blockPos.getX() - hitVec.x) / dx);
            }

            if (dy > 1.0E-7) {
                distanceToExit = Math.min(distanceToExit, (blockPos.getY() + 1.0 - hitVec.y) / dy);
            } else if (dy < -1.0E-7) {
                distanceToExit = Math.min(distanceToExit, (blockPos.getY() - hitVec.y) / dy);
            }

            if (dz > 1.0E-7) {
                distanceToExit = Math.min(distanceToExit, (blockPos.getZ() + 1.0 - hitVec.z) / dz);
            } else if (dz < -1.0E-7) {
                distanceToExit = Math.min(distanceToExit, (blockPos.getZ() - hitVec.z) / dz);
            }

            double advance = Math.max(0.0, distanceToExit) + 1.0E-5;
            return !Double.isInfinite(distanceToExit)
                            && !Double.isNaN(distanceToExit)
                            && !(advance >= remainingDistance)
                    ? hitVec.add(dx * advance, dy * advance, dz * advance)
                    : null;
        } else {
            return null;
        }
    }

    private static boolean hasEntityBlockingPath(Vec3d eye, Vec3d end, Entity target, double targetDistSq) {
        if (mc.player != null && mc.world != null) {
            Vec3d delta = end.subtract(eye);
            Box searchBox =
                    mc.player.getShape().expanded(delta.x, delta.y, delta.z).grown(1.0, 1.0, 1.0);

            for (Entity entity : mc.world.getEntities(
                    mc.player, searchBox, Predicates.and(EntityFilter.NOT_SPECTATOR, Entity::hasCollision))) {
                if (entity != null && entity != target && !entity.removed) {
                    float border = entity.getPickRadius();
                    Box bb = entity.getShape().grown(border, border, border);
                    HitResult hit = bb.clip(eye, end);
                    if (bb.contains(eye)) {
                        return true;
                    }

                    if (hit != null) {
                        double entityDistSq = eye.squaredDistanceTo(hit.facePos);
                        if (entityDistSq < targetDistSq - 1.0E-7) {
                            return true;
                        }
                    }
                }
            }

            return false;
        } else {
            return false;
        }
    }

    public static boolean isPathBlockedByEntity(Vec3d eye, Vec3d hitVec, Entity target) {
        if (eye != null && hitVec != null && target != null) {
            double targetDistSq = eye.squaredDistanceTo(hitVec);
            return hasEntityBlockingPath(eye, hitVec, target, targetDistSq);
        } else {
            return false;
        }
    }

    public static boolean isEyeInsideEntityAABB(Entity entity) {
        if (entity != null && mc.player != null) {
            Vec3d eye = mc.player.getEyePosition(1.0F);
            float borderSize = entity.getPickRadius();
            Box bb = entity.getShape().grown(borderSize, borderSize, borderSize);
            return bb.contains(eye);
        } else {
            return false;
        }
    }

    private static boolean mainRayHitsTargetAABB(Vec3d eye, Vec3d point, Entity target, double range) {
        double dx = point.x - eye.x;
        double dy = point.y - eye.y;
        double dz = point.z - eye.z;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-6) {
            return false;
        }

        double scale = range / len;
        Vec3d end = new Vec3d(eye.x + dx * scale, eye.y + dy * scale, eye.z + dz * scale);
        float borderSize = target.getPickRadius();
        Box aabb = target.getShape().grown(borderSize, borderSize, borderSize);
        return aabb.clip(eye, end) != null;
    }

    public static boolean hasValidAimPoint(Entity entity, double hMult, double vMult, double range) {
        return hasValidAimPoint(entity, hMult, vMult, range, false, true);
    }

    public static boolean hasValidAimPoint(
            Entity entity,
            double hMult,
            double vMult,
            double range,
            boolean allowThroughBlocks,
            boolean allowThroughEntities) {
        return hasValidAimPoint(entity, hMult, vMult, range, allowThroughBlocks, allowThroughEntities, false);
    }

    public static boolean hasValidAimPoint(
            Entity entity,
            double hMult,
            double vMult,
            double range,
            boolean allowThroughBlocks,
            boolean allowThroughEntities,
            boolean ignoreOpenFenceGates) {
        if (entity != null && mc.player != null) {
            if (isEyeInsideEntityAABB(entity)) {
                return true;
            }

            Vec3d mainPoint = getAimPoint(entity, hMult, vMult);
            if (mainPoint == null) {
                return false;
            }

            Vec3d eye = mc.player.getEyePosition(1.0F);
            if (eye.squaredDistanceTo(mainPoint) < 1.0E-6) {
                return true;
            }

            if (!mainRayHitsTargetAABB(eye, mainPoint, entity, range)) {
                return false;
            }

            if (canAimAtPoint(
                    eye, mainPoint, entity, range, allowThroughBlocks, allowThroughEntities, ignoreOpenFenceGates)) {
                return true;
            }

            List<Vec3d> backups = buildBackupPoints(entity, eye);
            Collections.sort(backups, Comparator.comparingDouble(px -> {
                double dx = px.x - eye.x;
                double dy = px.y - eye.y;
                double dz = px.z - eye.z;
                return dx * dx + dy * dy + dz * dz;
            }));

            for (Vec3d p : backups) {
                if (canAimAtPoint(
                        eye, p, entity, range, allowThroughBlocks, allowThroughEntities, ignoreOpenFenceGates)) {
                    return true;
                }
            }

            return false;
        } else {
            return false;
        }
    }

    public static float[] getRotationsWithBackup(
            Entity entity,
            double horizontalMultipoint,
            double verticalMultipoint,
            float baseYaw,
            float basePitch,
            double range) {
        return getRotationsWithBackup(
                entity, horizontalMultipoint, verticalMultipoint, baseYaw, basePitch, range, false, true);
    }

    public static float[] getRotationsWithBackup(
            Entity entity,
            double horizontalMultipoint,
            double verticalMultipoint,
            float baseYaw,
            float basePitch,
            double range,
            boolean allowThroughBlocks,
            boolean allowThroughEntities) {
        return getRotationsWithBackup(
                entity,
                horizontalMultipoint,
                verticalMultipoint,
                baseYaw,
                basePitch,
                range,
                allowThroughBlocks,
                allowThroughEntities,
                false);
    }

    public static float[] getRotationsWithBackup(
            Entity entity,
            double horizontalMultipoint,
            double verticalMultipoint,
            float baseYaw,
            float basePitch,
            double range,
            boolean allowThroughBlocks,
            boolean allowThroughEntities,
            boolean ignoreOpenFenceGates) {
        return getRotationsWithBackup(
                entity,
                horizontalMultipoint,
                verticalMultipoint,
                baseYaw,
                basePitch,
                range,
                allowThroughBlocks,
                allowThroughEntities,
                ignoreOpenFenceGates,
                3.0F);
    }

    public static float[] getRotationsWithBackup(
            Entity entity,
            double horizontalMultipoint,
            double verticalMultipoint,
            float baseYaw,
            float basePitch,
            double range,
            boolean allowThroughBlocks,
            boolean allowThroughEntities,
            boolean ignoreOpenFenceGates,
            float pitchOffset) {
        if (entity != null && mc.player != null) {
            Vec3d eye = mc.player.getEyePosition(1.0F);
            float borderSize = entity.getPickRadius();
            Box bb = entity.getShape().grown(borderSize, borderSize, borderSize);
            if (bb.contains(eye)) {
                double centerX = (bb.minX + bb.maxX) / 2.0;
                double centerZ = (bb.minZ + bb.maxZ) / 2.0;
                return getRotationsToPoint(centerX, eye.y, centerZ, baseYaw, basePitch, pitchOffset);
            }

            Vec3d mainPoint = getAimPoint(entity, horizontalMultipoint, verticalMultipoint);
            if (mainPoint == null) {
                return null;
            }

            if (eye.squaredDistanceTo(mainPoint) < 1.0E-6) {
                return null;
            }

            if (!mainRayHitsTargetAABB(eye, mainPoint, entity, range)) {
                return getRotationsToPoint(mainPoint.x, mainPoint.y, mainPoint.z, baseYaw, basePitch, pitchOffset);
            }

            if (canAimAtPoint(
                    eye, mainPoint, entity, range, allowThroughBlocks, allowThroughEntities, ignoreOpenFenceGates)) {
                return getRotationsToPoint(mainPoint.x, mainPoint.y, mainPoint.z, baseYaw, basePitch, pitchOffset);
            }

            List<Vec3d> backups = buildBackupPoints(entity, eye);
            Collections.sort(backups, Comparator.comparingDouble(px -> {
                double dx = px.x - eye.x;
                double dy = px.y - eye.y;
                double dz = px.z - eye.z;
                return dx * dx + dy * dy + dz * dz;
            }));

            for (Vec3d p : backups) {
                if (canAimAtPoint(
                        eye, p, entity, range, allowThroughBlocks, allowThroughEntities, ignoreOpenFenceGates)) {
                    return getRotationsToPoint(p.x, p.y, p.z, baseYaw, basePitch, pitchOffset);
                }
            }

            return null;
        } else {
            return null;
        }
    }

    public static float[] getRotationsPredicated(Entity entity, int ticks) {
        if (entity == null) {
            return null;
        }

        if (ticks == 0) {
            return getRotations(entity);
        }

        double posX = entity.x;
        double posY = entity.y;
        double posZ = entity.z;
        double n2 = posX - entity.prevX;
        double n3 = posZ - entity.prevZ;

        for (int i = 0; i < ticks; i++) {
            posX += n2;
            posZ += n3;
        }

        double n4 = posX - mc.player.x;
        double n5;
        if (entity instanceof LivingEntity) {
            n5 = posY + entity.getEyeHeight() * 0.9 - (mc.player.y + mc.player.getEyeHeight());
        } else {
            n5 = (entity.getShape().minY + entity.getShape().maxY) / 2.0 - (mc.player.y + mc.player.getEyeHeight());
        }

        double n6 = posZ - mc.player.z;
        return new float[] {
            applyVanilla(mc.player.yaw
                    + MathHelper.wrapDegrees(
                            (float) (Math.atan2(n6, n4) * (float) (180.0 / Math.PI)) - 90.0F - mc.player.yaw)),
            clampPitch(mc.player.pitch
                    + MathHelper.wrapDegrees((float)
                                    (-(Math.atan2(n5, MathHelper.sqrt(n4 * n4 + n6 * n6)) * (float) (180.0 / Math.PI)))
                            - mc.player.pitch)
                    + 3.0F)
        };
    }

    public static float[] smoothRotation(
            float baseYaw, float basePitch, float targetYaw, float targetPitch, int speed) {
        return smoothRotation(baseYaw, basePitch, targetYaw, targetPitch, speed, 0.0F);
    }

    public static float[] smoothRotation(
            float baseYaw, float basePitch, float targetYaw, float targetPitch, int speed, float randomizationPercent) {
        if (speed <= 0) {
            return new float[] {baseYaw, clampPitch(basePitch)};
        }

        if (speed >= 30) {
            return new float[] {targetYaw, clampPitch(targetPitch)};
        }

        float deltaYaw = MathHelper.wrapDegrees(targetYaw - baseYaw);
        float deltaPitch = targetPitch - basePitch;
        float magnitude = MathHelper.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
        if (magnitude < 0.001F) {
            return new float[] {targetYaw, clampPitch(targetPitch)};
        }

        float t = speed / 30.0F;
        float stepSize = t * t * 180.0F;
        float range = 0.6F * (float) (randomizationPercent / 100.0);
        float multiplier = range <= 0.001F ? 1.0F : 1.0F - range / 2.0F + (float) (Math.random() * range);
        stepSize *= multiplier;
        float proximityFactor = Math.min(1.0F, magnitude / 180.0F);
        proximityFactor = (float) Math.pow(proximityFactor, 0.7);
        float maxSlowdown = (float) (randomizationPercent / 100.0);
        float proximityMult = Math.max(0.8F, 1.0F - maxSlowdown * (1.0F - proximityFactor));
        stepSize *= proximityMult;
        float stepLength = Math.min(stepSize, magnitude);
        float scale = stepLength / magnitude;
        float stepYaw = deltaYaw * scale;
        float stepPitch = deltaPitch * scale;
        float yaw = baseYaw + stepYaw;
        float pitch = basePitch + stepPitch;
        return new float[] {yaw, clampPitch(pitch)};
    }

    public static float clampPitch(float n) {
        return MathHelper.clamp(n, -90.0F, 90.0F);
    }

    public static float[] fixRotation(float targetYaw, float targetPitch, float yaw, float pitch) {
        targetYaw = RotationHelper.unwrapYaw(targetYaw, yaw);
        float n5 = targetYaw - yaw;
        float abs = Math.abs(n5);
        float n7 = targetPitch - pitch;
        float n8 = mc.options.mouseSensitivity * 0.6F + 0.2F;
        double n9 = n8 * n8 * n8 * 1.2;
        float n10 = (float) (Math.round(n5 / n9) * n9);
        float n11 = (float) (Math.round(n7 / n9) * n9);
        targetYaw = yaw + n10;
        targetPitch = pitch + n11;
        return new float[] {targetYaw, clampPitch(targetPitch)};
    }

    public static float angle(double n, double n2) {
        return (float) (Math.atan2(n - mc.player.x, n2 - mc.player.z) * (float) (180.0 / Math.PI) * -1.0);
    }

    public static float deltaAngle(double n, double n2) {
        return (float) (Math.atan2(n, n2) * (float) (180.0 / Math.PI) * -1.0);
    }

    public static HitResult rayCast(double distance, float yaw, float pitch, boolean collisionCheck) {
        Vec3d getPositionEyes = mc.player.getEyePosition(1.0F);
        float n4 = -yaw * (float) (Math.PI / 180.0);
        float n5 = -pitch * (float) (Math.PI / 180.0);
        float cos = MathHelper.cos(n4 - (float) Math.PI);
        float sin = MathHelper.sin(n4 - (float) Math.PI);
        float n6 = -MathHelper.cos(n5);
        Vec3d vec3 = new Vec3d(sin * n6, MathHelper.sin(n5), cos * n6);
        return mc.world.rayTrace(
                getPositionEyes,
                getPositionEyes.add(vec3.x * distance, vec3.y * distance, vec3.z * distance),
                true,
                collisionCheck,
                true);
    }

    public static HitResult rayCastBlock(double distance, float yaw, float pitch) {
        Vec3d eyeVec = mc.player.getEyePosition(1.0F);
        Vec3d lookVec = Utils.getLookVec(yaw, pitch);
        Vec3d sumVec = eyeVec.add(lookVec.x * distance, lookVec.y * distance, lookVec.z * distance);
        HitResult mop = mc.world.rayTrace(eyeVec, sumVec, false, false, false);
        return mop != null && mop.type == Type.BLOCK ? mop : null;
    }

    public static HitResult rayTraceCustom(double blockReachDistance, float yaw, float pitch) {
        Vec3d vec3 = mc.player.getEyePosition(1.0F);
        Vec3d vec31 = getVectorForRotation(pitch, yaw);
        Vec3d vec32 =
                vec3.add(vec31.x * blockReachDistance, vec31.y * blockReachDistance, vec31.z * blockReachDistance);
        return mc.world.rayTrace(vec3, vec32, false, false, true);
    }

    public static HitResult rayTraceBlockIfNoEntityInFront(double reach, float yaw, float pitch) {
        if (mc.player != null && mc.world != null) {
            HitResult blockHit = rayTraceCustom(reach, yaw, pitch);
            Vec3d eyes = mc.player.getEyePosition(1.0F);
            if (blockHit != null && blockHit.type == Type.BLOCK) {
                double blockDist = blockHit.facePos.distanceTo(eyes);
                Vec3d lookVec = getVectorForRotation(pitch, yaw);
                Vec3d end = eyes.add(lookVec.x * reach, lookVec.y * reach, lookVec.z * reach);

                for (Entity entity : mc.world.getEntities(
                        mc.player,
                        mc.player
                                .getShape()
                                .expanded(lookVec.x * reach, lookVec.y * reach, lookVec.z * reach)
                                .grown(1.0, 1.0, 1.0),
                        Predicates.and(EntityFilter.NOT_SPECTATOR, Entity::hasCollision))) {
                    float border = entity.getPickRadius();
                    Box aabb = entity.getShape().grown(border, border, border);
                    HitResult entityHit = aabb.clip(eyes, end);
                    if (aabb.contains(eyes)) {
                        return null;
                    }

                    if (entityHit != null) {
                        double entityDist = eyes.distanceTo(entityHit.facePos);
                        if (entityDist < blockDist) {
                            return null;
                        }
                    }
                }

                return blockHit;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public static Vec3d getVectorForRotation(float pitch, float yaw) {
        float f = MathHelper.cos(-yaw * (float) (Math.PI / 180.0) - (float) Math.PI);
        float f1 = MathHelper.sin(-yaw * (float) (Math.PI / 180.0) - (float) Math.PI);
        float f2 = -MathHelper.cos(-pitch * (float) (Math.PI / 180.0));
        float f3 = MathHelper.sin(-pitch * (float) (Math.PI / 180.0));
        return new Vec3d(f1 * f2, f3, f * f2);
    }

    public static float applyVanilla(float yaw, boolean stop) {
        if (stop) {
            return yaw;
        }

        int scaleFactor = (int) Math.floor(serverRotations[0] / 360.0F);
        float unwrappedYaw = yaw + 360 * scaleFactor;
        if (unwrappedYaw < serverRotations[0] - 180.0F) {
            unwrappedYaw += 360.0F;
        } else if (unwrappedYaw > serverRotations[0] + 180.0F) {
            unwrappedYaw -= 360.0F;
        }

        float deltaYaw = unwrappedYaw - serverRotations[0];
        return serverRotations[0] + deltaYaw;
    }

    public static HitResult rayTrace(
            double range, float partialTicks, float[] rotations, LivingEntity ignoreCollision) {
        if (ignoreCollision != null) {
            HitResult target = rayTraceIgnore(range, partialTicks, rotations, ignoreCollision);
            if (target != null) {
                return target;
            }
        }

        Entity targetEntity = null;
        double d0 = range;
        if (rotations == null) {
            rotations = new float[] {mc.player.yaw, mc.player.pitch};
        }

        HitResult hitObject = rayTraceCustom(d0, rotations[0], rotations[1]);
        double distanceTo = d0;
        Vec3d vec3 = mc.player.getEyePosition(partialTicks);
        if (mc.interactionManager.hasExtendedReach()) {
            d0 = 6.0;
            distanceTo = 6.0;
        }

        if (hitObject != null) {
            distanceTo = hitObject.facePos.distanceTo(vec3);
        }

        Vec3d vec31 = getVectorForRotation(rotations[1], rotations[0]);
        Vec3d vec32 = vec3.add(vec31.x * d0, vec31.y * d0, vec31.z * d0);
        Vec3d vec33 = null;
        float f = 1.0F;
        List<Entity> list = mc.world.getEntities(
                mc.player,
                mc.player
                        .getShape()
                        .expanded(vec31.x * d0, vec31.y * d0, vec31.z * d0)
                        .grown(f, f, f),
                Predicates.and(EntityFilter.NOT_SPECTATOR, Entity::hasCollision));
        double d2 = distanceTo;

        for (int j = 0; j < list.size(); j++) {
            Entity entity1 = list.get(j);
            float f1 = entity1.getPickRadius();
            Box axisalignedbb = entity1.getShape().grown(f1, f1, f1);
            HitResult movingobjectposition = axisalignedbb.clip(vec3, vec32);
            if (axisalignedbb.contains(vec3)) {
                if (d2 >= 0.0) {
                    targetEntity = entity1;
                    vec33 = movingobjectposition == null ? vec3 : movingobjectposition.facePos;
                    d2 = 0.0;
                }
            } else if (movingobjectposition != null) {
                double d3 = vec3.distanceTo(movingobjectposition.facePos);
                if (d3 < d2 || d2 == 0.0) {
                    if (entity1 != mc.player.vehicle || ClientPlatform.canRiderInteract(entity1)) {
                        targetEntity = entity1;
                        vec33 = movingobjectposition.facePos;
                        d2 = d3;
                    } else if (d2 == 0.0) {
                        targetEntity = entity1;
                        vec33 = movingobjectposition.facePos;
                    }
                }
            }
        }

        return targetEntity != null && d2 < distanceTo ? new HitResult(targetEntity, vec33) : null;
    }

    public static HitResult rayTraceIgnore(
            double range, float partialTicks, float[] rotations, LivingEntity ignoreCollision) {
        HitResult blockHit = rayTraceCustom(range, rotations[0], rotations[1]);
        Vec3d start = mc.player.getEyePosition(partialTicks);
        double blockDistance = range;
        if (blockHit != null) {
            blockDistance = blockHit.facePos.distanceTo(start);
        }

        if (ignoreCollision != null) {
            if (rotations == null) {
                rotations = new float[] {mc.player.yaw, mc.player.pitch};
            }

            Vec3d lookVec = getVectorForRotation(rotations[1], rotations[0]);
            Vec3d end = start.add(lookVec.x * range, lookVec.y * range, lookVec.z * range);
            float f1 = ignoreCollision.getPickRadius();
            Box aabb = ignoreCollision.getShape().grown(f1, f1, f1);
            HitResult ignoreMOP = aabb.clip(start, end);
            if (aabb.contains(start)) {
                return new HitResult(ignoreCollision, start);
            }

            if (ignoreMOP != null) {
                double ignoreDist = start.distanceTo(ignoreMOP.facePos);
                if (ignoreDist < blockDistance) {
                    return new HitResult(ignoreCollision, ignoreMOP.facePos);
                }
            }
        }

        return blockHit != null ? blockHit : null;
    }

    public static float applyVanilla(float yaw) {
        return applyVanilla(yaw, false);
    }

    public static float[] getRotationsFromEye(Vec3d eye, double tx, double ty, double tz) {
        double dx = tx - eye.x;
        double dy = ty - eye.y;
        double dz = tz - eye.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, dist)));
        return new float[] {yaw, pitch};
    }

    public enum PLAYER_OFFSETS {
        EYE,
        CHEST,
        FOOT,
        NONE;

        public double getHeightOffset(Entity entity) {
            switch (this) {
                case EYE:
                case NONE:
                    return entity.getEyeHeight();
                case CHEST:
                    return entity.height / 2.0F;
                case FOOT:
                    return 0.0;
                default:
                    return entity.getEyeHeight();
            }
        }
    }
}
