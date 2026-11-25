package keystrokesmod.mixin.impl.render;

import java.util.Random;
import keystrokesmod.module.impl.render.ItemPhysics;
import keystrokesmod.platform.ClientPlatform;
import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.model.block.BakedModel;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.resource.model.ModelTransformations.Type;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@ForgeEvents.SideOnly(ForgeEvents.Side.CLIENT)
@Mixin(ItemEntityRenderer.class)
public abstract class MixinRenderEntityItem extends EntityRenderer<Entity> {
    @Unique
    private static final Random itemPhysics$random = new Random();

    @Unique
    private static long itemPhysics$lastNano = System.nanoTime();

    @Unique
    private static double itemPhysics$rotation;

    protected MixinRenderEntityItem(EntityRenderDispatcher renderManager) {
        super(renderManager);
    }

    @Inject(method = "render(Lnet/minecraft/entity/ItemEntity;DDDFF)V", at = @At("HEAD"), cancellable = true)
    private void itemPhysics$doRender(
            ItemEntity entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        if (ItemPhysics.instance != null) {
            ci.cancel();
            Minecraft mc = Minecraft.getInstance();
            ItemEntityRenderer self = (ItemEntityRenderer) (Object) this;
            double speed = ItemPhysics.instance.rotationSpeed.getInput();
            itemPhysics$rotation = (System.nanoTime() - itemPhysics$lastNano) / 2500000.0 * speed;
            if (!mc.isPaused()) {
                itemPhysics$lastNano = System.nanoTime();
            } else {
                itemPhysics$rotation = 0.0;
            }

            ItemStack stack = entity.getItem();
            if (stack != null && stack.getItem() != null) {
                int seed = Item.getId(stack.getItem()) + stack.getMetadata();
                itemPhysics$random.setSeed(seed);
                self.bindTexture(TextureAtlas.BLOCKS_LOCATION);
                self.getDispatcher()
                        .textureManager
                        .get(TextureAtlas.BLOCKS_LOCATION)
                        .pushFilter(false, false);
                GlStateManager.enableRescaleNormal();
                GlStateManager.alphaFunc(516, 0.1F);
                GlStateManager.enableBlend();
                GlStateManager.blendFuncSeparate(770, 771, 1, 0);
                GlStateManager.pushMatrix();
                BakedModel model = mc.getItemRenderer().getModelShaper().getModel(stack);
                boolean is3D = model.isGui3d();
                int count = itemPhysics$getModelCount(stack);
                GlStateManager.translatef((float) x, (float) y, (float) z);
                if (is3D) {
                    GlStateManager.scalef(0.5F, 0.5F, 0.5F);
                }

                GL11.glRotatef(90.0F, 1.0F, 0.0F, 0.0F);
                GL11.glRotatef(entity.yaw, 0.0F, 0.0F, 1.0F);
                if (is3D) {
                    GlStateManager.translated(0.0, 0.0, -0.08);
                } else {
                    GlStateManager.translated(0.0, 0.0, -0.04);
                }

                if (!entity.onGround) {
                    double rot = itemPhysics$rotation * 2.0;
                    entity.pitch += (float) rot;
                } else if (!is3D) {
                    entity.pitch = 0.0F;
                }

                if (is3D || mc.getEntityRenderDispatcher().options != null) {
                    GlStateManager.rotatef(entity.pitch, 1.0F, 0.0F, 0.0F);
                }

                GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);

                for (int k = 0; k < count; k++) {
                    GlStateManager.pushMatrix();
                    if (is3D) {
                        if (k > 0) {
                            float ox = (itemPhysics$random.nextFloat() * 2.0F - 1.0F) * 0.15F;
                            float oy = (itemPhysics$random.nextFloat() * 2.0F - 1.0F) * 0.15F;
                            float oz = (itemPhysics$random.nextFloat() * 2.0F - 1.0F) * 0.15F;
                            GlStateManager.translatef(
                                    ClientPlatform.shouldSpreadItems(self) ? ox : 0.0F,
                                    ClientPlatform.shouldSpreadItems(self) ? oy : 0.0F,
                                    oz);
                        }

                        model = ClientPlatform.ForgeHooksClient.handleCameraTransforms(model, Type.GROUND);
                        mc.getItemRenderer().renderItem(stack, model);
                        GlStateManager.popMatrix();
                    } else {
                        model = ClientPlatform.ForgeHooksClient.handleCameraTransforms(model, Type.GROUND);
                        mc.getItemRenderer().renderItem(stack, model);
                        GlStateManager.popMatrix();
                        GlStateManager.translatef(0.0F, 0.0F, 0.05375F);
                    }
                }

                GlStateManager.popMatrix();
                GlStateManager.disableRescaleNormal();
                GlStateManager.disableBlend();
                self.bindTexture(TextureAtlas.BLOCKS_LOCATION);
                self.getDispatcher()
                        .textureManager
                        .get(TextureAtlas.BLOCKS_LOCATION)
                        .popFilter();
            }
        }
    }

    @Unique
    private static int itemPhysics$getModelCount(ItemStack stack) {
        if (stack.size > 48) {
            return 5;
        } else if (stack.size > 32) {
            return 4;
        } else if (stack.size > 16) {
            return 3;
        } else {
            return stack.size > 1 ? 2 : 1;
        }
    }
}
