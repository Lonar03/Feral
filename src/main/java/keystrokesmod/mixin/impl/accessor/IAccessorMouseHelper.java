package keystrokesmod.mixin.impl.accessor;

import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@ForgeEvents.SideOnly(ForgeEvents.Side.CLIENT)
@Mixin(Mouse.class)
public interface IAccessorMouseHelper {
    @Accessor("x")
    int getDeltaX();

    @Accessor("y")
    int getDeltaY();
}
