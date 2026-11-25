package keystrokesmod.mixin.impl.world;

import keystrokesmod.module.ModuleManager;
import net.minecraft.block.BarrierBlock;
import net.minecraft.block.Block;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes barriers render as a translucent model (assets/minecraft/models/block/barrier.json) with Barrier Vis on. */
@Mixin(BarrierBlock.class)
public abstract class MixinBlockBarrier extends Block {
    private MixinBlockBarrier() {
        super(null);
    }

    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void feral$renderWhenBarrierVis(CallbackInfoReturnable<Integer> cir) {
        if (ModuleManager.barrierVis != null && ModuleManager.barrierVis.isEnabled()) {
            cir.setReturnValue(3);
        }
    }

    @Override
    public BlockLayer getRenderLayer() {
        return BlockLayer.TRANSLUCENT;
    }

    @Override
    public boolean shouldRenderFace(WorldView world, BlockPos pos, Direction side) {
        return world.getBlockState(pos).getBlock() != (Object) this;
    }
}
