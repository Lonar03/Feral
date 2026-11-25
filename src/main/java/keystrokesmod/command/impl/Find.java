package keystrokesmod.command.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import keystrokesmod.clickgui.components.impl.CategoryComponent;
import keystrokesmod.command.Command;
import keystrokesmod.command.CommandInput;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;

public class Find extends Command {
    public Find() {
        super("find");
    }

    @Override
    public void execute(CommandInput input) {
        String name = input.joinArguments(0);
        if (name.isEmpty()) {
            this.respond("&cInvalid syntax, enter a module name.");
        } else {
            Module target = null;

            for (Module module : ModuleManager.modules) {
                if (module.getName().equalsIgnoreCase(name)) {
                    target = module;
                    break;
                }
            }

            if (target == null) {
                this.respond("&cInvalid module.");
            } else {
                List<Module> categoryModules = new ArrayList<>();

                for (Module module : ModuleManager.modules) {
                    if (module.moduleCategory() == target.moduleCategory()) {
                        categoryModules.add(module);
                    }
                }

                categoryModules = CategoryComponent.sortModules(categoryModules);
                String category = target.moduleCategory().name();
                category = category.substring(0, 1).toUpperCase(Locale.ROOT) + category.substring(1);
                this.respond("&b" + category + " &7-> &b" + target.getName() + " &7("
                        + (categoryModules.indexOf(target) + 1) + "/" + categoryModules.size() + ")");
            }
        }
    }

    @Override
    public List<String> suggest(CommandInput input) {
        String query = input.joinArguments(0).toLowerCase(Locale.ROOT);
        List<String> suggestions = new ArrayList<>();

        for (Module module : ModuleManager.modules) {
            if (module.getName().toLowerCase(Locale.ROOT).startsWith(query)) {
                suggestions.add(module.getName());
            }
        }

        return suggestions;
    }

    @Override
    public int getSuggestionArgumentStart(CommandInput input) {
        return 0;
    }

    private void respond(String message) {
        this.replyWithHeader("&7[&f" + this.getName() + "&7] &r" + message);
    }
}
