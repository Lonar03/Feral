package keystrokesmod.script.packet.serverbound;

import net.minecraft.network.packet.c2s.play.ArmSwingC2SPacket;

public class C0A extends CPacket {
    public C0A(ArmSwingC2SPacket packet) {
        super(packet);
    }

    public C0A() {
        super(new ArmSwingC2SPacket());
    }
}
