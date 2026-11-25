package keystrokesmod.script.packet.serverbound;

import keystrokesmod.utility.Utils;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket.Status;

public class C16 extends CPacket {
    public String status;

    public C16(String status) {
        super(null);
        this.status = status;
    }

    public C16(ClientStatusC2SPacket packet) {
        super(packet);
        this.status = packet.getStatus().name();
    }

    public ClientStatusC2SPacket convert() {
        return new ClientStatusC2SPacket(Utils.getEnum(Status.class, this.status));
    }
}
