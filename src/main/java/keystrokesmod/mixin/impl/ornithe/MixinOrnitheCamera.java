package keystrokesmod.mixin.impl.ornithe;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.combat.AimAssist;
import keystrokesmod.module.impl.render.Freelook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public abstract class MixinOrnitheCamera {
    @ModifyExpressionValue(
            method = "transformCamera",
            at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;yaw:F"),
            require = 1)
    private float freelookRotationYaw(float original) {
        if (ModuleManager.freelook != null && ModuleManager.freelook.isEnabled() && Freelook.perspectiveToggled) {
            return Freelook.cameraYaw;
        }

        AimAssist aimAssist = ModuleManager.aimAssist;
        return aimAssist != null && aimAssist.isDetachedCameraActive() ? aimAssist.getCameraYaw() : original;
    }

    @ModifyExpressionValue(
            method = "transformCamera",
            at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;lastYaw:F"),
            require = 1)
    private float freelookPrevRotationYaw(float original) {
        if (ModuleManager.freelook != null && ModuleManager.freelook.isEnabled() && Freelook.perspectiveToggled) {
            return Freelook.cameraYaw;
        }

        AimAssist aimAssist = ModuleManager.aimAssist;
        return aimAssist != null && aimAssist.isDetachedCameraActive() ? aimAssist.getPrevCameraYaw() : original;
    }

    @ModifyExpressionValue(
            method = "transformCamera",
            at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;pitch:F"),
            require = 1)
    private float freelookRotationPitch(float original) {
        if (ModuleManager.freelook != null && ModuleManager.freelook.isEnabled() && Freelook.perspectiveToggled) {
            return Freelook.cameraPitch;
        }

        AimAssist aimAssist = ModuleManager.aimAssist;
        return aimAssist != null && aimAssist.isDetachedCameraActive() ? aimAssist.getCameraPitch() : original;
    }

    @ModifyExpressionValue(
            method = "transformCamera",
            at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;lastPitch:F"),
            require = 1)
    private float freelookPrevRotationPitch(float original) {
        if (ModuleManager.freelook != null && ModuleManager.freelook.isEnabled() && Freelook.perspectiveToggled) {
            return Freelook.cameraPitch;
        }

        AimAssist aimAssist = ModuleManager.aimAssist;
        return aimAssist != null && aimAssist.isDetachedCameraActive() ? aimAssist.getPrevCameraPitch() : original;
    }

    @Mixin(Camera.class)
    public static class ActiveRenderInfo {
        @ModifyExpressionValue(
                method = "setup",
                at = @At(value = "FIELD", target = "Lnet/minecraft/entity/living/player/PlayerEntity;yaw:F"),
                require = 1)
        private static float redirectRotationYaw(float original) {
            if (ModuleManager.freelook != null && ModuleManager.freelook.isEnabled() && Freelook.perspectiveToggled) {
                return Freelook.cameraYaw;
            }

            AimAssist aimAssist = ModuleManager.aimAssist;
            return aimAssist != null && aimAssist.isDetachedCameraActive() ? aimAssist.getCameraYaw() : original;
        }

        @ModifyExpressionValue(
                method = "setup",
                at = @At(value = "FIELD", target = "Lnet/minecraft/entity/living/player/PlayerEntity;pitch:F"),
                require = 1)
        private static float redirectRotationPitch(float original) {
            if (ModuleManager.freelook != null && ModuleManager.freelook.isEnabled() && Freelook.perspectiveToggled) {
                return Freelook.cameraPitch;
            }

            AimAssist aimAssist = ModuleManager.aimAssist;
            return aimAssist != null && aimAssist.isDetachedCameraActive() ? aimAssist.getCameraPitch() : original;
        }
    }

    @Mixin(net.minecraft.client.render.world.WorldRenderer.class)
    public static class WorldRenderer {
        @Shadow
        private Minecraft minecraft;

        @Unique
        private boolean feral$isFreelookActive() {
            return ModuleManager.freelook != null && ModuleManager.freelook.isEnabled() && Freelook.perspectiveToggled;
        }

        @Unique
        private boolean feral$isDetachedAimCameraActive() {
            return ModuleManager.aimAssist != null && ModuleManager.aimAssist.isDetachedCameraActive();
        }

        @ModifyExpressionValue(
                method = {"setupRender", "getLookVector"},
                at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;pitch:F"),
                require = 1)
        private float feral$setupTerrainRotationPitch(float original, Entity entity) {
            if (entity == this.minecraft.getCamera() && this.feral$isFreelookActive()) {
                return Freelook.cameraPitch;
            } else {
                return entity == this.minecraft.getCamera() && this.feral$isDetachedAimCameraActive()
                        ? ModuleManager.aimAssist.getCameraPitch()
                        : original;
            }
        }

        @ModifyExpressionValue(
                method = {"setupRender", "getLookVector"},
                at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;yaw:F"),
                require = 1)
        private float feral$setupTerrainRotationYaw(float original, Entity entity) {
            if (entity == this.minecraft.getCamera() && this.feral$isFreelookActive()) {
                return Freelook.cameraYaw;
            } else {
                return entity == this.minecraft.getCamera() && this.feral$isDetachedAimCameraActive()
                        ? ModuleManager.aimAssist.getCameraYaw()
                        : original;
            }
        }

        @ModifyExpressionValue(
                method = "getLookVector",
                at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;lastYaw:F"),
                require = 1)
        private float feral$viewVectorPrevYaw(float original, Entity entity) {
            if (entity == this.minecraft.getCamera() && this.feral$isFreelookActive()) {
                return Freelook.cameraYaw;
            } else {
                return entity == this.minecraft.getCamera() && this.feral$isDetachedAimCameraActive()
                        ? ModuleManager.aimAssist.getCameraYaw()
                        : original;
            }
        }

        @ModifyExpressionValue(
                method = "getLookVector",
                at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;lastPitch:F"),
                require = 1)
        private float feral$viewVectorPrevPitch(float original, Entity entity) {
            if (entity == this.minecraft.getCamera() && this.feral$isFreelookActive()) {
                return Freelook.cameraPitch;
            } else {
                return entity == this.minecraft.getCamera() && this.feral$isDetachedAimCameraActive()
                        ? ModuleManager.aimAssist.getCameraPitch()
                        : original;
            }
        }
    }
}
