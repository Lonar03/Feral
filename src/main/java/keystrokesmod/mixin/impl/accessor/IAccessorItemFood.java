package keystrokesmod.mixin.impl.accessor;

import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.item.FoodItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@ForgeEvents.SideOnly(ForgeEvents.Side.CLIENT)
@Mixin(FoodItem.class)
public interface IAccessorItemFood {
    @Accessor("alwaysEdible")
    boolean getAlwaysEdible();
}
