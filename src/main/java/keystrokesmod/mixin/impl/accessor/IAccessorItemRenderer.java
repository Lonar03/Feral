package keystrokesmod.mixin.impl.accessor;

import net.minecraft.client.render.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemInHandRenderer.class)
public interface IAccessorItemRenderer {
    @Accessor("handHeight")
    float getEquippedProgress();
}
