package keystrokesmod.mixin.impl.accessor;

import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.client.gui.GameGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@ForgeEvents.SideOnly(ForgeEvents.Side.CLIENT)
@Mixin(GameGui.class)
public interface IAccessorGuiIngame {
    @Accessor("overlayMessage")
    String getRecordPlaying();

    @Accessor("title")
    String getDisplayedTitle();

    @Accessor("subtitle")
    String getDisplayedSubTitle();
}
