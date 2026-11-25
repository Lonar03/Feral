package keystrokesmod.command.impl;

import keystrokesmod.command.Command;
import keystrokesmod.command.CommandInput;
import net.minecraft.client.Minecraft;

public class Fps extends Command {
    public Fps() {
        super("fps");
    }

    @Override
    public void execute(CommandInput input) {
        int fps = Minecraft.getCurrentFps();
        char color = (char)
                (fps < 60 ? 52 : (fps < 120 ? 99 : (fps < 300 ? 54 : (fps < 600 ? 101 : (fps < 1500 ? 97 : 50)))));
        this.replyTagged(input, "&7FPS: &" + color + fps);
    }
}
