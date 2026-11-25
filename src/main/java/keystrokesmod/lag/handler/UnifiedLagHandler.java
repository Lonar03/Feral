package keystrokesmod.lag.handler;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import keystrokesmod.event.GameTickEvent;
import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.lag.api.EnumLagDirection;
import keystrokesmod.lag.api.LagRequest;
import keystrokesmod.lag.queue.BiTrackLagNodeQueue;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.PacketUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class UnifiedLagHandler extends AbstractFastTrackProvider {
    @NotNull
    private final BiTrackLagNodeQueue queue = new BiTrackLagNodeQueue(this);

    @NotNull
    private final Set<Packet<?>> packetFastTrack =
            Collections.newSetFromMap(Collections.synchronizedMap(new IdentityHashMap<>()));

    @Nullable
    private volatile Vec3d serverPosition;

    public void requestLag(@NotNull LagRequest request) {
        this.queue.requestLag(request);
    }

    public void sendThroughLag(@NotNull Packet<?> packet) {
        if (Minecraft.getInstance().getNetworkHandler() != null) {
            if (!this.queue.tick(packet, EnumLagDirection.OUTBOUND, true)) {
                PacketUtils.sendPacketNoEvent(packet);
            }
        }
    }

    public boolean isBuffering() {
        return this.queue.isBuffering();
    }

    public void releaseExpiredPackets(@NotNull EnumLagDirection direction, long maxAgeMs) {
        this.queue.releaseExpiredPackets(direction, maxAgeMs);
    }

    public void releaseNextPacket(@NotNull EnumLagDirection direction) {
        this.queue.releaseNextPacket(direction);
    }

    @Nullable
    public Vec3d getLastReleasedServerPosition() {
        return this.serverPosition;
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onSendPacket(@NotNull SendPacketEvent event) {
        if (Minecraft.getInstance().getNetworkHandler() == null) {
            this.queue.clear();
            this.clearServerPositions();
        } else {
            Packet<?> packet = event.getPacket();
            boolean fastTracked = this.consumeFastTrack(packet);
            if (!event.isCanceled()) {
                if (fastTracked) {
                    this.updateServerPosition(packet);
                } else if (this.queue.tick(packet, EnumLagDirection.OUTBOUND)) {
                    event.setCanceled(true);
                } else {
                    this.updateServerPosition(packet);
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onReceivePacket(@NotNull ReceivePacketEvent event) {
        if (Minecraft.getInstance().getNetworkHandler() == null) {
            this.queue.clear();
            this.clearServerPositions();
        } else {
            Packet<?> packet = event.getPacket();
            boolean fastTracked = this.consumeFastTrack(packet);
            if (!event.isCanceled()) {
                if (!fastTracked) {
                    if (this.queue.tick(packet, EnumLagDirection.INBOUND)) {
                        event.setCanceled(true);
                    }
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onGameTick(@NotNull GameTickEvent event) {
        if (Minecraft.getInstance().getNetworkHandler() == null) {
            this.queue.clear();
            this.clearServerPositions();
        } else {
            this.queue.tick(null, null);
        }
    }

    @Override
    public void forPacket(@NotNull Packet<?> packet) {
        this.packetFastTrack.add(packet);
    }

    private boolean consumeFastTrack(@NotNull Packet<?> packet) {
        return this.packetFastTrack.remove(packet);
    }

    private void updateServerPosition(@NotNull Packet<?> packet) {
        if (packet instanceof PlayerMoveC2SPacket) {
            PlayerMoveC2SPacket movementPacket = (PlayerMoveC2SPacket) packet;
            if (movementPacket.hasPos()) {
                this.serverPosition = new Vec3d(movementPacket.getX(), movementPacket.getMinY(), movementPacket.getZ());
            }
        }
    }

    private void clearServerPositions() {
        this.serverPosition = null;
    }
}
