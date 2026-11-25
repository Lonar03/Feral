package keystrokesmod.event;

import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;

@ForgeEvents.Cancelable
public class PreExplosionPacketEvent extends ForgeEvents.Event {
    public ExplosionS2CPacket packet;

    public PreExplosionPacketEvent(ExplosionS2CPacket packet) {
        this.packet = packet;
    }
}
