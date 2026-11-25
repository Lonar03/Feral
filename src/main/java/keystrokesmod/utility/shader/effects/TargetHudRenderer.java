package keystrokesmod.utility.shader.effects;

import java.awt.Color;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.LivingEntity;
import org.lwjgl.opengl.GL11;

public final class TargetHudRenderer {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final boolean ORNITHE = TargetHudRenderer.class.getResource("/feral.ornithe.accesswidener") != null;
    private static final int TEXT = new Color(220, 220, 220, 255).getRGB();
    public static ButtonSetting showDifference;
    public static ButtonSetting showHealth;
    public static ButtonSetting showWinLoss;
    public static ButtonSetting traditionalHealthColor;
    public static TargetHudRenderer.Mode mode = TargetHudRenderer.Mode.Modern;
    public static Theme theme = Theme.Default;
    public static int offsetX = 70;
    public static int offsetY = 40;
    private static double shown;
    private static double target = -1.0;
    private static double from = -1.0;
    private static long changedAt = -1L;

    public static String text(String name, float health, double ratio) {
        StringBuilder sb = new StringBuilder(name);
        if (showHealth.isToggled()) {
            sb.append(' ').append(Strings.health(ratio, health));
        }

        float own = Strings.health((LivingEntity) mc.player);
        if (showDifference.isToggled()) {
            sb.append(' ').append(Strings.healthDifference(own - health, !showHealth.isToggled()));
        }

        if (showWinLoss.isToggled()) {
            boolean win = ratio <= own / mc.player.getMaxHealth();
            sb.append(' ').append('§').append(win ? "aW" : "cL");
        }

        String s = sb.toString();
        int n = s.length();
        if (n < 14) {
            int pad = (16 - n + 1) / 2;
            String sp = "          ".substring(0, pad);
            s = sp + s + sp;
        }

        return s;
    }

    public static void render(Animation fade, String name, float health, double ratio) {
        mode.render(fade, text(name, health, ratio), ratio);
    }

    public static void legacy(Animation fade, String s, double d) {
        int w = mc.textRenderer.getWidth(s) + 8;
        int x = RenderUtils.scaledWidth / 2 - w / 2 + offsetX;
        int y = RenderUtils.scaledHeight / 2 + offsetY;
        int x1 = x - 8;
        int y1 = y - 8;
        int x2 = x + w;
        int y2 = y + mc.textRenderer.fontHeight - 1 + 8;
        int a = fade == null ? 255 : 255 - fade.getValueInt(0, 255, 1);
        if (a > 0) {
            int bgA = Math.min(a, 100);
            int barA = Math.min(a, 210);
            int[] rgb = Theme.getGradients(theme.ordinal());
            int bx1 = x1 + 6;
            int bx2 = x2 - 6;
            int by = y2;
            if (target != -1.0) {
                long now = System.currentTimeMillis();
                long age = now - changedAt;
                long dur = 125L;
                if (d != target) {
                    from = age > dur ? target : shown;
                    target = d;
                    changedAt = now;
                } else if (age <= dur) {
                    double t = Math.min(1.0, (double) age / dur);
                    shown = from + (target - from) * t;
                } else {
                    shown = d;
                }
            } else {
                target = d;
            }

            d = shown;
            int c2;
            int c1;
            if (traditionalHealthColor.isToggled()) {
                c1 = c2 = RenderUtils.withAlpha(Strings.healthColor(d), barA);
            } else {
                c1 = RenderUtils.withAlpha(rgb[0], barA);
                c2 = RenderUtils.withAlpha(RenderUtils.blendInt(rgb[0], rgb[1], 1.0 - d), barA);
            }

            RenderUtils.roundedRectBorder(
                    x1,
                    y1,
                    x2,
                    y2 + 13,
                    10.0F,
                    RenderUtils.withAlpha(Color.black.getRGB(), bgA),
                    RenderUtils.withAlpha(rgb[0], a),
                    RenderUtils.withAlpha(rgb[1], a));
            if (d < 1.0) {
                RenderUtils.roundedRect(bx1, by, bx2, by + 5, 4.0F, RenderUtils.withAlpha(Color.black.getRGB(), bgA));
            }

            RenderUtils.roundedRectGradient(
                    bx1, by, (float) (bx2 + (bx1 - bx2) * (1.0 - d)), by + 5, 4.0F, c1, c1, c2, c2);
            GL11.glEnable(3042);
            GlStateManager.blendFuncSeparate(770, 771, 1, 0);
            mc.textRenderer.draw(s, x, y, RenderUtils.withAlpha(TEXT, RenderUtils.clampAlpha(a)), true);
            GL11.glDisable(3042);
            RenderUtils.resetColor();
        }
    }

