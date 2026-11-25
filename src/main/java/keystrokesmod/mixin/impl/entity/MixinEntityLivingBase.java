package keystrokesmod.mixin.impl.entity;

import com.google.common.collect.Maps;
import java.util.Map;
import keystrokesmod.event.JumpEvent;
import keystrokesmod.event.PreMotionEvent;
import keystrokesmod.event.PrePlayerMovementInputEvent;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.client.Settings;
import keystrokesmod.platform.ClientPlatform;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.RotationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MixinEntityLivingBase extends Entity {
    private final Map<Integer, StatusEffectInstance> activePotionsMap = Maps.newHashMap();

    @Shadow
    public float headYaw;

    @Shadow
    public float bodyYaw;

    @Shadow
    public float attackAnimationProgress;

    public MixinEntityLivingBase(World worldIn) {
        super(worldIn);
    }

    @Shadow
    public StatusEffectInstance getEffectInstance(StatusEffect potionIn) {
        return this.activePotionsMap.get(potionIn.id);
    }

    @Shadow
    public boolean hasStatusEffect(StatusEffect potionIn) {
        return this.activePotionsMap.containsKey(potionIn.id);
    }

    @Inject(method = "bodyMovement", at = @At("HEAD"), cancellable = true)
    protected void injectUpdateDistance(float p_110146_1_, float p_110146_2_, CallbackInfoReturnable<Float> cir) {
        float rotationYaw = this.yaw;
        if (Settings.fullBody != null
                && Settings.rotateBody != null
                && !Settings.fullBody.isToggled()
                && Settings.rotateBody.isToggled()
                && (LivingEntity) (Object) this instanceof LocalClientPlayerEntity
                && PreMotionEvent.setRenderYaw()) {
            if (this.attackAnimationProgress > 0.0F) {
                p_110146_1_ = RotationUtils.renderYaw;
            }

            rotationYaw = RotationUtils.renderYaw;
            this.headYaw = RotationUtils.renderYaw;
        }

        float f = MathHelper.wrapDegrees(p_110146_1_ - this.bodyYaw);
        this.bodyYaw += f * 0.3F;
        float f1 = MathHelper.wrapDegrees(rotationYaw - this.bodyYaw);
        boolean flag = f1 < 90.0F || f1 >= 90.0F;
        if (f1 < -75.0F) {
            f1 = -75.0F;
        }

        if (f1 >= 75.0F) {
            f1 = 75.0F;
        }

        this.bodyYaw = rotationYaw - f1;
        if (f1 * f1 > 2500.0F) {
            this.bodyYaw += f1 * 0.2F;
        }

        if (flag) {
            p_110146_2_ *= -1.0F;
        }

        cir.setReturnValue(p_110146_2_);
    }

    @Shadow
    protected float getJumpStrength() {
        return 0.42F;
    }

    @Overwrite
    public void jump() {
        JumpEvent jumpEvent =
                new JumpEvent((LivingEntity) (Object) this, this.getJumpStrength(), this.yaw, this.isSprinting());
        ForgeEvents.MinecraftForge.EVENT_BUS.post(jumpEvent);
        if (!jumpEvent.isCanceled()) {
            this.velocityY = jumpEvent.getMotionY();
            if (this.hasStatusEffect(StatusEffect.JUMP_BOOST)) {
                this.velocityY = this.velocityY
                        + (this.getEffectInstance(StatusEffect.JUMP_BOOST).getAmplifier() + 1) * 0.1F;
            }

            if (jumpEvent.applySprint()) {
                float f = jumpEvent.getYaw() * (float) (Math.PI / 180.0);
                this.velocityX = this.velocityX - MathHelper.sin(f) * 0.2F;
                this.velocityZ = this.velocityZ + MathHelper.cos(f) * 0.2F;
            }

            this.velocityDirty = true;
            ClientPlatform.ForgeHooks.onLivingJump((LivingEntity) (Object) this);
        }
    }

    @Inject(
            method = "hasStatusEffect(Lnet/minecraft/entity/living/effect/StatusEffect;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void isPotionActive(
            StatusEffect p_isPotionActive_1_, CallbackInfoReturnable<Boolean> callbackInfoReturnable) {
        if ((Object) this == Minecraft.getInstance().player) {
            if (ModuleManager.antiDebuff != null
                    && ModuleManager.antiDebuff.isEnabled()
                    && (p_isPotionActive_1_ == StatusEffect.NAUSEA && ModuleManager.antiDebuff.removeNausea.isToggled()
                            || p_isPotionActive_1_ == StatusEffect.BLINDNESS
                                    && ModuleManager.antiDebuff.removeBlindness.isToggled())
                    && ModuleManager.antiDebuff.removeSideEffects.isToggled()) {
                callbackInfoReturnable.setReturnValue(false);
            }
        }
    }

    @Redirect(
            method = "mobTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/living/LivingEntity;moveRelative(FF)V"))
    private void onMoveEntityWithHeadingRedirect(LivingEntity self, float originalStrafing, float originalForward) {
        if (self instanceof LocalClientPlayerEntity) {
            PrePlayerMovementInputEvent event = new PrePlayerMovementInputEvent(originalForward, originalStrafing);
            ForgeEvents.MinecraftForge.EVENT_BUS.post(event);
            self.moveRelative(event.strafe, event.forward);
        } else {
            self.moveRelative(originalStrafing, originalForward);
        }
    }
}
