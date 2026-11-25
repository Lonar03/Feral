package keystrokesmod.script.packet.clientbound;

import keystrokesmod.utility.Utils;
import net.minecraft.network.packet.s2c.play.TitlesS2CPacket;
import net.minecraft.network.packet.s2c.play.TitlesS2CPacket.Type;
import net.minecraft.text.LiteralText;

public class S45 extends SPacket {
    public String type;
    public String message;
    private int fadeInTime;
    private int displayTime;
    private int fadeOutTime;

    public S45(TitlesS2CPacket packet) {
        super(packet);
        this.type = packet.getType().name();
        this.message = packet.getText().getString();
        this.fadeInTime = packet.getFadeIn();
        this.displayTime = packet.getDuration();
        this.fadeOutTime = packet.getFadeOut();
    }

    public S45(String type, String message, int fadeInTime, int displayTime, int fadeOutTime) {
        super(new TitlesS2CPacket(
                Utils.getEnum(Type.class, type), new LiteralText(message), fadeInTime, displayTime, fadeOutTime));
        this.type = type;
        this.message = message;
        this.fadeInTime = fadeInTime;
        this.displayTime = displayTime;
        this.fadeOutTime = fadeOutTime;
    }
}
