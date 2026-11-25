package keystrokesmod.script.packet.clientbound;

import keystrokesmod.script.model.ItemStack;
import net.minecraft.network.packet.s2c.play.InventoryMenuSlotContentS2CPacket;

public class S2F extends SPacket {
    public int windowId;
    public int slot;
    public ItemStack itemStack;

    public S2F(InventoryMenuSlotContentS2CPacket packet) {
        super(packet);
        this.windowId = packet.getMenuId();
        this.slot = packet.getSlotId();
        this.itemStack = ItemStack.convert(packet.getItem());
    }

    public S2F(int windowId, int slot, ItemStack itemStack) {
        super(new InventoryMenuSlotContentS2CPacket(windowId, slot, itemStack.itemStack));
        this.windowId = windowId;
        this.slot = slot;
        this.itemStack = itemStack;
    }
}
