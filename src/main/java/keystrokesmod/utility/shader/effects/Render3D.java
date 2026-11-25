package keystrokesmod.utility.shader.effects;

import keystrokesmod.utility.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.util.math.Box;
import org.lwjgl.opengl.GL11;

public final class Render3D {
    private static final Minecraft mc = Minecraft.getInstance();

    public static void filledBox(Box a, float r, float g, float b, float alpha) {
        Tesselator t = Tesselator.getInstance();
        BufferBuilder w = t.getBuffer();
        w.begin(7, DefaultVertexFormat.POSITION_COLOR);
        double[][] v = new double[][] {
            {a.minX, a.minY, a.minZ},
            {a.minX, a.maxY, a.minZ},
            {a.maxX, a.maxY, a.minZ},
            {a.maxX, a.minY, a.minZ},
            {a.minX, a.minY, a.maxZ},
            {a.maxX, a.minY, a.maxZ},
            {a.maxX, a.maxY, a.maxZ},
            {a.minX, a.maxY, a.maxZ},
            {a.minX, a.minY, a.minZ},
            {a.minX, a.minY, a.maxZ},
            {a.minX, a.maxY, a.maxZ},
            {a.minX, a.maxY, a.minZ},
            {a.maxX, a.minY, a.minZ},
            {a.maxX, a.maxY, a.minZ},
            {a.maxX, a.maxY, a.maxZ},
            {a.maxX, a.minY, a.maxZ},
            {a.minX, a.minY, a.minZ},
            {a.maxX, a.minY, a.minZ},
            {a.maxX, a.minY, a.maxZ},
            {a.minX, a.minY, a.maxZ},
            {a.minX, a.maxY, a.minZ},
            {a.minX, a.maxY, a.maxZ},
            {a.maxX, a.maxY, a.maxZ},
            {a.maxX, a.maxY, a.minZ}
        };

        for (double[] p : v) {
            w.vertex(p[0], p[1], p[2]).color(r, g, b, alpha).nextVertex();
        }

        t.end();
    }

    public static void entityBox(Entity entity, int n, int n2, float f) {
        if (entity instanceof LivingEntity) {
            EntityRenderDispatcher rm = mc.getEntityRenderDispatcher();
            double d4 = entity.prevX + (entity.x - entity.prevX) * f - rm.cameraX;
            double d5 = entity.prevY + (entity.y - entity.prevY) * f - rm.cameraY;
            double d6 = entity.prevZ + (entity.z - entity.prevZ) * f - rm.cameraZ;
            GlStateManager.pushMatrix();
            if (n == 3) {
                GL11.glTranslated(d4, d5 - 0.2, d6);
                GL11.glRotated(-rm.cameraYaw, 0.0, 1.0, 0.0);
                GlStateManager.disableDepthTest();
                GL11.glScalef(0.03F, 0.03F, 0.03F);
                float f2 = 0.5F;
                RenderUtils.beginRect();
                RenderUtils.rawRect(-20.0F - f2, 75.0F, 20.0F + f2, 75.0F + f2, -15987700);
                RenderUtils.rawRect(-20.0F - f2, -1.0F - f2, 20.0F + f2, -1.0F, -15987700);
                RenderUtils.rawRect(-20.0F - f2, -1.0F, -20.0F, 75.0F, -15987700);
                RenderUtils.rawRect(20.0F + f2, -1.0F, 20.0F, 75.0F, -15987700);
                RenderUtils.endRect();
                f2 = 1.5F;
                if (n2 == 0) {
                    int n4 = RenderUtils.rainbow(2L, 0L);
                    int n5 = RenderUtils.rainbow(2L, 1000L);
                    RenderUtils.gradientRect(-20.0F, -1.0F + f2, -20.0F + f2, 75.0F - f2, n4, n5);
                    RenderUtils.gradientRect(20.0F, -1.0F + f2, 20.0F - f2, 75.0F - f2, n4, n5);
                    RenderUtils.rect(-20.0F, 75.0F, 20.0F, 75.0F - f2, n4);
                    RenderUtils.rect(-20.0F, -1.0F, 20.0F, -1.0F + f2, n5);
                } else {
                    RenderUtils.rect(-20.0F, 75.0F, 20.0F, 75.0F - f2, n2);
                    RenderUtils.rect(-20.0F, -1.0F, 20.0F, -1.0F + f2, n2);
                    RenderUtils.rect(-20.0F, -1.0F + f2, -20.0F + f2, 75.0F - f2, n2);
                    RenderUtils.rect(20.0F, -1.0F + f2, 20.0F - f2, 75.0F - f2, n2);
                }

                GlStateManager.enableDepthTest();
            } else if (n == 4) {
                RenderUtils.renderEntity(entity, 4, 0.0, 0.0, n2, false);
            } else if (n == 6) {
                RenderUtils.renderEntity(entity, 6, 0.0, 0.0, n2, false);
            } else {
                if (n2 == 0) {
                    n2 = RenderUtils.rainbow(2L, 0L);
                }

                float a = (n2 >> 24 & 0xFF) / 255.0F;
                float r = (n2 >> 16 & 0xFF) / 255.0F;
                float g = (n2 >> 8 & 0xFF) / 255.0F;
                float b = (n2 & 0xFF) / 255.0F;
                Box bb = entity.getShape().grown(0.1, 0.1, 0.1);
                Box bb2 = new Box(
                        bb.minX - entity.x + d4,
                        bb.minY - entity.y + d5,
                        bb.minZ - entity.z + d6,
                        bb.maxX - entity.x + d4,
                        bb.maxY - entity.y + d5,
                        bb.maxZ - entity.z + d6);
                GL11.glBlendFunc(770, 771);
                GL11.glEnable(3042);
                GL11.glDisable(3553);
                GL11.glDisable(2929);
                GL11.glDepthMask(false);
                if (n == 1) {
                    GlStateManager.color4f(r, g, b, a);
                    GL11.glEnable(2848);
                    GL11.glLineWidth(2.0F);
                    WorldRenderer.renderOutlineShape(bb2);
                    GL11.glDisable(2848);
                } else if (n == 2) {
                    filledBox(bb2, r, g, b, 0.37F);
                }

                RenderUtils.resetColor();
                GL11.glEnable(3553);
                GL11.glEnable(2929);
                GL11.glDepthMask(true);
                GL11.glDisable(3042);
            }

            GlStateManager.popMatrix();
        }
    }
}
