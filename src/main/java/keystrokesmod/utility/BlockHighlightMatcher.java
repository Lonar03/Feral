package keystrokesmod.utility;

import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos;

public interface BlockHighlightMatcher {
    boolean matchesBlock(BlockState var1);

    default boolean shouldIndexAt(BlockPos pos, BlockState state) {
        return this.matchesBlock(state);
    }

    default boolean isActive() {
        return true;
    }

    default void beginScanPass() {}
}
