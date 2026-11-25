package keystrokesmod.script.packet.clientbound;

import keystrokesmod.script.model.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityEquipmentS2CPacket;

public class S04 extends SPacket {
    public int entityId;
    public int slot;
    public ItemStack item;

    public S04(EntityEquipmentS2CPacket e) {
        super(e);
        this.entityId = e.getId();
        this.slot = e.getEquipmentSlot();
        this.item = ItemStack.convert(e.getItem());
    }

    public S04(int entityId, int slot, ItemStack item) {
        super(new EntityEquipmentS2CPacket(entityId, slot, item.itemStack));
        this.entityId = entityId;
        this.slot = slot;
        this.item = item;
    }
}
