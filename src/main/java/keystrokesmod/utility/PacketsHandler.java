package keystrokesmod.utility;

import java.util.concurrent.atomic.AtomicInteger;
import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.SelectSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.AddPlayerS2CPacket;
import net.minecraft.network.packet.s2c.play.SelectSlotS2CPacket;

public class PacketsHandler implements IMinecraftInstance {
    public AtomicInteger playerSlot = new AtomicInteger(-1);
    public AtomicInteger serverSlot = new AtomicInteger(-1);
    private final boolean handleSlots = true;

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGH)
    public void onSendPacket(SendPacketEvent e) {
        if (!e.isCanceled()) {
            Packet<?> packet = e.getPacket();
            if (packet instanceof SelectSlotC2SPacket) {
                SelectSlotC2SPacket slotPacket = (SelectSlotC2SPacket) packet;
                int slotId = slotPacket.getSlot();
                this.playerSlot.set(slotId);
                this.serverSlot.set(slotId);
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onReceivePacket(ReceivePacketEvent e) {
        if (e.getPacket() instanceof SelectSlotS2CPacket) {
            SelectSlotS2CPacket packet = (SelectSlotS2CPacket) e.getPacket();
            int index = packet.getSlot();
            if (index >= 0 && index < PlayerInventory.getHotbarSize()) {
                this.serverSlot.set(index);
            }
        } else if (e.getPacket() instanceof AddPlayerS2CPacket && Minecraft.getInstance().player != null) {
            AddPlayerS2CPacket packet = (AddPlayerS2CPacket) e.getPacket();
            if (packet.getId() != Minecraft.getInstance().player.getNetworkId()) {
                return;
            }

            this.playerSlot.set(-1);
        }
    }
}
