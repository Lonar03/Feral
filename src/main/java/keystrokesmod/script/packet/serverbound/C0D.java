package keystrokesmod.script.packet.serverbound;

import net.minecraft.network.packet.c2s.play.CloseInventoryMenuC2SPacket;

public class C0D extends CPacket {
    public int windowId;

    public C0D(int windowId) {
        super(null);
        this.windowId = windowId;
    }

    public C0D(CloseInventoryMenuC2SPacket packet) {
        super(packet);
    }

    public CloseInventoryMenuC2SPacket convert() {
        return new CloseInventoryMenuC2SPacket(this.windowId);
    }
}
