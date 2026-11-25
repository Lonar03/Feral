package keystrokesmod.command.impl;

import keystrokesmod.Feral;
import keystrokesmod.clickgui.ClickGui;
import keystrokesmod.clickgui.components.impl.CategoryComponent;
import keystrokesmod.clickgui.components.impl.ModuleComponent;
import keystrokesmod.command.Command;
import keystrokesmod.command.CommandInput;

public class ResetGui extends Command {
    public ResetGui() {
        super("resetgui");
    }

    @Override
    public void execute(CommandInput input) {
        if (Feral.clickGui != null && ClickGui.categories != null) {
            for (CategoryComponent category : ClickGui.categories) {
                for (ModuleComponent module : category.getModules()) {
                    module.restoreOpenState(false);
                }
            }

            Feral.clickGui.resetPositions();
            if (Feral.currentProfile != null) {
                Feral.currentProfile.getModule().saved = false;
            }
        }

        this.replyTagged(input, "&7Reset GUI positions.");
    }
}
