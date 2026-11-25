package keystrokesmod.mixin.impl.render;

import keystrokesmod.module.impl.render.ChestESP;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.client.render.block.entity.ChestRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChestRenderer.class)
public class MixinTileEntityChestRenderer {
    @Inject(method = "render(Lnet/minecraft/block/entity/ChestBlockEntity;DDDFI)V", at = @At("HEAD"))
    private void feral$chestChamsPre(
            ChestBlockEntity te, double x, double y, double z, float partialTicks, int destroyStage, CallbackInfo ci) {
        ChestESP.onRenderChestPre(te);
    }

    @Inject(method = "render(Lnet/minecraft/block/entity/ChestBlockEntity;DDDFI)V", at = @At("RETURN"))
    private void feral$chestChamsPost(
            ChestBlockEntity te, double x, double y, double z, float partialTicks, int destroyStage, CallbackInfo ci) {
        ChestESP.onRenderChestPost();
    }
}
