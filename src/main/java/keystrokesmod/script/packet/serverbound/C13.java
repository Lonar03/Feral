package keystrokesmod.script.packet.serverbound;

import net.minecraft.entity.living.player.PlayerAbilities;
import net.minecraft.network.packet.c2s.play.PlayerAbilitiesC2SPacket;

public class C13 extends CPacket {
    public boolean invulnerable;
    public boolean flying;
    public boolean allowFlying;
    public boolean creativeMode;
    public float flySpeed;
    public float walkSpeed;

    public C13(
            boolean invulnerable,
            boolean flying,
            boolean allowFlying,
            boolean creativeMode,
            float flySpeed,
            float walkSpeed) {
        super(null);
        this.invulnerable = invulnerable;
        this.flying = flying;
        this.allowFlying = allowFlying;
        this.creativeMode = creativeMode;
        this.flySpeed = flySpeed;
        this.walkSpeed = walkSpeed;
    }

    public C13(PlayerAbilitiesC2SPacket packet) {
        super(packet);
    }

    public PlayerAbilitiesC2SPacket convert() {
        PlayerAbilities capabilities = new PlayerAbilities();
        capabilities.invulnerable = this.invulnerable;
        capabilities.flying = this.flying;
        capabilities.canFly = this.allowFlying;
        capabilities.creativeMode = this.creativeMode;
        capabilities.setFlySpeed(this.flySpeed);
        capabilities.setWalkSpeed(this.walkSpeed);
        return new PlayerAbilitiesC2SPacket(capabilities);
    }
}
