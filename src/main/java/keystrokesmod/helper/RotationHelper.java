package keystrokesmod.helper;

import keystrokesmod.event.ClientRotationEvent;
import keystrokesmod.event.GameTickEvent;
import keystrokesmod.event.JumpEvent;
import keystrokesmod.event.PostPlayerInputEvent;
import keystrokesmod.event.PreMotionEvent;
import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.event.StrafeEvent;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.client.Settings;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

public class RotationHelper {
    private static RotationHelper INSTANCE = new RotationHelper();
    private Float serverYaw = null;
    private Float serverPitch = null;
    private boolean setRotations = false;
    private boolean serverRelativeMovement;
    public boolean forceMovementFix = false;
    private float savedYaw;
    private float savedPitch;
    private float savedPrevYaw;
    private float savedPrevPitch;
    public boolean swappedForMouseOver;
    private boolean rotationsUpdatedThisTick = false;
    private boolean needsArmYawUpdate = false;
    private Minecraft mc = Minecraft.getInstance();

    public static float unwrapYaw(float yaw, float prevYaw) {
        return prevYaw + (((yaw - prevYaw + 180.0F) % 360.0F + 360.0F) % 360.0F - 180.0F);
    }

    public float[] getRotationsToTarget(Entity target, ClientRotationEvent e, float smoothingFactor) {
        if (target != null && this.mc.player != null) {
            float baseYaw = e.yaw != null ? e.yaw : RotationUtils.serverRotations[0];
            float basePitch = e.pitch != null ? e.pitch : RotationUtils.serverRotations[1];
            float[] rot = RotationUtils.getRotations(target, baseYaw, basePitch);
            if (rot == null) {
                return null;
            }

            float factor = Math.max(1.0F, smoothingFactor);
            float yaw = baseYaw + MathHelper.wrapDegrees(rot[0] - baseYaw) / factor;
            float pitch = basePitch + (rot[1] - basePitch) / factor;
            return new float[] {yaw, pitch};
        } else {
            return null;
        }
    }

    public float[] getRotationsToTarget(Entity target, float smoothingFactor) {
        if (target != null && this.mc.player != null) {
            float baseYaw = this.mc.player.yaw;
            float basePitch = this.mc.player.pitch;
            float[] rot = RotationUtils.getRotations(target, baseYaw, basePitch);
            if (rot == null) {
                return null;
            }

            float factor = Math.max(1.0F, smoothingFactor);
            float yaw = baseYaw + MathHelper.wrapDegrees(rot[0] - baseYaw) / factor;
            float pitch = basePitch + (rot[1] - basePitch) / factor;
            return new float[] {yaw, pitch};
        } else {
            return null;
        }
    }

    public float[] getRotationsToTarget(Entity target, ClientRotationEvent e, int speed) {
        return this.getRotationsToTarget(target, e, speed, 0.0, 0.0, 0.0F);
    }

    public float[] getRotationsToTarget(
            Entity target,
            ClientRotationEvent e,
            int speed,
            double horizontalMultipoint,
            double verticalMultipoint,
            float randomizationPercent) {
        return this.getRotationsToTarget(
                target, e, speed, horizontalMultipoint, verticalMultipoint, randomizationPercent, false, 10.0);
    }

    public float[] getRotationsToTarget(
            Entity target,
            ClientRotationEvent e,
            int speed,
            double horizontalMultipoint,
            double verticalMultipoint,
            float randomizationPercent,
            boolean useBackupPoints,
            double range) {
        return this.getRotationsToTarget(
                target,
                e,
                speed,
                horizontalMultipoint,
                verticalMultipoint,
                randomizationPercent,
                useBackupPoints,
                range,
                false,
                true);
    }

    public float[] getRotationsToTarget(
            Entity target,
            ClientRotationEvent e,
            int speed,
            double horizontalMultipoint,
            double verticalMultipoint,
            float randomizationPercent,
            boolean useBackupPoints,
            double range,
            boolean allowThroughBlocks,
            boolean allowThroughEntities) {
        return this.getRotationsToTarget(
                target,
                e,
                speed,
                horizontalMultipoint,
                verticalMultipoint,
                randomizationPercent,
                useBackupPoints,
                range,
                allowThroughBlocks,
                allowThroughEntities,
                false);
    }

