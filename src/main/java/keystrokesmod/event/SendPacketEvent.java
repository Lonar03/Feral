package keystrokesmod.event;

import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.network.packet.Packet;

@ForgeEvents.Cancelable
public class SendPacketEvent extends ForgeEvents.Event {
    private Packet<?> packet;

    public SendPacketEvent(Packet<?> packet) {
        this.packet = packet;
    }

    public Packet<?> getPacket() {
        return this.packet;
    }
}
