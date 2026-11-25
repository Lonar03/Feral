package keystrokesmod.event;

import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.network.packet.Packet;

@ForgeEvents.Cancelable
public class ReceivePacketEvent extends ForgeEvents.Event {
    private Packet<?> packet;

    public ReceivePacketEvent(Packet<?> packet) {
        this.packet = packet;
    }

    public Packet<?> getPacket() {
        return this.packet;
    }
}
