package keystrokesmod.mixin.impl.client;

import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.player.SafeWalk;
import net.minecraft.client.Minecraft;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.options.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(GameOptions.class)
public class MixinGameSettings {
    @Overwrite
    public static boolean isPressed(KeyBinding key) {
        SafeWalk safewalk = ModuleManager.safeWalk;
        return key == Minecraft.getInstance().options.sneakKey
                        && safewalk != null
                        && safewalk.isEnabled()
                        && safewalk.sneak.isToggled()
                        && safewalk.isSneaking
                ? true
                : key.getKeyCode() != 0
                        && (key.getKeyCode() < 0
                                ? Mouse.isButtonDown(key.getKeyCode() + 100)
                                : Keyboard.isKeyDown(key.getKeyCode()));
    }
}
