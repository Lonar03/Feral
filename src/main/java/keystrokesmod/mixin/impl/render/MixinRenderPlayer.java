package keystrokesmod.mixin.impl.render;

import keystrokesmod.utility.BlockAnimationUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerRenderer;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public class MixinRenderPlayer {
    @Inject(method = "render(Lnet/minecraft/client/entity/living/player/ClientPlayerEntity;DDDFF)V", at = @At("HEAD"))
    private void onDoRenderHead(
            ClientPlayerEntity entity,
            double x,
            double y,
            double z,
            float entityYaw,
            float partialTicks,
            CallbackInfo ci) {
        BlockAnimationUtils.beginRender(entity);
    }

    @Inject(method = "render(Lnet/minecraft/client/entity/living/player/ClientPlayerEntity;DDDFF)V", at = @At("RETURN"))
    private void onDoRenderReturn(
            ClientPlayerEntity entity,
            double x,
            double y,
            double z,
            float entityYaw,
            float partialTicks,
            CallbackInfo ci) {
        BlockAnimationUtils.endRender(entity);
    }

    @Redirect(
            method = "setModelStatus(Lnet/minecraft/client/entity/living/player/ClientPlayerEntity;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/entity/living/player/PlayerInventory;getSelectedItem()Lnet/minecraft/item/ItemStack;"))
    private ItemStack redirectGetCurrentItem(PlayerInventory inventory) {
        return Minecraft.getInstance().options.perspective == 0 && inventory.player == Minecraft.getInstance().player
                ? Utils.getSpoofedItem(inventory.getSelectedItem())
                : inventory.getSelectedItem();
    }
}
