package keystrokesmod.utility;

import java.nio.FloatBuffer;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.platform.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Draws Meower's cats (ears, eyes, nose, whiskers, fluffy tails, paws) out of plain GL shapes, so the cat GUIs
 * need no textures.
 *
 * <p>Every part is written in the coordinates of the design canvas mockups ("mock" units) and mapped onto the
 * screen through a {@link Space}, so the shapes here can be compared one to one with the approved designs.
 *
 * <p>All drawing uses raw GL calls inside {@code glPushAttrib}/{@code glPopAttrib}, never {@code GlStateManager},
 * so the state cache Minecraft keeps stays correct after a cat is drawn.
 */
public final class CatRenderer {
    public static final int PINK = 0xFFF29BB4;
    public static final int PUPIL = 0xFF0B0C0E;
    public static final int WHITE = 0xFFFFFFFF;
    private static final int WHISKER = 0xFFE8E8E8;
    private static final int MOUTH = 0xFFD9D9D9;

    /** Fur colours of the two designs. */
    public static final int WINDOW_CAT_FUR = 0xFF26282D;

    public static final int CATEGORY_CAT_FUR = 0xFF14161A;
    public static final int CONTENTS_CAT_FUR = 0xFF202329;
    public static final int CONTENTS_CAT_GOLD = 0xFFE7B95A;

    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
    /** How far each pupil sits toward the middle of the face, as a fraction of the eye's radius. */
    private static final float PUPIL_LOOK = 0.04F;

    private static float alphaMul = 1.0F;
    private static float pixelsPerUnit = 2.0F;
    private static int depth;

    private CatRenderer() {}

    /* ------------------------------------------------------------------ state */

    /** Multiplies every colour's alpha, so a whole cat can fade with its menu. Reset it to 1 afterwards. */
    public static void setAlpha(float multiplier) {
        alphaMul = clamp(multiplier, 0.0F, 1.0F);
    }

