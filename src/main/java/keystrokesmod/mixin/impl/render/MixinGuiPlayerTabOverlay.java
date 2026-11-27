package keystrokesmod.mixin.impl.render;

import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.other.AccountHider;
import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.client.gui.overlay.PlayerTabOverlay;
import net.minecraft.client.network.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@ForgeEvents.SideOnly(ForgeEvents.Side.CLIENT)
@Mixin(PlayerTabOverlay.class)
public class MixinGuiPlayerTabOverlay {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void nameHider$hideTabName(PlayerInfo networkPlayerInfoIn, CallbackInfoReturnable<String> cir) {
        if (ModuleManager.accountHider != null && ModuleManager.accountHider.isEnabled()) {
            cir.setReturnValue(AccountHider.getTabName(networkPlayerInfoIn, (String) cir.getReturnValue()));
        }
    }

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void murderMystery$tabIcon(PlayerInfo networkPlayerInfoIn, CallbackInfoReturnable<String> cir) {
        if (ModuleManager.murderMystery != null && networkPlayerInfoIn.getProfile() != null) {
            String icon = ModuleManager.murderMystery.getNameIcon(
                    networkPlayerInfoIn.getProfile().getId());
            if (!icon.isEmpty()) {
                cir.setReturnValue(cir.getReturnValue() + icon);
            }
        }
    }
}
