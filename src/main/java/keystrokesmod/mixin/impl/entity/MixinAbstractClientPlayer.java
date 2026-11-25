// Ported from Meowtils (wtf.tatp.meowtils) via Meower, modified for Feral
package keystrokesmod.mixin.impl.entity;

import keystrokesmod.module.impl.other.AccountHider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Account Hider's custom skin: swaps the local player's skin texture and arm model, client-side only. */
@Mixin(ClientPlayerEntity.class)
public abstract class MixinAbstractClientPlayer {
    @Inject(
            method = "getSkinTextureLocation()Lnet/minecraft/resource/Identifier;",
            at = @At("HEAD"),
            cancellable = true)
    private void accountHider$getSkin(CallbackInfoReturnable<Identifier> cir) {
        if ((Object) this == Minecraft.getInstance().player) {
            Identifier skin = AccountHider.getCustomSkin();
            if (skin != null) {
                cir.setReturnValue(skin);
            }
        }
    }

    @Inject(method = "getModelType", at = @At("HEAD"), cancellable = true)
    private void accountHider$getModelType(CallbackInfoReturnable<String> cir) {
        if ((Object) this == Minecraft.getInstance().player && AccountHider.getCustomSkin() != null) {
            cir.setReturnValue(AccountHider.getCustomSkinType());
        }
    }
}
