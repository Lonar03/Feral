package keystrokesmod.event;

import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.network.packet.s2c.play.PlayerMoveS2CPacket;

@ForgeEvents.Cancelable
public class PlayerTeleportEvent extends ForgeEvents.Event {
    public PlayerMoveS2CPacket packet;

    public PlayerTeleportEvent(PlayerMoveS2CPacket packetIn) {
        this.packet = packetIn;
    }
}
