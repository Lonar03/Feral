package keystrokesmod.command.impl;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import keystrokesmod.command.Command;
import keystrokesmod.command.CommandInput;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.other.NickBot;

/** .nickbot start|stop|add|remove|list: runs NickBot and edits its custom word filters. */
public class NickBotCommand extends Command {
    public NickBotCommand() {
        super("nickbot");
    }

    @Override
    public void execute(CommandInput input) {
        String sub = input.argumentCount() == 0 ? "" : input.getArgument(0).toLowerCase();
        String entry = input.argumentCount() > 1 ? input.getArgument(1) : "";
        NickBot bot = ModuleManager.nickBot;
        if (sub.equals("start")) {
            bot.start();
        } else if (sub.equals("stop")) {
            bot.stop();
        } else if (sub.equals("add") && !entry.isEmpty()) {
            this.reply(
                    NickBot.addWord(entry) ? "&aAdded &f" + entry + " &ato the list." : "&cThis is already an entry.");
        } else if (sub.equals("remove") && !entry.isEmpty()) {
            this.reply(
                    NickBot.removeWord(entry)
                            ? "&cRemoved &f" + entry + " &cfrom the list."
                            : "&cThis is not an entry.");
        } else if (sub.equals("list")) {
            Set<String> words = NickBot.getWords();
            if (words.isEmpty()) {
                this.reply("&cYour list is empty.");
            } else {
                for (String word : words) {
                    this.reply(" &f" + word);
                }
            }
        } else {
            this.reply("&b.nickbot start &7Start the NickBot (ESC stops it)");
            this.reply("&b.nickbot stop &7Stop the NickBot");
            this.reply("&b.nickbot add <text> &7Add a custom word");
            this.reply("&b.nickbot remove <text> &7Remove a custom word");
            this.reply("&b.nickbot list &7Show the word list");
            this.reply("&6Syntax: &a?meow &7contains, &a=meow &7equals, &a<meow &7starts with, &a>meow &7ends with");
        }
    }

    @Override
    public List<String> suggest(CommandInput input) {
        return input.argumentCount() <= 1
                ? Arrays.asList("start", "stop", "add", "remove", "list")
                : super.suggest(input);
    }
}
