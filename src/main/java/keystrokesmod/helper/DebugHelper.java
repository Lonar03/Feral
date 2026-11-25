package keystrokesmod.helper;

import keystrokesmod.Feral;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.IMinecraftInstance;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;

public class DebugHelper implements IMinecraftInstance {
    public static boolean MIXIN;
    public static boolean BACKGROUND;

    @ForgeEvents.SubscribeEvent
    public void onRenderTick(ForgeEvents.TickEvent.RenderTickEvent ev) {
        if (Feral.DEBUG
                && ev.phase == ForgeEvents.TickEvent.Phase.END
                && Utils.nullCheck()
                && ModuleManager.debug.debugBPS.isToggled()
                && mc.screen == null) {
            RenderUtils.renderBPS(true, true);
        }
    }

    public static void debugMixin(Object obj, String message) {
        if (MIXIN) {
            Utils.sendMessage("&d" + obj.getClass().getSimpleName() + "&7: " + message);
        }
    }
}
