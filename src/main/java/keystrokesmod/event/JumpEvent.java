package keystrokesmod.event;

import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.entity.living.LivingEntity;

@ForgeEvents.Cancelable
public class JumpEvent extends ForgeEvents.Event {
    public LivingEntity entity;
    private float motionY;
    private float yaw;
    private boolean applySprint;

    public JumpEvent(LivingEntity entity, float motionY, float yaw, boolean applySprint) {
        this.entity = entity;
        this.motionY = motionY;
        this.yaw = yaw;
        this.applySprint = applySprint;
    }

    public float getMotionY() {
        return this.motionY;
    }

    public void setMotionY(float motionY) {
        this.motionY = motionY;
    }

    public float getYaw() {
        return this.yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public boolean applySprint() {
        return this.applySprint;
    }

    public void setSprint(boolean applySprint) {
        this.applySprint = applySprint;
    }
}
