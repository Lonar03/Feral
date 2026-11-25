package keystrokesmod.mixin.impl.accessor;

import net.minecraft.network.packet.s2c.play.CloseInventoryMenuS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CloseInventoryMenuS2CPacket.class)
public interface IAccessorCloseInventoryMenuS2C {
    @Accessor("id")
    int getWindowId();
}
