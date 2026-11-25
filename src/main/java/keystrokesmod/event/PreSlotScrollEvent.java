package keystrokesmod.event;

import keystrokesmod.platform.ornithe.ForgeEvents;

@ForgeEvents.Cancelable
public class PreSlotScrollEvent extends ForgeEvents.Event {
    public int slot;
    public int previousSlot;

    public PreSlotScrollEvent(int slot, int previousSlot) {
        this.slot = slot;
        this.previousSlot = previousSlot;
    }
}