    public float[] getRotationsToTarget(
            Entity target,
            ClientRotationEvent e,
            int speed,
            double horizontalMultipoint,
            double verticalMultipoint,
            float randomizationPercent,
            boolean useBackupPoints,
            double range,
            boolean allowThroughBlocks,
            boolean allowThroughEntities,
            boolean ignoreOpenFenceGates) {
        if (target != null && this.mc.player != null) {
            float baseYaw = e.yaw != null ? e.yaw : RotationUtils.serverRotations[0];
            float basePitch = e.pitch != null ? e.pitch : RotationUtils.serverRotations[1];
            float[] rot = useBackupPoints
                    ? RotationUtils.getRotationsWithBackup(
                            target,
                            horizontalMultipoint,
                            verticalMultipoint,
                            baseYaw,
                            basePitch,
                            range,
                            allowThroughBlocks,
                            allowThroughEntities,
                            ignoreOpenFenceGates,
                            0.0F)
                    : RotationUtils.getRotations(
                            target, horizontalMultipoint, verticalMultipoint, baseYaw, basePitch, 0.0F);
            return rot == null
                    ? null
                    : RotationUtils.smoothRotation(baseYaw, basePitch, rot[0], rot[1], speed, randomizationPercent);
        } else {
            return null;
        }
    }

    public float[] getRotationsToTarget(Entity target, int speed) {
        return this.getRotationsToTarget(target, speed, 0.0, 0.0, 0.0F);
    }

    public float[] getRotationsToTarget(
            Entity target,
            int speed,
            double horizontalMultipoint,
            double verticalMultipoint,
            float randomizationPercent) {
        return this.getRotationsToTarget(
                target, speed, horizontalMultipoint, verticalMultipoint, randomizationPercent, false, 10.0);
    }

    public float[] getRotationsToTarget(
            Entity target,
            int speed,
            double horizontalMultipoint,
            double verticalMultipoint,
            float randomizationPercent,
            boolean useBackupPoints,
            double range) {
        return this.getRotationsToTarget(
                target,
                speed,
                horizontalMultipoint,
                verticalMultipoint,
                randomizationPercent,
                useBackupPoints,
                range,
                false,
                true);
    }

    public float[] getRotationsToTarget(
            Entity target,
            int speed,
            double horizontalMultipoint,
            double verticalMultipoint,
            float randomizationPercent,
            boolean useBackupPoints,
            double range,
            boolean allowThroughBlocks,
            boolean allowThroughEntities) {
        return this.getRotationsToTarget(
                target,
                speed,
                horizontalMultipoint,
                verticalMultipoint,
                randomizationPercent,
                useBackupPoints,
                range,
                allowThroughBlocks,
                allowThroughEntities,
                false);
    }

    public float[] getRotationsToTarget(
            Entity target,
            int speed,
            double horizontalMultipoint,
            double verticalMultipoint,
            float randomizationPercent,
            boolean useBackupPoints,
            double range,
            boolean allowThroughBlocks,
            boolean allowThroughEntities,
            boolean ignoreOpenFenceGates) {
        if (target != null && this.mc.player != null) {
            float baseYaw = this.mc.player.yaw;
            float basePitch = this.mc.player.pitch;
            float[] rot = useBackupPoints
                    ? RotationUtils.getRotationsWithBackup(
                            target,
                            horizontalMultipoint,
                            verticalMultipoint,
                            baseYaw,
                            basePitch,
                            range,
                            allowThroughBlocks,
                            allowThroughEntities,
                            ignoreOpenFenceGates,
                            0.0F)
                    : RotationUtils.getRotations(
                            target, horizontalMultipoint, verticalMultipoint, baseYaw, basePitch, 0.0F);
            return rot == null
                    ? null
                    : RotationUtils.smoothRotation(baseYaw, basePitch, rot[0], rot[1], speed, randomizationPercent);
        } else {
            return null;
        }
    }

