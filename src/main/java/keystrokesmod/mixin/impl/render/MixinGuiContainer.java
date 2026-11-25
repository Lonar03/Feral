package keystrokesmod.mixin.impl.render;

import keystrokesmod.module.ModuleManager;
import net.minecraft.client.gui.screen.game.inventory.InventoryMenuScreen;
import net.minecraft.inventory.slot.InventorySlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryMenuScreen.class)
public class MixinGuiContainer {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void feral$cancelManagedInventoryMouseClick(
            int mouseX, int mouseY, int mouseButton, CallbackInfo callbackInfo) {
        if (shouldCancelManualInventoryInput()) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void feral$cancelManagedInventoryMouseDrag(
            int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick, CallbackInfo callbackInfo) {
        if (shouldCancelManualInventoryInput()) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void feral$cancelManagedInventoryMouseRelease(
            int mouseX, int mouseY, int state, CallbackInfo callbackInfo) {
        if (shouldCancelManualInventoryInput()) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "clickSlot", at = @At("HEAD"), cancellable = true)
    private void feral$cancelManagedInventoryWindowClick(
            InventorySlot slotIn, int slotId, int clickedButton, int clickType, CallbackInfo callbackInfo) {
        if (shouldCancelManualInventoryInput()) {
            callbackInfo.cancel();
        }
    }

    @Inject(
            method = "keyPressed",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/entity/living/player/LocalClientPlayerEntity;closeMenu()V"),
            cancellable = true)
    private void feral$keepInventoryOnKey(char typedChar, int keyCode, CallbackInfo ci) {
        if (ModuleManager.keepInventory != null
                && ModuleManager.keepInventory.handleCloseKey((InventoryMenuScreen) (Object) this, keyCode)) {
            ci.cancel();
        }
    }

    private static boolean shouldCancelManualInventoryInput() {
        return ModuleManager.inventory != null && ModuleManager.inventory.shouldCancelManualInventoryInput();
    }
}
