package keystrokesmod.mixin.impl.accessor;

import java.util.List;
import net.minecraft.client.gui.screen.game.BookEditScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BookEditScreen.class)
public interface IAccessorGuiScreenBook {
    @Accessor("lines")
    List<Text> getBookContents();

    @Accessor("book")
    ItemStack getBookObj();
}
