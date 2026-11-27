package keystrokesmod.command.impl;

import java.util.List;
import keystrokesmod.command.Command;
import keystrokesmod.command.CommandInput;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;

public class HideAll extends Command {
    public HideAll() {
        super("hide", "hideall");
    }

    @Override
    public void execute(CommandInput input) {
        if (input.argumentCount() > 0 && !"all".equalsIgnoreCase(input.getArgument(0))) {
            // [module]: one module (moved here from the old command line).
            Module module = CommandValues.findModule(input.joinArguments(0));
            if (module == null) {
                this.replyWithHeader("&cModule not found.");
            } else {
                module.setHidden(true);
                this.replyWithHeader("&7Hidden &b" + module.getName() + " &7from HUD.");
            }
        } else {
            int count = 0;

            for (Module module : ModuleManager.modules) {
                if (!module.isHidden()) {
                    module.setHidden(true);
                    count++;
                }
            }

            this.replyWithHeader("&7Hidden &c" + count + " &7module" + (count == 1 ? "" : "s") + " from HUD.");
        }
    }

    @Override
    public List<String> suggest(CommandInput input) {
        return CommandValues.moduleSuggestions(input.argumentCount() == 0 ? "" : input.joinArguments(0));
    }

    @Override
    public int getSuggestionArgumentStart(CommandInput input) {
        return 0;
    }
}
