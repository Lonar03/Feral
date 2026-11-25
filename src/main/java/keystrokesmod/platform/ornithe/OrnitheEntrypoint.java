package keystrokesmod.platform.ornithe;

import java.util.function.Consumer;
import keystrokesmod.Feral;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.ornithemc.osl.lifecycle.api.client.MinecraftClientEvents;
import net.ornithemc.osl.networking.api.client.ClientConnectionEvents;
import net.ornithemc.osl.networking.api.client.ClientConnectionEvents.DisconnectContext;

public final class OrnitheEntrypoint implements ClientModInitializer {
    public void onInitializeClient() {
        MinecraftClientEvents.READY.register((Consumer<Minecraft>) client -> new Feral().initialize());
        MinecraftClientEvents.TICK_START.register(
                (Consumer<Minecraft>) client -> postTick(ForgeEvents.TickEvent.Phase.START));
        MinecraftClientEvents.TICK_END.register(
                (Consumer<Minecraft>) client -> postTick(ForgeEvents.TickEvent.Phase.END));
        ClientConnectionEvents.DISCONNECT.register(
                (Consumer<DisconnectContext>) context -> ForgeEvents.MinecraftForge.EVENT_BUS.post(
                        new ForgeEvents.FMLNetworkEvent.ClientDisconnectionFromServerEvent()));
    }

    private static void postTick(ForgeEvents.TickEvent.Phase phase) {
        ForgeEvents.MinecraftForge.EVENT_BUS.post(new ForgeEvents.TickEvent.ClientTickEvent(phase));
    }
}