    public void updateServerRotations() {
        if (this.mc.player != null) {
            if (!this.rotationsUpdatedThisTick) {
                this.rotationsUpdatedThisTick = true;
                ClientRotationEvent event = new ClientRotationEvent(this.serverYaw, this.serverPitch);
                ForgeEvents.MinecraftForge.EVENT_BUS.post(event);
                this.serverYaw = event.yaw;
                this.serverPitch = event.pitch;
                this.serverRelativeMovement = event.hasServerRelativeMovement();
                if (this.serverYaw != null || this.serverPitch != null) {
                    if (!event.hasExactRotations()) {
                        if (this.serverYaw != null && Math.abs(this.serverYaw - this.mc.player.yaw) >= 1.0F) {
                            int randomFactor = (int) Settings.randomYawFactor.getInput();
                            if (randomFactor != 0) {
                                int n13 = randomFactor * 100 + Utils.randomizeInt(-30, 30);
                                this.serverYaw = this.serverYaw + Utils.randomizeInt(-n13, n13) / 100.0F;
                            }
                        }

                        float[] fixed = RotationUtils.fixRotation(
                                this.serverYaw == null ? this.mc.player.yaw : this.serverYaw,
                                this.serverPitch == null ? this.mc.player.pitch : this.serverPitch,
                                RotationUtils.serverRotations[0],
                                RotationUtils.serverRotations[1]);
                        this.serverYaw = fixed[0];
                        this.serverPitch = fixed[1];
                    }

                    if (this.serverYaw != this.mc.player.yaw && (event.yaw == null || !event.yaw.isNaN())) {
                        this.setRotations = true;
                    }

                    if (this.serverPitch != this.mc.player.pitch && (event.pitch == null || !event.pitch.isNaN())) {
                        this.setRotations = true;
                    }
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onPreUpdate(PreUpdateEvent e) {
        this.updateServerRotations();
    }

    @ForgeEvents.SubscribeEvent
    public void onPreMotion(PreMotionEvent e) {
        if (this.setRotations) {
            if (this.serverYaw != null && !this.serverYaw.isNaN()) {
                e.setYaw(this.serverYaw);
            }

            if (this.serverPitch != null && !this.serverPitch.isNaN()) {
                e.setPitch(this.serverPitch);
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onRunTick(GameTickEvent e) {
        if (this.setRotations && this.serverYaw != null && this.mc.player != null) {
            float serverYawVal = RotationUtils.serverRotations[0];
            float unwrapped = unwrapYaw(MathHelper.wrapDegrees(this.mc.player.yaw), serverYawVal);
            this.mc.player.yaw = unwrapped;
            this.mc.player.lastYaw = unwrapped;
        }

        this.serverYaw = this.serverPitch = null;
        this.setRotations = this.forceMovementFix = false;
        this.serverRelativeMovement = false;
        this.rotationsUpdatedThisTick = false;
        this.swappedForMouseOver = false;
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onRenderWorld(ForgeEvents.RenderWorldLastEvent e) {
        if (this.setRotations && this.mc.player != null) {
            this.mc.player.lastEasedYaw = this.mc.player.yaw;
            this.mc.player.easedYaw = this.mc.player.yaw;
        }
    }

    public boolean isActive() {
        return this.setRotations && (this.serverYaw != null || this.serverPitch != null);
    }

    public void beginSwap(Entity e, float yaw, float pitch, boolean swapPitch) {
        this.savedYaw = e.yaw;
        this.savedPrevYaw = e.lastYaw;
        this.savedPitch = e.pitch;
        this.savedPrevPitch = e.lastPitch;
        e.yaw = yaw;
        e.lastYaw = yaw;
        if (swapPitch) {
            e.pitch = pitch;
            e.lastPitch = pitch;
        }
    }

    public void endSwap(Entity e) {
        e.yaw = this.savedYaw;
        e.lastYaw = this.savedPrevYaw;
        e.pitch = this.savedPitch;
        e.lastPitch = this.savedPrevPitch;
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onPostInput(PostPlayerInputEvent event) {
        if (this.fixMovement() && !this.serverRelativeMovement) {
            float yaw = this.serverYaw;
            float forward = this.mc.player.input.movementForward;
            float strafe = this.mc.player.input.movementSideways;
            float inputScale = Math.max(Math.abs(forward), Math.abs(strafe));
            if (forward != 0.0F || strafe != 0.0F) {
                double angle = Math.toDegrees(getDirection(this.mc.player.yaw, forward, strafe));
                if (ModuleManager.scaffoldAdjust != null && ModuleManager.scaffoldAdjust.isEnabled()) {
                    angle = ModuleManager.scaffoldAdjust.optimizeMoveDirection(angle);
                }
                float closestForward = 0.0F;
                float closestStrafe = 0.0F;
                float closestDifference = Float.MAX_VALUE;

                for (float pfRaw = -1.0F; pfRaw <= 1.0F; pfRaw++) {
                    for (float psRaw = -1.0F; psRaw <= 1.0F; psRaw++) {
                        if (pfRaw != 0.0F || psRaw != 0.0F) {
                            float predictedForward = pfRaw * inputScale;
                            float predictedStrafe = psRaw * inputScale;
                            double predictedAngle =
                                    Math.toDegrees(getDirection(yaw, predictedForward, predictedStrafe));
                            double difference = Math.abs(MathHelper.wrapDegrees(angle - predictedAngle));
                            if (difference < closestDifference) {
                                closestDifference = (float) difference;
                                closestForward = predictedForward;
                                closestStrafe = predictedStrafe;
                            }
                        }
                    }
                }

                this.mc.player.input.movementForward = closestForward;
                this.mc.player.input.movementSideways = closestStrafe;
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onStrafe(StrafeEvent e) {
        if (this.fixMovement()) {
            e.setYaw(this.serverYaw);
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onJump(JumpEvent e) {
        if (e.entity == this.mc.player && this.fixMovement()) {
            e.setYaw(this.serverYaw);
        }
    }

    public boolean fixMovement() {
        return this.serverRelativeMovement
                || (ModuleManager.movementFix != null && ModuleManager.movementFix.isEnabled() || this.forceMovementFix)
                        && this.setRotations;
    }

    public boolean ownsServerRelativeMovement() {
        return this.serverRelativeMovement;
    }

    public void releaseServerRelativeMovement() {
        this.serverRelativeMovement = false;
    }

    public static double getDirection(float rotationYaw, double moveForward, double moveStrafing) {
        if (moveForward < 0.0) {
            rotationYaw += 180.0F;
        }

        float forward = 1.0F;
        if (moveForward < 0.0) {
            forward = -0.5F;
        } else if (moveForward > 0.0) {
            forward = 0.5F;
        }

        if (moveStrafing > 0.0) {
            rotationYaw -= 90.0F * forward;
        }

        if (moveStrafing < 0.0) {
            rotationYaw += 90.0F * forward;
        }

        return Math.toRadians(rotationYaw);
    }

    public static RotationHelper get() {
        return INSTANCE;
    }

    public void setRotations(float yaw, float pitch) {
        this.serverRelativeMovement = false;
        this.serverYaw = yaw;
        this.serverPitch = pitch;
        this.setRotations = true;
    }

    public void withRotations(float yaw, float pitch, Runnable action) {
        Float previousYaw = this.serverYaw;
        Float previousPitch = this.serverPitch;
        boolean previousSetRotations = this.setRotations;
        boolean previousServerRelativeMovement = this.serverRelativeMovement;
        this.setRotations(yaw, pitch);

        try {
            action.run();
        } finally {
            this.serverYaw = previousYaw;
            this.serverPitch = previousPitch;
            this.setRotations = previousSetRotations;
            this.serverRelativeMovement = previousServerRelativeMovement;
        }
    }

    public void setYaw(float yaw) {
        this.serverRelativeMovement = false;
        this.serverYaw = yaw;
        this.setRotations = true;
    }

    public void setPitch(float pitch) {
        this.serverRelativeMovement = false;
        this.serverPitch = pitch;
        this.setRotations = true;
    }

    public Float getServerYaw() {
        return this.serverYaw;
    }

    public Float getServerPitch() {
        return this.serverPitch;
    }
}
