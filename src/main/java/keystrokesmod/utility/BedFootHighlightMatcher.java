package keystrokesmod.utility;

import net.minecraft.block.BedBlock;
import net.minecraft.block.BedBlock.Part;
import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos;

public final class BedFootHighlightMatcher implements BlockHighlightMatcher {
    @Override
    public boolean matchesBlock(BlockState state) {
        return state != null && state.getBlock() instanceof BedBlock;
    }

    @Override
    public boolean shouldIndexAt(BlockPos pos, BlockState state) {
        return !this.matchesBlock(state) ? false : state.get(BedBlock.PART) == Part.FOOT;
    }
}
