package keystrokesmod.mixin.impl.world;

import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.combat.Displace;
import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public class MixinWorld {
    @Inject(
            method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/BlockState;I)Z",
            at = @At("RETURN"))
    private void feral$invalidateVoidBlock(
            BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<Boolean> info) {
        if (info.getReturnValueZ()) {
            this.feral$invalidateVoidTerrain(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());
        }
    }

    @Inject(method = "notifyRegionChanged(IIIIII)V", at = @At("RETURN"))
    private void feral$invalidateVoidChunks(int x1, int y1, int z1, int x2, int y2, int z2, CallbackInfo info) {
        if (y1 == 0 && y2 >= 255) {
            this.feral$invalidateVoidTerrain(x1, y1, z1, x2, y2, z2);
        }
    }

    private void feral$invalidateVoidTerrain(int x1, int y1, int z1, int x2, int y2, int z2) {
        World world = (World) (Object) this;
        if (world.isClient) {
            Displace displace = ModuleManager.displace;
            if (displace != null && displace.isEnabled()) {
                displace.invalidateVoidTerrain(world, x1, y1, z1, x2, y2, z2);
            }
        }
    }
}
