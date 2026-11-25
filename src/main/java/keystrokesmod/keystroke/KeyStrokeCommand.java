package keystrokesmod.keystroke;

import keystrokesmod.Feral;
import net.minecraft.server.command.AbstractCommand;
import net.minecraft.server.command.source.CommandSource;

public class KeyStrokeCommand extends AbstractCommand {
    public String getName() {
        return "keystrokesmod";
    }

    public void run(CommandSource sender, String[] args) {
        Feral.toggleKeyStrokeConfigGui();
    }

    public String getUsage(CommandSource sender) {
        return "/keystrokesmod";
    }

    public int getRequiredPermissionLevel() {
        return 0;
    }

    public boolean canUse(CommandSource sender) {
        return true;
    }
}
