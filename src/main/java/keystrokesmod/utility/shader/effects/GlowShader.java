package keystrokesmod.utility.shader.effects;

import java.util.ArrayList;
import java.util.List;
import keystrokesmod.utility.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import org.lwjgl.opengl.GL11;

public class GlowShader {
    private static final Minecraft mc = Minecraft.getInstance();
    private static GlowShader instance;
    private int iterations;
    private final Shader down;
    private final Shader up;
    private RenderTarget capture = new RenderTarget(1, 1, false);
    private RenderTarget first = new RenderTarget(1, 1, true);
    private final List<RenderTarget> buffers = new ArrayList<>();

    public static GlowShader get() {
        if (instance == null) {
            instance = new GlowShader();
        }

        return instance;
    }

    public GlowShader() {
        this.up = new Shader(
                "#version 120\n\nuniform sampler2D inTexture, textureToCheck;\nuniform vec2 halfpixel, offset, iResolution;\nuniform int check;\n\nvoid main() {\n    vec2 uv = gl_FragCoord.xy / iResolution;\n\n    vec2 offset1 = vec2(-halfpixel.x, 0.0) * offset;\n    vec2 offset2 = vec2(-halfpixel.x, halfpixel.y) * offset;\n    vec2 offset3 = vec2(0.0, halfpixel.y * 2.0) * offset;\n    vec2 offset4 = vec2(halfpixel.x, halfpixel.y) * offset;\n    vec2 offset5 = vec2(halfpixel.x * 2.0, 0.0) * offset;\n    vec2 offset6 = vec2(halfpixel.x, -halfpixel.y) * offset;\n    vec2 offset7 = vec2(0.0, -halfpixel.y * 2.0) * offset;\n    vec2 offset8 = vec2(-halfpixel.x, -halfpixel.y) * offset;\n\n    vec4 sum = texture2D(inTexture, uv + offset1);\n    sum.rgb *= sum.a;\n    vec4 smpl1 = texture2D(inTexture, uv + offset2);\n    smpl1.rgb *= smpl1.a;\n    sum += smpl1 * 2.0;\n    vec4 smp2 = texture2D(inTexture, uv + offset3);\n    smp2.rgb *= smp2.a;\n    sum += smp2;\n    vec4 smp3 = texture2D(inTexture, uv + offset4);\n    smp3.rgb *= smp3.a;\n    sum += smp3 * 2.0;\n    vec4 smp4 = texture2D(inTexture, uv + offset5);\n    smp4.rgb *= smp4.a;\n    sum += smp4;\n    vec4 smp5 = texture2D(inTexture, uv + offset6);\n    smp5.rgb *= smp5.a;\n    sum += smp5 * 2.0;\n    vec4 smp6 = texture2D(inTexture, uv + offset7);\n    smp6.rgb *= smp6.a;\n    sum += smp6;\n    vec4 smp7 = texture2D(inTexture, uv + offset8);\n    smp7.rgb *= smp7.a;\n    sum += smp7 * 2.0;\n\n    vec4 result = sum / 12.0;\n    float checkAlpha = texture2D(textureToCheck, gl_TexCoord[0].st).a;\n    gl_FragColor = vec4(result.rgb / result.a, mix(result.a, result.a * (1.0 - checkAlpha), float(check)));\n}\n");
        this.down = new Shader(
                "#version 120\n\nuniform sampler2D inTexture;\nuniform vec2 offset, halfpixel, iResolution;\n\nvoid main() {\n    vec2 uv = gl_FragCoord.xy / iResolution;\n\n    vec4 sum = texture2D(inTexture, uv);\n    sum.rgb *= sum.a;\n    sum *= 4.0;\n    \n    vec4 smp1 = texture2D(inTexture, uv - halfpixel.xy * offset);\n    smp1.rgb *= smp1.a;\n    sum += smp1;\n    \n    vec4 smp2 = texture2D(inTexture, uv + halfpixel.xy * offset);\n    smp2.rgb *= smp2.a;\n    sum += smp2;\n    \n    vec4 smp3 = texture2D(inTexture, uv + vec2(halfpixel.x, -halfpixel.y) * offset);\n    smp3.rgb *= smp3.a;\n    sum += smp3;\n    \n    vec4 smp4 = texture2D(inTexture, uv - vec2(halfpixel.x, -halfpixel.y) * offset);\n    smp4.rgb *= smp4.a;\n    sum += smp4;\n    \n    vec4 result = sum / 8.0;\n    gl_FragColor = vec4(result.rgb / result.a, result.a);\n}\n");
    }

    public void render(int n, float f) {
        this.render(n, f, null);
    }

