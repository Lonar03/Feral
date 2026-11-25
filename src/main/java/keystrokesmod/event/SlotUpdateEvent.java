package keystrokesmod.event;

import keystrokesmod.platform.ornithe.ForgeEvents;

@ForgeEvents.Cancelable
public class SlotUpdateEvent extends ForgeEvents.Event {
    public int slot;

    public SlotUpdateEvent(int slot) {
        this.slot = slot;
    }
}
