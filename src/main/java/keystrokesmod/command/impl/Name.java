package keystrokesmod.command.impl;

import keystrokesmod.command.Command;
import keystrokesmod.command.CommandInput;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.system.SystemUtils;

public class Name extends Command {
    public Name() {
        super("name", "ign", "name");
    }

    @Override
    public void execute(CommandInput input) {
        if (Utils.nullCheck()) {
            SystemUtils.addToClipboard(mc.player.getName());
            this.replyWithHeader("&7Copied &b" + mc.player.getName() + " &7to clipboard");
        }
    }
}
