package keystrokesmod.utility.font;

public interface FeralFontRenderer {
    int drawString(String var1, float var2, float var3, int var4, boolean var5);

    int drawGlyphString(String var1, float var2, float var3, FeralFontRenderer.GlyphColorProvider var4, boolean var5);

    default int drawString(String text, float x, float y, int color) {
        return this.drawString(text, x, y, color, false);
    }

    default int drawGlyphString(String text, float x, float y, FeralFontRenderer.GlyphColorProvider colorProvider) {
        return this.drawGlyphString(text, x, y, colorProvider, false);
    }

    default int drawStringWithShadow(String text, float x, float y, int color) {
        return this.drawString(text, x, y, color, true);
    }

    int getStringWidth(String var1);

    int getFontHeight();

    default int getLineHeight() {
        return this.getFontHeight();
    }

    default int getTextTopOffset() {
        return 0;
    }

    default int getTextBottomOffset() {
        return this.getFontHeight();
    }

    default void destroy() {}

    @FunctionalInterface
    interface GlyphColorProvider {
        int colorForGlyph(char var1, float var2, float var3, Integer var4);
    }
}
