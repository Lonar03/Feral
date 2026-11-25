package keystrokesmod.command.impl;

import keystrokesmod.command.Command;
import keystrokesmod.command.CommandInput;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.network.Limbo;

public class LimboCommand extends Command {
    public LimboCommand() {
        super("limbo");
    }

    @Override
    public void execute(CommandInput input) {
        ModuleManager.getModule(Limbo.class).enable();
    }
}
