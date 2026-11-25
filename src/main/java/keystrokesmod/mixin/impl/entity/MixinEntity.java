package keystrokesmod.mixin.impl.entity;

import keystrokesmod.event.ClientLookEvent;
import keystrokesmod.event.PlayerMoveEvent;
import keystrokesmod.event.StepHeightEvent;
import keystrokesmod.event.StrafeEvent;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.player.SafeWalk;
import keystrokesmod.module.impl.player.Tower;
import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class MixinEntity {
    @Unique
    private PlayerMoveEvent feral$moveEvent;

    @Unique
    private float feral$stepHeight;

    @Unique
    private boolean feral$attemptedStep;

    @Shadow
    public double velocityX;

    @Shadow
    public double velocityZ;

    @Shadow
    public float yaw;

    @ModifyVariable(method = "move", at = @At(value = "STORE", ordinal = 0), index = 19)
    private boolean injectSafeWalk(boolean flag) {
        Entity entity = (Entity) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        return entity != null && entity == mc.player && entity.onGround && SafeWalk.canSafeWalk() ? true : flag;
    }

    @Overwrite
    public void updateVelocity(float strafe, float forward, float friction) {
        StrafeEvent strafeEvent = new StrafeEvent(strafe, forward, friction, this.yaw);
        if ((Object) this == Minecraft.getInstance().player) {
            ForgeEvents.MinecraftForge.EVENT_BUS.post(strafeEvent);
        }

        strafe = strafeEvent.getStrafe();
        forward = strafeEvent.getForward();
        friction = strafeEvent.getFriction();
        if ((Object) this == Minecraft.getInstance().player) {
            friction = Tower.scaleStairsFriction(friction);
        }

        float yaw = strafeEvent.getYaw();
        float f = strafe * strafe + forward * forward;
        if (f >= 1.0E-4F) {
            f = MathHelper.sqrt(f);
            if (f < 1.0F) {
                f = 1.0F;
            }

            f = friction / f;
            strafe *= f;
            forward *= f;
            float f1 = MathHelper.sin(yaw * (float) Math.PI / 180.0F);
            float f2 = MathHelper.cos(yaw * (float) Math.PI / 180.0F);
            this.velocityX += strafe * f2 - forward * f1;
            this.velocityZ += forward * f2 + strafe * f1;
        }

        if ((Object) this == Minecraft.getInstance().player) {
            if (ModuleManager.bHop != null
                    && ModuleManager.bHop.isEnabled()
                    && ModuleManager.bHop.usesStagedMovement()) {
                ModuleManager.bHop.applyStagedMovement(strafeEvent.getForward(), strafeEvent.getStrafe(), yaw);
            }

            Tower.handleMoveFlying();
        }
    }

    @Redirect(
            method = "move",
            at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;stepHeight:F", opcode = 180, ordinal = 0))
    private float redirectStepHeight(Entity instance) {
        StepHeightEvent stepHeightEvent = new StepHeightEvent(instance, instance.stepHeight);
        ForgeEvents.MinecraftForge.EVENT_BUS.post(stepHeightEvent);
        return this.feral$stepHeight = stepHeightEvent.stepHeight;
    }

    @Redirect(
            method = "move",
            at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;stepHeight:F", opcode = 180, ordinal = 1))
    private float useStepHeight(Entity entity) {
        if (entity == Minecraft.getInstance().player
                && ModuleManager.step != null
                && ModuleManager.step.isEnabled()
                && this.feral$stepHeight > 0.6F) {
            this.feral$attemptedStep = true;
            ModuleManager.step.beginStep();
        }

        return this.feral$stepHeight;
    }

    @Inject(method = "move", at = @At("RETURN"))
    private void afterMove(double x, double y, double z, CallbackInfo ci) {
        if (this.feral$attemptedStep) {
            this.feral$attemptedStep = false;
            if (ModuleManager.step.isEnabled()) {
                ModuleManager.step.finishStep();
            }
        }
    }

    @Overwrite
    public final Vec3d getRotationVector(float pitch, float yaw) {
        ClientLookEvent event = new ClientLookEvent(yaw, pitch);
        ForgeEvents.MinecraftForge.EVENT_BUS.post(event);
        pitch = event.pitch;
        yaw = event.yaw;
        float f = MathHelper.cos(-yaw * (float) (Math.PI / 180.0) - (float) Math.PI);
        float f1 = MathHelper.sin(-yaw * (float) (Math.PI / 180.0) - (float) Math.PI);
        float f2 = -MathHelper.cos(-pitch * (float) (Math.PI / 180.0));
        float f3 = MathHelper.sin(-pitch * (float) (Math.PI / 180.0));
        return new Vec3d(f1 * f2, f3, f * f2);
    }

    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double injectPlayerMoveX(double value, double x, double y, double z) {
        this.feral$attemptedStep = false;
        this.feral$moveEvent = null;
        if ((Object) this instanceof LocalClientPlayerEntity) {
            this.feral$moveEvent = new PlayerMoveEvent(x, y, z);
            ForgeEvents.MinecraftForge.EVENT_BUS.post(this.feral$moveEvent);
        }

        return this.feral$moveEvent == null ? value : this.feral$moveEvent.x;
    }

    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double injectPlayerMoveY(double value) {
        return this.feral$moveEvent == null ? value : this.feral$moveEvent.y;
    }

    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private double injectPlayerMoveZ(double value) {
        return this.feral$moveEvent == null ? value : this.feral$moveEvent.z;
    }
}
