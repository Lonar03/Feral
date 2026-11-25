package keystrokesmod.mixin.impl.render;

import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.other.AccountHider;
import keystrokesmod.module.impl.other.Unscramble;
import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.client.render.TextRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@ForgeEvents.SideOnly(ForgeEvents.Side.CLIENT)
@Mixin(TextRenderer.class)
public class MixinFontRenderer {
    @ModifyVariable(
            method = "drawLayer(Ljava/lang/String;FFIZ)I",
            at = @At("HEAD"),
            require = 1,
            ordinal = 0,
            argsOnly = true)
    private String renderString(String string) {
        if (string == null) {
            return null;
        }

        if (ModuleManager.bedwars != null && ModuleManager.bedwars.isEnabled()) {
            string = ModuleManager.bedwars.filterScoreboardLine(string);
        }

        if (ModuleManager.accountHider != null && ModuleManager.accountHider.isEnabled()) {
            string = AccountHider.getFakeName(string);
        }

        if (ModuleManager.unscramble != null && ModuleManager.unscramble.isEnabled()) {
            string = Unscramble.removeObfuscation(string);
        }

        return string;
    }

    @ModifyVariable(
            method = "getWidth(Ljava/lang/String;)I",
            at = @At("HEAD"),
            require = 1,
            ordinal = 0,
            argsOnly = true)
    private String getStringWidth(String string) {
        if (string == null) {
            return null;
        }

        if (ModuleManager.bedwars != null && ModuleManager.bedwars.isEnabled()) {
            string = ModuleManager.bedwars.filterScoreboardLine(string);
        }

        if (ModuleManager.accountHider != null && ModuleManager.accountHider.isEnabled()) {
            string = AccountHider.getFakeName(string);
        }

        if (ModuleManager.unscramble != null && ModuleManager.unscramble.isEnabled()) {
            string = Unscramble.removeObfuscation(string);
        }

        return string;
    }
}
