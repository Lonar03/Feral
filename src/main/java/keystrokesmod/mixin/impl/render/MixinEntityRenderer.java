package keystrokesmod.mixin.impl.render;

import keystrokesmod.event.PostMouseSelectionEvent;
import keystrokesmod.helper.RotationHelper;
import keystrokesmod.mixin.interfaces.ISaturationRenderer;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.combat.AimAssist;
import keystrokesmod.module.impl.player.Freecam;
import keystrokesmod.module.impl.render.Freelook;
import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.PostChain;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@ForgeEvents.SideOnly(ForgeEvents.Side.CLIENT)
@Mixin(GameRenderer.class)
public class MixinEntityRenderer implements ISaturationRenderer {
    @Shadow
    private PostChain shaderEffect;

    @Unique
    private PostChain feral$saturationShader;

    @Inject(method = "hasShader", at = @At("HEAD"), cancellable = true)
    private void onIsShaderActive(CallbackInfoReturnable<Boolean> cir) {
        if (this.feral$saturationShader != null && GLX.usePostProcess) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getShader", at = @At("HEAD"), cancellable = true)
    private void onGetShaderGroup(CallbackInfoReturnable<PostChain> cir) {
        if (this.feral$saturationShader != null && GLX.usePostProcess && this.shaderEffect == null) {
            cir.setReturnValue(this.feral$saturationShader);
        }
    }

    @Inject(
            method = "onResolutionChanged",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/render/world/WorldRenderer;onResolutionChanged(II)V"))
    private void onUpdateShaderGroupSize(int width, int height, CallbackInfo ci) {
        if (this.feral$saturationShader != null) {
            this.feral$saturationShader.resize(width, height);
        }
    }

    @Inject(
            method = "render(FJ)V",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/render/world/WorldRenderer;renderEntityOutlines()V",
                            shift = Shift.AFTER))
    private void onRenderSaturation(float partialTicks, long nanoTime, CallbackInfo ci) {
        if (this.feral$saturationShader != null) {
            GlStateManager.matrixMode(5890);
            GlStateManager.pushMatrix();
            GlStateManager.loadIdentity();
            this.feral$saturationShader.process(partialTicks);
            GlStateManager.popMatrix();
        }
    }

    @Override
    public PostChain feral$getSaturationShader() {
        return this.feral$saturationShader;
    }

    @Override
    public void feral$setSaturationShader(PostChain shader) {
        this.feral$saturationShader = shader;
    }

    @ModifyArg(
            method = "applyHurtCam",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;rotatef(FFFF)V"),
            index = 0)
    private float injectNoHurtCam(float angle) {
        if (ModuleManager.noHurtCam != null && ModuleManager.noHurtCam.isEnabled()) {
            angle = (float) (angle / 14.0F * ModuleManager.noHurtCam.multiplier.getInput());
        }

        return angle;
    }

    @Redirect(
            method = "transformCamera",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/util/math/Vec3d;distanceTo(Lnet/minecraft/util/math/Vec3d;)D"))
    public double injectNoCameraClip(Vec3d raytrace, Vec3d original) {
        if (ModuleManager.noCameraClip != null && ModuleManager.noCameraClip.isEnabled()) {
            return ModuleManager.extendCamera != null && ModuleManager.extendCamera.isEnabled()
                    ? ModuleManager.extendCamera.distance.getInput()
                    : 4.0;
        } else {
            return raytrace.distanceTo(original);
        }
    }

    @Redirect(method = "render(FJ)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;focused:Z"))
    private boolean freelookOverrideMouse(Minecraft mc) {
        return Freelook.overrideMouse(mc);
    }

    @Redirect(
            method = "render(FJ)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/entity/living/player/LocalClientPlayerEntity;updateLocalPlayerCamera(FF)V"))
    private void freecamMouseInput(LocalClientPlayerEntity player, float yaw, float pitch) {
        if (Freecam.freeEntity != null) {
            Freecam.freeEntity.updateLocalPlayerCamera(yaw, pitch);
            Freecam.freeEntity.headYaw = Freecam.freeEntity.yaw;
            Freecam.freeEntity.lastHeadYaw = Freecam.freeEntity.yaw;
        } else {
            AimAssist aimAssist = ModuleManager.aimAssist;
            if (aimAssist == null || !aimAssist.handleMouseInput(player, yaw, pitch)) {
                player.updateLocalPlayerCamera(yaw, pitch);
            }
        }
    }