    public static void modern(Animation fade, String s, double d) {
        int w = mc.textRenderer.getWidth(s) + 8;
        int x = RenderUtils.scaledWidth / 2 - w / 2 + offsetX;
        int y = RenderUtils.scaledHeight / 2 + offsetY;
        int x1 = x - 8;
        int y1 = y - 8;
        int x2 = x + w;
        int y2 = y + mc.textRenderer.fontHeight - 1 + 8;
        int a = fade == null ? 255 : 255 - fade.getValueInt(0, 255, 1);
        if (a > 0) {
            int bgA = Math.min(a, 100);
            int barA = Math.min(a, 210);
            int[] rgb = Theme.getGradients(theme.ordinal());
            int bx1 = x1 + 6;
            int bx2 = x2 - 6;
            boolean falling = target < from;
            if (target == -1.0) {
                target = d;
            } else {
                long now = System.currentTimeMillis();
                long age = now - changedAt;
                long dur = !falling ? 500L : 800L;
                if (d == target) {
                    if (age > dur) {
                        shown = d;
                    } else {
                        double t = Math.min(1.0, (double) age / dur);
                        t = 1.0F - (float) Math.pow(1.0 - t, 5.0);
                        shown = from + (target - from) * t;
                    }
                } else {
                    if (age <= dur) {
                        from = shown;
                    } else {
                        from = target;
                    }

                    target = d;
                    changedAt = now;
                }
            }

            d = shown;
            int c2;
            int t2;
            int c1;
            int t1;
            if (!traditionalHealthColor.isToggled()) {
                double front = !falling ? d : target;
                c1 = RenderUtils.withAlpha(rgb[0], barA);
                c2 = RenderUtils.withAlpha(RenderUtils.blendInt(rgb[0], rgb[1], 1.0 - front), barA);
                t1 = RenderUtils.withAlpha(rgb[0], bgA);
                t2 = RenderUtils.withAlpha(RenderUtils.blendInt(rgb[0], rgb[1], 1.0 - d), bgA);
            } else {
                int hc = Strings.healthColor(d);
                c1 = c2 = RenderUtils.withAlpha(hc, barA);
                t1 = t2 = RenderUtils.withAlpha(hc, bgA);
            }

            float f = barA / 210.0F;
            if (ORNITHE && !GLX.useFbo()) {
                RenderUtils.roundedRect(x1, y1, x2, y2 + 13, 5.0F, RenderUtils.withAlpha(Color.black.getRGB(), bgA));
            } else {
                Integer framebuffer = ORNITHE ? GL11.glGetInteger(36006) : null;
                GlowShader glow = GlowShader.get();
                glow.startCapture();
                EspShaders.roundRect(x1, y1, x2, y2 + 13, 5.0F, RenderUtils.withAlpha(Color.black.getRGB(), barA));
                glow.render(2, 1.3F * f, framebuffer);
                Blur blur = Blur.get();
                blur.startStencil();
                EspShaders.roundRect(x1, y1, x2, y2 + 13, 5.0F, -1);
                blur.render(2, 2.0F * f);
            }

            if (d < 1.0) {
                RenderUtils.roundedRect(bx1, y2, bx2, y2 + 5, 4.0F, RenderUtils.withAlpha(Color.black.getRGB(), bgA));
            }

            if (!falling) {
                RenderUtils.roundedRectGradient(
                        bx1, y2, (float) (bx2 + (bx1 - bx2) * (1.0 - d)), y2 + 5, 4.0F, c1, c1, c2, c2);
            } else {
                if (target < 1.0) {
                    RenderUtils.roundedRectGradient(
                            bx1, y2, (float) (bx2 + (bx1 - bx2) * (1.0 - d)), y2 + 5, 4.0F, t1, t1, t2, t2);
                }

                RenderUtils.roundedRectGradient(
                        bx1, y2, (float) (bx2 + (bx1 - bx2) * (1.0 - target)), y2 + 5, 4.0F, c1, c1, c2, c2);
            }

            GL11.glEnable(3042);
            GlStateManager.blendFuncSeparate(770, 771, 1, 0);
            mc.textRenderer.draw(s, x, y, RenderUtils.withAlpha(TEXT, RenderUtils.clampAlpha(a)), true);
            GL11.glDisable(3042);
        }
    }

    public static void reset() {
        changedAt = -1L;
        target = -1.0;
        from = -1.0;
    }

    public enum Mode {
        Disabled,
        Modern,
        Legacy;

        public void render(String s, double d) {
            this.render(null, s, d);
        }

        public void render(Animation fade, String s, double d) {
            if (this == Legacy) {
                TargetHudRenderer.legacy(fade, s, d);
            } else {
                TargetHudRenderer.modern(fade, s, d);
            }
        }
    }
}
