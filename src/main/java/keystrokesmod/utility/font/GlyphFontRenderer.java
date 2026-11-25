package keystrokesmod.utility.font;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.other.NameHider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.platform.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public final class GlyphFontRenderer implements FeralFontRenderer {
    private static final int FIRST_GLYPH = 0;
    private static final int LAST_GLYPH = 255;
    private static final int CHANNEL_MASK = 255;
    private static final int GLYPH_MARGIN = 4;
    private static final float MIN_RENDER_SCALE = 2.0F;
    private static final float QUALITY_MULTIPLIER = 2.0F;
    private static final String ALPHABET = "ABCDEFGHOKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final String COLOR_CODES = "0123456789abcdefklmnor";
    private final Font renderFont;
    private final boolean antiAlias;
    private final FontRenderContext fontRenderContext;
    private final GlyphFontRenderer.GlyphData[] defaultGlyphs = new GlyphFontRenderer.GlyphData[256];
    private final Map<Character, GlyphFontRenderer.GlyphData> extendedGlyphs = new ConcurrentHashMap<>();
    private final float drawScale;
    private final float rawScale;
    private final float rawTextTop;
    private final float rawTextBottom;
    private final float fontHeight;
    private final float lineHeight;
    private boolean destroyed;
    private final float rawStrokeWidth;
    private final int strokePad;

    public GlyphFontRenderer(Font sourceFont, boolean antiAlias) {
        this(sourceFont, antiAlias, 0.0F);
    }

    /**
     * @param thickness extra letter weight in GUI pixels; each glyph gets an outline of this width traced around it
     *     before it is uploaded, so 0 renders the font as-is.
     */
    public GlyphFontRenderer(Font sourceFont, boolean antiAlias, float thickness) {
        float renderScale = resolveRenderScale();
        this.rawStrokeWidth = Math.max(0.0F, thickness) * renderScale;
        this.strokePad = (int) Math.ceil(this.rawStrokeWidth / 2.0F);
        this.drawScale = 1.0F / renderScale;
        this.rawScale = renderScale;
        this.renderFont =
                sourceFont.deriveFont(sourceFont.getStyle(), Math.max(1.0F, sourceFont.getSize2D() * renderScale));
        this.antiAlias = antiAlias;
        this.fontRenderContext = new FontRenderContext(new AffineTransform(), antiAlias, true);

        for (int codePoint = 0; codePoint <= 255; codePoint++) {
            this.defaultGlyphs[codePoint] = this.createGlyph((char) codePoint);
        }

        this.rawTextTop = this.computeRawTextTop();
        this.rawTextBottom = this.computeRawTextBottom();
        this.fontHeight = Math.max(1.0F, (this.rawTextBottom - this.rawTextTop) * this.drawScale);
        this.lineHeight = this.computeLineHeight();
    }

    @Override
    public int drawString(String text, float x, float y, int color, boolean shadow) {
        text = hideNames(text);
        if (!this.destroyed && text != null && !text.isEmpty()) {
            int width = 0;
            if (shadow) {
                width = this.drawInternal(text, x + 0.5F, y + 0.5F, color, true);
            }

            return Math.max(width, this.drawInternal(text, x, y, color, false));
        } else {
            return 0;
        }
    }

    @Override
    public int drawGlyphString(
            String text, float x, float y, FeralFontRenderer.GlyphColorProvider colorProvider, boolean shadow) {
        text = hideNames(text);
        if (!this.destroyed && text != null && !text.isEmpty()) {
            int width = 0;
            if (shadow) {
                width = this.drawGlyphInternal(text, x + 0.5F, y + 0.5F, colorProvider, true);
            }

            return Math.max(width, this.drawGlyphInternal(text, x, y, colorProvider, false));
        } else {
            return 0;
        }
    }

    @Override
    public int getStringWidth(String text) {
        text = hideNames(text);
        if (text != null && !text.isEmpty()) {
            float width = 0.0F;

            for (int i = 0; i < text.length(); i++) {
                char character = text.charAt(i);
                if (!isMalformedSectionPrefix(text, i)) {
                    if (character == 167 && i + 1 < text.length()) {
                        i++;
                    } else if (character != '\n') {
                        width += this.getGlyph(character).advance;
                    }
                }
            }

            return Math.round(width);
        } else {
            return 0;
        }
    }

    @Override
    public int getFontHeight() {
        return Math.round(this.fontHeight);
    }

    @Override
    public int getLineHeight() {
        return Math.round(this.lineHeight);
    }

    @Override
    public int getTextTopOffset() {
        return 0;
    }

    @Override
    public int getTextBottomOffset() {
        return Math.round(this.fontHeight);
    }

    @Override
    public void destroy() {
        if (!this.destroyed) {
            this.destroyed = true;
            deleteGlyphTextures(this.defaultGlyphs);

            for (GlyphFontRenderer.GlyphData glyph : this.extendedGlyphs.values()) {
                deleteGlyphTexture(glyph);
            }

            this.extendedGlyphs.clear();
        }
    }

    private int drawInternal(String text, float x, float y, int color, boolean shadowPass) {
        int alpha = color >>> 24 & 0xFF;
        if (alpha == 0) {
            alpha = 255;
        }

        int activeColor = shadowPass ? applyShadowColor(color) : withAlpha(color, alpha);
        float startX = x * this.rawScale;
        float drawX = startX;
        float drawY = y * this.rawScale - this.rawTextTop;
        GL11.glPushMatrix();

        try {
            GlStateManager.enableAlphaTest();
            GlStateManager.enableBlend();
            GlStateManager.blendFuncSeparate(770, 771, 1, 0);
            GlStateManager.enableTexture();
            GL11.glTexEnvi(8960, 8704, 8448);
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.scalef(this.drawScale, this.drawScale, 1.0F);

            for (int i = 0; i < text.length(); i++) {
                char character = text.charAt(i);
                if (!isMalformedSectionPrefix(text, i)) {
                    if (character == 167 && i + 1 < text.length()) {
                        char formatCode = Character.toLowerCase(text.charAt(++i));
                        int colorIndex = "0123456789abcdefklmnor".indexOf(formatCode);
                        if (colorIndex >= 0) {
                            if (colorIndex < 16) {
                                activeColor = getMinecraftColor(colorIndex, alpha, shadowPass);
                            } else if (formatCode == 'r') {
                                activeColor = shadowPass ? applyShadowColor(color) : withAlpha(color, alpha);
                            }
                        }
                    } else if (character == '\n') {
                        drawX = startX;
                        drawY += this.lineHeight * this.rawScale;
                    } else {
                        GlyphFontRenderer.GlyphData glyph = this.getGlyph(character);
                        if (glyph.textureId != 0 && glyph.textureWidth > 0.0F && glyph.textureHeight > 0.0F) {
                            this.renderGlyph(glyph, drawX - 4.0F, drawY, activeColor);
                        }

                        drawX += glyph.rawAdvance;
                    }
                }
            }
        } finally {
            GlStateManager.bindTexture(0);
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glPopMatrix();
            GlStateManager.matrixMode(5888);
        }

        return Math.round((drawX - startX) * this.drawScale);
    }

    private int drawGlyphInternal(
            String text, float x, float y, FeralFontRenderer.GlyphColorProvider colorProvider, boolean shadowPass) {
        float startX = x * this.rawScale;
        float drawX = startX;
        float drawY = y * this.rawScale - this.rawTextTop;
        Integer formattingColor = null;
        GL11.glPushMatrix();

        try {
            GlStateManager.enableAlphaTest();
            GlStateManager.enableBlend();
            GlStateManager.blendFuncSeparate(770, 771, 1, 0);
            GlStateManager.enableTexture();
            GL11.glTexEnvi(8960, 8704, 8448);
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.scalef(this.drawScale, this.drawScale, 1.0F);

            for (int i = 0; i < text.length(); i++) {
                char character = text.charAt(i);
                if (!isMalformedSectionPrefix(text, i)) {
                    if (character == 167 && i + 1 < text.length()) {
                        char formatCode = Character.toLowerCase(text.charAt(++i));
                        int colorIndex = "0123456789abcdefklmnor".indexOf(formatCode);
                        if (colorIndex >= 0 && colorIndex < 16) {
                            formattingColor = getMinecraftColor(colorIndex, 255, false);
                        } else if (formatCode == 'r') {
                            formattingColor = null;
                        }
                    } else if (character == '\n') {
                        drawX = startX;
                        drawY += this.lineHeight * this.rawScale;
                    } else {
                        GlyphFontRenderer.GlyphData glyph = this.getGlyph(character);
                        int glyphColor = colorProvider.colorForGlyph(
                                character, (drawX - startX) * this.drawScale, glyph.advance, formattingColor);
                        int alpha = glyphColor >>> 24 & 0xFF;
                        if (alpha == 0) {
                            alpha = 255;
                        }

                        glyphColor = withAlpha(glyphColor, alpha);
                        if (shadowPass) {
                            glyphColor = applyShadowColor(glyphColor);
                        }

                        if (glyph.textureId != 0 && glyph.textureWidth > 0.0F && glyph.textureHeight > 0.0F) {
                            this.renderGlyph(glyph, drawX - 4.0F, drawY, glyphColor);
                        }

                        drawX += glyph.rawAdvance;
                    }
                }
            }
        } finally {
            GlStateManager.bindTexture(0);
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glPopMatrix();
            GlStateManager.matrixMode(5888);
        }

        return Math.round((drawX - startX) * this.drawScale);
    }

    private void renderGlyph(GlyphFontRenderer.GlyphData glyph, float x, float y, int color) {
        GlStateManager.bindTexture(glyph.textureId);
        float alpha = (color >>> 24 & 0xFF) / 255.0F;
        float red = (color >>> 16 & 0xFF) / 255.0F;
        float green = (color >>> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        GlStateManager.color4f(red, green, blue, alpha);
        GL11.glBegin(7);
        GL11.glTexCoord2f(0.0F, 0.0F);
        GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(0.0F, 1.0F);
        GL11.glVertex2f(x, y + glyph.textureHeight);
        GL11.glTexCoord2f(1.0F, 1.0F);
        GL11.glVertex2f(x + glyph.textureWidth, y + glyph.textureHeight);
        GL11.glTexCoord2f(1.0F, 0.0F);
        GL11.glVertex2f(x + glyph.textureWidth, y);
        GL11.glEnd();
    }

    private GlyphFontRenderer.GlyphData getGlyph(char character) {
        if (character >= 0 && character <= 255) {
            GlyphFontRenderer.GlyphData glyph = this.defaultGlyphs[character];
            if (glyph != null) {
                return glyph;
            }
        }

        return this.extendedGlyphs.computeIfAbsent(character, this::createGlyph);
    }

    private GlyphFontRenderer.GlyphData createGlyph(char character) {
        if (this.destroyed) {
            return new GlyphFontRenderer.GlyphData(0, 0.0F, 0.0F, 0.0F, 0.0F, 0, 0);
        }

        if (Character.isISOControl(character) && character != '\n') {
            return new GlyphFontRenderer.GlyphData(0, 0.0F, 0.0F, 0.0F, 0.0F, 0, 0);
        }

        String glyphText = String.valueOf(character);
        BufferedImage metricsImage = new BufferedImage(1, 1, 2);
        Graphics2D metricsGraphics = metricsImage.createGraphics();

        try {
            metricsGraphics.setFont(this.renderFont);
            this.applyRenderHints(metricsGraphics);
            FontMetrics metrics = metricsGraphics.getFontMetrics();
            Rectangle2D bounds = metrics.getStringBounds(glyphText, metricsGraphics);
            // The outline grows each glyph by strokePad on every side; widen its advance to match so thicker
            // letters keep the same gap between them.
            float rawAdvance = Math.max(1.0F, (float) bounds.getWidth()) + this.strokePad * 2;
            int textureWidth = Math.max(1, (int) Math.ceil(rawAdvance) + 8);
            int textureHeight = Math.max(1, metrics.getHeight() + this.strokePad * 2);
            BufferedImage glyphImage = new BufferedImage(textureWidth, textureHeight, 2);
            Graphics2D glyphGraphics = glyphImage.createGraphics();

            try {
                glyphGraphics.setFont(this.renderFont);
                glyphGraphics.setBackground(new Color(255, 255, 255, 0));
                glyphGraphics.clearRect(0, 0, textureWidth, textureHeight);
                glyphGraphics.setColor(Color.WHITE);
                this.applyRenderHints(glyphGraphics);
                int originX = 4 + this.strokePad;
                int baselineY = metrics.getAscent() + this.strokePad;
                if (this.rawStrokeWidth > 0.0F) {
                    GlyphVector glyphVector =
                            this.renderFont.createGlyphVector(glyphGraphics.getFontRenderContext(), glyphText);
                    Shape outline = glyphVector.getOutline(originX, baselineY);
                    glyphGraphics.fill(outline);
                    glyphGraphics.setStroke(
                            new BasicStroke(this.rawStrokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    glyphGraphics.draw(outline);
                } else {
                    glyphGraphics.drawString(glyphText, originX, baselineY);
                }
            } finally {
                glyphGraphics.dispose();
            }

            int textureId = this.uploadTexture(glyphImage);
            int[] visibleBounds = findVisibleRowBounds(glyphImage);
            return new GlyphFontRenderer.GlyphData(
                    textureId,
                    textureWidth,
                    textureHeight,
                    rawAdvance,
                    rawAdvance * this.drawScale,
                    visibleBounds[0],
                    visibleBounds[1]);
        } finally {
            metricsGraphics.dispose();
        }
    }

    private int uploadTexture(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, new int[width * height], 0, width);
        ByteBuffer byteBuffer = BufferUtils.createByteBuffer(width * height * 4);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = pixels[x + y * width];
                byteBuffer.put((byte) (pixel >> 16 & 0xFF));
                byteBuffer.put((byte) (pixel >> 8 & 0xFF));
                byteBuffer.put((byte) (pixel & 0xFF));
                byteBuffer.put((byte) (pixel >> 24 & 0xFF));
            }
        }

        ((Buffer) byteBuffer).flip();
        int textureId = GL11.glGenTextures();
        GlStateManager.bindTexture(textureId);
        GL11.glTexParameteri(3553, 10241, 9729);
        GL11.glTexParameteri(3553, 10240, 9729);
        GL11.glTexImage2D(3553, 0, 6408, width, height, 0, 6408, 5121, byteBuffer);
        return textureId;
    }

    private float computeRawTextTop() {
        float minTop = Float.MAX_VALUE;

        for (int i = 0; i < "ABCDEFGHOKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".length(); i++) {
            GlyphFontRenderer.GlyphData glyph =
                    this.getGlyph("ABCDEFGHOKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".charAt(i));
            if (glyph.hasVisiblePixels()) {
                minTop = Math.min(minTop, glyph.visibleTop);
            }
        }

        return minTop == Float.MAX_VALUE ? 0.0F : minTop;
    }

    private float computeRawTextBottom() {
        float maxBottom = 0.0F;

        for (int i = 0; i < "ABCDEFGHOKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".length(); i++) {
            GlyphFontRenderer.GlyphData glyph =
                    this.getGlyph("ABCDEFGHOKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".charAt(i));
            if (glyph.hasVisiblePixels()) {
                maxBottom = Math.max(maxBottom, glyph.visibleBottom);
            }
        }

        return maxBottom <= 0.0F ? Math.max(1.0F, this.renderFont.getSize2D()) : maxBottom;
    }

    private float computeLineHeight() {
        return Math.max(
                this.fontHeight,
                (float) this.renderFont
                                .getStringBounds(
                                        "ABCDEFGHOKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz", this.fontRenderContext)
                                .getHeight()
                        * this.drawScale);
    }

    private static int[] findVisibleRowBounds(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int top = -1;
        int bottom = -1;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((image.getRGB(x, y) >>> 24 & 0xFF) != 0) {
                    if (top == -1) {
                        top = y;
                    }

                    bottom = y + 1;
                    break;
                }
            }
        }

        return top == -1 ? new int[] {0, 0} : new int[] {top, bottom};
    }

    private static float resolveRenderScale() {
        Minecraft mc = Minecraft.getInstance();
        int uiScale = 1;

        try {
            uiScale = Math.max(1, new Window(mc).getScale());
        } catch (Exception var3) {
        }

        return Math.max(2.0F, uiScale * 2.0F);
    }

    private void applyRenderHints(Graphics2D graphics) {
        graphics.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                this.antiAlias ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                this.antiAlias ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setRenderingHint(
                RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    private static void deleteGlyphTextures(GlyphFontRenderer.GlyphData[] glyphs) {
        for (int i = 0; i < glyphs.length; i++) {
            deleteGlyphTexture(glyphs[i]);
        }
    }

    private static void deleteGlyphTexture(GlyphFontRenderer.GlyphData glyph) {
        if (glyph != null && glyph.textureId != 0) {
            GL11.glDeleteTextures(glyph.textureId);
        }
    }

    private static int getMinecraftColor(int colorIndex, int alpha, boolean shadow) {
        int offset = (colorIndex >> 3 & 1) * 85;
        int red = (colorIndex >> 2 & 1) * 170 + offset;
        int green = (colorIndex >> 1 & 1) * 170 + offset;
        int blue = (colorIndex & 1) * 170 + offset;
        if (colorIndex == 6) {
            red += 85;
        }

        if (shadow) {
            red /= 4;
            green /= 4;
            blue /= 4;
        }

        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static int applyShadowColor(int color) {
        int alpha = color >>> 24 & 0xFF;
        if (alpha == 0) {
            alpha = 255;
        }

        int red = (color >>> 16 & 0xFF) / 4;
        int green = (color >>> 8 & 0xFF) / 4;
        int blue = (color & 0xFF) / 4;
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static int withAlpha(int color, int alpha) {
        return alpha << 24 | color & 16777215;
    }

    private static boolean isMalformedSectionPrefix(String text, int index) {
        if (!isFormattingArtifact(text.charAt(index))) {
            return false;
        }

        for (int i = index + 1; i < text.length(); i++) {
            char next = text.charAt(i);
            if (next == 167) {
                return true;
            }

            if (!isFormattingArtifact(next)) {
                return false;
            }
        }

        return false;
    }

    private static boolean isFormattingArtifact(char character) {
        return character == 194 || character == 195 || character == 130 || character == 8218;
    }

    private static String hideNames(String text) {
        return text != null && ModuleManager.nameHider != null && ModuleManager.nameHider.isEnabled()
                ? NameHider.getFakeName(text)
                : text;
    }

    private static final class GlyphData {
        private final int textureId;
        private final float textureWidth;
        private final float textureHeight;
        private final float rawAdvance;
        private final float advance;
        private final int visibleTop;
        private final int visibleBottom;

        private GlyphData(
                int textureId,
                float textureWidth,
                float textureHeight,
                float rawAdvance,
                float advance,
                int visibleTop,
                int visibleBottom) {
            this.textureId = textureId;
            this.textureWidth = textureWidth;
            this.textureHeight = textureHeight;
            this.rawAdvance = rawAdvance;
            this.advance = advance;
            this.visibleTop = visibleTop;
            this.visibleBottom = visibleBottom;
        }

        private boolean hasVisiblePixels() {
            return this.visibleBottom > this.visibleTop;
        }
    }
}
