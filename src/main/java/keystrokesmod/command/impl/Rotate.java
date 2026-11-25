package keystrokesmod.command.impl;

import keystrokesmod.command.Command;
import keystrokesmod.command.CommandInput;
import keystrokesmod.utility.Utils;

public class Rotate extends Command {
    public Rotate() {
        super("rotate");
    }

    @Override
    public void execute(CommandInput input) {
        if (input.argumentCount() != 2) {
            this.replyTagged(input, "&7Enter yaw/pitch.");
        } else {
            float yaw;
            float pitch;
            try {
                yaw = Float.parseFloat(input.getArgument(0));
                pitch = Float.parseFloat(input.getArgument(1));
            } catch (NumberFormatException exception) {
                this.replyTagged(input, "&7Enter valid yaw/pitch.");
                return;
            }

            if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
                this.replyTagged(input, "&7Enter valid yaw/pitch.");
            } else if (Utils.nullCheck()) {
                mc.player.yaw = yaw;
                mc.player.pitch = pitch;
            }
        }
    }
}
