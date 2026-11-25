package keystrokesmod.clickgui;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;

/**
 * Category colours for the Big cat GUI. MeowerClient keeps these in one "cat" module per category; Feral only has
 * the Big cat style, so each category gets Meower's default colour (a rainbow by category size, largest first, and
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
    public static final Module.category[] SPECIAL = {
        Module.category.zombies, Module.category.client, Module.category.profiles, Module.category.scripts
    };

    private static final int[] SPECIAL_COLORS = {
        0xFF2F8F45, // zombies: dark green
        0xFFD4D4D4, // client: light grey
        0xFFA8A8A8, // profiles: grey
        0xFF7D7D7D // scripts: dark grey
    };
    private static Map<Module.category, Integer> colors;

    private CatColors() {}

    public static boolean isSpecial(Module.category category) {
        for (Module.category c : SPECIAL) {
            if (c == category) {
                return true;
            }
        }

        return false;
    }

    /** The accent colour (ARGB, opaque) of a category. */
    public static int of(Module.category category) {
        if (colors == null) {
            Map<Module.category, Integer> counts = new EnumMap<>(Module.category.class);
            for (Module module : ModuleManager.modules) {
                counts.merge(module.moduleCategory(), 1, Integer::sum);
            }

            List<Module.category> main = new ArrayList<>();
            for (Module.category c : Module.category.values()) {
                if (!isSpecial(c)) {
                    main.add(c);
                }
            }

            main.sort((a, b) -> {
                int bySize = Integer.compare(counts.getOrDefault(b, 0), counts.getOrDefault(a, 0));
                return bySize != 0 ? bySize : a.name().compareTo(b.name());
            });
            Map<Module.category, Integer> built = new EnumMap<>(Module.category.class);
            for (int i = 0; i < main.size(); i++) {
                built.put(main.get(i), RAINBOW[Math.min(i, RAINBOW.length - 1)]);
            }

            for (int i = 0; i < SPECIAL.length; i++) {
                built.put(SPECIAL[i], SPECIAL_COLORS[i]);
            }

            colors = built;
        }

        return colors.getOrDefault(category, 0xFF1AA879);
    }
}
