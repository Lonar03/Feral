package keystrokesmod.event;

import keystrokesmod.platform.ornithe.ForgeEvents;

public class PrePlayerMovementInputEvent extends ForgeEvents.Event {
    public float forward;
    public float strafe;

    public PrePlayerMovementInputEvent(float forward, float strafe) {
        this.forward = forward;
        this.strafe = strafe;
    }
}
