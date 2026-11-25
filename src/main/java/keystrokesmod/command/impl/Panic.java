package keystrokesmod.command.impl;

import java.util.LinkedHashSet;
import java.util.Set;
import keystrokesmod.Feral;
import keystrokesmod.command.Command;
import keystrokesmod.command.CommandInput;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;

public class Panic extends Command {
    public Panic() {
        super("panic");
    }

    @Override
    public void execute(CommandInput input) {
        Set<Module> modules = new LinkedHashSet<>(ModuleManager.modules);
        if (Feral.scriptManager != null) {
            modules.addAll(Feral.scriptManager.scripts.values());
        }

        int count = 0;

        for (Module module : modules) {
            if (module.isEnabled() && module != ModuleManager.commandLine) {
                module.disable();
                count++;
            }
        }

        if (count == 0) {
            this.replyTagged(input, "&cNo modules enabled.");
        } else {
            if (Feral.currentProfile != null) {
                Feral.currentProfile.getModule().saved = false;
            }

            this.replyTagged(input, "&7Disabled &b" + count + " &7enabled modules.");
        }
    }
}
