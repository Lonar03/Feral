package keystrokesmod.mixin.impl.accessor;

import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@ForgeEvents.SideOnly(ForgeEvents.Side.CLIENT)
@Mixin(Screen.class)
public interface IAccessorGuiScreen {
    @Invoker("mouseClicked")
    void callMouseClicked(int var1, int var2, int var3);
}
