package keystrokesmod.event;

import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public class CollisionEvent extends ForgeEvents.Event {
    public final BlockPos blockPos;
    public final Block block;
    public Box boundingBox;

    public CollisionEvent(BlockPos position, Block block, Box boundingBox) {
        this.blockPos = position;
        this.block = block;
        this.boundingBox = boundingBox;
    }

    public BlockPos getBlockPos() {
        return this.blockPos;
    }

    public Block getBlock() {
        return this.block;
    }

    public Box getBoundingBox() {
        return this.boundingBox;
    }

    public void setBoundingBox(Box boundingBox) {
        this.boundingBox = boundingBox;
    }
}
