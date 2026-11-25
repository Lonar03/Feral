package keystrokesmod.utility.shader;

import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.shader.effects.Blur;

public final class KawaseBlur {
    private KawaseBlur() {}

    public static void renderBlur(int texture, int iterations, float offset) {
        RenderUtils.updateResolution();
        Blur blur = Blur.get();
        int saved = blur.stencil.colorTextureId;
        blur.stencil.colorTextureId = texture;

        try {
            blur.render(iterations, offset);
        } finally {
            blur.stencil.colorTextureId = saved;
        }
    }
}
