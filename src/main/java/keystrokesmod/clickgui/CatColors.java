package keystrokesmod.clickgui;

import java.util.EnumMap;
import java.util.Map;
import keystrokesmod.module.Module;

/**
 * Category colours for the Big cat GUI. MeowerClient keeps these in one "cat" module per category; Feral only has
 * the Big cat style, so each category gets a fixed colour (a rainbow in the shared category order, and
 * fixed colours for the categories below the divider).
 */
public final class CatColors {
    private static final int[] RAINBOW = {
        0xFFF06B6B, // red
        0xFFF39A4F, // orange
        0xFFF2D24B, // yellow
        0xFF6FD36A, // green
        0xFF4FD8D0, // aqua
        0xFF5B8FF0, // blue
        0xFFA07BF0, // purple
        0xFFF06FB6, // pink
        0xFFF7B6D2 // light pink
    };
    /** Categories listed below the divider, in order, with their colour. */
    private static final Module.category[] SPECIAL = {
        Module.category.zombies, Module.category.client, Module.category.profiles, Module.category.scripts
    };

    private static final int[] SPECIAL_COLORS = {
        0xFF2F8F45, // zombies: dark green
        0xFFD4D4D4, // client: light grey
        0xFFA8A8A8, // profiles: grey
        0xFF7D7D7D // scripts: dark grey
    };
    private static final Map<Module.category, Integer> COLORS = buildColors();

    private CatColors() {}

    private static Map<Module.category, Integer> buildColors() {
        Map<Module.category, Integer> colors = new EnumMap<>(Module.category.class);
        for (int i = 0; i < SPECIAL.length; i++) {
            colors.put(SPECIAL[i], SPECIAL_COLORS[i]);
        }
        int next = 0;
        for (Module.category category : Module.category.values()) {
            if (!colors.containsKey(category)) {
                colors.put(category, RAINBOW[Math.min(next++, RAINBOW.length - 1)]);
            }
        }
        return colors;
    }

    /** The accent colour (ARGB, opaque) of a category. */
    public static int of(Module.category category) {
        return COLORS.getOrDefault(category, 0xFF1AA879);
    }
}
