package keystrokesmod.script.packet.clientbound;

import net.minecraft.network.packet.s2c.play.CommandSuggestionsS2CPacket;

public class S3A extends SPacket {
    public String[] matches;

    public S3A(CommandSuggestionsS2CPacket packet, byte f) {
        super(packet);
        this.matches = packet.getSuggestions();
    }

    public S3A(String[] matches) {
        super(new CommandSuggestionsS2CPacket(matches));
        this.matches = matches;
    }
}
