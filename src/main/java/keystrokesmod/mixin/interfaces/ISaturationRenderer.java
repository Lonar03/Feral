package keystrokesmod.mixin.interfaces;

import net.minecraft.client.render.PostChain;

public interface ISaturationRenderer {
    PostChain feral$getSaturationShader();

    void feral$setSaturationShader(PostChain var1);
}