    void render(int n, float f, Integer framebuffer) {
        RenderTarget main = mc.getRenderTarget();
        this.capture.unbindWrite();
        if (n > 0 && !(f <= 0.0F)) {
            if (this.iterations != n || this.first.viewWidth != mc.width || this.first.viewHeight != mc.height) {
                this.setup(n);
                this.iterations = n;
            }

            GlStateManager.enableAlphaTest();
            GlStateManager.alphaFunc(516, 0.0F);
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(1, 1);
            GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            this.pass(this.buffers.get(1), this.capture.colorTextureId, this.down, f);

            for (int i = 1; i < n; i++) {
                this.pass(this.buffers.get(i + 1), this.buffers.get(i).colorTextureId, this.down, f);
            }

            for (int var7 = n; var7 > 1; var7--) {
                this.pass(this.buffers.get(var7 - 1), this.buffers.get(var7).colorTextureId, this.up, f);
            }

            RenderTarget fb = this.buffers.get(0);
            fb.clear();
            fb.bindWrite(false);
            this.up.use();
            this.up.uniformf("offset", f, f);
            this.up.uniformi("inTexture", 0);
            this.up.uniformi("check", 1);
            this.up.uniformi("textureToCheck", 16);
            this.up.uniformf("halfpixel", 1.0F / fb.viewWidth, 1.0F / fb.viewHeight);
            this.up.uniformf("iResolution", fb.viewWidth, fb.viewHeight);
            GlStateManager.activeTexture(34000);
            GL11.glBindTexture(3553, this.capture.colorTextureId);
            GlStateManager.activeTexture(33984);
            GL11.glBindTexture(3553, this.buffers.get(1).colorTextureId);
            RenderUtils.screenQuad();
            this.up.stop();
            GlStateManager.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
            this.bindDestination(main, framebuffer, false);
            GL11.glBindTexture(3553, this.buffers.get(0).colorTextureId);
            GlStateManager.enableAlphaTest();
            GlStateManager.alphaFunc(516, 0.0F);
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(770, 771);
            RenderUtils.screenQuad();
            GlStateManager.bindTexture(0);
            GlStateManager.enableAlphaTest();
            GlStateManager.alphaFunc(516, 0.0F);
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(770, 771);
        } else {
            this.bindDestination(main, framebuffer, true);
        }
    }

    private RenderTarget resize(RenderTarget fb, boolean depth) {
        if (fb != null && fb.viewWidth == mc.width && fb.viewHeight == mc.height) {
            return fb;
        }

        if (fb != null) {
            fb.destroyBuffers();
        }

        return new RenderTarget(mc.width, mc.height, depth);
    }

    private void setup(float f) {
        for (RenderTarget fb : this.buffers) {
            fb.destroyBuffers();
        }

        this.buffers.clear();
        this.first = this.resize(null, true);
        this.buffers.add(this.first);

        for (int n = 1; n <= f; n++) {
            RenderTarget fb =
                    new RenderTarget((int) (mc.width / Math.pow(2.0, n)), (int) (mc.height / Math.pow(2.0, n)), true);
            fb.setFilterMode(9729);
            GlStateManager.bindTexture(fb.colorTextureId);
            GL11.glTexParameteri(3553, 10242, 33648);
            GL11.glTexParameteri(3553, 10243, 33648);
            GlStateManager.bindTexture(0);
            this.buffers.add(fb);
        }
    }

    private void pass(RenderTarget fb, int texture, Shader shader, float f) {
        fb.clear();
        fb.bindWrite(false);
        shader.use();
        GL11.glBindTexture(3553, texture);
        shader.uniformf("offset", f, f);
        shader.uniformi("inTexture", 0);
        shader.uniformi("check", 0);
        shader.uniformf("halfpixel", 1.0F / fb.viewWidth, 1.0F / fb.viewHeight);
        shader.uniformf("iResolution", fb.viewWidth, fb.viewHeight);
        RenderUtils.screenQuad();
        shader.stop();
    }

    public void startCapture() {
        this.capture = this.resize(this.capture, false);
        this.capture.clear();
        this.capture.bindWrite(false);
    }

    public void render(RenderTarget fb, int n, float f) {
        RenderTarget saved = this.capture;
        this.capture = fb;

        try {
            this.render(n, f);
        } finally {
            this.capture = saved;
        }
    }

    private void bindDestination(RenderTarget main, Integer framebuffer, boolean updateViewport) {
        if (framebuffer != null && framebuffer != main.frameBufferId) {
            GLX.bindFramebuffer(GLX.GL_FRAMEBUFFER, framebuffer);
        } else {
            main.bindWrite(updateViewport);
        }
    }
}
