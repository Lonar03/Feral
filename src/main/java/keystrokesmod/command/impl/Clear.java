package keystrokesmod.command.impl;

import keystrokesmod.command.Command;
import keystrokesmod.command.CommandInput;
import keystrokesmod.utility.Utils;
import net.minecraft.text.LiteralText;

public class Clear extends Command {
    public Clear() {
        super("clear");
    }

    @Override
    public void execute(CommandInput input) {
        if (Utils.nullCheck()) {
            for (int i = 0; i < 30; i++) {
                mc.player.sendMessage(new LiteralText(""));
            }
        }
    }
}
