package keystrokesmod.helper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import keystrokesmod.mixin.impl.accessor.IAccessorS14PacketEntity;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityMoveS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTeleportS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityS2CPacket;
import net.minecraft.network.packet.s2c.play.RemoveEntitiesS2CPacket;
import net.minecraft.world.World;

public final class LeadPrediction {
    private LeadPrediction() {}

    public static final class TargetPrediction {
        private final double x;
        private final double y;
        private final double z;
        private final double positionUncertainty;
        private final double confidence;
        private final String profileName;
        private final String movementLabel;

        public TargetPrediction(
                double x,
                double y,
                double z,
                double positionUncertainty,
                double confidence,
                String profileName,
                String movementLabel) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.positionUncertainty = positionUncertainty;
            this.confidence = confidence;
            this.profileName = profileName;
            this.movementLabel = movementLabel;
        }

        public double getX() {
            return this.x;
        }

        public double getY() {
            return this.y;
        }

        public double getZ() {
            return this.z;
        }

        public double getPositionUncertainty() {
            return this.positionUncertainty;
        }

        public double getConfidence() {
            return this.confidence;
        }

        public String getProfileName() {
            return this.profileName;
        }

        public String getMovementLabel() {
            return this.movementLabel;
        }
    }

    public static final class EntityPositionTracker {
        private static final double NANOS_PER_TICK = 5.0E7;
        private final Object stateLock = new Object();
        private final Map<Integer, PositionState> stateByEntityId = new HashMap<>();
        private final Queue<PendingUpdate> pendingUpdates = new ArrayDeque<>();
        private final Queue<Integer> removedEntityIds = new ArrayDeque<>();

        public void clear() {
            synchronized (this.stateLock) {
                this.stateByEntityId.clear();
                this.pendingUpdates.clear();
                this.removedEntityIds.clear();
            }
        }

        public void onPacketReceived(Packet<?> var1, World var2) {
            double var3 = System.nanoTime() / NANOS_PER_TICK;
            synchronized (this.stateLock) {
                if (var1 instanceof RemoveEntitiesS2CPacket) {
                    for (int var9 : ((RemoveEntitiesS2CPacket) var1).getIds()) {
                        this.stateByEntityId.remove(var9);
                        this.dropQueuedUpdates(var9);
                        this.removedEntityIds.add(var9);
                    }
                } else if (var1 instanceof EntityMoveS2CPacket) {
                    EntityMoveS2CPacket var12 = (EntityMoveS2CPacket) var1;
                    int var15 = ((IAccessorS14PacketEntity) var12).getEntityId();
                    PositionState var17 = this.stateByEntityId.get(var15);
                    if (var17 == null) {
                        Entity var19 = var2 == null ? null : var2.getEntity(var15);
                        if (var19 == null) {
                            return;
                        }

                        var17 = new PositionState(
                                var19.lastKnownX / 32.0, var19.lastKnownY / 32.0, var19.lastKnownZ / 32.0);
                        this.stateByEntityId.put(var15, var17);
                    }

                    var17.x = var17.x + (double) var12.getDx() / 32.0;
                    var17.y = var17.y + (double) var12.getDy() / 32.0;
                    var17.z = var17.z + (double) var12.getDz() / 32.0;
                    this.pendingUpdates.add(new PendingUpdate(var15, var3, var17.x, var17.y, var17.z, false, false));
                } else if (var1 instanceof EntityTeleportS2CPacket) {
                    EntityTeleportS2CPacket var13 = (EntityTeleportS2CPacket) var1;
                    int var16 = var13.getId();
                    PositionState var18 =
                            new PositionState(var13.getX() / 32.0, var13.getY() / 32.0, var13.getZ() / 32.0);
                    this.stateByEntityId.put(var16, var18);
                    this.pendingUpdates.add(new PendingUpdate(var16, var3, var18.x, var18.y, var18.z, true, false));
                } else if (var1 instanceof EntityVelocityS2CPacket) {
                    EntityVelocityS2CPacket var14 = (EntityVelocityS2CPacket) var1;
                    this.pendingUpdates.add(new PendingUpdate(
                            var14.getId(),
                            var3,
                            var14.getVelocityX() / 8000.0,
                            var14.getVelocityY() / 8000.0,
                            var14.getVelocityZ() / 8000.0,
                            false,
                            true));
                }
            }
        }

        public void trackInitialPosition(int var1, double var2, double var4, double var6) {
            synchronized (this.stateLock) {
                if (!this.stateByEntityId.containsKey(var1)) {
                    this.stateByEntityId.put(var1, new PositionState(var2, var4, var6));
                    this.pendingUpdates.add(new PendingUpdate(
                            var1, System.nanoTime() / NANOS_PER_TICK, var2, var4, var6, false, false));
                }
            }
        }

        public void forceSample(int var1, double var2, double var4, double var6) {
            synchronized (this.stateLock) {
                this.stateByEntityId.put(var1, new PositionState(var2, var4, var6));
                this.pendingUpdates.add(
                        new PendingUpdate(var1, System.nanoTime() / NANOS_PER_TICK, var2, var4, var6, true, false));
            }
        }

        public void removeEntity(int var1) {
            synchronized (this.stateLock) {
                this.stateByEntityId.remove(var1);
                this.dropQueuedUpdates(var1);
            }
        }

        public int[] pollRemovedEntityIds() {
            synchronized (this.stateLock) {
                int[] var2 = new int[this.removedEntityIds.size()];
                int var3 = 0;

                Integer var4;
                while ((var4 = this.removedEntityIds.poll()) != null) {
                    var2[var3++] = var4;
                }

                return var2;
            }
        }

        public boolean hasPendingTeleport(int var1) {
            synchronized (this.stateLock) {
                for (PendingUpdate var4 : this.pendingUpdates) {
                    if (var4.entityId == var1 && var4.teleported) {
                        return true;
                    }
                }

                return false;
            }
        }

        private void dropQueuedUpdates(int var1) {
            Iterator var2 = this.pendingUpdates.iterator();

            while (var2.hasNext()) {
                if (((PendingUpdate) var2.next()).entityId == var1) {
                    var2.remove();
                }
            }
        }

        public void flushToPredictor(MotionPredictor var1) {
            synchronized (this.stateLock) {
                PendingUpdate var3;
                while ((var3 = this.pendingUpdates.poll()) != null) {
                    if (var3.isVelocityPacket) {
                        var1.applyVelocityPacket(var3.entityId, var3.time, var3.x, var3.y, var3.z);
                    } else {
                        var1.addSample(var3.entityId, var3.time, var3.x, var3.y, var3.z, var3.teleported);
                    }
                }
            }
        }

        private static final class PendingUpdate {
            public final int entityId;
            public final double time;
            public final double x;
            public final double y;
            public final double z;
            public final boolean teleported;
            public final boolean isVelocityPacket;

            PendingUpdate(
                    int entityId,
                    double time,
                    double x,
                    double y,
                    double z,
                    boolean teleported,
                    boolean isVelocityPacket) {
                this.entityId = entityId;
                this.time = time;
                this.x = x;
                this.y = y;
                this.z = z;
                this.teleported = teleported;
                this.isVelocityPacket = isVelocityPacket;
            }
        }

        private static final class PositionState {
            public double x;
            public double y;
            public double z;

            PositionState(double x, double y, double z) {
                this.x = x;
                this.y = y;
                this.z = z;
            }
        }
    }

    public static final class MotionPredictor {
        private static final int MAX_SAMPLES = 20;
        private static final double MAX_POSITION_JUMP_DISTANCE = 64.0;
        private static final double MAX_ACCELERATION = 0.12;
        private static final double MAX_UPWARD_VELOCITY = 0.82;
        private static final double MIN_DOWNWARD_VELOCITY = 1.5;
        private static final double VELOCITY_PACKET_WINDOW_TICKS = 3.5;
        private static final int STALE_VELOCITY_TICKS = 3;
        private static final double COLLISION_EPSILON = 0.001;
        private static final double VERTICAL_DRAG = 0.98;
        private static final double GRAVITY = 0.08;
        private static final String[] MOVEMENT_LABELS =
                new String[] {"velocity", "acceleration", "turn", "airborne", "stationary"};
        private final Map<Integer, TrackedEntity> trackedEntities = new HashMap<>();
        private double currentTime = Double.NaN;

        public synchronized void setCurrentTime(double var1) {
            this.currentTime = var1;
        }

        public synchronized void resetAll() {
            this.trackedEntities.clear();
        }

        public synchronized void removeEntity(int var1) {
            this.trackedEntities.remove(var1);
        }

        public synchronized boolean hasSamples(int var1) {
            TrackedEntity var2 = this.trackedEntities.get(var1);
            return var2 != null && !var2.samples.isEmpty();
        }

        public synchronized double computeSquaredDistanceToLastSample(int var1, double var2, double var4, double var6) {
            TrackedEntity var8 = this.trackedEntities.get(var1);
            Sample var9 = var8 == null ? null : var8.samples.peekLast();
            return var9 == null
                    ? Double.MAX_VALUE
                    : square(var9.posX - var2) + square(var9.posY - var4) + square(var9.posZ - var6);
        }

        public synchronized double[] computeAverageVelocity(int var1) {
            TrackedEntity var2 = this.trackedEntities.get(var1);
            if (var2 != null && var2.samples.size() >= 2) {
                Sample[] var3 = var2.samples.toArray(new Sample[var2.samples.size()]);
                return this.computeAverageVelocity(var3);
            } else {
                return new double[] {0.0, 0.0};
            }
        }

        public synchronized boolean hasRecentVelocity(int var1) {
            TrackedEntity var2 = this.trackedEntities.get(var1);
            return var2 != null && this.hasRecentVelocityPacket(var2);
        }

        public synchronized void addSample(
                int var1, double var2, double var4, double var6, double var8, boolean var10) {
            TrackedEntity var11 = this.trackedEntities.get(var1);
            if (var11 == null) {
                var11 = new TrackedEntity();
                this.trackedEntities.put(var1, var11);
            }

            boolean var12 = this.hasRecentVelocityPacket(var11);
            if (var12 && var11.hintTickCount == 0) {
                var11.samples.clear();
                this.resetCandidateWeights(var11);
            }

            Sample var13 = var11.samples.peekLast();
            double var14 = var13 == null
                    ? 0.0
                    : square(var4 - var13.posX) + square(var6 - var13.posY) + square(var8 - var13.posZ);
            if (!var10 && !(var14 > MAX_POSITION_JUMP_DISTANCE) && (var13 == null || !(var2 <= var13.time))) {
                if (var13 != null && var11.samples.size() >= 3) {
                    this.updateCandidateWeights(var11, var13, var2, var4, var6, var8);
                }
            } else {
                var11.samples.clear();
                this.resetTrackedMotion(var11);
            }

            var11.samples.addLast(new Sample(var2, var4, var6, var8));
            this.resetPathCache(var11);
            if (var12) {
                var11.hintTickCount++;
                if (var11.hintTickCount >= STALE_VELOCITY_TICKS) {
                    this.clearVelocityPacketState(var11);
                }
            } else {
                this.clearVelocityPacketState(var11);
            }

            while (var11.samples.size() > MAX_SAMPLES) {
                var11.samples.removeFirst();
            }
        }

        public synchronized void applyVelocityPacket(int var1, double var2, double var4, double var6, double var8) {
            TrackedEntity var10 = this.trackedEntities.get(var1);
            if (var10 == null) {
                var10 = new TrackedEntity();
                this.trackedEntities.put(var1, var10);
            }

            var10.hintVelocityX = var4;
            var10.hintVelocityY = var6;
            var10.hintVelocityZ = var8;
            var10.lastVelocitySampleTime = var2;
            var10.hasMovementHint = true;
            var10.hintTickCount = 0;
            this.resetPathCache(var10);

            for (int var11 = 0; var11 < var10.candidateWeights.length - 1; var11++) {
                var10.candidateWeights[var11] = Math.max(var10.candidateWeights[var11], 0.45);
            }

            var10.candidateWeights[4] = Math.max(var10.candidateWeights[4], 0.18);
        }

        public synchronized void updateMotionStateInternal(
                int var1, float var2, boolean var3, boolean var4, boolean var5, int var6, boolean var7) {
            TrackedEntity var8 = this.trackedEntities.get(var1);
            if (var8 == null) {
                var8 = new TrackedEntity();
                this.trackedEntities.put(var1, var8);
            }

            if (var8.hasInputState && var8.usingItem != var3) {
                this.resetTrackedMotion(var8);
                this.resetPathCache(var8);
            } else if (var8.hasInputState && var8.placingBlockDown != var7) {
                this.resetPathCache(var8);
            }

            var8.inputYaw = var2;
            var8.usingItem = var3;
            var8.sprinting = var4;
            var8.onGround = var5;
            var8.placingBlockDown = var7;
            var8.jumpBoostAmplifier = var6;
            var8.hasInputState = true;
        }

        public synchronized TargetPrediction predict(int var1, double var2, CollisionCheck var4) {
            TrackedEntity var5 = this.trackedEntities.get(var1);
            if (var5 == null || var5.samples.isEmpty()) {
                return null;
            } else {
                var2 = Math.max(0.0, Math.min(80.0, var2));
                MotionModel var6 = this.buildMotionModel(var5);
                this.updateAirborneWeights(var5, var6);
                double var7 = Math.min(80.0, var2 + var6.timeSinceLastSample);
                double[][] var9 = this.predictCandidatePositions(var5, var6, var7);
                int var10 = this.selectPredictionIndex(var5, var6);
                double var11 = var9[var10][0];
                double var13 = var9[var10][1];
                double var15 = var9[var10][2];
                if (var4 != null && var7 > 0.0) {
                    double[] var17 = this.resolveCollisionFreePosition(var5, var6, var10, var7, var4);
                    var11 = var17[0];
                    var13 = var17[1];
                    var15 = var17[2];
                }

                if (var6.implausible) {
                    var11 = var6.lastSample.posX;
                    var13 = var6.lastSample.posY;
                    var15 = var6.lastSample.posZ;
                }

                double var30 = Math.max(0.015, var5.candidateWeights[var10]);
                double var19 = Math.min(1.0, Math.max(0.18, (var5.samples.size() - 1) / 5.0));
                if (var6.stationary) {
                    var19 = Math.max(var19, Math.min(1.0, 0.58 + var6.timeSinceLastSample * 0.1));
                }

                double var21 = !var6.stationary && !(var6.timeSinceLastSample <= 2.0)
                        ? Math.exp(-(var6.timeSinceLastSample - 2.0) * 0.18)
                        : 1.0;
                double var23 = Math.exp(-var7 * (0.005 + Math.min(0.012, var30 * 0.009)));
                double var25 = clampValue(var19 * Math.exp(-var30 * 1.9) * var21 * var23, 0.02, 0.99);
                if (var6.implausible) {
                    var25 = Math.min(var25, 0.25);
                } else if (var6.applyingVelocityPacket || var6.recentVelocityPacket) {
                    var25 = Math.min(var25, 0.3);
                }

                double var27 = 0.08 + var30 * 1.8 + var7 * (0.018 + var30 * 0.02);
                if (var6.implausible) {
                    var27 = Math.max(var27, 0.75);
                }

                return new TargetPrediction(
                        var11,
                        var13,
                        var15,
                        var27,
                        var25,
                        MOVEMENT_LABELS[var10],
                        this.describeMotionState(var5, var6));
            }
        }

        private double[] resolveCollisionFreePosition(
                TrackedEntity var1, MotionModel var2, int var3, double var4, CollisionCheck var6) {
            if (var1.cachedCollisionPredicate != var6
                    || var1.cachedPredictionIndex != var3
                    || Double.doubleToLongBits(var1.lastCollisionCheckTime) != Double.doubleToLongBits(this.currentTime)
                    || var4 < var1.collisionCheckedTicks) {
                var1.cachedCollisionPredicate = var6;
                var1.lastCollisionCheckTime = this.currentTime;
                var1.cachedPredictionIndex = var3;
                var1.collisionCheckedTicks = 0.0;
                var1.collisionBlockedTick = Double.MAX_VALUE;
                var1.blockedPosClear = false;
                var1.xBlocked = false;
                var1.zBlocked = false;
                double[] var7 = this.predictCandidatePositions(var1, var2, 0.0)[var3];
                var1.safeX = var7[0];
                var1.safeY = var7[1];
                var1.safeZ = var7[2];
                var1.verticalVelocity = 0.0;
            }

            if (var4 >= var1.collisionBlockedTick) {
                if (var1.blockedPosClear) {
                    double[] var22 = this.predictCandidatePositions(var1, var2, var4)[var3];
                    return new double[] {var22[0], var1.safeY, var22[2]};
                } else {
                    if (var1.xBlocked || var1.zBlocked) {
                        double[] var21 = this.predictCandidatePositions(var1, var2, var4)[var3];
                        double var8 = var1.xBlocked ? var21[0] : var1.safeX;
                        double var23 = var1.zBlocked ? var21[2] : var1.safeZ;
                        if (var6.isPositionClear(var8, var21[1], var23)) {
                            return new double[] {var8, var21[1], var23};
                        }

                        if (var6.isPositionClear(var8, var1.safeY, var23)) {
                            return new double[] {var8, var1.safeY, var23};
                        }
                    }

                    return new double[] {var1.safeX, var1.safeY, var1.safeZ};
                }
            } else {
                while (var1.collisionCheckedTicks < var4) {
                    double var20 = Math.min(var4, var1.collisionCheckedTicks + 0.5);
                    double[] var9 = this.predictCandidatePositions(var1, var2, var20)[var3];
                    if (!var2.falling && !var1.placingBlockDown) {
                        var9[1] = this.resolveVerticalPosition(
                                var1, var9[0], var9[2], var20 - var1.collisionCheckedTicks, var6);
                    }

                    if (!var6.isPositionClear(var9[0], var9[1], var9[2])) {
                        double var10 = var1.collisionCheckedTicks;
                        double var12 = var20;
                        double[] var14 = new double[] {var1.safeX, var1.safeY, var1.safeZ};

                        for (int var15 = 0; var15 < 7; var15++) {
                            double var16 = (var10 + var12) * 0.5;
                            double[] var18 = this.predictCandidatePositions(var1, var2, var16)[var3];
                            if (var6.isPositionClear(var18[0], var18[1], var18[2])) {
                                var10 = var16;
                                var14 = var18;
                            } else {
                                var12 = var16;
                            }
                        }

                        var1.collisionBlockedTick = var12;
                        var1.safeX = var14[0];
                        var1.safeY = var14[1];
                        var1.safeZ = var14[2];
                        var1.blockedPosClear = var6.isPositionClear(var9[0], var14[1], var9[2]);
                        if (var1.blockedPosClear) {
                            if (var9[1] < var14[1]
                                    && var6.isPositionClear(var9[0], var14[1] + COLLISION_EPSILON, var9[2])) {
                                var1.safeY = var14[1] + COLLISION_EPSILON;
                            }

                            double[] var25 = this.predictCandidatePositions(var1, var2, var4)[var3];
                            return new double[] {var25[0], var1.safeY, var25[2]};
                        }

                        var1.xBlocked = var6.isPositionClear(var9[0], var9[1], var14[2]);
                        var1.zBlocked = var6.isPositionClear(var14[0], var9[1], var9[2]);
                        if (var1.xBlocked || var1.zBlocked) {
                            double[] var24 = this.predictCandidatePositions(var1, var2, var4)[var3];
                            double var26 = var1.xBlocked ? var24[0] : var14[0];
                            double var27 = var1.zBlocked ? var24[2] : var14[2];
                            if (var6.isPositionClear(var26, var24[1], var27)) {
                                return new double[] {var26, var24[1], var27};
                            }

                            if (var6.isPositionClear(var26, var14[1], var27)) {
                                return new double[] {var26, var14[1], var27};
                            }

                            var1.xBlocked = false;
                            var1.zBlocked = false;
                        }

                        return var14;
                    }

                    var1.collisionCheckedTicks = var20;
                    var1.safeX = var9[0];
                    var1.safeY = var9[1];
                    var1.safeZ = var9[2];
                }

                return new double[] {var1.safeX, var1.safeY, var1.safeZ};
            }
        }

        private double resolveVerticalPosition(
                TrackedEntity var1, double var2, double var4, double var6, CollisionCheck var8) {
            double var9 = var1.safeY;
            if (!var8.isPositionClear(var2, var9, var4)) {
                return var9;
            } else if (!var8.isPositionClear(var2, var9 - COLLISION_EPSILON, var4)) {
                var1.verticalVelocity = 0.0;
                return var9;
            } else {
                double var11 = Math.pow(VERTICAL_DRAG, var6);
                double var13 = (var1.verticalVelocity - GRAVITY * var6) * var11;
                double var15 = var9 + (var1.verticalVelocity + var13) * 0.5 * var6;
                var1.verticalVelocity = var13;
                if (var8.isPositionClear(var2, var15, var4)) {
                    return var15;
                } else {
                    double var17 = var9;
                    double var19 = var15;

                    for (int var21 = 0; var21 < 8; var21++) {
                        double var22 = (var17 + var19) * 0.5;
                        if (var8.isPositionClear(var2, var22, var4)) {
                            var17 = var22;
                        } else {
                            var19 = var22;
                        }
                    }

                    var1.verticalVelocity = 0.0;
                    return var17 + COLLISION_EPSILON;
                }
            }
        }

        private void resetPathCache(TrackedEntity var1) {
            var1.cachedCollisionPredicate = null;
            var1.cachedPredictionIndex = -1;
            var1.collisionCheckedTicks = 0.0;
            var1.collisionBlockedTick = Double.MAX_VALUE;
            var1.blockedPosClear = false;
            var1.xBlocked = false;
            var1.zBlocked = false;
            var1.verticalVelocity = 0.0;
        }

        private void updateCandidateWeights(
                TrackedEntity var1, Sample var2, double var3, double var5, double var7, double var9) {
            double var11 = var3 - var2.time;
            if (!(var11 <= 0.0) && !(var11 > 10.0)) {
                MotionModel var13 = this.buildMotionModel(var1);
                double[][] var14 = this.predictCandidatePositions(var1, var13, var11);

                for (int var15 = 0; var15 < var14.length; var15++) {
                    double var16 = var14[var15][0] - var5;
                    double var18 = var14[var15][1] - var7;
                    double var20 = var14[var15][2] - var9;
                    double var22 = Math.sqrt(var16 * var16 + var18 * var18 + var20 * var20);
                    var1.candidateWeights[var15] = var1.candidateWeights[var15] * 0.78 + Math.min(4.0, var22) * 0.22;
                }
            }
        }

        private MotionModel buildMotionModel(TrackedEntity var1) {
            MotionModel var2 = new MotionModel();
            var2.lastSample = var1.samples.peekLast();
            if (var2.lastSample != null && !Double.isNaN(this.currentTime)) {
                var2.timeSinceLastSample = clampValue(this.currentTime - var2.lastSample.time, 0.0, 20.0);
            }

            if (var1.samples.size() < 2) {
                var2.stationary = true;
                return var2;
            } else {
                Sample[] var3 = var1.samples.toArray(new Sample[var1.samples.size()]);
                Sample var4 = var3[var3.length - 2];
                Sample var5 = var3[var3.length - 1];
                double[] var6 = this.computeAverageVelocity(var3);
                var2.averageVelocityX = var6[0];
                var2.averageVelocityZ = var6[1];
                double var7 = clampSampleInterval(var5.time - var4.time);
                var2.sampleInterval = var7;
                var2.velocityX = (var5.posX - var4.posX) / var7;
                var2.rawVelocityY = (var5.posY - var4.posY) / var7;
                var2.velocityY = this.smoothVerticalVelocity(var5.posY - var4.posY, var7);
                var2.velocityZ = (var5.posZ - var4.posZ) / var7;
                var2.recentVelocityPacket = this.hasRecentVelocityPacket(var1);
                var2.applyingVelocityPacket = !var2.recentVelocityPacket
                        && var1.hasMovementHint
                        && (Double.isNaN(this.currentTime)
                                || Double.isNaN(var1.lastVelocitySampleTime)
                                || this.currentTime - var1.lastVelocitySampleTime <= VELOCITY_PACKET_WINDOW_TICKS);
                if (var2.applyingVelocityPacket) {
                    var2.velocityX = var2.velocityX * 0.35 + var1.hintVelocityX * 0.65;
                    var2.velocityY = var2.velocityY * 0.35 + var1.hintVelocityY * 0.65;
                    var2.rawVelocityY = var2.rawVelocityY * 0.35 + var1.hintVelocityY * 0.65;
                    var2.velocityZ = var2.velocityZ * 0.35 + var1.hintVelocityZ * 0.65;
                }

                this.clampModelVelocity(var1, var2);
                if (var3.length >= 3) {
                    Sample var9 = var3[var3.length - 3];
                    double var10 = clampSampleInterval(var4.time - var9.time);
                    double var12 = (var4.posX - var9.posX) / var10;
                    double var14 = (var4.posY - var9.posY) / var10;
                    double var16 = (var4.posZ - var9.posZ) / var10;
                    double var18 = Math.max(0.05, (var7 + var10) * 0.5);
                    var2.accelerationX =
                            clampValue((var2.velocityX - var12) / var18, -MAX_ACCELERATION, MAX_ACCELERATION);
                    var2.accelerationY =
                            clampValue((var2.velocityY - var14) / var18, -MAX_ACCELERATION, MAX_ACCELERATION);
                    var2.accelerationZ =
                            clampValue((var2.velocityZ - var16) / var18, -MAX_ACCELERATION, MAX_ACCELERATION);
                    double var20 = Math.sqrt(var12 * var12 + var16 * var16);
                    double var22 = Math.sqrt(var2.velocityX * var2.velocityX + var2.velocityZ * var2.velocityZ);
                    var2.speedDropped = var20 > 0.16 && var22 < var20 * 0.72;
                    var2.speedRetention = this.computeSpeedRetention(var3);
                    var2.turnRate = this.computeTurnRate(var3);
                }

                var2.stationary = this.isNearlyStill(var3)
                        || var2.timeSinceLastSample >= 4.0
                        || var1.hasMovementHint && !var2.applyingVelocityPacket;
                if (var2.stationary) {
                    var2.velocityX = 0.0;
                    var2.velocityY = 0.0;
                    var2.rawVelocityY = 0.0;
                    var2.velocityZ = 0.0;
                    var2.accelerationX = 0.0;
                    var2.accelerationY = 0.0;
                    var2.accelerationZ = 0.0;
                    var2.turnRate = 0.0;
                }

                var2.falling = !var2.stationary && !var1.onGround && this.hasVerticalMotion(var3, var2);
                var2.straightLineSprint =
                        var1.sprinting && this.isMovingStraight(var3) && (!var2.falling || var2.speedRetention > 0.85);
                if (var2.straightLineSprint) {
                    var2.velocityX = var2.averageVelocityX;
                    var2.velocityZ = var2.averageVelocityZ;
                    var2.speedRetention = 1.0;
                } else if (!var2.falling) {
                    var2.speedRetention = 1.0;
                }

                return var2;
            }
        }

        private boolean isMovingStraight(Sample[] var1) {
            if (var1.length < 4) {
                return false;
            } else {
                int var2 = Math.max(1, var1.length - 8);
                double var3 = var1[var1.length - 1].posX - var1[var2 - 1].posX;
                double var5 = var1[var1.length - 1].posZ - var1[var2 - 1].posZ;
                double var7 = Math.sqrt(var3 * var3 + var5 * var5);
                if (var7 < 0.15) {
                    return false;
                } else {
                    double var9 = var3 / var7;
                    double var11 = var5 / var7;

                    for (int var13 = var2; var13 < var1.length; var13++) {
                        Sample var14 = var1[var13 - 1];
                        Sample var15 = var1[var13];
                        double var16 = var15.posX - var14.posX;
                        double var18 = var15.posZ - var14.posZ;
                        double var20 = Math.sqrt(var16 * var16 + var18 * var18);
                        if (var20 < 0.04) {
                            return false;
                        }

                        double var22 = (var16 * var9 + var18 * var11) / var20;
                        if (var22 < 0.965) {
                            return false;
                        }
                    }

                    return true;
                }
            }
        }

        private double[] computeAverageVelocity(Sample[] var1) {
            int var2 = Math.max(1, var1.length - 8);
            double var3 = 0.0;
            double var5 = 0.0;
            double var7 = 0.0;

            for (int var9 = var2; var9 < var1.length; var9++) {
                Sample var10 = var1[var9 - 1];
                Sample var11 = var1[var9];
                double var12 = clampSampleInterval(var11.time - var10.time);
                var3 += var11.posX - var10.posX;
                var5 += var11.posZ - var10.posZ;
                var7 += var12;
            }

            if (var7 <= 0.0) {
                return new double[] {0.0, 0.0};
            } else {
                double var15 = var3 / var7;
                double var16 = var5 / var7;
                double var13 = Math.sqrt(var15 * var15 + var16 * var16);
                if (var13 > 0.65) {
                    var15 *= 0.65 / var13;
                    var16 *= 0.65 / var13;
                }

                return new double[] {var15, var16};
            }
        }

        private void updateAirborneWeights(TrackedEntity var1, MotionModel var2) {
            if (!var1.fallingInitialized) {
                var1.previousFalling = var2.falling;
                var1.fallingInitialized = true;
            } else if (var1.previousFalling != var2.falling) {
                var1.previousFalling = var2.falling;
                if (var2.falling) {
                    var1.candidateWeights[3] = Math.min(var1.candidateWeights[3], 0.12);
                } else {
                    var1.candidateWeights[4] = Math.min(var1.candidateWeights[4], 0.08);
                }
            }
        }

        private void clampModelVelocity(TrackedEntity var1, MotionModel var2) {
            double var3 = Math.sqrt(var2.velocityX * var2.velocityX + var2.velocityZ * var2.velocityZ);
            if (var3 > 0.65) {
                var2.implausible = true;
            }

            double var5 = var1.hasInputState && var1.usingItem ? 0.11 : 0.65;
            if (var3 > var5 && var3 > 1.0E-6) {
                var2.velocityX *= var5 / var3;
                var2.velocityZ *= var5 / var3;
            }

            if (var2.velocityY > MAX_UPWARD_VELOCITY
                    || var2.rawVelocityY > MAX_UPWARD_VELOCITY
                    || var2.velocityY < -MIN_DOWNWARD_VELOCITY
                    || var2.rawVelocityY < -MIN_DOWNWARD_VELOCITY) {
                var2.implausible = true;
            }

            var2.velocityY = clampValue(var2.velocityY, -MIN_DOWNWARD_VELOCITY, MAX_UPWARD_VELOCITY);
            var2.rawVelocityY = clampValue(var2.rawVelocityY, -MIN_DOWNWARD_VELOCITY, MAX_UPWARD_VELOCITY);
        }

        private String describeMotionState(TrackedEntity var1, MotionModel var2) {
            if (var2.stationary) {
                return var1.usingItem ? "blocking_still" : "still";
            } else {
                double var3 = Math.toRadians(var1.inputYaw);
                double var5 = var2.velocityX * -Math.sin(var3) + var2.velocityZ * Math.cos(var3);
                double var7 = var2.velocityX * Math.cos(var3) + var2.velocityZ * Math.sin(var3);
                String var9;
                if (Math.abs(var5) > Math.abs(var7) * 1.35) {
                    var9 = var5 >= 0.0 ? "forward" : "backward";
                } else if (Math.abs(var7) > Math.abs(var5) * 1.35) {
                    var9 = var7 >= 0.0 ? "right" : "left";
                } else {
                    var9 = (var5 >= 0.0 ? "forward_" : "backward_") + (var7 >= 0.0 ? "right" : "left");
                }

                String var10 =
                        var1.usingItem ? "blocking_" + var9 : (var1.sprinting ? "sprinting_" : "walking_") + var9;
                if (var2.falling) {
                    var10 = var10
                            + (var1.jumpBoostAmplifier >= 0
                                    ? "_jump_boost_" + (var1.jumpBoostAmplifier + 1)
                                    : "_airborne");
                }

                return var10;
            }
        }

        private double computeTurnRate(Sample[] var1) {
            if (var1.length < 4) {
                return 0.0;
            } else {
                int var2 = Math.max(2, var1.length - 3);
                double var3 = 0.0;
                int var5 = 0;
                int var6 = 0;

                for (int var7 = var2; var7 < var1.length; var7++) {
                    Sample var8 = var1[var7 - 2];
                    Sample var9 = var1[var7 - 1];
                    Sample var10 = var1[var7];
                    double var11 = clampSampleInterval(var9.time - var8.time);
                    double var13 = clampSampleInterval(var10.time - var9.time);
                    double var15 = (var9.posX - var8.posX) / var11;
                    double var17 = (var9.posZ - var8.posZ) / var11;
                    double var19 = (var10.posX - var9.posX) / var13;
                    double var21 = (var10.posZ - var9.posZ) / var13;
                    if (!(Math.sqrt(var15 * var15 + var17 * var17) < 0.05)
                            && !(Math.sqrt(var19 * var19 + var21 * var21) < 0.05)) {
                        double var23 = wrapAngleToPi(Math.atan2(var21, var19) - Math.atan2(var17, var15))
                                / Math.max(0.05, (var11 + var13) * 0.5);
                        if (!(Math.abs(var23) < 0.015)) {
                            int var25 = var23 < 0.0 ? -1 : 1;
                            if (var6 != 0 && var6 != var25) {
                                return 0.0;
                            }

                            var6 = var25;
                            var3 += var23;
                            var5++;
                        }
                    }
                }

                return var5 < 2 ? 0.0 : clampValue(var3 / var5, -0.25, 0.25);
            }
        }

        private boolean isNearlyStill(Sample[] var1) {
            if (var1.length < 4) {
                return false;
            } else {
                int var2 = Math.max(1, var1.length - 4);

                for (int var3 = var2; var3 < var1.length; var3++) {
                    Sample var4 = var1[var3 - 1];
                    Sample var5 = var1[var3];
                    double var6 = clampSampleInterval(var5.time - var4.time);
                    double var8 = Math.sqrt(square(var5.posX - var4.posX) + square(var5.posZ - var4.posZ)) / var6;
                    double var10 = Math.abs(var5.posY - var4.posY) / var6;
                    if (var8 > 0.018 || var10 > 0.025) {
                        return false;
                    }
                }

                return true;
            }
        }

        private boolean hasVerticalMotion(Sample[] var1, MotionModel var2) {
            if (Math.abs(var2.rawVelocityY) > 0.035) {
                return true;
            } else if (var1.length < 3) {
                return false;
            } else {
                Sample var3 = var1[var1.length - 3];
                Sample var4 = var1[var1.length - 2];
                Sample var5 = var1[var1.length - 1];
                double var6 = (var4.posY - var3.posY) / clampSampleInterval(var4.time - var3.time);
                double var8 = (var5.posY - var4.posY) / clampSampleInterval(var5.time - var4.time);
                return Math.abs(var6) > 0.025 && Math.abs(var8) > 0.015;
            }
        }

        private double computeSpeedRetention(Sample[] var1) {
            if (var1.length < 4) {
                return 1.0;
            } else {
                int var2 = Math.max(2, var1.length - 3);
                double var3 = 0.0;
                double var5 = Double.MAX_VALUE;
                double var7 = -Double.MAX_VALUE;
                int var9 = 0;
                int var10 = var2;

                while (var10 < var1.length) {
                    Sample var11 = var1[var10 - 2];
                    Sample var12 = var1[var10 - 1];
                    Sample var13 = var1[var10];
                    double var14 = clampSampleInterval(var12.time - var11.time);
                    double var16 = clampSampleInterval(var13.time - var12.time);
                    double var18 = (var12.posX - var11.posX) / var14;
                    double var20 = (var12.posZ - var11.posZ) / var14;
                    double var22 = (var13.posX - var12.posX) / var16;
                    double var24 = (var13.posZ - var12.posZ) / var16;
                    double var26 = Math.sqrt(var18 * var18 + var20 * var20);
                    double var28 = Math.sqrt(var22 * var22 + var24 * var24);
                    if (!(var26 < 0.05) && !(var28 < 0.05)) {
                        double var30 = (var18 * var22 + var20 * var24) / (var26 * var28);
                        if (!(var30 < 0.985)) {
                            double var32 = var28 / var26;
                            if (!(var32 < 0.72) && !(var32 > 0.975)) {
                                var5 = Math.min(var5, var32);
                                var7 = Math.max(var7, var32);
                                var3 += var32;
                                var9++;
                                var10++;
                                continue;
                            }

                            return 1.0;
                        }

                        return 1.0;
                    }

                    return 1.0;
                }

                return var9 >= 2 && var7 - var5 <= 0.06 ? clampValue(var3 / var9, 0.72, 0.975) : 1.0;
            }
        }

        private double[][] predictCandidatePositions(TrackedEntity var1, MotionModel var2, double var3) {
            double[][] var5 = new double[5][3];
            Sample var6 = var2.lastSample;
            if (var6 == null) {
                return var5;
            } else {
                var5[0][0] = var6.posX + var2.velocityX * var3;
                var5[0][1] = var6.posY;
                var5[0][2] = var6.posZ + var2.velocityZ * var3;
                double var7 = Math.min(4.0, var3);
                double var9 = var3 - var7;
                double var11 = var2.velocityX + var2.accelerationX * var7;
                double var13 = var2.velocityZ + var2.accelerationZ * var7;
                double var15 = Math.sqrt(var11 * var11 + var13 * var13);
                if (var15 > 0.45) {
                    var11 *= 0.45 / var15;
                    var13 *= 0.45 / var15;
                }

                var5[1][0] = var6.posX + var2.velocityX * var7 + 0.5 * var2.accelerationX * var7 * var7 + var11 * var9;
                var5[1][1] = var6.posY;
                var5[1][2] = var6.posZ + var2.velocityZ * var7 + 0.5 * var2.accelerationZ * var7 * var7 + var13 * var9;
                double var17 = Math.sqrt(var2.velocityX * var2.velocityX + var2.velocityZ * var2.velocityZ);
                if (Math.abs(var2.turnRate) > 1.0E-4 && var17 > 1.0E-4) {
                    double var19 = Math.atan2(var2.velocityZ, var2.velocityX);
                    double var21 = var17 / var2.turnRate;
                    var5[2][0] = var6.posX + var21 * (Math.sin(var19 + var2.turnRate * var3) - Math.sin(var19));
                    var5[2][2] = var6.posZ - var21 * (Math.cos(var19 + var2.turnRate * var3) - Math.cos(var19));
                } else {
                    var5[2][0] = var5[0][0];
                    var5[2][2] = var5[0][2];
                }

                var5[2][1] = var5[0][1];
                this.limitLateralOffset(var5[1], var5[0], var3);
                this.limitLateralOffset(var5[2], var5[0], var3);
                double var35 = var6.posX;
                double var36 = var6.posY;
                double var23 = var6.posZ;
                double var25 = var2.velocityX;
                double var27 = var2.velocityY;
                double var29 = var2.velocityZ;
                int var31 = (int) Math.floor(var3);

                for (int var32 = 0; var32 < var31; var32++) {
                    var35 += var25;
                    var23 += var29;
                    var25 *= var2.speedRetention;
                    var29 *= var2.speedRetention;
                    if (var2.falling) {
                        var36 += var27;
                        var27 = (var27 - GRAVITY) * VERTICAL_DRAG;
                    }
                }

                double var37 = var3 - var31;
                var5[3][0] = var35 + var25 * var37;
                var5[3][1] = var2.falling ? var36 + var27 * var37 : var6.posY;
                var5[3][2] = var23 + var29 * var37;
                var5[4][0] = var6.posX;
                var5[4][1] = var6.posY;
                var5[4][2] = var6.posZ;

                for (int var34 = 0; var34 < var5.length; var34++) {
                    this.limitDistanceFromLastSample(var5[var34], var6, var3);
                }

                return var5;
            }
        }

        private void limitDistanceFromLastSample(double[] var1, Sample var2, double var3) {
            double var5 = var1[0] - var2.posX;
            double var7 = var1[2] - var2.posZ;
            double var9 = Math.sqrt(var5 * var5 + var7 * var7);
            double var11 = 0.65 * Math.max(0.0, var3);
            if (var9 > var11 && var9 > 1.0E-6) {
                var1[0] = var2.posX + var5 * var11 / var9;
                var1[2] = var2.posZ + var7 * var11 / var9;
            }
        }

        private void limitLateralOffset(double[] var1, double[] var2, double var3) {
            double var5 = var1[0] - var2[0];
            double var7 = var1[2] - var2[2];
            double var9 = Math.sqrt(var5 * var5 + var7 * var7);
            double var11 = 0.6 + Math.min(1.2, var3 * 0.05);
            if (var9 > var11) {
                var1[0] = var2[0] + var5 * var11 / var9;
                var1[2] = var2[2] + var7 * var11 / var9;
            }
        }

        private int selectPredictionIndex(TrackedEntity var1, MotionModel var2) {
            if (var2.stationary) {
                return 4;
            } else if (var2.straightLineSprint) {
                return 3;
            } else if (var2.falling) {
                return 3;
            } else if (!var1.usingItem && !var2.speedDropped) {
                int var3 = 0;
                double var4 = Double.MAX_VALUE;

                for (int var6 = 0; var6 < var1.candidateWeights.length - 1; var6++) {
                    double var7 = var1.candidateWeights[var6];
                    if (var6 == 1
                            && Math.sqrt(var2.accelerationX * var2.accelerationX
                                            + var2.accelerationZ * var2.accelerationZ)
                                    > 0.025) {
                        var7 *= 0.82;
                    }

                    if (var6 == 2 && Math.abs(var2.turnRate) > 0.035) {
                        var7 *= 0.72;
                    }

                    if (var6 == 4) {
                        var7 *= 1.8;
                    }

                    if (var7 < var4) {
                        var4 = var7;
                        var3 = var6;
                    }
                }

                return var3;
            } else {
                return 0;
            }
        }

        private void resetTrackedMotion(TrackedEntity var1) {
            this.resetCandidateWeights(var1);
            this.clearVelocityPacketState(var1);
        }

        private void resetCandidateWeights(TrackedEntity var1) {
            for (int var2 = 0; var2 < var1.candidateWeights.length; var2++) {
                var1.candidateWeights[var2] = var2 == 4 ? 0.12 : 0.35;
            }

            var1.fallingInitialized = false;
        }

        private void clearVelocityPacketState(TrackedEntity var1) {
            var1.hasMovementHint = false;
            var1.lastVelocitySampleTime = Double.NaN;
            var1.hintTickCount = STALE_VELOCITY_TICKS;
        }

        private boolean hasRecentVelocityPacket(TrackedEntity var1) {
            return var1.hasMovementHint
                    && var1.hintTickCount < STALE_VELOCITY_TICKS
                    && (Double.isNaN(this.currentTime)
                            || Double.isNaN(var1.lastVelocitySampleTime)
                            || this.currentTime - var1.lastVelocitySampleTime <= VELOCITY_PACKET_WINDOW_TICKS);
        }

        private double smoothVerticalVelocity(double var1, double var3) {
            int var5 = Math.max(1, Math.min(10, (int) Math.round(var3)));
            double var6 = 0.0;
            double var8 = 0.0;
            double var10 = 1.0;
            double var12 = 0.0;

            for (int var14 = 0; var14 < var5; var14++) {
                var6 += var10;
                var8 += var12;
                var10 *= VERTICAL_DRAG;
                var12 = (var12 - GRAVITY) * VERTICAL_DRAG;
            }

            double var19 = (var1 - var8) / var6;
            double var16 = var19;

            for (int var18 = 0; var18 < var5; var18++) {
                var16 = (var16 - GRAVITY) * VERTICAL_DRAG;
            }

            return var16;
        }

        private static double clampSampleInterval(double var0) {
            return Math.max(1.0, Math.min(10.0, Math.rint(Math.max(0.0, var0))));
        }

        private static double square(double var0) {
            return var0 * var0;
        }

        private static double clampValue(double var0, double var2, double var4) {
            return Math.max(var2, Math.min(var4, var0));
        }

        private static double wrapAngleToPi(double var0) {
            while (var0 <= -Math.PI) {
                var0 += Math.PI * 2;
            }

            while (var0 > Math.PI) {
                var0 -= Math.PI * 2;
            }

            return var0;
        }

        public interface CollisionCheck {
            boolean isPositionClear(double var1, double var3, double var5);
        }

        private static final class TrackedEntity {
            public final Deque<Sample> samples = new ArrayDeque<>();
            public final double[] candidateWeights;
            public double hintVelocityX;
            public double hintVelocityY;
            public double hintVelocityZ;
            public double lastVelocitySampleTime;
            public boolean hasMovementHint;
            public int hintTickCount;
            public float inputYaw;
            public boolean usingItem;
            public boolean sprinting;
            public boolean onGround;
            public boolean placingBlockDown;
            public boolean hasInputState;
            public int jumpBoostAmplifier;
            public boolean previousFalling;
            public boolean fallingInitialized;
            public CollisionCheck cachedCollisionPredicate;
            public double lastCollisionCheckTime;
            public int cachedPredictionIndex;
            public double collisionCheckedTicks;
            public double collisionBlockedTick;
            public double safeX;
            public double safeY;
            public double safeZ;
            public double verticalVelocity;
            public boolean blockedPosClear;
            public boolean xBlocked;
            public boolean zBlocked;

            TrackedEntity() {
                double[] var10001 = new double[] {0.35, 0.0, 0.0, 0.0, 0.0};
                var10001[1] = 0.35;
                var10001[2] = 0.35;
                var10001[3] = 0.35;
                var10001[4] = 0.12;
                this.candidateWeights = var10001;
                this.lastVelocitySampleTime = Double.NaN;
                this.jumpBoostAmplifier = -1;
                this.lastCollisionCheckTime = Double.NaN;
                this.cachedPredictionIndex = -1;
                this.collisionBlockedTick = Double.MAX_VALUE;
            }
        }

        private static final class MotionModel {
            public Sample lastSample;
            public double velocityX;
            public double velocityY;
            public double rawVelocityY;
            public double velocityZ;
            public double accelerationX;
            public double accelerationY;
            public double accelerationZ;
            public double turnRate;
            public double sampleInterval;
            public double timeSinceLastSample;
            public boolean falling;
            public boolean stationary;
            public boolean speedDropped;
            public boolean implausible;
            public boolean applyingVelocityPacket;
            public boolean recentVelocityPacket;
            public boolean straightLineSprint;
            public double speedRetention = 1.0;
            public double averageVelocityX;
            public double averageVelocityZ;

            MotionModel() {}
        }

        private static final class Sample {
            public final double time;
            public final double posX;
            public final double posY;
            public final double posZ;

            Sample(double time, double posX, double posY, double posZ) {
                this.time = time;
                this.posX = posX;
                this.posY = posY;
                this.posZ = posZ;
            }
        }
    }
}
