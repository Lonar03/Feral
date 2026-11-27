package keystrokesmod.clickgui;

import keystrokesmod.utility.shader.ShaderUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

/** Single rounded mask per picker control; nothing is rasterized into stepped strips. */
public final class PickerGradient {
    public static final int SATURATION_VALUE = 0;
    public static final int HUE = 1;
    public static final int ALPHA = 2;
    public static final int SWATCH = 3;
    private static ShaderUtils shader;
    private static boolean unavailable;

    private PickerGradient() {}

    public static boolean draw(
            int mode,
            float x,
            float y,
            float w,
            float h,
            float radius,
            float scale,
            float checker,
            int color,
            float fade) {
        if (unavailable || w <= 0f || h <= 0f || !GLContext.getCapabilities().OpenGL20) {
            return false;
        }
        if (shader == null) {
            try {
                shader = new ShaderUtils("keystrokesmod:shaders/picker_gradient.fsh");
            } catch (RuntimeException e) {
                unavailable = true;
                return false;
            }
        }
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            shader.init();
            shader.setUniformi("mode", mode);
            shader.setUniformf("size", w * scale, h * scale);
            shader.setUniformf("radius", Math.min(radius, Math.min(w, h) / 2f) * scale);
            shader.setUniformf("checker", Math.max(1f, checker * scale));
            shader.setUniformf("fade", fade);
            shader.setUniformf(
                    "tint",
                    (color >> 16 & 255) / 255f,
                    (color >> 8 & 255) / 255f,
                    (color & 255) / 255f,
                    (color >>> 24) / 255f);
            ShaderUtils.drawQuads(x, y, w, h);
        } finally {
            GL20.glUseProgram(program);
            GL11.glPopAttrib();
        }
        return true;
    }
}
