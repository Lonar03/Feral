package keystrokesmod.mixin.impl.render;

import keystrokesmod.module.ModuleManager;
import net.minecraft.client.entity.particle.BarrierParticle;
import net.minecraft.client.entity.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides the barrier particle while Barrier Vis shows the block itself. */
@Mixin(BarrierParticle.class)
public abstract class MixinBarrierParticle {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void feral$hideWhenBarrierVis(CallbackInfo ci) {
        if (ModuleManager.barrierVis != null && ModuleManager.barrierVis.isEnabled()) {
            ((Particle) (Object) this).remove();
            ci.cancel();
        }
    }
}
