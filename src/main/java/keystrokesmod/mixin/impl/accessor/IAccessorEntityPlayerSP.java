package keystrokesmod.mixin.impl.accessor;

import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@ForgeEvents.SideOnly(ForgeEvents.Side.CLIENT)
@Mixin(LocalClientPlayerEntity.class)
public interface IAccessorEntityPlayerSP {
    @Accessor("sentX")
    double getLastReportedPosX();

    @Accessor("sentY")
    double getLastReportedPosY();

    @Accessor("sentZ")
    double getLastReportedPosZ();

    @Accessor("sentYaw")
    float getLastReportedYaw();
}
