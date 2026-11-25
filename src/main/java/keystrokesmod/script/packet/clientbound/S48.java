package keystrokesmod.script.packet.clientbound;

import net.minecraft.network.packet.s2c.play.ResourcePackS2CPacket;

public class S48 extends SPacket {
    public String url;
    public String hash;

    public S48(ResourcePackS2CPacket packet) {
        super(packet);
        this.url = packet.getUrl();
        this.hash = packet.getHash();
    }

    public S48(String url, String hash) {
        super(new ResourcePackS2CPacket(url, hash));
        this.url = url;
        this.hash = hash;
    }
}
