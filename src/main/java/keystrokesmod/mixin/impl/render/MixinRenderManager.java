package keystrokesmod.mixin.impl.render;

import keystrokesmod.event.PreMotionEvent;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.combat.AimAssist;
import keystrokesmod.module.impl.render.Freelook;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.RotationUtils;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@ForgeEvents.SideOnly(ForgeEvents.Side.CLIENT)
@Mixin(EntityRenderDispatcher.class)
public class MixinRenderManager {
    @Shadow
    private float cameraPitch;

    @Shadow
    private float cameraYaw;

    @Unique
    private float cachedPrevRotationPitch;

    @Unique
    private float cachedRotationPitch;

    @Inject(method = "render(Lnet/minecraft/entity/Entity;FZ)Z", at = @At("HEAD"))
    public void renderEntityStaticPre(
            Entity entity, float n, boolean b, CallbackInfoReturnable<Boolean> callbackInfoReturnable) {
        if (entity instanceof LocalClientPlayerEntity && PreMotionEvent.setRenderYaw()) {
            LocalClientPlayerEntity player = (LocalClientPlayerEntity) entity;
            this.cachedRotationPitch = player.pitch;
            this.cachedPrevRotationPitch = player.lastPitch;
            player.lastPitch = RotationUtils.prevRenderPitch;
            player.pitch = RotationUtils.renderPitch;
        }
    }

    @Inject(method = "render(Lnet/minecraft/entity/Entity;FZ)Z", at = @At("RETURN"))
    public void renderEntityStaticPost(
            Entity entity, float n, boolean b, CallbackInfoReturnable<Boolean> callbackInfoReturnable) {
        if (entity instanceof LocalClientPlayerEntity && PreMotionEvent.setRenderYaw()) {
            LocalClientPlayerEntity player = (LocalClientPlayerEntity) entity;
            player.lastPitch = this.cachedPrevRotationPitch;
            player.pitch = this.cachedRotationPitch;
        }
    }

    @Redirect(
            method = "prepare",
            at =
                    @At(
                            value = "FIELD",
                            target = "Lnet/minecraft/client/render/entity/EntityRenderDispatcher;cameraPitch:F",
                            opcode = 181))
    private void freelookRedirectPlayerViewX(EntityRenderDispatcher rm, float value) {
        if (ModuleManager.freelook != null && ModuleManager.freelook.isEnabled() && Freelook.perspectiveToggled) {
            this.cameraPitch = Freelook.cameraPitch;
        } else {
            AimAssist aimAssist = ModuleManager.aimAssist;
            this.cameraPitch =
                    aimAssist != null && aimAssist.isDetachedCameraActive() ? aimAssist.getCameraPitch() : value;
        }
    }

    @Redirect(
            method = "prepare",
            at =
                    @At(
                            value = "FIELD",
                            target = "Lnet/minecraft/client/render/entity/EntityRenderDispatcher;cameraYaw:F",
                            opcode = 181))
    private void freelookRedirectPlayerViewY(EntityRenderDispatcher rm, float value) {
        if (ModuleManager.freelook != null && ModuleManager.freelook.isEnabled() && Freelook.perspectiveToggled) {
            this.cameraYaw = Freelook.cameraYaw;
        } else {
            AimAssist aimAssist = ModuleManager.aimAssist;
            this.cameraYaw = aimAssist != null && aimAssist.isDetachedCameraActive() ? aimAssist.getCameraYaw() : value;
        }
    }
}
