package keystrokesmod.script.packet.serverbound;

import keystrokesmod.script.model.Entity;
import keystrokesmod.script.model.Vec3;
import keystrokesmod.utility.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket.Action;
import net.minecraft.util.math.Vec3d;

public class C02 extends CPacket {
    public Entity entity;
    public String action;
    public Vec3 hitVec;

    public C02(Entity entity, String action, Vec3 hitVec) {
        super(null);
        this.entity = entity;
        this.action = action;
        this.hitVec = hitVec;
    }

    public C02(PlayerInteractEntityC2SPacket packet) {
        super(packet);
        if (packet.getInteractTarget(Minecraft.getInstance().world) == null) {
            this.entity = null;
        } else {
            this.entity = Entity.convert(packet.getInteractTarget(Minecraft.getInstance().world));
        }

        this.action = packet.getAction().name();
        if (packet.getOffset() != null) {
            this.hitVec = new Vec3(packet.getOffset().x, packet.getOffset().y, packet.getOffset().z);
        }
    }

    public PlayerInteractEntityC2SPacket convert() {
        Action action = Utils.getEnum(Action.class, this.action);
        return this.hitVec != null && action == Action.INTERACT_AT
                ? new PlayerInteractEntityC2SPacket(
                        this.entity.entity, new Vec3d(this.hitVec.x, this.hitVec.y, this.hitVec.z))
                : new PlayerInteractEntityC2SPacket(this.entity.entity, action);
    }
}
