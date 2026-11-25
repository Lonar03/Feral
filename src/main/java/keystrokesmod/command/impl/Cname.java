package keystrokesmod.command.impl;

import keystrokesmod.command.Command;
import keystrokesmod.command.CommandInput;
import keystrokesmod.module.impl.other.AccountHider;

public class Cname extends Command {
    public Cname() {
        super("accounthider", "namehider");
    }

    @Override
    public void execute(CommandInput input) {
        if (input.argumentCount() == 0) {
            this.syntaxError();
        } else {
            AccountHider.setFakeName(input.joinArguments(0));
            this.replyWithHeader("&7Name has been set to &b" + AccountHider.fakeName + "&7.");
        }
    }
}
