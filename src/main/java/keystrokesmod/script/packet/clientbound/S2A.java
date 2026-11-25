package keystrokesmod.script.packet.clientbound;

import keystrokesmod.script.model.Vec3;
import keystrokesmod.utility.Utils;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;

public class S2A extends SPacket {
    public String type;
    public Vec3 position;
    public Vec3 offset;
    public float speed;
    public int count;
    public boolean longDistance;
    public int[] args;

    public S2A(ParticleS2CPacket packet) {
        super(packet);
        this.type = packet.getType().name();
        this.longDistance = packet.getIgnoreDistance();
        this.position = new Vec3(packet.getX(), packet.getY(), packet.getZ());
        this.offset = new Vec3(packet.getVelocityX(), packet.getVelocityY(), packet.getVelocityZ());
        this.speed = packet.getVelocityScale();
        this.count = packet.getCount();
        this.args = packet.getParameters();
    }

    public S2A(String type, boolean longDistance, Vec3 position, Vec3 offset, float speed, int count, int[] args) {
        super(new ParticleS2CPacket(
                Utils.getEnum(ParticleType.class, type),
                longDistance,
                (float) position.x,
                (float) position.y,
                (float) position.z,
                (float) offset.x,
                (float) offset.y,
                (float) offset.z,
                speed,
                count,
                args));
        this.type = type;
        this.longDistance = longDistance;
        this.position = position;
        this.offset = offset;
        this.speed = speed;
        this.count = count;
        this.args = args;
    }
}
