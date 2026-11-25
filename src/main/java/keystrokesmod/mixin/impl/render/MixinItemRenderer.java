package keystrokesmod.mixin.impl.render;

import keystrokesmod.mixin.interfaces.IMixinItemRenderer;
import keystrokesmod.utility.Utils;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.render.ItemInHandRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.UseAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class MixinItemRenderer implements IMixinItemRenderer {
    private ItemStack originalItemToRender;

    @Shadow
    private ItemStack itemInHand;

    public boolean cancelUpdate = false;
    public boolean cancelReset = false;
    private boolean renderItemInUse;

    @Shadow
    private float handHeight;

    @Shadow
    private float lastHandHeight;

    @Inject(method = "renderInFirstPerson", at = @At("HEAD"))
    private void modifyRenderItemPre(float p_renderItemInFirstPerson_1_, CallbackInfo info) {
        this.originalItemToRender = this.itemInHand;
        this.itemInHand = Utils.getSpoofedItem(this.originalItemToRender);
    }

    @Inject(method = "renderInFirstPerson", at = @At("RETURN"))
    private void modifyRenderItemPost(float p_renderItemInFirstPerson_1_, CallbackInfo info) {
        this.itemInHand = this.originalItemToRender;
    }

    @Redirect(
            method = "renderInFirstPerson",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/entity/living/player/ClientPlayerEntity;getItemUseTimer()I"))
    private int getItemInUseCountForRender(ClientPlayerEntity player) {
        int actualCount = player.getItemUseTimer();
        if (actualCount <= 0 && this.renderItemInUse && this.itemInHand != null) {
            return this.itemInHand.getUseAction() == UseAction.BLOCK ? 1 : actualCount;
        } else {
            return actualCount;
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onUpdateEquippedItem(CallbackInfo ci) {
        if (this.cancelUpdate) {
            this.cancelUpdate = false;
            this.handHeight = 1.0F;
            this.lastHandHeight = 1.0F;
            ci.cancel();
        }
    }

    @Inject(method = "onBlockUsed", at = @At("HEAD"), cancellable = true)
    public void injectResetEquippedProgress(CallbackInfo ci) {
        if (this.cancelReset) {
            this.cancelReset = false;
            this.handHeight = 1.0F;
            this.lastHandHeight = 1.0F;
            ci.cancel();
        }
    }

    @Inject(method = "onItemUsed", at = @At("HEAD"), cancellable = true)
    public void injectResetEquippedProgress2(CallbackInfo ci) {
        if (this.cancelReset) {
            this.cancelReset = false;
            this.handHeight = 1.0F;
            this.lastHandHeight = 1.0F;
            ci.cancel();
        }
    }

    @Override
    public void setCancelUpdate(boolean cancel) {
        this.cancelUpdate = cancel;
    }

    @Override
    public void setCancelReset(boolean reset) {
        this.cancelReset = reset;
    }

    @Override
    public boolean isRenderItemInUse() {
        return this.renderItemInUse;
    }

    @Override
    public void setRenderItemInUse(boolean renderItemInUse) {
        this.renderItemInUse = renderItemInUse;
    }
}
