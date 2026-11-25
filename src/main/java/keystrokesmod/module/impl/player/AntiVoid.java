package keystrokesmod.module.impl.player;

import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.PacketUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Position;

public class AntiVoid extends Module {
    private final SliderSetting fallDistance;
    private double lastGroundY;

    public AntiVoid() {
        super("AntiVoid", Module.category.player);
        this.registerSetting(this.fallDistance = new SliderSetting("Fall distance", 3.0, 0.0, 10.0, 0.1));
    }

    @Override
    public void onEnable() {
        this.lastGroundY = Double.NaN;
    }

    @ForgeEvents.SubscribeEvent
    public void onPreUpdate(PreUpdateEvent event) {
        if (mc.player != null && mc.world != null && mc.player.ticks > 1) {
            if (mc.player.onGround) {
                this.lastGroundY = mc.player.y;
            } else if (this.lastGroundY - mc.player.y > this.fallDistance.getInput()
                    && !mc.player.abilities.canFly
                    && Utils.overVoid(mc.player.x, mc.player.y, mc.player.z)) {
                PacketUtils.sendPacketNoEvent(new Position(mc.player.x, mc.player.y + 100.0, mc.player.z, false));
            }
        } else {
            this.lastGroundY = Double.NaN;
        }
    }
}
