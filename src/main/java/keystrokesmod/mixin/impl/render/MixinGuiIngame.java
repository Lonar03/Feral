package keystrokesmod.mixin.impl.render;

import keystrokesmod.helper.zombies.ZombiesGameHelper;
import net.minecraft.client.gui.GameGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Feeds Hypixel Zombies round titles into {@link ZombiesGameHelper}. */
@Mixin(GameGui.class)
public abstract class MixinGuiIngame {
    @Inject(method = "setTitles", at = @At("RETURN"))
    private void feral$onDisplayTitle(
            String title, String subTitle, int fadeIn, int stay, int fadeOut, CallbackInfo ci) {
        if (title == null) {
            return;
        }
        try {
            ZombiesGameHelper.onTitle(title);
        } catch (Exception ignored) {
            // A malformed title must never break the HUD.
        }
    }
}
