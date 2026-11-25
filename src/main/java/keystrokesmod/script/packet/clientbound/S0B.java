package keystrokesmod.script.packet.clientbound;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket;

public class S0B extends SPacket {
    public int entityId;
    public int type;

    public S0B(EntityAnimationS2CPacket packet) {
        super(packet);
        this.entityId = packet.getId();
        this.type = packet.getAction();
    }

    public S0B(int entityId, int type) {
        super(new EntityAnimationS2CPacket(createEntityWithCustomID(entityId), type));
        this.entityId = entityId;
        this.type = type;
    }

    private static Entity createEntityWithCustomID(int id) {
        RemoteClientPlayerEntity entity = new RemoteClientPlayerEntity(
                Minecraft.getInstance().world,
                new GameProfile(UUID.fromString("ae7a42d0-31b1-4926-ba4c-f28d6efad4a9"), ""));
        entity.setNetworkId(id);
        return entity;
    }
}
