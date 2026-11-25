package keystrokesmod.event;

import keystrokesmod.platform.ornithe.ForgeEvents;

public class ClientLookEvent extends ForgeEvents.Event {
    public float yaw;
    public float pitch;

    public ClientLookEvent(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }
}
