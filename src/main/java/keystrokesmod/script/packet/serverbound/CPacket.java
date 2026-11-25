package keystrokesmod.script.packet.serverbound;

import keystrokesmod.script.ScriptClassNames;
import net.minecraft.network.packet.Packet;

public class CPacket {
    public String name;
    public Packet packet;

    public CPacket(Packet packet) {
        if (packet != null) {
            this.packet = packet;
            this.name = ScriptClassNames.simpleName(packet.getClass());
        }
    }

    public Packet convert() {
        return this.packet;
    }
}
