package keystrokesmod.mixin.impl.entity;

import keystrokesmod.utility.BlockAnimationUtils;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class MixinEntityPlayerRender {
    @Shadow
    public abstract ItemStack getDisplayItemInHand();

    @Inject(method = "getItemUseTimer", at = @At("RETURN"), cancellable = true)
    private void feral$renderItemInUseCount(CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValueI() <= 0
                && BlockAnimationUtils.shouldForceModelBlockAnimation((PlayerEntity) (Object) this)) {
            cir.setReturnValue(1);
        }
    }

    @Inject(method = "isSwordBlocking", at = @At("RETURN"), cancellable = true)
    private void isBlocking(CallbackInfoReturnable<Boolean> cir) {
        if (!(Boolean) cir.getReturnValue()
                && BlockAnimationUtils.shouldForceBlockAnimation(
                        (PlayerEntity) (Object) this, this.getDisplayItemInHand())) {
            cir.setReturnValue(true);
        }
    }
}
