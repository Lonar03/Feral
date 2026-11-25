package keystrokesmod.mixin.impl.render;

import keystrokesmod.utility.GhostHandRenderState;
import net.minecraft.client.render.platform.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlStateManager.class)
public abstract class MixinGlStateManager {
    @Inject(method = "disableCull", at = @At("HEAD"), cancellable = true)
    private static void ghostHand$keepBackFacesHidden(CallbackInfo ci) {
        if (GhostHandRenderState.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "disableBlend", at = @At("HEAD"), cancellable = true)
    private static void ghostHand$keepBlendEnabled(CallbackInfo ci) {
        if (GhostHandRenderState.isActive()) {
            GlStateManager.blendFuncSeparate(770, 771, 1, 0);
            ci.cancel();
        }
    }

    @Inject(method = "blendFunc", at = @At("HEAD"), cancellable = true)
    private static void ghostHand$blendSeparate(int source, int destination, CallbackInfo ci) {
        if (GhostHandRenderState.isActive()) {
            GlStateManager.blendFuncSeparate(source, destination, 1, 0);
            ci.cancel();
        }
    }

    @ModifyVariable(method = "blendFuncSeparate", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static int ghostHand$sourceFactor(int factor) {
        return GhostHandRenderState.isActive() ? 32771 : factor;
    }

    @ModifyVariable(method = "blendFuncSeparate", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private static int ghostHand$destinationFactor(int factor) {
        return GhostHandRenderState.isActive() && factor != 1 ? 32772 : factor;
    }

    @ModifyVariable(method = "blendFuncSeparate", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private static int ghostHand$sourceAlphaFactor(int factor) {
        return GhostHandRenderState.isActive() ? 32771 : factor;
    }

    @ModifyVariable(method = "blendFuncSeparate", at = @At("HEAD"), argsOnly = true, ordinal = 3)
    private static int ghostHand$destinationAlphaFactor(int factor) {
        return GhostHandRenderState.isActive() ? 32772 : factor;
    }

    @ModifyVariable(method = "depthMask", at = @At("HEAD"), argsOnly = true)
    private static boolean ghostHand$disableDepthWrites(boolean enabled) {
        return enabled && !GhostHandRenderState.isActive();
    }

    @Inject(method = "color4f(FFFF)V", at = @At("HEAD"))
    private static void ghostHand$colorAlpha(float red, float green, float blue, float alpha, CallbackInfo ci) {
        GhostHandRenderState.setColorAlpha(alpha);
    }
}
