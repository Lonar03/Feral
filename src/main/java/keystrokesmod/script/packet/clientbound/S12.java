package keystrokesmod.script.packet.clientbound;

import keystrokesmod.script.model.Vec3;
import net.minecraft.network.packet.s2c.play.EntityVelocityS2CPacket;

public class S12 extends SPacket {
    public int entityId;
    public Vec3 motion;

    public S12(EntityVelocityS2CPacket packet) {
        super(packet);
        this.entityId = packet.getId();
        this.motion = new Vec3(packet.getVelocityX(), packet.getVelocityY(), packet.getVelocityZ());
    }

    public S12(int entityId, Vec3 motion) {
        super(new EntityVelocityS2CPacket(entityId, motion.x, motion.y, motion.z));
        this.entityId = entityId;
        this.motion = motion;
    }
}
