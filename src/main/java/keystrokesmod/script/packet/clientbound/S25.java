package keystrokesmod.script.packet.clientbound;

import keystrokesmod.script.model.Block;
import keystrokesmod.script.model.Vec3;
import net.minecraft.network.packet.s2c.play.BlockMiningProgressS2CPacket;
import net.minecraft.util.math.BlockPos;

public class S25 extends SPacket {
    public int entityId;
    public Block block;
    public int progress;

    public S25(BlockMiningProgressS2CPacket packet) {
        super(packet);
        this.entityId = packet.getPlayerId();
        this.block = new Block(Vec3.convert(packet.getPos()));
        this.progress = packet.getProgress();
    }

    public S25(int entityId, Block block, int progress) {
        super(new BlockMiningProgressS2CPacket(entityId, new BlockPos(block.x, block.y, block.z), progress));
        this.entityId = entityId;
        this.block = block;
        this.progress = progress;
    }

    public S25(int entityId, Vec3 position, int progress) {
        super(new BlockMiningProgressS2CPacket(entityId, Vec3.getBlockPos(position), progress));
        this.entityId = entityId;
        this.block = new Block(position);
        this.progress = progress;
    }
}
