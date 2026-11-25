package keystrokesmod.script.packet.serverbound;

import keystrokesmod.script.model.ItemStack;
import net.minecraft.network.packet.c2s.play.CreativeMenuSlotC2SPacket;

public class C10 extends CPacket {
    public int slot;
    public ItemStack itemStack;

    public C10(int slot, ItemStack itemStack) {
        super(null);
        this.slot = slot;
        this.itemStack = itemStack;
    }

    public C10(CreativeMenuSlotC2SPacket packet) {
        super(packet);
        this.slot = packet.getSlotId();
        this.itemStack = ItemStack.convert(packet.getItem());
    }

    public CreativeMenuSlotC2SPacket convert() {
        return new CreativeMenuSlotC2SPacket(this.slot, this.itemStack.itemStack);
    }
}
