package keystrokesmod.event;

import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.network.Connection;
import net.minecraft.network.packet.Packet;

public class DispatchPacketEvent extends ForgeEvents.Event {
    private Packet<?> packet;
    private final Connection networkManager;

    public DispatchPacketEvent(Packet<?> packet) {
        this(null, packet);
    }

    public DispatchPacketEvent(Connection networkManager, Packet<?> packet) {
        this.networkManager = networkManager;
        this.packet = packet;
    }

    public Connection getNetworkManager() {
        return this.networkManager;
    }

    public Packet<?> getPacket() {
        return this.packet;
    }
}
