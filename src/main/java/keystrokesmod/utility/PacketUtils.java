package keystrokesmod.utility;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import keystrokesmod.Feral;
import keystrokesmod.platform.ClientPlatform;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerHandActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerHandActionC2SPacket.Action;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class PacketUtils implements IMinecraftInstance {
    private static final Set<Packet<?>> skipSendEvent =
            Collections.newSetFromMap(Collections.synchronizedMap(new IdentityHashMap<>()));
    private static final Set<Packet<?>> skipReceiveEvent =
            Collections.newSetFromMap(Collections.synchronizedMap(new IdentityHashMap<>()));

    public static boolean consumeSendEventSkip(Packet<?> packet) {
        return skipSendEvent.remove(packet);
    }

    public static boolean consumeReceiveEventSkip(Packet<?> packet) {
        return skipReceiveEvent.remove(packet);
    }

    public static void sendPacketNoEvent(Packet packet) {
        if (packet != null && !ClientPlatform.className(packet.getClass()).startsWith("S")) {
            skipSendEvent.add(packet);
            Feral.mc.player.networkHandler.sendPacket(packet);
        }
    }

    public static void receivePacketNoEvent(Packet packet) {
        try {
            packet.handle(Feral.mc.getNetworkHandler());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendReleasePacket() {
        mc.player.networkHandler.sendPacket(
                new PlayerHandActionC2SPacket(Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN));
    }
}
