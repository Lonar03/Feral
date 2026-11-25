package keystrokesmod.mixin.impl.accessor;

import net.minecraft.network.Connection;
import net.minecraft.network.handler.PacketHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Connection.class)
public interface IAccessorNetworkManager {
    @Accessor("listener")
    PacketHandler getPacketListener();
}
