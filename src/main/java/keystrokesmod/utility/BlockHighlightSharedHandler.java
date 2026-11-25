package keystrokesmod.utility;

import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.client.Minecraft;

public final class BlockHighlightSharedHandler {
    private static final Minecraft mc = Minecraft.getInstance();

    @ForgeEvents.SubscribeEvent
    public void onReceivePacket(ReceivePacketEvent e) {
        SharedBlockHighlightCache.get().handleReceivePacket(e);
    }

    @ForgeEvents.SubscribeEvent
    public void onClientTick(ForgeEvents.TickEvent.ClientTickEvent e) {
        if (e.phase == ForgeEvents.TickEvent.Phase.END && Utils.nullCheck()) {
            SharedBlockHighlightCache cache = SharedBlockHighlightCache.get();
            if (cache.anyConsumerActive()) {
                int budget = 0;
                if (ModuleManager.blockESP != null) {
                    budget = Math.max(budget, ModuleManager.blockESP.getScanSpeedBudget());
                }

                if (ModuleManager.bedESP != null) {
                    budget = Math.max(budget, ModuleManager.bedESP.getScanSpeedBudget());
                }

                if (budget > 0) {
                    cache.tickScan(budget);
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onEntityJoinWorld(ForgeEvents.EntityJoinWorldEvent e) {
        if (e.entity == mc.player) {
            SharedBlockHighlightCache cache = SharedBlockHighlightCache.get();
            if (cache.anyConsumerActive()) {
                cache.clear();
                cache.enqueueLoadedChunks();
            }
        }
    }
}
