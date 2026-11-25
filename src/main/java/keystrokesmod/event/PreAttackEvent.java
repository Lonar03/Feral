package keystrokesmod.event;

import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.world.HitResult;

@ForgeEvents.Cancelable
public class PreAttackEvent extends ForgeEvents.Event {
    public final HitResult objectMouseOver;

    public PreAttackEvent(HitResult objectMouseOver) {
        this.objectMouseOver = objectMouseOver;
    }
}
