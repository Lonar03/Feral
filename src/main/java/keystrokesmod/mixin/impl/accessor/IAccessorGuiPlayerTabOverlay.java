package keystrokesmod.mixin.impl.accessor;

import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.client.gui.overlay.PlayerTabOverlay;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@ForgeEvents.SideOnly(ForgeEvents.Side.CLIENT)
@Mixin(PlayerTabOverlay.class)
public interface IAccessorGuiPlayerTabOverlay {
    @Accessor("header")
    Text getHeader();

    @Accessor("footer")
    Text getFooter();
}
