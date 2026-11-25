package keystrokesmod.mixin.impl.client;

import keystrokesmod.helper.KeyInputTracker;
import net.minecraft.client.options.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Feeds the Keystrokes module every key binding press, including ones made by modules (MCP: onTick). */
@Mixin(KeyBinding.class)
public class MixinKeyBinding {
    @Inject(method = "click", at = @At("HEAD"))
    private static void feral$trackPress(int keyCode, CallbackInfo ci) {
        KeyInputTracker.onKeyBindingTick(keyCode);
    }
}
