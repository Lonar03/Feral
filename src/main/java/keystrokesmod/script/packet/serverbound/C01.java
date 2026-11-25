package keystrokesmod.script.packet.serverbound;

import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;

public class C01 extends CPacket {
    public String message;

    public C01(String message) {
        super(null);
        this.message = message;
    }

    public C01(ChatMessageC2SPacket packet, byte f) {
        super(packet);
        this.message = packet.getMessage();
    }

    public ChatMessageC2SPacket convert() {
        return new ChatMessageC2SPacket(this.message);
    }
}
