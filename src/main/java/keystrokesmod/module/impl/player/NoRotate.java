package keystrokesmod.module.impl.player;

import keystrokesmod.event.PlayerTeleportEvent;
import keystrokesmod.module.Module;
import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.network.packet.s2c.play.PlayerMoveS2CPacket;

public class NoRotate extends Module {
    private float prevPitch = 0.0F;
    private float prevYaw = 0.0F;

    public NoRotate() {
        super("NoRotate", Module.category.player);
    }

    @ForgeEvents.SubscribeEvent
    public void onPrePlayerTeleport(PlayerTeleportEvent event) {
        if (this.isEnabled()) {
            if (mc.player != null && mc.player.pitch != 0.0F) {
                this.prevPitch = mc.player.pitch % 360.0F;
                this.prevYaw = mc.player.yaw % 360.0F;
            }
        }
    }

    public void handlePlayerPosLook(PlayerMoveS2CPacket packet) {
        if (this.isEnabled()) {
            if (packet.getPitch() != 0.0F && mc.player != null) {
                mc.player.lastYaw = this.prevYaw;
                mc.player.lastPitch = this.prevPitch;
                mc.player.pitch = this.prevPitch;
                mc.player.yaw = this.prevYaw;
            }
        }
    }
}
