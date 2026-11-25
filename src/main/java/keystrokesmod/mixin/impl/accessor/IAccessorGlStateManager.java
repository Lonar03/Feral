package keystrokesmod.mixin.impl.accessor;

import net.minecraft.client.render.platform.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GlStateManager.class)
public interface IAccessorGlStateManager {
    @Accessor("texture")
    static int getActiveTextureUnit() {
        throw new AssertionError();
    }
}
