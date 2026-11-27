package keystrokesmod.utility.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.Map;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.render.HUD;
import keystrokesmod.module.setting.Setting;

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
            case "ItemESP":
                JsonElement opacity = info.has("Display.Label Background Opacity")
                        ? info.get("Display.Label Background Opacity")
                        : info.get("Label Background Opacity");
                if (!info.has("Display.Label Background Color")
                        && !info.has("Label Background Color")
                        && opacity != null
                        && opacity.isJsonPrimitive()
                        && opacity.getAsJsonPrimitive().isNumber()) {
                    int alpha = (int) Math.round(Math.max(0, Math.min(100, opacity.getAsDouble())) * 255 / 100);
                    info.addProperty("Display.Label Background Color", "0,0,0," + alpha);
                }
                return moduleName;
            default:
                return moduleName;
        }
    }

    static void moveHudSettings(Map<Module, JsonObject> loaded) {
        JsonObject gui = null;
        Map.Entry<Module, JsonObject> hudEntry = null;
        for (Map.Entry<Module, JsonObject> entry : loaded.entrySet()) {
            String name = entry.getKey().getName();
            if (name.equals("Gui")) {
                gui = entry.getValue();
            } else if (entry.getKey() instanceof HUD) {
                hudEntry = entry;
            }
        }
        if (hudEntry == null) {
            return;
        }
        JsonObject hud = hudEntry.getValue();
        moveGuiArraylist(gui, hud);
        rename(hud, "Arraylist", "Arraylist.Style");
        rename(hud, "Big cat color mode", "Arraylist.Big cat color mode");
        if (!hud.has("Arraylist.Show arraylist")) {
            hud.addProperty("Arraylist.Show arraylist", true);
        }
        if (!hud.has("Notifications.Show notifications")
                && (gui == null || !gui.has("Notifications.Show notifications"))) {
            hud.addProperty("Notifications.Show notifications", true);
        }
        for (Setting setting : hudEntry.getKey().getSettings()) {
            String key = setting.getProfileKey();
            if (key.startsWith("Arraylist.") && !key.equals("Arraylist.Style")) {
                rename(hud, setting.getName(), key);
            } else if (key.startsWith("Notifications.") && !hud.has(key) && gui != null && gui.has(key)) {
                hud.add(key, gui.get(key));
            }
        }
    }

    /** Also import the original Meower arraylist when the HUD layout hasn't already been saved. */
    private static void moveGuiArraylist(JsonObject gui, JsonObject hud) {
        if (hud.has("Arraylist.Style")) {
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
