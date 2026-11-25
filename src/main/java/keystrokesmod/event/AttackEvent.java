package keystrokesmod.event;

import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;

@ForgeEvents.Cancelable
public class AttackEvent extends ForgeEvents.Event {
    public Entity target;
    public PlayerEntity attacker;
    public boolean swing;

    public AttackEvent(Entity target, PlayerEntity attacker, boolean swing) {
        this.target = target;
        this.attacker = attacker;
        this.swing = swing;
    }
}
