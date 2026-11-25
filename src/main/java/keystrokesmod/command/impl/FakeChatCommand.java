package keystrokesmod.command.impl;

import keystrokesmod.command.Command;
import keystrokesmod.command.CommandInput;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.other.FakeChat;
import keystrokesmod.utility.Utils;

public class FakeChatCommand extends Command {
    public FakeChatCommand() {
        super("fakechat");
    }

    @Override
    public void execute(CommandInput input) {
        if (Utils.nullCheck()) {
            if (input.argumentCount() > 0) {
                String raw = input.getRawMessage();
                int labelStart = raw.indexOf(input.getLabel(), 1);
                FakeChat.msg = raw.substring(labelStart + input.getLabel().length() + 1);
            }

            ModuleManager.getModule(FakeChat.class).enable();
        }
    }
}
