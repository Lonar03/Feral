package keystrokesmod.lag.queue.node.impl;

import keystrokesmod.lag.api.EnumLagDirection;
import keystrokesmod.lag.handler.AbstractFastTrackProvider;
import keystrokesmod.lag.queue.node.api.AbstractLagNode;
import keystrokesmod.utility.PacketUtils;
import net.minecraft.network.packet.Packet;
import org.jetbrains.annotations.NotNull;

public final class PacketLagNode extends AbstractLagNode {
    @NotNull
    private final Packet<?> packet;

    @NotNull
    private final EnumLagDirection direction;

    private final boolean sendWithoutEvent;
    private final long queuedAtMs;

    public PacketLagNode(@NotNull Packet<?> packet, @NotNull EnumLagDirection direction) {
        this(packet, direction, false);
    }

    public PacketLagNode(@NotNull Packet<?> packet, @NotNull EnumLagDirection direction, boolean sendWithoutEvent) {
        this.packet = packet;
        this.direction = direction;
        this.sendWithoutEvent = sendWithoutEvent;
        this.queuedAtMs = System.currentTimeMillis();
    }

    public long getQueuedAtMs() {
        return this.queuedAtMs;
    }

    public void goThrough(@NotNull AbstractFastTrackProvider fastTrack) {
        if (this.direction == EnumLagDirection.OUTBOUND) {
            if (this.sendWithoutEvent) {
                PacketUtils.sendPacketNoEvent(this.packet);
                return;
            }

            fastTrack.forPacket(this.packet);
        }

        this.direction.passThroughChannel(this.packet);
    }
}
