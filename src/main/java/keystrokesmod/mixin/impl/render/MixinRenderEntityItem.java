package keystrokesmod.mixin.impl.render;

import keystrokesmod.module.impl.render.ItemPhysics;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.model.block.BakedModel;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.resource.model.ModelTransformation;
import net.minecraft.client.resource.model.ModelTransformations.Type;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemEntityRenderer.class)
public abstract class MixinRenderEntityItem {
    @Inject(method = "applyItemBobbing", at = @At("RETURN"))
    private void itemPhysics$pose(
            ItemEntity item,
            double x,
            double y,
            double z,
            float tickDelta,
            BakedModel model,
            CallbackInfoReturnable<Integer> result) {
        ItemPhysics physics = ItemPhysics.instance;
        int count = result.getReturnValueI();
        if (physics == null || count == 0) {
            return;
        }
        boolean block = model.isGui3d();
        EntityRenderDispatcher dispatcher = ((ItemEntityRenderer) (Object) this).getDispatcher();
        float age = item.getAge() + tickDelta;
        float yaw = (age / 20.0F + item.bobOffset) * 57.295776F;
        float bob = MathHelper.sin(age / 10.0F + item.bobOffset) * 0.1F + 0.1F;
        float stackOffset = 0.046875F * (count - 1) / 2;
        // Leave vanilla's model draws and end-of-entity hooks intact; replace only the local pose.
        GlStateManager.translatef(0, -bob, 0);
        if (!block && count > 1) {
            GlStateManager.translatef(0, 0, stackOffset);
        }
        if (block || dispatcher.options != null) {
            GlStateManager.rotatef(-yaw, 0, 1, 0);
        }
        GlStateManager.rotatef(item.bobOffset * 57.295776F, 0, 1, 0);
        if (item.onGround) {
            if (!block) {
                ModelTransformation ground = model.getTransformations().get(Type.GROUND);
                // The flat model is 1/16 block thick. Rest its bottom on the surface and stack copies upward.
                GlStateManager.translatef(
                        0, 0.005F + 0.03125F * ground.scale.z - 0.25F * ground.scale.y - ground.translation.z, 0);
                GlStateManager.rotatef(-90, 1, 0, 0);
            }
        } else {
            float spin = (float) (age * 16.0 * physics.rotationSpeed.getInput() % 360);
            GlStateManager.rotatef(spin, 1, 0, 0);
            if (!block && count > 1) {
                GlStateManager.translatef(0, 0, -stackOffset);
            }
        }
    }
}
