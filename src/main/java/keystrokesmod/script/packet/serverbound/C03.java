package keystrokesmod.script.packet.serverbound;

import keystrokesmod.script.model.Vec3;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Angles;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Position;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndAngles;

public class C03 extends CPacket {
    public Vec3 position;
    public float yaw;
    public float pitch;
    public boolean ground;
    public boolean moving;
    public boolean rotating;

    public C03(PlayerMoveC2SPacket packet, byte f1, byte f2, byte f3, byte f4, byte f5, byte f6) {
        super(packet);
        if (packet instanceof Position || packet instanceof PositionAndAngles) {
            this.position = new Vec3(packet.getX(), packet.getMinY(), packet.getZ());
        }

        if (packet instanceof Angles || packet instanceof PositionAndAngles) {
            this.yaw = packet.getYaw();
            this.pitch = packet.getPitch();
        }

        this.ground = packet.getOnGround();
        this.moving = packet.hasPos();
        this.rotating = packet.hasAngles();
    }

    public C03(boolean ground) {
        super(new PlayerMoveC2SPacket(ground));
        this.ground = ground;
    }

    public C03(Vec3 position, boolean ground) {
        super(new Position(position.x, position.y, position.z, ground));
        this.position = position;
        this.ground = ground;
    }

    public C03(float yaw, float pitch, boolean ground) {
        super(new Angles(yaw, pitch, ground));
        this.yaw = yaw;
        this.pitch = pitch;
        this.ground = ground;
    }

    public C03(Vec3 position, float yaw, float pitch, boolean ground) {
        super(new PositionAndAngles(position.x, position.y, position.z, yaw, pitch, ground));
        this.position = position;
        this.yaw = yaw;
        this.pitch = pitch;
        this.ground = ground;
    }
}