    public static void begin() {
        if (depth++ > 0) {
            return;
        }

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT
                | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_LINE_BIT
                | GL11.GL_CURRENT_BIT
                | GL11.GL_LIGHTING_BIT
                | GL11.GL_HINT_BIT);
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        pixelsPerUnit = measurePixelsPerUnit();
    }

    public static void end() {
        if (depth <= 0) {
            return;
        }

        if (--depth == 0) {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    /** Screen pixels per GUI unit, read from the current matrix, so thin lines keep a sensible width. */
    private static float measurePixelsPerUnit() {
        try {
            MATRIX.clear();
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MATRIX);
            float matrixScale = Math.abs(MATRIX.get(0));
            int scale = new Window(Minecraft.getInstance()).getScale();
            float value = matrixScale * scale;
            return value > 0.0F ? value : 2.0F;
        } catch (Exception ignored) {
            return 2.0F;
        }
    }

    /* ------------------------------------------------------------- animation */

    /** Eye height multiplier for a blink every {@code periodMs}: closes over the last few percent of the cycle. */
    public static float blink(long now, long periodMs, long offsetMs) {
        float t = ((now + offsetMs) % periodMs) / (float) periodMs;
        if (t < 0.92F) {
            return 1.0F;
        }

        if (t < 0.95F) {
            return lerp(1.0F, 0.1F, (t - 0.92F) / 0.03F);
        }

        return lerp(0.1F, 1.0F, (t - 0.95F) / 0.05F);
    }

    /** Smooth back-and-forth angle between {@code fromDeg} and {@code toDeg}. */
    public static float sway(long now, long periodMs, float fromDeg, float toDeg) {
        float t = (now % periodMs) / (float) periodMs;
        float wave = (1.0F - (float) Math.cos(t * Math.PI * 2.0)) / 2.0F;
        return fromDeg + (toDeg - fromDeg) * wave;
    }

    /**
     * Ear twitch angle {@code sinceMs} after the cursor arrived: out by {@code peakDeg}, back past zero, then rest.
     * The right ear starts a moment later and turns the other way.
     */
    public static float twitch(long sinceMs, boolean rightEar, float peakDeg) {
        long t = sinceMs - (rightEar ? 80L : 0L);
        if (t < 0L || t > 500L) {
            return 0.0F;
        }

        float p = t / 500.0F;
        float angle;
        if (p < 0.35F) {
            angle = lerp(0.0F, -peakDeg, ease(p / 0.35F));
        } else if (p < 0.7F) {
            angle = lerp(-peakDeg, peakDeg * 0.36F, ease((p - 0.35F) / 0.35F));
        } else {
            angle = lerp(peakDeg * 0.36F, 0.0F, ease((p - 0.7F) / 0.3F));
        }

        return rightEar ? -angle : angle;
    }

    /** Which animations a cat plays: ear mode (0 on hover, 1 always, 2 off), tail sway and blinking. */
    public static final class Look {
        public static final Look DEFAULT = new Look(0, true, true);
        final int earMode;
        final boolean animateTail;
        final boolean blink;

        public Look(int earMode, boolean animateTail, boolean blink) {
            this.earMode = earMode;
            this.animateTail = animateTail;
            this.blink = blink;
        }

        /** Ear angle: a twitch when hovered, a twitch every second or so when always shaking, else still. */
        float ear(long now, long hoverSinceMs, long offsetMs, boolean rightEar, float peakDeg) {
            switch (this.earMode) {
                case 1:
                    return twitch((now + offsetMs) % 1100L, rightEar, peakDeg);
                case 2:
                    return 0.0F;
                default:
                    return twitch(hoverSinceMs, rightEar, peakDeg);
            }
        }

        float tail(long now, long periodMs, float fromDeg, float toDeg) {
            return this.animateTail ? sway(now, periodMs, fromDeg, toDeg) : (fromDeg + toDeg) / 2.0F;
        }

        float blink(long now, long periodMs, long offsetMs) {
            return this.blink ? CatRenderer.blink(now, periodMs, offsetMs) : 1.0F;
        }
    }

    /* -------------------------------------------------------------- primitives */

    public static void fillEllipse(float cx, float cy, float rx, float ry, int color) {
        if (rx <= 0.0F || ry <= 0.0F) {
            return;
        }

        int n = segmentsFor(Math.max(rx, ry));
        color(color);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(cx, cy);
        for (int i = 0; i <= n; i++) {
            double a = Math.PI * 2.0 * i / n;
            GL11.glVertex2f(cx + (float) Math.cos(a) * rx, cy + (float) Math.sin(a) * ry);
        }
        GL11.glEnd();
        smoothOutline(ellipsePoints(cx, cy, rx, ry, n), true, color);
    }

    public static void fillCircle(float cx, float cy, float r, int color) {
        fillEllipse(cx, cy, r, r, color);
    }

    /** Fills a polygon that every point can be seen from its average point (true for all the cat shapes). */
    public static void fillPolygon(float[] xy, int color) {
        int size = xy.length / 2;
        if (size < 3) {
            return;
        }

        float cx = 0.0F;
        float cy = 0.0F;
        for (int i = 0; i < size; i++) {
            cx += xy[i * 2];
            cy += xy[i * 2 + 1];
        }
        cx /= size;
        cy /= size;

        color(color);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(cx, cy);
        for (int i = 0; i <= size; i++) {
            int j = i % size;
            GL11.glVertex2f(xy[j * 2], xy[j * 2 + 1]);
        }
        GL11.glEnd();
        smoothOutline(xy, true, color);
    }

    /** A line of {@code width} GUI units along the points, with round joints and ends. */
    public static void stroke(float[] xy, boolean closed, float width, int color) {
        int size = xy.length / 2;
        if (size < 2 || width <= 0.0F) {
            return;
        }

        float pixels = width * pixelsPerUnit;
        if (pixels <= 3.0F) {
            color(color);
            GL11.glEnable(GL11.GL_LINE_SMOOTH);
            GL11.glLineWidth(Math.max(1.0F, pixels));
            GL11.glBegin(closed ? GL11.GL_LINE_LOOP : GL11.GL_LINE_STRIP);
            for (int i = 0; i < size; i++) {
                GL11.glVertex2f(xy[i * 2], xy[i * 2 + 1]);
            }
            GL11.glEnd();
            GL11.glLineWidth(1.0F);
            GL11.glDisable(GL11.GL_LINE_SMOOTH);
            return;
        }

        float half = width / 2.0F;
        color(color);
        GL11.glBegin(GL11.GL_QUADS);
        int segments = closed ? size : size - 1;
        for (int i = 0; i < segments; i++) {
            int j = (i + 1) % size;
            float x0 = xy[i * 2];
            float y0 = xy[i * 2 + 1];
            float x1 = xy[j * 2];
            float y1 = xy[j * 2 + 1];
            float dx = x1 - x0;
            float dy = y1 - y0;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 1.0E-4F) {
                continue;
            }
            float nx = -dy / len * half;
            float ny = dx / len * half;
            GL11.glVertex2f(x0 + nx, y0 + ny);
            GL11.glVertex2f(x1 + nx, y1 + ny);
            GL11.glVertex2f(x1 - nx, y1 - ny);
            GL11.glVertex2f(x0 - nx, y0 - ny);
        }
        GL11.glEnd();

        for (int i = 0; i < size; i++) {
            fillCircleNoSmooth(xy[i * 2], xy[i * 2 + 1], half, color);
        }
    }

    private static void fillCircleNoSmooth(float cx, float cy, float r, int color) {
        int n = segmentsFor(r);
        color(color);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(cx, cy);
        for (int i = 0; i <= n; i++) {
            double a = Math.PI * 2.0 * i / n;
            GL11.glVertex2f(cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r);
        }
        GL11.glEnd();
    }

    /** Draws a one-pixel smoothed line over a filled shape's edge, so it isn't jagged. */
    private static void smoothOutline(float[] xy, boolean closed, int color) {
        color(color);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(1.0F);
        GL11.glBegin(closed ? GL11.GL_LINE_LOOP : GL11.GL_LINE_STRIP);
        for (int i = 0; i < xy.length / 2; i++) {
            GL11.glVertex2f(xy[i * 2], xy[i * 2 + 1]);
        }
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
    }

    private static float[] ellipsePoints(float cx, float cy, float rx, float ry, int n) {
        float[] out = new float[n * 2];
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2.0 * i / n;
            out[i * 2] = cx + (float) Math.cos(a) * rx;
            out[i * 2 + 1] = cy + (float) Math.sin(a) * ry;
        }
        return out;
    }

    private static int segmentsFor(float radius) {
        return Math.max(12, Math.min(64, (int) (radius * pixelsPerUnit * 1.5F)));
    }

    private static void rotateAround(float px, float py, float degrees) {
        GL11.glTranslatef(px, py, 0.0F);
        GL11.glRotatef(degrees, 0.0F, 0.0F, 1.0F);
        GL11.glTranslatef(-px, -py, 0.0F);
    }

    private static void color(int argb) {
        float a = (argb >>> 24 & 0xFF) / 255.0F * alphaMul;
        GL11.glColor4f((argb >> 16 & 0xFF) / 255.0F, (argb >> 8 & 0xFF) / 255.0F, (argb & 0xFF) / 255.0F, a);
    }

    /** {@code color} with its alpha multiplied by {@code opacity}. */
    public static int fade(int color, float opacity) {
        int a = Math.round((color >>> 24 & 0xFF) * clamp(opacity, 0.0F, 1.0F));
        return a << 24 | color & 0xFFFFFF;
    }

    /* ----------------------------------------------------------- shared parts */

    /**
     * The round staring eye from the user's sketch: a round eye, a tall dark pupil 80% of its width and its full
     * height, nudged toward the face's middle by {@link #PUPIL_LOOK}, and two white highlight dots.
     *
     * @param lookRight true for the cat's left eye (on screen), whose pupil sits toward the middle on its right
     */
    public static void staringEye(float cx, float cy, float r, int rimColor, boolean lookRight, float blink) {
        GL11.glPushMatrix();
        GL11.glTranslatef(cx, cy, 0.0F);
        GL11.glScalef(1.0F, Math.max(0.05F, blink), 1.0F);
        GL11.glTranslatef(-cx, -cy, 0.0F);
        float pupilX = cx + (lookRight ? 1.0F : -1.0F) * r * PUPIL_LOOK;
        fillCircle(cx, cy, r, rimColor);
        fillEllipse(pupilX, cy, r * 0.8F, r, PUPIL);
        if (blink > 0.5F) {
            fillCircle(pupilX + r * 0.25F, cy - r * 0.385F, r * 0.25F, WHITE);
            fillCircle(pupilX - r * 0.26F, cy + r * 0.39F, r * 0.123F, WHITE);
        }
        GL11.glPopMatrix();
    }

    /** A small × for closing a panel. */
    public static void closeCross(float cx, float cy, float size, int color) {
        float h = size / 2.0F;
        stroke(new float[] {cx - h, cy - h, cx + h, cy + h}, false, size * 0.18F, color);
        stroke(new float[] {cx + h, cy - h, cx - h, cy + h}, false, size * 0.18F, color);
    }

    /** A paw print icon: one pad and four toes. */
    public static void pawIcon(float cx, float cy, float size, int color) {
        float u = size / 24.0F;
        float ox = cx - 12.0F * u;
        float oy = cy - 12.0F * u;
        fillEllipse(ox + 12.0F * u, oy + 16.0F * u, 6.0F * u, 5.0F * u, color);
        fillCircle(ox + 5.0F * u, oy + 9.0F * u, 2.6F * u, color);
        fillCircle(ox + 10.0F * u, oy + 5.5F * u, 2.6F * u, color);
        fillCircle(ox + 15.0F * u, oy + 5.5F * u, 2.6F * u, color);
        fillCircle(ox + 20.0F * u, oy + 9.0F * u, 2.6F * u, color);
    }

    /**
     * A fluffy tail along {@code path}: overlapping "beads" in the edge colour make a scalloped outline, a second
     * row of beads and a solid core fill it in.
     */
    private static void fluffyTail(
            Space sp, float[] mockPath, float edgeR, float beadR, float coreR, float spacing, int edge, int fill) {
        float[] pts = resample(sp.map(mockPath), sp.len(spacing));
        float[] core = resample(sp.map(mockPath), Math.max(0.5F, sp.len(coreR) * 0.5F));
        for (int i = 0; i < pts.length / 2; i++) {
            fillCircleNoSmooth(pts[i * 2], pts[i * 2 + 1], sp.len(edgeR), edge);
        }
        for (int i = 0; i < pts.length / 2; i++) {
            fillCircleNoSmooth(pts[i * 2], pts[i * 2 + 1], sp.len(beadR), fill);
        }
        for (int i = 0; i < core.length / 2; i++) {
            fillCircleNoSmooth(core[i * 2], core[i * 2 + 1], sp.len(coreR), fill);
        }
    }

    /** Points spaced {@code step} apart along a polyline. */
    private static float[] resample(float[] xy, float step) {
        int size = xy.length / 2;
        if (size < 2 || step <= 0.0F) {
            return xy;
        }

        float[] out = new float[64];
        int n = 0;
        out[n++] = xy[0];
        out[n++] = xy[1];
        float carried = 0.0F;
        for (int i = 0; i < size - 1; i++) {
            float x0 = xy[i * 2];
            float y0 = xy[i * 2 + 1];
            float dx = xy[i * 2 + 2] - x0;
            float dy = xy[i * 2 + 3] - y0;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            float d = step - carried;
            while (d <= len) {
                if (n + 2 > out.length) {
                    out = Arrays.copyOf(out, out.length * 2);
                }
                out[n++] = x0 + dx * d / len;
                out[n++] = y0 + dy * d / len;
                d += step;
            }
            carried = len - (d - step);
        }
        if (n + 2 > out.length) {
            out = Arrays.copyOf(out, out.length + 2);
        }
        out[n++] = xy[xy.length - 2];
        out[n++] = xy[xy.length - 1];
        return Arrays.copyOf(out, n);
    }

    /** One ear: fur with an outline, a pink inside, and white fluff lines. Twitches around {@code pivot}. */
    private static void ear(
            Space sp,
            float[] outline,
            float[] inner,
            float[][] fluff,
            float pivotX,
            float pivotY,
            float angle,
            int fur,
            int outlineColor,
            float outlineWidth,
            float fluffWidth) {
        GL11.glPushMatrix();
        rotateAround(sp.x(pivotX), sp.y(pivotY), angle);
        float[] o = sp.map(outline);
        fillPolygon(o, fur);
        stroke(o, true, sp.len(outlineWidth), outlineColor);
        fillPolygon(sp.map(inner), fade(PINK, 0.75F));
        for (float[] line : fluff) {
            stroke(sp.map(line), false, sp.len(fluffWidth), fade(WHITE, 0.85F));
        }
        GL11.glPopMatrix();
    }

    /* ------------------------------------------------------ concept 2: window */

    // Head artboard: 240x170, head centre (120, 120). Mock units are 1.5x GUI units at window scale 1.
    private static final float[] WIN_EAR = new Path()
            .move(40, 92)
            .line(52, 18)
            .line(47, 6)
            .line(55, 11)
            .quad(58, -2, 63, 9)
            .line(70, 4)
            .line(68, 16)
            .line(112, 60)
            .points();
    private static final float[] WIN_EAR_INNER =
            new Path().move(56, 70).line(60, 30).line(92, 62).points();
    private static final float[][] WIN_EAR_FLUFF = {
        new Path().move(62, 66).quad(62, 50, 59, 38).points(),
        new Path().move(70, 66).quad(72, 54, 72, 44).points(),
        new Path().move(78, 66).quad(82, 58, 86, 52).points()
    };
    private static final float[][] WIN_WHISKERS = {{70, 128, 6, 118}, {70, 134, 4, 136}, {72, 140, 10, 154}};
    private static final float[] WIN_NOSE = {114, 128, 126, 128, 120, 135};
    private static final float[][] WIN_MOUTH = {
        new Path().move(120, 135).quad(114, 143, 106, 139).points(),
        new Path().move(120, 135).quad(126, 143, 134, 139).points()
    };
    // Tail artboard 150x230, starts at (6, 20) just inside the window's right edge.
    private static final float[] WIN_TAIL = new Path()
            .move(6, 20)
            .cubic(70, 10, 120, 60, 110, 130)
            .cubic(102, 190, 50, 200, 40, 170)
            .cubic(32, 146, 60, 140, 70, 152)
            .points();
    // Front paw artboard 90x56: an arch whose bottom hides over the window.
    private static final float[] WIN_FRONT_PAW = new Path()
            .move(6, 56)
            .line(6, 26)
            .quad(6, 4, 45, 4)
            .quad(84, 4, 84, 26)
            .line(84, 56)
            .points();
    private static final float[][] WIN_TOE_LINES = {{30, 20, 30, 34}, {45, 18, 45, 34}, {60, 20, 60, 34}};

    private static long windowHeadHoverStart = -1L;

    private static float windowK(float scale) {
        return scale / 1.5F;
    }

    private static Space windowHeadSpace(float winX, float winY, float winW, float scale) {
        float k = windowK(scale);
        float headCx = winX + winW / 2.0F;
        float headCy = winY - 16.0F * k;
        return new Space(headCx - 120.0F * k, headCy - 120.0F * k, k);
    }

    /** True when the cursor is over the window cat's head (its ears twitch while it is). */
    public static boolean isOverWindowCatHead(float mx, float my, float winX, float winY, float winW, float scale) {
        Space sp = windowHeadSpace(winX, winY, winW, scale);
        float dx = (mx - sp.x(120)) / sp.len(96);
        float dy = (my - sp.y(120)) / sp.len(78);
        boolean overHead = dx * dx + dy * dy <= 1.0F && my <= winY;
        boolean overEars = mx >= sp.x(40) && mx <= sp.x(200) && my >= sp.y(0) && my <= sp.y(92);
        return overHead || overEars;
    }

    /**
     * Concept 2, the parts behind the window: the tail wrapping the right side and the head looking over the top.
     * Draw the window right after this.
     */
    public static void drawWindowCatBehind(
            float winX, float winY, float winW, float winH, float scale, int accent, boolean headHovered) {
        long now = System.currentTimeMillis();
        float k = windowK(scale);
        begin();
        try {
            // Tail, anchored just inside the right edge a little below the middle.
            float anchorX = winX + winW - 4.0F * k;
            float anchorY = winY + winH * 0.537F;
            Space tail = new Space(anchorX - 6.0F * k, anchorY - 20.0F * k, k);
            GL11.glPushMatrix();
            rotateAround(anchorX, anchorY, sway(now, 3000L, -4.0F, 7.0F));
            fluffyTail(tail, WIN_TAIL, 15.5F, 13.0F, 11.0F, 9.0F, accent, WINDOW_CAT_FUR);
            GL11.glPopMatrix();

            // Head.
            if (headHovered) {
                if (windowHeadHoverStart < 0L) {
                    windowHeadHoverStart = now;
                }
            } else {
                windowHeadHoverStart = -1L;
            }
            long since = windowHeadHoverStart < 0L ? Long.MAX_VALUE : now - windowHeadHoverStart;
            Space head = windowHeadSpace(winX, winY, winW, scale);
            Space mirrored = head.mirrored(120);
            ear(
                    head,
                    WIN_EAR,
                    WIN_EAR_INNER,
                    WIN_EAR_FLUFF,
                    76,
                    92,
                    twitch(since, false, 12.0F),
                    WINDOW_CAT_FUR,
                    accent,
                    2.0F,
                    1.6F);
            ear(
                    mirrored,
                    WIN_EAR,
                    WIN_EAR_INNER,
                    WIN_EAR_FLUFF,
                    76,
                    92,
                    twitch(since, true, 12.0F),
                    WINDOW_CAT_FUR,
                    accent,
                    2.0F,
                    1.6F);
            fillEllipse(head.x(120), head.y(120), head.len(96) + head.len(1.0F), head.len(78) + head.len(1.0F), accent);
            fillEllipse(head.x(120), head.y(120), head.len(95), head.len(77), WINDOW_CAT_FUR);

            float blink = blink(now, 4500L, 0L);
            slitEye(head, 86, 110, 90, 104, accent, blink);
            slitEye(head, 154, 110, 158, 104, accent, blink);
            fillPolygon(head.map(WIN_NOSE), PINK);
            for (float[] mouth : WIN_MOUTH) {
                stroke(head.map(mouth), false, head.len(2.0F), MOUTH);
            }
            for (float[] w : WIN_WHISKERS) {
                stroke(head.map(w), false, head.len(1.6F), fade(WHISKER, 0.85F));
                stroke(mirrored.map(w), false, head.len(1.6F), fade(WHISKER, 0.85F));
            }
        } finally {
            end();
        }
    }

    /** Concept 2, the parts in front of the window: front paws gripping the top edge, back paws under it. */
    public static void drawWindowCatFront(float winX, float winY, float winW, float winH, float scale, int accent) {
        float k = windowK(scale);
        float centerX = winX + winW / 2.0F;
        begin();
        try {
            frontPaw(new Space(centerX - 155.0F * k, winY - 24.0F * k, k), accent);
            frontPaw(new Space(centerX + 65.0F * k, winY - 24.0F * k, k), accent);
            backPaw(new Space(winX + 30.0F * k, winY + winH - 14.0F * k, k), accent);
            backPaw(new Space(winX + winW - 110.0F * k, winY + winH - 14.0F * k, k), accent);
        } finally {
            end();
        }
    }

    private static void slitEye(Space sp, float cx, float cy, float dotX, float dotY, int iris, float blink) {
        GL11.glPushMatrix();
        GL11.glTranslatef(sp.x(cx), sp.y(cy), 0.0F);
        GL11.glScalef(1.0F, Math.max(0.05F, blink), 1.0F);
        GL11.glTranslatef(-sp.x(cx), -sp.y(cy), 0.0F);
        fillEllipse(sp.x(cx), sp.y(cy), sp.len(13), sp.len(16), iris);
        fillEllipse(sp.x(cx), sp.y(cy), sp.len(4), sp.len(12), PUPIL);
        if (blink > 0.5F) {
            fillCircle(sp.x(dotX), sp.y(dotY), sp.len(3), WHITE);
        }
        GL11.glPopMatrix();
    }

    private static void frontPaw(Space sp, int accent) {
        float[] arch = sp.map(WIN_FRONT_PAW);
        fillPolygon(arch, WINDOW_CAT_FUR);
        stroke(arch, false, sp.len(2.0F), accent);
        for (float[] toe : WIN_TOE_LINES) {
            stroke(sp.map(toe), false, sp.len(2.0F), 0xFF3A3D44);
        }
    }

    private static void backPaw(Space sp, int accent) {
        fillEllipse(sp.x(40), sp.y(22), sp.len(37), sp.len(19), accent);
        fillEllipse(sp.x(40), sp.y(22), sp.len(36), sp.len(18), WINDOW_CAT_FUR);
        int bean = fade(PINK, 0.8F);
        fillEllipse(sp.x(40), sp.y(27), sp.len(10), sp.len(7), bean);
        fillCircle(sp.x(22), sp.y(16), sp.len(5), bean);
        fillCircle(sp.x(34), sp.y(11), sp.len(5), bean);
        fillCircle(sp.x(46), sp.y(11), sp.len(5), bean);
        fillCircle(sp.x(58), sp.y(16), sp.len(5), bean);
    }

    /* -------------------------------------------- concept 1b: category cats */

    // Panel artboard 196 wide; the ear artboard starts 36 above the panel top.
    private static final float[] CAT_EAR = new Path()
            .move(14, 44)
            .line(28, 10)
            .line(25, 3)
            .line(31, 6)
            .quad(34, -2, 38, 5)
            .line(42, 2)
            .line(41, 10)
            .line(68, 44)
            .points();
    private static final float[] CAT_EAR_INNER = {27, 40, 32, 18, 50, 40};
    private static final float[][] CAT_EAR_FLUFF = {
        new Path().move(33, 40).quad(33, 32, 31, 25).points(),
        new Path().move(37, 40).quad(39, 33, 39, 28).points(),
        new Path().move(41, 40).quad(44, 35, 46, 32).points()
    };
    private static final float[][] CAT_WHISKERS = {
        new Path().move(34, 7).quad(18, 3, 2, 2).points(),
        new Path().move(34, 11).quad(18, 11, 1, 12).points(),
        new Path().move(34, 15).quad(18, 19, 3, 22).points()
    };
    private static final float[] CAT_TAIL = new Path()
            .move(10, 0)
            .cubic(10, 26, 48, 20, 48, 46)
            .cubic(48, 64, 30, 72, 22, 58)
            .points();

    /** Mock-to-GUI scale for a category cat panel {@code width} GUI units wide. */
    private static float categoryK(float width) {
        return width / 196.0F;
    }

    /**
     * Category cat parts behind the panel: ears on top and the tail hanging under the bottom. Draw the panel
     * after this so it covers the ear bases and the tail root.
     */
    public static void drawCategoryCatBehind(
            float x, float y, float width, float bottom, int fur, int body, long hoverSinceMs, Look look) {
        long now = System.currentTimeMillis();
        float k = categoryK(width);
        begin();
        try {
            // The ears sit so their inner base corners line up with the eye centres.
            Space left = new Space(x + 12.0F * k, y - 36.0F * k, k);
            Space right = new Space(x - 12.0F * k, y - 36.0F * k, k).mirrored(98);
            ear(
                    left,
                    CAT_EAR,
                    CAT_EAR_INNER,
                    CAT_EAR_FLUFF,
                    41,
                    44,
                    look.ear(now, hoverSinceMs, (long) (x * 7.0F), false, 14.0F),
                    body,
                    fur,
                    2.0F,
                    1.2F);
            ear(
                    right,
                    CAT_EAR,
                    CAT_EAR_INNER,
                    CAT_EAR_FLUFF,
                    41,
                    44,
                    look.ear(now, hoverSinceMs, (long) (x * 7.0F), true, 14.0F),
                    body,
                    fur,
                    2.0F,
                    1.2F);

            Space tail = new Space(x + 120.0F * k, bottom - 10.0F * k, k);
            GL11.glPushMatrix();
            rotateAround(tail.x(10), tail.y(0), look.tail(now + (long) (x * 7.0F), 2600L, -7.0F, 9.0F));
            fluffyTail(tail, CAT_TAIL, 10.0F, 7.5F, 6.0F, 7.0F, fur, body);
            GL11.glPopMatrix();
        } finally {
            end();
        }
    }

    /**
     * Category cat face, drawn over the panel's header: two staring eyes centred between the ears, a nose under
     * them and whiskers out of both sides. Returns the y where the category name should start.
     */
    public static float drawCategoryCatFace(float x, float y, float width, int fur, Look look) {
        long now = System.currentTimeMillis();
        float k = categoryK(width);
        float centerX = x + width / 2.0F;
        float eyeR = Math.max(3.0F, 6.5F * k);
        float eyeY = y + 3.0F + eyeR;
        float eyeGap = 18.0F * k;
        begin();
        try {
            float blink = look.blink(now, 5000L, (long) (x * 13.0F));
            staringEye(centerX - eyeGap, eyeY, eyeR, WHITE, true, blink);
            staringEye(centerX + eyeGap, eyeY, eyeR, WHITE, false, blink);
            float noseY = eyeY + eyeR * 0.55F;
            float noseW = Math.max(2.4F, 6.0F * k);
            fillPolygon(
                    new float[] {
                        centerX - noseW / 2.0F, noseY, centerX + noseW / 2.0F, noseY, centerX, noseY + noseW * 0.66F
                    },
                    PINK);

            Space leftWhiskers = new Space(x - 30.0F * k, y + 16.0F * k, k);
            Space rightWhiskers = new Space(x + width - 4.0F * k, y + 16.0F * k, k).mirrored(17);
            for (float[] w : CAT_WHISKERS) {
                stroke(leftWhiskers.map(w), false, Math.max(0.5F, 1.4F * k), fade(WHISKER, 0.8F));
                stroke(rightWhiskers.map(w), false, Math.max(0.5F, 1.4F * k), fade(WHISKER, 0.8F));
            }
        } finally {
            end();
        }
        return eyeY + eyeR + 1.0F;
    }

    /** Panel body of a cat: a filled rounded box with an outline in the cat's colour. */
    public static void drawCatPanel(float x, float y, float x2, float y2, float radius, int fill, int outline) {
        begin();
        try {
            float[] box = roundedBox(x, y, x2, y2, radius, radius);
            fillPolygon(box, fill);
            stroke(box, true, 1.0F, outline);
        } finally {
            end();
        }
    }

    /* ----------------------------------------------- concept 1b: contents cat */

    // Head artboard 210x112; the panel starts at its y = 104.
    private static final float[] CONTENTS_EAR = new Path()
            .move(24, 92)
            .line(40, 22)
            .line(36, 12)
            .line(43, 16)
            .quad(47, 4, 52, 14)
            .line(58, 10)
            .line(56, 22)
            .line(98, 68)
            .points();
    private static final float[] CONTENTS_EAR_INNER = {42, 74, 48, 36, 78, 68};
    private static final float[][] CONTENTS_EAR_FLUFF = {
        new Path().move(50, 72).quad(50, 58, 47, 46).points(),
        new Path().move(56, 72).quad(58, 60, 58, 50).points(),
        new Path().move(62, 72).quad(66, 64, 69, 58).points()
    };
    private static final float[] CONTENTS_DOME = new Path()
            .move(1, 112)
            .line(1, 98)
            .quad(1, 54, 105, 54)
            .quad(209, 54, 209, 98)
            .line(209, 112)
            .points();
    private static final float[] CONTENTS_NOSE = {100, 98, 110, 98, 105, 104};
    private static final float[][] CONTENTS_MOUTH = {
        new Path().move(105, 104).quad(101, 110, 95, 107).points(),
        new Path().move(105, 104).quad(109, 110, 115, 107).points()
    };
    private static final float[][] CONTENTS_WHISKERS = {
        new Path().move(58, 96).quad(30, 90, -36, 86).points(),
        new Path().move(58, 101).quad(30, 101, -38, 104).points(),
        new Path().move(60, 106).quad(30, 112, -34, 122).points()
    };
    private static final float[] CONTENTS_TAIL = new Path()
            .move(10, 0)
            .cubic(10, 28, 50, 22, 50, 50)
            .cubic(50, 68, 32, 76, 24, 64)
            .points();

    private static float contentsK(float width) {
        return width / 210.0F;
    }

    /** How far the contents cat's head and ears reach above its panel, in GUI units. */
    public static float contentsHeadHeight(float width) {
        return 104.0F * contentsK(width);
    }

    /** True when the cursor is over the contents cat's head dome (it can be dragged by it). */
    public static boolean isOverContentsHead(float mx, float my, float x, float y, float width) {
        float k = contentsK(width);
        return mx >= x && mx <= x + width && my >= y - 50.0F * k && my <= y;
    }

    /** How far a dome-headed cat {@code width} wide reaches above its panel (the dome, ears not counted). */
    public static float domeHeight(float width) {
        return 50.0F * contentsK(width);
    }

    /**
     * The contents cat: panel body, then the head on top of it, then the tail. The panel's rows are drawn by the
     * caller afterwards.
     */
    public static void drawContentsCat(float x, float y, float width, float bottom, int color, long hoverSinceMs) {
        drawDomeCat(x, y, width, bottom, CONTENTS_CAT_FUR, color, color, 0L, hoverSinceMs, Look.DEFAULT);
    }

    /**
     * The rounded category cat: the contents cat's shape (dome head with eyes, nose, mouth and whiskers on top of
     * the panel) in the category's colour, with white eyes.
     */
    public static void drawRoundedCategoryCat(
            float x, float y, float width, float bottom, int fur, int body, long hoverSinceMs, Look look) {
        drawDomeCat(x, y, width, bottom, body, fur, WHITE, (long) (x * 7.0F), hoverSinceMs, look);
    }

    private static void drawDomeCat(
            float x,
            float y,
            float width,
            float bottom,
            int fill,
            int accent,
            int eyeColor,
            long offsetMs,
            long hoverSinceMs,
            Look look) {
        long now = System.currentTimeMillis();
        float k = contentsK(width);
        Space head = new Space(x, y - 104.0F * k, k);
        Space mirrored = head.mirrored(105);
        begin();
        try {
            // Tail first so the panel covers its root.
            Space tail = new Space(x + 124.0F * k, bottom - 10.0F * k, k);
            GL11.glPushMatrix();
            rotateAround(tail.x(10), tail.y(0), look.tail(now + offsetMs, 2600L, -7.0F, 9.0F));
            fluffyTail(tail, CONTENTS_TAIL, 11.5F, 9.0F, 7.0F, 8.0F, accent, fill);
            GL11.glPopMatrix();

            // Panel: sides and a rounded bottom, open at the top where the head joins.
            float radius = 22.0F * k;
            float[] body = roundedBox(x, y, x + width, bottom, 0.0F, radius);
            fillPolygon(body, fill);
            stroke(openTop(body), false, head.len(2.0F), accent);

            ear(
                    head,
                    CONTENTS_EAR,
                    CONTENTS_EAR_INNER,
                    CONTENTS_EAR_FLUFF,
                    61,
                    92,
                    look.ear(now, hoverSinceMs, offsetMs, false, 14.0F),
                    fill,
                    accent,
                    2.0F,
                    1.4F);
            ear(
                    mirrored,
                    CONTENTS_EAR,
                    CONTENTS_EAR_INNER,
                    CONTENTS_EAR_FLUFF,
                    61,
                    92,
                    look.ear(now, hoverSinceMs, offsetMs, true, 14.0F),
                    fill,
                    accent,
                    2.0F,
                    1.4F);
            float[] dome = head.map(CONTENTS_DOME);
            fillPolygon(dome, fill);
            stroke(dome, false, head.len(2.0F), accent);
            // Cover the seam between the dome and the panel.
            fillPolygon(
                    new float[] {
                        head.x(2),
                        head.y(103),
                        head.x(208),
                        head.y(103),
                        head.x(208),
                        head.y(112),
                        head.x(2),
                        head.y(112)
                    },
                    fill);

            float blink = look.blink(now, 5000L, offsetMs * 2L);
            staringEye(head.x(78), head.y(84), head.len(13), eyeColor, true, blink);
            staringEye(head.x(132), head.y(84), head.len(13), eyeColor, false, blink);
            fillPolygon(head.map(CONTENTS_NOSE), PINK);
            for (float[] mouth : CONTENTS_MOUTH) {
                stroke(head.map(mouth), false, head.len(1.6F), MOUTH);
            }
            for (float[] w : CONTENTS_WHISKERS) {
                stroke(head.map(w), false, head.len(1.4F), fade(WHISKER, 0.8F));
                stroke(mirrored.map(w), false, head.len(1.4F), fade(WHISKER, 0.8F));
            }
        } finally {
            end();
        }
    }

    /** A box with separate top and bottom corner radii, as polygon points going round clockwise. */
    private static float[] roundedBox(float x, float y, float x2, float y2, float topR, float bottomR) {
        float[] out = new float[160];
        int n = 0;
        int steps = 8;
        // top-left corner
        n = arc(out, n, x + topR, y + topR, topR, 180, 270, steps);
        n = arc(out, n, x2 - topR, y + topR, topR, 270, 360, steps);
        n = arc(out, n, x2 - bottomR, y2 - bottomR, bottomR, 0, 90, steps);
        n = arc(out, n, x + bottomR, y2 - bottomR, bottomR, 90, 180, steps);
        return Arrays.copyOf(out, n);
    }

    private static int arc(float[] out, int n, float cx, float cy, float r, float fromDeg, float toDeg, int steps) {
        if (r <= 0.0F) {
            out[n++] = cx;
            out[n++] = cy;
            return n;
        }

        for (int i = 0; i <= steps; i++) {
            double a = Math.toRadians(fromDeg + (toDeg - fromDeg) * i / steps);
            out[n++] = cx + (float) Math.cos(a) * r;
            out[n++] = cy + (float) Math.sin(a) * r;
        }
        return n;
    }

    /** The outline of a {@link #roundedBox} with square top corners, minus its top edge. */
    private static float[] openTop(float[] box) {
        // Box order: top-left, top-right, bottom-right arc, bottom-left arc. Start at top-right, end at top-left.
        int size = box.length / 2;
        float[] out = new float[box.length];
        int n = 0;
        for (int i = 1; i < size; i++) {
            out[n++] = box[i * 2];
            out[n++] = box[i * 2 + 1];
        }
        out[n++] = box[0];
        out[n++] = box[1];
        return out;
    }

    /* ---------------------------------------------------------------- helpers */

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * clamp(t, 0.0F, 1.0F);
    }

    private static float ease(float t) {
        t = clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }

    /** Maps mock coordinates to GUI coordinates: {@code gui = origin + mock * k}, optionally mirrored. */
    public static final class Space {
        private final float ox;
        private final float oy;
        private final float k;
        private final boolean mirror;
        private final float axis;

        public Space(float ox, float oy, float k) {
            this(ox, oy, k, false, 0.0F);
        }

        private Space(float ox, float oy, float k, boolean mirror, float axis) {
            this.ox = ox;
            this.oy = oy;
            this.k = k;
            this.mirror = mirror;
            this.axis = axis;
        }

        /** The same space flipped left-right around mock x = {@code axisX}. */
        public Space mirrored(float axisX) {
            return new Space(this.ox, this.oy, this.k, true, axisX);
        }

        public float x(float mx) {
            return this.ox + (this.mirror ? 2.0F * this.axis - mx : mx) * this.k;
        }

        public float y(float my) {
            return this.oy + my * this.k;
        }

        public float len(float v) {
            return v * this.k;
        }

        public float[] map(float[] mock) {
            float[] out = new float[mock.length];
            for (int i = 0; i + 1 < mock.length; i += 2) {
                out[i] = this.x(mock[i]);
                out[i + 1] = this.y(mock[i + 1]);
            }
            return out;
        }
    }

    /** Builds a polyline from SVG-style commands, flattening curves. */
    public static final class Path {
        private static final int CURVE_STEPS = 12;
        private float[] pts = new float[32];
        private int n;
        private float lastX;
        private float lastY;

        public Path move(float x, float y) {
            return this.line(x, y);
        }

        public Path line(float x, float y) {
            this.add(x, y);
            return this;
        }

        public Path quad(float qx, float qy, float x, float y) {
            float sx = this.lastX;
            float sy = this.lastY;
            for (int i = 1; i <= CURVE_STEPS; i++) {
                float t = i / (float) CURVE_STEPS;
                float u = 1.0F - t;
                this.add(u * u * sx + 2.0F * u * t * qx + t * t * x, u * u * sy + 2.0F * u * t * qy + t * t * y);
            }
            return this;
        }

        public Path cubic(float c1x, float c1y, float c2x, float c2y, float x, float y) {
            float sx = this.lastX;
            float sy = this.lastY;
            for (int i = 1; i <= CURVE_STEPS * 2; i++) {
                float t = i / (float) (CURVE_STEPS * 2);
                float u = 1.0F - t;
                float a = u * u * u;
                float b = 3.0F * u * u * t;
                float c = 3.0F * u * t * t;
                float d = t * t * t;
                this.add(a * sx + b * c1x + c * c2x + d * x, a * sy + b * c1y + c * c2y + d * y);
            }
            return this;
        }

        public float[] points() {
            return Arrays.copyOf(this.pts, this.n);
        }

        private void add(float x, float y) {
            if (this.n + 2 > this.pts.length) {
                this.pts = Arrays.copyOf(this.pts, this.pts.length * 2);
            }
            this.pts[this.n++] = x;
            this.pts[this.n++] = y;
            this.lastX = x;
            this.lastY = y;
        }
    }
}
