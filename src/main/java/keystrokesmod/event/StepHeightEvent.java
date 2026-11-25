package keystrokesmod.event;

import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.entity.Entity;

public class StepHeightEvent extends ForgeEvents.Event {
    public Entity entity;
    public float stepHeight;

    public StepHeightEvent(Entity entity, float stepHeight) {
        this.entity = entity;
        this.stepHeight = stepHeight;
    }
}
