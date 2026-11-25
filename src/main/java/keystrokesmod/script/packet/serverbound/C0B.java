package keystrokesmod.script.packet.serverbound;

import keystrokesmod.utility.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.packet.c2s.play.PlayerMovementActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMovementActionC2SPacket.Action;

public class C0B extends CPacket {
    public String action;
    public int horsePower;

    public C0B(String action, int horsePower) {
        super(null);
        this.action = action;
        this.horsePower = horsePower;
    }

    public C0B(PlayerMovementActionC2SPacket packet) {
        super(packet);
        this.action = packet.getAction().name();
        this.horsePower = packet.getData();
    }

    public PlayerMovementActionC2SPacket convert() {
        return new PlayerMovementActionC2SPacket(
                Minecraft.getInstance().player, Utils.getEnum(Action.class, this.action), this.horsePower);
    }
}
