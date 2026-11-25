package keystrokesmod.utility.profile;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

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
            case "Account Hider":
                // Meower's Name Hider (renamed, + custom skin). "Hide name" off meant the name wasn't hidden.
                if (info.has("Hide name") && !info.get("Hide name").getAsBoolean()) {
                    info.add("enabled", new JsonPrimitive(false));
                }
                return "Name Hider";
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

    private static void rename(JsonObject info, String from, String to) {
        if (info.has(from) && !info.has(to)) {
            info.add(to, info.get(from));
        }
    }
}
