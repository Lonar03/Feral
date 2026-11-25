package keystrokesmod.event;

import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.client.gui.screen.Screen;

public class GuiUpdateEvent extends ForgeEvents.Event {
    public Screen guiScreen;
    public boolean opened;

    public GuiUpdateEvent(Screen guiScreen, boolean opened) {
        this.guiScreen = guiScreen;
        this.opened = opened;
    }
}