    @Redirect(
            method = "setupFog",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/entity/living/LivingEntity;hasStatusEffect(Lnet/minecraft/entity/living/effect/StatusEffect;)Z"))
    private boolean redirectSetupFog(LivingEntity entity, StatusEffect potion) {
        return entity == Minecraft.getInstance().player
                        && ModuleManager.antiDebuff != null
                        && ModuleManager.antiDebuff.canRemoveBlindness(potion)
                ? false
                : entity.hasStatusEffect(potion);
    }

    @Redirect(
            method = "setupClearColor",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/entity/living/LivingEntity;hasStatusEffect(Lnet/minecraft/entity/living/effect/StatusEffect;)Z"))
    private boolean redirectFogColor(LivingEntity entity, StatusEffect potion) {
        return entity == Minecraft.getInstance().player
                        && ModuleManager.antiDebuff != null
                        && ModuleManager.antiDebuff.canRemoveBlindness(potion)
                ? false
                : entity.hasStatusEffect(potion);
    }

    @Redirect(
            method = "setupCamera",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/entity/living/player/LocalClientPlayerEntity;hasStatusEffect(Lnet/minecraft/entity/living/effect/StatusEffect;)Z"))
    private boolean redirectSetupCameraTransform(LocalClientPlayerEntity entity, StatusEffect potion) {
        return entity == Minecraft.getInstance().player
                        && ModuleManager.antiDebuff != null
                        && ModuleManager.antiDebuff.canRemoveNausea(potion)
                ? false
                : entity.hasStatusEffect(potion);
    }

    @Inject(
            method = "renderWorld",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/render/GameRenderer;pick(F)V",
                            shift = Shift.AFTER))
    private void onRenderWorld(float partialTicks, long finishTimeNano, CallbackInfo ci) {
        ForgeEvents.MinecraftForge.EVENT_BUS.post(new PostMouseSelectionEvent());
    }

    @Inject(method = "pick", at = @At("HEAD"))
    private void onGetMouseOverHead(float partialTicks, CallbackInfo ci) {
        RotationHelper rh = RotationHelper.get();
        if (!rh.swappedForMouseOver) {
            Minecraft mc = Minecraft.getInstance();
            Entity view = mc.getCamera();
            if (view != null && rh.isActive()) {
                Float yaw = rh.getServerYaw();
                Float pitch = rh.getServerPitch();
                if (yaw != null && !yaw.isNaN() && pitch != null && !pitch.isNaN()) {
                    rh.beginSwap(view, yaw, pitch, true);
                    rh.swappedForMouseOver = true;
                }
            }
        }
    }

    @Inject(
            method = "pick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiler/Profiler;pop()V", shift = Shift.BEFORE))
    private void onGetMouseOverBeforeEndSection(float partialTicks, CallbackInfo ci) {
        if (ModuleManager.bedAura != null && ModuleManager.bedAura.shouldOverrideMouseOver()) {
            ModuleManager.bedAura.modifyMouseOverFromGetMouseOver(partialTicks);
        } else if (ModuleManager.killAura != null && ModuleManager.killAura.shouldOverrideMouseOver()) {
            ModuleManager.killAura.modifyMouseOverFromGetMouseOver(partialTicks);
        } else if (ModuleManager.ghostHand != null && ModuleManager.ghostHand.shouldOverrideMouseOver()) {
            ModuleManager.ghostHand.modifyMouseOverFromGetMouseOver(partialTicks);
        } else {
            if (ModuleManager.piercing != null && ModuleManager.piercing.shouldOverrideMouseOver()) {
                ModuleManager.piercing.modifyMouseOverFromGetMouseOver(partialTicks);
            }
        }
    }

    @Inject(method = "pick", at = @At("RETURN"))
    private void onGetMouseOverReturn(float partialTicks, CallbackInfo ci) {
        RotationHelper rh = RotationHelper.get();
        if (rh.swappedForMouseOver) {
            Entity view = Minecraft.getInstance().getCamera();
            if (view != null) {
                rh.endSwap(view);
            }

            rh.swappedForMouseOver = false;
        }
    }
}
