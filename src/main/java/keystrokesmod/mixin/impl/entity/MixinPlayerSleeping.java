package keystrokesmod.mixin.impl.entity;

import keystrokesmod.module.ModuleManager;
import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Easy Revive: downed (sleeping) Zombies teammates get a standing hitbox. */
@Mixin(PlayerEntity.class)
public abstract class MixinPlayerSleeping {
    @Inject(method = "isSleeping", at = @At("HEAD"), cancellable = true)
    private void feral$isSleeping(CallbackInfoReturnable<Boolean> cir) {
        // Only other players as seen by this client (downed teammates in Zombies). Touching our own player, or the
        // integrated server's copy of it in singleplayer, desyncs the bed state.
        if (ModuleManager.easyRevive != null
                && ModuleManager.easyRevive.isEnabled()
                && (Object) this instanceof RemoteClientPlayerEntity) {
            cir.setReturnValue(false);
        }
    }
}
