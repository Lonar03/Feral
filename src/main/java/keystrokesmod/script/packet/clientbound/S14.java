package keystrokesmod.script.packet.clientbound;

import keystrokesmod.mixin.impl.accessor.IAccessorS14PacketEntity;
import net.minecraft.network.packet.s2c.play.EntityMoveS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityMoveS2CPacket.Angles;
import net.minecraft.network.packet.s2c.play.EntityMoveS2CPacket.Position;
import net.minecraft.network.packet.s2c.play.EntityMoveS2CPacket.PositionAndAngles;

public class S14 extends SPacket {
    public int entityId;
    public byte posX;
    public byte posY;
    public byte posZ;
    public byte yaw;
    public byte pitch;
    public boolean onGround;
    public boolean rotating;

    public S14(EntityMoveS2CPacket e) {
        super(e);
        this.entityId = ((IAccessorS14PacketEntity) e).getEntityId();
        this.posX = e.getDx();
        this.posY = e.getDy();
        this.posZ = e.getDz();
        this.yaw = e.getYaw();
        this.pitch = e.getPitch();
        this.rotating = e.hasAngles();
    }

    public S14(int entityId, byte posX, byte posY, byte posZ, byte yaw, byte pitch, boolean onGround) {
        super(new PositionAndAngles(entityId, posX, posY, posZ, yaw, pitch, onGround));
        this.entityId = entityId;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.yaw = yaw;
        this.pitch = pitch;
        this.onGround = onGround;
    }

    public S14(int entityId, byte posX, byte posY, byte posZ, boolean onGround) {
        super(new Position(entityId, posX, posY, posZ, onGround));
        this.entityId = entityId;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.onGround = onGround;
    }

    public S14(int entityId, byte yaw, byte pitch, boolean onGround) {
        super(new Angles(entityId, yaw, pitch, onGround));
        this.entityId = entityId;
        this.yaw = yaw;
        this.pitch = pitch;
        this.onGround = onGround;
    }
}
