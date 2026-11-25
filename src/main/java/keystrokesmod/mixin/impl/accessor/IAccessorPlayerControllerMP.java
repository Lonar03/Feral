package keystrokesmod.mixin.impl.accessor;

import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.client.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@ForgeEvents.SideOnly(ForgeEvents.Side.CLIENT)
@Mixin(ClientPlayerInteractionManager.class)
public interface IAccessorPlayerControllerMP {
    @Accessor("miningProgress")
    float getCurBlockDamageMP();

    @Accessor("target")
    BlockPos getCurrentBlock();

    @Accessor("miningProgress")
    void setCurBlockDamageMP(float var1);

    @Accessor("miningCooldown")
    int getBlockHitDelay();

    @Accessor("miningCooldown")
    void setBlockHitDelay(int var1);

    @Invoker("updateSelectedHotbarSlot")
    void callSyncCurrentPlayItem();
}
