package keystrokesmod.event;

import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.item.ItemStack;

@ForgeEvents.Cancelable
public class UseItemEvent extends ForgeEvents.Event {
    public ItemStack usedItemStack;

    public UseItemEvent(ItemStack usedItemStack) {
        this.usedItemStack = usedItemStack;
    }
}
