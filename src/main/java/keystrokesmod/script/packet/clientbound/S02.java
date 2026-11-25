package keystrokesmod.script.packet.clientbound;

import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.text.LiteralText;

public class S02 extends SPacket {
    public byte type;
    public String message;

    public S02(ChatMessageS2CPacket packet) {
        super(packet);
        this.type = packet.getType();
        this.message = packet.getMessage().getString();
    }

    public S02(byte type, String message) {
        super(new ChatMessageS2CPacket(new LiteralText(message), type));
        this.type = type;
        this.message = message;
    }
}
