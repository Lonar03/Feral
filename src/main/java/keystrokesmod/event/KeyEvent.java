package keystrokesmod.event;

import keystrokesmod.platform.ornithe.ForgeEvents;

public class KeyEvent extends ForgeEvents.Event {
    public String keyName;
    public int keyCode;
    public boolean state;
    public boolean inGui;

    public KeyEvent(String keyName, int keyCode, boolean state, boolean inGui) {
        this.keyName = keyName;
        this.keyCode = keyCode;
        this.state = state;
        this.inGui = inGui;
    }
}
