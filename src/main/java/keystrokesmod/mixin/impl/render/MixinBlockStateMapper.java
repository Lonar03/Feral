package keystrokesmod.mixin.impl.render;

import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.resource.model.BlockModelDefinitions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets the barrier block load a real model so Barrier Vis can render it (vanilla registers it as invisible). */
@Mixin(BlockModelDefinitions.class)
public abstract class MixinBlockStateMapper {
    @Shadow
    private Set<Block> invisibleBlocks;

    @Inject(method = "registerInvisible", at = @At("TAIL"))
    private void feral$unregisterBarrier(CallbackInfo ci) {
        this.invisibleBlocks.remove(Blocks.BARRIER);
    }
}
