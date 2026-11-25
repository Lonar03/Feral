package keystrokesmod.event;

import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.network.packet.Packet;

public class NoEventPacketEvent extends ForgeEvents.Event {
    private Packet<?> packet;

    public NoEventPacketEvent(Packet<?> packet) {
        this.packet = packet;
    }

    public Packet<?> getPacket() {
        return this.packet;
    }
}
