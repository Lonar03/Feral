package keystrokesmod.mixin.impl.accessor;

import net.minecraft.client.render.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityRenderDispatcher.class)
public interface IAccessorRenderManager {
    @Accessor("offsetX")
    double getRenderPosX();

    @Accessor("offsetY")
    double getRenderPosY();

    @Accessor("offsetZ")
    double getRenderPosZ();
}
