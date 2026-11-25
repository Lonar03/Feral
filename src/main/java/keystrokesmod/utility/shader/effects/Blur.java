package keystrokesmod.utility.shader.effects;

import java.util.ArrayList;
import java.util.List;
import keystrokesmod.utility.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

public class Blur {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final boolean ORNITHE = Blur.class.getResource("/feral.ornithe.accesswidener") != null;
    private static Blur instance;
    private Integer destinationFramebuffer;
    public RenderTarget first = new RenderTarget(1, 1, false);
    public List<RenderTarget> buffers = new ArrayList<>();
    public Shader up = new Shader(
            "#version 120\n\nuniform sampler2D inTexture, textureToCheck;\nuniform vec2 halfpixel, offset, iResolution;\nuniform int check;\n\nvoid main() {\n    vec2 uv = gl_FragCoord.xy / iResolution;\n    vec4 sum = texture2D(inTexture, uv + vec2(-halfpixel.x * 2.0, 0.0) * offset);\n    sum += texture2D(inTexture, uv + vec2(-halfpixel.x, halfpixel.y) * offset) * 2.0;\n    sum += texture2D(inTexture, uv + vec2(0.0, halfpixel.y * 2.0) * offset);\n    sum += texture2D(inTexture, uv + vec2(halfpixel.x, halfpixel.y) * offset) * 2.0;\n    sum += texture2D(inTexture, uv + vec2(halfpixel.x * 2.0, 0.0) * offset);\n    sum += texture2D(inTexture, uv + vec2(halfpixel.x, -halfpixel.y) * offset) * 2.0;\n    sum += texture2D(inTexture, uv + vec2(0.0, -halfpixel.y * 2.0) * offset);\n    sum += texture2D(inTexture, uv + vec2(-halfpixel.x, -halfpixel.y) * offset) * 2.0;\n    vec4 average = sum / 12.0;\n    gl_FragColor = vec4(average.rgb, mix(1.0, texture2D(textureToCheck, gl_TexCoord[0].st).a, check));\n}");
    public Shader down = new Shader(
            "#version 120\n\nuniform sampler2D inTexture;\nuniform vec2 offset, halfpixel, iResolution;\n\nvoid main() {\n    vec2 uv = gl_FragCoord.xy / iResolution;\n    vec4 sum = texture2D(inTexture, uv) * 4.0;\n    sum += texture2D(inTexture, uv - halfpixel.xy * offset);\n    sum += texture2D(inTexture, uv + halfpixel.xy * offset);\n    sum += texture2D(inTexture, uv + vec2(halfpixel.x, -halfpixel.y) * offset);\n    sum += texture2D(inTexture, uv - vec2(halfpixel.x, -halfpixel.y) * offset);\n    gl_FragColor = vec4(sum.rgb * 0.125, 1.0);\n}");
    public RenderTarget stencil = new RenderTarget(1, 1, true);
    public int iterations;

    public static Blur get() {
        if (instance == null) {
            instance = new Blur();
        }

        return instance;
    }

    public void setup(float n) {
        for (RenderTarget fb : this.buffers) {
            fb.destroyBuffers();
        }

        this.buffers.clear();
        this.first = this.resize(null, false);
        this.buffers.add(this.first);

        for (int i = 1; i <= n; i++) {
            RenderTarget fb =
                    new RenderTarget((int) (mc.width / Math.pow(2.0, i)), (int) (mc.height / Math.pow(2.0, i)), false);
            fb.setFilterMode(9729);
            GlStateManager.bindTexture(fb.colorTextureId);
            GL11.glTexParameteri(3553, 10242, 33648);
            GL11.glTexParameteri(3553, 10243, 33648);
            GlStateManager.bindTexture(0);
            this.buffers.add(fb);
        }
    }

    public RenderTarget resize(RenderTarget fb, boolean depth) {
        if (fb != null && fb.viewWidth == mc.width && fb.viewHeight == mc.height) {
            return fb;
        }

        if (fb != null) {
            fb.destroyBuffers();
        }

        return new RenderTarget(mc.width, mc.height, depth);
    }

