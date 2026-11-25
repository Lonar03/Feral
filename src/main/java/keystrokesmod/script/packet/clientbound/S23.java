package keystrokesmod.script.packet.clientbound;

import keystrokesmod.script.model.Block;
import keystrokesmod.script.model.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;

public class S23 extends SPacket {
    public Vec3 position;
    public Block block;

    public S23(BlockUpdateS2CPacket packet, byte f) {
        super(packet);
        this.position = Vec3.convert(packet.getPos());
        this.block = new Block(packet.getBlockState(), new BlockPos(this.position.x, this.position.y, this.position.z));
    }

    public S23(Vec3 position) {
        super(new BlockUpdateS2CPacket(Minecraft.getInstance().world, Vec3.getBlockPos(position)));
        this.position = position;
        this.block = new Block(position);
    }
}
