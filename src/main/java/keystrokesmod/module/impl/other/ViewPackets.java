package keystrokesmod.module.impl.other;

import keystrokesmod.event.NoEventPacketEvent;
import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.platform.ClientPlatform;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.Utils;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientSettingsC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.network.packet.c2s.play.CreativeMenuSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CustomPayloadC2SPacket;
import net.minecraft.network.packet.c2s.play.InventoryMenuClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.InventoryMenuConfirmC2SPacket;
import net.minecraft.network.packet.c2s.play.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerHandActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMovementActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerUseC2SPacket;
import net.minecraft.network.packet.c2s.play.SelectSlotC2SPacket;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.HoverEvent.Action;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class ViewPackets extends Module {
    private ButtonSetting includeCancelled;
    private ButtonSetting singlePlayer;
    private ButtonSetting sent;
    private ButtonSetting ignoreC00;
    private ButtonSetting ignoreC03;
    private ButtonSetting compactC03;
    private ButtonSetting ignoreC0F;
    private ButtonSetting received;
    private Packet packet;
    public static long tick;

    public ViewPackets() {
        super("View Packets", Module.category.other);
        this.registerSetting(this.includeCancelled = new ButtonSetting("Include cancelled", true));
        this.registerSetting(this.singlePlayer = new ButtonSetting("Singleplayer", false));
        this.registerSetting(this.sent = new ButtonSetting("Sent", false));
        this.registerSetting(this.ignoreC00 = new ButtonSetting("Ignore C00", false));
        this.registerSetting(this.ignoreC03 = new ButtonSetting("Ignore C03", false));
        this.registerSetting(this.compactC03 = new ButtonSetting("Compact C03", false));
        this.registerSetting(this.ignoreC0F = new ButtonSetting("Ignore C0F", false));
        this.registerSetting(this.received = new ButtonSetting("Received", false));
    }

    @Override
    public void onDisable() {
        this.packet = null;
        tick = 0L;
    }

    private static String formatBoolean(boolean b) {
        return b ? "&atrue" : "&cfalse";
    }

    private void sendMessage(Packet packet, boolean b) {
        if (Utils.nullCheck()) {
            String s = b ? "&a" + packet.getClass().getSimpleName() : this.applyInfo(packet);
            String string = (this.compactC03.isToggled() && packet instanceof PlayerMoveC2SPacket ? "&6" : "&d")
                    + packet.getClass().getSimpleName();
            LiteralText chatComponentText = new LiteralText(
                    Utils.formatColor("&7[&dF&7]&r &7" + (b ? "Received" : "Sent") + " packet (t:&b" + tick + "&7): "));
            Style chatStyle = new Style();
            chatStyle.setHoverEvent(new HoverEvent(Action.SHOW_TEXT, new LiteralText(Utils.formatColor(s))));
            chatComponentText.append(new LiteralText(Utils.formatColor(string)).setStyle(chatStyle));
            mc.player.sendMessage(chatComponentText);
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onSendPacket(SendPacketEvent e) {
        if (this.sent.isToggled()) {
            if (!this.singlePlayer.isToggled()
                    || !mc.isSingleplayer()
                    || e.getPacket().getClass().getSimpleName().charAt(0) != 'S') {
                if (!e.isCanceled() || this.includeCancelled.isToggled()) {
                    if (!this.ignoreC00.isToggled() || !(e.getPacket() instanceof KeepAliveC2SPacket)) {
                        if (!this.ignoreC0F.isToggled() || !(e.getPacket() instanceof InventoryMenuConfirmC2SPacket)) {
                            if (!(e.getPacket() instanceof PlayerMoveC2SPacket)
                                    || !this.ignoreC03.isToggled()
                                            && (!this.compactC03.isToggled()
                                                    || this.packet != null
                                                            && !(this.packet instanceof PlayerMoveC2SPacket))) {
                                this.sendMessage(this.packet = e.getPacket(), false);
                            }
                        }
                    }
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onSendPacketNoEvent(NoEventPacketEvent e) {
        if (this.sent.isToggled()) {
            if (!this.singlePlayer.isToggled()
                    || !mc.isSingleplayer()
                    || e.getPacket().getClass().getSimpleName().charAt(0) != 'S') {
                if (!e.isCanceled() || this.includeCancelled.isToggled()) {
                    if (!this.ignoreC00.isToggled() || !(e.getPacket() instanceof KeepAliveC2SPacket)) {
                        if (!this.ignoreC0F.isToggled() || !(e.getPacket() instanceof InventoryMenuConfirmC2SPacket)) {
                            if (!(e.getPacket() instanceof PlayerMoveC2SPacket)
                                    || !this.ignoreC03.isToggled()
                                            && (!this.compactC03.isToggled()
                                                    || this.packet != null
                                                            && !(this.packet instanceof PlayerMoveC2SPacket))) {
                                this.sendMessage(this.packet = e.getPacket(), false);
                            }
                        }
                    }
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onReceivePacket(ReceivePacketEvent e) {
        if (this.received.isToggled()) {
            if (!this.singlePlayer.isToggled()
                    || !mc.isSingleplayer()
                    || e.getPacket().getClass().getSimpleName().charAt(0) != 'C') {
                this.sendMessage(e.getPacket(), true);
            }
        }
    }

    private String applyInfo(Packet packet) {
        String s = "&a" + packet.getClass().getSimpleName();
        if (packet instanceof PlayerHandActionC2SPacket) {
            PlayerHandActionC2SPacket c07PacketPlayerDigging = (PlayerHandActionC2SPacket) packet;
            String string =
                    s + "\n&7Status: &b" + c07PacketPlayerDigging.getAction().name() + "\n&7Facing: &b"
                            + c07PacketPlayerDigging.getFace().name();
            BlockPos getPosition = c07PacketPlayerDigging.getPos();
            s = string + "\n&7Position: &b" + getPosition.getX() + "&7, &b" + getPosition.getY() + "&7, &b"
                    + getPosition.getZ();
        } else if (packet instanceof SelectSlotC2SPacket) {
            s = s + "\n&7Swap to slot: &b" + ((SelectSlotC2SPacket) packet).getSlot();
        } else if (packet instanceof PlayerMovementActionC2SPacket) {
            s = s + "\n&7Action: &b"
                    + ((PlayerMovementActionC2SPacket) packet).getAction().name() + "\n&7Aux data: &b"
                    + ((PlayerMovementActionC2SPacket) packet).getData();
        } else if (packet instanceof PlayerUseC2SPacket) {
            PlayerUseC2SPacket c08PacketPlayerBlockPlacement = (PlayerUseC2SPacket) packet;
            String string2 = s
                    + "\n&7Item: &b"
                    + (c08PacketPlayerBlockPlacement.getItemInHand() == null
                            ? "null"
                            : ClientPlatform.registryName(c08PacketPlayerBlockPlacement
                                            .getItemInHand()
                                            .getItem())
                                    .replace("minecraft:", ""))
                    + "\n&7Direction: &b"
                    + c08PacketPlayerBlockPlacement.getFace();
            BlockPos getPosition = c08PacketPlayerBlockPlacement.getPos();
            s = string2
                    + "\n&7Position: &b"
                    + (getPosition == null
                            ? "null"
                            : getPosition.getX() + "&7, &b" + getPosition.getY() + "&7, &b" + getPosition.getZ())
                    + "\n&7Offset: &b"
                    + round(c08PacketPlayerBlockPlacement.getFaceX())
                    + "&7, &b"
                    + round(c08PacketPlayerBlockPlacement.getFaceY())
                    + "&7, &b"
                    + round(c08PacketPlayerBlockPlacement.getFaceZ());
        } else if (packet instanceof PlayerInteractEntityC2SPacket) {
            PlayerInteractEntityC2SPacket c02PacketUseEntity = (PlayerInteractEntityC2SPacket) packet;
            String string3 =
                    s + "\n&7Action: &b" + c02PacketUseEntity.getAction().name();
            Entity getEntityFromWorld = c02PacketUseEntity.getInteractTarget(mc.world);
            String string4 =
                    string3 + "\n&7Target: &b" + (getEntityFromWorld == null ? "null" : getEntityFromWorld.getName());
            Vec3d getHitVec = c02PacketUseEntity.getOffset();
            if (getHitVec == null) {
                s = string4 + "\n&7Hit vec: &bnull";
            } else {
                s = string4 + "\n&7Hit vec: &b" + round(getHitVec.x) + "&7, &b" + round(getHitVec.y) + "&7, &b"
                        + round(getHitVec.z);
            }
        } else if (packet instanceof ChatMessageC2SPacket) {
            s = s + "\n&7Length: &b"
                    + ((ChatMessageC2SPacket) packet).getMessage().length();
        } else if (packet instanceof CustomPayloadC2SPacket) {
            s = s + "\n&7Channel: &b" + ((CustomPayloadC2SPacket) packet).getChannel();
        } else if (packet instanceof ClientSettingsC2SPacket) {
            s = s + "\n&7Language: &b" + ((ClientSettingsC2SPacket) packet).getLanguage() + "\n&7Chat visibility: &b"
                    + ((ClientSettingsC2SPacket) packet).getChatVisibility().name();
        } else if (packet instanceof KeepAliveC2SPacket) {
            s = s + "\n&7Key: &b" + ((KeepAliveC2SPacket) packet).getTimeMillis();
        } else if (packet instanceof ClientStatusC2SPacket) {
            s = s + "\n&7Status: &b"
                    + ((ClientStatusC2SPacket) packet).getStatus().name();
        } else if (packet instanceof CreativeMenuSlotC2SPacket) {
            s = s
                    + "\n&7Slot: &b"
                    + ((CreativeMenuSlotC2SPacket) packet).getSlotId()
                    + "\n&7Item: &b"
                    + (((CreativeMenuSlotC2SPacket) packet).getItem() == null
                            ? "null"
                            : ClientPlatform.registryName(((CreativeMenuSlotC2SPacket) packet)
                                            .getItem()
                                            .getItem())
                                    .replace("minecraft:", ""));
        } else if (packet instanceof InventoryMenuClickSlotC2SPacket) {
            InventoryMenuClickSlotC2SPacket c0EPacketClickWindow = (InventoryMenuClickSlotC2SPacket) packet;
            s = s
                    + "\n&7Window: &b"
                    + c0EPacketClickWindow.getMenuId()
                    + "\n&7Slot: &b"
                    + c0EPacketClickWindow.getSlot()
                    + "\n&7Button: &b"
                    + c0EPacketClickWindow.getClickData()
                    + "\n&7Action: &b"
                    + c0EPacketClickWindow.getActionId()
                    + "\n&7Mode: &b"
                    + c0EPacketClickWindow.getAction()
                    + "\n&7Item: &b"
                    + (c0EPacketClickWindow.getItem() == null
                            ? "null"
                            : ClientPlatform.registryName(
                                            c0EPacketClickWindow.getItem().getItem())
                                    .replace("minecraft:", ""));
        } else if (packet instanceof InventoryMenuConfirmC2SPacket) {
            s = s + "\n&7Window: &b" + ((InventoryMenuConfirmC2SPacket) packet).getMenuId() + "\n&7Uid: &b"
                    + ((InventoryMenuConfirmC2SPacket) packet).getActionId();
        } else if (packet instanceof PlayerMoveC2SPacket) {
            PlayerMoveC2SPacket c03PacketPlayer = (PlayerMoveC2SPacket) packet;
            s = s
                    + "\n&7Position: &b"
                    + round(c03PacketPlayer.getX())
                    + "&7, &b"
                    + round(c03PacketPlayer.getMinY())
                    + "&7, &b"
                    + round(c03PacketPlayer.getZ())
                    + "\n&7Rotations: &b"
                    + round(c03PacketPlayer.getYaw())
                    + "&7, &b"
                    + round(c03PacketPlayer.getPitch())
                    + "\n&7Ground: "
                    + formatBoolean(c03PacketPlayer.getOnGround())
                    + "\n&7Moving: "
                    + formatBoolean(c03PacketPlayer.hasPos())
                    + "\n&7Rotating: "
                    + formatBoolean(c03PacketPlayer.hasAngles());
        }

        return s + "\n&7Client tick: &e" + tick;
    }

    private static double round(double n) {
        return Utils.round(n, 3);
    }

    @ForgeEvents.SubscribeEvent
    public void onTick(ForgeEvents.TickEvent.ClientTickEvent e) {
        if (e.phase == ForgeEvents.TickEvent.Phase.START) {
            tick++;
        }
    }
}
