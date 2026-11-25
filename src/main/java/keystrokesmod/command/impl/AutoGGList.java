package keystrokesmod.command.impl;

import java.util.Arrays;
import java.util.List;
import keystrokesmod.command.Command;
import keystrokesmod.command.CommandInput;
import keystrokesmod.module.impl.minigames.AutoGG;

/** .autogg / .autogl add|remove|list: edits the messages AutoGG's random mode picks from. */
public class AutoGGList extends Command {
    private final boolean gl;

    public AutoGGList(boolean gl) {
        super(gl ? "autogl" : "autogg");
        this.gl = gl;
    }

    @Override
    public void execute(CommandInput input) {
        String label = this.gl ? "AutoGL" : "AutoGG";
        String sub = input.argumentCount() == 0 ? "" : input.getArgument(0).toLowerCase();
        String message = input.argumentCount() > 1 ? input.joinArguments(1).trim() : "";
        if (sub.equals("add") && !message.isEmpty()) {
            this.reply(
                    AutoGG.addMessage(this.gl, message)
                            ? "&aAdded &f" + message + " &ato the " + label + " list."
                            : "&f" + message + " &ais already in the list.");
        } else if (sub.equals("remove") && !message.isEmpty()) {
            this.reply(
                    AutoGG.removeMessage(this.gl, message)
                            ? "&cRemoved &f" + message + " &cfrom the " + label + " list."
                            : "&f" + message + " &cis not in the list.");
        } else if (sub.equals("list")) {
            List<String> messages = AutoGG.getMessages(this.gl);
            if (messages.isEmpty()) {
                this.reply("&cYour " + label + " list is empty.");
            } else {
                this.reply("&7" + label + " messages:");
                for (String m : messages) {
                    this.reply(" &f" + m);
                }
            }
        } else {
            String name = this.getName();
            this.reply("&b." + name + " add <msg> &7Add a message");
            this.reply("&b." + name + " remove <msg> &7Remove a message");
            this.reply("&b." + name + " list &7List all messages");
        }
    }

    @Override
    public List<String> suggest(CommandInput input) {
        return input.argumentCount() <= 1 ? Arrays.asList("add", "remove", "list") : super.suggest(input);
    }
}
