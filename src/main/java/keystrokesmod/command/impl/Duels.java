package keystrokesmod.command.impl;

import java.util.List;
import keystrokesmod.Feral;
import keystrokesmod.command.Command;
import keystrokesmod.command.CommandInput;
import keystrokesmod.module.impl.minigames.DuelsStats;
import keystrokesmod.utility.NetworkUtils;
import keystrokesmod.utility.ProfileUtils;
import keystrokesmod.utility.Utils;

/** Duels stats lookup, plus the API key and nick Duels Stats uses. Moved here from the old command line. */
public class Duels extends Command {
    public Duels() {
        super("duels");
    }

    @Override
    public void execute(CommandInput input) {
        if (input.argumentCount() == 0) {
            this.syntaxError();
            return;
        }

        String first = input.getArgument(0);
        if (first.equalsIgnoreCase("key")) {
            this.setKey(input);
        } else if (first.equalsIgnoreCase("nick")) {
            this.setNick(input);
        } else if (input.argumentCount() == 1) {
            this.lookup(first);
        } else {
            this.syntaxError();
        }
    }

    private void setKey(CommandInput input) {
        if (input.argumentCount() != 2) {
            this.syntaxError();
            return;
        }

        String apiKey = input.getArgument(1);
        this.replyWithHeader("&7Checking key...");
        Feral.getScheduledExecutor().execute(() -> {
            boolean valid = NetworkUtils.isHypixelKeyValid(apiKey);
            if (valid) {
                NetworkUtils.API_KEY = apiKey;
            }
            this.replyLater(valid ? "&aKey set." : "&cInvalid key.");
        });
    }

    private void setNick(CommandInput input) {
        if (input.argumentCount() != 2) {
            this.syntaxError();
        } else if (input.getArgument(1).equalsIgnoreCase("reset")) {
            DuelsStats.nick = "";
            this.replyWithHeader("&7Nick reset.");
        } else {
            DuelsStats.nick = input.getArgument(1);
            this.replyWithHeader("&7Nick set to &b" + DuelsStats.nick + "&7.");
        }
    }

    private void lookup(String playerName) {
        if (NetworkUtils.API_KEY.isEmpty()) {
            this.replyWithHeader("&cAPI key is empty! &7Use &b" + this.prefixed("duels key [key]") + "&7.");
            return;
        }

        this.replyWithHeader("&7Retrieving data...");
        Feral.getScheduledExecutor().execute(() -> {
            int[] stats = ProfileUtils.getHypixelStats(playerName, ProfileUtils.DM.OVERALL);
            if (stats == null) {
                this.replyLater("&cThere was an error.");
            } else if (stats[0] == -1) {
                String shown = playerName.length() > 16 ? playerName.substring(0, 16) + "..." : playerName;
                this.replyLater("&c" + shown + " does not exist!");
            } else {
                double wlr = stats[1] != 0 ? Utils.round((double) stats[0] / stats[1], 2) : stats[0];
                this.replyLater("&e" + playerName + " stats:");
                this.replyLater("&7Wins: &f" + stats[0] + " &7Losses: &f" + stats[1] + " &7WLR: &f" + wlr);
                this.replyLater("&7Winstreak: &f" + stats[2] + " &7Threat: "
                        + DuelsStats.gtl(stats[0], stats[1], wlr, stats[2]).substring(2));
            }
        });
    }

    /** Replies from the lookup thread on the game thread. */
    private void replyLater(String message) {
        mc.executeTask(() -> this.reply(message));
    }

    @Override
    public List<String> suggest(CommandInput input) {
        return input.argumentCount() <= 1 ? this.filterSuggestions(input, "key", "nick") : super.suggest(input);
    }
}
