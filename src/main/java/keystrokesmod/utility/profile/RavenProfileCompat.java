package keystrokesmod.utility.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.Map;
import keystrokesmod.module.Module;

/**
 * Loads profiles saved by older Raven bS versions and clients based on them (like MeowerClient): renamed modules and
 * settings that Feral (Raven bS 17) calls differently. Settings that only exist on one side are left alone.
 */
final class RavenProfileCompat {
    private RavenProfileCompat() {}

    /** Rewrites {@code info} in place where needed and returns the Feral module name for it. */
    static String apply(String moduleName, JsonObject info) {
        switch (moduleName) {
            case "Anti Shuffle":
                // Renamed to Unscramble (other)
                return "Unscramble";
            case "Name Hider":
                // Renamed to Account Hider (+ Hide name toggle and custom skin), like Meower. Meower profiles
                // already save it as Account Hider.
                return "Account Hider";
            case "Bridge Assist":
                rename(info, "Sneak key pressed", "Only while sneaking");
                rename(info, "Holding blocks", "Blocks only");
                rename(info, "Not moving forward", "Disable on forward");
                if (info.has("Looking down") && !info.has("Minimum pitch")) {
                    // Meower: "Looking down" = pitch >= 70. Feral: Minimum pitch slider, 19 (its minimum) = disabled.
                    info.add(
                            "Minimum pitch",
                            new JsonPrimitive(info.get("Looking down").getAsBoolean() ? 70.0 : 19.0));
                }
                return moduleName;
            case "Quick Switch":
                rename(info, "Cycle preset", "Cycle profile");
                return moduleName;
            default:
                return moduleName;
        }
    }

    /**
     * The Big cat arraylist settings moved from Gui to HUD ("Arraylist: Big cat"). Profiles from MeowerClient and older
     * Feral versions still have them in Gui; copy them into HUD where HUD doesn't have them yet.
     */
    static void moveGuiArraylist(Map<Module, JsonObject> loaded) {
        JsonObject gui = null;
        JsonObject hud = null;
        for (Map.Entry<Module, JsonObject> entry : loaded.entrySet()) {
            String name = entry.getKey().getName();
            if (name.equals("Gui")) {
                gui = entry.getValue();
            } else if (name.equals("HUD")) {
                hud = entry.getValue();
            }
        }
        if (hud == null) {
            return;
        }
        boolean catShown;
        if (hud.has("Arraylist")) {
            // an earlier Feral build called the option "Cat"
            if (!isOption(hud.get("Arraylist"), -1, "Cat")) {
                return;
            }
            hud.add("Arraylist", new JsonPrimitive("Big cat"));
            catShown = true;
        } else {
            JsonElement style = gui == null ? null : gui.get("Style");
            JsonElement show = gui == null ? null : groupValue(gui, "Show arraylist");
            catShown =
                    isOption(style, 1, "Big cat") && (show == null || !show.isJsonPrimitive() || show.getAsBoolean());
            if (catShown) {
                hud.add("Arraylist", new JsonPrimitive("Big cat"));
            }
        }
        if (!catShown || gui == null) {
            return;
        }
        for (String key :
                new String[] {"Arraylist corner", "Arraylist scale", "Arraylist opacity", "Show module info"}) {
            JsonElement value = groupValue(gui, key);
            if (value != null && !hud.has(key)) {
                hud.add(key, value);
            }
        }
    }

    /** Option sliders are saved as the option name ("Big cat"), older/Meower profiles may have the index. */
    private static boolean isOption(JsonElement value, int index, String name) {
        if (value == null || !value.isJsonPrimitive()) {
            return false;
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isNumber()) {
            return primitive.getAsDouble() == index;
        }
        return primitive.isString() && primitive.getAsString().equalsIgnoreCase(name);
    }

    /** Gui's arraylist settings were in the "Arraylist" group: saved as "Arraylist.<name>" (or just the name). */
    private static JsonElement groupValue(JsonObject info, String name) {
        return info.has("Arraylist." + name) ? info.get("Arraylist." + name) : info.get(name);
    }

    private static void rename(JsonObject info, String from, String to) {
        if (info.has(from) && !info.has(to)) {
            info.add(to, info.get(from));
        }
    }
}
