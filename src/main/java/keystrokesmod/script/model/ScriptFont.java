package keystrokesmod.script.model;

import keystrokesmod.utility.font.FeralFontRenderer;
import keystrokesmod.utility.font.FontManager;

public final class ScriptFont {
    private final String name;
    private final float size;
    private final boolean pixelHeight;

    public ScriptFont(String name, double size) {
        this(name, size, false);
    }

    public ScriptFont(String name, double size, boolean pixelHeight) {
        if (Double.isFinite(size) && !(size < 1.0) && !(size > 256.0)) {
            this.name = name == null ? FontManager.getDefaultFontName() : name;
            this.size = (float) size;
            this.pixelHeight = pixelHeight;
        } else {
            throw new IllegalArgumentException("Font size must be finite and between 1 and 256");
        }
    }

    private FeralFontRenderer getRenderer() {
        return this.pixelHeight
                ? FontManager.getRendererForPixelHeight(this.name, this.size)
                : FontManager.getRenderer(this.name, this.size);
    }

    public String getName() {
        return this.name;
    }

    public float getSize() {
        return this.size;
    }

    public int drawString(String text, double x, double y, int color) {
        return this.drawString(text, x, y, color, false);
    }

    public int drawString(String text, double x, double y, int color, boolean shadow) {
        return this.getRenderer().drawString(text, (float) x, (float) y, color, shadow);
    }

    public int drawStringWithShadow(String text, double x, double y, int color) {
        return this.drawString(text, x, y, color, true);
    }

    public int getStringWidth(String text) {
        return this.getRenderer().getStringWidth(text);
    }

    public int getFontHeight() {
        return this.getRenderer().getFontHeight();
    }

    public int getLineHeight() {
        return this.getRenderer().getLineHeight();
    }

    public int getTextTopOffset() {
        return this.getRenderer().getTextTopOffset();
    }

    public int getTextBottomOffset() {
        return this.getRenderer().getTextBottomOffset();
    }
}
