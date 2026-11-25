package keystrokesmod.module.impl.player;

import keystrokesmod.mixin.impl.accessor.IAccessorEntityLivingBase;
import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.Utils;

public class DelayRemover extends Module {
    public ButtonSetting oldReg;
    public ButtonSetting removeJumpTicks;

    public DelayRemover() {
        super("Delay Remover", Module.category.player, 0);
        this.liteModule = true;
        this.registerSetting(this.oldReg = new ButtonSetting("1.7 hitreg", true));
        this.registerSetting(this.removeJumpTicks = new ButtonSetting("Remove jump ticks", false));
        this.closetModule = true;
    }

    @ForgeEvents.SubscribeEvent
    public void onTick(ForgeEvents.TickEvent.PlayerTickEvent e) {
        if (e.phase == ForgeEvents.TickEvent.Phase.END && mc.focused && Utils.nullCheck()) {
            if (this.oldReg.isToggled()) {
                ((IAccessorMinecraft) mc).setLeftClickCounter(0);
            }

            if (this.removeJumpTicks.isToggled()) {
                ((IAccessorEntityLivingBase) mc.player).setJumpTicks(0);
            }
        }
    }
}
