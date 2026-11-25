package keystrokesmod.script.packet.serverbound;

import keystrokesmod.script.ScriptClassNames;
import keystrokesmod.script.packet.PacketMappings;
import keystrokesmod.script.packet.clientbound.S23;
import keystrokesmod.script.packet.clientbound.S3A;
import keystrokesmod.script.packet.clientbound.SPacket;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ArmSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.SelectSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.CommandSuggestionsS2CPacket;

public class PacketHandler {
    public static CPacket convertServerBound(Packet packet) {
        if (packet != null && !ScriptClassNames.simpleName(packet.getClass()).startsWith("S")) {
            Class<? extends CPacket> asClass = PacketMappings.minecraftToScriptC.get(packet.getClass());
            CPacket newPacket;
            if (asClass != null) {
                if (packet instanceof PlayerMoveC2SPacket) {
                    newPacket = new C03(
                            (PlayerMoveC2SPacket) packet, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0);
                } else if (packet instanceof ChatMessageC2SPacket) {
                    newPacket = new C01((ChatMessageC2SPacket) packet, (byte) 0);
                } else if (packet instanceof SelectSlotC2SPacket) {
                    newPacket = new C09((SelectSlotC2SPacket) packet, true);
                } else {
                    try {
                        newPacket = asClass.getConstructor(packet.getClass()).newInstance(packet);
                    } catch (Exception e) {
                        newPacket = new CPacket(packet);
                    }
                }
            } else {
                newPacket = new CPacket(packet);
            }

            return newPacket;
        } else {
            return null;
        }
    }

    public static SPacket convertClientBound(Packet packet) {
        Class<? extends SPacket> asClass = PacketMappings.minecraftToScriptS.get(packet.getClass());
        SPacket newPacket;
        if (asClass != null) {
            if (packet instanceof CommandSuggestionsS2CPacket) {
                newPacket = new S3A((CommandSuggestionsS2CPacket) packet, (byte) 0);
            } else if (packet instanceof BlockUpdateS2CPacket) {
                newPacket = new S23((BlockUpdateS2CPacket) packet, (byte) 0);
            } else {
                try {
                    newPacket = asClass.getConstructor(packet.getClass()).newInstance(packet);
                } catch (Exception e) {
                    newPacket = new SPacket(packet);
                }
            }
        } else {
            newPacket = new SPacket(packet);
        }

        return newPacket;
    }

    public static Packet convertCPacket(CPacket cPacket) {
        try {
            if (cPacket instanceof C0A) {
                return new ArmSwingC2SPacket();
            }

            if (cPacket instanceof C0B) {
                return ((C0B) cPacket).convert();
            }

            if (cPacket instanceof C0D) {
                return ((C0D) cPacket).convert();
            }

            if (cPacket instanceof C09) {
                return ((C09) cPacket).convert();
            }

            if (cPacket instanceof C0E) {
                return ((C0E) cPacket).convert();
            }

            if (cPacket instanceof C0F) {
                return ((C0F) cPacket).convert();
            }

            if (cPacket instanceof C08) {
                return ((C08) cPacket).convert();
            }

            if (cPacket instanceof C07) {
                return ((C07) cPacket).convert();
            }

            if (cPacket instanceof C01) {
                return ((C01) cPacket).convert();
            }

            if (cPacket instanceof C02) {
                return ((C02) cPacket).convert();
            }

            if (cPacket instanceof C03) {
                return cPacket.packet;
            }

            if (cPacket instanceof C10) {
                return ((C10) cPacket).convert();
            }

            if (cPacket instanceof C13) {
                return ((C13) cPacket).convert();
            }

            if (cPacket instanceof C16) {
                return ((C16) cPacket).convert();
            }
        } catch (Exception e) {
            if (cPacket != null && cPacket.packet != null && !cPacket.name.startsWith("S")) {
                return cPacket.packet;
            }

            return null;
        }

        return cPacket == null && cPacket.packet == null ? null : cPacket.packet;
    }
}
