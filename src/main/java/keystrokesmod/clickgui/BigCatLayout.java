package keystrokesmod.clickgui;

/** Window layout in Minecraft GUI units; decoration never pushes controls off-screen. */
final class BigCatLayout {
    static final float MARGIN = 4f;
    private static final float CAT_HEAD = 94f;
    private static final float CAT_FEET = 21f;

    private BigCatLayout() {}

    static float fit(float preferred, float viewport, float pop) {
        return Math.min(preferred * pop, Math.max(0f, viewport - 2f * MARGIN));
    }

    static float controlsScale(float preferred, float width, float height) {
        return Math.min(preferred, Math.min(width / 290f, height / 202f));
    }

    static float centered(float viewport, float size) {
        return (viewport - size) / 2f;
    }

    static float centeredY(float viewport, float height, float scale) {
        float room = Math.max(0f, viewport - height - 2f * MARGIN);
        return centered(viewport, height) + (CAT_HEAD - CAT_FEET) / 2f * Math.min(scale, room / (CAT_HEAD + CAT_FEET));
    }

    static float position(float desired, float viewport, float size) {
        return Math.max(MARGIN, Math.min(desired, viewport - size - MARGIN));
    }

    static float topCatScale(float scale, float top) {
        return Math.min(scale, top / CAT_HEAD);
    }

    static float tailScale(float scale, float right, float above, float below) {
        return Math.min(scale, Math.min(right / 86f, Math.min(above / 22f, below / 136f)));
    }

    static float backPawScale(float scale, float bottom) {
        return Math.min(scale, bottom / CAT_FEET);
    }

    static double resizedValue(float requestedSize, float available, double min, double max, double step) {
        double limit = Math.min(max, Math.floor(available / step) * step);
        return Math.max(min, Math.min(limit, Math.round(requestedSize / step) * step));
    }
}
