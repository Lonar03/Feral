package keystrokesmod.event;

import keystrokesmod.platform.ornithe.ForgeEvents;

@ForgeEvents.Cancelable
public class KeyPressEvent extends ForgeEvents.Event {
    public char typedChar;
    public int keyCode;

    public KeyPressEvent(char typedChar, int keyCode) {
        this.typedChar = typedChar;
        this.keyCode = keyCode;
    }
}
