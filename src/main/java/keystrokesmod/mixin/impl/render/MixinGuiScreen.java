package keystrokesmod.mixin.impl.render;

import keystrokesmod.event.KeyPressEvent;
import keystrokesmod.platform.ClientPlatform;
import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class MixinGuiScreen {
    @Shadow
    public Minecraft minecraft;

    @Shadow
    public int width;

    @Shadow
    public int height;

    @Inject(method = "sendChatMessage(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true)
    private void messageSend(String msg, boolean addToChat, CallbackInfo callbackInfo) {
        if (addToChat && ClientPlatform.handleChat(msg)) {
            this.minecraft.gui.getChat().addRecentMessage(msg);
            callbackInfo.cancel();
        }
    }

    @Inject(method = "handleKeyboard", at = @At("HEAD"), cancellable = true)
    private void injectHandleKeyboardInput(CallbackInfo callbackInfo) {
        KeyPressEvent event = new KeyPressEvent(Keyboard.getEventCharacter(), Keyboard.getEventKey());
        ForgeEvents.MinecraftForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            callbackInfo.cancel();
        }
    }
}
