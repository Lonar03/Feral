package keystrokesmod.event;

import keystrokesmod.platform.ornithe.ForgeEvents;

public class PostSetSliderEvent extends ForgeEvents.Event {
    public double previousVal;
    public double newVal;

    public PostSetSliderEvent(double previousVal, double newVal) {
        this.previousVal = previousVal;
        this.newVal = newVal;
    }
}
