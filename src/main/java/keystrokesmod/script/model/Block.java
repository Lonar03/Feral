package keystrokesmod.script.model;

import keystrokesmod.platform.ClientPlatform;
import keystrokesmod.script.ScriptClassNames;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos;

public class Block {
    public String type;
    public String name;
    public boolean interactable;
    public int variant;
    public double height;
    public double width;
    public double length;
    public double x;
    public double y;
    public double z;

    public Block(net.minecraft.block.Block block, BlockPos blockPos) {
        this.type = ScriptClassNames.simpleName(block.getClass());
        this.name = ClientPlatform.registryName(block).replace("minecraft:", "");
        this.interactable = BlockUtils.isInteractable(block);
        this.variant = block.getMetadataFromState(BlockUtils.getBlockState(blockPos));
        this.height = block.getMaxY() - block.getMinY();
        this.width = block.getMaxX() - block.getMinX();
        this.length = block.getMaxZ() - block.getMinZ();
        this.x = blockPos.getX();
        this.y = blockPos.getY();
        this.z = blockPos.getZ();
    }

    public Block(BlockState state, BlockPos blockPos) {
        net.minecraft.block.Block block = state.getBlock();
        this.type = ScriptClassNames.simpleName(block.getClass());
        this.name = ClientPlatform.registryName(block).replace("minecraft:", "");
        this.interactable = BlockUtils.isInteractable(block);
        this.variant = block.getMetadataFromState(state);
        this.height = block.getMaxY() - block.getMinY();
        this.width = block.getMaxX() - block.getMinX();
        this.length = block.getMaxZ() - block.getMinZ();
        this.x = blockPos.getX();
        this.y = blockPos.getY();
        this.z = blockPos.getZ();
    }

    public Block(int x, int y, int z) {
        this(BlockUtils.getBlock(x, y, z), new BlockPos(x, y, z));
    }

    protected Block(String name, Vec3 position) {
        this(Utils.getBlockFromName(name), Vec3.getBlockPos(position));
    }

    public Block(String name) {
        this(name, new Vec3(-1.0, -1.0, -1.0));
    }

    public Block(Vec3 position) {
        this(BlockUtils.getBlock(position.x, position.y, position.z), new BlockPos(position.x, position.y, position.z));
    }
}
