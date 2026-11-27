package keystrokesmod.command.impl;

import java.util.List;
import keystrokesmod.command.Command;
import keystrokesmod.command.CommandInput;
import keystrokesmod.module.impl.combat.Velocity;
import keystrokesmod.module.setting.impl.SliderSetting;

/** Sets Velocity's horizontal/vertical %, past the slider's range (-100 - 300). Moved here from the old command line. */
public class SetVelocity extends Command {
    public SetVelocity() {
        super("setvelocity");
    }

    @Override
    public void execute(CommandInput input) {
        if (input.argumentCount() != 2) {
            this.usage();
            return;
        }

        SliderSetting setting;
        String axis;
        switch (input.getArgument(0).toLowerCase()) {
            case "horizontal":
            case "h":
                setting = Velocity.horizontal;
                axis = "horizontal";
                break;
            case "vertical":
            case "v":
                setting = Velocity.vertical;
                axis = "vertical";
                break;
            default:
                this.usage();
                return;
        }

        Double value = CommandValues.parse(input.getArgument(1), -100.0, 300.0);
        if (value == null) {
            this.replyWithHeader("&cInvalid value. &7[-100 - 300]");
        } else {
            setting.setValueRawWithEvent(value);
            this.replyWithHeader("&7Set &b" + axis + " &7velocity to &b" + input.getArgument(1) + "&7.");
        }
    }

    private void usage() {
        this.replyWithHeader("&7Usage: &b" + this.prefixed("setvelocity [h/v] [value]"));
    }

    @Override
    public List<String> suggest(CommandInput input) {
        return input.argumentCount() <= 1
                ? this.filterSuggestions(input, "horizontal", "vertical")
                : super.suggest(input);
    }
}
