package keystrokesmod.event;

import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.entity.Entity;

public class AntiCheatFlagEvent extends ForgeEvents.Event {
    public String flag;
    public Entity entity;

    public AntiCheatFlagEvent(String flag, Entity entity) {
        this.flag = flag;
        this.entity = entity;
    }
}
