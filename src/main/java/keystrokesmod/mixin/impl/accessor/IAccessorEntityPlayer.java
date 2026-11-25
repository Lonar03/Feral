package keystrokesmod.mixin.impl.accessor;

import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.entity.living.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@ForgeEvents.SideOnly(ForgeEvents.Side.CLIENT)
@Mixin(PlayerEntity.class)
public interface IAccessorEntityPlayer {
    @Accessor("itemUseTimer")
    void setItemInUseCount(int var1);

    /** The raw sleeping flag (isSleeping() can be hooked, e.g. by Easy Revive). */
    @Accessor("sleeping")
    boolean isSleepingRaw();
}
