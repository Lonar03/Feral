package keystrokesmod.script.packet.clientbound;

import net.minecraft.network.packet.s2c.play.PlayerHealthS2CPacket;

public class S06 extends SPacket {
    public float health;
    public float saturation;
    public int food;

    public S06(PlayerHealthS2CPacket packet) {
        super(packet);
        this.health = packet.getHealth();
        this.saturation = packet.getSaturation();
        this.food = packet.getHunger();
    }

    public S06(float health, float saturation, int food) {
        super(new PlayerHealthS2CPacket(health, food, saturation));
        this.health = health;
        this.saturation = saturation;
        this.food = food;
    }
}
