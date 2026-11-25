package keystrokesmod.mixin.impl.render;

import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.other.NameHider;
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
        if (ModuleManager.nameHider != null && ModuleManager.nameHider.isEnabled()) {
            cir.setReturnValue(NameHider.getTabName(networkPlayerInfoIn, (String) cir.getReturnValue()));
        }
    }
}
