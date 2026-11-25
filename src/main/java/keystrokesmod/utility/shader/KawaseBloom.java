package keystrokesmod.utility.shader;

import keystrokesmod.utility.RenderUtils;
import net.minecraft.client.render.pipeline.RenderTarget;

public final class KawaseBloom {
    private static RenderTarget source;

    private KawaseBloom() {}

    public static void renderBlur(int texture, int iterations, float offset) {
        RenderUtils.updateResolution();
        if (source == null) {
            source = new RenderTarget(1, 1, false);
        }

        int saved = source.colorTextureId;
        source.colorTextureId = texture;

        try {
            keystrokesmod.utility.shader.effects.GlowShader.get().render(source, iterations, offset);
        } finally {
            source.colorTextureId = saved;
        }
    }
}
