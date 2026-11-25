package keystrokesmod.mixin.impl.accessor;

import net.minecraft.network.packet.c2s.play.CloseInventoryMenuC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CloseInventoryMenuC2SPacket.class)
public interface IAccessorCloseInventoryMenuC2S {
    @Accessor("id")
    int getWindowId();
}
