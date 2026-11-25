package keystrokesmod.mixin.impl.render;

import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.other.NameHider;
import keystrokesmod.module.impl.render.AntiShuffle;
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

        if (ModuleManager.nameHider != null && ModuleManager.nameHider.isEnabled()) {
            string = NameHider.getFakeName(string);
        }

        if (ModuleManager.antiShuffle != null && ModuleManager.antiShuffle.isEnabled()) {
            string = AntiShuffle.removeObfuscation(string);
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

        if (ModuleManager.nameHider != null && ModuleManager.nameHider.isEnabled()) {
            string = NameHider.getFakeName(string);
        }

        if (ModuleManager.antiShuffle != null && ModuleManager.antiShuffle.isEnabled()) {
            string = AntiShuffle.removeObfuscation(string);
        }

        return string;
    }
}
