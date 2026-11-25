package keystrokesmod.command.impl;

import java.util.List;
import java.util.Locale;
import keystrokesmod.Feral;
import keystrokesmod.command.Command;
import keystrokesmod.command.CommandInput;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.world.Weather;

public class Time extends Command {
    public Time() {
        super("time");
    }

    @Override
    public void execute(CommandInput input) {
        if (input.argumentCount() != 1) {
            this.replyTagged(input, "&cInvalid syntax, enter day/sunset/night/vanilla.");
        } else {
            String preset = input.getArgument(0).toLowerCase(Locale.ROOT);
            double value;
            switch (preset) {
                case "day":
                    value = 14.1;
                    break;
                case "sunset":
                    value = 21.0;
                    break;
                case "night":
                    value = 2.1;
                    break;
                case "vanilla":
                    value = 0.0;
                    break;
                default:
                    this.replyTagged(input, "&cInvalid time, enter day/sunset/night/vanilla.");
                    return;
            }

            Weather weather = ModuleManager.weather;
            weather.time.setValueWithEvent(value);
            if (value != 0.0) {
                weather.enable();
            }

            if (Feral.currentProfile != null) {
                Feral.currentProfile.getModule().saved = false;
            }

            this.replyTagged(input, "&7Set time to &b" + preset + "&7.");
        }
    }

    @Override
    public List<String> suggest(CommandInput input) {
        return input.argumentCount() <= 1
                ? this.filterSuggestions(input, "day", "sunset", "night", "vanilla")
                : super.suggest(input);
    }
}
