package keystrokesmod.script.packet.serverbound;

import keystrokesmod.script.model.Vec3;
import keystrokesmod.utility.Utils;
import net.minecraft.network.packet.c2s.play.PlayerHandActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerHandActionC2SPacket.Action;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class C07 extends CPacket {
    public Vec3 position;
    public String status;
    public String facing;

    public C07(Vec3 position, String status, String facing) {
        super(null);
        this.position = position;
        this.status = status;
        this.facing = facing;
    }

    public C07(PlayerHandActionC2SPacket packet) {
        super(packet);
        this.position = Vec3.convert(packet.getPos());
        this.status = packet.getAction().name();
        this.facing = packet.getFace().name();
    }

    public PlayerHandActionC2SPacket convert() {
        return new PlayerHandActionC2SPacket(
                Utils.getEnum(Action.class, this.status),
                new BlockPos(this.position.x, this.position.y, this.position.z),
                Utils.getEnum(Direction.class, this.facing));
    }
}