    public void pass(RenderTarget fb, int texture, Shader shader, float offset) {
        fb.clear();
        fb.bindWrite(false);
        shader.use();
        GL11.glBindTexture(3553, texture);
        shader.uniformf("offset", offset, offset);
        shader.uniformi("inTexture", 0);
        shader.uniformi("check", 0);
        shader.uniformf("halfpixel", 1.0F / fb.viewWidth, 1.0F / fb.viewHeight);
        shader.uniformf("iResolution", fb.viewWidth, fb.viewHeight);
        RenderUtils.screenQuad();
        shader.stop();
    }

    public void startStencil() {
        this.destinationFramebuffer = this.currentFramebuffer();
        this.stencil = this.resize(this.stencil, false);
        this.stencil.clear();
        this.stencil.bindWrite(false);
    }

    public void render(int n, float offset) {
        Integer framebuffer = this.destinationFramebuffer;
        this.destinationFramebuffer = null;
        if (framebuffer == null) {
            framebuffer = this.currentFramebuffer();
        }

        RenderTarget main = mc.getRenderTarget();
        this.stencil.unbindWrite();
        if (n > 0 && !(offset <= 0.0F)) {
            if (this.iterations != n || this.first.viewWidth != mc.width || this.first.viewHeight != mc.height) {
                this.setup(n);
                this.iterations = n;
            }

            RenderTarget out = this.buffers.get(0);
            int sceneTexture = main.colorTextureId;
            if (framebuffer != null && framebuffer != main.frameBufferId) {
                GLX.bindFramebuffer(GLX.GL_FRAMEBUFFER, framebuffer);
                sceneTexture = out.colorTextureId;
                GL13.glActiveTexture(33984);
                GL11.glBindTexture(3553, sceneTexture);
                GL11.glCopyTexSubImage2D(3553, 0, 0, 0, 0, 0, out.viewWidth, out.viewHeight);
            }

            this.pass(this.buffers.get(1), sceneTexture, this.down, offset);

            for (int i = 1; i < n; i++) {
                this.pass(this.buffers.get(i + 1), this.buffers.get(i).colorTextureId, this.down, offset);
            }

            for (int var8 = n; var8 > 1; var8--) {
                this.pass(this.buffers.get(var8 - 1), this.buffers.get(var8).colorTextureId, this.up, offset);
            }

            out.clear();
            out.bindWrite(false);
            this.up.use();
            this.up.uniformf("offset", offset, offset);
            this.up.uniformi("inTexture", 0);
            this.up.uniformi("check", 1);
            this.up.uniformi("textureToCheck", 16);
            this.up.uniformf("halfpixel", 1.0F / out.viewWidth, 1.0F / out.viewHeight);
            this.up.uniformf("iResolution", out.viewWidth, out.viewHeight);
            GL13.glActiveTexture(34000);
            GL11.glBindTexture(3553, this.stencil.colorTextureId);
            GL13.glActiveTexture(33984);
            GL11.glBindTexture(3553, this.buffers.get(1).colorTextureId);
            RenderUtils.screenQuad();
            this.up.stop();
            this.bindDestination(main, framebuffer);
            GlStateManager.enableTexture();
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glBindTexture(3553, this.buffers.get(0).colorTextureId);
            GlStateManager.enableAlphaTest();
            GlStateManager.alphaFunc(516, 0.0F);
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(770, 771);
            RenderUtils.screenQuad();
            GlStateManager.bindTexture(0);
            GlStateManager.disableBlend();
        } else {
            this.bindDestination(main, framebuffer);
        }
    }

    private Integer currentFramebuffer() {
        return ORNITHE && GLX.useFbo() ? GL11.glGetInteger(36006) : null;
    }

    private void bindDestination(RenderTarget main, Integer framebuffer) {
        if (framebuffer != null && framebuffer != main.frameBufferId) {
            GLX.bindFramebuffer(GLX.GL_FRAMEBUFFER, framebuffer);
        } else {
            main.bindWrite(true);
        }
    }
}
