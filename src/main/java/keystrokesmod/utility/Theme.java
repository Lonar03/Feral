package keystrokesmod.utility;

import java.awt.Color;
import java.util.Arrays;
import keystrokesmod.module.impl.client.Settings;

public enum Theme {
    Default(null, null),
    Rainbow(null, null),
    Aurora(new Color(120, 0, 200), new Color(0, 255, 180)),
    Cherry(new Color(255, 200, 200), new Color(243, 58, 106)),
    Cotton_candy(new Color(99, 249, 255), new Color(255, 104, 204)),
    Flare(new Color(241, 39, 17), new Color(245, 175, 25)),
    Flower(new Color(215, 166, 231), new Color(211, 90, 232)),
    Forest(new Color(34, 139, 34), new Color(154, 205, 50)),
    Frost(new Color(253, 253, 253), new Color(210, 225, 230)),
    Gold(new Color(255, 250, 0), new Color(255, 253, 195)),
    Grayscale(new Color(240, 240, 240), new Color(110, 110, 110)),
    Inferno(new Color(255, 51, 0), new Color(50, 0, 0)),
    Royal(new Color(125, 204, 241), new Color(30, 71, 170)),
    Sandstorm(new Color(194, 178, 128), new Color(255, 236, 179)),
    Sky(new Color(140, 254, 250), new Color(14, 210, 247)),
    Vine(new Color(0, 255, 25), new Color(180, 255, 180));

    private final Color firstGradient;
    private final Color secondGradient;
    public static Color[] DESCRIPTOR = new Color[] {new Color(95, 235, 255), new Color(68, 102, 250)};
    public static Color[] HIDDEN_BIND = new Color[] {new Color(245, 33, 33), new Color(229, 21, 98)};
    public static final int DEFAULT_THEME_INDEX = Default.ordinal();
    public static final String[] THEMES_SETTING =
            Arrays.stream(values()).map(theme -> theme.name().replace('_', ' ')).toArray(String[]::new);
    public static final String[] THEMES_STRING = Arrays.stream(values())
            .filter(theme -> theme != Default)
            .map(theme -> theme.name().replace('_', ' '))
            .toArray(String[]::new);

    Theme(Color firstGradient, Color secondGradient) {
        this.firstGradient = firstGradient;
        this.secondGradient = secondGradient;
    }

    public static int resolveThemeIndex(int index) {
        return index == DEFAULT_THEME_INDEX ? (int) Settings.defaultTheme.getInput() + 1 : index;
    }

    public static int getGradient(int index, double delay) {
        index = resolveThemeIndex(index);
        if (index == Rainbow.ordinal()) {
            return getChromaOffset(2L, (long) delay);
        }

        Theme[] values = values();
        return index > DEFAULT_THEME_INDEX && index < values.length
                ? convert(
                                values[index].firstGradient,
                                values()[index].secondGradient,
                                (Math.sin(System.currentTimeMillis()
                                                                / 1.0E8
                                                                * Settings.timeMultiplier.getInput()
                                                                * 400000.0
                                                        + delay * Settings.offset.getInput())
                                                + 1.0)
                                        * 0.5)
                        .getRGB()
                : -1;
    }

    public static int getChromaOffset(long speed, long delay) {
        double offset = Settings.offset.getInput() * delay;
        long time = System.currentTimeMillis() + (long) offset;
        float hue = (float) (time % (15000L / speed)) / (15000.0F / (float) speed);
        return Color.getHSBColor(hue, 1.0F, 1.0F).getRGB();
    }

    public static int getGradient(Color firstGradient, Color secondGradient, double delay) {
        return convert(
                        firstGradient,
                        secondGradient,
                        (Math.sin(System.currentTimeMillis() / 1.0E8 * 0.5 * 400000.0 + delay * 0.55F) + 1.0) * 0.5)
                .getRGB();
    }

    public static Color convert(Color color, Color color2, double delay) {
        double n2 = 1.0 - delay;
        return new Color(
                (int) (color.getRed() * delay + color2.getRed() * n2),
                (int) (color.getGreen() * delay + color2.getGreen() * n2),
                (int) (color.getBlue() * delay + color2.getBlue() * n2));
    }

    public static int[] getGradients(int index) {
        index = resolveThemeIndex(index);
        Theme[] values = values();
        if (values != null && index >= 0 && index < values.length && values[index] != null) {
            Color firstGradient = values[index].firstGradient;
            Color secondGradient = values[index].secondGradient;
            return firstGradient != null && secondGradient != null
                    ? new int[] {firstGradient.getRGB(), secondGradient.getRGB()}
                    : new int[] {Utils.getChroma(2L, 0L), Utils.getChroma(2L, 0L)};
        } else {
            return new int[] {0, 0};
        }
    }
}
