package keystrokesmod.script.packet.serverbound;

import net.minecraft.network.packet.c2s.play.SelectSlotC2SPacket;

public class C09 extends CPacket {
    public int slot;

    public C09(int slot) {
        super(null);
        this.slot = slot;
    }

    public C09(SelectSlotC2SPacket packet, boolean identifier) {
        super(packet);
        this.slot = packet.getSlot();
    }

    public SelectSlotC2SPacket convert() {
        return new SelectSlotC2SPacket(this.slot);
    }
}
