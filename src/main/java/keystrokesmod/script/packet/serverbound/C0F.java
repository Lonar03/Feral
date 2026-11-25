package keystrokesmod.script.packet.serverbound;

import net.minecraft.network.packet.c2s.play.InventoryMenuConfirmC2SPacket;

public class C0F extends CPacket {
    public int windowId;
    public short uid;

    public C0F(int windowId, short uid) {
        super(null);
        this.windowId = windowId;
        this.uid = uid;
    }

    public C0F(InventoryMenuConfirmC2SPacket packet) {
        super(packet);
        this.windowId = packet.getMenuId();
        this.uid = packet.getActionId();
    }

    public InventoryMenuConfirmC2SPacket convert() {
        return new InventoryMenuConfirmC2SPacket(this.windowId, this.uid, true);
    }
}
