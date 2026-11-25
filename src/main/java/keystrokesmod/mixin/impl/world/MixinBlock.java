package keystrokesmod.mixin.impl.world;

import java.util.List;
import keystrokesmod.event.CollisionEvent;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.block.Block;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public abstract class MixinBlock {
    // Ignore Block: the crosshair goes through every block it ignores (Meower: isCollidable).
    @Inject(method = "canRayTrace()Z", at = @At("HEAD"), cancellable = true)
    private void feral$ignoreBlockRayTrace(CallbackInfoReturnable<Boolean> cir) {
        if (ModuleManager.ignoreBlock != null && ModuleManager.ignoreBlock.shouldIgnore((Block) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Shadow
    public abstract Box getCollisionShape(World var1, BlockPos var2, BlockState var3);

    @Overwrite
    public void addCollisions(
            World worldIn, BlockPos pos, BlockState state, Box mask, List<Box> list, Entity collidingEntity) {
        Box axisalignedbb = this.getCollisionShape(worldIn, pos, state);
        CollisionEvent event = new CollisionEvent(pos, (Block) (Object) this, axisalignedbb);
        ForgeEvents.MinecraftForge.EVENT_BUS.post(event);
        axisalignedbb = event.boundingBox;
        if (axisalignedbb != null && mask.intersects(axisalignedbb)) {
            list.add(axisalignedbb);
        }
    }
}
