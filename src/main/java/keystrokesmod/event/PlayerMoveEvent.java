package keystrokesmod.event;

import keystrokesmod.platform.ornithe.ForgeEvents;

public class PlayerMoveEvent extends ForgeEvents.Event {
    public double x;
    public double y;
    public double z;

    public PlayerMoveEvent(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
