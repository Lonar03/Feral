package keystrokesmod.utility.shader.effects;

import keystrokesmod.utility.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.client.render.platform.GlStateManager;
import org.lwjgl.opengl.GL11;

public final class EspShaders {
    private static final Minecraft mc = Minecraft.getInstance();
    private static Shader fill;
    private static Shader roundRect;
    private static Shader outline;

    private EspShaders() {}

    public static Shader fill() {
        if (fill == null) {
            fill = new Shader(
                    "#version 120\nuniform sampler2D u_texture;\nuniform vec3 u_color;\nuniform float u_alpha_threshold;\nvarying float v_glint;\n\nvoid main() {\n    if (v_glint > 0.5) discard;\n\n    float a = texture2D(u_texture, gl_TexCoord[0].st).a;\n    if (a <= u_alpha_threshold) discard;\n\n    gl_FragColor = vec4(u_color, 1.0);\n}\n",
                    "#version 120\nvarying float v_glint;\n\nvoid main() {\n    mat4 m = gl_TextureMatrix[0];\n\n    float delta =\n        abs(m[0][0] - 1.0) + abs(m[0][1]) +\n        abs(m[1][0]) + abs(m[1][1] - 1.0) +\n        abs(m[3][0]) + abs(m[3][1]);\n\n    v_glint = step(0.001, delta);\n    gl_TexCoord[0] = gl_MultiTexCoord0;\n    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n}\n");
        }

        return fill;
    }

    public static Shader roundRect() {
        if (roundRect == null) {
            roundRect = new Shader(
                    "#version 120\n\nuniform vec2 u_size;\nuniform float u_radius;\nuniform vec4 u_color;\n\nvoid main(void)\n{\n    gl_FragColor = vec4(u_color.rgb, u_color.a * smoothstep(1.0, 0.0, length(max((abs(gl_TexCoord[0].st - 0.5) + 0.5) * u_size - u_size + u_radius, 0.0)) - u_radius + 0.5));\n}");
        }

        return roundRect;
    }

    public static Shader outline() {
        if (outline == null) {
            outline = new Shader(
                    "#version 120\nuniform sampler2D u_texture;\nuniform vec2 u_texel_size;\nuniform float u_radius;\nuniform float u_alpha_threshold;\n\nvec4 pick(vec2 uv, vec2 p) {\n    vec4 c;\n    c = texture2D(u_texture, uv + vec2( p.x, 0.0)); if (c.a > u_alpha_threshold) return c;\n    c = texture2D(u_texture, uv + vec2(-p.x, 0.0)); if (c.a > u_alpha_threshold) return c;\n    c = texture2D(u_texture, uv + vec2(0.0,  p.y)); if (c.a > u_alpha_threshold) return c;\n    c = texture2D(u_texture, uv + vec2(0.0, -p.y)); if (c.a > u_alpha_threshold) return c;\n    c = texture2D(u_texture, uv + vec2( p.x,  p.y)); if (c.a > u_alpha_threshold) return c;\n    c = texture2D(u_texture, uv + vec2(-p.x,  p.y)); if (c.a > u_alpha_threshold) return c;\n    c = texture2D(u_texture, uv + vec2( p.x, -p.y)); if (c.a > u_alpha_threshold) return c;\n    c = texture2D(u_texture, uv + vec2(-p.x, -p.y)); if (c.a > u_alpha_threshold) return c;\n    return vec4(0.0);\n}\n\nvoid main() {\n    vec2 uv = gl_TexCoord[0].st;\n    vec4 center = texture2D(u_texture, uv);\n    if (center.a > u_alpha_threshold || u_radius <= 0.0) discard;\n\n    vec4 c = pick(uv, u_texel_size * min(u_radius, 1.0));\n    if (c.a <= u_alpha_threshold && u_radius > 1.0) c = pick(uv, u_texel_size * u_radius);\n    if (c.a <= u_alpha_threshold) discard;\n\n    gl_FragColor = vec4(c.rgb, 1.0);\n}\n");
        }

        return outline;
    }

    public static boolean valid(Shader s) {
        return s.program > 0;
    }

    public static void useFill(int n) {
        Shader s = fill();
        s.use();
        s.uniformi("u_texture", 0);
        s.uniformf("u_color", (n >> 16 & 0xFF) / 255.0F, (n >> 8 & 0xFF) / 255.0F, (n & 0xFF) / 255.0F);
        s.uniformf("u_alpha_threshold", 0.001F);
    }

    public static void roundRect(float x, float y, float x2, float y2, float radius, int n) {
        Shader s = roundRect();
        if (s.program != -1) {
            s.use();
            float w = x2 - x;
            float h = y2 - y;
            s.uniformf("u_size", w, h);
            s.uniformf("u_radius", radius);
            float a = (n >> 24 & 0xFF) / 255.0F;
            float r = (n >> 16 & 0xFF) / 255.0F;
            float g = (n >> 8 & 0xFF) / 255.0F;
            float b = (n & 0xFF) / 255.0F;
            s.uniformf("u_color", r, g, b, a);
            GlStateManager.color4f(r, g, b, a);
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(770, 771);
            GlStateManager.enableAlphaTest();
            GlStateManager.alphaFunc(516, 0.0F);
            RenderUtils.quad(x, y, w, h);
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.disableBlend();
            s.stop();
        }
    }

    private static void fullscreenQuad() {
        Window sr = new Window(mc);
        double w = sr.getScaledWidth();
        double h = sr.getScaledHeight();
        GL11.glBegin(7);
        GL11.glTexCoord2d(0.0, 1.0);
        GL11.glVertex2d(0.0, 0.0);
        GL11.glTexCoord2d(0.0, 0.0);
        GL11.glVertex2d(0.0, h);
        GL11.glTexCoord2d(1.0, 0.0);
        GL11.glVertex2d(w, h);
        GL11.glTexCoord2d(1.0, 1.0);
        GL11.glVertex2d(w, 0.0);
        GL11.glEnd();
    }

    public static void outline(RenderTarget fb, float radius, float threshold) {
        Shader s = outline();
        if (valid(s)) {
            s.use();
            s.uniformi("u_texture", 0);
            s.uniformf("u_texel_size", 1.0F / fb.viewWidth, 1.0F / fb.viewHeight);
            s.uniformf("u_radius", radius);
            s.uniformf("u_alpha_threshold", threshold);
            GlStateManager.bindTexture(fb.colorTextureId);
            fullscreenQuad();
            GlStateManager.bindTexture(0);
            s.stop();
        }
    }
}
