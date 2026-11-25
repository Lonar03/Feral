package keystrokesmod.mixin.impl.ornithe;

import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.render.Chams;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class OrnitheArgentumHooks {
    private OrnitheArgentumHooks() {}

    @Pseudo
    @Mixin(targets = "dev/rdh/argentum/impl/render/entity/instancing/EntityInstancing", remap = false)
    public static class EntityInstancing {
        @Inject(method = "beginEntity", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
        private void feral$drawChamsImmediately(CallbackInfoReturnable<Object> cir) {
            Chams chams = ModuleManager.chams;
            if (chams != null && chams.isRenderingThroughWalls()) {
                cir.setReturnValue(null);
            }
        }
    }

    @Pseudo
    @Mixin(targets = "dev/rdh/argentum/impl/render/terrain/ArgentumWorldRenderer", remap = false)
    public static class EntityVisibility {
        @Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
        private void feral$keepChamsPlayersVisible(Entity entity, CallbackInfoReturnable<Boolean> cir) {
            Chams chams = ModuleManager.chams;
            if (chams != null && chams.shouldRenderThroughWalls(entity)) {
                cir.setReturnValue(true);
            }
        }
    }
}
