package keystrokesmod.module.impl.network;

import keystrokesmod.Feral;
import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.Utils;
import net.minecraft.network.packet.c2s.play.PlayerUseC2SPacket;

public class Limbo extends Module {
    private final ButtonSetting lock;

    public Limbo() {
        super("Limbo", Module.category.network);
        this.registerSetting(new DescriptionSetting("Sends you to Hypixel limbo."));
        this.registerSetting(this.lock = new ButtonSetting("Lock", true));
    }

    @Override
    public void enable() {
        if (this.lock.isToggled()) {
            Utils.sendMessage("&b" + this.getName() + "&7: &r&cDisable 'Lock' to enable.");
        } else if (Utils.nullCheck() && mc.getNetworkHandler() != null && Utils.isHypixel()) {
            super.enable();
        } else {
            Utils.sendMessage("&b" + this.getName() + "&7: &r&cMust be on Hypixel.");
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onPreUpdate(PreUpdateEvent event) {
        try {
            if (Utils.nullCheck() && mc.getNetworkHandler() != null && Utils.isHypixel()) {
                Feral.lagHandler.sendThroughLag(new PlayerUseC2SPacket());
            }
        } finally {
            this.disable();
        }
    }
}
