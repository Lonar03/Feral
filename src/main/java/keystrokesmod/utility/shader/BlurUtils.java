package keystrokesmod.utility.shader;

import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.shader.effects.Blur;

public class BlurUtils {
    public static void prepareBlur() {
        RenderUtils.updateResolution();
        Blur.get().startStencil();
    }

    public static void prepareBloom() {
        RenderUtils.updateResolution();
        keystrokesmod.utility.shader.effects.GlowShader.get().startCapture();
    }

    public static void blurEnd(int passes, float radius) {
        Blur.get().render(passes, radius);
    }

    public static void bloomEnd(int passes, float radius) {
        keystrokesmod.utility.shader.effects.GlowShader.get().render(passes, radius);
    }
}
