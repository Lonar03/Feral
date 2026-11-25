package keystrokesmod.mixin.impl.accessor;

import java.util.List;
import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.client.render.PostChain;
import net.minecraft.client.render.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@ForgeEvents.SideOnly(ForgeEvents.Side.CLIENT)
@Mixin(PostChain.class)
public interface IAccessorShaderGroup {
    @Accessor("passes")
    List<PostPass> getListShaders();
}
