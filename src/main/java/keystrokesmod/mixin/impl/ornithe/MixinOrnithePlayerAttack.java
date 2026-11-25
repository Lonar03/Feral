package keystrokesmod.mixin.impl.ornithe;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.combat.Reduce;
import keystrokesmod.module.impl.combat.Velocity;
import keystrokesmod.module.impl.movement.KeepSprint;
import keystrokesmod.platform.ClientPlatform;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class MixinOrnithePlayerAttack {
    private static final String ORNITHE_ATTACK = "attackTargetEntityWithCurrentItem";
    private static final String ORNITHE_SPRINT_RESET = "Lnet/minecraft/entity/player/EntityPlayer;setSprinting(Z)V";
    private static final int SLOWDOWN_INJECTOR_ORDER = 900;

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void feral$beforeAttack(Entity target, CallbackInfo ci) {
        if (!ClientPlatform.ForgeHooks.onPlayerAttackTarget((PlayerEntity) (Object) this, target)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "attack",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;addVelocity(DDD)V", shift = Shift.AFTER),
            require = 1)
    private void feral$applyCustomSlowdown(
            Entity target, CallbackInfo ci, @Share("customSlowdown") LocalBooleanRef customSlowdown) {
        if (ModuleManager.reduce != null && ModuleManager.reduce.isEnabled()) {
            customSlowdown.set(true);
            Reduce.reduce(target);
        } else if (ModuleManager.keepSprint != null && ModuleManager.keepSprint.isEnabled()) {
            customSlowdown.set(true);
            KeepSprint.keepSprint(target);
        }
    }

    @ModifyExpressionValue(
            method = "attack",
            at = @At(value = "CONSTANT", args = "doubleValue=0.6", ordinal = 0),
            require = 1,
            order = 900)
    private double feral$attackSlowdownX(double multiplier, @Share("customSlowdown") LocalBooleanRef customSlowdown) {
        if (customSlowdown.get()) {
            return 1.0;
        }

        Velocity.recordAttackSlowdown((PlayerEntity) (Object) this, multiplier, 1.0);
        return multiplier;
    }

    @ModifyExpressionValue(
            method = "attack",
            at = @At(value = "CONSTANT", args = "doubleValue=0.6", ordinal = 1),
            require = 1,
            order = 900)
    private double feral$attackSlowdownZ(double multiplier, @Share("customSlowdown") LocalBooleanRef customSlowdown) {
        if (customSlowdown.get()) {
            return 1.0;
        }

        Velocity.recordAttackSlowdown((PlayerEntity) (Object) this, 1.0, multiplier);
        return multiplier;
    }

    @WrapWithCondition(
            method = "attack",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/living/player/PlayerEntity;setSprinting(Z)V"),
            require = 1)
    private boolean feral$allowSprintReset(
            PlayerEntity player, boolean sprinting, @Share("customSlowdown") LocalBooleanRef customSlowdown) {
        return !customSlowdown.get();
    }
}
