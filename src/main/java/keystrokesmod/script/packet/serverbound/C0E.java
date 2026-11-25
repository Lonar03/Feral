package keystrokesmod.script.packet.serverbound;

import keystrokesmod.script.model.ItemStack;
import net.minecraft.network.packet.c2s.play.InventoryMenuClickSlotC2SPacket;

public class C0E extends CPacket {
    public int windowId;
    public int slot;
    public int button;
    public int mode;
    public short action;
    public ItemStack itemStack;

    public C0E(int windowId, int slot, int button, int mode, ItemStack itemStack) {
        super(null);
        this.windowId = windowId;
        this.slot = slot;
        this.button = button;
        this.mode = mode;
        this.itemStack = itemStack;
    }

    public C0E(InventoryMenuClickSlotC2SPacket packet) {
        super(packet);
        this.windowId = packet.getMenuId();
        this.slot = packet.getSlot();
        this.button = packet.getClickData();
        this.mode = packet.getAction();
        this.action = packet.getActionId();
        this.itemStack = new ItemStack(packet.getItem(), (byte) 0);
    }

    public InventoryMenuClickSlotC2SPacket convert() {
        return new InventoryMenuClickSlotC2SPacket(
                this.windowId, this.slot, this.button, this.mode, this.itemStack.itemStack, this.action);
    }
}
