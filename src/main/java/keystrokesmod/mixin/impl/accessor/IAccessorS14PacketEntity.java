package keystrokesmod.mixin.impl.accessor;

import net.minecraft.network.packet.s2c.play.EntityMoveS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityMoveS2CPacket.class)
public interface IAccessorS14PacketEntity {
    @Accessor("id")
    int getEntityId();
}
