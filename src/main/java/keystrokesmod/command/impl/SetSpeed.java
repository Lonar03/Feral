package keystrokesmod.command.impl;

import java.util.List;
import keystrokesmod.command.Command;
import keystrokesmod.command.CommandInput;
import keystrokesmod.module.impl.movement.BHop;
import keystrokesmod.module.impl.movement.Fly;
import keystrokesmod.module.impl.movement.Speed;
import keystrokesmod.module.setting.impl.SliderSetting;

/** Sets Fly/BHop/Speed speed, past the slider's range (0 - 100). Moved here from the old command line. */
public class SetSpeed extends Command {
    public SetSpeed() {
        super("setspeed");
    }

    @Override
    public void execute(CommandInput input) {
        if (input.argumentCount() != 2) {
            this.usage();
            return;
        }

        SliderSetting setting;
        switch (input.getArgument(0).toLowerCase()) {
            case "fly":
                setting = Fly.horizontalSpeed;
                break;
            case "bhop":
                setting = BHop.speedSetting;
                break;
            case "speed":
                setting = Speed.multiplier;
                break;
            default:
                this.usage();
                return;
        }

        Double value = CommandValues.parse(input.getArgument(1), 0.0, 100.0);
        if (value == null) {
            this.replyWithHeader("&cInvalid value. &7[0 - 100]");
        } else {
            setting.setValueRawWithEvent(value);
            this.replyWithHeader(
                    "&7Set &b" + input.getArgument(0).toLowerCase() + " &7speed to &b" + input.getArgument(1) + "&7.");
        }
    }

    private void usage() {
        this.replyWithHeader("&7Usage: &b" + this.prefixed("setspeed [fly/bhop/speed] [value]"));
    }

    @Override
    public List<String> suggest(CommandInput input) {
        return input.argumentCount() <= 1
                ? this.filterSuggestions(input, "fly", "bhop", "speed")
                : super.suggest(input);
    }
}
