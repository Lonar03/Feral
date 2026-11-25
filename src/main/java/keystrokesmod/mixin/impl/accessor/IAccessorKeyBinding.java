package keystrokesmod.mixin.impl.accessor;

import net.minecraft.client.options.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyBinding.class)
public interface IAccessorKeyBinding {
    // Press a binding directly. KeyBinding.set(keyCode, ...) goes through the key code, which hits whichever binding
    // registered last on that code when the binding is unbound (code 0).
    @Accessor("pressed")
    void setPressed(boolean pressed);

    /** Pending clicks (MCP: pressTime). */
    @Accessor("clickCount")
    void setPressTime(int pressTime);
}
