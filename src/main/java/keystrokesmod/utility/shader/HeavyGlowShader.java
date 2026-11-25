package keystrokesmod.utility.shader;

import keystrokesmod.utility.RenderUtils;
import net.minecraft.client.render.pipeline.RenderTarget;
import org.lwjgl.opengl.GL20;

public final class HeavyGlowShader {
    private final HeavyGlowShader.MaskShader mask = new HeavyGlowShader.MaskShader();
    private final HeavyGlowShader.EdgeShader edge = new HeavyGlowShader.EdgeShader();

    public boolean isValid() {
        return this.mask.isValid() && this.edge.isValid();
    }

    public void useMask(int color) {
        this.mask.use();
        GL20.glUniform3f(
                this.mask.uniform("color"),
                (color >> 16 & 0xFF) / 255.0F,
                (color >> 8 & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F);
    }

    public void stopMask() {
        this.mask.stop();
    }

    public void render(RenderTarget source) {
        KawaseBloom.renderBlur(source.colorTextureId, 2, 6.5F);
        this.edge.use();

        try {
            GL20.glUniform2f(this.edge.uniform("texelSize"), 1.0F / source.viewWidth, 1.0F / source.viewHeight);
            RenderUtils.drawFramebufferFullscreen(source);
        } finally {
            this.edge.stop();
        }
    }

    private static final class EdgeShader extends OutlineESPShader {
        private static final String FRAGMENT =
                "#version 120\nuniform sampler2D tex;\nuniform vec2 texelSize;\nvec4 pick(vec2 uv, vec2 p) {\n  vec4 c;\n  c = texture2D(tex, uv + vec2(p.x, 0.0)); if (c.a > 0.1) return c;\n  c = texture2D(tex, uv + vec2(-p.x, 0.0)); if (c.a > 0.1) return c;\n  c = texture2D(tex, uv + vec2(0.0, p.y)); if (c.a > 0.1) return c;\n  c = texture2D(tex, uv + vec2(0.0, -p.y)); if (c.a > 0.1) return c;\n  c = texture2D(tex, uv + vec2(p.x, p.y)); if (c.a > 0.1) return c;\n  c = texture2D(tex, uv + vec2(-p.x, p.y)); if (c.a > 0.1) return c;\n  c = texture2D(tex, uv + vec2(p.x, -p.y)); if (c.a > 0.1) return c;\n  c = texture2D(tex, uv + vec2(-p.x, -p.y)); if (c.a > 0.1) return c;\n  return vec4(0.0);\n}\nvoid main() {\n  vec2 uv = gl_TexCoord[0].st;\n  if (texture2D(tex, uv).a > 0.1) discard;\n  vec4 c = pick(uv, texelSize);\n  if (c.a <= 0.1) c = pick(uv, texelSize * 2.0);\n  if (c.a <= 0.1) discard;\n  gl_FragColor = vec4(c.rgb, 1.0);\n}";

        private EdgeShader() {
            super(
                    "#version 120\nuniform sampler2D tex;\nuniform vec2 texelSize;\nvec4 pick(vec2 uv, vec2 p) {\n  vec4 c;\n  c = texture2D(tex, uv + vec2(p.x, 0.0)); if (c.a > 0.1) return c;\n  c = texture2D(tex, uv + vec2(-p.x, 0.0)); if (c.a > 0.1) return c;\n  c = texture2D(tex, uv + vec2(0.0, p.y)); if (c.a > 0.1) return c;\n  c = texture2D(tex, uv + vec2(0.0, -p.y)); if (c.a > 0.1) return c;\n  c = texture2D(tex, uv + vec2(p.x, p.y)); if (c.a > 0.1) return c;\n  c = texture2D(tex, uv + vec2(-p.x, p.y)); if (c.a > 0.1) return c;\n  c = texture2D(tex, uv + vec2(p.x, -p.y)); if (c.a > 0.1) return c;\n  c = texture2D(tex, uv + vec2(-p.x, -p.y)); if (c.a > 0.1) return c;\n  return vec4(0.0);\n}\nvoid main() {\n  vec2 uv = gl_TexCoord[0].st;\n  if (texture2D(tex, uv).a > 0.1) discard;\n  vec4 c = pick(uv, texelSize);\n  if (c.a <= 0.1) c = pick(uv, texelSize * 2.0);\n  if (c.a <= 0.1) discard;\n  gl_FragColor = vec4(c.rgb, 1.0);\n}");
        }

        @Override
        public void onLink() {
            this.cacheUniform("tex");
            this.cacheUniform("texelSize");
        }

        @Override
        public void onUse() {
            GL20.glUseProgram(this.programId);
            GL20.glUniform1i(this.uniform("tex"), 0);
        }
    }

    private static final class MaskShader extends OutlineESPShader {
        private static final String VERTEX =
                "#version 120\nvarying float glint;\nvoid main() {\n  mat4 m = gl_TextureMatrix[0];\n  float delta = abs(m[0][0] - 1.0) + abs(m[0][1]) + abs(m[1][0]) + abs(m[1][1] - 1.0) + abs(m[3][0]) + abs(m[3][1]);\n  glint = step(0.001, delta);\n  gl_TexCoord[0] = gl_MultiTexCoord0;\n  gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n}";
        private static final String FRAGMENT =
                "#version 120\nuniform sampler2D tex;\nuniform vec3 color;\nvarying float glint;\nvoid main() {\n  if (glint > 0.5 || texture2D(tex, gl_TexCoord[0].st).a <= 0.001) discard;\n  gl_FragColor = vec4(color, 1.0);\n}";

        private MaskShader() {
            super(
                    "#version 120\nuniform sampler2D tex;\nuniform vec3 color;\nvarying float glint;\nvoid main() {\n  if (glint > 0.5 || texture2D(tex, gl_TexCoord[0].st).a <= 0.001) discard;\n  gl_FragColor = vec4(color, 1.0);\n}",
                    "#version 120\nvarying float glint;\nvoid main() {\n  mat4 m = gl_TextureMatrix[0];\n  float delta = abs(m[0][0] - 1.0) + abs(m[0][1]) + abs(m[1][0]) + abs(m[1][1] - 1.0) + abs(m[3][0]) + abs(m[3][1]);\n  glint = step(0.001, delta);\n  gl_TexCoord[0] = gl_MultiTexCoord0;\n  gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n}");
        }

        @Override
        public void onLink() {
            this.cacheUniform("tex");
            this.cacheUniform("color");
        }

        @Override
        public void onUse() {
            GL20.glUseProgram(this.programId);
            GL20.glUniform1i(this.uniform("tex"), 0);
        }
    }
}
