package keystrokesmod.event;

import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.network.packet.s2c.play.EntityVelocityS2CPacket;

@ForgeEvents.Cancelable
public class PreEntityVelocityEvent extends ForgeEvents.Event {
    public EntityVelocityS2CPacket packet;

    public PreEntityVelocityEvent(EntityVelocityS2CPacket packet) {
        this.packet = packet;
    }
}
