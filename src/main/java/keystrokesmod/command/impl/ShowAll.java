package keystrokesmod.command.impl;

import java.util.List;
import keystrokesmod.command.Command;
import keystrokesmod.command.CommandInput;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;

public class ShowAll extends Command {
    public ShowAll() {
        super("show", "showall");
    }

    @Override
    public void execute(CommandInput input) {
        if (input.argumentCount() > 0 && !"all".equalsIgnoreCase(input.getArgument(0))) {
            // [module]: one module (moved here from the old command line).
            Module module = CommandValues.findModule(input.joinArguments(0));
            if (module == null) {
                this.replyWithHeader("&cModule not found.");
            } else {
                module.setHidden(false);
                this.replyWithHeader("&7Made &b" + module.getName() + " &7visible in HUD.");
            }
        } else {
            int count = 0;

            for (Module module : ModuleManager.modules) {
                if (module.isHidden()) {
                    module.setHidden(false);
                    count++;
                }
            }

            this.replyWithHeader("&7Made &a" + count + " &7module" + (count == 1 ? "" : "s") + " visible in HUD.");
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
