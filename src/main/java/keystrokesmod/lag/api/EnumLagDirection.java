package keystrokesmod.lag.api;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;
import keystrokesmod.platform.ClientPlatform;
import keystrokesmod.utility.IMinecraftInstance;
import keystrokesmod.utility.Utils;
import net.minecraft.network.DifferentThreadException;
import net.minecraft.network.packet.Packet;
import org.jetbrains.annotations.NotNull;

public enum EnumLagDirection implements IMinecraftInstance {
    INBOUND(packet -> {
        try {
            ((Packet) packet).handle(mc.getNetworkHandler());
        } catch (DifferentThreadException var2) {
        } catch (Exception e) {
            Utils.sendDebugMessage("error while handling packet: " + ClientPlatform.className(packet.getClass()));
        }
    }),
    OUTBOUND(packet -> mc.getNetworkHandler().sendPacket(packet));

    @NotNull
    public static final Set<EnumLagDirection> ONLY_INBOUND = EnumSet.of(INBOUND);

    @NotNull
    public static final Set<EnumLagDirection> ONLY_OUTBOUND = EnumSet.of(OUTBOUND);

    @NotNull
    public static final Set<EnumLagDirection> BIDIRECTIONAL = EnumSet.allOf(EnumLagDirection.class);

    @NotNull
    private final Consumer<Packet<?>> channel;

    EnumLagDirection(@NotNull final Consumer<Packet<?>> channel) {
        this.channel = channel;
    }

    public void passThroughChannel(@NotNull Packet<?> packet) {
        this.channel.accept(packet);
    }
}
