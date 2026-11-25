package keystrokesmod.mixin.impl.render;

import keystrokesmod.module.impl.render.ChestESP;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.client.render.block.entity.EnderChestRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnderChestRenderer.class)
public class MixinTileEntityEnderChestRenderer {
    @Inject(method = "render(Lnet/minecraft/block/entity/EnderChestBlockEntity;DDDFI)V", at = @At("HEAD"))
    private void feral$enderChestChamsPre(
            EnderChestBlockEntity te,
            double x,
            double y,
            double z,
            float partialTicks,
            int destroyStage,
            CallbackInfo ci) {
        ChestESP.onRenderChestPre(te);
    }

    @Inject(method = "render(Lnet/minecraft/block/entity/EnderChestBlockEntity;DDDFI)V", at = @At("RETURN"))
    private void feral$enderChestChamsPost(
            EnderChestBlockEntity te,
            double x,
            double y,
            double z,
            float partialTicks,
            int destroyStage,
            CallbackInfo ci) {
        ChestESP.onRenderChestPost();
    }
}
