package keystrokesmod.command.impl;

import java.util.ArrayList;
import java.util.List;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;

/** Shared parsing for commands. */
final class CommandValues {
    private CommandValues() {}

    /** The number in {@code text} if it is finite and within [min, max], otherwise null. */
    static Double parse(String text, double min, double max) {
        try {
            double value = Double.parseDouble(text);
            return Double.isFinite(value) && value >= min && value <= max ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Finds a module by name, ignoring case and spaces ("killaura" finds "KillAura"). */
    static Module findModule(String query) {
        String normalized = normalize(query);
        for (Module module : ModuleManager.modules) {
            if (normalize(module.getName()).equals(normalized)) {
                return module;
            }
        }
        return null;
    }

    /** Module names starting with {@code query}, plus "all". */
    static List<String> moduleSuggestions(String query) {
        List<String> out = new ArrayList<>();
        String q = query == null ? "" : query.toLowerCase();
        if ("all".startsWith(q)) {
            out.add("all");
        }
        for (Module module : ModuleManager.modules) {
            if (module.getName().toLowerCase().startsWith(q)) {
                out.add(module.getName());
            }
        }
        return out;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace(" ", "").toLowerCase();
    }
}
