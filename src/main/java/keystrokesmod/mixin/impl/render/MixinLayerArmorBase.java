package keystrokesmod.mixin.impl.render;

import keystrokesmod.utility.GhostHandRenderState;
import net.minecraft.client.render.entity.layer.AbstractArmorLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractArmorLayer.class)
public abstract class MixinLayerArmorBase {
    @Shadow
    private boolean hasColor;

    @Redirect(
            method = "renderArmor",
            at =
                    @At(
                            value = "FIELD",
                            target = "Lnet/minecraft/client/render/entity/layer/AbstractArmorLayer;hasColor:Z"))
    private boolean ghostHand$skipGlint(AbstractArmorLayer<?> layer) {
        return this.hasColor || GhostHandRenderState.isActive();
    }
}
