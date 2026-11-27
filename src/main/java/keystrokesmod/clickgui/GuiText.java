package keystrokesmod.clickgui;

import keystrokesmod.utility.font.FeralFontRenderer;

public final class GuiText {
    private GuiText() {}

    public static String fit(FeralFontRenderer renderer, String text, float width) {
        if (text == null || width <= 0f) {
            return "";
        }
        if (renderer.getStringWidth(text) <= width) {
            return text;
        }
        String suffix = "..";
        if (renderer.getStringWidth(suffix) > width) {
            return "";
        }
        int end = text.length();
        while (end > 0) {
            end = text.offsetByCodePoints(end, -1);
            if (end > 0 && text.charAt(end - 1) == '\u00a7') {
                end--;
            }
            String shortened = text.substring(0, end) + suffix;
            if (renderer.getStringWidth(shortened) <= width) {
                return shortened;
            }
        }
        return suffix;
    }
}
